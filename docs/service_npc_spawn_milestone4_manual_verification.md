# Service NPC Spawn Block Milestone 4 Manual Verification

**Overall status: PASS — ACCEPTED DESIGN VARIANCE**

Automated tests do not replace these checks. Milestone 4 must not be merged until every required check passes or a human explicitly accepts a documented exception. Third-party tools that bypass block loading or ticking remain a known limitation.

Do not change a result to `PASS` without preserving real evidence. Use `FAIL` for an observed failure and `NOT RUN` when evidence is unavailable.

## Authentication-hardening preflight (required before Section A)

This verification must use a new clean dedicated-server world. Do not reuse the old `run/saves/sandbox` world because it may contain historical credential SavedData.

1. Stop the Rails, Minecraft server, and Minecraft client processes.
2. Follow `docs/server_authentication.md` to rotate the development shard secret and, if still active, the deprecated global bearer. Do not record either value here.
3. Put the public shard name and new shard secret only in the dedicated server's process environment or ignored `run/server/config/britannia_mod-server.properties` file. Do not pass credential environment variables to the client.
4. Use HTTPS outside local development. For this localhost verification, bind Rails to loopback and use `http://127.0.0.1:3000`.
5. Start Rails, then start the dedicated server against the new world. Leave the Rails update listener disabled unless its loopback-only legacy behavior is itself under test.
6. Use `/britannia_api status` from an operator context. Record only credential source, eight-hex fingerprint, last bootstrap result, HTTP result, city count, and Service NPC registry revision/count.
7. Connect the client and confirm the Service NPC Spawn Block menu contains at least one UUID-backed city and the active, spawnable `bank_teller` type. Stop if either registry prerequisite is unavailable; do not fabricate data.
8. Confirm the client received no credential packet. Inspect new-world NBT, generated server/client logs, crash reports, and packaged client/JAR resources for credential absence. Confirm the rotated old credential no longer authenticates without printing it.
9. Preserve the preflight evidence with the manual run, then perform Sections A-D exactly below. This preflight and all four sections remain `NOT RUN` until observed by a human.

### Preflight result record

| Field | Value |
| --- | --- |
| Date | 2026-07-15 to 2026-07-16 |
| Tester | Dustin Hill |
| Clean world name | `m4_auth_hardened_verification_4` for Sections A-C; `m4_auth_hardened_pending_verification` for Section D |
| Credential source | `SERVER_FILE` |
| Diagnostic fingerprint | Eight-hex redacted diagnostic recorded; no credential value recorded |
| Bootstrap HTTP/result | HTTP 200; authenticated compact bootstrap succeeded |
| City count | UUID-backed Britain confirmed; the total count was not separately captured |
| Service NPC registry revision/count | Schema 1; one active, spawnable `bank_teller`; the top-level revision was not separately captured |
| Client/world/log/JAR inspection | PASS; client isolation and final world/log/artifact exclusion scans were clean |
| Old credential rejection | PASS; the rejected value was not displayed or recorded |
| Result | PASS |
| Evidence | `/britannia_api status` showed `source=SERVER_FILE`, shard `Britannia`, an eight-hex fingerprint, and `last_bootstrap=success`; tab completion exposed only `status`. The menu showed UUID-backed Britain and Bank Teller. |

## A. UI, authorization, and restart

### Procedure

1. Start a server with at least one cached city and one active, spawnable Service NPC type.
2. Place a Service NPC Spawn Block as an operator.
3. Open the configuration menu.
4. Select a city and `bank_teller`.
5. Disable the block.
6. Save.
7. Confirm revision `1`.
8. Confirm state `PENDING_REGISTRATION`.
9. Leave the screen open.
10. Remove the player's operator permission.
11. Attempt another save.
12. Confirm the result is `UNAUTHORIZED`.
13. Confirm previous authoritative values are restored unchanged.
14. Restart the server.
15. Reopen the block.
16. Confirm UUID, city, type, enabled state, revision, and pending status survived.

### Result record

| Field | Value |
| --- | --- |
| Date | 2026-07-15 to 2026-07-16 |
| Tester | Dustin Hill |
| Minecraft/NeoForge build | Minecraft 1.21.1; NeoForge 21.1.72; Britannia 0.1.7k |
| Server type | Local dedicated server, loopback only |
| Result | PASS — ACCEPTED DESIGN VARIANCE |
| Evidence | Initial save selected Britain / `bank_teller` / Enabled No and produced revision 1, `PENDING_REGISTRATION`, the unchanged Spawn Point UUID, and exactly one matching UPSERT. Authoritative NBT and the UI remained unchanged after permission removal and after restart. |
| Notes | De-op immediately closed the open menu, so a live stale Save and visible `UNAUTHORIZED` response were not reachable through the normal client. De-opped right-click could not open a new menu. No mutation, revision increment, or additional pending work occurred. Direct unauthorized-handler rejection remains covered by automated tests. Earlier ambiguous-target and operator-input attempts were classified invalid, not product failures. |

