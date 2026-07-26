# Banner and Dyeing Implementation Log

## 2026-07-26 - Milestone 14: Crafting and Existing Material-System Integration

### Preflight and platform inspection

- Verified branch `banners-dyetub`, HEAD `b5f2ec8fa06e981125857a7e247b3a667df0c8b3`, merge base with `patch-18`
  `62df1dc97c5113a86f9c0f258cb90538f31efe89`, divergence `0 14`, and the sole baseline untracked path `.claude/`.
  `.claude/` remained untouched, uninspected, and excluded from every Git operation.
- The two root specifications were intentionally absent as declared by the request and Milestone 13 baseline; they
  were not recreated. Their prior full-read evidence remains in Milestone 0, and the attached Milestone 14 brief was
  read in full.
- Inspected NeoForge/Minecraft 21.1.72 sources for `Recipe`, `CraftingRecipe`, `CustomRecipe`, `RecipeSerializer`,
  `RecipeType`, `CraftingInput`, `RecipeManager`, `CraftingMenu`, and `ResultSlot`. Normal crafting uses
  `RecipeType.CRAFTING`; custom JSON selection uses a `MapCodec`/`RegistryFriendlyByteBuf` serializer; result-slot
  handling asks the matched recipe for remainders on every completed craft.
- Inspected `UOMetalToolMaterial`, `BlacksmithCrafting`, `CraftableRegistry`, ingredient definitions, item registration,
  typed components, creative tabs, banner state/factory/validation, fabric/palette/mount registries, scaffold
  generation, and the existing registered-stack tests. The repository has no generic material identity shared by
  metals and fabrics and no existing brass item identity.

### Pattern, material, mount, and recipe architecture

- Added one shared maximum-stack-one `britannia_mod:banner_pattern` and one
  `britannia_mod:banner_pattern_definition` typed component using the existing `BannerDefinitionId` persistent and
  stream codecs. Reads are non-mutating; tooltips show the active definition, stable missing ID, and provisional
  warning. The existing items tab emits exactly 33 configured canonical variants and no raw pattern.
- Added tag-backed resolvers with typed unknown, ambiguous, missing, disabled, and unsupported failures plus exact tag
  evidence. Fabric inputs are cotton cloth, vanilla white wool, linen cloth, and the existing spiders silk. Iron
  reuses the same vanilla ingot identity recognized by `UOMetalToolMaterial.IRON`; brass uses the new minimal
  banner-mount input because the repository has no brass metal identity.
- Added one `britannia_mod:banner_crafting` serializer on the platform's existing `minecraft:crafting` recipe type.
  Its strict persistent codec requires current schema, definition ID, and fabric units 1 through 6 and rejects
  unknown or duplicate map fields; its network codec synchronizes the same three authoritative values.
- Matching is shapeless and exact: one matching configured pattern, footprint-area fabric slots of one material, one
  supported mount, no ambiguity, and no extra slot. Current definition/material/palette/natural-colour/mount data must
  be active. Missing/reloaded data returns no match/output and mutates no input.
- Assembly delegates to `BannerItemFactory.craftedMaterialBanner`. Output is always one fresh shared banner with the
  current schema, recipe/pattern definition, input-derived fabric and mount, authored natural colour, and no source
  pigment. `DyeResolver`, client render metadata, placement state, and custom C2S crafting packets are not used.
- The pattern is provisionally reusable. Valid recipe remainders contain exactly one unchanged configured pattern and
  preserve normal container/material/mount remainders. Invalid direct remainder evaluation returns no attempted-craft
  remainder.

### Generation, tests, and verification boundary

- Extended the established banner scaffold instead of introducing a second data generator. It deterministically emits
  33 `data/britannia_mod/recipe/banner/<definition>.json` files and six `tags/item/banner_*` files. There is no
  material/mount expansion and no dye recipe. The status generator records recipe ID/presence, pattern presence,
  fabric units, supported crafting materials/mounts, natural-colour validation, and manual status for every row.
- Initial restricted Gradle/scaffold launches failed because the wrapper distribution was denied network access;
  approved reruns used the configured Gradle/Java 21 toolchain. The first normal generator run hit the documented
  CRLF-vs-LF metadata mismatch and preserved prior scaffold files; explicit scaffold `--force` refreshed only its
  declared outputs, then generation reported 33 definitions and 33 recipes.
- The first focused test compile found four test-source mistakes (one renamed render-data accessor, one local variable,
  and a JSON/NBT type mismatch). The corrected run passed. An expanded run found one assertion expecting translated
  tooltip text while the fixture intentionally had no published global registry; it now asserts the stable
  diagnostic component representation. No production validation was weakened for these corrections.
- Focused Milestone 14 validation passes 16 tests. It executes all 264 definition/material/mount crafts, persistent
  state round trips, strict codec and network round trips, every current footprint cost, malformed-grid cases,
  tag evidence and ambiguity, missing/disabled/reloaded data, standard plus pattern remainders, and a deterministic
  repeated-crafting simulation.
- The corrected banner/dyeing package and final full clean test run each passed all 548 tests across 47 suites with
  zero failures, errors, or skips. Separate `clean` and `build` gates passed; the final scaffold check reported
  33 manifest entries, 33 definitions, 33 recipes, 33 active definitions, zero disabled definitions, and 33
  localization entries. The packaged JAR contains exactly 33 banner recipes and six crafting input tags, and contains
  no dye recipes, banner commands, or NPC/banner hooks. Compilation retained only the two documented pre-existing
  warnings for the mixin `@Overwrite` Javadoc and deprecated `OrderShieldItem.initializeClient`.
- No GameTest or live client was added. The repository still has no crafting-menu integration test harness. Automated
  bulk simulation covers per-craft consumption, mount limiting, one returned pattern, one fresh stack per output, and
  state isolation; it does not claim a manually observed shift-click or full-inventory transfer.
- Manual crafting, dyeing, placement, break/drop, and relog checks were not performed in this non-interactive run.
  Existing automated dyeing, item/placed rendering, placement, persistence, and lifecycle suites remain the
  regression boundary.

### Scope and next milestone

- Fabric costs, ingredient identities, reusable-pattern policy, survival acquisition, unlocks, recipe-book display,
  and economy remain explicitly provisional. The special recipes provide no representative recipe-book output.
- No pigment/dye/tub recipe, admin/debug command, NPC hook, direct placed-banner dyeing, mount swapping, placement or
  rendering behavior, catalogue identity/dimension change, final artwork, or Milestone 15 code was added.
- Stop after the Milestone 14 commit. Next milestone: Milestone 15 only.

## 2026-07-26 - Milestone 13: Placed Banner Rendering and Live State Sync

### Preflight baseline

- Verified branch `banners-dyetub`, HEAD `2ee72ccdf036a6e63a9d6fa55cabd1aa000fd29b`, merge base with `patch-18`
  `62df1dc97c5113a86f9c0f258cb90538f31efe89`, divergence `0 13`, and the sole preserved untracked path
  `.claude/`.
- Previously preserved `ModConfig.java`, root specification, `logs/`, and `tmp/` paths were intentionally absent
  before Milestone 13. They were not restored or recreated. `.claude/` remained untouched, uninspected, uncommitted,
  and excluded from staging.

### Shared appearance and placed rendering

- Refactored item appearance extraction around immutable `BannerAppearanceState`/`BannerAppearanceKey` values shared
  with placed rendering. The shared record contains only stable display inputs and resource/data generations; it
  contains no world, player, position, block entity, item-stack, mutable registry, render-buffer, or time reference.
- Registered exactly one client-only `BannerBlockEntityRenderer` for the authoritative anchor and zero child
  renderers. Anchor and part block models are explicitly invisible while their established selection/collision
  behavior is preserved.
- Added generated, two-sided cutout meshes for large 3x2, medium-wall 2x2, medium 1x2, small 1x1, and x-small 1x1
  families in both wall-parallel and wall-perpendicular orientations across all four facings. The renderer applies
  resolved material colour only to the dye-mask pass; the base, static overlay, and brass/iron mount passes remain
  untinted. Existing placeholder resources are reused and no final heraldic art was added.
- Geometry keys include appearance, persisted orientation/facing/footprint, family, mount, reload generations, and
  fallback state, but not anchor position or world identity. Persisted occupancy remains authoritative when current
  definition dimensions disagree, and the renderer emits one safe missing-content diagnostic without mutating the
  block entity or its children.

### Bounds, lighting, cache lifetime, and live state

- Dynamic render bounds are the persisted occupied-cell union plus a finite 0.125-block cloth/mount margin. The
  renderer uses a finite 64-block distance from the full bounds and relies on those bounds for ordinary frustum
  culling; malformed state falls back to a finite one-cell box.
- Lighting takes the maximum block/sky values from the anchor and at most six occupied cells whose chunks are already
  loaded. It never forces a chunk load and does not use full-bright lighting.
- Shared appearance and placed-plan caches are each access-bounded to 256 entries; placed diagnostic de-duplication
  is separately capped at 256. Display-data replacement and model/resource reloads invalidate item, appearance, and
  placed caches. Diagnostics are generation-aware and de-duplicated, so missing content can recover after a later
  data or resource reload without retaining stale plans.
- Added a server-only banner-state replacement boundary that preserves the placed structure, marks the anchor dirty,
  and calls `sendBlockUpdated`. Existing update-tag and update-packet paths synchronize initial tracking, late join,
  and live refresh; rendering re-extracts current anchor state and naturally selects a new bounded key.

### Tests, corrections, and verification boundary

- Added deterministic placed-render tests for shared appearance/tint separation, five families, two orientations,
  four facings, two mounts, anchor convention, bounds, fallback, cache bounds/invalidation, state changes, update
  tags/packets, and dedicated-server client isolation.
- The first focused run exposed a test-fixture error: it paired the production block-entity type with a custom test
  block. A narrow test `BlockEntityType` was introduced after Minecraft test bootstrap; one missing import and one
  premature static initializer were corrected. A production compile then exposed one reassigned facing captured by a
  lambda; extraction now captures the normalized final facing. No production behavior was weakened to satisfy tests.
- Focused `BannerPlaced*` validation passed 34 tests. The combined item/placed render selection passed, and the
  banner-dyeing package passed 532 tests across 45 suites with zero failures, errors, or skips before the clean full
  gate.
- No GameTest/client integration source was added: the repository has no established GameTest source/bootstrap or
  live client-render harness, and introducing one would be unrelated framework construction. The automated boundary
  verifies serialized update tags/packets and renderer state changes; it does not claim a manually observed live
  client session.
- Manual in-game matrix testing was not performed because there is still no safe configured-banner acquisition path.
  No recipe, creative entry, or command was added solely for QA.
- `tools\scaffold_banners.bat --check` initially reported all 49 generated text artifacts changed because the
  machine-wide Git checkout converted their tracked LF bytes to CRLF while the checker compares generated LF bytes
  literally. Normalizing only those files to their existing tracked bytes produced no content diff; the exact rerun
  passed with 33 manifest entries, 33 definitions, 33 active/0 disabled, 33 localization entries, 14 provisional
  names, 33 provisional dimensions, and five asset families. Scaffold generation was not run.
- The required banner package test passed 532 tests across 45 suites. From-clean full `gradlew.bat test --no-daemon
  --stacktrace` also passed 532 tests across 45 suites with zero failures, errors, or skips. `gradlew.bat clean
  --no-daemon`, `gradlew.bat build --no-daemon`, and `git diff --check` passed; the production build completed
  `jar`, `jarJar`, `assemble`, `check`, and `build`.
- `Britannia_Mod-0.1.7k-all.jar` contains one anchor renderer, zero part renderers, the shared appearance classes,
  placed render state/key/cache/geometry classes, five family models, three fabric layers, both mount models and
  textures, the missing-content resources, and the existing parallel/perpendicular block resources. Banner crafting,
  recipe, command, and direct-world dye handler class checks each found zero entries.

### Scope boundary and next milestone

- No crafting, recipe, admin command, NPC integration, direct placed-banner dyeing, mount swapping, final artwork,
  extra block entity, child renderer, or Milestone 14 work was added.
- Stop after the Milestone 13 validation and commit. Do not begin Milestone 14.

## 2026-07-21 - Milestone 12: Orientation-Aware Placement and Mount Variants

### Orientation selection and authority

- Added stable wall-parallel and wall-perpendicular placement modes without adding orientation to
  `BannerInstanceState`. Sneak-use cycles a server-owned, per-player ephemeral preference in stable order, normalizes
  it against the held definition, and synchronizes only the selected display value to the client. Logout and server
  stop clear preference state; no client-to-server orientation payload or item-stack mutation was introduced.
- Definitions that expose one orientation remain fixed to that mode. Definitions that expose both default
  deterministically to wall-parallel. Unsupported orientation and mount choices fail before world reads or mutation.

### Geometry, support, persistence, and lifecycle

- The shared local/world transform now drives placement, preview, part-to-anchor resolution, persistence, repair,
  removal, pick block, and diagnostic shapes for both orientations and every horizontal facing. Parallel width grows
  viewer-right and requires wall support behind every top-row cell. Perpendicular width grows outward from the wall
  and requires only the anchor's wall support.
- Anchor and generic part blockstates persist orientation. The anchor's versioned placed-structure record remains
  authoritative across reload and definition changes; legacy records still migrate to one-cell wall-parallel.
  Integrity repairs a stale anchor orientation property from the persisted record, rejects mismatched parts, and
  teardown remains duplicate-proof and rollback-safe.
