# Banner and Dyeing Open Questions

Date: 2026-07-30

Milestone: Banner Content closed; unrelated future decisions retained below

## Banner Content milestone resolved

Seggellion reported every Parallel Large live-review check passed on 2026-07-30 against commit
`598dde33b4f2f322f1ed32c4773b8ab69080eb22`. All six Large definitions are `complete`, and no Large correction
remains open. Evidence is in `content/banner-final-intake/PARALLEL_LARGE_GATE_E_CLOSEOUT.md`, the completed matrix
in `docs/banner-dyeing/PARALLEL_LARGE_LIVE_REVIEW.md`, and each Large submission directory's `GATE_E_REVIEW.md`.

All five reviewed family groups are complete: nine Extra-small, six Small, eight perpendicular Medium, six parallel
Medium, and six parallel Large definitions. The final 35-definition catalogue has no placeholder, in-progress,
disabled, provisional-identity, or content-intake work remaining.

## Parallel Medium Gate E resolved

Seggellion reported every live-review check passed for all six Parallel Medium definitions on 2026-07-30 against
commit `c61d8121d6d1224ea5647bedee8f3d13dd7af933`. All six are `complete`; no Parallel Medium correction remains
open. Evidence is in `content/banner-final-intake/PARALLEL_MEDIUM_GATE_E_REVIEW.md`, the completed runbook, and
each submission directory's `GATE_E_REVIEW.md`.

## Perpendicular Medium Gate E resolved

Seggellion reported every live-review check passed for all eight Medium definitions on 2026-07-29 against commit
`79474963299603ae73b2efcaf58a9a8614dc881b`. All eight definitions are `complete`, and no Medium correction remains
open. Authoritative evidence is in `content/banner-final-intake/MEDIUM_FAMILY_GATE_E_REVIEW.md`, the completed
matrix in `docs/banner-dyeing/PERPENDICULAR_MEDIUM_LIVE_REVIEW.md`, and each Medium submission directory's
`GATE_E_REVIEW.md`.
## Small-family Gate E resolved

All six canonical Small definitions passed direct product-owner live review against commit
`e4f457b132667efc0c9789ee6044c47bedf68bdc`. Seggellion approved every item, preview, placed-rendering,
orientation, facing, mount, reload, and lifecycle check on 2026-07-29. The six definitions are `complete`; no
Small-family correction remains open. Authoritative evidence is in
`content/banner-final-intake/SMALL_FAMILY_GATE_E_REVIEW.md` and each Small submission directory's
`GATE_E_REVIEW.md`.

## Extra-small Gate E resolved

The nine-definition extra-small family completed direct product-owner live review on the 2026-07-28 closeout-record
date against commit `bf68e4b0025f1aed9a905904a669a09f39e06d31`. All shared and per-banner checks passed,
Gate E is `PASS`, all nine definitions are `complete`, and no extra-small correction remains open. The authoritative
records are `content/banner-final-intake/EXTRA_SMALL_GATE_E_REVIEW.md` and each intake directory's
`GATE_E_REVIEW.md`.

## Future two-file architecture questions

- Decide the artist guidance for deliberate partial-alpha recolouring, including useful blend ranges and review
  examples.
- Record whether a future design ever genuinely needs fixed foreground detail over a recoloured underlayer at the
  exact same pixel. That is not an alternative in the current architecture; it would require product-owner approval
  and a separate architecture milestone.

## Historical Milestone 16 content-intake baseline (superseded)

Earlier intake preparation and Road Guard-only review states are historical, not active questions. They are retained
in the implementation log and Road Guard review history. The current nine-banner evidence supersedes them; no
extra-small live-review item remains unresolved.

The read-only intake validator remains available at:

```text
.\tools\scaffold_banners.bat --check-final-intake <path>
```

It does not decide art direction, approve input, infer dimensions, copy assets, modify the catalogue, or perform
visual review. Banner crafting remains product-disabled, survival acquisition remains unresolved, and Milestone 17
has not started.

## Milestone 15 provisional administration decisions

- The command root is provisionally `/britannia` because the repository has no coherent shared administrator root.
  Decide before release whether this syntax should remain, be aliased, or move under another established operator
  namespace.
