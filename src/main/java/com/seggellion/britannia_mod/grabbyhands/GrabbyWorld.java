package com.seggellion.britannia_mod.grabbyhands;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
import java.util.Optional;

/**
 * The seam Grabby Hands reads and mutates the world through.
 *
 * <p>Its job is to keep the transaction services unit-testable on the project's existing plain-JUnit
 * harness with no Minecraft server bootstrap. A fake implementation drives pickup against an
 * in-memory map, which is what makes "exactly once" and the two-player race directly assertable.
 *
 * <p>Deliberately narrow: it grows only when a transaction actually needs an operation, so it never
 * accumulates methods nothing calls.
 *
 * <p>This is a Grabby Hands-owned abstraction. {@code ShrineLifecycleService.WorldAccess} solves a
 * comparable problem for shrines and was studied as a reference, but the two are unrelated and
 * neither depends on the other.
 */
public interface GrabbyWorld {
    /** Identity used to key per-object mutation guards. Never mutated, never persisted. */
    Object levelIdentity();

    BlockState blockState(BlockPos pos);

    /** Provenance at a position; {@link GrabbyInstanceState#worldPlaced()} when there is none. */
    GrabbyInstanceState grabbyState(BlockPos pos);

    /**
     * Stamps provenance at a position.
     *
     * @return {@code false} if the block there cannot carry provenance
     */
    boolean setGrabbyState(BlockPos pos, GrabbyInstanceState state);

    /**
     * Why one sub-object of a multi-object block refuses to be carried, if it does.
     *
     * <p>Empty when the block holds no sub-objects at all, which is every ordinary enrolled block.
     */
    default Optional<GrabbyTransportRefusal> subObjectRefusal(BlockPos pos, int subObjectId) {
        return Optional.empty();
    }

    /**
     * Whether several objects a player can address separately live at this position.
     *
     * <p>This is what admits a sub-object pickup, in place of the movable-type tag an ordinary object
     * is admitted by. The two are not the same question: the tag says a block travels whole, and the
     * blocks carrying it are held to that - a registered item to be placed back as, a provenance
     * capable block entity, a matching axe-destroyable entry. A host travels only in pieces and meets
     * none of those, so enrolling one would be claiming something untrue about it.
     */
    default boolean hostsSubObjects(BlockPos pos) {
        return false;
    }

    /** The item one sub-object would be carried as, without taking it. */
    default Optional<ItemStack> peekSubObject(BlockPos pos, int subObjectId) {
        return Optional.empty();
    }

    /**
     * Takes one sub-object out of a multi-object block and returns the item carrying it.
     *
     * <p>Empty when there is no such sub-object, which includes the ordinary case of a block that has
     * none and the racing case of one another player has just taken.
     */
    default Optional<ItemStack> takeSubObject(BlockPos pos, int subObjectId) {
        return Optional.empty();
    }

    /** Current level game time, used as the placement timestamp. */
    long gameTime();

    /**
     * Builds the portable item for the object at {@code pos} <em>without mutating anything</em>.
     *
     * <p>The live implementation uses the block's own clone-stack path, which is already the
     * project's block-to-item state transfer: {@code WineBottleBlock.getCloneItemStack} copies the
     * full {@code WineData} across, so a bottle's six gameplay fields survive capture today without
     * Grabby Hands knowing a single thing about wine.
     *
     * @return the portable stack, or {@link ItemStack#EMPTY} if nothing can represent this object
     */
    ItemStack capturePortableStack(BlockPos pos);

    /**
     * Detaches any payload the block would otherwise drop from its own removal path.
     *
     * <p>Called once, immediately before {@link #removeObject}, and only after every check has passed
     * — so a refused pickup never reaches it and never disturbs the object.
     *
     * <p>Without this step the placed-item host would hand the player its item twice: once into the
     * inventory, once onto the floor from {@code onRemove}. Containers have the same hazard on a
     * larger scale, which is why the step is generic rather than host-specific.
     *
     * @return {@code true} if something was detached
     */
    boolean detachPayloadBeforeRemoval(BlockPos pos);

    /**
     * Puts back whatever {@link #detachPayloadBeforeRemoval} took, after a removal that did not happen.
     *
     * <p>Unreachable in practice — the guard is held and the block was just verified present — but the
     * consequence of being wrong is a destroyed inventory, so the recovery is written rather than
     * reasoned about.
     */
    void restoreAfterFailedRemoval(BlockPos pos, ItemStack captured);

    /**
     * Why the object at this position refuses transport right now, if it does.
     *
     * <p>The object's own rules; the transport layer only relays them.
     */
    Optional<GrabbyTransportRefusal> transportRefusal(BlockPos pos);

    /**
     * Gives a freshly placed host the item it represents.
     *
     * @return {@code false} if the block there cannot hold a payload
     */
    boolean attachPayload(BlockPos pos, ItemStack payload);

    /**
     * Removes the world object without running any loot or drop path.
     *
     * <p>Drop-free removal is what stops a pickup producing both a portable item and a floor drop.
     *
     * <p>Note for M7: containers additionally spill their contents from {@code onRemove}. The payload
     * detach step above is the generic answer to that, but a container's contents are not a single
     * stack, so M7 still owes it a transaction step of its own.
     *
     * @return {@code false} if the block was already gone or could not be removed
     */
    boolean removeObject(BlockPos pos);

    /**
     * Detaches a payload that <em>is</em> the object, so destruction consumes it.
     *
     * <p>Deliberately narrower than {@link #detachPayloadBeforeRemoval}. A placed-item host's payload
     * is the thing being destroyed and must not survive as a floor drop. A container's contents are
     * not the container, and are left attached precisely so the block's own removal path spills them.
     *
     * @return {@code true} if a payload was consumed
     */
    boolean detachObjectPayloadForDestruction(BlockPos pos);