- Brass and iron remain instance mount IDs flowing through planning, persistence, preview, item return, and render
  descriptors. They do not change occupied cells or support requirements in this milestone.

### Client ghost and presentation boundary

- Added a client-only world-space wireframe ghost after particles. It uses synchronized display-only registry data,
  the held configured shared banner, the selected orientation, already-loaded chunks, and the same pure transforms as
  server planning. It renders planned cells, required supports, dimensions, orientation, mount, and typed local
  failures without changing blocks or sending per-frame packets.
- Locally clear plans remain advisory because server protection is not fully knowable on the client. The preview says
  so explicitly and server placement always revalidates. Brass uses gold, iron gray, missing mount data magenta,
  blocked cells red, and invalid supports orange. Placed models remain neutral diagnostic assets; final cloth and
  mount artwork are intentionally deferred.

### Gate D status and validation evidence

- The generated catalogue status now contains one row for every stable ID with dimensions, supported orientations,
  supported/default mounts, automated parallel/perpendicular/brass/iron results, manual result, and content status.
  All 33 rows pass the automated matrix; manual in-game validation was not performed. Final names, dimensions,
  per-definition orientations, per-definition mounts, and placed artwork remain unapproved.
- Normal scaffold generation and `tools\\scaffold_banners.bat --check` passed with 33 manifest entries,
  33 definitions, 33 active/0 disabled entries, 33 localization entries, 14 provisional names, 33 provisional
  dimensions, and five placeholder asset families.
- Focused M12 validation passed with 88 tests across 13 suites, 0 failures, 0 errors, and 0 skipped. Its planner
  matrix executes 1,056 successful plans: 33 definitions x two mounts x two orientations x four facings.
- `gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace` passed with
  496 tests across 41 suites before the final planner read-order hardening. Final `gradlew.bat clean --no-daemon
  --stacktrace` passed in 25 seconds; the clean full `gradlew.bat test --no-daemon --stacktrace` passed in 2 minutes
  57 seconds with 498 tests across 41 suites, 0 failures, 0 errors, and 0 skipped.
- Final `gradlew.bat build --no-daemon --stacktrace` passed in 51 seconds and completed `jar`, `jarJar`, `assemble`,
  `check`, and `build`. `Britannia_Mod-0.1.7k-all.jar` contains the M12 preference, preview, ghost, payload, and
  perpendicular-model resources; it contains zero part block entities, placed block-entity renderers, or part item
  models. Existing compiler warnings are unchanged and no M12 warning was introduced.
- Milestone commit subject: `feat(banners): add orientation-aware placement and mount variants`; the resulting hash
  is reported in the handoff because a commit cannot contain its own hash.

### Scope boundary and next milestone

- No recipe, creative-tab entry, admin/debug command, NPC integration, direct placed-banner dyeing, new banner item,
  per-definition block, part block entity, placed block-entity renderer, or final heraldic asset was added.
- Stop at Gate D after the Milestone 12 commit. Do not begin Milestone 13.

## 2026-07-21 - Milestone 11: Multi-Block Anchor and Occupied Parts

### Structure content, footprint, and transform

- Kept `britannia_mod:banner` and its existing `britannia_mod:banner` block entity as the single authoritative anchor.
  Added exactly one generic `britannia_mod:banner_part` occupied-part block with no block entity and no `BlockItem`.
  Parts store only bounded local offsets plus facing; the anchor alone owns the complete `BannerInstanceState` and
  authoritative placed footprint.
- All 33 active wall-parallel definitions are eligible from catalogue dimensions: 13 use 1x1, 8 use 1x2, 6 use
  2x2, and 6 use 3x2. Maximum occupancy is six cells. Placeholder/provisional status does not affect eligibility,
  and there is no stable-ID or group-name footprint switch.
- The clicked wall-adjacent target is the top-left anchor when viewed from the front. `FACING` points outward;
  viewer-right is `FACING.getCounterClockWise()`. Local horizontal offsets increase viewer-right and vertical offsets
  increase downward. Cells and stored offsets are deterministically ordered by vertical offset and then horizontal
  offset, with the anchor first.

### Saved placement state and Milestone 10 migration

- Added versioned placed-structure schema 1 under anchor NBT key `placed_structure`. It stores stable orientation,
  width, height, and the complete ordered occupied offsets. The stored footprint remains authoritative if a later data
  pack changes a definition's dimensions, and it can be decoded without the live banner registry.
- A Milestone 10 anchor missing `placed_structure` migrates to a valid one-cell wall-parallel placement and is marked
  changed for the next save. Unknown/future or malformed placement data is contained as structurally invalid without
  erasing otherwise decodable banner state.
- The same banner and placement records are used by disk NBT, update tags, and update packets. Placement transfers both
  records before child placement and synchronizes only after the complete structure verifies.

### Placement transaction and support

- Planning remains read-only and server-authoritative. It validates item state, registry references, profile,
  rectangular dimensions, every world/border/build-height position, every already-loaded chunk, replaceability,
  protection, all top-row wall supports, anchor block-entity compatibility, part offset encoding, and final blockstate
  acceptance before mutation. It never requests or force-loads a chunk.
- The support policy is one sturdy wall face behind every top-row cell; lower rows do not require their own wall face.
  Placement order is anchor block, anchor banner/placement data, row-major parts, exact cell and anchor-state
  verification, client synchronization, neighbor/effect notification, and finally item consumption. Survival consumes
  exactly one shared banner after success; creative consumes none.
- Every failure restores exact pre-placement blockstates in reverse mutation order with drops suppressed. Rollback and
  structure mutation run under the shared lifecycle guard, and consumption/effects never occur for a failed attempt.

### Removal, drops, pistons, and pick block

- One central lifecycle service owns anchor break, child break, support loss, explosions, external replacement, orphan
  cleanup, recovery failure, and rollback cleanup. A per-level/anchor reentrancy guard prevents callbacks from removing
  twice or duplicating drops.
- Survival anchor break, child break, and support loss remove every occupied banner cell and emit exactly one item at
  the anchor, reconstructed from the anchor's exact state. Creative removal emits none. Explosion policy is complete
  removal with no banner drop. Vanilla block loot/player-destroy callbacks emit no competing item.
- External replacement preserves the replacement cell and cleans the remaining known banner cells without a drop.
  Both anchor and part use `PushReaction.BLOCK`, preventing piston push and sticky pull.
- Pick block on the anchor reconstructs the exact shared banner item. Pick block on a child resolves its encoded anchor
  without loading chunks, validates membership against synchronized anchor placement data, and returns the same item.

### Chunk integrity and recovery

- Chunk-load inspection is deferred until `ServerTickEvent.Post`, because NeoForge warns against level interaction in
  the load callback. Placement, part resolution, inspection, and repair use only already-loaded chunks through
  `hasChunk`, `hasChunkAt`, and `getChunkNow`.
- A child whose anchor chunk is unloaded is left untouched. A definitively absent/invalid/mismatched anchor makes the
  child an orphan and removes it without a drop. Missing expected parts are repaired only when their chunks are loaded
  and cells are replaceable. Any obstruction is preserved and the remaining known structure is removed without a
  drop; repair never overwrites unrelated content.
- Persisted offsets drive reload, integrity, repair, removal, and definition-change behavior. Cross-chunk placement is
  accepted only when every involved chunk is already loaded; subsequent independently ordered chunk loads do not cause
  false orphan deletion.

### Assets, scope, and validation evidence

- Packaged diagnostic resources contain the existing anchor blockstate/model and the new part blockstate/model. The
  part is a thin full-cell occupancy marker with outline/collision geometry supplied by its block. The production JAR
  contains `BannerPartBlock`, the anchor block entity, and all structure services; it contains no part item model,
  part block entity, banner block-entity renderer, or placed layered renderer.
- No GameTest was added because the repository has no GameTest source root, bootstrap, templates, or registration.
  Unit tests exercise actual blocks and block entities plus the footprint, planner, transaction, persistence, lifecycle
  policy, resources, and source-level platform boundaries. Live in-game interaction/visual checks were not performed
  because no safe configured-banner acquisition path exists; no recipe, creative entry, or admin command was added for
  QA.
- Narrow Milestone 11 tests passed: 38 tests, 0 failures, 0 errors, 0 skipped. The first run had one test-fixture
  failure because a multi-block support position was hard-coded at y=70 while the planned click was at y=0; the test
  now derives that support position from the plan, and production code was unchanged.
- `tools\\scaffold_banners.bat --check` passed: manifest=33, definitions=33, active=33, disabled=0,
  localization=33, provisional names=14, provisional dimensions=33, asset families=5.
- `gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace` passed with
  480 tests, 0 failures, 0 errors, 0 skipped across 38 suites. `gradlew.bat clean --no-daemon` passed in 13 seconds.
  The clean `gradlew.bat test --no-daemon --stacktrace` passed in 1 minute 53 seconds with the same 480/0/0/0 result.
  `gradlew.bat build --no-daemon` passed in 22 seconds and completed `jar`, `jarJar`, `assemble`, `check`, and `build`.
- One earlier `compileTestJava` launcher timed out while Gradle daemons remained active; `gradlew.bat --stop` stopped two
  daemons and the immediate rerun passed without a source change. Existing compiler warnings remain the missing
  `@Overwrite` Javadoc on `PlayerSleepMixin` and deprecated-for-removal `OrderShieldItem.initializeClient`; no new
  compiler warning was introduced.

### Counts, limitations, and next milestone

- Production content remains 33 active/0 disabled banners, 4 materials, 4 palettes, 7 pigments, and 2 mounts.
  Registration remains one shared banner item, one dye tub, seven pigment items, and three total data components
  including wine data. This milestone adds one block but zero items and zero block-entity types.
- Development presentation remains static diagnostic geometry. Wall-perpendicular placement, orientation selection,
  cycling, placement ghosts, mount-dependent occupancy, placed layered rendering, direct placed-banner dyeing,
  recipes, commands, NPC integration, and final heraldic artwork remain out of scope.
- Ordinary external `setBlock` replacement is handled through block callbacks. A world-edit tool that bypasses normal
  callbacks can temporarily leave parts until deferred chunk integrity processing; no universal hook exists for tools
  that bypass both callbacks and chunk lifecycle.
- No approved catalogue identity or dimension changed. Next milestone: Milestone 12 only. It has not started.

## 2026-07-21 - Milestone 10: Single-Block Placement Foundation

### Scope and registration

- Added one focused `BannerBlockRegistry` with exactly one `britannia_mod:banner` block and one matching block entity
  type. No `BlockItem` or per-definition block registration was added; the existing shared `BannerItem` remains the
  only inventory form and now routes `useOn` through the placement service.
- Eligibility is data-driven from the immutable registry snapshot. A banner is placeable only when its active
  definition is exactly 1x1 with a supported wall orientation and its stored material, palette colour, optional source
  pigment, and mount all remain resolvable and active. The 13 currently eligible definitions are selected by these
  traits rather than an ID allowlist; the other 20 definitions fail with a typed deferred/unsupported result.
- Added a four-way horizontal blockstate and one thin diagnostic block model that reuses the existing missing texture.
  This milestone intentionally adds no block-entity renderer, layered placed-banner rendering, child/part blocks, or
  final heraldic art.

### Planning, authority, and transaction boundary

- Placement is server-authoritative. The planner is read-only and validates the shared item, complete stored state,
  current registry publication, single-block eligibility, horizontal clicked face, replaceable target, world and
  border bounds, sturdy wall support, protection checks, block-entity compatibility, and state acceptance before any
  mutation. `FACING` points outward and the support is the block opposite that direction.
- The executor mutates exactly one target cell, locates the new banner block entity, transfers the complete
  `BannerInstanceState`, verifies exact equality, and only then consumes one item in survival. Creative placement does
  not consume. Sounds and the block-place game event occur only after a committed success.
- A placement, block-entity, state-transfer, or verification failure restores the exact original target block state
  with drops suppressed. Typed failures are localized to server action-bar feedback. The live adapter respects
  `ServerLevel.mayInteract`, `Player.mayUseItemAt`, the world border, build height, replaceability, and sturdy-face
  support checks.

### Persistence, synchronization, support, and item return

- `BannerBlockEntity` persists the complete versioned state through `BannerInstanceState.CODEC`, including banner
  definition, material, resolved colour, optional source pigment, and mount. Load failures are contained and logged as
  structurally invalid state rather than crashing or silently substituting another catalogue entry.
- Runtime status distinguishes configured-valid, configured-with-missing-references, unconfigured, and structurally
  invalid. Missing data-pack references never rewrite the stored stable IDs. State changes mark the entity changed and
  send the normal block-entity update; update tags and packets use the same persisted representation.
- Normal break, support loss, explosion/default block drops, and pick-block all use the block entity as the sole source
  for reconstructing one shared banner item. Valid and reference-missing states preserve all six state fields exactly.
  Unconfigured or structurally invalid entities safely return one raw, unconfigured shared banner rather than a guessed
  configuration. There is no duplicate loot-table path.
- The wall-parallel shape rotates with `FACING`. Removing the supporting face transitions the block to air through
  normal neighbor-update behavior, allowing the same one-item drop path to run.

### Automated coverage and corrections

