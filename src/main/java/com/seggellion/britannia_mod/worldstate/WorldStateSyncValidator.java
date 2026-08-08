package com.seggellion.britannia_mod.worldstate;

import java.util.List;

/**
 * Validates an already-parsed {@link WorldStateChangesResponse} before anything downstream
 * ever sees it -- mirrors {@code ServiceNpcRegistryParser}'s schema_version rejection and
 * {@code WorldBootstrapHandler}'s isolated-degrade pattern (a rejection here never crashes the
 * poller, it just becomes a typed, logged outcome for the next scheduled poll to try again).
 *
 * Malformed JSON (missing/wrong-typed fields) is rejected earlier, during parsing, by {@link
 * WorldStateChangesResponseParser}; this class only ever sees a structurally well-formed
 * response and checks whether its *content* is trustworthy: the right shard, a schema version
 * this client understands, an honest echo of the range actually requested, and a changes array
 * that is internally consistent.
 */
public final class WorldStateSyncValidator {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    private WorldStateSyncValidator() {}

    public sealed interface Result permits Accepted, Rejected {}

    public record Accepted(WorldStateChangesResponse response) implements Result {}

    public record Rejected(String reason) implements Result {}

    /**
     * @param pinnedShardPublicId the shard_public_id observed on this poller's first accepted
     *                            response, or null if none has been accepted yet. Not persisted
     *                            across restarts -- see WorldStateSyncPoller's own docs.
     */
    public static Result validate(
            WorldStateChangesResponse response, long requestedFromVersion, String pinnedShardPublicId
    ) {
        if (response.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            return new Rejected("unsupported_schema_version");
        }
        if (pinnedShardPublicId != null && !pinnedShardPublicId.equals(response.shardPublicId())) {
            return new Rejected("shard_identity_mismatch");
        }
        if (response.fromVersion() != requestedFromVersion) {
            return new Rejected("from_version_mismatch");
        }
        if (response.toVersion() < response.fromVersion()) {
            return new Rejected("to_version_before_from_version");
        }
        if (!versionsAreStrictlyIncreasing(response.changes())) {
            return new Rejected("changes_not_strictly_increasing");
        }
        return new Accepted(response);
    }

    private static boolean versionsAreStrictlyIncreasing(List<WorldStateChangeRecord> changes) {
        long previous = Long.MIN_VALUE;
        boolean first = true;
        for (WorldStateChangeRecord change : changes) {
            if (!first && change.version() <= previous) return false;
            previous = change.version();
            first = false;
        }
        return true;
    }
}
