# Persistent Flower System Test Matrix

Milestone 6 evidence is recorded here without claiming a live two-client session or final Milestone 7 rendering.

| Area | Automated evidence | Runtime/manual evidence | Status |
|---|---|---|---|
| Authorization | Creative, operator 2, non-operator, stored protection, system reasons, and centralized-handler source checks in `FlowerInteractionTest` | Isolated dedicated server reached readiness with the handler registered | Validated; live denial untested |
| Care | Shared nutrient arithmetic/caps and identity preservation; source routing/denial checks | Watering/fertilizer live interaction not manually exercised | Implemented; runtime untested |
| Mature shearing | All seven natural maturity boundaries, reset identity, quality/provenance and successful-only damage paths inspected | Live inventory/drop interaction not manually exercised | Implemented; runtime untested |
| Seed extraction | All seven harvested/seed mappings and `CropSeedExtractor` delegation checked | Existing off-hand/shift semantics inherited; no live click performed | Implemented |
| Adventure cutback | Grain-tag delegation, dagger resource membership, cancellation and reset paths checked | No live Adventure player interaction | Implemented; runtime untested |
| Uprooting | Tool-policy and private/community restoration transaction paths checked | No live private/community plot interaction | Implemented; runtime untested |
| Poppy stage 7 | Species/stage/99/100/tool/exact-repeat pure matrix checked | No live skill-backed interaction | Implemented; runtime untested |
| Break/replacement | Protected denial and normal-break restoration paths inspected; protected BlockItem use is consumed as denial | Authorized in-place BlockItem replacement is not a separate feature; admin removes then places | Implemented with documented limitation |
| Explosion/fluid/piston | Policy matrix, explosion target filtering, fluid cancellation, and `PushReaction.BLOCK` checked | No live explosion/fluid/piston fixture | Implemented; runtime untested |
| Persistence/synchronization | Existing lifecycle/growth round trips plus state-preserving interaction helpers and block updates | Restart-after-interaction not manually exercised | Automated contract coverage |
| Existing farming regression | Full 48-test suite, crop care shared arithmetic, existing extractor/tool classes unchanged except shared delegation | Isolated server reached `Done`; isolated client initialized sound and the block atlas | Validated |
| Rendering boundary | Test asserts no flower renderer; placeholder contract remains Milestone 3 only | Soil-only presentation expected | Passed |

Runtime log notes: the dedicated server still reports the pre-existing client-only `TitleScreenBackgroundMixin` distribution error and missing optional configuration before reaching readiness. The client still reports unrelated legacy missing-model warnings. Targeted searches found no Milestone 6 flower or skinning-knife resource failures.

## Milestone 7 rendering evidence

| Area | Automated evidence | Runtime/manual evidence | Status |
|---|---|---|---|
| Model coverage | `FlowerRenderingTest` resolves 7 species x 7 stages x 2 passes, asserts 49 base + 49 mask registrations, exact paths, tint index 0 on every shared-parent face, paired particle reference, and no third model texture | Live 7x7 matrix plus targeted fixtures rendered all species/stages; corrected screenshots retained under `build/flower-render-review/` | Validated |
| Stage mapping | Exact stages 1-7, Poppy 6/7, stage-1 reset mapping, and low/high visual clamps are covered | Poppy 6/7 distinct shapes, empty stage-1 mask, and persisted low/high clamps were inspected after restart | Validated |
| Tint conversion | Black, white, RGB primaries, `0x123456`, and every configured palette tint use exact channel division with alpha 1; invalid numeric and unknown-species fallbacks covered | Same-species stage-5 light/burgundy comparison visibly differs after adding model tint indices; corrupt `-1` and unknown species visibly use deterministic fallbacks | Validated |
| Pass contract | Pure plan fixes base then mask; source contract asserts two cutout calls, base white, saved mask tint, one shared pose/rotation, identical light/overlay, and no translucent/third pass | Multi-angle corrected/geometry captures show fixed green base regions, coloured masks, aligned silhouettes, no halo/seam/checkerboard, and no static Z-fighting | Validated; continuous-motion flicker remains subjective owner review |
| Reload/cache | Identifiers are static; baked models are reacquired from `ModelManager`; reload resets only bounded diagnostics | Live `reloadResourcePacks()` completed twice with the visible fixture; final post-reload capture retained and no flower asset/renderer error logged | Validated |
| Dedicated-server isolation | Common flower sources contain no client renderer/model imports; registration is under the existing `Dist.CLIENT` subscriber | Isolated world reached readiness on three starts; final `Done (0.507s)` after two clean save/restart cycles, with corrupt tint and unknown species preserved | Validated |
| Soil/flower responsibility | Existing hydration soil blockstate remains untouched; renderer uses soil-top offset and renders flower planes only | Hydration-varied soil remained visible in close, low, normal, elevated, matrix, and dense views; flowers begin at the soil top | Validated |
| Existing crop renderer | `FarmingBlockEntityRenderer` and `CropVisualModels` are unchanged | Client crop regression pending | Preserved by scope |
| Chunk/reconnect persistence | Lifecycle tests cover client tags and save/load without rerolling; renderer plan is pure | Client reconnect, server restart, and forced teleport chunk unload/reload preserved visible identity and exact queried NBT | Validated |
| Dense garden | Per-frame code performs two model lookups/submissions with one cutout buffer and no parsing/collection rebuild | 10x10 mixed garden captured before and after correction; timed capture/reload sequence completed without renderer errors or observed stalls | Practical pass; no formal profiler measurement |

