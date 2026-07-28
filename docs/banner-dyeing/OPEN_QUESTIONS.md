# Banner and Dyeing Open Questions

Date: 2026-07-27

Milestone: 16 Batch 1A

## Milestone 16A remaining content and review questions

- Supply and approve the final complete base texture and selective grayscale-alpha dye mask for every banner.
- Approve the exact final pixel dimensions for each banner's two aligned images.
- Decide the artist guidance for deliberate partial-alpha recolouring, including useful blend ranges and review
  examples.
- Record whether a future design ever genuinely needs fixed foreground detail over a recoloured underlayer at the
  exact same pixel. That is not an alternative in the current architecture; it would require product-owner approval
  and a separate architecture milestone.
- Complete the unverified Road Guard Gate E matrix: brass versus iron; natural cotton, wool, linen, and silk;
  inventory, hand, dropped, item-frame, and preview contexts; parallel versus perpendicular; all four facings;
  save/reload; relog; F3+T resource reload; `/reload`; break/drop; pick block; re-placement; support loss; exact
  fixed-charcoal inspection; and all seven pigments.
- Obtain explicit visual approval that the remaining contexts agree, only mask-active yellow regions recolour,
  charcoal and transparency remain fixed, highlights and shadows remain readable, and mounts remain untinted.
  Product-owner evidence already confirms placed rendering, absence of the purple fallback, approved Road Guard
  model/art visibility, and basic visible dye application; Road Guard cannot become `complete` and Gate E cannot pass
  until the remaining evidence exists.

## Milestone 16 content-intake readiness

Road Guard has an approved intake and is integrated as the sole `in_progress` proof of concept. Its partial live
visual review has passed, while the remaining Gate E matrix is pending. The other six extra-small definitions, and
the remaining 26 catalogue definitions, still have no approved final owner assets or decision packages. The intake
kit defines the evidence required to integrate each without guessing.

Owner input is still required per definition for final display name, dimensions, orientations, supported/default
mounts, placement profile, geometry convention, localization, the complete base texture, the selective dye mask,
provenance, and distribution permission. A completed intake may authorize an `in_progress` integration; it does not
authorize `complete`.

The read-only validator is implemented at:

```text
.\tools\scaffold_banners.bat --check-final-intake <path>
```

It deliberately does not decide art direction, approve input, infer dimensions from pixels, copy assets, modify the
catalogue, or perform live visual review. Whether future final assets need new pixel dimensions or custom geometry
must be decided from actual approved inputs rather than pre-emptive tooling changes.

Banner crafting remains product-disabled, survival acquisition remains unresolved, and Milestone 17 has not started.

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

- Placed rendering uses generated planar placeholder geometry for the five existing footprint families. The mesh is
  deliberately two-sided, uses a finite 64-block view distance, and samples bounded loaded-cell light. These are
  implementation defaults pending final visual and performance review, not final art direction.
- Parallel and perpendicular transforms, persisted dimensions, support rules, anchor convention, allowed
  orientations, and allowed mounts remain exactly the Milestone 12 Gate D defaults. A definition/footprint mismatch
  renders the explicit missing-content fallback and never rewrites persisted occupancy.
- Milestone 13 originally reused a three-image diagnostic banner stack. Milestone 16A superseded that historical
  decision with one complete base texture and one selective grayscale-alpha dye mask; brass/iron and missing-content
  resources remain separate. Server data packs still cannot distribute client models or textures.
- Automated tests cover all five footprint families, both orientations, all four facings, brass and iron, tint
  separation, dynamic bounds, cache generations, missing content, state update tags/packets, and dedicated-server
  class isolation. Manual in-game visual verification remains unperformed because the repository still has no safe
  configured-banner acquisition path and Milestone 13 does not add one.

Still unresolved at Gate D: final heraldic artwork, final cloth and mount geometry, approved dimensions,
per-definition orientations and mounts, view-distance/lighting tuning, multiplayer client resource-pack
distribution, and a gameplay workflow for directly dyeing or swapping mounts on placed banners.

## Milestone 12 Gate D provisional decisions

- Both stable orientations are implemented. Wall-parallel width grows viewer-right and retains top-row wall support;
  wall-perpendicular width grows outward and uses anchor-only wall support. These are development policies, not final
  per-definition content approval.
- Sneak-use cycles a server-owned per-player orientation preference; ordinary use places with the normalized
  selection. This interaction is provisional until manual usability review.
- Brass and iron flow end-to-end as mount variants but do not alter occupancy or support. Static placed blocks remain
  neutral diagnostic geometry; the ghost distinguishes brass, iron, blocked, invalid-support, and missing-data states.
- Client ghosts are explicitly advisory where server-only protection cannot be known. Manual in-game checks were not
  performed in this non-interactive milestone run.

Still unresolved at Gate D: approve or revise final dimensions, each definition's allowed orientations and mounts,
default mounts, support semantics, selection UX, mount-specific geometry/occupancy, and final placed artwork. The
generated 33-row automated matrix is evidence of implementation coverage, not content or visual approval.

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
- Development placement remains static diagnostic anchor/part block models. No layered placed renderer, placement
  ghost, orientation selection, recipe, command, or direct placed-banner dyeing was introduced.

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
- The full in-game rendering matrix remains unperformed until a safe configured-banner acquisition path exists; no
  recipes, commands, or creative catalogue entries were added solely for rendering QA.

