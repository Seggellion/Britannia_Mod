package com.seggellion.britannia_mod.resource.deposit;

import com.seggellion.britannia_mod.resource.placement.MaterializationService;
import com.seggellion.britannia_mod.resource.placement.PlannedDeposit;
import com.seggellion.britannia_mod.resource.shape.ShapeBounds;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The join between a plan and the ledger.
 *
 * <p>Milestone 3 could plan a deposit and write it, but nothing remembered that it had. This turns
 * a {@link PlannedDeposit} into a {@link DepositInstance}, registers it, and — after the fact —
 * records what a complete materialisation pass established about it.
 *
 * <p>Kept deliberately thin. It owns no policy: the identity comes from {@link DepositIdentity},
 * the registration decision from {@link DepositLedger}, and what may actually be written from
 * {@link MaterializationService}.
 */
public final class DepositRegistrar {

    private DepositRegistrar() {
    }

    /** Describe a planned deposit as a ledger instance, ready to register. */
    public static DepositInstance describe(
            PlannedDeposit deposit, long instanceId, DepositSource source, String sourceIdentity) {

        ShapeBounds bounds = deposit.plan().bounds();
        BlockPos origin = deposit.origin();
        return new DepositInstance(
                instanceId,
                deposit.resource().id(),
                deposit.resource().revision(),
                source,
                sourceIdentity,
                origin,
                deposit.config().seed(),
                deposit.config().radius(),
                deposit.config().rotation(),
                origin.offset(bounds.minX(), bounds.minY(), bounds.minZ()),
                origin.offset(bounds.maxX(), bounds.maxY(), bounds.maxZ()),
                deposit.count(),
                DepositInstance.UNKNOWN_COUNT,
                DepositInstance.UNKNOWN_COUNT,
                DepositInstance.CURRENT_MATERIALIZATION_VERSION);
    }

    /**
     * Record what a materialisation pass established, but only when it established anything.
     *
     * <p>A pass that hit its work budget has examined part of the plan, so its numbers describe a
     * prefix rather than the deposit. Writing them down would turn "we have not finished looking"
     * into "this is how much is blocked", which is a lie the ledger would then keep. Progress is
     * left unknown until a pass covers the whole plan.
     *
     * <p>After a complete pass the two counts partition the plan exactly:
     * {@code materialized + blocked == planned}. Cells that already held the resource count as
     * materialised — they do — and every other refusal counts as blocked.
     */
    public static void recordCompletePass(
            ServerLevel level, long instanceId, MaterializationService.Result result, int plannedCells) {

        if (result.truncated()) {
            return;
        }
        int alreadyPresent = result.rejected(MaterializationService.Rejection.ALREADY_PRESENT);
        int materialized = result.placed() + alreadyPresent;
        int blocked = plannedCells - materialized;
        DepositLedger.get(level).recordProgress(instanceId, materialized, Math.max(0, blocked));
    }

    /**
     * How many of this deposit's cells are currently worked out.
     *
     * <p>Counted from the restoration store across the chunks the deposit's own bounds touch, which
     * is bounded by the deposit rather than by the world's debt count. Deliberately not a persisted
     * counter: one would need decrementing on extraction, restoration, admin removal and migration,
     * and would be wrong the first time one of those was missed.
     */
    public static int depletedCells(ServerLevel level, DepositInstance instance) {
        var storage = com.seggellion.britannia_mod.blockrestore.BrokenBlockDataStorage.get(level);
        int depleted = 0;
        for (var chunk : instance.touchedChunks()) {
            for (var debt : storage.debtsIn(chunk)) {
                if (debt.instanceId == instance.instanceId()) {
                    depleted++;
                }
            }
        }
        return depleted;
    }
}