- Added 33 focused tests for data-driven 13/20 eligibility, all typed planning failures, one-cell targeting and outward
  facing, planner non-mutation, exact state transfer, survival/creative consumption, every rollback branch, natural
  and dyed persistence, optional pigment, update tag/packet application, invalid NBT containment, missing-reference ID
  preservation, break/pick transfer, registrations, absence of a `BlockItem`, shape/support/drop ownership, assets,
  localization, and common/client/Milestone-11 scope isolation.
- No GameTest was added. The repository has no GameTest source root, bootstrap, templates, or registration. The tests
  instead exercise the actual block, block entity, shared item/component codecs, planner, transaction executor, and
  packaged resources. No claim of live-world visual or interaction testing is made.
- The first focused run exposed a test-fixture issue: constructing a vanilla block-entity update packet calls through
  the attached level for registry access. The packet-application test was corrected to construct the actual packet
  from the persisted update tag and apply it through `onDataPacket`; production packet creation remains unchanged.
- During the clean full-suite run, a short-lived earlier Gradle launcher retained
  `build/test-results/test/binary/output.bin`. `gradlew --stop` released the two stale daemons; the rerun passed without
  changing code or tests.

### Final commands and results

1. Normal scaffold generation passed: manifest=33, definitions=33, active=33, disabled=0, localization=33,
   provisional names=14, provisional dimensions=33, asset families=5.
2. Focused Milestone 10 tests passed: 33 tests, 0 failures, 0 errors, 0 skipped.
3. `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace`
   passed with 460 tests, 0 failures, 0 errors, and 0 skipped across 35 suites.
4. Final `.\gradlew.bat clean --no-daemon` passed in 11 seconds.
5. Final `.\gradlew.bat test --no-daemon --stacktrace` passed from the clean output: 460 tests, 0 failures,
   0 errors, 0 skipped across 35 suites.
6. Final `.\gradlew.bat build --no-daemon --stacktrace` passed in 16 seconds; `jar`, `jarJar`, `assemble`, `check`,
   and `build` completed. `Britannia_Mod-0.1.7k-all.jar` contains the new block/BE/placement classes, one banner
   blockstate, one banner block model, and localization resource.
7. `git diff --check` passed; only repository line-ending conversion notices were emitted.

The unchanged compiler warnings are missing `@Overwrite` Javadoc on `PlayerSleepMixin` and the deprecated-for-removal
`Item.initializeClient` override in `OrderShieldItem`. No new warning was introduced.

### Counts, manual boundary, limitations, and next milestone

- Production content remains 33 active/0 disabled banners, 4 materials, 4 palettes, 7 pigments, and 2 mounts. Exactly
  13 current definitions are 1x1 wall-placeable and 20 remain deferred. New registrations are one block, one block
  entity type, and zero separate block items; existing registrations remain one shared banner, one dye tub, seven
  pigment items, and three total data components including wine data.
- The live survival/creative, support-loss, protected-area, explosion, pick-block, save/reload, relog, and multiplayer
  matrix was not performed because the repository still has no safe configured-banner acquisition path. No recipe,
  creative entry, debug command, or Milestone 15 command was introduced solely for QA. Automated state, transaction,
  codec, resource, and ownership tests are not represented as in-game screenshots.
- Placed banners use diagnostic static geometry only. Custom item names are not part of `BannerInstanceState` and are
  therefore not transferred through placement. Multi-block layouts, placed layered rendering, direct-world dyeing,
  crafting, commands, and NPC integration remain outside this milestone.
- Next milestone: Milestone 11 only. It has not started.

## 2026-07-21 - Milestone 9: Layered Item Rendering

### Rendering API and side boundary

- Verified the exact NeoForge 21.1.72 sources before selecting an API. `Item.initializeClient` is deprecated for
  removal and directs extension users to `RegisterClientExtensionsEvent`; Milestone 9 adds neither that deprecated
  override nor a BEWLR. The banner instead uses current `ModelEvent.RegisterAdditional`,
  `ModelEvent.ModifyBakingResult`, stack-aware vanilla `ItemOverrides`, standard `applyTransform`, and NeoForge baked
  render passes.
- One `BannerItemBakedModel` wraps the one shared `britannia_mod:banner` inventory model. Its override resolves a
  stack-specific immutable appearance to the five family geometry models plus the selected mount pass. It delegates
  GUI, first/third-person, ground, and fixed transforms to the repository-standard item model.
- Renderer, model repository, cache, state extractor, tint registration, preview stack helper, and connection cleanup
  are isolated under `client/banner`. Common state projections and payloads contain no client or Blaze3D imports.
  `BritanniaMod` references only the common render-data sync listener.

### Client render data and authority

- Added an immutable display-only projection of active banner assets/status, material natural-colour and palette
  display sRGB entries, and mount assets. `S2CBannerRenderDataPayload` synchronizes this projection on login and
  `OnDatapackSyncEvent`; the client publishes it atomically with a monotonically increasing generation and clears it
  on disconnect.
- Server gameplay continues to use `RegistrySnapshot`; the client projection is never used for dye resolution,
  confirmation, validation, or mutation. Stable resolved-colour IDs remain authoritative. Missing/stale data, unknown
  server IDs, and absent local assets select the diagnostic fallback rather than another lexical entry or colour.
- Server data-pack overrides to display metadata are synchronized. Models/textures remain client resource-pack
  content, so a server override can select only an asset available in the client's packs; new server-only assets fall
  back diagnostically. This exact multiplayer boundary is recorded in `OPEN_QUESTIONS.md`.

### Render state, layers, cache, and fallback

- The typed immutable state contains definition/material/colour/mount IDs, canonical display sRGB, geometry, neutral
  fabric base, grayscale dye mask, untinted static overlay, mount geometry/texture, placeholder status, natural flag,
  typed fallback reason/stable diagnostic ID, and client-data generation. Extraction reads but never validates,
  repairs, substitutes, or mutates `ItemStack` state.
- The render key contains every appearance field plus client-data and baked-resource generations. It intentionally
  excludes `ItemStack`, source pigment, custom name, player, level, screen, registry maps, and timestamps.
- Layer order is neutral fabric base, tint-index-1 dye mask, untinted static overlay, and untinted brass/iron mount.
  The item-colour handler returns canonical palette ARGB only for tint index 1. Authored mask alpha/quads control tint
  participation; overlay and mount faces have no tint index.
- A synchronized access-ordered client cache stores at most 256 immutable-key-to-baked-model entries. Model bake or
  resource reload and client render-data replacement clear entries and missing-log identities. Diagnostics are
  de-duplicated by reason, stable ID, data generation, and resource generation, preventing per-frame log spam while
  allowing a later successful reload.
- Missing component, registry, definition, material, colour, mount, geometry, base, mask, overlay, mount model, or
  mount texture returns the one missing-item model. Original components and stable IDs remain untouched.

### Assets and preview integration

- Updated the scaffold templates before regenerating scaffold-owned files. The item boundary now packages five
  visibly distinct family JSON models, one neutral base texture, one grayscale/cutout dye mask, one untinted overlay,
  one missing texture/model, and visibly different brass and iron mount models/textures. This is diagnostic
  placeholder presentation, not final heraldic art.
- Mount definitions now reference `banner/mount/brass` and `banner/mount/iron`. The scaffold metadata owns the new
  outputs and `--check` remains deterministic. Catalogue identities, names, dimensions, and all 33 definition IDs are
  unchanged.
- Extended only the S2C preview payload with current/proposed display-only stable-ID descriptors. The screen creates
  detached client stacks and calls the normal item renderer twice, so inventory and preview share the same layered
  architecture. Existing current/new swatches and localized details remain. The proposed stack never mutates the held
  item, and `C2SConfirmDyeApplicationPayload` remains exactly one session UUID.

### Automated coverage, initial failures, and corrections

- Added 25 focused rendering tests covering all 33 definitions, all five families, four materials, natural/dyed
  cotton, brass/iron, canonical colour projection, render-key equality/invalidation/exclusions, exact layer order and
  tint indices, alpha/cutout pixels, every typed fallback, non-mutation, asset packaging/ownership, bounded cache,
  reload invalidation, log de-duplication, packet round-trip, atomic publication, detached previews, shared screen
  renderer, confirmation authority, current model events/transforms, and client/common isolation.
- The first restricted scaffold and Gradle attempts could not download/use the wrapper distribution because sandbox
  socket access was denied. Approved retries used the configured Gradle 8.9/Java 21 toolchain.
- The first `compileTestJava` failed because the Milestone 8 payload test still called the former three-argument open
  payload constructor. It was corrected to provide current/proposed render descriptors; compilation then passed.
- The first 426-test banner/dye regression run had one failure: `BannerScaffoldToolTest` expected five placeholder
  models but the new missing diagnostic model makes six. The assertion was corrected and expanded to require the two
  mount models and two mount textures. The rerun and clean full suite passed without weakening tint, fallback, cache,
  authority, or isolation coverage.

### Final commands and results

1. Normal scaffold generation passed: manifest=33, definitions=33, active=33, disabled=0, localization=33,
   provisional names=14, provisional dimensions=33, asset families=5.
2. `.\tools\scaffold_banners.bat --check` passed in 25 seconds with the same counts.
3. Focused `BannerRender*` tests passed after final disabled-content coverage in 1 minute 38 seconds: 25 tests,
   0 failures, 0 errors, 0 skipped.
4. `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace`
   passed after the documented scaffold count correction: 426 tests, 0 failures, 0 errors, 0 skipped.
5. Final `.\gradlew.bat clean --no-daemon` passed in 19 seconds.
6. Final `.\gradlew.bat test --no-daemon --stacktrace` passed immediately after clean in 56 seconds: 427 tests,
   0 failures, 0 errors, 0 skipped across 30 suites.
7. Final `.\gradlew.bat build --no-daemon` passed in 36 seconds; `jar`, `jarJar`, `assemble`, `check`, and
   `build` completed. The production JAR contains 24 banner-client class entries, 8 banner models, 6 banner textures,
   and the common render-data payload.
8. `git diff --check` passed; only repository line-ending conversion notices were emitted.

The unchanged compiler warnings are missing `@Overwrite` Javadoc on `PlayerSleepMixin` and the deprecated-for-removal
`Item.initializeClient` override in `OrderShieldItem`. No new warning was introduced.

### Counts, manual boundary, limitations, and next milestone

- Production content remains 33 active/0 disabled banners, 4 materials, 4 palettes, 7 pigments, and 2 mounts.
  Registrations remain one shared banner, one dye tub, seven pigment items, and two banner/dye components (three total
  component registrations including pre-existing wine data). Cache capacity is 256 entries and the tested resource
  and client-data generation replacements retain zero obsolete entries after clear.
- The manual size/material/mount/context rendering matrix was not performed. There is still no safe configured-banner
  acquisition path, and no recipes, creative catalogue, or Milestone 15 command was added solely for QA. Automated
  model/pixel/JAR tests are not represented as live inventory, hand, dropped-item, frame, or preview screenshots.
- Final heraldic art, placed-banner blocks/entities/rendering, placement, collision, drops, crafting, recipes,
  commands, NPC integration, and direct-world dyeing remain absent.
- Next milestone: Milestone 10 only. It has not started.

## 2026-07-20 - Milestone 8: Dye Preview and Confirmed Item Dyeing

### Player flow and interaction routing

- Completed the first player-facing item dyeing path: a loaded registered dye tub in `MAIN_HAND` plus a configured
  shared banner in `OFF_HAND` is validated and resolved on the server, opens a client-only preview, and mutates only
  after a server-confirmed apply. Physical left/right hands are not hard-coded.
- `DyeTubItem` routes a recognized off-hand `PigmentItem` first through the unchanged Milestone 6 loading service;
  otherwise the shared banner item requests preview; empty or unknown items retain the existing typed invalid-off-hand
  loading result. Off-hand tub use returns pass before client or server mutation.
- Normal server-side item use is already the preview intent, so no redundant `C2S_RequestDyePreview` was added.
  Client prediction performs no component write, consumption, success sound, or particle emission.

### Network architecture and authority

Four typed play payloads were added under `network/payload/dye` and registered through the existing protocol-`1`
`PayloadRegistrar` path in `NetworkHandler`:

| ID | Direction | Fields |
|---|---|---|
| `britannia_mod:open_dye_preview` | S2C | session UUID, display-only preview projection, lifetime milliseconds |
| `britannia_mod:confirm_dye_application` | C2S | session UUID only |
| `britannia_mod:cancel_dye_preview` | C2S | session UUID only |
| `britannia_mod:dye_application_result` | S2C | session UUID, typed result enum, close-screen flag |

- The display projection contains localization keys, explicit/nearest match type, perceptual distance, current/new
  sRGB swatches, and placeholder/provisional flags. It is never returned to or trusted by the server.
- Confirm and cancel contain no pigment, material, banner definition, mount, resolved colour, match type, distance,
  banner state, or tub state. C2S handlers enqueue authoritative work and resolve the `ServerPlayer`; S2C handlers are
  selected only on the client distribution and open/update `DyePreviewScreen` from `ClientNetworkHandler`.
- Common packet records and all common preview/session/application classes contain no Minecraft client or Blaze3D
  imports. The client-only screen is annotated and isolated under `client/screen`.

### Preview session design

