package com.seggellion.britannia_mod.economy;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import org.slf4j.Logger;

import java.util.List;

/**
 * Vendor/Trader Milestone 19.5: returns items stranded by a crash during a
 * trader sale.
 *
 * <p>A refund needs the player's inventory, so recovery runs on login (the same
 * real event {@code BankTransferReconciliationService} uses), with a startup
 * scan that only reports — it never mints items into an offline player.
 *
 * <h2>What is refunded, and what deliberately is not</h2>
 * Only a receipt whose status proves the items durably left the player
 * ({@code ITEMS_REMOVED} / {@code DISPATCHED}) is refunded. A receipt still in
 * {@code RESERVED} means the removal never became durable — the player's saved
 * inventory still holds those items — so it is resolved WITHOUT a refund. That
 * asymmetry is deliberate: an unrecoverable ambiguity must never mint currency
 * or goods, because duplication is unbounded inflation while the alternative is
 * bounded and visible.
 *
 * <h2>Why a DISPATCHED receipt is still refunded</h2>
 * The sale may in fact have committed in Rails before the crash, in which case
 * the player keeps both the payout and the goods. That is accepted knowingly:
 * the sale's idempotency key makes the ledger side exact, this window requires
 * a crash inside a single in-flight HTTP call, and the alternative — destroying
 * a player's items on the chance that a request we never saw an answer to
 * succeeded — is the worse failure. Every such refund is logged with its key so
 * an operator can reconcile against the Rails ledger.
 */
public final class TraderSaleReservationRecovery {
    private static final Logger LOGGER = LogUtils.getLogger();

    private TraderSaleReservationRecovery() {
    }

    /** Wired from mod setup, mirroring the banking reconciliation service. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(TraderSaleReservationRecovery::onLogin);
    }

    private static void onLogin(PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        refundStrandedReservations(level, player);
    }

    /** Public for GameTest access, following this codebase's established pattern. */
    public static int refundStrandedReservations(ServerLevel level, ServerPlayer player) {
        TraderSaleReservationStore store = TraderSaleReservationStore.get(level);
        List<TraderSaleReservationReceipt> receipts = store.forPlayer(player.getUUID());
        if (receipts.isEmpty()) return 0;

        int refunded = 0;
        for (TraderSaleReservationReceipt receipt : receipts) {
            if (!receipt.refundable()) {
                LOGGER.info(
                        "Trader sale reservation {} for {} was never durably removed; resolving without refund",
                        receipt.idempotencyKey(), player.getGameProfile().getName());
                store.resolve(receipt.idempotencyKey());
                continue;
            }

            List<ItemStack> stacks = receipt.decodeItems(level.registryAccess());
            if (receipt.hasUnreadableItems(level.registryAccess())) {
                LOGGER.error(
                        "Trader sale reservation {} has unreadable item payloads; refunding only the readable ones",
                        receipt.idempotencyKey());
            }
            for (ItemStack stack : stacks) {
                ItemStack refund = stack.copy();
                if (!player.getInventory().add(refund)) {
                    player.drop(refund, false);
                }
            }
            LOGGER.warn(
                    "Refunded {} stranded trader sale item stack(s) to {} from reservation {} (status {}) "
                            + "-- reconcile against the Rails ledger for this idempotency key",
                    stacks.size(), player.getGameProfile().getName(),
                    receipt.idempotencyKey(), receipt.status());
            store.resolve(receipt.idempotencyKey());
            refunded += stacks.size();
        }

        if (refunded > 0) {
            player.inventoryMenu.broadcastChanges();
            player.inventoryMenu.broadcastFullState();
        }
        store.flush(level);
        return refunded;
    }

    /** Startup visibility only: never mints items into an offline player. */
    public static void reportStrandedReservations(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        TraderSaleReservationStore store = TraderSaleReservationStore.get(overworld);
        List<TraderSaleReservationReceipt> receipts = store.snapshot();
        if (receipts.isEmpty() && store.unreadableCount() == 0) return;

        LOGGER.warn("Trader sale reservations awaiting recovery: {} (unreadable entries: {})",
                receipts.size(), store.unreadableCount());
        for (TraderSaleReservationReceipt receipt : receipts) {
            LOGGER.warn("  reservation {} player={} status={} stacks={} -- refunds on that player's next login",
                    receipt.idempotencyKey(), receipt.playerUuid(),
                    receipt.status(), receipt.itemPayloads().size());
        }
    }
}