- Direct administrator selection of a resolved colour records no source pigment. This intentionally represents
  administrative construction rather than a historical dye event; decide whether a future audit mode needs different
  provenance without changing this default.
- Empty and unlimited loaded dye tubs are available to operators. Finite-use tubs remain test-fixture-only; decide
  whether a later administrator command needs an explicitly bounded-use form.
- Future NPC/shop pigment pricing, stock, availability, unlock conditions, and refresh policy remain entirely
  undefined. `PigmentSourceService` supplies validated stacks and immutable entries only.
- Survival banner acquisition remains undefined. Banner crafting remains rejected and is not reopened by this
  question.
- Decide whether the `/britannia` administration/debug commands ship in production releases or are limited to
  development/operator builds. They currently use the repository's normal level-2 operator permission.
- Live in-game command, full-inventory drop, multiplayer target, and end-to-end obtain/dye/place/break matrices remain
  unperformed in this non-interactive run. The real Brigadier tree and service boundaries have automated coverage.

## Corrective Milestone 14R product decision

Banner crafting is not currently part of the approved feature. Any future acquisition or crafting design requires a
new product decision.

The footprint-area fabric costs, reusable-pattern policy, pattern acquisition, cloth input identities, brass mount
crafting input, recipe-book behavior, and crafting economy implemented by the rejected Milestone 14 are not accepted
defaults. They have been removed rather than retained as disabled or provisional runtime behavior.

Banner acquisition remains deferred to approved admin/development tooling. Corrective Milestone 14R adds no command,
creative configured-banner entry, NPC hook, loot table, recipe, pattern, blueprint, or template alternative.

## Milestone 13 Gate D provisional decisions

- Milestone 13 used generated planar placeholder geometry for the five existing footprint families. The mesh was
  deliberately two-sided, with a finite 64-block view distance and bounded loaded-cell light. Later content milestones
  supplied approved per-family geometry; view-distance and lighting tuning remain separate future decisions.
- Parallel and perpendicular transforms, persisted dimensions, support rules, anchor convention, allowed
  orientations, and allowed mounts remain exactly the Milestone 12 Gate D defaults. A definition/footprint mismatch
  renders the explicit missing-content fallback and never rewrites persisted occupancy.
- Milestone 13 originally reused a three-image diagnostic banner stack. Milestone 16A superseded that historical
  decision with one complete base texture and one selective grayscale-alpha dye mask; brass/iron and missing-content
  resources remain separate. Server data packs still cannot distribute client models or textures.
- Automated tests cover all five footprint families, both orientations, all four facings, brass and iron, tint
  separation, dynamic bounds, cache generations, missing content, state update tags/packets, and dedicated-server
  class isolation. This was the Milestone 13 state; later administrative acquisition and family Gate E runs
  completed the banner visual-review matrix.

Banner Content closeout resolved final banner artwork, geometry, dimensions, orientations, and mounts. Still unresolved
outside content intake: view-distance/lighting tuning, multiplayer client resource-pack distribution, and a gameplay
workflow for directly dyeing or swapping mounts on placed banners.

## Milestone 12 Gate D provisional decisions

- Both stable orientations are implemented. Wall-parallel width grows viewer-right and retains top-row wall support;
  wall-perpendicular width grows outward and uses anchor-only wall support. These began as development policies; the
  final catalogue now approves each definition's supported orientation set.
- Sneak-use cycles a server-owned per-player orientation preference; ordinary use places with the normalized
  selection. This interaction is provisional until manual usability review.
- Brass and iron flow end-to-end as mount variants but do not alter occupancy or support. Static placed blocks remain
  neutral diagnostic geometry; the ghost distinguishes brass, iron, blocked, invalid-support, and missing-data states.
- Client ghosts are explicitly advisory where server-only protection cannot be known. Manual in-game checks were not
  performed in this non-interactive milestone run.

Banner Content closeout resolved final dimensions, allowed orientations and mounts, default mounts, geometry, and placed
artwork. Still unresolved outside content intake: support semantics, orientation-selection UX, and whether later gameplay
changes ever require different mount occupancy. The current 35-definition matrix and family Gate E evidence approve the
implemented content behavior.

