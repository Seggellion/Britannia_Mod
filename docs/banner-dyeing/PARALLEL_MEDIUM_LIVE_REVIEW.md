# Parallel Medium Live Review

Date prepared: 2026-07-30

Status: NOT PERFORMED — INTEGRATED AS `in_progress`; READY FOR GATE E

This runbook covers only the six `medium-wall` definitions extracted from
`C:/projects/britannia/raw fiels/tabbard/banner_medium_wall.ai`. Seggellion approved the exact prepared assets,
provenance, 1 × 2 dimensions, parallel-only placement, and five geometry groups on 2026-07-30. All six packages
validate `READY_FOR_INTEGRATION` and are integrated as `in_progress`. No perpendicular case belongs in this review.

## Reusable parallel Medium procedure

1. Confirm the definition's intake validator result is `READY_FOR_INTEGRATION`, integrate only the approved assets,
   run the catalogue scaffold twice, and build a development client with operator permission level 2.
2. Run `/britannia banner validate`. Expect the data-derived active total, zero disabled definitions, and no missing
   texture/model/profile finding.
3. Run the row's natural-brass, natural-iron, and direct-blue commands exactly. The direct-blue item isolates
   rendering from dye-tub interaction.
4. Run `/britannia dye tub give @s britannia_mod:woad_blue`. Hold the loaded tub in the main hand and the natural
   brass banner in the off hand. Open the preview, cancel once, reopen, apply, and compare the result with the
   directly created blue banner.
5. Place the banner only as `wall_parallel` on north-, south-, east-, and west-facing supports. The fabric and
   `wall_parallel` physical mount must lie along the wall and span in the established viewer-right direction. Confirm
   anchor-only rendering, no wall overlap, and no z-fighting.
6. Repeat placement with brass and iron. The physical mount geometry stays the same while its material changes.
   Neither mount may receive banner dye. No player-facing material named `parallel` may appear.
7. Compare inventory, first- and third-person hand, dropped item, item frame, dye preview, placed natural, and placed
   recoloured rendering. Natural uses the complete base only; dyed rendering adds the tinted authored mask. Fixed
   heraldry, borders, ornamentation, and any authored attachment/crossbar pixels remain unchanged.
8. Save/reload, exercise initial and late client tracking, press F3+T, run `/reload`, break/drop, remove support, pick
   block, and re-place. Stable definition ID, material ID, resolved colour ID, source pigment, mount, parallel
   orientation, and structure state must survive.
9. Record `PASS` or `FAIL` in every result cell. Gate E remains unperformed until the product owner reviews the exact
   integrated hashes and every row passes.

## Definition-specific commands and expectations

Every row uses the approved 1 × 2 logical dimensions, only `wall_parallel`, brass and iron mounts, brass by default,
and all four horizontal facings.

| Definition | Exact commands | Expected natural and fixed regions | Expected dyeable region | Expected geometry and attachment behaviour |
|---|---|---|---|---|
| `britannia_mod:verdant_grape_pennon` | Natural brass: `/britannia banner give @s britannia_mod:verdant_grape_pennon britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:verdant_grape_pennon britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:verdant_grape_pennon britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Gold pointed pennon with dark grape-vine heraldry, fabric texture, highlights, and transparent crenellated top openings. The vine remains fixed. | The authored gold cloth field becomes blue without recolouring the vine. | Shared grape/rosette pointed geometry; authored top silhouette remains in the base while the separate parallel mount lies along the wall. |
| `britannia_mod:silver_rosette_pennon` | Natural brass: `/britannia banner give @s britannia_mod:silver_rosette_pennon britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:silver_rosette_pennon britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:silver_rosette_pennon britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Silver-grey pointed pennon with pale rosette-vine heraldry, fabric texture, highlights, and transparent crenellated top openings. The pale vine remains fixed. | The authored silver-grey cloth field becomes blue without recolouring the vine. | Same shared grape/rosette geometry and mount behaviour as Verdant Grape Pennon. |
| `britannia_mod:four_seals_pennon` | Natural brass: `/britannia banner give @s britannia_mod:four_seals_pennon britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:four_seals_pennon britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:four_seals_pennon britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Rounded split field with pale left cloth, dark right cloth, five dark seal discs, top suspension opening, texture, highlights, and shadows. The right field and seals remain fixed. | The pale left half becomes blue without bleeding into the dark half or seal discs. | Distinct wide rounded geometry; top opening remains transparent and the runtime parallel mount stays separate and untinted. |
| `britannia_mod:twin_spades_pennon` | Natural brass: `/britannia banner give @s britannia_mod:twin_spades_pennon britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:twin_spades_pennon britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:twin_spades_pennon britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Diagonal gold/dark pennon with stair-step division, dark upper spade, gold lower spade, texture, highlights, and fixed dark regions. | The authored gold field and lower spade become blue; the dark field, stair-step details, and upper spade remain unchanged. | Distinct shallow-point geometry; authored edge/attachment pixels stay in the base and the shared parallel mount remains a separate pass. |
| `britannia_mod:ankh_pennon` | Natural brass: `/britannia banner give @s britannia_mod:ankh_pennon britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:ankh_pennon britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:ankh_pennon britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Narrow U-ended dark pennon with gold upper field and ankh, curved top opening, fixed dark attachment-tab row, texture, highlights, and shadows. | The gold upper field and ankh become blue; the dark lower field and tab row remain unchanged. | Distinct narrow U-ended geometry; the authored tab row remains protected while brass/iron applies only to the separate parallel mount. |
| `britannia_mod:joined_wards` | Natural brass: `/britannia banner give @s britannia_mod:joined_wards britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:joined_wards britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:joined_wards britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Swallowtail quartered field with gold quadrants, black/pale checker quadrants, fixed top strip, texture, highlights, and shadows. | Both gold quadrants become blue without changing either checker quadrant or the top strip. | Distinct two-point swallowtail geometry; authored top pixels remain part of the base and the wall-parallel mount stays separate and untinted. |

## Definition-specific results

No result has been performed. Replace each `—` with `PASS` or `FAIL` during the future Gate E session.

| Definition | Inventory item | Preview | Placed natural | Placed recoloured | Parallel only | Brass | Iron | N/S/E/W | Anchor/offset | Save/reload | Initial/late tracking | Break/drop | Pick block | Re-placement | Resource reload | Data reload | No purple fallback | Overall |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `verdant_grape_pennon` | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| `silver_rosette_pennon` | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| `four_seals_pennon` | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| `twin_spades_pennon` | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| `ankh_pennon` | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| `joined_wards` | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