- `DyePreviewSessionService` is runtime-only, synchronized, and keyed by player UUID. Sessions use random UUIDs,
  expire after 30,000 milliseconds, and allow exactly one active session per player. Creating a new preview replaces
  the previous session.
- A session stores creation/expiry time, expected main/off item identities, defensive exact copies of both stacks,
  authoritative pigment, complete current banner/tub states, resolved colour/match/distance, display projection, and
  the immutable registry snapshot reference used for resolution.
- Exact stack matching includes item, count, and every component, so custom names, custom data, unrelated components,
  banner state, tub state, and hand swaps all stale the relevant confirmation.
- The registry system has no numeric generation counter. Snapshot publication replaces the immutable aggregate
  object, so reference identity is the exact runtime publication identity; any reload publication requires a fresh
  preview.
- A matching confirmation removes/marks the session consumed before validation or mutation. Short-lived terminal
  tombstones distinguish expired, cancelled, replaced, and replayed IDs without persisting data. Cleanup is lazy on
  create/claim/cancel/count; no tick handler or saved player data exists.
- Logout, dimension change, death, and server stop remove sessions. Successful and failed matched confirmations are
  one-use; cancellation removes the matching session; duplicate confirmation returns `SESSION_REPLAYED`.

### Preview validation and display

Validation is non-mutating and ordered across: exact main-hand tub; exact off-hand shared banner; configured banner
component; loaded/non-depleted tub; published registry availability; active/disabled pigment; complete banner
validation (definition, material, palette, stored colour, mount, supported mount); resolver success; and the generic
`DyeableItem` colour-update plan. Missing stored colour is not auto-repaired to open a preview.

Typed failures distinguish invalid hands, empty/depleted tub, unconfigured/invalid banner, unavailable registry,
missing/disabled pigment/material/definition/mount, missing palette, resolver failure, no compatible colour, generic
dyeable rejection, and session creation failure. Preview creation changes neither stack and never decrements uses.

`DyePreviewScreen` displays banner/material/mount, current colour and optional source pigment, tub pigment, resolved
new colour, explicit or closest-available label, static registered banner icon, current/new authoritative swatches,
placeholder warning, restrained provisional-dimension warning, Cancel, and Apply Dye. Apply disables immediately,
remains disabled in flight, and disables on the advisory local timeout. Escape, inventory key, and Cancel send only
the opaque cancellation intent. The screen has no menu, container, layered renderer, dye mask, overlay, geometry, or
final heraldry dependency; layered item rendering remains deferred to Milestone 9.

### Confirmation, atomicity, finite use, and feedback

Confirmation claims the player-bound session, checks expiry, re-reads exact hands, compares exact stack copies,
verifies registered item identities, checks the same registry publication, revalidates tub and generic banner state,
re-runs `DyeResolver`, and requires an exact match with the previewed authoritative `DyeResult`.

The service then detects exact no-op, plans the banner colour/source update and finite tub decrement completely, and
only then applies the banner component followed by the tub component. Expected failures change neither stack. An
unexpected runtime failure logs player/session/pigment/material context and restores the prior approved components on
a best-effort narrow rollback boundary.

- Unlimited tub: component remains exactly unchanged.
- Finite tub: one successful real application decrements exactly once.
- Finite one: becomes zero while retaining its pigment; later preview/apply is typed `TUB_DEPLETED`.
- Exact same colour and pigment: `ALREADY_DYED`, no mutation, use, sound, or particles; screen closes.
- Same colour with a different pigment: source provenance updates and one finite use is consumed.
- Banner schema, definition, material, mount, custom name, and unrelated components remain unchanged.
- Success emits one vanilla `DYE_USE` sound, six restrained `HAPPY_VILLAGER` particles, localized action-bar feedback,
  and a typed close result. Failures and cancellation emit no success effects.

### Automated coverage and corrections

- Added registered-`ItemStack` JUnit coverage for preview validation, missing/disabled registry content, no compatible
  colour, resolver failure, non-mutation, finite preview, exact stack staleness, session uniqueness/replacement,
  expiry/cancel/disconnect/replay/cross-player rejection, explicit and nearest application, finite/unlimited use,
  exact no-op, same-colour provenance, sequential re-dyes, resolver/registry changes, component preservation,
  rollback, all four packet codecs/IDs, C2S authority fields, view-model state, localization, routing, and client-class
  isolation.
- No GameTest was added. The repository still has no GameTest source root, annotated bootstrap, templates, or test
  registration. Registered production-shaped items/components plus isolated packet/client-server boundaries exercise
  this item-only milestone without introducing unrelated framework infrastructure. No live in-game claim is made.
- The first restricted `compileJava` attempt could not access the Gradle distribution because sandbox networking was
  denied. The approved retry passed with only the two existing compiler warnings.
- The first focused test command completed after the tool timeout; its XML proved 23 tests, 0 failures/errors/skips.
  The expanded focused command then passed normally: 27 tests, 0 failures/errors/skips.
- The first complete 402-test banner/dye run found two obsolete Milestone 6 scope-only guards: one included the now-
  required `DyeTubItem` banner routing, and one prohibited every future dye-named payload/screen. Both guards were
  narrowed to the exact unchanged Milestone 6 loading-core files; their no-client/no-banner/no-payload guarantees
  remain intact, and no stale-state, replay, authority, or atomicity assertion was weakened.
- Corrected required banner/dye command: 402 tests, 0 failures, 0 errors, 0 skipped.

### Final commands and results

1. `.\tools\scaffold_banners.bat --check` passed in 35 seconds: manifest=33, definitions=33, active=33,
   disabled=0, localization=33, provisional names=14, provisional dimensions=33, asset families=5.
2. `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace` passed
   after the documented scope-guard correction: 402 tests, 0 failures, 0 errors, 0 skipped.
3. `.\gradlew.bat clean --no-daemon` passed in 39 seconds.
4. `.\gradlew.bat test --no-daemon --stacktrace` passed from clean state in 3 minutes 20 seconds: 402 tests,
   0 failures, 0 errors, 0 skipped.
5. `.\gradlew.bat build --no-daemon` passed in 53 seconds; check, jar, jarJar, assemble, and build completed.
6. `git diff --check` passed during source review; final staged diff checks are recorded in the handoff report.

The unchanged compiler warnings are missing `@Overwrite` Javadoc on `PlayerSleepMixin` and the deprecated-for-removal
`Item.initializeClient` override in `OrderShieldItem`.

### Counts, manual boundary, limitations, and next milestone

- Production content remains 33 active/0 disabled banners, 4 materials, 4 palettes, 7 pigments, and 2 mounts.
  Gameplay registrations remain one shared banner, one dye tub, seven pigment items, and two banner/dye typed data
  components (three total registrations in `DataComponentRegistry`, including pre-existing wine data).
- The full manual obtain/open/cancel/apply/stale/double-click/expiry/re-dye/relog checklist was not performed because
  there is still no safe banner acquisition path before Milestone 15 and no live client/server session was launched.
  Automated persistence and handler tests are not presented as in-game, multiplayer, relog, or world-save evidence.
- Preview is text, static icon, and swatches only. There is no layered item rendering, placed banner, block, block
  entity, placement, crafting, recipe, admin command, NPC integration, or direct-world dyeing.
- Session lifetime, finite-use zero policy, and exact no-op close UX remain provisional as recorded in
  `OPEN_QUESTIONS.md`.
- Next milestone: Milestone 9 - Layered Item Rendering - only. It has not started.

## 2026-07-20 - Milestone 7: Generic Dyeable Item API and Banner Item State

### Files and architecture

- Added exactly one shared `britannia_mod:banner` registration through `BannerItemRegistry`. It has provisional
  maximum stack size 1 and no prototype banner component, so a raw stack is explicitly unconfigured rather than an
  arbitrary `large_01`/cotton/brass banner. No per-definition, material, colour, or mount items were registered.
- Added exactly one `britannia_mod:banner_instance_state` component to `DataComponentRegistry`, attaching the existing
  immutable `BannerInstanceState.CODEC` for persistent `ItemStack` storage and
  `BannerInstanceState.STREAM_CODEC` for component network synchronization. No raw custom NBT or custom packet is an
  authoritative banner-state path.
- Added the common-side `DyeableItem` contract plus banner-neutral read, update-plan, and typed-failure values. It
  exposes material, resolved colour, optional source pigment, pigment applicability, pure colour-update planning, and
  explicit stale-safe application without assuming `BannerInstanceState` for future textiles.
- Added `BannerItemStateAccess`, typed validation status/issues, immutable colour-update and repair plans, and explicit
  apply methods. Reads, validation, tooltips, planning, and reload inspection never mutate stacks. Apply changes only
  the typed banner component, preserving custom names and every unrelated component.
- Validation distinguishes valid, valid-with-diagnostics, unconfigured, registry-unavailable, repairable, and invalid
  state. Issues retain stable IDs for missing/disabled definitions, materials, pigments and mounts; missing palettes,
  colours, unsupported mounts, component/decode failures where observable, invalid items, and stale plans.
- A missing source pigment is diagnostic while an existing resolved colour remains usable. Missing definition,
  material, palette, or mount never triggers substitution or erasure.
- Added `BannerItemFactory` typed results for natural cotton admin banners, crafted-material natural banners, and
  fully specified development banners. Cotton/material natural colours are read from the active material/palette;
  mounts and every supplied reference are validated without silent fallback.
- Added missing-colour repair planning. An active stored pigment is re-resolved with `DyeResolver`; otherwise the
  material natural colour is proposed. Definition, material, mount, schema version, and historical source-pigment ID
  are retained. Natural fallback deliberately retains unavailable pigment provenance and reports it diagnostically.
- Added safe localized tooltip projection for natural, dyed, placeholder, provisional-dimension, both-orientation,
  one-orientation, unconfigured, repairable, and missing-reference states. Configured names use the active banner
  definition; player custom names still win through normal `ItemStack` behavior.
- Added one static item model using the existing original high-contrast banner placeholder texture. No renderer or
  final heraldic artwork was added.
- Creative/development access uses the smallest safe boundary: the shared item is registered but no raw or generated
  banner stack is added to the creative tab because its callback cannot safely depend on the reload-published server
  data snapshot. Factory integration tests cover acquisition until Milestone 15.

### Factories, persistence, merge, and repair evidence

- Natural cotton admin factory: success; cotton natural colour, definition default mount, no source pigment.
- Crafted factories: cotton, wool, linen, and silk all succeed with their authored natural colours. Explicit brass
  and iron mounts succeed; unsupported/missing references return typed failures.
- Fully specified dyed silk/ruby/madder/brass factory: success and full validation success.
- All 33 active definitions create valid natural cotton stacks using the same registered item and each definition ID,
  material, natural colour, default mount, and absent source pigment are asserted.
- Actual registered `ItemStack` persistence covers raw, natural, all four crafted materials, fully specified dyed,
  custom name, unrelated custom data, and every one of the 33 catalogue definitions. The registered component stream
  codec round trip preserves all five identities and schema version.
- Merge compatibility independently distinguishes definition, material, resolved colour, present/different/absent
  source pigment, mount, and configured/unconfigured state after serialization. Identical states are component-
  compatible, but the selected maximum stack size 1 prevents inventory stacking.
- Missing colour with an active source pigment produces a re-resolved repair; missing/no source uses natural colour;
  unavailable historical source uses natural colour while retaining provenance. Plans do not mutate before apply,
  stale plans fail, and missing definition/material/mount are not repaired.

### Registry, catalogue, manual, and integration boundary

- Production counts remain 33 active/0 disabled banners, 4 active materials, 4 active palettes, 7 active pigments,
  2 active mounts, one dye tub, and seven pigment items. The Milestone 6 loading/resolver/component regressions pass.
- Registry-removal tests cover definition, material, palette, resolved colour, source pigment, and mount; disabled
  tests cover definition, material, source pigment, and mount; registry-unavailable state retains the component.
- No GameTest was added. The repository still has no GameTest source root, annotated bootstrap, or templates, and the
  existing registered-`ItemStack` JUnit integration boundary directly exercises the component/persistence/merge work
  in scope without introducing unrelated framework infrastructure.
- No in-game acquisition path exists before Milestone 15, so the manual obtain/tooltip/relog/merge/missing-content
  checklist was not performed. Automated persistence is not claimed as a live world-save or relog test.

### Commands and exact results

1. Git preflight matched exactly: branch `banners-dyetub`; merge base
   `62df1dc97c5113a86f9c0f258cb90538f31efe89`; divergence `0 7`; starting HEAD
   `56b67c02cbb668544300df3f07a4e9dd966c6be4`; and only preserved modified `ModConfig.java` plus the preserved
   untracked root specifications, `.claude/`, `logs/`, and `tmp/` were present.
2. The first restricted `compileJava` could not access the Gradle 8.9 distribution because sandbox networking was
   denied. The approved retry passed in 28 seconds with only the two existing compiler warnings.
3. The first focused `Banner*` run executed 113 tests and found two fixture-boundary failures: the missing-colour
   parameter used a natural stack whose colour existed, and the configured-name assertion supplied a fixture snapshot
   while `getName` reads the global runtime snapshot. The fixture now uses the dyed stack and `configuredName` exposes
   the same snapshot-backed projection used by `getName`; the corrected 113-test run passed.