Milestone 7 runtime notes: the isolated client rendered 160 valid/targeted/corrupt fixture flowers, completed live reload and chunk cycling, and reconnected across clean server restarts. Review found and corrected missing model tint indices plus corrupt numeric-tint deserialization; the corrected fixture and logs passed. Known unrelated legacy client asset/GeckoLib errors, the dedicated-server `TitleScreenBackgroundMixin` distribution error, and the optional-config warning remain untouched. No two-live-client session, continuous-motion flicker recording, or formal frame-time profile is claimed.

## Milestone 8 species-content evidence

| Area | Automated evidence | Runtime/manual evidence | Status |
|---|---|---|---|
| Species completeness | `FlowerSpeciesContentTest` locks seven IDs, fourteen item/seed mappings, stage limits, timings, profile fields, palette counts/fallbacks, and reverse registry mappings | Source/resource gap analysis compared design, decisions, registry, registrations, localization, models, and earlier tests | Validated |
| Palette validation | Every species has an explicit smaller-than-global allowlist, positive weights totaling 100, nonblank rarity, unique IDs/tints, valid fallback, 24-bit values, and 2,000 seeded selections restricted to its allowlist | Design palette counts remain 8/4/10/9/6/9/9; no palette was broadened | Validated |
| Practical growth | Every species reaches natural maturity under a deterministic hydration/nutrient/preferred-climate/altitude scenario; every tolerated climate is positive and slower; every unsuitable climate blocks | Scenarios are documented in the implementation status; no manual multi-condition growth session is claimed | Validated by deterministic coverage |
| Player-facing data | Fourteen registrations/localizations/item models/textures, creative grouping, flowers/seeds tags, and skinning-knife registration/localization/model/texture/tag/grouping are asserted | Protected denial and Poppy stage-7 feedback were converted from literals to localization keys | Validated |
| Models and textures | Seven species x stages 1-7, 49 base + 49 mask models, 49 base + 49 mask PNGs, and absence of stage 8 are asserted | Milestone 7 owner-approved renderer/model references were not changed | Preserved |
| Harvest and renewal | Registry reverse mappings plus one mapped harvested-item construction, shared quality/provenance, identity-preserving reset, and `CropSeedExtractor` delegation are source-contract tested | No live inventory interaction is newly claimed | Preserved |
| Acquisition scope | Test scans recipe, loot, and worldgen JSON for all fourteen item IDs and `skinning_knife`; no unapproved source may resolve | Creative/admin, mature harvest, and item-to-seed extraction remain the only approved paths | Preserved |
| Regression boundary | No crop definitions, crop models, renderer architecture, saved colour schema, protection policy, or Poppy mastery rule changed | Milestone 9 regression/multiplayer closeout was not performed | Preserved by scope |

Milestone 8 runtime notes: the full and focused 64-test runs, 231-file generator audit, isolated dedicated-server readiness, and isolated client sound/atlas readiness passed. Targeted log review found no flower-related issue. Known unrelated optional-config, invalid-path, and GeckoLib diagnostics remain untouched. No manual multi-condition growth, two-live-client multiplayer, new visual review, or Milestone 9 regression is claimed.
