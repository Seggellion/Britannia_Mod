package com.seggellion.britannia_mod.blessed.rescue;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.blessed.BlessedItemLifecycleMetadata;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

/**
 * Keeps a blessed item that is loose in the world from being destroyed by the ordinary ways the
 * world destroys loose items.
 *
 * <h2>Rescue first</h2>
 * The playbook's posture (D-LIFE-03) is to prevent the common, reliably observable losses rather
 * than let them happen and reconcile afterwards. Every path handled here therefore ends with the
 * item still existing, and <strong>none of them reports a destruction</strong>. A destruction
 * report means "this instance_uuid positively no longer exists"; an item this class saved plainly
 * still does.
 *
 * <p>Equally important is what this class does NOT do. It never treats an item it cannot see as
 * destroyed. It is driven entirely by positive events on a real {@link ItemEntity} that is in
 * front of it -- an expiring entity, an entity in lava, an entity below the world. Absence is
 * never an input (playbook invariant C).
 *
 * <h2>Ownership</h2>
 * Responsibility follows the OWNER STAMPED ON THE STACK, never whoever happens to be holding or
 * to have dropped it. Another player may carry a medallion around; that neither transfers the
 * entitlement nor makes them the person a rescue returns it to. A mismatch is logged and nothing
 * else (playbook D-LIFE-07).
 *
 * <h2>Legacy blessed items</h2>
 * A pre-M6 blessed deed carries {@code blessed}/{@code owner}/{@code deed_id} but no
 * {@code instance_uuid}. It is rescued exactly the same way -- losing one is just as bad -- but it
 * can never participate in lifecycle reporting, because there is no materialization identity to
 * report about.
 */
public final class BlessedItemRescueHandlers {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * How long an unrescuable-right-now item is kept alive before the question is asked again.
     * The owner being offline is the ordinary case; the item simply waits.
     */
    private static final int EXTRA_LIFE_TICKS = 6000; // 5 minutes

    /** How far below the world an entity must fall before it is treated as void-bound. */
    private static final int VOID_MARGIN = 16;

    private BlessedItemRescueHandlers() {
    }

    // --- despawn --------------------------------------------------------------

    /**
     * A dropped blessed item reaching its despawn timer must not simply vanish.
     *
     * <p>Dropping is not destruction and never becomes it: the materialization stays
     * {@code active} throughout, and nothing is reported.
     */
    @SubscribeEvent
    public static void onItemExpire(ItemExpireEvent event) {
        ItemEntity itemEntity = event.getEntity();
        if (itemEntity.level().isClientSide()) return;

        ItemStack stack = itemEntity.getItem();
        if (!BlessedItemLifecycleMetadata.isBlessed(stack)) return;

        if (returnToStampedOwner(itemEntity, stack, "despawn")) {
            return;
        }

        // Owner offline. Keep it in the world rather than let it expire; ask again later.
        event.setExtraLife(EXTRA_LIFE_TICKS);
        LOGGER.info("Blessed item kept alive past its despawn timer {} -- owner is not online to "
                + "receive it", describe(stack));
    }

    // --- lava, fire, void -----------------------------------------------------

    /**
     * The three loose-item hazards this version exposes deterministically on a per-entity tick.
     *
     * <p>Checked in the same handler because they share the rescue: get the item to its owner if
     * that is possible, and otherwise get it out of the hazard.
     */
    @SubscribeEvent
    public static void onItemEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;

        ItemStack stack = itemEntity.getItem();
        if (!BlessedItemLifecycleMetadata.isBlessed(stack)) return;

        boolean inLava = itemEntity.isInLava();
        boolean burning = itemEntity.isOnFire();
        boolean falling = itemEntity.getY() < itemEntity.level().getMinBuildHeight() - VOID_MARGIN;

        if (!inLava && !burning && !falling) return;

        String hazard = inLava ? "lava" : burning ? "fire" : "void";

        if (returnToStampedOwner(itemEntity, stack, hazard)) {
            return;
        }

