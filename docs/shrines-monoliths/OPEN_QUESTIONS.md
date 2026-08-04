# Shrine and Monolith Open Questions

Settled decisions from the authoritative design and playbook are intentionally omitted. No remaining open question blocks the completed Milestone 1 definition and transform foundation.

## 1. When will the completed banner branch be integrated?

- **Unresolved issue:** The safe banner planner/executor/footprint/lifecycle implementation is present only on local branch `banners-dyetub`, not on `patch-18` or `shrines-monoliths`. It is unknown whether shrine/monolith work should expect that branch to land first.
- **Why inspection did not answer it:** Git history proves the implementations are on different branch lines but contains no integration plan or ordering decision.
- **Evidence inspected:** `git branch --all --contains 4cd7aec`; `git ls-tree -r banners-dyetub`; banner placement, structure, block entity, and test sources via `git show`; current branch tree and merge-base inspection.
- **Blocks Milestone 1:** **No.** Milestone 1 can specify a repository-native contract without merging or copying banner code. It does affect whether later implementation adapts an integrated primitive or independently introduces one.
- **Smallest owner decision required:** Confirm whether `banners-dyetub` is expected to merge before shrine/monolith implementation begins.

## 2. What final monolith variants and assets will replace the Milestone 6-7 diagnostics?

- **Resolved Milestone 6-7 prerequisites:** On 2026-08-03 the owner authorized provisional `monolith/diagnostic_missing_content` for Milestone 6 and then exactly one additional provisional `monolith/diagnostic_alternate` package for Milestone 7. Their measured bounds, pivots, orientations, hashes, and shared `+16`-voxel correction are recorded in `PLACEHOLDER_ASSETS.md`; exactly these two are sufficient for model cycling diagnostics.
- **Unresolved issue:** Final monolith variant count, stable lore/display names, final geometry and texture files, and the replacement/migration decision remain unspecified. Neither provisional ID is final artwork.
- **Why inspection did not answer it:** No final owner-supplied monolith art or variant manifest exists in the inspected repository, local refs, content directories, or specifications.
- **Evidence inspected:** `src/main/resources/assets/britannia_mod`; `content`; all committed `*.bbmodel`, `*.geo.json`, texture, localization, and monolith-name searches; both root specifications; the Milestone 6 owner override.
- **Blocks Milestone 6:** **No.** The explicit diagnostic authorization resolved that prerequisite.
- **Blocks Milestone 7:** **No.** The owner's exact second-placeholder authorization resolves the model-cycling prerequisite.
- **Blocks final-content replacement:** **Yes.** No final art or migration package has been supplied.
- **Smallest owner package required:** Supply each approved stable variant ID and localized display name, its exact geometry and texture source/production paths, approval status, authored bounds/pivot/default forward direction, texture dimensions/alpha/UV compatibility, and confirmation of whether the diagnostic ID remains, is disabled, or is migrated.

## Confirmed non-questions

- Milestone 5 uses the playbook's explicit fallback administrator policy because repository inspection found no canonical reusable predicate: logical-server validated creative mode or server permission level 2 or higher. The policy is centralized in `DecoratorAuthorization` and applies only to shrine cycling; unrelated decorator targets were not changed.
- On 2026-08-03, after the corrective Milestone 4 review, the owner explicitly approved the exact historical placeholder package from `e1fcae20ae2f2af796b7b44ad9ca9d65352cffff` for use: one static shared shrine geometry, nine mapped 128 by 128 textures, the bounded diagnostic geometry, static animation manifest, and Honesty-based family item presentation. This resolves the shrine asset blocker without changing the corrective history.
- On 2026-08-03, the owner separately authorized exactly two provisional monolith diagnostics across Milestones 6 and 7: existing `diagnostic_missing_content` and additional `diagnostic_alternate`. This authorization is limited to those exact placeholders and does not imply a third variant or final content.
- Shrine dimensions, occupied cells, family behavior, and nine identities are settled.
- Monolith dimensions, occupied cells, family behavior, and the positive sixteen-voxel render correction are settled.
- Collision/render independence and per-cell `0..16` bounds are settled.
- Cross-family conversion is forbidden and is not open for interpretation.
- The Milestone 0 NeoForm clean-build issue is resolved: NeoGradle's supply pipeline rebuilt the incomplete generated artifact, and the normal Milestone 1 clean production build passed.

## 3. Which Milestone 8 checks still require live Milestone 9 validation?

- **Unresolved issue:** Reliable interactive control was unavailable for the live stair/full-block/slab/wall/fence/attachment ring, actual save-world reload, visual overlap and duplicate-render review, two authenticated clients, and multiplayer observation.
- **Automated evidence available:** All registered structure states, transform-derived occupancy/perimeters, registered vanilla neighbor states, persistence/update paths, collision mathematics, startup smokes, and production packaging pass. This evidence is deliberately not described as live placement or visual validation.
- **Blocks Milestone 8:** **No.** The authorization explicitly permits these manual checks to remain `UNVERIFIED` when reliable interaction is unavailable, provided the exhaustive automated matrix passes.
- **Blocks final live validation:** **Yes.** Carry the complete representative ring and multiplayer/save-world checks into Milestone 9 without inferring results from level-C/D evidence.
