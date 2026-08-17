package com.seggellion.britannia_mod.service.spawn;

/** Closed mapping from typed results to safe persisted identifiers. */
public final class ServiceNpcSpawnFailureCodes {
    public static final String AUTHENTICATION_BLOCKED = "authentication_blocked";
    public static final String SHARD_MISMATCH = "shard_mismatch";
    public static final String UUID_COLLISION_PENDING_REPAIR = "uuid_collision_pending_repair";
    public static final String INVALID_LOCAL_OPERATION = "invalid_local_operation";
    public static final String UUID_COLLISION_SAME_LOCATION = "uuid_collision_same_location";
    public static final String UUID_COLLISION_REPAIR_EXHAUSTED = "uuid_collision_repair_exhausted";
    public static final String UUID_GENERATION_EXHAUSTED = "uuid_generation_exhausted";
    public static final String COLLISION_REPAIR_BLOCK_MISSING = "collision_repair_block_missing";
    public static final String COLLISION_REPAIR_BLOCK_MISMATCH = "collision_repair_block_mismatch";
    public static final String COLLISION_REPAIR_CONFIGURATION_MISMATCH =
        "collision_repair_configuration_mismatch";
    public static final String COLLISION_REPAIR_CLAIM_CONFLICT = "collision_repair_claim_conflict";
    public static final String COLLISION_REPAIR_INVALID_EVIDENCE = "collision_repair_invalid_evidence";
    public static final String COLLISION_REPAIR_STAGED_STATE_INVALID =
        "collision_repair_staged_state_invalid";
    public static final String COLLISION_REPAIR_FAILED = "collision_repair_failed";

    private ServiceNpcSpawnFailureCodes() {}

    public static String from(ServiceNpcSpawnClientResult result) {
        if (result instanceof ServiceNpcSpawnClientResult.Protocol protocol) {
            return switch (protocol.response().outcome()) {
                case UNAUTHORIZED -> AUTHENTICATION_BLOCKED;
                case SERVER_NOT_AUTHORIZED -> "server_not_authorized";
                case INVALID_CITY -> "invalid_city";
                case INVALID_SERVICE_NPC_TYPE -> "invalid_service_npc_type";
                case SERVICE_NPC_TYPE_INACTIVE -> "service_npc_type_inactive";
                case SERVICE_NPC_TYPE_NOT_SPAWNABLE -> "service_npc_type_not_spawnable";
                // Distinct from their service_npc counterparts on purpose: these name the
                // EconomicNpcType row an operator has to activate, and the durable record is
                // the only place that reason is ever visible.
                case INVALID_ECONOMIC_NPC_TYPE -> "invalid_economic_npc_type";
                case ECONOMIC_NPC_TYPE_INACTIVE -> "economic_npc_type_inactive";
                case ECONOMIC_NPC_TYPE_NOT_SPAWNABLE -> "economic_npc_type_not_spawnable";
                case AMBIGUOUS_NPC_TYPE -> "ambiguous_npc_type";
                case STALE_REVISION -> "stale_revision";
                case REVISION_CONFLICT -> "revision_conflict";
                case LOCATION_OCCUPIED -> "location_occupied";
                case UUID_COLLISION -> UUID_COLLISION_PENDING_REPAIR;
                case PAYLOAD_TOO_LARGE -> "payload_too_large";
                case SERVICE_UNAVAILABLE -> "service_unavailable";
                case UNSUPPORTED_PROTOCOL -> "protocol_incompatible";
                case APPLIED, ALREADY_APPLIED -> "acknowledged";
                default -> INVALID_LOCAL_OPERATION;
            };
        }
        if (result instanceof ServiceNpcSpawnClientResult.TransportFailure transport) {
            return switch (transport.safeCode()) {
                case "connect_timeout" -> "connect_timeout";
                case "read_timeout" -> "read_timeout";
                case "overall_timeout" -> "overall_timeout";
                case "executor_saturated" -> "executor_saturated";
                case "response_too_large" -> "response_too_large";
                default -> "transport_error";
            };
        }
        if (result instanceof ServiceNpcSpawnClientResult.HttpFailure http) {
            return switch (http.safeCode()) {
                case "endpoint_unavailable" -> "endpoint_unavailable";
                case "rate_limited" -> "rate_limited";
                case "authentication_rejected" -> AUTHENTICATION_BLOCKED;
                case "malformed_protocol_response" -> "malformed_response";
                case "unsupported_response_protocol", "unknown_response_outcome" -> "protocol_incompatible";
                default -> http.retryable() ? "transport_error"
                    : http.disposition() == ServiceNpcSpawnClientResult.Disposition.PROTOCOL_INCOMPATIBLE
                        ? "protocol_incompatible" : INVALID_LOCAL_OPERATION;
            };
        }
        if (result instanceof ServiceNpcSpawnClientResult.LocalFailure local) {
            return switch (local.safeCode()) {
                case "credentials_unavailable", "minecraft_server_key_missing",
                     "minecraft_server_key_invalid" -> AUTHENTICATION_BLOCKED;
                case "request_too_large" -> "payload_too_large";
                default -> local.disposition() == ServiceNpcSpawnClientResult.Disposition.CONFIGURATION_BLOCKED
                    || local.disposition() == ServiceNpcSpawnClientResult.Disposition.AUTHENTICATION_BLOCKED
                        ? AUTHENTICATION_BLOCKED : INVALID_LOCAL_OPERATION;
            };
        }
        return "cancelled";
    }
}