    /** Whether the object here refuses destruction because it would bypass its own security. */
    boolean securedAgainstDestruction(BlockPos pos);

    /** How many occupied slots the object at this position holds. Zero when it is not a container. */
    int occupiedSlotCount(BlockPos pos);

    /** World-audible sound at a position, heard by everyone nearby. */
    void playWorldSound(BlockPos pos, SoundEvent event);

    /**
     * Particles and the material-appropriate break sound for a destroyed object.
     *
     * <p>Uses the block's own declared {@code SoundType}, so a wooden chair and a glass bottle sound
     * different without Grabby Hands maintaining a material table of its own.
     */
    void playDestructionEffect(BlockPos pos, BlockState state);

    static GrabbyWorld of(ServerLevel level) {
        return new ServerGrabbyWorld(level);
    }

    /** The live implementation. Reads only already-loaded state; never force-loads a chunk. */
    final class ServerGrabbyWorld implements GrabbyWorld {
        private final ServerLevel level;

        private ServerGrabbyWorld(ServerLevel level) {
            this.level = Objects.requireNonNull(level, "level");
        }

        @Override
        public Object levelIdentity() {
            return level;
        }

        @Override
        public BlockState blockState(BlockPos pos) {
            return level.getBlockState(pos);
        }

        @Override
        public GrabbyInstanceState grabbyState(BlockPos pos) {
            return GrabbyProvenanceAccess.read(level, pos);
        }

        @Override
        public boolean setGrabbyState(BlockPos pos, GrabbyInstanceState state) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            return GrabbyProvenanceAccess.write(blockEntity, state);
        }

        @Override
        public long gameTime() {
            return level.getGameTime();
        }

        @Override
        public ItemStack capturePortableStack(BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                return ItemStack.EMPTY;
            }
            ItemStack portable = state.getBlock().getCloneItemStack(level, pos, state);
            // An object that carries more than its item form can express writes the rest itself.
            // Deliberately not folded into getCloneItemStack, which creative middle-click also uses.
            if (!portable.isEmpty()
                    && level.getBlockEntity(pos) instanceof GrabbyPortableState portableState) {
                portableState.writePortableState(portable, level.registryAccess());
            }
            return portable;
        }

        @Override
        public Optional<GrabbyTransportRefusal> transportRefusal(BlockPos pos) {
            return level.getBlockEntity(pos) instanceof GrabbyPortableState portableState
                    ? portableState.transportRefusal()
                    : Optional.empty();
        }

        @Override
        public void restoreAfterFailedRemoval(BlockPos pos, ItemStack captured) {
            if (level.getBlockEntity(pos) instanceof GrabbyPortableState portableState) {
                portableState.restorePortableState(captured, level.registryAccess());
            } else if (level.getBlockEntity(pos) instanceof GrabbyPayloadHolder holder) {
                holder.setGrabbyPayload(captured);
            }
        }

        @Override
        public boolean attachPayload(BlockPos pos, ItemStack payload) {
            if (level.getBlockEntity(pos) instanceof GrabbyPayloadHolder holder) {
                holder.setGrabbyPayload(payload);
                return true;
            }
            return false;
        }

        @Override
        public boolean detachPayloadBeforeRemoval(BlockPos pos) {
            return level.getBlockEntity(pos) instanceof GrabbyDetachable detachable
                    && detachable.detachForTransport();
        }

        @Override
        public Optional<GrabbyTransportRefusal> subObjectRefusal(BlockPos pos, int subObjectId) {
            return level.getBlockEntity(pos) instanceof GrabbySubObjectHost host
                    ? host.subObjectRefusal(subObjectId)
                    : Optional.empty();
        }

        @Override
        public boolean hostsSubObjects(BlockPos pos) {
            return level.getBlockEntity(pos) instanceof GrabbySubObjectHost;
        }

        @Override
        public Optional<ItemStack> peekSubObject(BlockPos pos, int subObjectId) {
            return level.getBlockEntity(pos) instanceof GrabbySubObjectHost host
                    ? host.previewSubObject(subObjectId, level.registryAccess())
                    : Optional.empty();
        }

        @Override
        public Optional<ItemStack> takeSubObject(BlockPos pos, int subObjectId) {
            return level.getBlockEntity(pos) instanceof GrabbySubObjectHost host
                    ? host.takeSubObject(subObjectId, level.registryAccess())
                    : Optional.empty();
        }

        @Override
        public boolean removeObject(BlockPos pos) {
            if (level.getBlockState(pos).isAir()) {
                return false;
            }
            // removeBlock, not destroyBlock: destroyBlock runs dropResources and would hand the
            // player a second copy of whatever they are in the middle of picking up.
            return level.removeBlock(pos, false);
        }

        @Override
        public boolean securedAgainstDestruction(BlockPos pos) {
            return level.getBlockEntity(pos) instanceof GrabbyPortableState portableState
                    && portableState.securedAgainstDestruction();
        }

        @Override
        public int occupiedSlotCount(BlockPos pos) {
            return level.getBlockEntity(pos) instanceof GrabbyPortableState portableState
                    ? portableState.occupiedSlotCount()
                    : 0;
        }

        @Override
        public boolean detachObjectPayloadForDestruction(BlockPos pos) {
            return level.getBlockEntity(pos) instanceof GrabbyPayloadHolder holder
                    && holder.detachForTransport();
        }

        @Override
        public void playWorldSound(BlockPos pos, SoundEvent event) {
            level.playSound(null, pos, event, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        @Override
        public void playDestructionEffect(BlockPos pos, BlockState state) {
            // The vanilla block-break effect: particles plus the block's own break sound, which is
            // exactly what GrabbySoundRoles.destroy resolves to.
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        }
    }
}