4. The first complete banner/dye run executed 375 tests and found one obsolete Milestone 6 scope assertion that
   scanned the entire `dye` package and prohibited the now-required generic `DyeableItem` API. It now scans the exact
   Milestone 6 dye-tub implementation files, preserving the original no-banner/client/UI coupling guarantee. The
   corrected required narrow command passed in 32 seconds: 375 tests, 0 failures, 0 errors, 0 skipped.
5. `.\\tools\\scaffold_banners.bat --check` passed in
   11 seconds: manifest=33, definitions=33, active=33, disabled=0, localization=33, provisional names=14,
   provisional dimensions=33, asset families=5.
6. `.\\gradlew.bat clean --no-daemon` passed in 11 seconds.
7. `.\\gradlew.bat test --no-daemon --stacktrace` passed from clean state in 63 seconds: 375 tests, 0 failures,
   0 errors, 0 skipped.
8. `.\\gradlew.bat build --no-daemon` passed in 21 seconds; test/check, jar, jarJar, assemble, and build completed.
9. `git diff --check` passed during implementation review. Final staged checks and commit evidence are recorded in the
   Milestone 7 handoff report.

The unchanged compiler warnings are missing `@Overwrite` Javadoc on `PlayerSleepMixin` and the deprecated-for-removal
`Item.initializeClient` override in `OrderShieldItem`.

### Known limitations and next milestone

- No dye-preview/confirmation screen, menu, packet, held-item banner dyeing, tub use, block, block entity, placement,
  renderer, recipe, crafting integration, command, NPC shop, or direct-world dyeing was added.
- Item art is diagnostic placeholder art; no final heraldry exists. Stack size 1 remains provisional.
- No manual in-game, save/reload, relog, multiplayer, or GameTest verification was performed for the documented
  infrastructure/acquisition reasons.
- Next milestone: Milestone 8 - Dye Preview and Confirmed Item Dyeing - only. It has not started.

## 2026-07-20 - Milestone 6: Dye Items and Stateful Dye Tub

### Files and architecture

- Added one focused `DyeItemRegistry` with `britannia_mod:dye_tub` and seven pigment items whose registry paths match
  the seven existing pigment-definition IDs exactly: `madder_red`, `woad_blue`, `verdigris`, `weld_gold`,
  `soot_black`, `chalk_white`, and `ice_blue`.
- Added one reusable immutable-identity `PigmentItem` and one reusable `DyeTubItem`. The tub has a default maximum
  stack size of one; pigment items retain the normal maximum of 64.
- Added `britannia_mod:dye_tub_state` to the existing `DataComponentRegistry`. Its type attaches the established
  persistent `DyeTubState.CODEC` and network `DyeTubState.STREAM_CODEC`. Every newly registered tub has an explicit
  `DyeTubState.empty()` prototype component.
- Added `DyeTubStateAccess`. Reads normalize a missing legacy component to `DyeTubState.empty()` without mutating the
  stack; writes always install one explicit typed component. Empty, finite, and unlimited states retain their existing
  versioned contract, and unlimited remains represented only by absent `remaining_uses`.
- Added typed loading results and a plan/apply service. Planning validates the exact tub, exact off-hand stack,
  server-owned item mapping, snapshot availability, active/disabled pigment membership, current tub state, same-
  pigment no-op, and complete replacement state before mutation. Apply rechecks the planned stack identities/state,
  writes only the tub component, then shrinks the off-hand stack by one only in survival.
- The authoritative mapping is a fixed item-registry-ID to `PigmentId` map plus an immutable `PigmentItem` identity
  check. Client-editable components and custom data never supply the pigment ID.
- Extended snapshot publication with an explicit first-publication flag so an unloaded empty snapshot is distinct
  from a published snapshot whose requested definition is missing.
- `DyeTubItem.use` accepts only `InteractionHand.MAIN_HAND`, reads `player.getOffhandItem()`, returns pass from the off
  hand, performs no client-side mutation, and uses the normal item-use round trip without a custom payload.
- Successful server mutation plays vanilla `SoundEvents.BOTTLE_FILL` once and sends eight restrained vanilla
  `ParticleTypes.SPLASH` particles. No-op and failure results emit no success effects. Action-bar feedback is sent
  once from the server after planning/application.
- Tooltips use translations for empty/hint, contains, uses, and unlimited text. Loaded pigment names come from the
  current active snapshot. Missing or removed definitions preserve the stored stable ID and display an unavailable-
  pigment diagnostic rather than mutating or erasing state.
- Added the tub and seven pigments to the existing Britannia items creative tab without reordering unrelated items.
- Added eight minimal generated item models and two original shared 32 x 32 placeholder textures. The final project
  assets were generated with the built-in image tool as crisp diagnostic pixel-art sprites, keyed to transparency,
  and downscaled with nearest-neighbour sampling. They are placeholders, not final item art.
- Added focused component, mapping, loading, atomicity, tooltip, feedback, scope, resource, registry-availability,
  real `ItemStack` persistence, and registered network-component tests.

### State, loading, and provisional gameplay decisions

1. Canonical new-tub state is the explicit typed `DyeTubState.empty()` component. An absent legacy component reads as
   the same value without a read-side write.
2. A successful load stores schema version 1, the server-derived stable pigment ID, and absent `remaining_uses`.
3. Survival consumes exactly one off-hand pigment after the component write; creative inventory permissions consume
   zero. Loading a different pigment replaces the old value.
4. Loading the same pigment returns `ALREADY_CONTAINS`, changes neither stack, consumes nothing, and emits no success
   effects.
5. Empty/unsupported off hands, unavailable registry data, missing or disabled definitions, an invalid tub, or stale
   plan/state modify neither stack. The result model distinguishes every case without using exceptions for expected
   interactions.
6. These consumption, replacement, capacity, and no-op rules are reversible provisional defaults and are not final
   product approval.

### Persistence and integration boundary

- Plain JUnit registers a narrow test component and the eight production-shaped items into the real Minecraft built-
  in registries after the required version/bootstrap initialization. Tests then use the actual `ItemStack` persistent
  codec boundary for explicit empty, loaded unlimited, stable pigment ID, and finite fixture round trips.
- The registered component type's attached network stream codec is exercised from a component read on a registered
  tub and written back to another registered tub. The plain-JUnit registry view intentionally does not claim full
  connection-level item-registry ID synchronization, which NeoForge only configures during a real modded connection.
- Different loaded components remain unequal under `ItemStack.isSameItemSameComponents`; the tub maximum stack size
  is one, so one state cannot represent or load multiple tubs. Pigment items remain stackable to 64.
- No GameTest was added. `runGameTestServer` exists, but the repository still has no GameTest source root, annotated
  tests, templates, or test registration bootstrap. Building that unrelated framework would exceed this narrow item
  milestone. The registered `ItemStack` codec and loading-service boundary is the closest practical integration test.
- No in-game client, relog, or save/reload check was performed. Automated `ItemStack` serialization is evidence for
  component persistence, not a claim that a world relog was manually verified.

### Commands and exact results

1. Git preflight matched exactly: branch `banners-dyetub`; merge base
   `62df1dc97c5113a86f9c0f258cb90538f31efe89`; divergence `0 6`; starting HEAD
   `c897bf440aeefdc8f17c21621a38146987e5cc9f`; and only the preserved modified `ModConfig.java` plus the preserved
   untracked root specifications, `.claude/`, `logs/`, and `tmp/` were present.
2. The first restricted `compileJava` could not access the Gradle 8.9 distribution because sandbox networking was
   denied. The approved retry reached compilation and found one new compile error: `Component.withStyle` is not on
   the immutable interface. The tooltip now calls `copy().withStyle(...)`; the corrected compile passed in 26 seconds.
3. Focused-test harness corrections, with no production assertion weakened:
   - the first 35-test attempt had 22 initialization failures because real built-in registry access requires Minecraft
     bootstrap;
   - the first bootstrap patch placed two `@BeforeAll` methods after their class braces and caused four test-source
     compile errors; the methods were moved inside their test classes;
   - the next 39-test run had 24 initialization failures because Minecraft version detection must precede bootstrap;
     `SharedConstants.tryDetectVersion()` was added before `Bootstrap.bootStrap()` and NeoForge registry unfreezing;
   - the next 43-test run had one network assertion failure because plain JUnit does not mark the built-in item
     registry as connection-synchronized. The test was corrected to the requirement's registered component stream-
     codec boundary; persistent tests continue to use the actual `ItemStack` codec.
4. Final focused command selecting the Milestone 6 component, mapping, loading, tooltip, resource/scope, persistence,
   and availability tests passed in 24 seconds: 43 tests, 0 failures, 0 errors, 0 skipped.
5. `.\tools\scaffold_banners.bat --check` passed in 53 seconds: 33 manifest entries, 33 definitions, 33 active,
   0 disabled, 33 generated banner localization entries, 14 provisional names, 33 provisional dimensions, and 5
   placeholder asset families.
6. `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace` passed in
   37 seconds. XML results: 297 tests, 0 failures, 0 errors, 0 skipped.
7. `.\gradlew.bat clean --no-daemon` passed in 23 seconds.
8. `.\gradlew.bat test --no-daemon --stacktrace` passed in 72 seconds. XML results: 297 tests, 0 failures, 0 errors,
   0 skipped.
9. `.\gradlew.bat build --no-daemon` passed in 22 seconds; `test`, `check`, `jar`, `jarJar`, `assemble`, and `build`
   completed.
10. Final review tightened registry publication so the availability flag and immutable snapshot remain one atomic
    publication value. The focused 43-test selection then passed again in 37 seconds, the full 297-test suite passed
    again in 31 seconds, and `build` passed again in 16 seconds.
11. Final diff, staging, and commit checks are recorded in the Milestone 6 handoff report.

The existing compiler warnings remain unchanged: missing `@Overwrite` Javadoc on `PlayerSleepMixin` and the
deprecated-for-removal `Item.initializeClient` override in `OrderShieldItem`.

### Catalogue, registry, manual, and scope results

- Registered content: seven pigment items, one dye tub, one new typed component, and seven deterministic mappings.
- Production data remains 33 active/0 disabled banners, 4 active materials, 4 active palettes, and 7 active pigments.
- Survival service evidence consumes one item; creative evidence consumes zero; replacement changes the pigment;
  same-pigment evidence changes nothing and consumes zero.
- The complete manual checklist remains unperformed: obtain items; verify empty tooltip; load main-hand tub from off-
  hand pigment; verify survival consumption and loaded tooltip; relog/save-reload; replace pigment; repeat same
  pigment; verify creative no-consumption; and try a non-pigment. No in-game claim is made.
- No banner item, banner component, dyeable-item API, banner state adapter, preview screen, menu, payload, colour
  application, block, block entity, placement, renderer, crafting, recipe, command, NPC shop, or direct-world dyeing
  was added. Milestone 7 has not started.

### Known limitations and deviations

- Consumption rules remain provisional; tub capacity is unlimited for now; washing/emptying and finite-use gameplay
  are absent.
- Final item art and final pigment availability/acquisition are unresolved; current access is development creative-tab
  access only.
- There is no banner item, banner dyeing path, or preview screen.
- Live GameTest, in-game multiplayer, relog, and world-save checks were not performed for the documented repository-
  infrastructure reason above.

### Next milestone

Milestone 7 - Generic Dyeable Item API and Banner Item State - only. It has not started.

## 2026-07-20 - Milestone 5: Materials, Palettes, and Colour Mathematics

### Files and architecture

- Added an isolated common-side colour layer under `dye/colour`: strict canonical sRGB parsing, eight-bit channel
  normalization, the standard inverse sRGB transfer function, linear-sRGB-to-OKLab conversion, Euclidean OKLab
  distance, authored-reference comparison, and explicit comparison/tie tolerances.
- Added `DyeResolver` and immutable resolution outcome/explanation records under `dye/service`. Expected content
  failures are returned as typed values rather than generic unchecked exceptions. Lightweight and explanatory entry
  points share one selection algorithm and return the same `DyeResult`.
- Extended the existing Milestone 3 cross-reference pipeline to reject authored pigment or palette OKLab values that
  disagree excessively with their canonical sRGB. Added `ProductionDyeContent` as a release/development-content
  boundary for the four required materials without hard-coding that requirement into the generic loader.
- Authored four material definitions, four compact material palettes, and seven pigment definitions under the existing
  data-resource folders. No parallel colour model, item registration, gameplay object, component, packet, screen,
  renderer, recipe, command, block, or block entity was introduced.
- Transferred the Milestone 4 cotton placeholder material/palette out of scaffold ownership. The scaffold now uses a
  private in-memory cotton fixture only to cross-validate its banner outputs; it emits no colour resources. Normal
  regeneration refreshed the sidecar metadata, and `--check` remains clean.
- Added material, resolved-colour, and pigment localization while retaining all 33 banner localization keys exactly.
- Added independent reference, resolver, compatibility, ordering, immutability, registry-integration, production-data,
  and common-side safety tests.

### Colour mathematics and numeric policy

- Canonical input remains uppercase six-digit `#RRGGBB`. Parsing produces three integer channels in `[0, 255]`, then
  normalizes each channel to `[0, 1]`; malformed, lowercase, short, prefixless, non-finite, and out-of-range values are
  rejected rather than clamped.