        // Owner offline: get it out of the hazard where it stands.
        itemEntity.clearFire();
        if (falling) {
            // Nothing survives below the world, and there is no "up a bit" that helps, so it goes
            // somewhere the world definitely exists. Deliberately blunt: an entitlement sitting at
            // spawn is recoverable, one that fell out of the world is not.
            ServerLevel level = (ServerLevel) itemEntity.level();
            var spawn = level.getSharedSpawnPos();
            itemEntity.setPos(spawn.getX() + 0.5D, level.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    spawn.getX(), spawn.getZ()) + 1.0D, spawn.getZ() + 0.5D);
            itemEntity.setDeltaMovement(0.0D, 0.0D, 0.0D);
        } else {
            // Same nudge the quest-item path uses: up and out, with a little lift.
            itemEntity.setPos(itemEntity.getX(), itemEntity.getY() + 1.25D, itemEntity.getZ());
            itemEntity.setDeltaMovement(0.0D, 0.1D, 0.0D);
        }
        itemEntity.setExtendedLifetime();

        LOGGER.info("Blessed item moved clear of {} {} -- owner is not online to receive it",
                hazard, describe(stack));
    }

    // --- shared rescue --------------------------------------------------------

    /**
     * Hands the item to the player stamped on it, if they are online, and removes the entity in
     * the same breath so no second copy can exist.
     *
     * @return true when the item was delivered to its owner and the entity discarded
     */
    private static boolean returnToStampedOwner(ItemEntity itemEntity, ItemStack stack, String hazard) {
        if (!(itemEntity.level() instanceof ServerLevel level)) return false;

        UUID stampedOwner = stampedOwnerOf(stack);
        if (stampedOwner == null) return false;

        logHolderMismatch(itemEntity, stampedOwner);

        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(stampedOwner);
        if (owner == null) return false;

        // Copy first, discard second: the entity must be gone before anything can pick it up
        // again, and the copy is what the owner receives.
        ItemStack rescued = stack.copy();
        itemEntity.discard();

        if (!owner.getInventory().add(rescued)) {
            owner.drop(rescued, false);
        }

        LOGGER.info("Blessed item rescued from {} and returned to its stamped owner {} {}",
                hazard, stampedOwner, describe(stack));
        return true;
    }

    /**
     * The owner this item belongs to, from the canonical parser where the item is lifecycle
     * aware, and from the raw {@code owner} tag for a pre-M6 blessed deed that has no
     * materialization identity. Both are the STAMPED owner; neither is the holder.
     */
    private static UUID stampedOwnerOf(ItemStack stack) {
        Optional<BlessedItemLifecycleMetadata> lifecycle = BlessedItemLifecycleMetadata.of(stack);
        if (lifecycle.isPresent()) {
            return lifecycle.get().ownerUuid();
        }

        // Legacy fallback, deliberately narrow and defensive.
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        try {
            CompoundTag tag = data.copyTag();
            String owner = tag.getString("owner");
            return owner.isBlank() ? null : UUID.fromString(owner);
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    /**
     * Someone other than the owner dropped it. Worth knowing, and nothing more: the entitlement
     * does not move, the stamp is not rewritten, and the holder is not prevented from carrying it.
     */
    private static void logHolderMismatch(ItemEntity itemEntity, UUID stampedOwner) {
        UUID thrower = itemEntity.getOwner() == null ? null : itemEntity.getOwner().getUUID();
        if (thrower != null && !thrower.equals(stampedOwner)) {
            LOGGER.warn("Blessed item owner mismatch: dropped by {} but stamped to {} -- rescuing "
                    + "to the stamped owner and changing nothing else", thrower, stampedOwner);
        }
    }

    /** Safe identifiers for a log line. Never credentials, never signatures. */
    private static String describe(ItemStack stack) {
        return BlessedItemLifecycleMetadata.of(stack)
                .map(meta -> "instance_uuid=" + meta.instanceUuid() + " deed_id=" + meta.deedId()
                        + " item=" + stack.getItem())
                .orElse("legacy blessed item (no instance_uuid) item=" + stack.getItem());
    }
}