## Milestone 11 provisional structure policies

- Wall-parallel placement uses a top-mounted hanging rule: every occupied top-row cell requires a sturdy backing
  face, while lower cloth cells do not. Removing any top-row support removes the complete structure and returns one
  configured item; removing only a lower backing block does not. Gate D may revise this with orientation/mount work.
- Explosions remove the complete structure but return no configured item. This is deliberate because the actual
  NeoForge callback calculates per-cell loot before block removal; the no-drop policy makes repeated callbacks
  deterministic and duplicate-proof.
- Player survival break and support loss are the only Milestone 11 configured-drop causes. Creative removal, placement
  rollback, orphan cleanup, explosions, administrative cleanup, and detected external replacement drop nothing.
- A missing part is restored only when its persisted occupied cell is loaded and replaceable. An obstruction is never
  overwritten; the remaining known banner cells are removed without a drop and the obstruction is preserved.
- External block replacement is detected through `onRemove` for ordinary `setBlock` paths. A world-edit tool that
  bypasses normal block callbacks can temporarily leave parts until the deferred chunk integrity pass; no universal
  hook exists for tools that bypass both callbacks and chunk lifecycle.
- Milestone 11 placement used static diagnostic anchor/part block models and had no layered placed renderer, ghost,
  orientation selection, recipe, command, or direct placed-banner dyeing. Later milestones added the approved renderer,
  ghost, selection, and administrative acquisition while crafting and direct placed-banner dyeing remain absent.

Still unresolved: whether Gate D retains top-row-only support and the no-drop explosion policy, and whether a later
world-edit compatibility integration should expose an explicit structure-removal API.

## Milestone 9 client-resource boundary

- Server banner definitions, canonical palette display colours, mount references, and placeholder status are
  synchronized to each client as immutable display-only metadata on login and server data-pack reload.
- Models and textures are client resource-pack content. A server data-pack can override display metadata and select
  an asset already packaged by the client, but it cannot distribute a new model or texture. Unknown or absent assets
  therefore render the explicit missing-content diagnostic rather than substituting another banner.
- Milestone 16A supersedes Milestone 9's original image split. The current placeholder uses one complete base and
  one selective grayscale-alpha mask for every material; material-specific banner textures are intentionally not an
  open option.
- At Milestone 9 the full in-game rendering matrix was unperformed because no safe configured-banner acquisition path
  existed. Later administrative acquisition and all five family Gate E reviews completed the banner content matrix
  without adding recipes or creative catalogue entries.

Still unresolved: whether future multiplayer releases require an associated client resource-pack distribution
policy for server-defined banner assets. Material-specific banner textures are rejected by the authoritative
two-file architecture and are not an open option.

## Milestone 8 provisional preview and finite-use decisions

- Dye preview sessions live for 30 seconds, are bound to one player, use random UUID identities, and allow one active
  session per player. A new preview replaces the previous one. This lifetime and replacement UX remain provisional.
- Session staleness uses exact `ItemStack` copies (`ItemStack.matches`, including item, count, and all components) and
  the immutable registry snapshot's publication object identity. The registry layer has no numeric generation API;
  any newly published snapshot object invalidates the preview even when its decoded content is equivalent.
- Exact no-op means both resolved colour and source pigment already match. It consumes no finite use and closes the
  preview with `ALREADY_DYED`. Closing on this result is the selected clear UX but remains revisable.
- A different pigment resolving to the same colour is a real application: provenance changes, and one finite use is
  consumed. This preserves the established historical source-pigment contract.
- A finite tub reaching zero remains loaded with its pigment for diagnostics. It cannot preview or apply again and is
  not emptied, destroyed, replaced, or washed automatically. This zero-use behavior remains provisional.
- Milestone 9 upgrades the screen to render detached current/proposed banner stacks through the same shared item
  renderer while retaining the Milestone 8 localized text and authoritative colour swatches.

Still unresolved: final session lifetime/UX, final finite tub capacity and acquisition rules, whether depleted tubs
can later be refilled or washed, and whether exact no-op should retain rather than close the preview.

## Milestone 7 provisional item decisions

