# Small Banner Family Live Review

Date prepared: 2026-07-28

Approval evidence updated: 2026-07-29

Status: GATE E PASS - PRODUCT-OWNER LIVE REVIEW COMPLETE

This record covers the six small-family asset packages prepared from
`C:/projects/britannia/raw fiels/tabbard/banner_small.ai`. Product-owner approval and live testing by Seggellion are recorded against commit
`e4f457b132667efc0c9789ee6044c47bedf68bdc` and the exact intake hashes. All six intakes validate, every listed
runtime check passed, and the family is approved for transition from `in_progress` to `complete`. The authoritative
batch summary is `content/banner-final-intake/SMALL_FAMILY_GATE_E_REVIEW.md`.

## Setup and reusable procedure

1. Confirm each intake is `READY_FOR_INTEGRATION`, integrate only that approved definition, run the scaffold, and
   build a development client with operator permission level 2.
2. Run `/britannia banner validate`. Expect 35 active definitions, zero disabled definitions, and no validation
   errors or purple fallback.
3. For each definition, run the natural brass, natural iron, and direct blue commands in the table exactly as
   written. The direct command isolates rendering from the dye-tub interaction.
4. Run `/britannia dye tub give @s britannia_mod:woad_blue`. Hold the loaded tub in the main hand and the natural
   brass banner in the off hand. Open the preview, compare current and proposed states, cancel once, reopen, apply,
   and compare the result with the directly created blue banner.
5. Test both `wall_parallel` and `wall_perpendicular` against north-, south-, east-, and west-facing supports. Repeat
   with brass and iron. The parallel mount must lie along the wall; the perpendicular mount must project outward.
   Mount geometry and material must remain separate, and neither mount may tint.
6. Compare inventory, first- and third-person hand, dropped item, item frame, preview, placed natural, and placed
   recoloured rendering. Natural rendering must use the complete base only; dyed rendering must add only the tinted
   authored mask.
7. Save and reload, exercise initial and late client tracking, press F3+T, run `/reload`, break/drop, remove support,
   pick block, and re-place. Stable definition ID, material ID, resolved colour ID, source pigment, mount,
   orientation, and configured drops must survive.
8. The product owner recorded `PASS` for every result cell. Automated tests and review images remain supporting evidence rather than substitutes for that live approval.

## Definition-specific commands and expectations

Every row uses the approved 1 x 1 logical dimensions, `wall_parallel`, `wall_perpendicular`, brass, iron, and all
four horizontal facings.

| Definition | Exact commands | Expected natural and fixed regions | Expected dyeable region | Expected geometry |
|---|---|---|---|---|
| `britannia_mod:silver_and_gold_pennon` | Natural brass: `/britannia banner give @s britannia_mod:silver_and_gold_pennon britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:silver_and_gold_pennon britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:silver_and_gold_pennon britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Paired pointed pennons, grey left cloth, gold right cloth, loops, rod, fabric texture, highlights, and shadows. Grey left cloth and all hardware remain fixed. | Gold right pennon becomes blue without changing the left pennon or hardware. | Approved shared paired-pennon geometry using the full authored 74 x 121 alpha bounds. |
| `britannia_mod:star_standard` | Natural brass: `/britannia banner give @s britannia_mod:star_standard britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:star_standard britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:star_standard britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Black rounded hanging field, pale crown, circular star-and-wreath heraldry, fabric texture, highlights, and shadows. Pale crown and heraldry remain fixed. | Black field becomes blue around the protected crown and heraldry. | Approved distinct rounded 70 x 120 Star Standard geometry. |
| `britannia_mod:ship_standard` | Natural brass: `/britannia banner give @s britannia_mod:ship_standard britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:ship_standard britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:ship_standard britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Narrow red rectangular field with gold/brown border, anchor heraldry, vertical ornaments, attachment hardware, highlights, and shadows. Border, anchor, ornaments, and hardware remain fixed. | Inner red field becomes blue without bleeding into the fixed decoration. | Approved distinct narrow rectangular 58 x 120 Ship Standard geometry. |
| `britannia_mod:pennon_of_silver` | Natural brass: `/britannia banner give @s britannia_mod:pennon_of_silver britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:pennon_of_silver britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:pennon_of_silver britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Paired pointed silver/grey pennons, loops, rod, texture, highlights, and shadows. Right pennon and all hardware remain fixed. | Left pennon becomes blue without changing the right pennon or hardware. | Same approved shared paired-pennon geometry as Silver and Gold Pennon. |
| `britannia_mod:iron_ward` | Natural brass: `/britannia banner give @s britannia_mod:iron_ward britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:iron_ward britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:iron_ward britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Three rounded dark tails beneath a gold scalloped crown, with fabric texture, highlights, and shadows. Crown and centre tail remain fixed. | Outer two tails become blue while the crown and centre tail remain unchanged. | Approved distinct three-tail 61 x 119 small geometry. |
| `britannia_mod:iron_ward_auxiliary` | Natural brass: `/britannia banner give @s britannia_mod:iron_ward_auxiliary britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:iron_ward_auxiliary britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:iron_ward_auxiliary britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Wider three-tail dark auxiliary ward beneath a pale crown, with central dark field, texture, highlights, and shadows. Crown and centre tail remain fixed. | Outer two tails become blue without recolouring the crown or centre tail. | Approved distinct wider and taller three-tail 64 x 121 small geometry. |

## Definition-specific result table

All cells were reported `PASS` by Seggellion on 2026-07-29.

| Definition | Inventory item | Preview | Placed natural | Placed recoloured | Parallel | Perpendicular | Brass | Iron | N/S/E/W | Save/reload | Break/drop | Pick block | Re-placement | Resource reload | Data reload | No purple fallback | Overall |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `silver_and_gold_pennon` | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| `star_standard` | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| `ship_standard` | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| `pennon_of_silver` | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| `iron_ward` | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| `iron_ward_auxiliary` | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
