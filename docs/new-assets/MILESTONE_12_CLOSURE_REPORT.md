# Milestone 12 Closure Report

## Status

**OWNER APPROVED WITH DOCUMENTED EVIDENCE EXCEPTIONS**

- Branch: `new-assets`
- Starting HEAD: `775806db` (`Audit new asset integrations`)
- Merge/push authorization: none

Milestones 0–12 are implemented and individually approved. On 2026-08-10, the owner explicitly approved Milestone 12 and authorized the final isolated commit. No separate owner-session logs, screenshots, multiplayer evidence, or canonical training-dummy skill-slug confirmation were supplied. This report preserves those evidence gaps instead of representing the corresponding checks as performed.

## Automated evidence already satisfied

| Gate | Evidence | Result |
|---|---|---|
| Full build and unit/resource contracts | Gradle 8.9 build; 1,712 tests, 17 skipped, zero failures or errors | PASS |
| Server-side integration | Dedicated GameTest server; all 347 required tests passed | PASS |
| Client resources | Development client completed resource reload and block-atlas creation with no audited new-assets model, texture, localization, or render warnings | PASS |
| Cross-system data audit | 37 block IDs, two textile items, and ibis entity/spawn egg covered by `NewAssetsCrossSystemAuditTest` | PASS |
| Manifest explanation | The only `MISSING` entry is owner-deferred future textile silk, explicitly distinct from spiders' silk | PASS |

The existing 44×44 `dungeon_moongate_block` mip warning and unrelated repository resource warnings predate and remain outside this project. The updated one-block city moongate does not produce that warning.

## Required owner live-validation matrix

| Area | Minimum acceptance evidence | Status |
|---|---|---|
| Decorative assets | Creative entries, inventory presentation, orientation, transparency, collision, tool, drop, and reload spot checks | Owner accepted; no separate live evidence |
| Merchant carts | All six approved colors, 3×3×3 placement, usable collision, and temporary-art acceptance | Owner accepted; no separate live evidence |
| Fountain | 2×2×3 footprint, translucent water, alignment, and basin collision | Owner accepted; no separate live evidence |
| Crates | Three sizes, storage capacity, persistence, comparator/menu behavior, atomic teardown, and multiplayer safety | Owner accepted; no separate live evidence |
| Water well | Watering can, vanilla bucket, and water-only pitcher behavior; no unsupported-container mutation | Owner accepted; no separate live evidence |
| Ladder | Three-block placement, double-sided climbing, Adventure placement, axe removal, and teardown | Owner accepted; no separate live evidence |
| Jhelom ibis | White/scarlet visuals, animation, persistence/sync, only the three Jhelom areas, and combined cap of 15 independent of other city animals | Owner accepted; no separate live evidence |
| Training dummy | Weapon mappings including all axes→Swordsmanship, per-player cooldown, durability protection, animation, 25.0 cap, and integrated skill slugs | Owner accepted; skill slugs not separately confirmed |
| Textiles | Wool/cotton/flax spinning, five yarn/thread→one folded cloth, rollback/drop safety, and spiders'-silk rejection | Owner accepted; no separate live evidence |
| Display cases | Independent/end/middle/corner recomputation and confirmation of no storage or displayed-item inventory | Owner accepted; no separate live evidence |
| Moongates | One-cell/32-voxel city gate, city travel with cooldown/mount/escort, legacy-top migration, and unchanged paired dungeon gates | Owner accepted; no separate live evidence |
| Runtime | Clean client and dedicated-server launches in the owner's target environment | Automated environment passed; owner environment not supplied |

Detailed per-asset procedures and evidence fields are in `LIVE_TEST_CHECKLIST.md`.

## Known accepted design boundaries

- Purchased and generated art is temporary and will be replaced later while preserving final registry IDs.
- Future textile `silk` is deferred and must not alias `britannia_mod:spiders_silk`.
- Display cases are decorative and intentionally have no storage or displayed-item inventory.
- The city moongate updates the existing random-city gate; paired dungeon moongates remain a separate system.
- The water well supports watering cans, vanilla buckets, and water-only pitchers.
- All axes map to Swordsmanship at the training dummy. Canonical skill slugs were not separately confirmed against an external integrated skill service; the owner accepted closure with the provisional slugs and this caveat retained.
- Models that required specified dimensional re-authoring are recorded in `ASSET_IMPORT_MANIFEST.md`; moongate, training dummy, ladder, and loom re-authoring is complete.

## Replacement-art backlog

Temporary/purchased/generated art and conspicuous code-authored placeholders remain replacement work by owner direction. Replacement must preserve registry IDs, functional contracts, model envelopes, and multiblock footprints unless separately redesigned. Future silk creation/acquisition is a separate feature, not replacement art.

## Closure gates

- [x] Manifest has no unexplained missing entries.
- [x] Automated functional and data requirements pass.
- [ ] Required live checks are completed with separate owner-session evidence; no evidence was supplied.
- [x] Owner explicitly accepted closure without separate live-session evidence.
- [x] Current temporary/placeholder presentation is accepted for this closure; replacement remains future work.
- [ ] Training-dummy skill slugs are independently confirmed in the integrated environment.
- [x] Owner accepted the provisional training-dummy slugs with the integration caveat documented.
- [x] Owner explicitly approved project closure.
- [x] Owner authorized the final isolated Milestone 12 commit.
- [x] No merge or push has been performed.

## Owner closure record

- Tester/date: Owner approval recorded in the Codex task, 2026-08-10; no separate interactive test record supplied.
- Game/modpack version: Not separately supplied.
- Client result and log path: Automated development-client reload passed as recorded in `IMPLEMENTATION_LOG.md`; no owner-client log supplied.
- Dedicated-server result and log path: All 347 automated GameTests passed as recorded in `IMPLEMENTATION_LOG.md`; no owner-server log supplied.
- Screenshots/video path: None supplied.
- Failed checks and follow-up disposition: No failures reported; absent live evidence and skill-slug confirmation remain documented exceptions.
- Placeholder acceptance or replacement notes: Current temporary/placeholder presentation accepted for closure; replacement remains future work under the existing final IDs.
- Confirmed skill slugs: Not independently confirmed; provisional `swordsmanship`, `mace_fighting`, `fencing`, and `tactics` accepted with the integration caveat retained.
- Closure decision: Approved with documented evidence exceptions.
- Final commit authorization: Yes.