- The inverse sRGB transfer function is `c / 12.92` at `c <= 0.04045`, otherwise
  `((c + 0.055) / 1.055)^2.4`. Source: W3C CSS Color 4's sRGB conversion algorithm, which reproduces IEC
  61966-2-1: https://www.w3.org/TR/css-color-4/#color-conversion-code .
- Linear sRGB is converted directly to OKLab with Bjorn Ottosson's updated 2021-01-25 matrices and signed cube-root
  stage: https://bottosson.github.io/posts/oklab/ . The implementation uses the published ten-decimal constants.
- All calculations use Java `double`. `COMPARISON_EPSILON` is `1e-12`; values at or below it are treated as numerical
  zero. `TIE_EPSILON` is `1e-9`. Independent reference-vector assertions use a `5e-9` tolerance.
- Authored OKLab triples may contain any finite doubles because the established codec permits them; they are not
  clamped. Registry validation compares them with their computed canonical-sRGB values and rejects a definition when
  Euclidean disagreement exceeds `5e-7`. NaN and both infinities remain structural errors.
- The computed OKLab value from canonical sRGB is authoritative for matching. Authored `reference_oklab` and
  `match_oklab` remain persisted audit values and must agree within the validation tolerance; disagreement cannot be
  silent. No conversion cache was added because the current immutable dataset is small and keeping the utility pure
  avoids shared mutable state; a future immutable snapshot-local cache may be added without changing results.

### Resolver semantics

1. Look up the pigment, material, and material palette in one immutable `RegistrySnapshot`.
2. Verify palette ownership and non-empty content defensively.
3. If a pigment override exists, verify its target and return it immediately as `EXPLICIT_MAPPING`; compatibility and
   mathematical proximity cannot displace it.
4. Otherwise filter entries. Any shared excluded pigment tag rejects the entry first. Empty allowed tags impose no
   positive restriction; non-empty allowed tags require at least one shared pigment tag.
5. Compute Euclidean distance between pigment and entry OKLab values derived from canonical sRGB.
6. Select lowest distance; values within `1e-9` enter tie-breaking. Then prefer a shared colour-family tag, higher
   priority, and finally the lexicographically smaller resolved-colour ID.
7. Only tags with the `colour_family_` prefix count as colour families. Generic tags such as `common`, `development`,
   `fabric`, and rarity tags never affect that tie stage.
8. Candidate input is normalized to stable-ID order, and explanation candidates/rejections are emitted in a
   deterministic order. Registry, JSON, map, set, and resource load order cannot affect the result.
9. No compatible entry returns `NO_COMPATIBLE_COLOUR` with ordered rejection reasons. Missing IDs, ownership errors,
   missing natural colours, and malformed explicit mappings use other typed failures. The resolver never substitutes
   an incompatible or natural colour silently.
10. Dedicated natural lookup returns the material's authored natural colour with `MatchType.NATURAL`; it does not
    represent natural state as a pigment.

### Development content counts

- Materials: 4 - `cotton`, `wool`, `linen`, and `silk`.
- Palettes: 4. Cotton, wool, and linen each contain 8 entries; silk contains 9; total entries: 33.
- Pigments: 7 - `madder_red`, `woad_blue`, `verdigris`, `weld_gold`, `soot_black`, `chalk_white`, and `ice_blue`.
- Explicit overrides: 4, one `madder_red` mapping in each material palette.
- Compatibility-restricted entries: 1, `silk_glacial`, allowed for `ice` and excluded for `mundane` pigments.
- `madder_red` representative results:
  - cotton -> `cotton_red`, explicit mapping, distance `0.060252859817`;
  - wool -> `wool_oxblood`, explicit mapping, distance `0.049274171079`;
  - linen -> `linen_madder`, explicit mapping, distance `0.113283634696`;
  - silk -> `silk_ruby`, explicit mapping, distance `0.074164136656`.
- All values above are development data for architecture proof and are not final art-direction-approved colours.

### Commands and exact results

1. Required Git preflight:
   - `git branch --show-current` - `banners-dyetub`.
   - `git status --short --branch` - only the preserved modified `ModConfig.java` and preserved untracked root
     specifications, `.claude/`, `logs/`, and `tmp/` were present.
   - `git merge-base banners-dyetub patch-18` - `62df1dc97c5113a86f9c0f258cb90538f31efe89`.
   - `git rev-list --left-right --count patch-18...banners-dyetub` - `0 5`.
   - starting `HEAD` - `f75635f03af636c0699c3458c26538fc415f4dbe`.
2. The first restricted `compileJava compileScaffoldJava` attempt could not access the Gradle 8.9 distribution because
   sandbox network access was denied. The approved retry passed in 56 seconds. It confirmed the two existing compiler
   warnings: missing `@Overwrite` Javadoc on `PlayerSleepMixin`, and the deprecated-for-removal
   `Item.initializeClient` override in `OrderShieldItem`.
3. The first complete narrow Milestone 5 run passed in 31 seconds. After final defensive/order tests and scaffold
   wording regeneration, the final required narrow command
   `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace` passed in
   23 seconds. XML results: 254 tests, 0 failures, 0 errors, 0 skipped.
4. `.\tools\scaffold_banners.bat` regenerated only scaffold-owned status/metadata successfully: 33 definitions,
   33 active, 0 disabled, 33 localization entries, 14 provisional names, and 33 provisional dimensions.
5. Final `.\tools\scaffold_banners.bat --check` passed in 16 seconds with the same counts.
6. `.\gradlew.bat clean --no-daemon` passed in 11 seconds.
7. `.\gradlew.bat test --no-daemon --stacktrace` passed in 56 seconds. XML results: 254 tests, 0 failures,
   0 errors, 0 skipped.
8. `.\gradlew.bat build --no-daemon` passed in 21 seconds; `test`, `check`, `jar`, `jarJar`, `assemble`, and `build`
   completed.
9. `git diff --check` passed before documentation/staging review. Final staged checks are recorded in the handoff.

No JUnit test initially failed and no production validation or test assertion was weakened. The only initial failure
was the expected restricted-sandbox Gradle distribution access error; the approved retry used the configured
toolchain successfully.

### Catalogue, validation, and scope results

- Catalogue entries: 33 active, 0 disabled. Stable IDs, source references, display labels, provisional dimensions,
  definitions, and the manifest count are unchanged.
- Gate B decisions are reflected in the generated status report and `OPEN_QUESTIONS.md`: all 33 stable IDs remain,
  all 14 unnamed entries remain visibly provisional, and `Tournament Medium` / `Pennon of Silver` remain canonical
  scaffold labels.
- Registry validation reports 4 active materials, 4 active palettes, 7 active pigments, 33 active banners, and no
  validation errors or warnings for the authored development dataset.
- Generic intentionally small registry fixtures still publish successfully; the four-material rule exists only in
  `ProductionDyeContent` and is invoked at the production-catalogue boundary.
- Common-side class scans found no Minecraft client, Blaze3D, gameplay registration, component, payload, screen,
  block-entity, recipe, or command references in the colour/resolver implementation.
- No manual visual or in-game check was performed because this milestone contains no item, tub, UI, rendering, or
  gameplay path. Correctness is established by reference vectors and deterministic automated tests, not screenshots.

### Known limitations and deviations

- Development palette colours are not final art-approved palettes, and the pigment catalogue is not final.
- Final special-dye restrictions and rare-pigment semantics remain open.
- The approximation-rejection threshold remains deferred; nearest matching currently selects the closest compatible
  entry regardless of absolute distance.
- Whether authored OKLab remains persisted long term is open. Milestone 5 retains it with strict consistency
  validation for backward compatibility.
- There are no dye items, dye tubs, held-item interactions, components, item state adapters, consumption rules,
  tooltips, particles, sounds, UI, networking, rendering, blocks, block entities, placement, crafting, recipes,
  commands, NPC shops, or direct-world dyeing.

### Next milestone

Milestone 6 - Dye Items and Stateful Dye Tub - only. It has not started.

## 2026-07-20 - Milestone 4: Scaffold All 33 Banner Placeholders

### Files and generated content

- Added the canonical editable manifest at `content/banner_catalogue.yml`. It is YAML 1.2 expressed in its
  JSON-compatible syntax so the existing Gson/Java/Gradle dependency set can validate it without a new runtime or
  parser dependency.
- Added the Java scaffold implementation under `tools/scaffold`, the Gradle `scaffoldBanners` task, and the Windows
  entry point `tools/scaffold_banners.bat`.
- Generated exactly 33 definitions under
  `src/main/resources/data/britannia_mod/banner_definitions`, in canonical manifest order.
- Generated minimal supporting definitions: one placeholder cotton material, one single-natural-colour cotton
  palette, brass and iron mounts, and five size-family placement profiles. No pigment is required for the natural
  scaffold state.
- Generated four 16 x 16 diagnostic PNGs and five shared vanilla-model JSON placeholders under
  `assets/britannia_mod/.../banner/placeholder`. The images are neutral grayscale fabric/mask/overlay assets plus a
  conventional high-contrast missing texture; they contain no final heraldry.
- Structurally merged exactly 33 banner translation keys into the existing `en_us.json` while retaining unrelated
  keys and the file's existing layout.
- Generated `content/banner_catalogue_status.md` and the sidecar
  `content/.banner_scaffold_metadata.json` used for non-overwrite detection.
- Added catalogue-specific runtime validation in `ProductionBannerCatalogue` and the real reload listener without
  changing the generic Milestone 3 loader or its small-dataset behavior.
- Added 48 Milestone 4 tests across manifest, generator, production content, catalogue-boundary, asset, localization,
  scope, and safety cases.
- Updated `OPEN_QUESTIONS.md` with Gate B label discrepancies and the remaining universal runtime asset-mapping
  decision.

### Scaffold architecture and invocation

- Generation command: `.\tools\scaffold_banners.bat`.
- Non-mutating verification command: `.\tools\scaffold_banners.bat --check`.
- Destructive opt-in command: `.\tools\scaffold_banners.bat --force`.
- The tool validates the complete manifest before calculating or writing any output. It requires exactly 33 entries,
  unique stable IDs, unique continuous indices 1 through 33, the canonical ID/order/table, canonical group counts,
  positive source references, visible provisional status, placeholder content status, provisional dimensions, safe
  output IDs, supported groups, and the scaffold defaults.
- Normal generation compares each declared output with the last generated SHA-256 recorded in the sidecar. An
  unmodified generated output can be refreshed; a customized output is reported and preserved. Unknown and unrelated
  files are never deleted or rewritten.
- Localization is handled per generated key. Existing unrelated keys remain untouched, customized generated values
  are preserved normally, and `--force` warns before replacing them.
- `--check` writes nothing and returns non-zero for missing, changed, duplicate, unsafe, undecodable, inactive, or
  otherwise invalid output.
- `--force` prints an explicit list of customized declared files and localization keys before overwriting them. It
  still does not touch unrelated files.
- File replacement uses a same-directory temporary file followed by `ATOMIC_MOVE` where the filesystem supports it,
  with a same-filesystem replace fallback.

### Catalogue and registry results

- Manifest entries: 33.
- Unique IDs: 33.
- Unique indices: 33, continuous from 1 through 33.
- Group counts: large 6, medium-wall 6, medium 8, small 6, x-small 7.
- Name status counts: provisional 14, source-named 19.
- Content status counts: placeholder 33, in-progress 0, complete 0.
- Provisional dimensions: 33.
- Generated banner definitions: 33.
- Generated localization entries: 33.
- Active banner definitions after Milestone 3 development validation: 33.
- Disabled banner definitions: 0.
- Supporting active definitions: one material, one palette, two mounts, five profiles, zero pigments.
- Placeholder asset families: 5; common diagnostic textures: 4.
- All emitted logical placeholder geometry and texture IDs map deterministically to a file in the scaffold's declared
  output set.

### Commands and exact results

1. Required Git preflight:
   - `git branch --show-current` - `banners-dyetub`.
   - `git status --short --branch` - only the preserved modified `ModConfig.java` and preserved untracked root
     specifications, `.claude/`, `logs/`, and `tmp/` were present.
   - `git merge-base banners-dyetub patch-18` - `62df1dc97c5113a86f9c0f258cb90538f31efe89`.
   - `git rev-list --left-right --count patch-18...banners-dyetub` - `0 4`.
   - `git rev-parse HEAD` - `343e65f436c9cb07ce9c96aec39c752dc318c1d0`.
2. Initial restricted `compileScaffoldJava` and generation attempts could not access the Gradle 8.9 distribution
   because sandbox network access was denied. Approved retries used the existing configured Gradle toolchain.
3. `.\gradlew.bat compileScaffoldJava --no-daemon --stacktrace` - passed in 33 seconds. It confirmed the two existing
   compiler warnings: missing `@Overwrite` Javadoc on `PlayerSleepMixin`, and the deprecated-for-removal
   `Item.initializeClient` override in `OrderShieldItem`.
4. `.\tools\scaffold_banners.bat` - passed in 22 seconds and reported 33 manifest entries, 33 generated definitions,
   33 active, 0 disabled, 33 localization entries, 14 provisional names, 33 provisional dimensions, and 5 asset
   families.
5. `.\tools\scaffold_banners.bat --check` - passed after generation; the final post-fix check passed in 11 seconds
   with the same counts.
6. The first narrow 48-test Milestone 4 run compiled successfully and found one failure in the localization force-path
   test. The replacement helper called `Matcher.start()` after a second `find()` invalidated the prior match. The
   offsets are now captured before the duplicate-match check; no validation or safety assertion was weakened.