- The one shared banner item has maximum stack size 1. This is the conservative repository-compatible state-safety
  default and remains a product decision; component equality is still tested independently so a later approved stack
  size increase cannot merge different banner states.
- Raw `britannia_mod:banner` stacks contain no default banner component and are visibly unconfigured. No definition,
  material, colour, or mount is silently selected.
- No banner stack is added to the creative tab in Milestone 7. The tab callback has no safe dependency on the
  server-data reload snapshot needed to generate configured stacks. Factory tests provide development access until
  Milestone 15 admin tooling; an invalid raw stack is not exposed in the tab.
- Missing-colour natural fallback retains an unavailable historical `source_pigment_id`. This preserves provenance;
  full validation continues to report the unavailable pigment until content returns or an explicit later migration
  changes policy.

Still unresolved: whether banner stacks should ever exceed one item, and whether a later component-aware creative-tab
bootstrap is desirable before the Milestone 15 admin tools.

## Milestone 6 provisional gameplay defaults

Milestone 6 implements the build playbook's reversible defaults so the item/component interaction can be tested end
to end. These are provisional development rules, not permanently approved product rules:

- Loading a tub consumes exactly one pigment item in normal survival play.
- Players with creative inventory permissions consume no pigment.
- Loading a different pigment replaces the pigment already stored in the tub.
- Loaded tubs have unlimited uses, represented only by absent `remaining_uses`.
- Loading the same pigment is a successful no-change interaction: it consumes nothing and emits no success sound or
  particles.

Still unresolved: whether any of these defaults are final; whether a loaded tub can be emptied or washed; whether
finite uses will be enabled; final item artwork; and final pigment availability, acquisition, and economy.

## Gate B identity history (superseded by Banner Content closeout)

Gate B originally approved a 33-definition identity set, retained 14 provisional names, and closed the
`tournament_medium` and `pennon_of_silver` label questions. Later owner-approved migrations expanded the authoritative
catalogue to 35 canonical definitions while preserving catalogue indices and decode compatibility. The final
Banner Content evidence approves all display names, dimensions, orientations, mounts, geometries, and artwork; no
provisional identity or content decision remains open.

Milestone 4 established deterministic scaffold paths. Milestone 9 selected vanilla JSON baked models and block-atlas
PNG textures for the item renderer; Milestone 16A retained that mapping while limiting banner image IDs to complete
base plus selective mask and keeping mount textures independent. The completed family integrations supplied the final
artwork and geometry.

## Milestone 3 physical-asset validation boundary resolved

Milestone 3 originally validated only logical `ResourceLocation` identities because the packaged-file mapping was not
yet authoritative. Later rendering and content milestones established it: banner geometry maps to model JSON, base
and mask identities map to PNG textures, placement profiles map to data JSON, and mount resources remain separate.
Scaffold checks, family integration tests, and the final JAR audit now validate physical existence. This is no longer
an open Banner Content question.

## Milestone 2 placement-profile boundary resolved

Milestone 2 originally stored only versioned profile identity, declared dimensions, and `requires_wall_support` because
occupied-cell offsets, facing transforms, anchors, and support rules were not yet authoritative. Milestones 10 through
12 established the persisted placement contract, and the final content integrations approved the profile, geometry,
dimensions, orientations, and mounts for every definition. Future changes to support semantics or interaction UX remain
separate gameplay decisions, not Banner Content blockers.

This register separates facts that the repository can resolve from product decisions that require owner input. A question is blocking only when proceeding would force an incompatible public API, saved-data contract, stable ID, or asset convention.

## Questions answerable from the repository

These questions were resolved during Milestone 0 and are not owner decisions.

