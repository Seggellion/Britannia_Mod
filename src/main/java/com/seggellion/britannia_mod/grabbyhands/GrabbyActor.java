package com.seggellion.britannia_mod.grabbyhands;

import com.seggellion.britannia_mod.registry.GrabbyRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The player side of a Grabby transaction, behind a seam so transactions test without a live server.
 *
 * <p>Everything a transaction needs to know about the acting player is here, and nothing else. In
 * particular there is no "may this player use the object" question, because Grabby Hands does not
 * answer that one — see {@link GrabbyPolicy}.
 */
public interface GrabbyActor {
    UUID id();

    /** Server-side reach validation. The client's claimed position is never trusted. */
    boolean canReach(BlockPos pos);

    boolean isCreative();

    /** Effective operator level, as {@link GrabbyPolicy#ADMIN_PERMISSION_LEVEL} understands it. */
    int permissionLevel();

    /** Whether {@code pos} sits inside a registered structure this actor does not own. */
    boolean insideForeignStructure(BlockPos pos);

    /**
     * Whether this stack would fit in the actor's inventory <em>right now</em>, without inserting it.
     *
     * <p>Checked before the world object is removed. That ordering is what guarantees a full
     * inventory never destroys the thing you were trying to pick up.
     */
    boolean hasRoomFor(ItemStack stack);

    /**
     * Inserts the stack into the actor's inventory.
     *
     * @return {@code true} only if the whole stack was accepted
     */
    boolean give(ItemStack stack);

    /**
     * Places a stack into the world at the actor's feet.
     *
     * <p>The last-resort path: if insertion somehow fails after the world object was already removed,
     * the object becomes a floor drop rather than vanishing. Losing an item is worse than an
     * inelegant outcome.
     */
    void dropAtFeet(ItemStack stack);

    /** A sound only this actor hears. Inventory events are personal, not broadcast. */
    void playPersonalSound(SoundEvent event);

    /**
     * Costs the held tool one point of durability, as breaking a block with it would.
     *
     * <p>Vanilla parity rather than an invented rule: an axe swing that destroys an object costs the
     * same as an axe swing that breaks one. Creative players are exempt, as everywhere else.
     *
     * @return {@code true} if durability was actually consumed
     */
    boolean damageTool();

    /**
     * Where the main-hand item would land, without placing anything.
     *
     * <p>This is vanilla's own answer, not a guess: it is whatever {@code BlockPlaceContext} resolves,
     * which means clicking a replaceable block targets that block and clicking the top face of a
     * chair targets the space above it. Getting the position first is what lets policy be checked at
     * the real destination before any mutation happens.
     *
     * @return the destination, or empty if nothing could be placed there
     */
    Optional<BlockPos> resolvePlacementTarget(BlockHitResult hit);

    /**
     * Runs the main-hand item's own native placement path.
     *
     * <p>Everything specialized comes along for free because vanilla does it:
     * {@code getStateForPlacement} restores facing, {@code canSurvive} and {@code isUnobstructed}
     * enforce the block's real support and collision rules (which is what preserves existing
     * furniture stacking), {@code setPlacedBy} transfers item state into the block entity (which is
     * how a wine bottle keeps its six {@code WineData} fields), the native place sound plays, and the
     * source item is consumed — only on success.
     *
     * <p>Grabby Hands must never replace this with a {@code setBlock} call. Doing so would silently
     * drop all of the above.
     *
     * @return the position the object landed at, or empty if placement was refused
     */
    Optional<BlockPos> placeMainHandItem(BlockHitResult hit);

    /**
     * Places the generic item host, carrying the main-hand item as its payload.
     *
     * <p>For items with no block form of their own. The host is still placed through
     * {@code BlockItem.place} using a synthetic stack of the host's own block item, so facing,
     * {@code canSurvive}, the unobstructed check and the native place sound all still come from
     * vanilla rather than from a bespoke routine.
     *
     * <p>Because vanilla consumes the synthetic stack rather than the player's, one of the player's
     * real items is consumed explicitly here — and only after placement has actually succeeded.
     *
     * @return the position the host landed at, or empty if placement was refused
     */
    Optional<BlockPos> placeItemHost(BlockHitResult hit);

    static GrabbyActor of(ServerPlayer player) {
        return new ServerGrabbyActor(player);
    }

