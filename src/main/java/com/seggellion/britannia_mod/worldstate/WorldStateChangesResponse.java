package com.seggellion.britannia_mod.worldstate;

import java.util.List;

/** Parsed (but not yet validated) body of {@code GET /api/world_state_changes/:shard}. */
public record WorldStateChangesResponse(
        int schemaVersion,
        String shardPublicId,
        long fromVersion,
        long toVersion,
        long currentVersion,
        boolean fullBootstrapRequired,
        List<WorldStateChangeRecord> changes
) {}