Still unresolved: whether future multiplayer releases require an associated client resource-pack distribution
policy for server-defined banner assets, and whether release art uses distinct authored fabric textures per material.

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

## Gate B decisions closed before Milestone 5

Gate B approved the 33-definition identity set and closed the two scaffold-label questions:

- `tournament_medium` remains the stable ID and `Tournament Medium` remains the current canonical scaffold label.
- `pennon_of_silver` remains the stable ID and `Pennon of Silver` remains the current canonical scaffold label.
- All 14 unnamed banners remain under their approved provisional stable IDs with visible `Name Required` labels.
- Stable IDs do not change merely because a display label changes later.

Final display names, dimensions, orientations, mount support, recipes, geometry, and artwork remain unapproved.
The current source page/row references remain authoritative catalogue references.

Milestone 4 established deterministic scaffold paths. Milestone 9 selected vanilla JSON baked models and block-atlas
PNG textures for the item renderer; Milestone 16A retains that mapping while limiting banner image IDs to complete
base plus selective mask and keeping mount textures independent. This resolves the placeholder convention only;
final artwork remains a separate decision.

## Milestone 3 physical-asset validation boundary

Milestone 3 validates every authored geometry, texture, palette, and other logical reference as a namespaced
`ResourceLocation`. It does not validate physical model or texture existence. Repository evidence does not yet define
one reliable mapping from the extensionless logical IDs in `BannerAssets` and `MountDefinition` to packaged files:
the project uses vanilla JSON models, GeckoLib geometry, textures with `.png` suffixes, and a custom geometry loader.
Choosing one path convention here would reject valid future assets or silently bless the wrong resource type. The
rendering/content milestone must establish that mapping before physical existence checks can be made authoritative.
This is an asset convention question only; it does not affect stable definition IDs or snapshot safety.

## Milestone 2 placement-profile boundary

The specifications establish declared width/height and wall-support requirements, but they do not yet establish
authoritative occupied-cell offsets, facing transforms, anchor-cell selection, or whether support must exist behind
every occupied cell. Milestone 2 therefore stores only versioned profile identity, declared dimensions, and
`requires_wall_support`. Milestone 3 may validate that a definition and referenced profile agree on dimensions, but
the final occupied-cell saved-data contract remains deferred until the placement design is implemented in Milestones
10 through 12. No offset or anchor semantics were guessed in this milestone.

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

These decisions affect player experience or content approval. The data model can represent all options, so none blocks the next architecture milestone unless noted.

1. Does loading a dye tub consume one dye item, retain it, or partially consume a multi-use dye source?
2. Are dye tubs unlimited-use in the first release, or do they store a finite use count?
3. Can a loaded tub be emptied or washed, and does that recover anything?
4. Is direct dyeing of placed banners in the first release, or must players break, dye, and replace them?
5. Is mount style fixed when a banner is acquired, or can it be swapped later?
6. Do all 33 designs support both brass and iron mounts, or does each definition have an approved subset?
7. Do cotton, wool, linen, and silk use distinct fabric textures in release one, or palette differences only?
8. Must every dye operation use a confirmation screen, or may repeat dyeing support an expedited interaction?
9. How are special dyes such as ice dye obtained and priced?
10. Should nearest-colour matching always choose the closest compatible colour, or reject results beyond an owner-defined threshold?
11. Is there a natural/bleach operation to restore an undyed material colour?
12. Approve final banner display names, dimensions, orientations, mounts, and art as content batches reach their
    review gates; the stable identity set itself is already approved.
13. Approve or revise the Milestone 5 development palette colours and the final pigment catalogue.
14. Decide final special-pigment compatibility restrictions and whether rare pigments need additional semantics.
15. Decide whether authored OKLab remains persisted long term or is migrated to computed-only data.

## Deferred non-blocking decisions

- Default reversible dye-tub behavior if still unanswered at its milestone: replace the stored pigment, consume one dye item, and use unlimited tub applications, as specified by the build playbook.
- Direct placed-banner dyeing can remain outside the first release.
- Rare dye economy, visual effects, multi-region tinting, washing, dye crafting, and other dyeable textiles are post-release hooks.
- Milestone 5 development palettes and pigments prove the architecture but are not final art-direction-approved
  content.
- Whether the first banner data loader supports live resource reload on day one or initially loads validated server data at startup can be decided in the registry milestone without changing stable IDs or item state.
- A documentation/scaffold implementation language will be selected from tools already accepted by the project when Milestone 4 begins.
- The Gradle combined `clean build` NeoForm race can be owned by build maintenance; separate `clean` then `build` succeeds and is sufficient for continued feature verification.

## Blocking decisions

None blocks Milestone 9.

The following become blocking at their stated review gates:

- Before a final content entry is marked complete: approve its final name, dimensions, supported orientations, mounts, and original art.
- Before release: explicitly approve any catalogue entries that remain provisional/placeholders.

The lack of final names or dimensions is not currently blocking. Exactly 33 entries must still be present; unnamed banners receive stable provisional IDs and a visible `Name Required` status, and provisional names/dimensions are not approved lore.
