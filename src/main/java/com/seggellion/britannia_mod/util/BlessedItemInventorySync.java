package com.seggellion.britannia_mod.sync;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.blessed.delivery.BlessedDeliveryReceipt;
import com.seggellion.britannia_mod.blessed.delivery.BlessedDeliveryReceiptStatus;
import com.seggellion.britannia_mod.blessed.delivery.BlessedDeliveryReceiptStore;
import com.seggellion.britannia_mod.blessed.delivery.BlessedDeliveryReceipts;
import com.seggellion.britannia_mod.blessed.delivery.BlessedDeliveryReportClient;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Delivers blessed items according to what Rails says about them, and nothing else.
 *
 * <h2>What changed, and why it matters</h2>
 *
 * <p>This class used to decide whether to create an item by looking for one:
 *
 * <pre>{@code
 *   boolean found = inv.items.stream().anyMatch(...);   // the 36 main slots
 *   if (found) continue;
 *   inv.add(new ItemStack(target));                     // otherwise, make another
 * }</pre>
 *
 * <p>That made inventory absence the authority for creating a permanent entitlement, and
 * absence is not evidence of anything. An item in the offhand, a chest, the bank, a display
 * case, an ender chest or on the ground as a dropped entity is absent from those 36 slots, so
 * every one of those was a duplicate waiting to happen. M1 measured it: because the granted
 * stack is component-identical, a duplicate could even MERGE into the offhand stack the scan
 * had failed to see, taking it from one to two with nothing visible on screen.
 *
 * <p>Delivery authority is now:
 *
 * <pre>{@code
 *   Rails materialization state  +  durable local receipt keyed by instance_uuid
 * }</pre>
 *
 * <p>Rails decides that a delivery is owed ({@code pending}); a receipt written and fsync'd to
 * disk BEFORE the item is created decides whether this shard has already acted on it. Where the
 * item currently lives never enters into it. Once Rails says {@code active}, this class does not
 * care whether the medallion is in a pocket, a chest or a display case -- and deliberately does
 * not look.
 *
 * <p>A scan survives only as a diagnostic: when Rails says {@code active} and nothing matching is
 * visible, that is worth a reconciliation warning, but it repairs nothing. Restoring a genuinely
 * lost item is Rails' decision to make (a fresh {@code pending} with a NEW instance_uuid), never
 * this class's.
 *
 * <h2>Threading</h2>
 * {@link #apply} mutates player inventory and SavedData, so it must run on the server thread --
 * {@code BlessedItemSyncHandler} already hands it back there via {@code server.execute}. Only the
 * Rails acknowledgement is pushed off-thread, through {@link ServerHttpExecutor}.
 */
public final class BlessedItemInventorySync {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BlessedItemInventorySync() {
    }

    public static void apply(ServerPlayer player, List<BlessedItemSyncAPI.BlessedRow> rows) {
        if (rows == null || rows.isEmpty()) return;

        for (BlessedItemSyncAPI.BlessedRow row : rows) {
            // Per-row isolation. Before M6 a single malformed identifier threw out of the whole
            // loop and silently abandoned every row behind it, so one bad entitlement cost a
            // player all the others. A row now fails alone.
            try {
                applyRow(player, row);
            } catch (Exception failure) {
                LOGGER.error("Blessed row failed and was skipped player={} item={} deed={} -- "
                        + "remaining rows continue", player.getUUID(), row.itemName(), row.deedId(),
                        failure);
            }
        }
    }

    private static void applyRow(ServerPlayer player, BlessedItemSyncAPI.BlessedRow row) {
        // Legacy consumed deeds. The catalogue still lists them; they were spent on a house and
        // there is nothing to deliver. Rails also declines to materialize them, so this and the
        // lifecycle check below agree -- but `used` is checked first because it is the older and
        // narrower contract, and a Rails build predating M4 sends only this.
        if (row.used()) return;

        if (!row.hasLifecycle()) {
            // FAIL CLOSED. No materialization identity means Rails either did not authorise a
            // delivery here (consumed, or scoped to another shard) or is too old to say. Either
            // way, inventing one is exactly the duplication this milestone removes.
            LOGGER.debug("Blessed row carries no materialization identity; not delivering "
                    + "player={} item={} deed={}", player.getUUID(), row.itemName(), row.deedId());
            return;
        }

        UUID instanceUuid;
        try {
            instanceUuid = UUID.fromString(row.instanceUuid());
        } catch (IllegalArgumentException malformed) {
            LOGGER.error("Blessed row has an unusable instance_uuid={} player={} item={} -- skipped",
                    row.instanceUuid(), player.getUUID(), row.itemName());
            return;
        }

        switch (row.state()) {
            case "pending" -> deliver(player, row, instanceUuid);
            case "active" -> noteAlreadyDelivered(player, row, instanceUuid);
            case "destroyed" ->
                // Rails restores by minting a NEW pending materialization with a fresh
                // instance_uuid. The mod never grants itself restoration authority.
                LOGGER.debug("Blessed instance={} is destroyed; awaiting a Rails restoration",
                        instanceUuid);
            default ->
                // FAIL CLOSED on anything this build does not understand, rather than guessing
                // that an unknown state means "deliver".
                LOGGER.warn("Blessed instance={} has unknown state={} -- not delivering",
                        instanceUuid, row.state());
        }
    }

    /**
     * {@code pending}: the only state that authorises creating a physical item.
     */
    private static void deliver(ServerPlayer player, BlessedItemSyncAPI.BlessedRow row,
                                UUID instanceUuid) {
        Item target = resolveItem(row, player);
        if (target == null) return;

        ServerLevel level = player.serverLevel();
        Optional<BlessedDeliveryReceipt> existing = BlessedDeliveryReceipts.find(level, instanceUuid);

        if (existing.isPresent()) {
            BlessedDeliveryReceipt receipt = existing.get();

            if (!receipt.matches(row.deedId(), row.itemName(), player.getUUID())) {
                // Local state binds this instance to a different item or player. Something is
                // corrupt or badly wrong; refusing is the only safe move.
                LOGGER.error("Blessed receipt fingerprint mismatch instance={} player={} item={} "
                        + "-- refusing to deliver", instanceUuid, player.getUUID(), row.itemName());
                return;
            }

            if (receipt.status() == BlessedDeliveryReceiptStatus.DESTROYED) {
                // Terminal, and terminal locally is enough. This instance is positively known to
                // be gone, so it is never physically re-delivered no matter what Rails still
                // says -- Rails may simply not have heard yet. A restoration arrives as a NEW
                // pending materialization with a NEW instance_uuid, which is a different row and
                // takes the ordinary path above.
                LOGGER.warn("Blessed instance={} is destroyed locally; refusing to re-deliver it. "
                        + "A restoration must arrive as a new instance_uuid.", instanceUuid);
                return;
            }

            if (receipt.status() == BlessedDeliveryReceiptStatus.DELIVERED) {
                // The item already exists in this world; only Rails has not been told, or its
                // answer was lost. Replay the acknowledgement -- never the delivery.
                LOGGER.info("Blessed instance={} already delivered locally; replaying the Rails "
                        + "acknowledgement", instanceUuid);
                reportDelivered(player, instanceUuid);
                return;
            }

            // A receipt that is still PENDING_PHYSICAL_DELIVERY means the server died between
            // writing it and creating the item. Finish the SAME delivery, under the SAME
            // identity: a second instance_uuid would be a second entitlement.
            LOGGER.warn("Recovering an unfinished blessed delivery instance={} player={} item={}",
                    instanceUuid, player.getUUID(), row.itemName());
        } else {
            // Receipt BEFORE the risky part, flushed to disk before this returns. If the process
            // dies immediately after, recovery finds the receipt and completes this same
            // delivery instead of starting a new one.
            BlessedDeliveryReceiptStore.RecordOutcome outcome =
                    BlessedDeliveryReceipts.recordPendingDelivery(
                            level, instanceUuid, row.deedId(), row.itemName(), player.getUUID(),
                            System.currentTimeMillis());

            switch (outcome) {
                case FINGERPRINT_MISMATCH -> {
                    LOGGER.error("Blessed receipt fingerprint mismatch on record instance={} "
                            + "player={} -- refusing to deliver", instanceUuid, player.getUUID());
                    return;
                }
                case READ_ONLY_SCHEMA -> {
                    LOGGER.error("Blessed receipt store is read-only (newer on-disk schema); "
                            + "refusing to deliver instance={}", instanceUuid);
                    return;
                }
                default -> { /* CREATED or IDEMPOTENT_REPLAY: safe to proceed */ }
            }
        }

        materialize(player, target, row, instanceUuid);
    }

    /** The risky part, and everything that must follow it in order. */
    private static void materialize(ServerPlayer player, Item target,
                                    BlessedItemSyncAPI.BlessedRow row, UUID instanceUuid) {
        ItemStack stack = stamp(target, player, row, instanceUuid);

        Inventory inventory = player.getInventory();
        if (!inventory.add(stack)) {
            // A full inventory must not lose a permanent entitlement, and must not leave the
            // receipt pending forever either -- the item exists either way.
            player.drop(stack, false);
            LOGGER.info("Blessed instance={} delivered to the ground; player={} inventory was full",
                    instanceUuid, player.getUUID());
        }

        BlessedDeliveryReceipts.markDelivered(player.serverLevel(), instanceUuid);
        reportDelivered(player, instanceUuid);
    }

    /**
     * The blessed identity stamp. {@code deed_id} keeps its meaning for every existing reader;
     * {@code instance_uuid} is added beside it (playbook D-LIFE-04) so a shard can tell one
     * physical delivery lifecycle from the permanent entitlement behind it.
     */
    private static ItemStack stamp(Item target, ServerPlayer player,
                                   BlessedItemSyncAPI.BlessedRow row, UUID instanceUuid) {
        ItemStack stack = new ItemStack(target);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("blessed", true);
        tag.putString("owner", player.getStringUUID());
        tag.putString("deed_id", row.deedId());
        tag.putString("instance_uuid", instanceUuid.toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /**
     * {@code active}: Rails has already been told this exists. Create nothing.
     *
     * <p>The scan below is a DIAGNOSTIC and nothing else. It never creates an item, and its
     * silence proves nothing: an active medallion is perfectly at home in a chest, a bank, a
     * display case or an ender chest, none of which it looks in. Its only job is to make a
     * genuinely lost item visible in the log so an operator can decide -- restoration is Rails'
     * call, through a fresh pending materialization.
     */
    private static void noteAlreadyDelivered(ServerPlayer player,
                                             BlessedItemSyncAPI.BlessedRow row, UUID instanceUuid) {
        if (!visibleInCarriedInventory(player, instanceUuid)) {
            LOGGER.warn("Blessed instance={} is active for player={} item={} but is not visible in "
                    + "their carried inventory. This is NOT an error and NOTHING is being "
                    + "replaced -- it is equally consistent with the item being stored in a "
                    + "chest, bank, display case or ender chest, none of which are inspected.",
                    instanceUuid, player.getUUID(), row.itemName());
        }
    }

    /**
     * Main inventory plus offhand, for logging only. Deliberately not a container, world or
     * ender-chest search: widening the scan would rebuild the architecture this milestone
     * removed, at greater cost and with the same false confidence.
     */
    private static boolean visibleInCarriedInventory(ServerPlayer player, UUID instanceUuid) {
        Inventory inventory = player.getInventory();
        return carries(inventory.items, instanceUuid) || carries(inventory.offhand, instanceUuid);
    }

    private static boolean carries(List<ItemStack> slots, UUID instanceUuid) {
        String wanted = instanceUuid.toString();
        for (ItemStack stack : slots) {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) continue;
            if (wanted.equals(data.copyTag().getString("instance_uuid"))) return true;
        }
        return false;
    }

    /**
     * Two distinct failures, distinguished on purpose.
     *
     * <p>A malformed identifier used to throw {@link ResourceLocation}'s own exception out of the
     * whole batch. A well-formed but UNREGISTERED one was worse: the item registry is defaulted,
     * so it quietly resolved to {@code minecraft:air}, produced an empty stack that
     * {@code Inventory.add} refused, and vanished with no throw and no log line at all.
     *
     * @return the resolved item, or null after logging why not
     */
    private static Item resolveItem(BlessedItemSyncAPI.BlessedRow row, ServerPlayer player) {
        ResourceLocation id = ResourceLocation.tryParse(row.itemName());
        if (id == null) {
            LOGGER.error("Blessed row names a malformed item id={} player={} deed={} -- skipped",
                    row.itemName(), player.getUUID(), row.deedId());
            return null;
        }

        // getHolder, not get: `get` on a defaulted registry answers air for anything it does not
        // know, which is how an unknown id used to become a silent non-delivery.
        Optional<Holder.Reference<Item>> holder = BuiltInRegistries.ITEM.getHolder(id);
        if (holder.isEmpty()) {
            LOGGER.error("Blessed row names an unregistered item id={} player={} deed={} -- "
                    + "skipped (no air is delivered)", id, player.getUUID(), row.deedId());
            return null;
        }
        return holder.get().value();
    }

    /**
     * Off the server thread, because it is network I/O. The item already exists by now, so a
     * failure here delays the acknowledgement and nothing else -- the durable receipt is what
     * replays it, on the player's next sync.
     */
    private static void reportDelivered(ServerPlayer player, UUID instanceUuid) {
        UUID owner = player.getUUID();
        ServerHttpExecutor.submit(player.server,
                () -> BlessedDeliveryReportClient.reportDelivered(player.server, instanceUuid, owner))
            .whenComplete((outcome, error) -> {
                if (error != null) {
                    LOGGER.warn("Blessed delivery report failed instance={}; will replay", instanceUuid, error);
                    return;
                }
                if (outcome instanceof BlessedDeliveryReportClient.Outcome.Accepted accepted) {
                    LOGGER.info("Rails recorded blessed delivery instance={} duplicate={}",
                            instanceUuid, accepted.duplicate());
                }
                // Conflict/NotFound/Retryable are already logged by the client with their
                // reasons. None of them causes a re-delivery: the receipt says the item exists.
            });
    }
}
