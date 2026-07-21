# Banner and Dyeing Open Questions

Date: 2026-07-20

Milestone: 6

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

Milestone 4 now provides a deterministic scaffold-level mapping from every emitted logical placeholder asset ID to
its declared JSON model or PNG file. The broader runtime mapping across future vanilla models, GeckoLib geometry,
textures, and the custom geometry loader remains unresolved until the rendering asset convention is selected. The
placeholder mapper is deliberately not presented as that universal rendering contract.

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

Repository-answerable follow-up for the rendering milestone: verify the supported NeoForge 21.1 replacement for deprecated `Item.initializeClient(IClientItemExtensions)` before choosing the banner item renderer. The current code compiles but the baseline emits a removal warning, so copying it without verification is unsafe. This does not block Milestones 0–8.

## Product decisions requiring owner input

These decisions affect player experience or content approval. The data model can represent all options, so none blocks the next architecture milestone unless noted.

1. Does loading a dye tub consume one dye item, retain it, or partially consume a multi-use dye source?
2. Are dye tubs unlimited-use in the first release, or do they store a finite use count?
3. Can a loaded tub be emptied or washed, and does that recover anything?
4. Is direct dyeing of placed banners in the first release, or must players break, dye, and replace them?
5. Is mount style fixed at crafting time, or can it be swapped later?
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

None blocks Milestone 1.

The following become blocking at their stated review gates:

- Before a final content entry is marked complete: approve its final name, dimensions, supported orientations, mounts, and original art.
- Before release: explicitly approve any catalogue entries that remain provisional/placeholders.

The lack of final names or dimensions is not currently blocking. Exactly 33 entries must still be present; unnamed banners receive stable provisional IDs and a visible `Name Required` status, and provisional names/dimensions are not approved lore.