| Question | Resolution | Evidence |
|---|---|---|
| What platform is this? | Minecraft 1.21.1 on NeoForge 21.1.72, Java 21 | `build.gradle`, `gradle.properties`, generated mod metadata |
| What mappings are active? | Official mappings through NeoForm/UserDev; Parchment properties exist but are unused | `neoFormApplyOfficialMappings` task output and no Parchment configuration in `build.gradle` |
| What is the mod ID/package? | `britannia_mod` / `com.seggellion.britannia_mod` | `gradle.properties`, `BritanniaMod.java` |
| How are items, blocks, block entities, and components registered? | NeoForge `DeferredRegister` / `DeferredHolder` on the mod event bus | Classes under `registry/` and `BritanniaMod` constructor |
| How should new item instance state be stored? | A typed registered `DataComponentType` with persistent `Codec` and network `StreamCodec` | `DataComponentRegistry`, `WineData`, `WineBottleItem` |
| How do block entities persist and sync? | `saveAdditional` / `loadAdditional`, update tag/packet, `setChanged`, block update | `WineBottleBlockEntity` |
| How are packets registered? | `CustomPacketPayload` + `StreamCodec` through `RegisterPayloadHandlersEvent` and `PayloadRegistrar` | `NetworkHandler` and payload records |
| How are non-container screens opened? | Client-only `Screen` opened from an S2C handler; actions sent by C2S payload | Blacksmith screen and payload flow |
| Is there a shared material ID type suitable for fabrics? | No. Existing material enums are metal- and item-family-specific | `UOMetalToolMaterial`, `MaterialQualityJewelryItem.UOMaterial` |
| Is there a resource-reload registry convention? | No. The only local JSON loader is startup-only classpath/Gson loading | `OreVeinLoader`; no reload listener registrations |
| Is data generation implemented? | No provider exists; only the `runData` task and generated-resource path are configured | `build.gradle`, Gradle task list, absent `src/generated` |
| What tests exist? | No unit or GameTest sources; `test` is `NO-SOURCE`; `runGameTestServer` exists | source tree and baseline Gradle output |
| What renderer conventions exist? | Static JSON models, GeckoLib renderers, custom geometry loader, and block-entity renderers registered in client setup | assets tree, `ClientModSetup`, `OrderShieldRenderer` |

Resolved in Milestone 9: the supported banner path uses baked-model registration/replacement and render passes; no
new `Item.initializeClient` hook or BEWLR was added. The unrelated `OrderShieldItem` warning remains unchanged.

## Product decisions requiring owner input

Banner Content closeout resolved three former questions: every one of the 35 definitions supports brass and iron,
materials share the definition's authored base rather than selecting material-specific textures, and all final banner
names, dimensions, orientations, mounts, geometries, and artwork are approved. The unrelated owner decisions still
open are:

1. Does loading a dye tub consume one dye item, retain it, or partially consume a multi-use dye source?
2. Are dye tubs unlimited-use in the first release, or do they store a finite use count?
3. Can a loaded tub be emptied or washed, and does that recover anything?
4. Is direct dyeing of placed banners in the first release, or must players break, dye, and replace them?
5. Is mount style fixed when a banner is acquired, or can it be swapped later?
6. Must every dye operation use a confirmation screen, or may repeat dyeing support an expedited interaction?
7. How are special dyes such as ice dye obtained and priced?
8. Should nearest-colour matching always choose the closest compatible colour, or reject results beyond an owner-defined threshold?
9. Is there a natural/bleach operation to restore an undyed material colour?
10. Approve or revise the Milestone 5 development palette colours and the final pigment catalogue.
11. Decide final special-pigment compatibility restrictions and whether rare pigments need additional semantics.
12. Decide whether authored OKLab remains persisted long term or is migrated to computed-only data.

## Deferred non-blocking decisions

- Default reversible dye-tub behavior if still unanswered at its milestone: replace the stored pigment, consume one dye item, and use unlimited tub applications, as specified by the build playbook.
- Direct placed-banner dyeing can remain outside the first release.
- Rare dye economy, visual effects, multi-region tinting, washing, dye crafting, and other dyeable textiles are post-release hooks.
- Milestone 5 development palettes and pigments prove the architecture but are not final art-direction-approved
  content.
- The Gradle combined `clean build` NeoForm race can be owned by build maintenance; separate `clean` then `build` succeeds and is sufficient for continued feature verification.

## Blocking decisions

No Banner Content decision remains blocking: all 35 entries are canonical, approved, integrated, live-reviewed, and
complete. Release approval, distribution policy, acquisition/economy, and later gameplay decisions remain separate
from this closed content milestone.