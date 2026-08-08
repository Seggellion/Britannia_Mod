package com.seggellion.britannia_mod.service.spawn;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class ServiceNpcSpawnRequestSerializer {
    private static final Gson GSON = new Gson();
    private ServiceNpcSpawnRequestSerializer() {}

    public static byte[] serialize(ServiceNpcSpawnOperationRequest request) {
        Objects.requireNonNull(request, "request");
        JsonObject root = new JsonObject();
        root.addProperty("protocol_version", request.protocolVersion());
        root.addProperty("operation_id", request.operationId().toString());
        root.addProperty("operation", request.operation().name());
        root.addProperty("spawn_uuid", request.spawnUuid().toString());
        root.addProperty("source_revision", request.sourceRevision());
        JsonObject location = new JsonObject();
        location.addProperty("world_name", request.worldName());
        location.addProperty("dimension", request.dimension().toString());
        location.addProperty("x", request.x());
        location.addProperty("y", request.y());
        location.addProperty("z", request.z());
        root.add("location", location);
        if (request.cityPublicId() != null) {
            root.addProperty("city_public_id", request.cityPublicId().toString());
        }
        if (request.serviceNpcTypeKey() != null) {
            root.addProperty("service_npc_type_key", request.serviceNpcTypeKey());
        }
        if (request.enabled() != null) root.addProperty("enabled", request.enabled());
        byte[] body = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
        if (body.length > ServiceNpcSpawnOperationRequest.MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("request_too_large");
        }
        return body;
    }
}
