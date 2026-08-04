# Shrine and Monolith Rollback Plan

## Scope and prerequisite

This plan covers the complete transparent feature chronology from
`b10efd3382f74bf8e1970588bcd0b57869c8771a` through the corrected production/evidence tip
`b26165350227e6d46cfde3af91ff738a42e48c11`, plus both Milestone 10 documentation commits identified
in the audit history. It includes the original rendering implementation/revert/restoration, the
original conditional audit, and all three corrective Milestone 9A shrine commits. It is an operational
plan, not an instruction to rewrite feature history.

Rollback must first be rehearsed against a verified copy of the exact affected world and player data. A production rollback must not begin while the server is running or before a restorable backup has been independently verified.

## Registered data that may exist in worlds or inventories

| Registry kind | ID |
| --- | --- |
| Block | `britannia_mod:large_structure_anchor` |
| Block | `britannia_mod:large_structure_part` |
| Block entity type | `britannia_mod:large_structure` |
| Item | `britannia_mod:shrine` |
| Item | `britannia_mod:monolith` |
| Data component | `britannia_mod:shrine_instance_state` |
| Data component | `britannia_mod:monolith_instance_state` |

The existing, reused `britannia_mod:interior_decorator_tool` predates this feature and is not removed by a shrine/monolith rollback.

Anchor block entities persist schema version 1 under `shrine_state`, including `schema_version`, `family_id`, `variant_id`, `facing`, and the exact ordered `placed_footprint`. Shrine and monolith inventory stacks persist schema version 1 in their family-specific data component, with `schema_version`, `family_id`, and `variant_id`.

## Why a code-only revert is unsafe

Removing the registrations while anchors, parts, configured items, or components remain can produce missing registry mappings, unreadable inventory state, incomplete structures, or world-load failures. Blindly reverting commits is therefore not sufficient once saved structures or items exist. Deleting the world is not an acceptable default because unrelated builds, player progress, inventories, and server state must be preserved.

No supported bulk-removal command exists in this feature. Before moving to an incompatible JAR, every placed shrine and monolith must be removed through the supported whole-structure lifecycle while the compatible JAR is running, and configured shrine/monolith items must be removed or converted according to an owner-approved inventory plan. If complete removal cannot be proven, the world must remain on a compatible JAR.

## Required preparation

1. Identify the exact active JAR, configuration, world directory, and player-data directory; record their hashes and timestamps.
2. Stop new player access and announce a maintenance window.
3. While still on the compatible JAR, inventory all known placed structures and configured items. Do not infer absence from an incomplete area scan.
4. Remove each placed structure using supported anchor-or-part lifecycle behavior. Confirm that the configured drop policy produced the expected result and no raw anchor/part item exists.
5. Resolve configured shrine and monolith items in player inventories, ender chests, containers, dropped entities, and administrative storage through an explicit owner-approved policy.
6. Shut down the server through the normal console `stop` path. Confirm player, world, chunk, overworld, Nether, and End saves complete.
7. Create a full offline backup of the world, dimensions, player data, configuration, mod list, and exact current JAR.
8. Verify the backup by hash or a second independent archive listing and perform a restore test into a disposable directory.
9. Rehearse every remaining step on that restored copy before touching production.

## Rollback execution

1. Keep the production server stopped.
2. Preserve the current JAR and configuration beside the verified backup; do not overwrite the only known-good copy.
3. Restore the prior repository-approved JAR using the repository's normal deployment procedure.
4. Restore only configuration changes that are demonstrably feature-specific. This feature added no shrine/monolith configuration file, so do not remove unrelated server configuration.
5. Start the copied rehearsal world first. Review missing-mapping, registry, data-component, block-entity, chunk, and player-inventory diagnostics.
6. Verify representative affected regions, all dimensions, player inventories, login, save, restart, and unrelated features.
7. Only after the rehearsal passes may the same controlled procedure be separately authorized for production.

## Validation after rollback

- The server reaches `Done` and stops normally.
- Overworld, Nether, and End save without registry or block-entity errors.
- No `large_structure_anchor`, `large_structure_part`, shrine/monolith item, or shrine/monolith component mapping remains in saved data unless the restored JAR still supports it.
- No missing block, missing item, missing component, malformed block entity, orphan part, or duplicate configured drop is reported.
- Player inventories and containers load without loss or substitution.
- Unrelated banking, banner, world, and gameplay content remains unchanged.
- Server and client logs contain no new rollback-related error.

## Abort criteria

Abandon the rollback and restore the verified backup with the compatible JAR if any saved structure or configured item remains, a registry/data-component mapping is missing, player data is lost or rewritten unexpectedly, the world fails to load or save, an unrelated block/item is substituted, or dimension/chunk integrity cannot be established.

Rollback must be rehearsed because registry removal and saved-state compatibility failures can surface only when real chunks or player records load. The safe recovery path is restoration of the verified backup with the exact compatible JAR, not world deletion or ad hoc NBT editing.
