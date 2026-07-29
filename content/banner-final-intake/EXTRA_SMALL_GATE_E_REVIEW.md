# Extra-Small Banner Gate E Review

This record captures direct product-owner confirmation for the complete nine-banner extra-small family. The manual review is complete; it is not an automated substitute for visual judgment.

## Review environment

- Reviewer: Product Owner
- Review/closeout-record date: 2026-07-28 (current local date; the exact execution date was not separately supplied)
- Commit tested: `bf68e4b0025f1aed9a905904a669a09f39e06d31`
- Minecraft: 1.21.1
- NeoForge: 21.1.72
- Resource packs: not supplied
- Shaders: not supplied
- Screenshots: not supplied

## Batch-level manual results

- All nine stable IDs correct: PASS
- All nine 128 x 128 base textures reviewed: PASS
- All nine 128 x 128 dye masks reviewed: PASS
- Natural appearance reviewed for every banner: PASS
- Fixed regions remain unchanged: PASS
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
- /reload data reload: PASS
- Break/drop: PASS
- Support loss: PASS
- Pick block: PASS
- Re-placement: PASS
- Item/preview/placed consistency: PASS
- All seven pigments represented: PASS
- All four materials represented: PASS
- Every unique geometry reviewed: PASS
- Block-atlas reload: PASS

## Approved definitions and reviewed assets

| Stable ID | Display name | Geometry | Base SHA-256 | Mask SHA-256 | Approval |
|---|---|---|---|---|---|
| `britannia_mod:road_guard` | Road Guard | `britannia_mod:banner/road_guard/geometry` | `46ce83a31b9954cea1b3934249eab919ca9408658772b96b0e2752c6aaa48b2d` | `8efeff71ca5c8689fef725c7fc3172b783687ffcb3c9dd0bf978874cad048f77` | APPROVED |
| `britannia_mod:pale_road_guard` | Pale Road Guard | `britannia_mod:banner/road_guard/geometry` | `d3a95ac9ea835943ee57f12fc8ff68048bcba65fbaa4a90c8761b6a0e22fb9bb` | `2f4c56225adfea15a0bf9f07a58f228555774c189a464af6de7722fe5a9c2da0` | APPROVED |
| `britannia_mod:red_crosslets` | Red Crosslets | `britannia_mod:banner/road_guard/geometry` | `222f27705b9543662db1788bde63fb81ba5efb510a738c811115fbcfa4fe1950` | `31162c610a4b49a049e4589e13d68e54e025771fa86ad13454eac58efdcf4b51` | APPROVED |
| `britannia_mod:captains_red_crosslets` | Captain's Red Crosslets | `britannia_mod:banner/road_guard/geometry` | `2eb1cf8b6aa5b1f863dc673b010281875723ab3c0c315328e92a7e56d9e2da68` | `48431385c961644d65cf2bf60f322efb132070a30636da3f5acc94747c463b89` | APPROVED |
| `britannia_mod:scarlet_court` | Scarlet Court | `britannia_mod:banner/road_guard/geometry` | `1d243ac0cff95dd4b3bc5c6e8c3c5117c31c0b747b1b8c7be1f66122ab7e2d2e` | `3e63c73a6abf07eabdbd71d2fb1311a2f7b4b2f3de39c3544e36310d6b1f1814` | APPROVED |
| `britannia_mod:verdant_court` | Verdant Court | `britannia_mod:banner/road_guard/geometry` | `d7b79fc3d313a2082b16193fabc9a47ffa226a7956cfe0b1ed00355d585c65dd` | `44aa39f534353d60027fbf4ca780a92700ad23dfd57b86ba3cd2d541bab0adb2` | APPROVED |
| `britannia_mod:small_curtain` | Small Curtain | `britannia_mod:banner/small_curtain/geometry` | `e5ead84ea05f73ad61281a0160e2c2875955a195af917494c1bc83849e96b355` | `39d7f7fdc5efc3e11139acbb69ed88ad30abc685d402ab6765a38dc3e8ba4651` | APPROVED |
| `britannia_mod:prosperity_standard` | Prosperity Standard | `britannia_mod:banner/road_guard/geometry` | `c40be2bbc878fb3102fd1fada88f150e507612635abe9b81132daaba9f5e4acf` | `187fa08bdd798125328e04b850dc761f4131c0caf8cfc7b0aacf252fd457a043` | APPROVED |
| `britannia_mod:guardian_standard` | Guardian Standard | `britannia_mod:banner/road_guard/geometry` | `d4a4245760608e8ec6048292827672e8d61836497431c173459218b29f9c6e2a` | `5b74d9f79a5f5130e9790aa23a30fe9b543a202dccae686205c84451d22d0b1e` | APPROVED |

All reviewed textures are 128 x 128, 8-bit RGBA two-file assets (`base_texture` plus `dye_mask`). Supported orientations are `wall_parallel` and `wall_perpendicular`; supported mounts are `britannia_mod:brass` and `britannia_mod:iron` for every reviewed definition, as derived from the authoritative intake records.

## Decision

- Batch approval: APPROVED
- Gate E: PASS
- Requested corrections: none
- Approved transition: exactly the nine listed definitions move from `in_progress` to `complete`.
- Crafting: not applicable - product-disabled
