# Farming Skill Progression Implementation Status

Milestone: 14 - Data Model and Registration Integration
Branch: `Farming`
Milestone 14 status: Approved
Milestone 15 status: Not started; begins only after the isolated Milestone 14 commit
Milestone 12 commit: `ca696c602ea9fe47b7a8ec30069ba17bc180d5cf` (`docs(farming): inventory plantable content and skill integration points`)
Milestone 13 commit: `922cc766c34628dd4cd86d49cec01eaeeb91eb4e` (`docs(farming): approve crop skill progression`)

## Coverage

| Contract | Count |
|---|---:|
| Accepted catalog species | 74 |
| Approved proposal rows | 74 |
| Implemented definitions | 74 |
| Crop definitions | 67 |
| Flower definitions | 7 |
| Unique planting-item mappings | 74 |

The authoritative contract is `FarmingSkillRequirement.minimumFarmingSkill()`. `CropDefinition` and `FlowerDefinition` each store an explicit `float minimumFarmingSkill`; their constructors reject non-finite values and values outside inclusive `0..100`. A primitive field with no compatibility constructor makes every active definition supply a value explicitly. Zero remains a valid approved threshold and is not a missing-value fallback.

The float type matches the existing Farming skill representation and future comparison boundary `current Farming >= minimumFarmingSkill`. Approved values remain whole numbers, so the representation introduces no decimal band policy.

## Definition architecture

- All ordinary annual, root, grain, berry, fibre, trellis, lifecycle-special, compatibility, banana, orchard, and grape species are authoritative `CropDefinition` entries.
- The nine `FruitTreeDefinition` records are structure overlays for already-counted crop IDs. They do not own or duplicate requirements; startup validation confirms each overlay resolves to its authoritative crop and planting item.
- The seven persistent flowers are authoritative `FlowerDefinition` entries.
- Crop lookup/save aliases remain lookup-only and do not create progression species or additional values.
- Grapes remains the existing `CropDefinition` species at Farming 80. No grape registry ID, variety data, component/NBT, planting transaction, rendering, harvest, or viewer presentation changed.
- Six compatibility definitions retain their approved metadata because they are part of the accepted catalog. Native vanilla planting routes are not intercepted or gated.

`FarmingSkillRequirementResolver` is the single read-only path from planting item to existing crop/flower species mapping to authoritative requirement. It contains no player skill comparison and is not called by planting or presentation code in Milestone 14.

## Validation and bootstrap

`FarmingSkillRequirementValidator` validates definition values, duplicate/conflicting species, duplicate planting items, missing definitions or mappings, exact 67/7/74 coverage, central resolver agreement, fruit-tree overlays, ordinary Poppy Farming 20, and independent Poppy stage-7 Farming 100. Common setup enqueues this validation once after deferred registration is available, so client and dedicated-server common initialization use the same fail-fast path.

The test reconciliation parses `FARMING_CONTENT_MASTER_CATALOG.md` and `FARMING_SKILL_PROGRESSION_PROPOSAL.md` and compares their 74 IDs, planting-item IDs, and approved values to runtime definitions. The complete approved table is not duplicated in test Java.

## Save and network compatibility

The requirement is immutable definition metadata. It is not written to crop, flower, tree, seed-item, or planted-state NBT and is not sent in a packet. Existing worlds already persist species identity and therefore resolve the current definition automatically. No save migration or legacy requirement fallback is needed. A future approved threshold change would change future eligibility without rewriting existing plantings; Milestone 14 adds no eligibility consumer. No network change is required.

## Special boundaries

- Ordinary Poppy identification/cultivation metadata is Farming 20. The existing stage-7 mastery constant remains Farming 100 plus stage 6, an approved skinning knife, and mutation authorization. Neither value derives from the other.
- Native vanilla planting behavior remains untouched.
- The existing grape system remains untouched. The approved future localized under-skilled presentation `a brown seed` is not implemented.
- Acquisition, recipes, merchants, natural generation, loot, Rails/economy, and pricing remain deferred.
- Milestone 15 planting enforcement, Milestone 16 viewer-specific identification, and Milestone 17 final regression/closeout remain deferred and unstarted.

## Working-tree boundary

Milestone 14 is owner-approved and ready for its isolated commit. Pre-existing Corrective Milestone 11 changes remain present and must not be staged or committed. `FLOWER_SYSTEM_DECISIONS.MD` contains both its pre-existing Milestone 11 edits and an appended Milestone 14 decision section; those scopes remain distinguishable in the diff.

## Validation result

- Focused proposal/catalog/runtime reconciliation: PASS.
- `cleanTest compileJava processResources test`: PASS.
- Corrective Milestone 11 non-writing placeholder check: PASS, 182 generated files plus hash ledger.
- `git diff --check`: PASS.
- Isolated `runGameTestServer` common bootstrap: PASS. It logged `74 species, 67 crops, 7 flowers, 74 planting items` before the empty GameTest harness reported that no test functions were registered; Gradle completed successfully. No owner world was opened. The server emitted the repository's pre-existing dedicated-dist warning for `TitleScreenBackgroundMixin`; no Milestone 14 class referenced or loaded client code.