7. The corrected narrow command selecting `BannerCatalogueManifestTest`, `BannerScaffoldToolTest`,
   `GeneratedBannerCatalogueTest`, and `ProductionBannerCatalogueTest` passed: 48 tests, 0 failures, 0 errors,
   0 skipped.
8. `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace` - passed in
   1 minute 6 seconds. XML results: 207 tests, 0 failures, 0 errors, 0 skipped.
9. `.\gradlew.bat clean --no-daemon` - passed in 21 seconds.
10. `.\gradlew.bat test --no-daemon --stacktrace` - passed in 53 seconds. XML results: 207 tests, 0 failures,
    0 errors, 0 skipped.
11. `.\gradlew.bat build --no-daemon` - passed in 29 seconds; `jar`, `jarJar`, `assemble`, and `build` completed.
12. Packaged-JAR entry inspection found exactly 33 banner-definition JSON files, 5 placeholder model JSON files,
    4 placeholder PNG files, and the merged `en_us.json` in `Britannia_Mod-0.1.7k-all.jar`.
13. `git diff --check` passed during implementation review; staged checks are required immediately before commit.

### Validation and scope decisions

- The tool feeds all generated JSON through the Milestone 2 codecs and the complete generated data set through the
  Milestone 3 development policy. It does not introduce a second runtime format or bypass the loader.
- The release assertion is held in `ProductionBannerCatalogue`, not `DefinitionRegistry` or `RegistryDataLoader`.
  It activates when canonical production IDs are present and requires the exact ID set, 33 active entries, and zero
  disabled entries. Empty and intentionally small generic fixtures remain outside the boundary.
- The reload listener validates a candidate snapshot before atomically publishing it globally, so an incomplete real
  catalogue does not replace the last valid snapshot.
- The placeholder palette's single natural colour and illustrative structural OKLab triple exist only because the
  Milestone 2 codec requires them. No conversion, distance calculation, dye resolution, authored production palette,
  or Milestone 5 behavior was added.
- Placeholder model JSON and PNG existence is proven by the scaffold's deterministic mapping. No claim is made that
  the later renderer uses these files, because rendering is outside this milestone.

### Known limitations and Gate B

- Final names are not approved. Fourteen entries remain visibly `Name Required`; source-named labels are preserved
  but are not represented as final owner approval.
- `Tournament Medium` versus `Tournament`, and `Pennon of Silver` versus `Silver Pennon`, require Gate B review.
- All 33 dimensions are provisional. Final per-banner orientation and mount support are also unapproved scaffold
  defaults.
- Final original heraldic art, renderer integration, gameplay registrations, placement mechanics, items, blocks,
  components, packets, screens, recipes, crafting, and commands are not implemented or manually verified.
- The universal future logical-ID-to-vanilla/GeckoLib/custom-loader mapping remains open; only the Milestone 4
  placeholder output set has an authoritative mapper.
- No in-game or runtime rendering test was performed because there is no banner item, block, or renderer in scope.

### Next milestone

Stop for Gate B review of the complete 33-entry catalogue, stable IDs, provisional names/dimensions, and manifest
workflow. Milestone 5 has not started and must not begin before Gate B approval.

## 2026-07-20 - Milestone 3: Data Registries and Validation Pipeline

### Files changed

- Added immutable registry primitives and the aggregate `RegistrySnapshot` under `bannerdyeing/registry`.
- Added resource discovery, structural decoding, cross-reference validation, fixed-point production disabling,
  atomic publication, read-only global access, and the common/server reload listener.
- Added structured validation severity, stage, issue, summary, report, and policy types under
  `bannerdyeing/validation`.
- Registered the reload listener explicitly from `BritanniaMod` on the NeoForge game event bus.
- Added `RegistryDatasetFixtures` and `RegistryDataLoaderTest` under test sources. The fixtures are generated in
  test code from the Milestone 2 codecs and never enter packaged resources.
- Updated `OPEN_QUESTIONS.md` with the unresolved physical-asset mapping boundary.

### Commands and results

1. Required Git preflight:
   - `git branch --show-current` - `banners-dyetub`.
   - `git status --short --branch` - only the preserved modified `ModConfig.java` and preserved untracked root
     specifications, `.claude/`, `logs/`, and `tmp/` were present.
   - `git merge-base banners-dyetub patch-18` - `62df1dc97c5113a86f9c0f258cb90538f31efe89`.
   - `git rev-list --left-right --count patch-18...banners-dyetub` - `0 3`.
   - Milestone 2 full hash - `31e46ddba426136e904a7d59cfddb32322ca3a8b`.
2. Initial restricted `.\gradlew.bat compileJava --no-daemon --stacktrace` - Gradle distribution access was denied
   by the sandbox. The approved retry passed in 1 minute 2 seconds and confirmed the two existing warnings: missing
   `@Overwrite` Javadoc on `PlayerSleepMixin`, and the deprecated-for-removal `Item.initializeClient` override in
   `OrderShieldItem`.
3. First `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.RegistryDataLoaderTest"
   --no-daemon --stacktrace` - 25 tests ran; one report-order test assertion failed because it compared the report's
   explicit enum/domain ordering to unrelated ordinary string ordering. The redundant string-sort assertion was
   removed; validation ordering and production code were not weakened.
4. Corrected narrow registry test command - 25 tests passed, 0 failures, 0 errors, 0 skipped.
5. Completed narrow registry test command after the remaining structural-attribution cases were added - 29 tests
   passed, 0 failures, 0 errors, 0 skipped.
6. Final `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace` -
   passed in 32 seconds. XML results: 159 tests, 0 failures, 0 errors, 0 skipped.
7. Final `.\gradlew.bat clean --no-daemon` - passed in 12 seconds.
8. Final `.\gradlew.bat test --no-daemon --stacktrace` - passed in 41 seconds. XML results: 159 tests,
   0 failures, 0 errors, 0 skipped.
9. Final `.\gradlew.bat build --no-daemon` - passed in 22 seconds; `jar`, `jarJar`, `assemble`, and `build`
   completed.
10. Common-source scan - no `net.minecraft.client`, Blaze3D, gameplay objects, item stacks, block entities, screens,
    packets, or `DeferredRegister` usage in the registry/validation implementation.
11. `git diff --check`, staged-diff checks, and final scope checks are recorded in the milestone handoff report.

### Registry and resource-loading decisions

- The six registries are typed views inside one immutable aggregate snapshot. Active lookup maps, deterministic
  definition lists, source-bearing entries, and disabled diagnostic lists are all defensively copied and read-only.
- `AtomicReference<RegistrySnapshot>` is the sole publication mechanism. Candidate work occurs off to the side and
  one reference write exposes the complete new snapshot to concurrent readers.
- `AddReloadListenerEvent` is the repository-compatible NeoForge 21.1 server-data hook. Effective resources are read
  and structurally decoded during `SimplePreparableReloadListener.prepare`; policy validation and publication occur
  during `apply`.
- Folders are exactly `banner_definitions`, `fabric_materials`, `pigments`, `material_palettes`, `banner_mounts`, and
  `placement_profiles` below each data namespace.
- `ResourceManager.listResources` supplies only the effective resource at a path, so ordinary higher-priority pack
  replacement is not a duplicate. Duplicate checks operate on embedded IDs across distinct effective resources.
- Embedded stable IDs are authoritative. Filenames and namespaces are retained as exact diagnostics but are not
  required to match an embedded ID. This permits pack organization without inventing a filename identity contract.

### Validation and policy decisions

- Stage 1 parses every effective JSON resource and reuses the Milestone 2 codecs for required fields, schema,
  dimensions, colours, OKLab values, identifiers, and local collection constraints. It never publishes partial data.
- Stage 2 validates palette owners, material/palette and natural-colour agreement, override pigments, default
  materials, default/supported mounts, placement-profile existence, and dimension containment. Profile width and
  height must be at least the banner's declared width and height; rotations and occupied cells remain deferred.
- Development/fail-fast rejects any candidate with an error, retains the prior snapshot, exposes/logs the complete
  report, and lets the reload framework surface failure without terminating the JVM from low-level code.
- Production/disable-invalid removes the owner of each invalid decoded definition, validates again, and repeats to a
  stable fixed point. A bad palette can therefore disable its material and then banners using that material. Authored
  immutable definitions are never mutated and unrelated substitutes are never selected.
- Every issue carries stage, severity, domain, optional definition ID, exact source resource, stable issue code,
  message, and optional related ID. Reports are deduplicated and deterministically ordered before logging once.

### Test coverage and integration boundary

- Test-only generated datasets cover valid data, every applicable missing/mismatch case, malformed and unknown-schema
  resources, duplicate IDs, simultaneous errors, dependency cascades, replacement and preservation, reference-safe
  production subsets, deterministic ordering, immutable exposure, empty datasets, and authoritative embedded IDs.
- Atomic publication has a concurrent-reader test, and common registry classes are scanned for client references.
- The listener is compiled and wired to `AddReloadListenerEvent`, but no automated Minecraft bootstrap/GameTest was
  added. Runtime resource-manager override behavior and listener invocation therefore have isolated core coverage plus
  API compilation, not a live-server integration test. `ResourceManager.listResources` itself owns pack priority.

### Known limitations and deviations

- Logical asset IDs are syntax-validated. Physical geometry/model/texture existence is not checked until the project
  chooses a reliable mapping across vanilla, GeckoLib, texture, and custom-loader resource types; this is recorded in
  `OPEN_QUESTIONS.md`.
- Structurally undecodable resources remain visible in the report but cannot appear in a disabled typed-definition
  list because no valid immutable definition exists to retain.
- No resource-pack-stack integration test was added because constructing the actual Minecraft pack/bootstrap layer is
  unsuitable for the ordinary unit harness. Same-path replacement uses the platform's effective-resource map.
- No production definitions, catalogue manifest, assets, gameplay registrations, components, items, blocks, block
  entities, screens, packets, rendering, recipes, commands, placement mechanics, or Milestone 4 work were added.

### Next milestone

Stop after the Milestone 3 commit and owner review. The next permitted work is Milestone 4 - Scaffold All 33 Banner
Placeholders - only; do not begin it as part of this milestone.

## 2026-07-20 - Milestone 2: Core Data Records and Codecs

### Files changed

- Added banner data contracts under `banner/data`: `BannerDefinition`, `BannerDimensions`, `BannerAssets`,
  `BannerSourceReference`, `BannerContentStatus`, `MountDefinition`, and `PlacementProfile`.
- Added common item-compatible banner state under `banner/state`: `BannerInstanceState`.
- Added dye definitions under `dye/data`: `FabricMaterialDefinition` and `PigmentDefinition`.
- Added palette contracts under `dye/palette`: `MaterialPalette` and `MaterialPaletteEntry`.
- Added dye state and result contracts: `DyeTubState`, `DyeResult`, and `MatchType`.
- Added `DataCodecs` for the shared schema, canonical sRGB, finite-number, OKLab, tag, and non-blank-string
  structural codecs.
- Added `CoreDataFixtures`, `CoreDataCodecTest`, and `CoreDataValidationTest`.
- Updated `OPEN_QUESTIONS.md` with the deliberately deferred occupied-cell/anchor semantics for placement profiles.

### Commands and results

1. Required Git preflight:
   - `git branch --show-current` - `banners-dyetub`.
   - `git status --short --branch` - only the preserved modified `ModConfig.java` and preserved untracked root
     specifications, `.claude/`, `logs/`, and `tmp/` were present.
   - `git merge-base banners-dyetub patch-18` - `62df1dc97c5113a86f9c0f258cb90538f31efe89`.
   - `git rev-list --left-right --count patch-18...banners-dyetub` - `0 2`.
   - `git log --oneline --decorate -5` - expected Milestone 1 `129ed2d` and Milestone 0 `e43bbc5` were the first
     two feature commits.
2. Initial restricted `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon
   --stacktrace` - could not access/download the Gradle 8.9 distribution because sandbox network access was denied.
3. The first approved retry was given an accidentally short command timeout. Its orphaned worker temporarily held
   `build/test-results/test/binary/output.bin`; the next retry reported that output-lock error. `.\gradlew.bat --stop`
   stopped the one orphaned daemon. No source or test assertion failed in either attempt.
4. `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace` after the
   complete fixture coverage was added - passed in 11 seconds. XML results: 130 tests, 0 failures, 0 errors,
   0 skipped.
5. `.\gradlew.bat clean --no-daemon` - passed in 9 seconds.
6. `.\gradlew.bat test --no-daemon --stacktrace` - passed in 39 seconds. XML results: 130 tests, 0 failures,
   0 errors, 0 skipped.
7. `.\gradlew.bat build --no-daemon` - passed in 14 seconds; `jar`, `jarJar`, `assemble`, and `build` completed.
8. `git diff --check` - passed before documentation/staging review; repeated during final review.
9. Common-source client/API scan - no `net.minecraft.client`, Blaze3D, gameplay objects, registration APIs,
   reload listeners, payloads, screens, renderers, items, blocks, or block entities in the Milestone 2 data packages.

No initially failing JUnit test required correction. The only initial failures were Gradle environment access and an
orphaned-worker file lock, as described above.

### Structural validation decisions

- Reused all six Milestone 1 stable ID wrappers. Asset, model, texture, and palette identities use namespaced
  `ResourceLocation` values rather than a parallel identifier system.
