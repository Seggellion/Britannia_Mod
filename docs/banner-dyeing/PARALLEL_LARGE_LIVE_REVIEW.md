# Parallel Large Live Review

Date prepared: 2026-07-30

Status: READY FOR LIVE REVIEW — INTEGRATED; GATE E UNPERFORMED

This runbook covers only the six approved definitions extracted from the authoritative
`C:/projects/britannia/raw fiels/tabbard/banner_large.ai`. Seggellion approved the prepared packages, provenance,
distribution, canonical ID migrations, 2 x 2 dimensions, six geometries, and parallel-only placement on
2026-07-30. The commands below use the integrated canonical IDs. No live check in this document has been performed,
and no perpendicular Large case belongs here.

## Reusable parallel Large procedure

1. Confirm every intake validates `READY_FOR_INTEGRATION`. Record product-owner approval, provenance, distribution
   permission, approved logical dimensions, and approved geometry without inventing any field.
2. Migrate only the approved `large_01` through `large_06` identities to their source-named canonical IDs at
   unchanged indices, add decode-only compatibility aliases, integrate through catalogue generation, run the
   scaffold twice, and build a development client with operator permission level 2.
3. Run `/britannia banner validate`. Expect the data-derived active total, zero disabled definitions, no duplicate
   provisional/final identities, and no missing texture/model/profile finding.
4. Run the row's natural-brass, natural-iron, and direct-blue commands exactly. The direct-blue item isolates
   rendering from dye-tub interaction.
5. Run `/britannia dye tub give @s britannia_mod:woad_blue`. Hold the loaded tub in the main hand and the natural
   brass banner in the off hand. Open the preview, cancel once, reopen, apply, and compare with the direct-blue item.
6. Place only as `wall_parallel` on north-, south-, east-, and west-facing supports. Confirm viewer-right span,
   full approved footprint, anchor-only rendering, correct offsets, no wall overlap, and no z-fighting.
7. Repeat placement with brass and iron. The shared physical mount geometry must stay untinted; `parallel` must not
   appear as a player-facing material.
8. Compare inventory, first- and third-person hand, dropped item, item frame, preview, placed natural, and placed
   recoloured rendering. Natural uses the complete base only. Dyed rendering adds the tinted authored mask while
   fixed heraldry, borders, ornamentation, shadows, highlights, and authored attachment/crossbar pixels remain
   unchanged.
9. Save/reload, test initial and late client tracking, press F3+T, run `/reload`, break/drop, remove support, pick
   block, and re-place. Stable definition ID, material ID, resolved colour ID, source pigment, mount, parallel
   orientation, and placed structure must survive.
10. Record `PASS` or `FAIL` in every result cell. Gate E remains unperformed until the product owner reviews the
    exact integrated hashes and every row passes.

## Definition-specific commands and expectations

Every row is approved as 2 x 2 logical dimensions, only `wall_parallel`, brass and iron mounts, brass by default,
and all four horizontal facings.

| Definition | Exact commands | Expected natural and fixed regions | Expected dyeable region | Expected geometry and attachment behaviour |
|---|---|---|---|---|
| `britannia_mod:tournament_curtain` | Natural brass: `/britannia banner give @s britannia_mod:tournament_curtain britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:tournament_curtain britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:tournament_curtain britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Alternating pale and dark diamond curtain with dark pointed lower edge, fabric shading, and transparency. Dark diamonds and edge remain fixed. | The authored pale diamonds become blue without recolouring the dark diamonds. | Distinct wide, shallow pointed-curtain silhouette. Authored pixels remain in the base; the parallel runtime mount remains a separate untinted pass. |
| `britannia_mod:threefold_chain_standard` | Natural brass: `/britannia banner give @s britannia_mod:threefold_chain_standard britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:threefold_chain_standard britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:threefold_chain_standard britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Gold crossbar, suspension loops, scalloped top border, three rounded tails, chain/flower heraldry, borders, shading, and transparency remain fixed. | The authored grey cloth fields become blue without recolouring the gold structure, heraldry, or borders. | Distinct three-lobed standard silhouette. Authored crossbar/loops remain fixed base artwork while the runtime mount stays separate. |
| `britannia_mod:iron_serpent_standard` | Natural brass: `/britannia banner give @s britannia_mod:iron_serpent_standard britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:iron_serpent_standard britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:iron_serpent_standard britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Black panels, pale ankhs, pale/dark serpents, fixed dividers, pointed lower edge, highlights, and shadows remain unchanged. | The authored pale cloth panels become blue while black panels and heraldry stay fixed. | Distinct three-point divided standard; no runtime hardware is included in either texture. |
| `britannia_mod:silver_fleur_curtain` | Natural brass: `/britannia banner give @s britannia_mod:silver_fleur_curtain britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:silver_fleur_curtain britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:silver_fleur_curtain britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Perforated top band, dark zigzag bands, silver fleur heraldry, multi-point lower edge, fabric texture, highlights, and shadows remain fixed. | The authored pale zigzag cloth bands become blue without recolouring fleurs or dark bands. | Distinct perforated, multi-point curtain silhouette; fixed authored attachment pixels remain in the base. |
| `britannia_mod:gilded_trellis_curtain` | Natural brass: `/britannia banner give @s britannia_mod:gilded_trellis_curtain britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:gilded_trellis_curtain britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:gilded_trellis_curtain britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Perforated top band, pale/dark trellis bands, multi-point lower edge, shading, and fixed authored details remain unchanged. | The authored gilded trellis selection becomes blue without expanding into unselected pale/dark content. | Distinct perforated trellis curtain silhouette; the shared parallel runtime mount remains separate and untinted. |
| `britannia_mod:gilded_chevron_curtain` | Natural brass: `/britannia banner give @s britannia_mod:gilded_chevron_curtain britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:brass`<br>Natural iron: `/britannia banner give @s britannia_mod:gilded_chevron_curtain britannia_mod:cotton britannia_mod:cotton_natural britannia_mod:iron`<br>Direct dyed: `/britannia banner give @s britannia_mod:gilded_chevron_curtain britannia_mod:cotton britannia_mod:cotton_blue britannia_mod:brass`<br>Dye tub: `/britannia dye tub give @s britannia_mod:woad_blue` | Pale chevron bands, multi-point lower edge, unselected fixed regions, fabric texture, highlights, and shadows remain unchanged. | The authored gilded chevron selection becomes blue exactly where shown in the intake preview. | Distinct broad chevron curtain silhouette; authored pixels stay in the base and brass/iron applies only to the runtime mount. |

## Definition-specific results

Use `PASS` or `FAIL`; every cell is intentionally blank.

| Definition | Inventory item | Preview | Placed natural | Placed recoloured | Full footprint/anchor | Parallel only | Brass | Iron | N/S/E/W | Save/reload | Initial/late tracking | Break/drop | Pick block | Re-placement | Resource reload | Data reload | No purple fallback | Overall |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `tournament_curtain` | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| `threefold_chain_standard` | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| `iron_serpent_standard` | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| `silver_fleur_curtain` | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| `gilded_trellis_curtain` | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
| `gilded_chevron_curtain` | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — | — |
