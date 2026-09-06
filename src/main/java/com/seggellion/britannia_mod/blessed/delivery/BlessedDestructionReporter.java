package com.seggellion.britannia_mod.blessed.delivery;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.server.http.ServerHttpExecutor;

import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * The one way a materialization is positively declared gone.
 *
 * <h2>What "positively" means</h2>
 * A destruction report asserts that this specific {@code instance_uuid} no longer exists. It is
 * never sent because an item could not be found: an item may be in a chest, a bank, a display
 * case, an ender chest or another player's pocket, none of which this shard inspects. Absence is
 * not destruction (playbook invariant C, D-LIFE-03).
 *
 * <h2>Why nothing in M7 calls this automatically</h2>
 * M7 is rescue-first, and rescue succeeded everywhere it was applied: despawn, lava, fire, the
 * void and the Trash Barrel all end with the item still in the world, so there is nothing to
 * report. That leaves this path with exactly one intended caller -- the audited operator recovery
 * action M9 builds, for the exceptional losses the engine does not let anyone observe.
 *
 * <p>Building the capability now, and testing it, is deliberate: it means M9 wires an operator
 * button to a proven, idempotent, crash-tolerant path rather than inventing one under time
 * pressure.
 *
 * <h2>Ordering</h2>
 * The local receipt moves to DESTROYED <em>before</em> Rails is told, and durably. That ordering
 * is what stops a stale delivery replay resurrecting an instance that is known to be gone: after
 * this returns, no amount of retrying can make the sync deliver {@code instance_uuid} again, even
 * if Rails is unreachable for a week. Rails restores by minting a NEW pending materialization
 * with a NEW identity, which the ordinary delivery path then handles.
 */
public final class BlessedDestructionReporter {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BlessedDestructionReporter() {
    }

    /**
     * Records the destruction locally and reports it to Rails.
     *
     * <p>Safe to call twice: the receipt transition is idempotent and so is the Rails endpoint.
     *
     * @param reason a short, safe, human-meaningful cause for the log -- see
     *               {@link BlessedDestructionReason}
     * @return true when the local state moved or was already terminal, false when there is no
     *         such receipt to destroy
     */
    public static boolean reportDestroyed(ServerLevel level, UUID instanceUuid, UUID ownerUuid,
                                          BlessedDestructionReason reason) {
        BlessedDeliveryReceiptStore.MarkDestroyedOutcome outcome =
                BlessedDeliveryReceipts.markDestroyed(level, instanceUuid);

        switch (outcome) {
            case NOT_FOUND -> {
                // Nothing local claims this instance was ever delivered here. Reporting its
                // destruction would be asserting something this shard cannot know.
                LOGGER.warn("Refusing to report destruction of instance={} reason={} -- no local "
                        + "delivery receipt exists for it", instanceUuid, reason.wireReason());
                return false;
            }
            case READ_ONLY_SCHEMA -> {
                LOGGER.error("Cannot record destruction of instance={} -- the receipt store is "
                        + "read-only (newer on-disk schema)", instanceUuid);
                return false;
            }
            case ALREADY_DESTROYED -> LOGGER.info(
                    "Destruction of instance={} already recorded; replaying the Rails report",
                    instanceUuid);
            case MARKED -> LOGGER.warn("Blessed instance={} destroyed reason={} owner={} -- "
                    + "reporting to Rails", instanceUuid, reason.wireReason(), ownerUuid);
        }

        // Off-thread: the local terminal state is already durable, so a failure here delays the
        // report and nothing else. It is replayed on the owner's next blessed sync.
        ServerHttpExecutor.submit(level.getServer(),
                () -> BlessedDeliveryReportClient.reportDestroyed(
                        level.getServer(), instanceUuid, ownerUuid))
            .whenComplete((result, error) -> {
                if (error != null) {
                    LOGGER.warn("Destruction report for instance={} could not be sent; the local "
                            + "receipt keeps it for replay", instanceUuid, error);
                    return;
                }
                if (result instanceof BlessedDeliveryReportClient.Outcome.Accepted accepted) {
                    LOGGER.info("Rails recorded destruction of instance={} duplicate={}",
                            instanceUuid, accepted.duplicate());
                }
                // Conflict and NotFound are logged by the client. Neither re-materializes
                // anything: the local receipt is terminal either way.
            });

        return true;
    }
}
