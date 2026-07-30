# Perpendicular Medium Banner Live Review

Date prepared: 2026-07-29

Status: NOT PERFORMED - DRAFT INTAKES ARE NOT READY FOR INTEGRATION

This runbook covers the eight exact-name layers in
`C:/projects/britannia/raw fiels/tabbard/banner_medium.ai`. It contains no parallel-medium cases. Do not execute
the runtime matrix until the intake validator reports `READY_FOR_INTEGRATION` and the perpendicular-only
placement-profile conflict recorded in `OPEN_QUESTIONS.md` is resolved.

## Reusable procedure

1. Confirm the definition-specific intake is `READY_FOR_INTEGRATION`, integrate only the approved definition,
   regenerate the catalogue scaffold, and build a development client with operator permission level 2.
2. Run `/britannia banner validate`. Expect the data-derived active total, zero disabled definitions, no validation
   errors, and no purple fallback.
3. Run the natural brass, natural iron, direct blue, and dye-tub commands in the definition table.
4. For dye-tub interaction, hold the loaded tub in the main hand and the natural brass banner in the off hand.
   Open the preview, cancel once, reopen, apply, and compare with the direct blue banner.
5. Test only `wall_perpendicular` on north-, south-, east-, and west-facing support. The banner must project outward
   from the supporting wall, use anchor-only support, and render the untinted selected brass or iron mount.
6. Compare inventory, first- and third-person hand, dropped item, item frame, preview, placed natural, and placed
   recoloured rendering. Natural rendering uses only the complete base; recolouring adds only the tinted authored
   mask.
7. Save/reload, initial and late client tracking, F3+T, `/reload`, support removal, break/drop, pick block, and
   re-placement must preserve definition, material, resolved colour, source pigment, mount, and perpendicular
   orientation without changing the banner-state schema.
8. Record `PASS` or `FAIL` in every result cell. Do not infer a result from review images or automated tests.

## Definition-specific commands and expectations

Every proposed definition uses logical dimensions 1 x 2, `wall_perpendicular`, brass and iron, and all four
horizontal facings. The dimensions and geometry remain proposals pending live aspect review.

| Definition | Exact commands | Expected natural/fixed regions | Expected dyeable region | Expected geometry |
|---|---|---|---|---|
| `britannia_mod:tournament_medium` | Natural brass: `/britannia banner give @s britannia_mod:tournament_medium britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:tournament_medium britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:tournament_medium britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Alternating black and pale diamonds, three-point bottom, texture, shading, and apparent top attachment detail. Black diamonds and attachment detail remain fixed. | Pale diamonds become blue. | Shared tournament-pair silhouette; apparent attachment pixels require owner classification. |
| `britannia_mod:ceremonial_tournament` | Natural brass: `/britannia banner give @s britannia_mod:ceremonial_tournament britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:ceremonial_tournament britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:ceremonial_tournament britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Alternating dark and pale grey diamonds, three-point bottom, texture, shading, and apparent top attachment detail. Dark diamonds and attachment detail remain fixed. | Pale diamonds become blue. | Shared tournament-pair silhouette; apparent attachment pixels require owner classification. |
| `britannia_mod:iron_quarter` | Natural brass: `/britannia banner give @s britannia_mod:iron_quarter britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:iron_quarter britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:iron_quarter britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Gold/grey quartered field, single-point bottom, texture, shading, and visible crossbar. Grey quarters and crossbar remain fixed. | Gold quarters become blue. | Distinct wide pointed silhouette; crossbar pixels require owner classification. |
| `britannia_mod:outer_ward` | Natural brass: `/britannia banner give @s britannia_mod:outer_ward britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:outer_ward britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:outer_ward britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Dark left field, pale right field, battlement division, pointed bottom, texture, and shading. Dark left field remains fixed. | Pale right field becomes blue around the fixed battlement edge. | Shared pointed-ward silhouette. |
| `britannia_mod:ward_of_serpents` | Natural brass: `/britannia banner give @s britannia_mod:ward_of_serpents britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:ward_of_serpents britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:ward_of_serpents britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Six-panel black/grey field, serpent and ankh heraldry, pointed bottom, texture, and shading. Black panels and no unselected pixels change. | Authored symbols and grey panels selected by the mask become blue. | Shared pointed-ward silhouette. |
| `britannia_mod:serpent_guard` | Natural brass: `/britannia banner give @s britannia_mod:serpent_guard britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:serpent_guard britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:serpent_guard britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Alternating black/pale radial wedges, rounded lower corners, texture, and shading. Black wedges remain fixed. | Pale wedges become blue. | Shared rounded-guard silhouette. |
| `britannia_mod:crossroad_guard` | Natural brass: `/britannia banner give @s britannia_mod:crossroad_guard britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:crossroad_guard britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:crossroad_guard britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Dark quartered cloth, gold cross, rounded lower corners, texture, highlights, and shadows. Cloth remains fixed. | Gold cross becomes blue. | Shared rounded-guard silhouette. |
| `britannia_mod:argent_shield` | Natural brass: `/britannia banner give @s britannia_mod:argent_shield britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:argent_shield britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:argent_shield britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Pale shield field, dark border and bend, four-point stars, rounded bottom, loops, texture, shading, and visible crossbar. Dark bend remains fixed. | Pale field, border, and stars selected by the authored mask become blue. | Distinct narrow shield silhouette; crossbar pixels require owner classification. |

## Definition-specific result table

No live checks have been performed.

| Definition | Inventory item | Preview | Placed natural | Placed recoloured | Perpendicular | Brass | Iron | N/S/E/W | Save/reload | Break/drop | Pick block | Re-placement | Resource reload | Data reload | No purple fallback | Overall |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `tournament_medium` |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| `ceremonial_tournament` |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| `iron_quarter` |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| `outer_ward` |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| `ward_of_serpents` |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| `serpent_guard` |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| `crossroad_guard` |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
| `argent_shield` |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
