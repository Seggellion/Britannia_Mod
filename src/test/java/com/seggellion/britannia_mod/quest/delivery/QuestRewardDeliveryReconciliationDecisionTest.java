package com.seggellion.britannia_mod.quest.delivery;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static com.seggellion.britannia_mod.quest.delivery.QuestRewardDeliveryReconciliationDecision.Action;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Rowan farming questline M3: the restart-reconciliation table of protocol section 1.8 and the
 * acknowledgement schedule, as pure functions.
 */
class QuestRewardDeliveryReconciliationDecisionTest {

    @TestFactory
    Stream<DynamicTest> theDecisionTable() {
        Map<String, Object[]> rows = new LinkedHashMap<>();
        // ledger state, marker present -> action
        rows.put("no row, no marker -> record pending_local then insert", new Object[]{null, false, Action.RECORD_AND_INSERT});
        rows.put("no row, marker -> ledger lost the row; record applied, acknowledge", new Object[]{null, true, Action.RECORD_APPLIED_THEN_ACKNOWLEDGE});
        rows.put("pending_local, no marker -> crash before or during the insertion; insert", new Object[]{pendingLocal(), false, Action.INSERT});
        rows.put("pending_local, marker -> crash after the player save; repair to applied", new Object[]{pendingLocal(), true, Action.REPAIR_APPLIED_THEN_ACKNOWLEDGE});
        rows.put("queued, no marker -> retry the insertion", new Object[]{queued(), false, Action.INSERT});
        rows.put("queued (Rails holds queued), no marker -> retry the insertion", new Object[]{queuedAcknowledged(), false, Action.INSERT});
        rows.put("queued, marker -> the retry landed but the ledger did not; repair", new Object[]{queued(), true, Action.REPAIR_APPLIED_THEN_ACKNOWLEDGE});
        rows.put("applied, any marker -> acknowledge only", new Object[]{applied(), false, Action.ACKNOWLEDGE_ONLY});
        rows.put("applied, marker -> acknowledge only", new Object[]{applied(), true, Action.ACKNOWLEDGE_ONLY});
        rows.put("applied with terminal error -> nothing", new Object[]{applied().withTerminalError("delivery_not_found", 9L), true, Action.NOTHING});
        rows.put("acknowledged -> nothing", new Object[]{acknowledged(), true, Action.NOTHING});
        rows.put("acknowledged, no marker (marker list bounded away) -> nothing", new Object[]{acknowledged(), false, Action.NOTHING});

        return rows.entrySet().stream().map(row -> DynamicTest.dynamicTest(row.getKey(), () -> {
            QuestRewardDeliveryLedgerEntry entry = (QuestRewardDeliveryLedgerEntry) row.getValue()[0];
            boolean marker = (Boolean) row.getValue()[1];
            assertEquals(row.getValue()[2], QuestRewardDeliveryReconciliationDecision.decide(entry, marker));
        }));
    }

    @Test
    void theAcknowledgementBackoffDoublesFromSixtySecondsAndCapsAtTenMinutes() {
        assertEquals(60_000L, QuestRewardDeliveryBackoff.delayMillis(0));
        assertEquals(60_000L, QuestRewardDeliveryBackoff.delayMillis(1));
        assertEquals(120_000L, QuestRewardDeliveryBackoff.delayMillis(2));
        assertEquals(240_000L, QuestRewardDeliveryBackoff.delayMillis(3));
        assertEquals(480_000L, QuestRewardDeliveryBackoff.delayMillis(4));
        assertEquals(600_000L, QuestRewardDeliveryBackoff.delayMillis(5));
        assertEquals(600_000L, QuestRewardDeliveryBackoff.delayMillis(50));
        assertEquals(30_000L, QuestRewardDeliveryBackoff.QUEUED_RETRY_MILLIS);
    }

    private static QuestRewardDeliveryLedgerEntry pendingLocal() {
        QuestRewardDelivery delivery = new QuestRewardDelivery(UUID.randomUUID(), 41L, "9001", "k",
            List.of(new QuestRewardDeliveryItem("britannia_mod:gold_coin", 2, false)), "pending", "");
        return QuestRewardDeliveryLedgerEntry.pendingLocal(delivery, UUID.randomUUID(), 1L);
    }

    private static QuestRewardDeliveryLedgerEntry queued() {
        return pendingLocal().withQueued(2L);
    }

    private static QuestRewardDeliveryLedgerEntry queuedAcknowledged() {
        return queued().withAcknowledged(QuestRewardDeliveryProtocol.Outcome.QUEUED, 3L);
    }

    private static QuestRewardDeliveryLedgerEntry applied() {
        return pendingLocal().withApplied(2L);
    }

    private static QuestRewardDeliveryLedgerEntry acknowledged() {
        return applied().withAcknowledged(QuestRewardDeliveryProtocol.Outcome.APPLIED, 3L);
    }
}
