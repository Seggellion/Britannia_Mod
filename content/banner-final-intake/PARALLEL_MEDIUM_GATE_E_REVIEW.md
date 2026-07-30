# Parallel Medium Banner Family Gate E Review

This record captures direct product-owner confirmation for the complete six-banner Parallel Medium family.
The manual review is complete; it is not an automated substitute for visual judgment.

## Review environment

- Reviewer: Seggellion (Product Owner)
- Review/closeout-record date: 2026-07-30
- Commit tested: `c61d8121d6d1224ea5647bedee8f3d13dd7af933`
- Minecraft: 1.21.1
- NeoForge: 21.1.72
- Resource packs: not separately supplied
- Shaders: not separately supplied
- Screenshots: not separately supplied

## Batch-level manual results

- All six stable IDs correct: PASS
- All six 128 x 128 base textures reviewed: PASS
- All six 128 x 128 dye masks reviewed: PASS
- Natural appearance reviewed for every banner: PASS
- Fixed heraldry, borders, ornamentation, and authored attachment pixels remain unchanged: PASS
- Dyeable regions recolour correctly: PASS
- Highlights and shadows remain visible: PASS
- Alpha edges are clean: PASS
- No colour bleed or halo: PASS
- No blurred or unintended texture filtering: PASS
- No purple or missing-texture fallback: PASS
- Inventory and hand rendering: PASS
- Dropped-item rendering: PASS
- Item-frame rendering: PASS
- Dye-preview current state: PASS
- Dye-preview proposed state: PASS
- Cancel is non-mutating: PASS
- Apply updates intended regions: PASS
- Re-dye replaces the prior colour: PASS
- Unlimited dye tubs remain unlimited: PASS
- Brass mount behaviour: PASS
- Iron mount behaviour: PASS
- Wall-parallel placement: PASS
- North-facing placement: PASS
- South-facing placement: PASS
- East-facing placement: PASS
- West-facing placement: PASS
- Anchor-only rendering and wall offset: PASS
- No wall overlap or z-fighting: PASS
- Mount and model alignment: PASS
- Save/reload: PASS
- Initial and late client tracking: PASS
- F3+T resource reload: PASS
- `/reload` data reload: PASS
- Break/drop: PASS
- Support loss: PASS
- Pick block: PASS
- Re-placement: PASS
- Item/preview/placed consistency: PASS
- Every unique geometry reviewed: PASS
- Block-atlas reload: PASS
- Earlier-family regression: PASS
- Parallel-only orientation enforcement: PASS
- No perpendicular Parallel Medium placement was exposed: PASS

## Approved definitions and reviewed assets

| Stable ID | Display name | Geometry | Base SHA-256 | Mask SHA-256 | Approval |
|---|---|---|---|---|---|
| `britannia_mod:verdant_grape_pennon` | Verdant Grape Pennon | `britannia_mod:banner/medium_wall/grape_rosette_pair/geometry` | `5faf14b7a221752158bbf59393e6b096f4df109db3716437614898794cf0af3b` | `c1aa265275d1a61e2221bc547b3665bf2288f62ae062bbee95a6dd85ef2dfddb` | APPROVED |
| `britannia_mod:silver_rosette_pennon` | Silver Rosette Pennon | `britannia_mod:banner/medium_wall/grape_rosette_pair/geometry` | `e8eb84f1e2fee6cff3e6210cce20846e4dff8c675b6d9fbf8d762a3209c42633` | `e938710b77cd045981a077b4ea80c6d0a5af713e2b3a6ea068e954ec95bb2be8` | APPROVED |
| `britannia_mod:four_seals_pennon` | Four Seals Pennon | `britannia_mod:banner/medium_wall/four_seals_pennon/geometry` | `0b7db42e5ef3969a347df79ee19cd1546f924a423227b5dd251ccc5a1f145a8f` | `81128cba4d6e1a49c60870eddf53ddd8c7bc7b1fcc04462b653a883a15cb9e14` | APPROVED |
| `britannia_mod:twin_spades_pennon` | Twin Spades Pennon | `britannia_mod:banner/medium_wall/twin_spades_pennon/geometry` | `f6e5a834a57b940ade9b84873b78ad71f0c91efa09eeb8d93ef0a4820ed3fc0b` | `6908bb015759849df80c3c2b4aae7a746d5982fa311a1ae6bb1513ad35d515db` | APPROVED |
| `britannia_mod:ankh_pennon` | Ankh Pennon | `britannia_mod:banner/medium_wall/ankh_pennon/geometry` | `65163afac35ae78167436a08be48f4029beb8df45b24fc0f544a6938191bfb80` | `7077db933ffbeb8383616f8bf34ba42f6c75472b6a0463599b23f36f2e393eab` | APPROVED |
| `britannia_mod:joined_wards` | Joined Wards | `britannia_mod:banner/medium_wall/joined_wards/geometry` | `8551830308310eb073cc06bcf4760d69db4ebe897c2aed88bfe49239a84c5a70` | `ec964aa2ae06ab42ca323c232c4a2e18e952d5cd3aecdb9dcd1b161a6d9dba82` | APPROVED |

All reviewed textures are 128 x 128, 8-bit RGBA two-file assets (`base_texture` plus `dye_mask`). Supported
orientation is `wall_parallel`; supported mounts are `britannia_mod:brass` and `britannia_mod:iron` for every
reviewed definition.

## Decision

- Batch approval: APPROVED
- Gate E: PASS
- Requested corrections: none
- Approved transition: exactly the six listed definitions move from `in_progress` to `complete`.
- Perpendicular Medium family: unchanged
- Crafting: not applicable - product-disabled
- Large-family work: may begin only after this closeout is committed
