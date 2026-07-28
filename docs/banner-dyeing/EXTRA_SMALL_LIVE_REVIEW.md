# Extra-Small Banner Family Live Review

Date prepared: 2026-07-28

Status: NOT PERFORMED

This is the reusable Gate E procedure for the nine current 128 x 128 extra-small assets. Record `PASS` or `FAIL`
only while running the live build. Do not reuse the historical Road Guard result.

## Setup and reusable procedure

1. Start a development client with operator permission level 2. Use the ordinary shared banner item; there are no
   per-definition items.
2. Run `/britannia banner validate`. Expected result: 35 active definitions, 0 disabled, and no validation error.
3. For each table row, run both natural commands and the direct dyed command exactly as written. The direct dyed
   command isolates rendering from the interaction flow.
4. Also run `/britannia dye tub give @s britannia_mod:woad_blue`. Put the loaded tub in the main hand and that row's
   natural brass banner in the off hand. Use the tub, compare current/proposed preview, cancel once, reopen, apply,
   and confirm it matches the directly created blue banner.
5. Place natural and dyed copies against suitable support in `wall_parallel` and `wall_perpendicular`. For each
   orientation, test north-, south-, east-, and west-facing supports with both brass and iron.
6. Compare inventory, first/third-person hand, dropped item, item frame, dye preview, and placed appearance. The
   natural state must use the authored base only; the dyed state must add the mask-selected blue recolour. The
   mount must never tint.
7. Save/reload, relog or exercise initial/late tracking, press F3+T, run `/reload`, break/drop, pick block, remove
   support, and re-place. Definition, material, colour, source pigment, mount, orientation, appearance, and exactly
   one configured drop must survive as applicable.
8. Record observations and every result below. Any failure leaves the definition `in_progress`.

Common expected mount behavior:

- Parallel: the banner lies parallel to the support wall and uses the parallel attachment/rod arrangement.
- Perpendicular: the banner projects outward and uses the distinct wall-rooted perpendicular attachment.
- Brass and iron alter only mount material appearance; neither mount is dyed.
- North, south, east, and west must preserve the readable front/back relationship without mirroring or incorrect
  rotation.

## Definition-specific commands and expectations

All rows support `wall_parallel`, `wall_perpendicular`, brass, iron, and all four horizontal facings.

| Definition | Exact commands | Expected natural/fixed/dyeable appearance | Expected fabric geometry |
|---|---|---|---|
| `britannia_mod:road_guard` | Natural brass: `/britannia banner give @s britannia_mod:road_guard britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:road_guard britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:road_guard britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Natural yellow upper cloth; charcoal lower split tails and small pale ties remain fixed. Yellow/white mask-selected cloth becomes blue while charcoal/ties remain unchanged. | Shared narrow Road Guard geometry with split lower silhouette. |
| `britannia_mod:pale_road_guard` | Natural brass: `/britannia banner give @s britannia_mod:pale_road_guard britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:pale_road_guard britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:pale_road_guard britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Natural pale upper cloth; grey/pale split-tail contrast and small ties stay fixed where excluded. Authored pale mask-selected cloth becomes blue. | Shared narrow Road Guard geometry with split lower silhouette. |
| `britannia_mod:red_crosslets` | Natural brass: `/britannia banner give @s britannia_mod:red_crosslets britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:red_crosslets britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:red_crosslets britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Red/gold crosslet head panel, charcoal tail, pale ties, highlights, and heraldry remain fixed. The authored lower cloth selection becomes blue. | Shared narrow Road Guard geometry with split lower silhouette. |
| `britannia_mod:captains_red_crosslets` | Natural brass: `/britannia banner give @s britannia_mod:captains_red_crosslets britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:captains_red_crosslets britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:captains_red_crosslets britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Red/blue crosslet head panel, grey left tail, gold right-tail detail, ties, and heraldry remain fixed. Only authored cloth selection becomes blue. | Shared narrow Road Guard geometry with split lower silhouette. |
| `britannia_mod:scarlet_court` | Natural brass: `/britannia banner give @s britannia_mod:scarlet_court britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:scarlet_court britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:scarlet_court britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Gold border/crosslets, red fixed detailing, rod/ties, shadows, and highlights stay intact. The authored inner field selection becomes blue. | Shared Road Guard presentation geometry; texture alpha supplies the rounded hanging silhouette. |
| `britannia_mod:verdant_court` | Natural brass: `/britannia banner give @s britannia_mod:verdant_court britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:verdant_court britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:verdant_court britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Gold border/vine, dark outer field, rod/ties, highlights, and shadows remain fixed. The authored inner cloth/vine-adjacent selection becomes blue without recolouring gold ornament. | Shared Road Guard presentation geometry; texture alpha supplies the rounded hanging silhouette. |
| `britannia_mod:small_curtain` | Natural brass: `/britannia banner give @s britannia_mod:small_curtain britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:small_curtain britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:small_curtain britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Natural cream curtain retains folds, top rod/rings, highlights, shadows, and transparent gaps. Authored curtain fabric selection becomes blue without introducing coloured-base pixels into the mask. | Distinct wider flat-bottomed Small Curtain geometry; it must not look narrowed or split like Road Guard. |
| `britannia_mod:prosperity_standard` | Natural brass: `/britannia banner give @s britannia_mod:prosperity_standard britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:prosperity_standard britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:prosperity_standard britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Grey/gold chevron head, dark outline, gold texture/detail, rod/ties, highlights, and shadows remain fixed. The authored long lower field becomes blue. | Shared Road Guard presentation geometry; texture alpha supplies the rounded long standard silhouette. |
| `britannia_mod:guardian_standard` | Natural brass: `/britannia banner give @s britannia_mod:guardian_standard britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:guardian_standard britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:guardian_standard britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Gold/grey chevron head, dark outline/field, rod/ties, highlights, and shadows remain fixed. The authored interior field becomes blue without bleeding into the border/head. | Shared Road Guard presentation geometry; texture alpha supplies the rounded long standard silhouette. |

## Results

Use one row per definition. Add screenshots or a linked evidence folder where available.

| Definition | Natural | Blue/direct | Dye-tub preview/apply | Parallel mount | Perpendicular mount | N/S/E/W | Brass/iron untinted | Item/preview/placed | Persistence/reloads/tracking | Break/drop/pick/re-place | Overall | Notes/evidence |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `road_guard` | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | PENDING | |
| `pale_road_guard` | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | PENDING | |
| `red_crosslets` | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | PENDING | |
| `captains_red_crosslets` | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | PENDING | |
| `scarlet_court` | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | PENDING | |
| `verdant_court` | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | PENDING | |
| `small_curtain` | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | PENDING | |
| `prosperity_standard` | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | PENDING | |
| `guardian_standard` | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | NOT PERFORMED | PENDING | |
