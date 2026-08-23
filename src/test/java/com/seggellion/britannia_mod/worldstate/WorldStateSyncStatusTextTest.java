package com.seggellion.britannia_mod.worldstate;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The panel line an admin reads at the post. Plain JUnit, no server and no screen -- the whole
 * point of formatting server-side is that this stays a pure function of an outcome.
 */
class WorldStateSyncStatusTextTest {
    @Test
    void anAppliedDeltaNamesTheVersionItReached() {
        assertEquals(
                "World sync: OK at v42",
                WorldStateSyncStatusText.of(WorldStateSyncOutcome.accepted(
                        new WorldStateChangesResponse(1, "shard", 40L, 42L, 42L, false, List.of())))
        );
    }

    @Test
    void aRecoveredFullBootstrapReadsDifferentlyFromAnOrdinaryDelta() {
        // Worth distinguishing: it means this server had fallen far enough behind that Rails
        // refused to catch it up incrementally.
        assertEquals(
                "World sync: refreshed at v7",
                WorldStateSyncStatusText.of(WorldStateSyncOutcome.fullBootstrapApplied(7L))
        );
    }

    @Test
    void aPollerThatHasNeverRunSaysSoRatherThanClaimingHealth() {
        assertEquals("World sync: not run yet", WorldStateSyncStatusText.of(WorldStateSyncOutcome.neverPolled()));
        assertEquals("World sync: unavailable", WorldStateSyncStatusText.of(null));
    }

    @Test
    void everyFailureShapeIsLabelledAsAFailure() {
        assertEquals("World sync FAILED: shard_identity_mismatch",
                WorldStateSyncStatusText.of(WorldStateSyncOutcome.rejected("shard_identity_mismatch")));
        assertEquals("World sync FAILED: malformed_change: x must be an int",
                WorldStateSyncStatusText.of(WorldStateSyncOutcome.applyRejected("malformed_change: x must be an int")));
        assertEquals("World sync FAILED: transport_error",
                WorldStateSyncStatusText.of(WorldStateSyncOutcome.transportFailure("transport_error")));
        assertEquals("World sync deferred: no_player_online",
                WorldStateSyncStatusText.of(WorldStateSyncOutcome.fullBootstrapDeferred("no_player_online")));
    }

    @Test
    void aReasonCarryingUuidsIsTruncatedButTheLabelSurvivesIntact() {
        // The real one: "assignment <uuid> references missing spawn point <uuid>". Wider than the
        // panel and useless at a glance -- the identifiers are in the log, the kind is what the
        // operator needs here.
        String reason = "assignment 1fd4c455-cf3e-44d9-9d13-0c431577fb52 references missing spawn point";

        String line = WorldStateSyncStatusText.of(WorldStateSyncOutcome.applyRejected(reason));

        assertTrue(line.startsWith("World sync FAILED: "), "truncation ate the label: " + line);
        assertTrue(line.endsWith("…"), "a truncated reason must say it was truncated: " + line);
        assertEquals("World sync FAILED: ".length() + WorldStateSyncStatusText.MAX_REASON_CHARS, line.length());
    }

    @Test
    void anEmptyReasonStillProducesSomethingReadable() {
        assertEquals("World sync FAILED: unknown", WorldStateSyncStatusText.of(WorldStateSyncOutcome.rejected("")));
    }

    @Test
    void onlyALandedPollCountsAsHealthy() {
        assertTrue(WorldStateSyncStatusText.isHealthy(WorldStateSyncOutcome.accepted(
                new WorldStateChangesResponse(1, "shard", 0L, 1L, 1L, false, List.of()))));
        assertTrue(WorldStateSyncStatusText.isHealthy(WorldStateSyncOutcome.fullBootstrapApplied(1L)));
        assertFalse(WorldStateSyncStatusText.isHealthy(WorldStateSyncOutcome.neverPolled()));
        assertFalse(WorldStateSyncStatusText.isHealthy(WorldStateSyncOutcome.rejected("x")));
        assertFalse(WorldStateSyncStatusText.isHealthy(WorldStateSyncOutcome.applyRejected("x")));
        assertFalse(WorldStateSyncStatusText.isHealthy(WorldStateSyncOutcome.transportFailure("x")));
        assertFalse(WorldStateSyncStatusText.isHealthy(WorldStateSyncOutcome.fullBootstrapDeferred("x")));
        assertFalse(WorldStateSyncStatusText.isHealthy(null));
    }
}
