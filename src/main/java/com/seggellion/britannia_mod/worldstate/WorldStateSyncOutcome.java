package com.seggellion.britannia_mod.worldstate;

/**
 * The result of the poller's most recent poll, held only in memory for this slice's own
 * observability and tests. Never applied to any cache -- Slice 1's whole point is to stop
 * exactly here. Deliberately not persisted: a restart naturally forgets it, matching the fact
 * that {@link WorldStateSyncPoller}'s locally-tracked version isn't persisted either yet.
 */
public sealed interface WorldStateSyncOutcome
        permits WorldStateSyncOutcome.NeverPolled, WorldStateSyncOutcome.Accepted,
        WorldStateSyncOutcome.Rejected, WorldStateSyncOutcome.TransportFailure {

    record NeverPolled() implements WorldStateSyncOutcome {}

    record Accepted(WorldStateChangesResponse response) implements WorldStateSyncOutcome {}

    record Rejected(String reason) implements WorldStateSyncOutcome {}

    record TransportFailure(String safeCode) implements WorldStateSyncOutcome {}

    static WorldStateSyncOutcome neverPolled() {
        return new NeverPolled();
    }

    static WorldStateSyncOutcome accepted(WorldStateChangesResponse response) {
        return new Accepted(response);
    }

    static WorldStateSyncOutcome rejected(String reason) {
        return new Rejected(reason);
    }

    static WorldStateSyncOutcome transportFailure(String safeCode) {
        return new TransportFailure(safeCode);
    }
}
