# Medium Banner Family Gate E Review

This record captures direct product-owner confirmation for the complete eight-banner perpendicular Medium family.
The manual review is complete; it is not an automated substitute for visual judgment.

## Review environment

- Reviewer: Seggellion (Product Owner)
- Review/closeout-record date: 2026-07-29
- Commit tested: `79474963299603ae73b2efcaf58a9a8614dc881b`
- Minecraft: 1.21.1
- NeoForge: 21.1.72
- Resource packs: not separately supplied
- Shaders: not separately supplied
- Screenshots: not separately supplied

## Batch-level manual results

- All eight stable IDs correct: PASS
- All eight 128 x 128 base textures reviewed: PASS
- All eight 128 x 128 dye masks reviewed: PASS
- Natural appearance reviewed for every banner: PASS
- Fixed regions, including authored crossbars and attachment pixels, remain unchanged: PASS
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
- Wall-perpendicular placement: PASS
- North-facing placement: PASS
- South-facing placement: PASS
- East-facing placement: PASS
- West-facing placement: PASS
- Rotation and mirroring: PASS
- Mount and model alignment: PASS
- Save/reload: PASS
- Relog and client tracking: PASS
- F3+T resource reload: PASS
- `/reload` data reload: PASS
- Break/drop: PASS
- Support loss: PASS
- Pick block: PASS
- Re-placement: PASS
- Item/preview/placed consistency: PASS
- All seven pigments represented: PASS
- All four materials represented: PASS
- Every unique geometry reviewed: PASS
- Block-atlas reload: PASS
- Extra-small and Small family regression: PASS
- Perpendicular-only orientation enforcement: PASS
- No parallel Medium placement was exposed: PASS

## Approved definitions and reviewed assets

| Stable ID | Display name | Geometry | Base SHA-256 | Mask SHA-256 | Approval |
|---|---|---|---|---|---|
| `britannia_mod:tournament_medium` | Tournament Medium | `britannia_mod:banner/medium/tournament_pair/geometry` | `0fd6d82d3e674f964387be404739bd17a298af04fad6d36e1a7867e86549d503` | `739d65774502e03213da5fc56a8b21222c1febbda1e5163ca05a0cd674d521bb` | APPROVED |
| `britannia_mod:ceremonial_tournament` | Ceremonial Tournament | `britannia_mod:banner/medium/tournament_pair/geometry` | `b8b623c0b03c8ee2dffb9183610d4dbc05703cc10876b6a783041fd7ab689804` | `8153536154f5163ccbca26cacec58c4c5cd60a1adbdd7e5c7ddc60954b8464ab` | APPROVED |
| `britannia_mod:iron_quarter` | Iron Quarter | `britannia_mod:banner/medium/iron_quarter/geometry` | `a0a7e52030d084b734737ed6dce970a402e38bc02cdd684b6196eb4271a337ca` | `1959fca2ee4cfe059ffd629cb2130d5835488ebad78280381df5cc14d0c750a8` | APPROVED |
| `britannia_mod:outer_ward` | Outer Ward | `britannia_mod:banner/medium/pointed_ward/geometry` | `24269b8f0e07ff6f5050490217457f0fdb7931d57ebee4c51e046143af85a016` | `ff2c4c2231b6aa0090c0c1fb0650bcf79f5c1da868cea1375e68a77a1f7daae0` | APPROVED |
| `britannia_mod:ward_of_serpents` | Ward of Serpents | `britannia_mod:banner/medium/pointed_ward/geometry` | `73332ee0c37c5c72fae721de76c9ea85141845bc59252d348d84b433104ff6ff` | `badcb1bcea7a0fbc37ef0bc0d4257a9c60378244e0a1b4eb756a47deb8a01d8d` | APPROVED |
| `britannia_mod:serpent_guard` | Serpent Guard | `britannia_mod:banner/medium/rounded_guard/geometry` | `6b157d409cb268bf03f305f0eda85450bb8dac3eaeaf227303ace82baba72ce8` | `b3cd16f6c62431d44b76df6eef9a6554e4b03b668deb46a0d9daaf791202eaee` | APPROVED |
| `britannia_mod:crossroad_guard` | Crossroad Guard | `britannia_mod:banner/medium/rounded_guard/geometry` | `78f8580a8a308e0b3895fd7992a14c37074d8e03115565cdce18c0b76bcfdd59` | `f7cea5c270a963ed2782f47c73eabf8401d94915d516da7b3c499ae98fcc5f3a` | APPROVED |
| `britannia_mod:argent_shield` | Argent Shield | `britannia_mod:banner/medium/argent_shield/geometry` | `3f0f1829dd09920f15f4fe984608dd85088769a40cac7a3c7787f32100017556` | `0852a65d273ff9220cd56db45142db0b048050887f15925a22f479fddbdc67de` | APPROVED |

All reviewed textures are 128 x 128, 8-bit RGBA two-file assets (`base_texture` plus `dye_mask`). Supported
orientation is `wall_perpendicular`; supported mounts are `britannia_mod:brass` and `britannia_mod:iron` for every
reviewed definition.

## Decision

- Batch approval: APPROVED
- Gate E: PASS
- Requested corrections: none
- Approved transition: exactly the eight listed definitions move from `in_progress` to `complete`.
- Parallel `medium-wall` family: unchanged
- Crafting: not applicable - product-disabled
- Resource packs: not separately supplied
- Shaders: not separately supplied
- Screenshots: not separately supplied