- All stored top-level contracts use `BannerDyeingConstants.CURRENT_SCHEMA_VERSION`; unknown versions fail with an
  explicit codec error. Nested value records and the transient `DyeResult` do not repeat a schema field.
- Persistent codecs require every non-optional field and do not provide invalid-value defaults. Network codecs exist
  only for `BannerInstanceState` and `DyeTubState`, the two contracts selected for future typed synchronized item
  components by `PROJECT_FACTS.md`.
- Banner width is limited to one through three blocks. Height is limited to one through 16 blocks; 16 is a documented
  conservative occupancy bound that exceeds the current catalogue while preventing unbounded structural input.
- Banner orientations and mounts must be non-empty, and the default mount must be in the supported mount collection.
- Canonical sRGB is uppercase six-digit `#RRGGBB`. OKLab components and dye-result distances must be finite; distances
  must also be non-negative. Numeric fixture values remain illustrative authored data, not scientifically verified
  production colours.
- Palettes reject duplicate entry IDs, require their natural colour to be one of their own entries, and require local
  pigment overrides to target one of those entries. Overrides are copied into lexical pigment-ID order for stable
  output.
- Unlimited dye-tub uses have one representation: an absent `remaining_uses`. Finite counts may be zero or positive;
  a use count without a loaded pigment is structurally invalid. No consumption or replacement policy is encoded.
- Lists and maps are defensively copied and exposed as immutable collections. Source-sheet labels are preserved only
  as provenance and are never promoted to display names.

### Validation deferred to Milestone 3

- Registry membership and existence of referenced definitions, materials, pigments, colours, mounts, palettes,
  placement profiles, models, and textures.
- Agreement between a banner definition and its referenced placement profile or material palette.
- Cross-resource uniqueness, missing resources, disabled-entry policy, and complete registry-set validation.
- Whether an absent source pigment corresponds to the material's natural colour; that requires loaded material and
  palette data. Decoding intentionally remains valid without live registries.

### Known limitations and deviations

- `PlacementProfile` deliberately contains only schema version, stable ID, declared dimensions, and a wall-support
  flag. Occupied offsets, rotations, anchor choice, support-cell rules, and placed-state persistence remain deferred
  to the placement milestones and are recorded in `OPEN_QUESTIONS.md`.
- No data loading, registries, catalogue data, palette resolution, colour conversion, gameplay objects, components,
  packets, rendering, placement behavior, commands, recipes, or assets were added.
- `MaterialPaletteEntry` is nested in a versioned `MaterialPalette`; it does not repeat `schema_version`.
- `DyeResult` is a small immutable resolver result and is not a stored top-level state object, so it does not include a
  schema field or a network codec.

### Next milestone

Stop after the Milestone 2 commit and owner review. The next permitted work is Milestone 3 - Data Registries and
Validation Pipeline - only; do not begin it as part of this milestone.

## 2026-07-20 — Milestone 1: Feature Skeleton, IDs, and Test Harness

### Files changed

- Added common feature entry points: `BannerFeature`, `DyeFeature`, and `BannerDyeingBootstrap`.
- Added the shared `StableResourceId` contract and immutable `ResourceLocation` wrappers for banner definitions, fabric materials, pigments, resolved colours, mounts, and placement profiles.
- Added the stable `BannerOrientation` enum for wall-parallel and wall-perpendicular orientations.
- Added `BannerDyeingConstants` with schema version 1 and the initial cotton, brass, and iron IDs.
- Added JUnit Jupiter test dependencies and enabled the JUnit Platform in `build.gradle`.
- Added reusable ID fixtures and tests covering construction, parsing, equality, string form, persistent codecs, stream codecs, invalid identifiers, null rejection, orientation serialization, bootstrap constants, logger categories, and common-code client-reference safety.

### Commands and results

1. `git branch --show-current` — `banners-dyetub`.
2. `git merge-base patch-18 HEAD` — `62df1dc97c5113a86f9c0f258cb90538f31efe89`; `git rev-list --left-right --count patch-18...banners-dyetub` returned `0 1` before this milestone commit.
3. Initial restricted-sandbox narrow Gradle invocation — could not download/access the Gradle distribution because network access was denied; rerun with approved dependency access.
4. `.\gradlew.bat test --tests "com.seggellion.britannia_mod.bannerdyeing.*" --no-daemon --stacktrace` — first approved run reached project compilation and found one test-only `JsonOps` input type error. The test was corrected to use `JsonPrimitive`; production code compiled.
5. The same narrow test command after correction — passed in 13 seconds. XML results: 53 tests, 0 failures, 0 errors, 0 skipped.
6. `.\gradlew.bat clean --no-daemon` — passed in 17 seconds.
7. `.\gradlew.bat test --no-daemon --stacktrace` — passed in 59 seconds. XML results: 53 tests, 0 failures, 0 errors, 0 skipped.
8. `.\gradlew.bat build --no-daemon` — passed in 22 seconds; `jar`, `jarJar`, `assemble`, and `build` completed.
9. Common-source client-reference scan — passed with no `net.minecraft.client` or `com.mojang.blaze3d` references.
10. Out-of-scope API scan — passed with no registrations, block entities, menus, screens, renderers, recipes, or custom payload APIs in the Milestone 1 packages.

### Decisions

- Model stable domain identifiers as small immutable record wrappers around `ResourceLocation`, with a shared read-only contract while retaining distinct compile-time types.
- Put both persistent `Codec` and network `StreamCodec` definitions on each identifier type so later milestones share one canonical serialization boundary.
- Reject malformed identifiers through the repository's Minecraft 1.21.1 `ResourceLocation` validation rather than adding a second validation grammar.
- Keep the feature bootstraps side-effect-free in this milestone. Registration and gameplay wiring belong to later milestones.
- Use structured logger categories dedicated to banner and dye content validation.
- Add the smallest conventional JUnit 5 harness because the repository had no test framework or test sources.

### Known limitations and deviations

- No registries, content catalogue, JSON loading, blocks, items, block entities, screens, renderers, packets, recipes, or gameplay behavior are implemented; these are intentionally outside Milestone 1.
- The bootstrap classes establish common boundaries but are not invoked by the main mod initializer until a later registration milestone has real work to wire.
- This milestone has unit-level common-code safety checks only. No manual in-game check is meaningful for a registration-free skeleton.
- The first approved narrow run exposed and led to correction of a test-only compile error before the successful validation runs; it was not a production-code failure.

### Next milestone

Stop after the Milestone 1 commit and owner review. The next permitted work is Milestone 2 only; do not begin it as part of this milestone.

## 2026-07-20 — Milestone 0: Repository Discovery and Implementation Facts

### Branch-creation evidence

- Remote used: `origin` (`https://github.com/Seggellion/Britannia_Mod.git`).
- Remote refresh: `git fetch --prune origin` succeeded.
- Starting branch: `patch-18`, tracking `origin/patch-18`.
- `patch-18` tip after fetch: `62df1dc97c5113a86f9c0f258cb90538f31efe89` (`Add refill fishing rod barrel at 5213 66 8912 (#400)`).
- Local/remote divergence: `git rev-list --left-right --count patch-18...origin/patch-18` returned `0 0`.
- Existing feature branch check: `banners-dyetub` did not exist locally.
- Creation command: `git switch -c banners-dyetub patch-18`.
- Creation point / merge base: `62df1dc97c5113a86f9c0f258cb90538f31efe89`.
- Initial feature/base divergence: `git rev-list --left-right --count patch-18...banners-dyetub` returned `0 0`.
- Current branch: `banners-dyetub`.
- No reset, deletion, merge, rebase, stash, or checkout-discard operation was used.

### Pre-existing working tree

The branch was intentionally created with the user's existing work present and preserved:

- Modified: `src/main/java/com/seggellion/britannia_mod/config/ModConfig.java` (local API base URL selection).
- Untracked: `.claude/`, `UltimaCraft_Banner_Dyeing_LLM_Build_Spec.md`, `UltimaCraft_Banner_and_Dyeing_System_Design.md`, `logs/`, and `tmp/`.

The two root specification files were read in full before branch creation. None of the pre-existing paths is part of the Milestone 0 commit.

### Files added

- `docs/banner-dyeing/PROJECT_FACTS.md`
- `docs/banner-dyeing/OPEN_QUESTIONS.md`
- `docs/banner-dyeing/IMPLEMENTATION_LOG.md`

No gameplay code, registrations, renderers, packets, screens, recipes, block entities, catalogue entries, or assets were added.

### Repository facts recorded

- Gradle 8.9 / NeoGradle UserDev 7.0.165.
- Minecraft 1.21.1 / NeoForge 21.1.72 / Java 21 / active official mappings.
- `britannia_mod` and `com.seggellion.britannia_mod` token mappings.
- NeoForge deferred registration conventions.
- Typed persistent/networked data components as the selected item-state architecture.
- NBT plus update-tag/update-packet block-entity conventions.
- Custom payload networking and direct client `Screen` flow.
- Client-only event gating and rendering registration.
- Static/manual recipe state and absent data generator.
- Empty unit/GameTest source state.
- Metal-specific blacksmithing material flow and the absence of a reusable general material ID.
- Branch, CI, and release integration conventions.
- The exact 33-banner catalogue constraint, including unnamed placeholder handling.

### Baseline commands and results

1. `git fetch --prune origin` — passed; added remote refs and confirmed `patch-18` remained current.
2. `.\gradlew.bat clean build --no-daemon` in the restricted sandbox — could not start because Gradle 8.9 download network access was denied (`java.net.SocketException: Permission denied: getsockopt`). This was an environment restriction, not a repository failure.
3. `.\gradlew.bat clean build --no-daemon` with approved dependency/network access — failed in `:neoFormPatch` before project compilation. A second identical clean-build invocation reproduced the failure.
4. `.\gradlew.bat test --no-daemon --stacktrace` with approved access — passed in 1m 10s. `compileTestJava` and `test` were `NO-SOURCE`. Project compilation emitted two existing warnings: missing Javadoc on `PlayerSleepMixin`'s `@Overwrite`, and the deprecated-for-removal `Item.initializeClient(IClientItemExtensions)` override in `OrderShieldItem`.
5. `.\gradlew.bat neoFormPatch --rerun-tasks --no-daemon --stacktrace` — passed/up-to-date in 20s, showing the NeoForm patch stage works outside the combined clean-build invocation.
6. `.\gradlew.bat clean --no-daemon` — passed in 12s.
7. `.\gradlew.bat build --no-daemon` immediately after the separate clean — passed in 42s; compiled, packaged `jar`/`jarJar`, and reported `test` / `testJunit` as `NO-SOURCE`.
8. `.\gradlew.bat tasks --all --no-daemon` — passed and confirmed `build`, `check`, `test`, `runData`, and `runGameTestServer` tasks.

Assessment: the combined `clean build` failure is pre-existing build/toolchain behavior, likely an ordering/cache interaction between parallel Gradle clean and NeoForm output. The inference is supported by the repeatable combined failure and successful isolated patch task plus separate clean/build. It does not currently block later work, provided verification uses separate `clean` and `build` invocations. No unrelated build fix was attempted.

### Documentation and post-change checks

1. Required-file/content validation — passed. All three documents exist; repository tokens, open-question categories, blacksmithing facts, and the exact-33/`Name Required` rule are present.
2. Trailing-whitespace scan across `docs/banner-dyeing/*.md` — passed with no matches.
3. `.\gradlew.bat build --no-daemon` — passed in 19s after the documentation changes; `test` and `testJunit` remained `NO-SOURCE`, and compilation/package tasks were up-to-date.
4. Final staged `git diff --check` and scope inspection are required immediately before commit.

### Decisions

- Use typed custom data components for banner and dye-tub item state.
- Use namespaced `ResourceLocation` IDs under `britannia_mod`.
- Do not reuse metal-only `UOMetalToolMaterial` or jewelry's separate metal enum for fabrics.
- Reuse blacksmithing's server-authoritative material-from-input interaction principles.
- Use the existing payload-opened client `Screen` convention for a slotless dye preview and a server-revalidated C2S confirmation.
- Keep renderers/screens/models in client packages and common state/codecs free of client imports.
- Register placed-banner rendering through the client render event; verify the non-deprecated item-render API in the rendering milestone.
- Preserve exactly 33 banner entries in release. Unnamed entries remain included with stable provisional IDs and `Name Required` status; placeholder names/dimensions are not final content.

### Known limitations and deviations

- The root specification files remain untracked and were not duplicated into `docs/`. The initiating prompt identifies the root copies as authoritative and limits the requested Milestone 0 commit to the three integration documents. This is a documented deviation from the playbook's general instruction to copy untracked specs into the documentation area.
- The repository has no implemented data-generation provider, reloadable JSON registry, unit tests, or GameTests. Later milestones must establish these incrementally.
- The repository's existing custom item renderer hook is deprecated for removal; its supported replacement must be verified before banner rendering work.
- No manual in-game check was required or performed for this documentation-only milestone.

### Commit

- Message: `docs(banners): record repository integration facts`
- Hash: this log is part of that commit; record the resulting hash in the Milestone 0 report.

### Next milestone

Stop at Gate A for owner review. Recommend Milestone 1 — Feature Skeleton, IDs, and Test Harness — only after the repository facts and integration choices are approved.
