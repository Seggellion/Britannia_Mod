package com.seggellion.britannia_mod.worldstate;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class WorldStateSyncValidatorTest {
    private static final String SHARD_A = "11111111-1111-4111-8111-111111111111";
    private static final String SHARD_B = "22222222-2222-4222-8222-222222222222";

    @Test
    void acceptsAWellFormedResponseMatchingWhatWasRequested() {
        WorldStateChangesResponse response = response(SHARD_A, 0, 2, 2, false, List.of(
                change(1), change(2)
        ));

        WorldStateSyncValidator.Result result = WorldStateSyncValidator.validate(response, 0, null);

        WorldStateSyncValidator.Accepted accepted = assertInstanceOf(WorldStateSyncValidator.Accepted.class, result);
        assertEquals(response, accepted.response());
    }

    @Test
    void acceptsAFullBootstrapRequiredResponseWithoutTreatingItAsARejection() {
        WorldStateChangesResponse response = response(SHARD_A, 5, 5, 40, true, List.of());

        WorldStateSyncValidator.Result result = WorldStateSyncValidator.validate(response, 5, null);

        assertInstanceOf(WorldStateSyncValidator.Accepted.class, result);
    }

    @Test
    void rejectsAShardIdentityMismatchAgainstThePinnedShard() {
        WorldStateChangesResponse response = response(SHARD_B, 0, 0, 0, false, List.of());

        WorldStateSyncValidator.Result result = WorldStateSyncValidator.validate(response, 0, SHARD_A);

        WorldStateSyncValidator.Rejected rejected = assertInstanceOf(WorldStateSyncValidator.Rejected.class, result);
        assertEquals("shard_identity_mismatch", rejected.reason());
    }

    @Test
    void acceptsAMatchingShardIdentityAgainstThePinnedShard() {
        WorldStateChangesResponse response = response(SHARD_A, 0, 0, 0, false, List.of());

        WorldStateSyncValidator.Result result = WorldStateSyncValidator.validate(response, 0, SHARD_A);

        assertInstanceOf(WorldStateSyncValidator.Accepted.class, result);
    }

    @Test
    void rejectsAnUnsupportedSchemaVersion() {
        WorldStateChangesResponse response = new WorldStateChangesResponse(
                99, SHARD_A, 0, 0, 0, false, List.of()
        );

        WorldStateSyncValidator.Result result = WorldStateSyncValidator.validate(response, 0, null);

        WorldStateSyncValidator.Rejected rejected = assertInstanceOf(WorldStateSyncValidator.Rejected.class, result);
        assertEquals("unsupported_schema_version", rejected.reason());
    }

    @Test
    void rejectsAFromVersionThatDoesNotMatchWhatWasRequested() {
        WorldStateChangesResponse response = response(SHARD_A, 3, 3, 3, false, List.of());

        WorldStateSyncValidator.Result result = WorldStateSyncValidator.validate(response, 0, null);

        WorldStateSyncValidator.Rejected rejected = assertInstanceOf(WorldStateSyncValidator.Rejected.class, result);
        assertEquals("from_version_mismatch", rejected.reason());
    }

    @Test
    void rejectsAToVersionBelowFromVersion() {
        WorldStateChangesResponse response = response(SHARD_A, 5, 3, 5, false, List.of());

        WorldStateSyncValidator.Result result = WorldStateSyncValidator.validate(response, 5, null);

        WorldStateSyncValidator.Rejected rejected = assertInstanceOf(WorldStateSyncValidator.Rejected.class, result);
        assertEquals("to_version_before_from_version", rejected.reason());
    }

    @Test
    void rejectsChangesWithADuplicateVersionNumber() {
        WorldStateChangesResponse response = response(SHARD_A, 0, 2, 2, false, List.of(
                change(1), change(1)
        ));

        WorldStateSyncValidator.Result result = WorldStateSyncValidator.validate(response, 0, null);

        WorldStateSyncValidator.Rejected rejected = assertInstanceOf(WorldStateSyncValidator.Rejected.class, result);
        assertEquals("changes_not_strictly_increasing", rejected.reason());
    }

    @Test
    void rejectsChangesThatAreOutOfOrder() {
        WorldStateChangesResponse response = response(SHARD_A, 0, 2, 2, false, List.of(
                change(2), change(1)
        ));

        WorldStateSyncValidator.Result result = WorldStateSyncValidator.validate(response, 0, null);

        WorldStateSyncValidator.Rejected rejected = assertInstanceOf(WorldStateSyncValidator.Rejected.class, result);
        assertEquals("changes_not_strictly_increasing", rejected.reason());
    }

    private static WorldStateChangesResponse response(
            String shardPublicId, long fromVersion, long toVersion, long currentVersion,
            boolean fullBootstrapRequired, List<WorldStateChangeRecord> changes
    ) {
        return new WorldStateChangesResponse(
                WorldStateSyncValidator.SUPPORTED_SCHEMA_VERSION, shardPublicId, fromVersion, toVersion,
                currentVersion, fullBootstrapRequired, changes
        );
    }

    private static WorldStateChangeRecord change(long version) {
        return new WorldStateChangeRecord(
                version, "created", "npc_spawn_assignment", "aaaaaaaa-1111-4111-8111-111111111111",
                1, new JsonObject(), "2026-07-16T12:00:00.000000Z"
        );
    }
}