    /** The live implementation. */
    final class ServerGrabbyActor implements GrabbyActor {
        private final ServerPlayer player;

        private ServerGrabbyActor(ServerPlayer player) {
            this.player = Objects.requireNonNull(player, "player");
        }

        @Override
        public UUID id() {
            return player.getUUID();
        }

        @Override
        public boolean canReach(BlockPos pos) {
            return player.canInteractWithBlock(pos, 1.0);
        }

        @Override
        public boolean isCreative() {
            return player.isCreative();
        }

        @Override
        public int permissionLevel() {
            return GrabbyPolicy.effectivePermissionLevel(player);
        }

        @Override
        public boolean insideForeignStructure(BlockPos pos) {
            return GrabbyPolicy.insideForeignStructure(pos, player);
        }

        @Override
        public boolean hasRoomFor(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            // A free slot, or an existing compatible stack with headroom. Deliberately conservative:
            // it asks the real inventory the same question Inventory.add would, without mutating it.
            return player.getInventory().getFreeSlot() >= 0
                    || hasMergeableSlot(stack);
        }

        private boolean hasMergeableSlot(ItemStack stack) {
            if (stack.getMaxStackSize() <= 1) {
                return false;
            }
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack existing = player.getInventory().getItem(slot);
                if (!existing.isEmpty()
                        && existing.getCount() < existing.getMaxStackSize()
                        && ItemStack.isSameItemSameComponents(existing, stack)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean give(ItemStack stack) {
            return player.getInventory().add(stack) && stack.isEmpty();
        }

        @Override
        public void dropAtFeet(ItemStack stack) {
            player.drop(stack, false);
        }

        @Override
        public void playPersonalSound(SoundEvent event) {
            player.playNotifySound(event, SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        @Override
        public boolean damageTool() {
            ItemStack tool = player.getMainHandItem();
            if (player.isCreative() || tool.isEmpty() || !tool.isDamageableItem()) {
                return false;
            }
            tool.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            return true;
        }

        @Override
        public Optional<BlockPos> resolvePlacementTarget(BlockHitResult hit) {
            BlockPlaceContext context = placementContext(hit);
            if (!context.canPlace()) {
                return Optional.empty();
            }
            return Optional.of(placementRoot(context).immutable());
        }

        @Override
        public Optional<BlockPos> placeMainHandItem(BlockHitResult hit) {
            ItemStack held = player.getMainHandItem();
            if (!(held.getItem() instanceof BlockItem blockItem)) {
                return Optional.empty();
            }
            BlockPlaceContext context = placementContext(hit);
            BlockPos target = placementRoot(context).immutable();

            // An item that owns its placement runs its own path. Calling BlockItem.place on a crate
            // would drop a single cell where the structure's anchor was supposed to go.
            InteractionResult result = blockItem instanceof GrabbyStructurePlacementItem
                    ? blockItem.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit))
                    : blockItem.place(context);
            return result.consumesAction() ? Optional.of(target) : Optional.empty();
        }

        /** Where the held item's object will actually be rooted, which is not always the clicked cell. */
        private BlockPos placementRoot(BlockPlaceContext context) {
            return player.getMainHandItem().getItem() instanceof GrabbyStructurePlacementItem structure
                    ? structure.grabbyPlacementRoot(context)
                    : context.getClickedPos();
        }

        @Override
        public Optional<BlockPos> placeItemHost(BlockHitResult hit) {
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) {
                return Optional.empty();
            }
            ItemStack hostStack = new ItemStack(GrabbyRegistry.PLACED_ITEM_BLOCK_ITEM.get());
            BlockPlaceContext context = new BlockPlaceContext(
                    player.level(), player, InteractionHand.MAIN_HAND, hostStack, hit);
            BlockPos target = context.getClickedPos().immutable();
            if (!GrabbyRegistry.PLACED_ITEM_BLOCK_ITEM.get().place(context).consumesAction()) {
                return Optional.empty();
            }
            held.consume(1, player);
            return Optional.of(target);
        }

        private BlockPlaceContext placementContext(BlockHitResult hit) {
            return new BlockPlaceContext(
                    player.level(), player, InteractionHand.MAIN_HAND, player.getMainHandItem(), hit);
        }
    }
}