## B. Ctrl+middle-click identity stripping

### Procedure

1. Configure a post.
2. Obtain an item with ordinary middle-click.
3. Obtain an item with Ctrl+middle-click.
4. Inspect inventory data.
5. Confirm neither item contains `minecraft:block_entity_data`.
6. Place both items.
7. Confirm each receives a new UUID.
8. Confirm both start unconfigured at revision zero.

### Result record

| Field | Value |
| --- | --- |
| Date | 2026-07-16 |
| Tester | Dustin Hill |
| Minecraft/NeoForge build | Minecraft 1.21.1; NeoForge 21.1.72; Britannia 0.1.7k |
| Server type | Local dedicated server, loopback only |
| Result | PASS |
| Evidence | The isolated retry used one human ordinary middle-click and one Ctrl+middle-click. Authoritative inventory inspection found no `minecraft:block_entity_data` in either item. Controlled placements retained the source UUID and generated two distinct new UUIDs; both copies were `UNCONFIGURED`, revision 0, with no city/type, assignment, or synchronization cache. |
| Notes | The earlier contaminated attempt remains recorded as invalid operator input and is not part of this PASS result. |

## C. Production WorldEdit copy/paste

### Procedure

1. Record the exact WorldEdit version used in production.
2. Configure a source post.
3. Copy and paste it.
4. Wait at least one server tick.
5. Confirm the source UUID is unchanged.
6. Confirm the pasted post has a different UUID.
7. Confirm the pasted post is enabled.
8. Confirm the pasted post is unconfigured.
9. Confirm its revision is zero.
10. Confirm it has no assignment or synchronization cache.

### Result record

| Field | Value |
| --- | --- |
| Date | 2026-07-16 |
| Tester | Dustin Hill |
| Minecraft/NeoForge build | Minecraft 1.21.1; NeoForge 21.1.72; Britannia 0.1.7k |
| Server type | Local dedicated server, loopback only |
| Exact production WorldEdit version | WorldEdit 7.3.8+6939-7d32b45 |
| Result | PASS |
| Evidence | The isolated Station C paste left the source UUID unchanged and assigned the pasted post a different UUID. The pasted post was enabled, `UNCONFIGURED`, revision 0, with no copied city/type, assignment, synchronization cache, or premature UPSERT. Bounded duplicate-repair evidence contained no credential data. |
| Notes | Only the approved existing WorldEdit JAR was used; it was not modified or packaged. |

## D. Real pending-store restart

### Procedure

1. Configure one post so an UPSERT exists.
2. Destroy another configured post so a REMOVE exists.
3. Stop the server gracefully.
4. Inspect `world/data/britannia_service_npc_spawn_pending.dat`.
5. Confirm both UPSERT and REMOVE are present.
6. Restart the server.
7. Stop it gracefully again.
8. Confirm both unacknowledged records remain.

### Result record

| Field | Value |
| --- | --- |
| Date | 2026-07-16 |
| Tester | Dustin Hill |
| Minecraft/NeoForge build | Minecraft 1.21.1; NeoForge 21.1.72; Britannia 0.1.7k |
| Server type | Local dedicated server, loopback only |
| Result | PASS |
| Evidence | Dedicated clean world `m4_auth_hardened_pending_verification` produced exactly two schema-1 records: one intended UPSERT and one intended REMOVE. UUID, revision, dimension, operation, and coordinates matched their target posts. After graceful stop, restart, and a second graceful stop, the store was byte-for-byte unchanged; SHA-256 `99314F4A2FD05C213B53F0EA83C3571605EF61D45456637624A0DFBE8A63AA17`. |
| Notes | Credential marker count was zero. No unrelated pending record was present. Runtime NBT evidence remains untracked. |

## Review and exception record

| Field | Value |
| --- | --- |
| All required sections passed | YES |
| Documented exception accepted | YES |
| Exception details | Section A live stale-menu Save was unreachable because permission loss closed the menu. This safely prevented unauthorized mutation; direct handler rejection is automated. The block is invisible and untargetable in Adventure mode but visible and targetable in Creative mode; server-side configuration remains permission-authorized. |
| Reviewer | Dustin Hill |
| Review date | 2026-07-16 |
| Merge authorization | NOT PERFORMED; this closeout authorizes only the selective commits and expressly prohibits merge or push |
