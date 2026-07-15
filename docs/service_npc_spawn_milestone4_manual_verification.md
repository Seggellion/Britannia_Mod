# Service NPC Spawn Block Milestone 4 Manual Verification

**Overall status: NOT RUN**

Automated tests do not replace these checks. Milestone 4 must not be merged until every required check passes or a human explicitly accepts a documented exception. Third-party tools that bypass block loading or ticking remain a known limitation.

Do not change a result to `PASS` without preserving real evidence. Use `FAIL` for an observed failure and `NOT RUN` when evidence is unavailable.

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
| Date | Not recorded |
| Tester | Not recorded |
| Minecraft/NeoForge build | Not recorded |
| Server type | Not recorded |
| Result | NOT RUN |
| Evidence | No user-provided evidence. |
| Notes | None recorded. |

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
| Date | Not recorded |
| Tester | Not recorded |
| Minecraft/NeoForge build | Not recorded |
| Server type | Not recorded |
| Result | NOT RUN |
| Evidence | No user-provided evidence. |
| Notes | None recorded. |

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
| Date | Not recorded |
| Tester | Not recorded |
| Minecraft/NeoForge build | Not recorded |
| Server type | Not recorded |
| Exact production WorldEdit version | Not recorded |
| Result | NOT RUN |
| Evidence | No user-provided evidence. |
| Notes | None recorded. |

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
| Date | Not recorded |
| Tester | Not recorded |
| Minecraft/NeoForge build | Not recorded |
| Server type | Not recorded |
| Result | NOT RUN |
| Evidence | No user-provided evidence. |
| Notes | None recorded. |

## Review and exception record

| Field | Value |
| --- | --- |
| All required sections passed | NO - NOT RUN |
| Documented exception accepted | NO |
| Exception details | None recorded. |
| Reviewer | Not recorded |
| Review date | Not recorded |
| Merge authorization | NOT GRANTED |
