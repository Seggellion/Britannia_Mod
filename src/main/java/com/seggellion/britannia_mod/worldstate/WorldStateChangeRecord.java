package com.seggellion.britannia_mod.worldstate;

import com.google.gson.JsonObject;

/** One entry from {@code GET /api/world_state_changes/:shard}'s {@code changes} array. */
public record WorldStateChangeRecord(
        long version,
        String changeType,
        String resourceType,
        String resourceId,
        long resourceRevision,
        JsonObject payload,
        String createdAt
) {}
