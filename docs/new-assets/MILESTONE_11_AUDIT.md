# Milestone 11 Cross-System Audit

## Result

The requested new-assets surface is consistently registered and data-complete. The audit covers 37 block IDs, the two textile items, and the ibis entity/spawn egg. Automated guards now check exact registrations, block items, creative-tab exposure, localization, parseable blockstates/models/loot, tool tags, transparent render layers, placeholder disclosure, and the principal functional integrations.

Silk is the only intentionally missing asset. It remains a distinct future textile material and must not alias `britannia_mod:spiders_silk`.

## Asset-family findings

| Family | IDs or systems audited | Result |
|---|---|---|
| Small decoration | globe, fern, moonglow bush, folded cloth, bolt of cloth, pewter mug, kettle, plates and silverware | Complete; globe and folded cloth were corrected to use cutout rendering |
| Merchant carts | red, purple, blue, green, yellow, white | Complete; one final ID per approved color and temporary-art status retained |
| Large decoration | fountain, scarecrow, dress form, loom | Complete; existing multiblock, collision, sound/material, and render contracts retained |
| Storage | small, medium, and large crates | Complete; block entities, inventory persistence, atomic teardown, loot, and axe tags retained |
| Utility | water well and ladder | Complete; the well now has the missing pickaxe tag, and bucket/watering-can/water-pitcher behavior remains wired; ladder Adventure placement and axe-removal paths remain wired |
| Ibis | ibis entity, spawn egg, white/scarlet variants, Jhelom population policy | Complete; server bootstrap calls the three-area combined population policy with a cap of 15; temporary art is disclosed in the item name |
| Training dummy | training dummy and skill-training service | Complete; the missing axe tag was added; weapon mapping, cooldown, durability protection, animation, and 25.0 activity cap remain integrated |
| Textiles | spinning wheel, ball of yarn, spool of thread, loom, folded cloth | Complete for authorized materials and ratios; silk remains explicitly deferred and spiders' silk remains unsupported |
| Display cases | display case independent/end/middle/corner behavior | Complete; decorative-only boundary and lack of storage/display inventory retained |
| City moongate | moongate block and legacy-top migration | Complete; translucent layer, one-cell teleport contract, existing city destinations, and temporary-art disclosure retained |
| Sandstone | brick, three wall forms, two windows, two posts, battlement, column | Complete; the missing sandstone-brick localization was added; all entries have item, creative, state/model, loot, localization, and pickaxe data |

## Corrections made during the audit

1. Added cutout render-layer registration for the globe and folded cloth, whose textures contain transparency.
2. Added the water well to the pickaxe-mineable tag.
3. Added the training dummy to the axe-mineable tag.
4. Added the missing `custom_sandstone_brick` localization.
5. Marked the ibis spawn egg and city moongate as temporary art in their player-facing names.
6. Strengthened the manifest boundary that future textile silk is not spiders' silk.

## Automated coverage

`NewAssetsCrossSystemAuditTest` protects the cross-system integration surface against registration drift and missing data. Earlier milestone unit tests and GameTests remain responsible for detailed geometry, collision, sound/material selection, multiblock transactions, storage, processing ratios, spawning, skill gains, neighbor recomputation, and teleport behavior.

The source manifest contains no unexplained `MISSING` entries: its one `MISSING` record is the owner-deferred future silk item. All purchased-pack and generated art remains temporary and is tracked for replacement. Models requiring dimensional re-authoring remain documented in `ASSET_IMPORT_MANIFEST.md`.

## Closure boundary

This milestone establishes code/data consistency. Visual quality, interaction feel, multiplayer observation, and owner acceptance remain Milestone 12 live-validation work.
