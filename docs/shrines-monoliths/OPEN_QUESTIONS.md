# Shrine and Monolith Open Questions

Settled decisions from the authoritative design and playbook are intentionally omitted. No remaining open question blocks the completed Milestone 1 definition and transform foundation.

## 1. When will the completed banner branch be integrated?

- **Unresolved issue:** The safe banner planner/executor/footprint/lifecycle implementation is present only on local branch `banners-dyetub`, not on `patch-18` or `shrines-monoliths`. It is unknown whether shrine/monolith work should expect that branch to land first.
- **Why inspection did not answer it:** Git history proves the implementations are on different branch lines but contains no integration plan or ordering decision.
- **Evidence inspected:** `git branch --all --contains 4cd7aec`; `git ls-tree -r banners-dyetub`; banner placement, structure, block entity, and test sources via `git show`; current branch tree and merge-base inspection.
- **Blocks Milestone 1:** **No.** Milestone 1 can specify a repository-native contract without merging or copying banner code. It does affect whether later implementation adapts an integrated primitive or independently introduces one.
- **Smallest owner decision required:** Confirm whether `banners-dyetub` is expected to merge before shrine/monolith implementation begins.

## 2. What exact administrator predicate should gate shrine/monolith decorator actions?

- **Unresolved issue:** The approved feature is administrator-controlled, but `InteriorDecoratorToolItem` performs no administrator check. Other code uses creative mode and/or permission level 2 in several places without a single decorator authorization service.
- **Why inspection did not answer it:** Existing call sites are inconsistent: some target blocks require creative, some exclude only spectators/adventure, and the tool's own rotations/styles have no permission gate.
- **Evidence inspected:** `item/InteriorDecoratorToolItem.java`; all `INTERIOR_DECORATOR_TOOL` references; permission checks in spawn blocks and command handlers; `structure/StructureProtectionHandler.java`.
- **Blocks Milestone 1:** **No.** It blocks the later decorator-interaction milestone, not the initial structure contract.
- **Smallest owner decision required:** Choose one predicate: creative-only, permission-level-2-only, or creative-or-permission-level-2.

## 3. Where are the monolith models and textures, and which names are approved?

- **Unresolved issue:** The design requires model-varying monolith variants and a `+16`-voxel render correction, but no supplied monolith asset is present. Authored coordinates, lowest-Y values, variant count, stable names, and provisional names therefore cannot be verified.
- **Why inspection did not answer it:** Searches of resource, content, documentation, model, texture, GeckoLib, and Blockbench locations found no monolith asset or metadata outside the specifications.
- **Evidence inspected:** `src/main/resources/assets/britannia_mod`; `content`; all `*.bbmodel`, `*.geo.json`, texture, localization, and monolith-name searches; both root specifications.
- **Blocks Milestone 1:** **No.** It blocks asset integration and visual acceptance in later milestones.
- **Smallest owner decision required:** Supply the asset package and a mapping from each file to either an approved stable display/variant name or an explicit `unnamed/provisional` status.

## Confirmed non-questions

- On 2026-08-03, after the corrective Milestone 4 review, the owner explicitly approved the exact historical placeholder package from `e1fcae20ae2f2af796b7b44ad9ca9d65352cffff` for use: one static shared shrine geometry, nine mapped 128 by 128 textures, the bounded diagnostic geometry, static animation manifest, and Honesty-based family item presentation. This resolves the shrine asset blocker without changing the corrective history.
- Shrine dimensions, occupied cells, family behavior, and nine identities are settled.
- Monolith dimensions, occupied cells, family behavior, and the positive sixteen-voxel render correction are settled.
- Collision/render independence and per-cell `0..16` bounds are settled.
- Cross-family conversion is forbidden and is not open for interpretation.
- The Milestone 0 NeoForm clean-build issue is resolved: NeoGradle's supply pipeline rebuilt the incomplete generated artifact, and the normal Milestone 1 clean production build passed.
