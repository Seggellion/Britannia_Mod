package com.seggellion.britannia_mod.economy;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Recover modern sales by receipt. Retain legacy unknown dispatches for ledger reconciliation. */
public final class TraderSaleReservationRecovery {
    private TraderSaleReservationRecovery() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TraderSaleReservationRecovery::onLogin);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> TraderSaleSettlementService.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> TraderSaleSettlementService.stopped(event.getServer()));
    }

    private static void onLogin(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level)
            refundStrandedReservations(level, player);
    }

    /** Returns complete legacy stacks delivered, retained for existing callers. */
    public static int refundStrandedReservations(ServerLevel level, ServerPlayer player) {
        var store = TraderSaleReservationStore.get(level);
        int refunded = 0;
        for (var receipt : store.forPlayer(player.getUUID())) {
            if (receipt.replayable()) continue;
            switch (receipt.status()) {
                case RESERVED -> {
                    store.resolve(receipt.idempotencyKey());
                    if (!store.flush(level)) store.record(receipt);
                }
                case ITEMS_REMOVED, REFUND_PENDING -> {
                    store.settlement(receipt.idempotencyKey(), TraderSaleReservationReceipt.Status.REFUND_PENDING, "{}");
                    if (store.flush(level) && TraderSaleSettlementService.deliver(level, player, store.find(receipt.idempotencyKey()), true))
                        refunded += receipt.itemPayloads().size();
                }
                default -> LogUtils.getLogger().warn("Legacy sale {} has an unknown outcome; retained for ledger reconciliation", receipt.idempotencyKey());
            }
        }
        TraderSaleSettlementService.recover(level, player);
        return refunded;
    }

    public static void reportStrandedReservations(MinecraftServer server) {
        var store = TraderSaleReservationStore.get(server.overworld());
        if (store.snapshot().isEmpty() && store.unreadableCount() == 0) return;
        LogUtils.getLogger().warn("Trader sales awaiting receipt resolution/delivery: {} (unreadable: {})",
                store.snapshot().size(), store.unreadableCount());
        for (var receipt : store.snapshot()) LogUtils.getLogger().warn("  sale {} player={} status={} replayable={}",
                receipt.idempotencyKey(), receipt.playerUuid(), receipt.status(), receipt.replayable());
    }
}
