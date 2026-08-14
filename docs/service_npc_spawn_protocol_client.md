# Service NPC spawn-operation client — Milestone 5, Slice 2

This dedicated-server-only client transports one explicitly supplied immutable operation to the Rails Slice 1 endpoint. Rails must be migrated and each save provisioned with an active `MinecraftServer.public_id` before this client is used.

## Server configuration

Keep the existing shard credentials and API origin in the restricted server environment or `config/britannia_mod-server.properties`. Add the stable, non-secret server/save identifier using:

- environment: `ULTIMACRAFT_MINECRAFT_SERVER_KEY`
- restricted property: `minecraft_server_key`

The environment value takes precedence over the property. It must parse as a UUID. A missing or malformed value blocks only spawn-operation submission; quest, bootstrap, economy, and other existing Rails calls continue to use the existing shard credentials. The key is never client-synchronized and is not written to packets, block/item NBT, world `SavedData`, or pending operations. Credential logging remains limited to the existing shard name/source and secret fingerprint; neither the secret nor server key is added to `toString`.

## Wire request

The closed resolver always targets:

`POST /api/service_npc_spawn_operations`

It preserves service-origin normalization, accepts configured roots ending in `/api`, permits HTTP only for explicit loopback development, does not follow redirects, and exposes no caller-selected path or query.

Headers are exactly server-owned:

- `Shard-Name`
- `Shard-Secret`
- `Minecraft-Server-Key`
- `Content-Type: application/json`
- `Accept: application/json`

The UTF-8 JSON body is bounded to 16 KiB before a connection is opened:

```json
{
  "protocol_version": 1,
  "operation_id": "11111111-2222-4333-8444-555555555555",
  "operation": "UPSERT",
  "spawn_uuid": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
  "source_revision": 3,
  "location": {
    "world_name": "Britannia",
    "dimension": "minecraft:overworld",
    "x": 142,
    "y": 68,
    "z": -315
  },
  "city_public_id": "12345678-1234-4234-8234-123456789abc",
  "service_npc_type_key": "bank_teller",
  "enabled": true
}
```

UPSERT requires all three configuration fields and a positive revision. REMOVE permits revision zero and omits absent configuration fields rather than emitting JSON nulls. Shard/server identity, secrets, retry state, dispositions, and block-entity state never appear in the body.

## Results and transport

`ServiceNpcSpawnRegistrationClient.submit(MinecraftServer, ServiceNpcSpawnOperationRequest)` returns a `ServiceNpcSpawnRequestHandle`. Its future completes with one closed typed result: validated Rails protocol, sanitized HTTP failure, sanitized transport failure, local request/configuration failure, or cancellation.

The parser accepts only Rails protocol version 1 and the committed closed outcome registry. It requires the core envelope fields, validates status/success/retryability consistency, and validates operation/spawn correlation before a response is usable. Successful UPSERT/REMOVE acknowledgement revision, registration state, and timestamp are checked. Unknown additive fields are ignored; unknown outcomes, versions, incompatible types, unsafe collision metadata, and malformed HTTP 200 bodies fail closed.

UUID collisions retain `LIVE`, `TOMBSTONED`, or `REDACTED` and the replacement-required flag. Safe same-shard LIVE canonical locations are typed and bounded. Redacted and tombstoned collisions cannot carry canonical metadata. Location occupancy carries its distinct occupying spawn UUID and canonical location; it is never treated as UUID replacement.

HTTP classification:

- 200 requires valid `APPLIED` or `ALREADY_APPLIED`
- 400/409/413/422 and 401/403 use a consistent known protocol envelope
- 404 is retryable `endpoint_unavailable`
- 429 is retryable and exposes an optional `Retry-After` seconds/HTTP-date hint capped at five minutes
- 500/502/503/504 are retryable; valid 503 `SERVICE_UNAVAILABLE` is retained
- other 4xx are permanent/protocol failures as applicable; other 5xx are retryable
- redirects are rejected and never followed

The existing bounded server HTTP executor performs transport off the tick thread. Connect/read/overall bounds remain 5/10/15 seconds. Both success and error bodies are capped at 64 KiB. The handle retains the active request; explicit cancellation disconnects it and completes safely. Executor saturation, timeouts, oversize responses, cancellation, and transport errors expose safe codes only. Raw bodies, exception text, URLs with credential state, secrets, and headers are not logged.

## Slice boundary

There is deliberately no automatic delivery or retry. This slice does not select or mutate pending records, persist attempt state, acknowledge operations, update block entities/registration state, repair UUIDs, reconcile claims, schedule ticks, or mutate a world. Slice 3 will adapt compacted pending records into this request and apply typed results on the server thread.

A safe development check is the JUnit in-process HTTP harness in `ServiceNpcSpawnRegistrationClientTest`; it supplies a request directly, captures the one POST, and uses only test sentinel credentials. No production debug command was added.
