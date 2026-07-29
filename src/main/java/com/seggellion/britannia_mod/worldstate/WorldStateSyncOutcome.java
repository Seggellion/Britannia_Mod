package com.seggellion.britannia_mod.worldstate;

/**
 * The result of the poller's most recent poll, held only in memory for observability and tests
 * -- never itself the durable record of anything (the durable state lives in {@code
 * ServiceNpcAssignmentsCache} once {@link Accepted} actually commits).
 *
 * Milestone 13 NeoForge Slice 2 changes what {@link Accepted} means: in Slice 1 it meant
 * "validated, then discarded" (that slice's whole point was to stop there). As of Slice 2 it
 * means "validated, applied to a candidate cache snapshot, and durably committed" -- see {@link
 * ServiceNpcAssignmentsCandidateApply} and {@code ServiceNpcAssignmentsCache} for the mechanism.
 *
 * {@link Rejected} is Slice 1's own validation layer (wrong shard, unsupported schema, version
 * mismatches, non-monotonic changes) rejecting the response before this slice's apply logic ever
 * sees it -- unchanged from Slice 1. {@link ApplyRejected} is new in Slice 2: a response that
 * passed Slice 1's validation but whose batch could not be turned into a valid candidate (a
 * malformed per-resource payload Slice 1's own generic validation does not check, or a candidate
 * that fails its post-apply referential-integrity sanity check). Both {@link Rejected} and {@link
 * ApplyRejected} carry the same guarantee: the live cache is left completely untouched, never
 * partially applied, and never discarded to empty -- that discard-to-empty policy is reserved
 * for a genuinely unreadable on-disk file at load time, a different, older, and unchanged
 * situation (see {@code ServiceNpcAssignmentsCache}'s own docs for all three).
 */
public sealed interface WorldStateSyncOutcome
        permits WorldStateSyncOutcome.NeverPolled, WorldStateSyncOutcome.Accepted,
        WorldStateSyncOutcome.Rejected, WorldStateSyncOutcome.ApplyRejected, WorldStateSyncOutcome.TransportFailure {

    record NeverPolled() implements WorldStateSyncOutcome {}

    record Accepted(WorldStateChangesResponse response) implements WorldStateSyncOutcome {}

    record Rejected(String reason) implements WorldStateSyncOutcome {}

    record ApplyRejected(String reason) implements WorldStateSyncOutcome {}

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

    static WorldStateSyncOutcome applyRejected(String reason) {
        return new ApplyRejected(reason);
    }

    static WorldStateSyncOutcome transportFailure(String safeCode) {
        return new TransportFailure(safeCode);
    }
}
