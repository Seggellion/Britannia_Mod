# Flower Illustrator Layer Manifest

## Approved source and policy

- Status: **Milestone 19 committed locally; Milestone 20 validation complete with uncommitted records awaiting owner review**
- Source: `C:\projects\britannia\raw fiels\flowers.ai`
- Approved SHA-256: `1B5407855D448B8385CC6D067858A347DF3C0B0993B390382A36EE1D42C0A9EE`
- Size: 80,453,426 bytes
- Last modified (UTC): `2026-08-05T03:53:13Z`
- Canvas: Artboard 1, `[0,0,128,-128]`, 128 x 128 points
- Output: 128 x 128 transparent PNG24, straight alpha, Art Optimized; numeric RGB treated as sRGB
- Relevant layers: 56
- Approved bases: 49
- Approved white masks: 33
- Approved base-only stages retaining transparent mask placeholders: 16

The exact source role objects are named page items inside stage layers. An `@name` path segment denotes a named page item. Bounds use Illustrator's `left,top,right,bottom` point order. For all 33 authored-mask stages, the current `@base_texture` is the detailed embedded original at the stage-specific bounds recorded below. The former 128 x 128 alpha-disjoint export raster is retained hidden as `@base_texture_alpha_disjoint_nonexport` at `[0,0,128,-128]`. Illustrator exports the current detailed base through the approved 128 x 128 Artboard 1 crop.

The current AI is untagged RGB after Illustrator's scripted save. Its pixel values are treated as sRGB by the PNG pipeline. The immediately prior alpha-disjoint source is retained at `C:\projects\britannia\raw fiels\codex_backups\flowers_pre_detail_preserving_tint_20260804_205234.ai` (SHA-256 `00E0C2F5CE56422361FC88C7F8F0BAD20A337B1FD85F33ED4252B6680F22A223`).

Path shorthand:

- `M:` = `src/main/resources/assets/britannia_mod/models/block/flowers/`
- `T:` = `src/main/resources/assets/britannia_mod/textures/block/flowers/`

## Approved top-level layer tree

| Order | Source container | Repository species | Visible / locked | Children | Role | Approval |
|---:|---|---|---|---:|---|---|
| 1 | `hyacinth` | `hyacinth` | Yes / No | 7 | NON_EXPORT container | APPROVED |
| 2 | `lilly` | `lily` | Yes / No | 7 | NON_EXPORT container | APPROVED owner alias |
| 3 | `campion` | `campion` | Yes / No | 7 | NON_EXPORT container | APPROVED |
| 4 | `poppy` | `poppy` | Yes / No | 7 | NON_EXPORT container | APPROVED numeric stage mapping |
| 5 | `orfluer` | `orfluer` | Yes / No | 7 | NON_EXPORT container | APPROVED |
| 6 | `foxglove` | `foxglove` | Yes / No | 7 | NON_EXPORT container | APPROVED |
| 7 | `snowdrop` | `snowdrop` | Yes / No | 7 | NON_EXPORT container | APPROVED; direct extras excluded |

## Approved stage/object mapping

Expected output dimensions are 128 x 128. Base-only rows approve base replacement while retaining the current transparent mask placeholder.

| # | Source stage layer | Visible / locked | Repository target | Base source object / bounds | Mask source object / bounds | Canonical model | Proposed outputs | Status |
|---:|---|---|---|---|---|---|---|---|
| 1 | `hyacinth/stage_1` | Yes / No | `hyacinth:1` | `hyacinth/stage_1/@base_texture` / `-3.74475774812436,-0.74169312070262,131.659497571029,-136.145948439857` / 135.404 x 135.404 pt | NONE AUTHORED - retain transparent placeholder | `M:hyacinth/stage_1.json` | `T:hyacinth/stage_1_base_texture.png`; `T:hyacinth/stage_1_dye_mask.png` | APPROVED BASE; RETAIN MASK PLACEHOLDER |
| 2 | `hyacinth/stage_2` | Yes / No | `hyacinth:2` | `hyacinth/stage_2/@base_texture` / `-3.74475774812436,-0.74169312070262,131.659497571029,-136.145948439857` / 135.404 x 135.404 pt | NONE AUTHORED - retain transparent placeholder | `M:hyacinth/stage_2.json` | `T:hyacinth/stage_2_base_texture.png`; `T:hyacinth/stage_2_dye_mask.png` | APPROVED BASE; RETAIN MASK PLACEHOLDER |
| 3 | `hyacinth/stage_3` | Yes / No | `hyacinth:3` | `hyacinth/stage_3/@base_texture` / `-3.74475774812436,-0.74169312070262,131.659497571029,-136.145948439857` / 135.404 x 135.404 pt | `hyacinth/stage_3/@dye_mask` / `9.25297913285431,-13.612012698477,122.830987074198,-63.5401302599048` / 31 white paths | `M:hyacinth/stage_3.json` | `T:hyacinth/stage_3_base_texture.png`; `T:hyacinth/stage_3_dye_mask.png` | APPROVED BASE + MASK |
| 4 | `hyacinth/stage_4` | Yes / No | `hyacinth:4` | `hyacinth/stage_4/@base_texture` / `-3.74475774812436,-0.74169312070262,131.659497571029,-136.145948439857` / 135.404 x 135.404 pt | `hyacinth/stage_4/@dye_mask` / `5.99975578728117,-17.6323847943931,123.371163624675,-86.1289759692791` / 79 white paths | `M:hyacinth/stage_4.json` | `T:hyacinth/stage_4_base_texture.png`; `T:hyacinth/stage_4_dye_mask.png` | APPROVED BASE + MASK |
| 5 | `hyacinth/stage_5` | Yes / No | `hyacinth:5` | `hyacinth/stage_5/@base_texture` / `-3.74475774812436,-0.74169312070262,131.659497571029,-136.145948439857` / 135.404 x 135.404 pt | `hyacinth/stage_5/@dye_mask` / `5.5739450594283,-8.54323837835454,123.877579140748,-82.9674454013038` / 139 white paths | `M:hyacinth/stage_5.json` | `T:hyacinth/stage_5_base_texture.png`; `T:hyacinth/stage_5_dye_mask.png` | APPROVED BASE + MASK |
| 6 | `hyacinth/stage_6` | Yes / No | `hyacinth:6` | `hyacinth/stage_6/@base_texture` / `-3.40425531914298,0.19148936169586,128.46808510638,-131.680851063828` / 131.872 x 131.872 pt | `hyacinth/stage_6/@dye_mask` / `7.2034766728666,-10.5836110456466,120.659605228384,-73.4332153265059` / 97 white paths | `M:hyacinth/stage_6.json` | `T:hyacinth/stage_6_base_texture.png`; `T:hyacinth/stage_6_dye_mask.png` | APPROVED BASE + MASK |
| 7 | `hyacinth/stage_7` | Yes / No | `hyacinth:7` | `hyacinth/stage_7/@base_texture` / `-3.40425531914298,0.19148936169586,128.46808510638,-131.680851063828` / 131.872 x 131.872 pt | `hyacinth/stage_7/@dye_mask` / `6.77335346718428,-15.3366163004584,121.285042437325,-73.6292110008726` / 45 white paths | `M:hyacinth/stage_7.json` | `T:hyacinth/stage_7_base_texture.png`; `T:hyacinth/stage_7_dye_mask.png` | APPROVED BASE + MASK |
| 8 | `lilly/stage_1` | Yes / No | `lily:1` | `lilly/stage_1/@base_texture` / `-2.37644020709013,-3.66056168564501,128.789627693794,-134.826629586529` / 131.166 x 131.166 pt | NONE AUTHORED - retain transparent placeholder | `M:lily/stage_1.json` | `T:lily/stage_1_base_texture.png`; `T:lily/stage_1_dye_mask.png` | APPROVED BASE; RETAIN MASK PLACEHOLDER |
| 9 | `lilly/stage_2` | Yes / No | `lily:2` | `lilly/stage_2/@base_texture` / `-2.37644020709013,-3.66056168564501,128.789627693794,-134.826629586529` / 131.166 x 131.166 pt | NONE AUTHORED - retain transparent placeholder | `M:lily/stage_2.json` | `T:lily/stage_2_base_texture.png`; `T:lily/stage_2_dye_mask.png` | APPROVED BASE; RETAIN MASK PLACEHOLDER |
| 10 | `lilly/stage_3` | Yes / No | `lily:3` | `lilly/stage_3/@base_texture` / `-2.37644020709013,-3.66056168564501,128.789627693794,-134.826629586529` / 131.166 x 131.166 pt | `lilly/stage_3/@dye_mask` / `1.95324171911852,-22.6843770095638,104.72305221991,-47.4270291452185` / 11 white paths | `M:lily/stage_3.json` | `T:lily/stage_3_base_texture.png`; `T:lily/stage_3_dye_mask.png` | APPROVED BASE + MASK |
| 11 | `lilly/stage_4` | Yes / No | `lily:4` | `lilly/stage_4/@base_texture` / `-2.37644020709013,-3.66056168564501,128.789627693794,-134.826629586529` / 131.166 x 131.166 pt | `lilly/stage_4/@dye_mask` / `1.49507894443559,-18.6842080920924,126.432545325064,-61.465196513549` / 33 white paths | `M:lily/stage_4.json` | `T:lily/stage_4_base_texture.png`; `T:lily/stage_4_dye_mask.png` | APPROVED BASE + MASK |
| 12 | `lilly/stage_5` | Yes / No | `lily:5` | `lilly/stage_5/@base_texture` / `-2.37644020709013,-3.66056168564501,128.789627693794,-134.826629586529` / 131.166 x 131.166 pt | `lilly/stage_5/@dye_mask` / `2.07992107201972,-10.6800883245896,125.684808118836,-70.4735560832996` / 37 white paths | `M:lily/stage_5.json` | `T:lily/stage_5_base_texture.png`; `T:lily/stage_5_dye_mask.png` | APPROVED BASE + MASK |
| 13 | `lilly/stage_6` | Yes / No | `lily:6` | `lilly/stage_6/@base_texture` / `-2.37644020709013,-3.66056168564501,128.789627693794,-134.826629586529` / 131.166 x 131.166 pt | `lilly/stage_6/@dye_mask` / `2.49808719288467,-15.1390532524738,126.360499346063,-59.4708845683163` / 26 white paths | `M:lily/stage_6.json` | `T:lily/stage_6_base_texture.png`; `T:lily/stage_6_dye_mask.png` | APPROVED BASE + MASK |
| 14 | `lilly/stage_7` | Yes / No | `lily:7` | `lilly/stage_7/@base_texture` / `-2.33010910764006,-3.79137890762013,128.835958793245,-134.957446808505` / 131.166 x 131.166 pt | `lilly/stage_7/@dye_mask` / `6.4774672712183,-39.1182170309839,116.636096709235,-54.680906486361` / 7 white paths | `M:lily/stage_7.json` | `T:lily/stage_7_base_texture.png`; `T:lily/stage_7_dye_mask.png` | APPROVED BASE + MASK |
| 15 | `campion/stage_1` | Yes / No | `campion:1` | `campion/stage_1/@base_texture` / `-2.75149562136176,-7.54483749810879,126.784804144481,-137.081137263951` / 129.536 x 129.536 pt | NONE AUTHORED - retain transparent placeholder | `M:campion/stage_1.json` | `T:campion/stage_1_base_texture.png`; `T:campion/stage_1_dye_mask.png` | APPROVED BASE; RETAIN MASK PLACEHOLDER |
| 16 | `campion/stage_2` | Yes / No | `campion:2` | `campion/stage_2/@base_texture` / `-2.75149562136176,-7.54483749810879,126.784804144481,-137.081137263951` / 129.536 x 129.536 pt | NONE AUTHORED - retain transparent placeholder | `M:campion/stage_2.json` | `T:campion/stage_2_base_texture.png`; `T:campion/stage_2_dye_mask.png` | APPROVED BASE; RETAIN MASK PLACEHOLDER |
| 17 | `campion/stage_3` | Yes / No | `campion:3` | `campion/stage_3/@base_texture` / `-4.20276374633522,0.8898876999574,125.333536019507,-128.646412065886` / 129.536 x 129.536 pt | `campion/stage_3/@dye_mask` / `7.57392359129426,-11.5728376552497,114.161137824529,-82.649178309448` / 30 white paths | `M:campion/stage_3.json` | `T:campion/stage_3_base_texture.png`; `T:campion/stage_3_dye_mask.png` | APPROVED BASE + MASK |
| 18 | `campion/stage_4` | Yes / No | `campion:4` | `campion/stage_4/@base_texture` / `-6.01144878295963,1.92342200660005,123.524850982883,-127.612877759242` / 129.536 x 129.536 pt | `campion/stage_4/@dye_mask` / `2.37794297173969,-9.97675918839286,113.095305570805,-80.4094280980626` / 30 white paths | `M:campion/stage_4.json` | `T:campion/stage_4_base_texture.png`; `T:campion/stage_4_dye_mask.png` | APPROVED BASE + MASK |
| 19 | `campion/stage_5` | Yes / No | `campion:5` | `campion/stage_5/@base_texture` / `-3.69163495315206,27.4255319148997,133.510638297888,-164.829787234041` / 137.202 x 192.255 pt | `campion/stage_5/@dye_mask` / `2.49863684762477,-5.95586802853722,126.774248427288,-72.2414023596648` / 52 white paths | `M:campion/stage_5.json` | `T:campion/stage_5_base_texture.png`; `T:campion/stage_5_dye_mask.png` | APPROVED BASE + MASK |
| 20 | `campion/stage_6` | Yes / No | `campion:6` | `campion/stage_6/@base_texture` / `-7.04498308960137,1.14827127661829,122.491316676242,-128.388028489224` / 129.536 x 129.536 pt | `campion/stage_6/@dye_mask` / `5.22016231500675,-6.31164547870321,111.471443291288,-86.352250361253` / 34 white paths | `M:campion/stage_6.json` | `T:campion/stage_6_base_texture.png`; `T:campion/stage_6_dye_mask.png` | APPROVED BASE + MASK |
| 21 | `campion/stage_7` | Yes / No | `campion:7` | `campion/stage_7/@base_texture` / `-6.6574077246114,1.27746306494919,122.878892041232,-128.258836700894` / 129.536 x 129.536 pt | NONE AUTHORED - retain transparent placeholder | `M:campion/stage_7.json` | `T:campion/stage_7_base_texture.png`; `T:campion/stage_7_dye_mask.png` | APPROVED BASE; RETAIN MASK PLACEHOLDER |
| 22 | `poppy/stage1` | Yes / No | `poppy:1` | `poppy/stage1/@base_texture` / `-3.63829787236227,3.9361702127735,132.170212765963,-131.872340425552` / 135.809 x 135.809 pt | NONE AUTHORED - retain transparent placeholder | `M:poppy/stage_1.json` | `T:poppy/stage_1_base_texture.png`; `T:poppy/stage_1_dye_mask.png` | APPROVED BASE; RETAIN MASK PLACEHOLDER |
| 23 | `poppy/stage2` | Yes / No | `poppy:2` | `poppy/stage2/@base_texture` / `-3.63829787236227,3.9361702127735,132.170212765963,-131.872340425552` / 135.809 x 135.809 pt | NONE AUTHORED - retain transparent placeholder | `M:poppy/stage_2.json` | `T:poppy/stage_2_base_texture.png`; `T:poppy/stage_2_dye_mask.png` | APPROVED BASE; RETAIN MASK PLACEHOLDER |
| 24 | `poppy/stage3` | Yes / No | `poppy:3` | `poppy/stage3/@base_texture` / `-3.63829787236227,3.9361702127735,132.170212765963,-131.872340425552` / 135.809 x 135.809 pt | `poppy/stage3/@dye_mask` / `21.21498654269,-23.1971148480852,127.604297822549,-61.0841859650927` / 7 white paths | `M:poppy/stage_3.json` | `T:poppy/stage_3_base_texture.png`; `T:poppy/stage_3_dye_mask.png` | APPROVED BASE + MASK |
| 25 | `poppy/stage4` | Yes / No | `poppy:4` | `poppy/stage4/@base_texture` / `-3.63829787236227,3.9361702127735,132.170212765963,-131.872340425552` / 135.809 x 135.809 pt | `poppy/stage4/@dye_mask` / `12.1247619428614,-29.4821897638012,116.192710501215,-53.0987237455647` / 7 white paths | `M:poppy/stage_4.json` | `T:poppy/stage_4_base_texture.png`; `T:poppy/stage_4_dye_mask.png` | APPROVED BASE + MASK |
| 26 | `poppy/stage5` | Yes / No | `poppy:5` | `poppy/stage5/@base_texture` / `-3.63829787236227,3.9361702127735,132.170212765963,-131.872340425552` / 135.809 x 135.809 pt | `poppy/stage5/@dye_mask` / `3.85605345397562,-9.74047984665322,126.709547274819,-40.5574347332295` / 64 white paths | `M:poppy/stage_5.json` | `T:poppy/stage_5_base_texture.png`; `T:poppy/stage_5_dye_mask.png` | APPROVED BASE + MASK |
| 27 | `poppy/stage6` | Yes / No | `poppy:6` | `poppy/stage6/@base_texture` / `-3.63829787236227,3.9361702127735,132.170212765963,-131.872340425552` / 135.809 x 135.809 pt | `poppy/stage6/@dye_mask` / `1.10854398153242,-19.1145301643319,120.747488965599,-47.6784822271438` / 3 white paths | `M:poppy/stage_6.json` | `T:poppy/stage_6_base_texture.png`; `T:poppy/stage_6_dye_mask.png` | APPROVED BASE + MASK |
| 28 | `poppy/stage_7` | Yes / No | `poppy:7` | `poppy/stage_7/@base_texture` / `-3.63829787236227,3.9361702127735,132.170212765963,-131.872340425552` / 135.809 x 135.809 pt | NONE AUTHORED - retain transparent placeholder | `M:poppy/stage_7.json` | `T:poppy/stage_7_base_texture.png`; `T:poppy/stage_7_dye_mask.png` | APPROVED BASE; RETAIN MASK PLACEHOLDER |
| 29 | `orfluer/stage_1` | Yes / No | `orfluer:1` | `orfluer/stage_1/@base_texture` / `19.7738396950426,-19.1664547992987,111.723404255328,-111.116019359584` / 91.950 x 91.950 pt | NONE AUTHORED - retain transparent placeholder | `M:orfluer/stage_1.json` | `T:orfluer/stage_1_base_texture.png`; `T:orfluer/stage_1_dye_mask.png` | APPROVED BASE; RETAIN MASK PLACEHOLDER |
| 30 | `orfluer/stage_2` | Yes / No | `orfluer:2` | `orfluer/stage_2/@base_texture` / `19.7738396950426,-19.1664547992987,111.723404255328,-111.116019359584` / 91.950 x 91.950 pt | NONE AUTHORED - retain transparent placeholder | `M:orfluer/stage_2.json` | `T:orfluer/stage_2_base_texture.png`; `T:orfluer/stage_2_dye_mask.png` | APPROVED BASE; RETAIN MASK PLACEHOLDER |
| 31 | `orfluer/stage_3` | Yes / No | `orfluer:3` | `orfluer/stage_3/@base_texture` / `19.7738396950426,-19.1664547992987,111.723404255328,-111.116019359584` / 91.950 x 91.950 pt | `orfluer/stage_3/@dye_mask` / `58.01880405458,-29.050449023468,74.9907211369118,-47.9464178110866` / 5 white paths | `M:orfluer/stage_3.json` | `T:orfluer/stage_3_base_texture.png`; `T:orfluer/stage_3_dye_mask.png` | APPROVED BASE + MASK |
| 32 | `orfluer/stage_4` | Yes / No | `orfluer:4` | `orfluer/stage_4/@base_texture` / `19.7738396950426,3.82093634077228,111.723404255328,-134.103410499656` / 91.950 x 137.924 pt | `orfluer/stage_4/@dye_mask` / `27.2176296857469,-6.20064081232158,104.328928316645,-55.7170410004637` / 7 white paths | `M:orfluer/stage_4.json` | `T:orfluer/stage_4_base_texture.png`; `T:orfluer/stage_4_dye_mask.png` | APPROVED BASE + MASK |
| 33 | `orfluer/stage_5` | Yes / No | `orfluer:5` | `orfluer/stage_5/@base_texture` / `19.7738396950426,-19.1664547992987,111.723404255328,-111.116019359584` / 91.950 x 91.950 pt | `orfluer/stage_5/@dye_mask` / `33.5056853950973,-22.9047372333025,100.451790009141,-73.9184057783932` / 79 white paths | `M:orfluer/stage_5.json` | `T:orfluer/stage_5_base_texture.png`; `T:orfluer/stage_5_dye_mask.png` | APPROVED BASE + MASK |
| 34 | `orfluer/stage_6` | Yes / No | `orfluer:6` | `orfluer/stage_6/@base_texture` / `19.7738396950426,-19.1664547992987,111.723404255328,-111.116019359584` / 91.950 x 91.950 pt | `orfluer/stage_6/@dye_mask` / `31.2147213749813,-19.2940817575754,102.176861215446,-78.4843232568273` / 8 white paths | `M:orfluer/stage_6.json` | `T:orfluer/stage_6_base_texture.png`; `T:orfluer/stage_6_dye_mask.png` | APPROVED BASE + MASK |
| 35 | `orfluer/stage_7` | Yes / No | `orfluer:7` | `orfluer/stage_7/@base_texture` / `19.7738396950426,3.82093634077228,111.723404255328,-134.103410499656` / 91.950 x 137.924 pt | `orfluer/stage_7/@dye_mask` / `22.7991058758462,-1.24952844655945,110.213760534853,-67.8064664475296` / 24 white paths | `M:orfluer/stage_7.json` | `T:orfluer/stage_7_base_texture.png`; `T:orfluer/stage_7_dye_mask.png` | APPROVED BASE + MASK |
| 36 | `foxglove/stage_1` | Yes / No | `foxglove:1` | `foxglove/stage_1/@base_texture` / `-1.50576270665897,3.07384344869479,126.532230244062,-124.964149502026` / 128.038 x 128.038 pt | NONE AUTHORED - retain transparent placeholder | `M:foxglove/stage_1.json` | `T:foxglove/stage_1_base_texture.png`; `T:foxglove/stage_1_dye_mask.png` | APPROVED BASE; RETAIN MASK PLACEHOLDER |
| 37 | `foxglove/stage_2` | Yes / No | `foxglove:2` | `foxglove/stage_2/@base_texture` / `0.70668790035143,-0.48181098337591,128.744680851072,-128.519803934096` / 128.038 x 128.038 pt | `foxglove/stage_2/@dye_mask` / `15.4730471982202,-36.2635311748663,117.167828536174,-67.291628749319` / 8 white paths | `M:foxglove/stage_2.json` | `T:foxglove/stage_2_base_texture.png`; `T:foxglove/stage_2_dye_mask.png` | APPROVED BASE + MASK |
| 38 | `foxglove/stage_3` | Yes / No | `foxglove:3` | `foxglove/stage_3/@base_texture` / `-1.40032029481517,-0.86490338249587,126.637672655905,-128.902896333217` / 128.038 x 128.038 pt | `foxglove/stage_3/@dye_mask` / `17.8771243122774,-10.5057699507452,108.795118389702,-64.4285686810672` / 8 white paths | `M:foxglove/stage_3.json` | `T:foxglove/stage_3_base_texture.png`; `T:foxglove/stage_3_dye_mask.png` | APPROVED BASE + MASK |
| 39 | `foxglove/stage_4` | Yes / No | `foxglove:4` | `foxglove/stage_4/@base_texture` / `-1.40032029481517,-0.86490338249587,126.637672655905,-128.902896333217` / 128.038 x 128.038 pt | `foxglove/stage_4/@dye_mask` / `9.86244579512731,-17.8400508248042,114.827323329335,-77.219164056829` / 77 white paths | `M:foxglove/stage_4.json` | `T:foxglove/stage_4_base_texture.png`; `T:foxglove/stage_4_dye_mask.png` | APPROVED BASE + MASK |
| 40 | `foxglove/stage_5` | Yes / No | `foxglove:5` | `foxglove/stage_5/@base_texture` / `-1.40032029481517,-0.86490338249587,126.637672655905,-128.902896333217` / 128.038 x 128.038 pt | `foxglove/stage_5/@dye_mask` / `9.39610047042152,-7.58418867613727,118.118009527436,-82.3182228146743` / 120 white paths | `M:foxglove/stage_5.json` | `T:foxglove/stage_5_base_texture.png`; `T:foxglove/stage_5_dye_mask.png` | APPROVED BASE + MASK |
| 41 | `foxglove/stage_6` | Yes / No | `foxglove:6` | `foxglove/stage_6/@base_texture` / `-1.50576270665897,3.07384344869479,126.532230244062,-124.964149502026` / 128.038 x 128.038 pt | `foxglove/stage_6/@dye_mask` / `10.3651806781963,-21.7231576824652,114.293350655205,-56.1397893027515` / 48 white paths | `M:foxglove/stage_6.json` | `T:foxglove/stage_6_base_texture.png`; `T:foxglove/stage_6_dye_mask.png` | APPROVED BASE + MASK |
| 42 | `foxglove/stage_7` | Yes / No | `foxglove:7` | `foxglove/stage_7/@base_texture` / `-1.40032029481517,-0.86490338249587,126.637672655905,-128.902896333217` / 128.038 x 128.038 pt | NONE AUTHORED - retain transparent placeholder | `M:foxglove/stage_7.json` | `T:foxglove/stage_7_base_texture.png`; `T:foxglove/stage_7_dye_mask.png` | APPROVED BASE; RETAIN MASK PLACEHOLDER |
| 43 | `snowdrop/stage_1` | Yes / No | `snowdrop:1` | `snowdrop/stage_1/@base_texture` / `-2.62839629182781,3.27692561436925,126.947592592433,-126.299063269891` / 129.576 x 129.576 pt | NONE AUTHORED - retain transparent placeholder | `M:snowdrop/stage_1.json` | `T:snowdrop/stage_1_base_texture.png`; `T:snowdrop/stage_1_dye_mask.png` | APPROVED BASE; RETAIN MASK PLACEHOLDER |
| 44 | `snowdrop/stage_2` | Yes / No | `snowdrop:2` | `snowdrop/stage_2/@base_texture` / `-2.62839629182781,3.27692561436925,126.947592592433,-126.299063269891` / 129.576 x 129.576 pt | NONE AUTHORED - retain transparent placeholder | `M:snowdrop/stage_2.json` | `T:snowdrop/stage_2_base_texture.png`; `T:snowdrop/stage_2_dye_mask.png` | APPROVED BASE; RETAIN MASK PLACEHOLDER |
| 45 | `snowdrop/stage_3` | Yes / No | `snowdrop:3` | `snowdrop/stage_3/@base_texture` / `-2.62839629182781,3.27692561436925,126.947592592433,-126.299063269891` / 129.576 x 129.576 pt | `snowdrop/stage_3/@dye_mask` / `18.6570317266733,-14.7002175110138,118.16173889878,-57.2363000908226` / 3 white paths | `M:snowdrop/stage_3.json` | `T:snowdrop/stage_3_base_texture.png`; `T:snowdrop/stage_3_dye_mask.png` | APPROVED BASE + MASK |
| 46 | `snowdrop/stage_4` | Yes / No | `snowdrop:4` | `snowdrop/stage_4/@base_texture` / `-2.62839629182781,3.27692561436925,126.947592592433,-126.299063269891` / 129.576 x 129.576 pt | `snowdrop/stage_4/@dye_mask` / `16.3024933730439,-40.1677934009067,123.48023215888,-64.1226522153456` / 12 white paths | `M:snowdrop/stage_4.json` | `T:snowdrop/stage_4_base_texture.png`; `T:snowdrop/stage_4_dye_mask.png` | APPROVED BASE + MASK |
| 47 | `snowdrop/stage_5` | Yes / No | `snowdrop:5` | `snowdrop/stage_5/@base_texture` / `-2.62839629182781,3.27692561436925,126.947592592433,-126.299063269891` / 129.576 x 129.576 pt | `snowdrop/stage_5/@dye_mask` / `12.2854411078897,-39.1151497543688,122.306701478594,-55.1733469645296` / 17 white paths | `M:snowdrop/stage_5.json` | `T:snowdrop/stage_5_base_texture.png`; `T:snowdrop/stage_5_dye_mask.png` | APPROVED BASE + MASK |
| 48 | `snowdrop/stage_6` | Yes / No | `snowdrop:6` | `snowdrop/stage_6/@base_texture` / `-2.62839629182781,3.27692561436925,126.947592592433,-126.299063269891` / 129.576 x 129.576 pt | `snowdrop/stage_6/@dye_mask` / `7.46492364958067,-36.8548011490448,123.590871047621,-54.3334261400669` / 4 white paths | `M:snowdrop/stage_6.json` | `T:snowdrop/stage_6_base_texture.png`; `T:snowdrop/stage_6_dye_mask.png` | APPROVED BASE + MASK |
| 49 | `snowdrop/stage_7` | Yes / No | `snowdrop:7` | `snowdrop/stage_7/@base_texture` / `-2.62839629182781,3.27692561436925,126.947592592433,-126.299063269891` / 129.576 x 129.576 pt | `snowdrop/stage_7/@dye_mask` / `7.05879712048682,-36.2655770015999,126.841094288919,-51.7757416295826` / 10 white paths | `M:snowdrop/stage_7.json` | `T:snowdrop/stage_7_base_texture.png`; `T:snowdrop/stage_7_dye_mask.png` | APPROVED BASE + MASK |

## Approved coverage

| Source container | Repository species | Authored white masks | Base-only placeholder stages |
|---|---|---|---|
| `hyacinth` | `hyacinth` | 3-7 | 1-2 |
| `lilly` | `lily` | 3-7 | 1-2 |
| `campion` | `campion` | 3-6 | 1-2, 7 |
| `poppy` | `poppy` | 3-6 | 1-2, 7 |
| `orfluer` | `orfluer` | 3-7 | 1-2 |
| `foxglove` | `foxglove` | 2-6 | 1, 7 |
| `snowdrop` | `snowdrop` | 3-7 | 1-2 |
| **Total** | **7 species** | **33** | **16** |

## Approved non-stage exclusions

| Source object | State | Role | Decision |
|---|---|---|---|
| `snowdrop/@back` | hidden embedded RasterItem | NON_EXPORT | APPROVED |
| `snowdrop/@front` | hidden embedded RasterItem | NON_EXPORT | APPROVED |

## Pre-export flags resolved

| Prior flag | Refreshed evidence | Resolution |
|---|---|---|
| Yellow masks | all 33 mask fills are now `RGB(255,255,255)` | RESOLVED |
| Hidden Hyacinth 6/7 | both stage layers now visible | RESOLVED |
| Extreme Hyacinth 6/7 scale | bases now approximately 131.872 x 131.872 pt | RESOLVED |
| `lilly` spelling | owner approved repository alias `lily` | RESOLVED |
| Orfluer identity | source container now exactly `orfluer` | RESOLVED |
| Non-square/out-of-artboard objects | owner approved 128 x 128 Artboard 1 crop | RESOLVED FOR EXPORT |
| Missing source masks | owner approved transparent placeholder retention | RESOLVED |
| Snowdrop extra rasters | owner approved NON_EXPORT | RESOLVED |
| Linked assets | 0 PlacedItems; all bases embedded | CLEAR |
| Duplicate candidates | exactly one base and at most one mask per stage | CLEAR |

## Decision register

| ID | Approved decision | Status |
|---|---|---|
| ILLUSTRATOR-001 | external authoritative source; current SHA-256 `1B540785...A9EE` | APPROVED |
| ILLUSTRATOR-002 | exact species/stage mapping, including `lilly -> lily` | APPROVED |
| ILLUSTRATOR-003 | 33 paired and 16 base-only mappings | APPROVED |
| ILLUSTRATOR-004 | native Illustrator scripting | APPROVED |
| ILLUSTRATOR-005 | Artboard 1 crop | APPROVED |
| ILLUSTRATOR-006 | 128 x 128 PNG | APPROVED |
| ILLUSTRATOR-007 | sRGB | APPROVED |
| ILLUSTRATOR-008 | straight alpha | APPROVED |
| ILLUSTRATOR-009 | Art Optimized, no post-export rescale | APPROVED |
| ILLUSTRATOR-010 | all stage layers visible/unlocked; Snowdrop extras excluded | APPROVED |
| ILLUSTRATOR-011 | flat-white masks | APPROVED AND VERIFIED |
| ILLUSTRATOR-012 | transparent mask placeholders for base-only stages | APPROVED |
| ILLUSTRATOR-013 | no extra stages | APPROVED |
| ILLUSTRATOR-014 | source remains external and authoritative; owner-authorized alpha correction completed | APPROVED AND COMPLETED |
| ILLUSTRATOR-015 | alpha-disjoint bases with hidden originals | SUPERSEDED BY ILLUSTRATOR-016 |
| ILLUSTRATOR-016 | detailed originals restored as export bases; former alpha-disjoint rasters hidden; masks blended at 50% in runtime | APPROVED BY OWNER REQUEST AND VERIFIED |

## Milestone 19 gate state

All 49 base rows were approved. Thirty-three mask rows were approved for export. Sixteen mask destinations were approved for placeholder retention. Source structure, white-mask policy, Artboard 1 crop, dimensions, numeric sRGB values, alpha, anti-aliasing, canonical targets, exclusions, and source retention passed the pre-export gate. The post-M20 owner request supersedes only the alpha-disjoint base rule; species/stage mappings, masks, dimensions, crop, and exclusions remain unchanged.

The Milestone 19 export and technical integration are complete. The current ledger below includes the later owner-authorized detail restoration.

## Current export ledger

Source-authoritative detail restoration and native re-export are complete. The source is SHA-256 `1B5407855D448B8385CC6D067858A347DF3C0B0993B390382A36EE1D42C0A9EE`. Post-save export found 49 named detailed bases, 33 white masks, and 33 hidden alpha-disjoint archive rasters.

For all 33 authored-mask stages, `@base_texture` is the detailed original and `@base_texture_alpha_disjoint_nonexport` is hidden and excluded. Base-only stages continue to use their approved original source base. All rows below passed 128 x 128 export geometry, transparent padding, nonempty base, exact-white visible mask colour where authored, and detailed base overlap on every authored-mask stage.

| # | Source stage | Repository target | Base output SHA-256 | Mask output SHA-256 | Mask disposition | Visible base / mask pixels | Export status |
|---:|---|---|---|---|---|---:|---|
| 1 | `hyacinth/stage_1` | `src/main/resources/assets/britannia_mod/textures/block/flowers/hyacinth/stage_1_{base_texture,dye_mask}.png` | `261541E0EC17D8D4043220AD3D62E764AD1F0018392C6B014CAC4AE762F32A0C` | `06AB142C3BAD172A564F50931DD667C07AF918ED7298107B0C41F79CD761CC8F` | Transparent placeholder retained | 2397 / 0 | EXPORTED AND VALIDATED |
| 2 | `hyacinth/stage_2` | `src/main/resources/assets/britannia_mod/textures/block/flowers/hyacinth/stage_2_{base_texture,dye_mask}.png` | `1F4EB0425372B1FD5E516A03C48036D90156BC869857561A10D78156427A1707` | `06AB142C3BAD172A564F50931DD667C07AF918ED7298107B0C41F79CD761CC8F` | Transparent placeholder retained | 5100 / 0 | EXPORTED AND VALIDATED |
| 3 | `hyacinth/stage_3` | `src/main/resources/assets/britannia_mod/textures/block/flowers/hyacinth/stage_3_{base_texture,dye_mask}.png` | `693ECC07B5D612DC0ABAE002A42463FDBCD28F62A56241BA66858F52BDF8A2A0` | `4FA31EFE26B2A899499E7EEA858ED51C4B80E76091DEB6D5593945CBBABEDE17` | Illustrator white mask exported | 6037 / 2287 | EXPORTED AND VALIDATED |
| 4 | `hyacinth/stage_4` | `src/main/resources/assets/britannia_mod/textures/block/flowers/hyacinth/stage_4_{base_texture,dye_mask}.png` | `1081DC41C5DF7370EC7A9B102DFC88938E80C6684C92ABE732FCF84271ADCCB5` | `6F3DB6218799AC61CCF5C4130CA954C204137E6C3E7FBA11DF635FDF5CD38208` | Illustrator white mask exported | 7155 / 3888 | EXPORTED AND VALIDATED |
| 5 | `hyacinth/stage_5` | `src/main/resources/assets/britannia_mod/textures/block/flowers/hyacinth/stage_5_{base_texture,dye_mask}.png` | `0E3E64DD89D398A7118895F9438CBD52A3CFF5FBAA14F1C4D6D02D8783315817` | `BC456DA3605C7132599FDFC8B7F63D76D293BB40806E79F7A8F3CD13DA84FF08` | Illustrator white mask exported | 8638 / 5070 | EXPORTED AND VALIDATED |
| 6 | `hyacinth/stage_6` | `src/main/resources/assets/britannia_mod/textures/block/flowers/hyacinth/stage_6_{base_texture,dye_mask}.png` | `81876C0F4D0967942707B6F826CD550297B3D02B19717272E5E3443A3A3412DA` | `B7263A170774E5D433F79EB2F323A11660340F3809A3E03F3E7624D101C801B6` | Illustrator white mask exported | 6519 / 3590 | EXPORTED AND VALIDATED |
| 7 | `hyacinth/stage_7` | `src/main/resources/assets/britannia_mod/textures/block/flowers/hyacinth/stage_7_{base_texture,dye_mask}.png` | `B8FF77A6A7BE66AFD92293B060BD7389BA56A5AE23975F722EDEF922DC98BBE2` | `D27240DD61AE06BC8C99620BEAF4E1BDB3649B4114E21EE46322A1618AE698D8` | Illustrator white mask exported | 6012 / 2822 | EXPORTED AND VALIDATED |
| 8 | `lilly/stage_1` | `src/main/resources/assets/britannia_mod/textures/block/flowers/lily/stage_1_{base_texture,dye_mask}.png` | `A8E417D726FBDFA30E0702B3B3FC23D53D1A44AA88186FBF61F54FF017A9659F` | `06AB142C3BAD172A564F50931DD667C07AF918ED7298107B0C41F79CD761CC8F` | Transparent placeholder retained | 2430 / 0 | EXPORTED AND VALIDATED |
| 9 | `lilly/stage_2` | `src/main/resources/assets/britannia_mod/textures/block/flowers/lily/stage_2_{base_texture,dye_mask}.png` | `0C386B8F5DFFCC1DF4F69AD9903161F467DC9A4D098E7B1FCF08B005F702B2CC` | `06AB142C3BAD172A564F50931DD667C07AF918ED7298107B0C41F79CD761CC8F` | Transparent placeholder retained | 4156 / 0 | EXPORTED AND VALIDATED |
| 10 | `lilly/stage_3` | `src/main/resources/assets/britannia_mod/textures/block/flowers/lily/stage_3_{base_texture,dye_mask}.png` | `37BB2BC8A60B485BDE00FB9D32EBD609E85CD44B8303E14D0AA7433CE362F9A8` | `1163EFB25FB8EDD330FE0234693A4A5D8021E4944EACA2F4D6C0EB429661BC33` | Illustrator white mask exported | 6452 / 752 | EXPORTED AND VALIDATED |
| 11 | `lilly/stage_4` | `src/main/resources/assets/britannia_mod/textures/block/flowers/lily/stage_4_{base_texture,dye_mask}.png` | `919F66948A64015999FC504BAB3C06E05447E90AFEBCAF9AFD442096CC971689` | `F18D3F059226B0250116B8A3416186A0FF575B9261F04D7946B1AD48B45465D9` | Illustrator white mask exported | 8271 / 2110 | EXPORTED AND VALIDATED |
| 12 | `lilly/stage_5` | `src/main/resources/assets/britannia_mod/textures/block/flowers/lily/stage_5_{base_texture,dye_mask}.png` | `18D3E2B9D9DDC208E72D154781FB9CF01722A953659889C9EB85A17C8D095DDA` | `B2EB5D4423962E62711579A0807806E37E5BC1A476F5C24B54EBB2DFBA30C029` | Illustrator white mask exported | 8774 / 3205 | EXPORTED AND VALIDATED |
| 13 | `lilly/stage_6` | `src/main/resources/assets/britannia_mod/textures/block/flowers/lily/stage_6_{base_texture,dye_mask}.png` | `DB6289B68FB72D9DB09C23AE06CA8D65B52EE1B32EF26563F42D8DC313284D91` | `F2FEF4375DF98C2B5E09AF067C6FB13DA3B6E856D3BCA98C6BCC4695D09B2C84` | Illustrator white mask exported | 6885 / 2199 | EXPORTED AND VALIDATED |
| 14 | `lilly/stage_7` | `src/main/resources/assets/britannia_mod/textures/block/flowers/lily/stage_7_{base_texture,dye_mask}.png` | `7E382F7FC5E68015712A28D9538CFFCE2465CB8EAFFE7B7F359DE80A092EEA6E` | `F65D16BD8FBCBEEF5F9DF7AC4D21F9991F012CB6D03FE86F81F18918CFACA3A2` | Illustrator white mask exported | 4819 / 524 | EXPORTED AND VALIDATED |
| 15 | `campion/stage_1` | `src/main/resources/assets/britannia_mod/textures/block/flowers/campion/stage_1_{base_texture,dye_mask}.png` | `9F83AF134A634B1045AF795DC74FEC493BF9239962DB447B64502E48195D0564` | `06AB142C3BAD172A564F50931DD667C07AF918ED7298107B0C41F79CD761CC8F` | Transparent placeholder retained | 1243 / 0 | EXPORTED AND VALIDATED |
| 16 | `campion/stage_2` | `src/main/resources/assets/britannia_mod/textures/block/flowers/campion/stage_2_{base_texture,dye_mask}.png` | `FA75F77769B96D78F6168BB0EF9FCE18D82F88E96FF93D7C428CD185A82A89E0` | `06AB142C3BAD172A564F50931DD667C07AF918ED7298107B0C41F79CD761CC8F` | Transparent placeholder retained | 2543 / 0 | EXPORTED AND VALIDATED |
| 17 | `campion/stage_3` | `src/main/resources/assets/britannia_mod/textures/block/flowers/campion/stage_3_{base_texture,dye_mask}.png` | `52652AA38235FAEB3098E4380E8435DD62997F742611AA5E55548A827F1700EC` | `50B5C24452CD50D34B7A59AD0C6A9226D031A8B56B43F2828DF6A7AC8BA39881` | Illustrator white mask exported | 2956 / 904 | EXPORTED AND VALIDATED |
| 18 | `campion/stage_4` | `src/main/resources/assets/britannia_mod/textures/block/flowers/campion/stage_4_{base_texture,dye_mask}.png` | `9A054E3215E963950B330217441F91243A340CDAF68187CDFF923D6BFD9374AF` | `C80C06F10688BBDDFE649B5EEED63DD81CC8B03EEE8A30F0C86B69D5930EAE76` | Illustrator white mask exported | 3265 / 960 | EXPORTED AND VALIDATED |
| 19 | `campion/stage_5` | `src/main/resources/assets/britannia_mod/textures/block/flowers/campion/stage_5_{base_texture,dye_mask}.png` | `FAE6C4521646A842F82F86DA38078F0DDFCF3D011B5B4EF6A24D3FDC2617487B` | `A359F48EEE401CCF1A045F422E0BC70C795512F2A6BBF87755755FC681B2379B` | Illustrator white mask exported | 5821 / 3798 | EXPORTED AND VALIDATED |
| 20 | `campion/stage_6` | `src/main/resources/assets/britannia_mod/textures/block/flowers/campion/stage_6_{base_texture,dye_mask}.png` | `6EC1A903E16C6065793C49863845529780DD3D941AD12473E22477AB4ACE09A6` | `C926C9F2078817C02CA7BD87B0FDBB94C278311C3D0902C7A4999CB9882AA611` | Illustrator white mask exported | 3392 / 1281 | EXPORTED AND VALIDATED |
| 21 | `campion/stage_7` | `src/main/resources/assets/britannia_mod/textures/block/flowers/campion/stage_7_{base_texture,dye_mask}.png` | `9C79B23C732B91F95E6CC7323117A3E8BD14CF15C691F1E68D723788EE2B0BD3` | `06AB142C3BAD172A564F50931DD667C07AF918ED7298107B0C41F79CD761CC8F` | Transparent placeholder retained | 3578 / 0 | EXPORTED AND VALIDATED |
| 22 | `poppy/stage1` | `src/main/resources/assets/britannia_mod/textures/block/flowers/poppy/stage_1_{base_texture,dye_mask}.png` | `011AEB53EC74832BB751C31D8DE668D0FA8005A11BB8EB58676433E42C9B3E33` | `06AB142C3BAD172A564F50931DD667C07AF918ED7298107B0C41F79CD761CC8F` | Transparent placeholder retained | 629 / 0 | EXPORTED AND VALIDATED |
| 23 | `poppy/stage2` | `src/main/resources/assets/britannia_mod/textures/block/flowers/poppy/stage_2_{base_texture,dye_mask}.png` | `BD1F50CA43969F5923A8E81B1507283A69F49D0BA7A1A43C856DD0D037E60E21` | `06AB142C3BAD172A564F50931DD667C07AF918ED7298107B0C41F79CD761CC8F` | Transparent placeholder retained | 2250 / 0 | EXPORTED AND VALIDATED |
| 24 | `poppy/stage3` | `src/main/resources/assets/britannia_mod/textures/block/flowers/poppy/stage_3_{base_texture,dye_mask}.png` | `0D072BDCC9C4FC9BDE57CF2C583B15A27E220FE7F71871052CAB1BE01ABC024A` | `826E181152EB13FCC12B06340A482D81347A6D9A077E02A97E78BEA452355BE3` | Illustrator white mask exported | 2547 / 454 | EXPORTED AND VALIDATED |
| 25 | `poppy/stage4` | `src/main/resources/assets/britannia_mod/textures/block/flowers/poppy/stage_4_{base_texture,dye_mask}.png` | `CECCFB0702AD12AFF8AE9401102543D3CBDA95DCE0F8F5D8D61D12F4E5454203` | `44A9B812AE953C29404CC1CAFE72A642C09D74A417337052FF99834E7B74C16D` | Illustrator white mask exported | 2182 / 1053 | EXPORTED AND VALIDATED |
| 26 | `poppy/stage5` | `src/main/resources/assets/britannia_mod/textures/block/flowers/poppy/stage_5_{base_texture,dye_mask}.png` | `CA30B5EF0E3A5A71C09E52805E339C959D9A24E33B42F5FFA79091C87969A096` | `D423621BD9A7269DBD5043972D4BD1AAFC754F454DA514618486E25803F0EE33` | Illustrator white mask exported | 4337 / 1858 | EXPORTED AND VALIDATED |
| 27 | `poppy/stage6` | `src/main/resources/assets/britannia_mod/textures/block/flowers/poppy/stage_6_{base_texture,dye_mask}.png` | `0477D4BC70DFA4D49FA6AEF7BECA1D8DF71A2A2232627F3694D339E42D3FFB4D` | `56D98B2FEEF0E446475699ED29B4A4B3E70FC2DA1446E5809029212C9F77F5ED` | Illustrator white mask exported | 4512 / 858 | EXPORTED AND VALIDATED |
| 28 | `poppy/stage_7` | `src/main/resources/assets/britannia_mod/textures/block/flowers/poppy/stage_7_{base_texture,dye_mask}.png` | `3E49EB01101BE26FEE1811A9C39878747B3D39740BFDAAF40419594443B55893` | `06AB142C3BAD172A564F50931DD667C07AF918ED7298107B0C41F79CD761CC8F` | Transparent placeholder retained | 3646 / 0 | EXPORTED AND VALIDATED |
| 29 | `orfluer/stage_1` | `src/main/resources/assets/britannia_mod/textures/block/flowers/orfluer/stage_1_{base_texture,dye_mask}.png` | `1E015D8132C4D9D75F628A52B67248F543F80DEF8B82F7720300422CC474934E` | `06AB142C3BAD172A564F50931DD667C07AF918ED7298107B0C41F79CD761CC8F` | Transparent placeholder retained | 1998 / 0 | EXPORTED AND VALIDATED |
| 30 | `orfluer/stage_2` | `src/main/resources/assets/britannia_mod/textures/block/flowers/orfluer/stage_2_{base_texture,dye_mask}.png` | `D33CEDE2BE86E24963E0312E62F4A7EBBDFAE0ED2428A6A923109632CF169B3F` | `06AB142C3BAD172A564F50931DD667C07AF918ED7298107B0C41F79CD761CC8F` | Transparent placeholder retained | 2841 / 0 | EXPORTED AND VALIDATED |
| 31 | `orfluer/stage_3` | `src/main/resources/assets/britannia_mod/textures/block/flowers/orfluer/stage_3_{base_texture,dye_mask}.png` | `EE17BAAD81784F7450FD8E7CE08CA365DF97F5EB6FAA94B79B03068604B266EB` | `ADD20470A965BF557DD7F89CEFD17B5595B08396A381D6C915F3442F40893121` | Illustrator white mask exported | 2508 / 248 | EXPORTED AND VALIDATED |
| 32 | `orfluer/stage_4` | `src/main/resources/assets/britannia_mod/textures/block/flowers/orfluer/stage_4_{base_texture,dye_mask}.png` | `A31D085CB1F1AFDBEACCF8A92BA70C9DC2AD9E23347BF4247B9EC926F91748E6` | `07CD80F2A625B99176C2A542A801232E57712C5480D008053906690B62E29BFC` | Illustrator white mask exported | 4581 / 2116 | EXPORTED AND VALIDATED |
| 33 | `orfluer/stage_5` | `src/main/resources/assets/britannia_mod/textures/block/flowers/orfluer/stage_5_{base_texture,dye_mask}.png` | `2414E182847AC7D2124A5AF7F0D21F3B17D802A6D6812EF54F62D3CFFDFEBD11` | `B61EDEFFC2B98A6AEAF3B116AC1A80F5C60647DCC73F0DFE27C2577BD4F14FAD` | Illustrator white mask exported | 2773 / 1976 | EXPORTED AND VALIDATED |
| 34 | `orfluer/stage_6` | `src/main/resources/assets/britannia_mod/textures/block/flowers/orfluer/stage_6_{base_texture,dye_mask}.png` | `0793B8EE31375BB2C67714D23C894E00F1C675839C54EA7378F16092A7653AC9` | `8F3BEAAE729241A64392AD759E9B88F6917A6909E6C973706E992104990D225A` | Illustrator white mask exported | 3297 / 2479 | EXPORTED AND VALIDATED |
| 35 | `orfluer/stage_7` | `src/main/resources/assets/britannia_mod/textures/block/flowers/orfluer/stage_7_{base_texture,dye_mask}.png` | `23CB3E200E19DAE71BDE890E762DDF79C5A9415B19FB0579795E50B236F76D83` | `E4E2F881459EDC099919F43C1F0C57B5859A5F49910251E53C6584CE66C13882` | Illustrator white mask exported | 5701 / 3449 | EXPORTED AND VALIDATED |
| 36 | `foxglove/stage_1` | `src/main/resources/assets/britannia_mod/textures/block/flowers/foxglove/stage_1_{base_texture,dye_mask}.png` | `D56C37D9CAAC6F995200F457159B54DDE673953C9083079587F26AABAA0490AA` | `06AB142C3BAD172A564F50931DD667C07AF918ED7298107B0C41F79CD761CC8F` | Transparent placeholder retained | 2357 / 0 | EXPORTED AND VALIDATED |
| 37 | `foxglove/stage_2` | `src/main/resources/assets/britannia_mod/textures/block/flowers/foxglove/stage_2_{base_texture,dye_mask}.png` | `58901E5E9C7C5E78CD64F6733FE14F51F8E021E2144C3C08FE07B99616374B86` | `8B65EF53EFA8F24F456B72F9F9C82388FE5FC3DB80EB0A7D5D656BDFAD953DCC` | Illustrator white mask exported | 2931 / 686 | EXPORTED AND VALIDATED |
| 38 | `foxglove/stage_3` | `src/main/resources/assets/britannia_mod/textures/block/flowers/foxglove/stage_3_{base_texture,dye_mask}.png` | `59045281A5B6BCFBEDC77FBB498E25E7EFD9772374D1E56BF67C4CA03E894A69` | `AF4C55E67CD48F4D3762BE39695CDC0768B2B6C27D097B4822B91DD3B36D185D` | Illustrator white mask exported | 4217 / 1016 | EXPORTED AND VALIDATED |
| 39 | `foxglove/stage_4` | `src/main/resources/assets/britannia_mod/textures/block/flowers/foxglove/stage_4_{base_texture,dye_mask}.png` | `DFDA2550109129CD36896A966E387DEBA823E17D2D97BCAC3652A67E0F5964C5` | `2D927E70F9E6E152A211067470B83FAE8C0EB2C0488975569CE17C77F961DE4F` | Illustrator white mask exported | 4299 / 1616 | EXPORTED AND VALIDATED |
| 40 | `foxglove/stage_5` | `src/main/resources/assets/britannia_mod/textures/block/flowers/foxglove/stage_5_{base_texture,dye_mask}.png` | `DDF20AC68815796471E4BCD14A4FF3E07EAA647A61E58F07B1FCBFEF50A8B0EE` | `7F58BCFA2EAC5715561A796D590F9342B257768B8FAF9CE423AA54F790ED6CDE` | Illustrator white mask exported | 6818 / 3346 | EXPORTED AND VALIDATED |
| 41 | `foxglove/stage_6` | `src/main/resources/assets/britannia_mod/textures/block/flowers/foxglove/stage_6_{base_texture,dye_mask}.png` | `B49F9928736021405B400F6EFB6E7B68928DC3B85FDA61FCAF146B4512AE2374` | `25A273E9314DBE2E732EF4807DE4F7A0C358C7883F99E7CF130F98DE269609D6` | Illustrator white mask exported | 5489 / 955 | EXPORTED AND VALIDATED |
| 42 | `foxglove/stage_7` | `src/main/resources/assets/britannia_mod/textures/block/flowers/foxglove/stage_7_{base_texture,dye_mask}.png` | `B3784F01EF3D267A8E6790D1937A2CAA6DCA3E7085D875EAD882928F8281565F` | `06AB142C3BAD172A564F50931DD667C07AF918ED7298107B0C41F79CD761CC8F` | Transparent placeholder retained | 4172 / 0 | EXPORTED AND VALIDATED |
| 43 | `snowdrop/stage_1` | `src/main/resources/assets/britannia_mod/textures/block/flowers/snowdrop/stage_1_{base_texture,dye_mask}.png` | `0570D1B2C5854360EB4D5E9CB810C37E2D583D7B0AA8922E1F5082D20D7EBAEF` | `06AB142C3BAD172A564F50931DD667C07AF918ED7298107B0C41F79CD761CC8F` | Transparent placeholder retained | 1673 / 0 | EXPORTED AND VALIDATED |
| 44 | `snowdrop/stage_2` | `src/main/resources/assets/britannia_mod/textures/block/flowers/snowdrop/stage_2_{base_texture,dye_mask}.png` | `C7E4C1E15121A918C9C7F6289B4DCE72BBEA3BF0B9A76BCFE2411A886F1750A9` | `06AB142C3BAD172A564F50931DD667C07AF918ED7298107B0C41F79CD761CC8F` | Transparent placeholder retained | 3313 / 0 | EXPORTED AND VALIDATED |
| 45 | `snowdrop/stage_3` | `src/main/resources/assets/britannia_mod/textures/block/flowers/snowdrop/stage_3_{base_texture,dye_mask}.png` | `C84F57694421710DEABBFD8F7D9F6508B2F22BA79C15CBCB9B06971B7259A919` | `33B19E51FF21139B21C9CD817524C60413EDD450FDD54A50C46D425FEC0EB627` | Illustrator white mask exported | 4386 / 1253 | EXPORTED AND VALIDATED |
| 46 | `snowdrop/stage_4` | `src/main/resources/assets/britannia_mod/textures/block/flowers/snowdrop/stage_4_{base_texture,dye_mask}.png` | `CACA89B39BE0D8677E9AC15AC9FF9248820C0701D3CE2BA3CBDE2F51FEAE717D` | `637A7000D4CFA3CA2F3B5E8E9408917B7E18E8014C365F2BD97D91C1B8971FD6` | Illustrator white mask exported | 4434 / 1148 | EXPORTED AND VALIDATED |
| 47 | `snowdrop/stage_5` | `src/main/resources/assets/britannia_mod/textures/block/flowers/snowdrop/stage_5_{base_texture,dye_mask}.png` | `685B1CD30EB460118D46FA70A493197E59321950A72216564FBD8E76EE9D99D4` | `3927DD6C1442010BE010971787B95672FC38929343641192B9992C95E2F0B4D2` | Illustrator white mask exported | 3246 / 793 | EXPORTED AND VALIDATED |
| 48 | `snowdrop/stage_6` | `src/main/resources/assets/britannia_mod/textures/block/flowers/snowdrop/stage_6_{base_texture,dye_mask}.png` | `ED12F43402A313BD64A69ECC806C4E1FA592048D30B39555BC2B9D9EB6DEB6C5` | `945169748BCBC3099F2F66AE2D4E5107B21F633D26A8CBE46EA420DF17DF1070` | Illustrator white mask exported | 3567 / 963 | EXPORTED AND VALIDATED |
| 49 | `snowdrop/stage_7` | `src/main/resources/assets/britannia_mod/textures/block/flowers/snowdrop/stage_7_{base_texture,dye_mask}.png` | `68BF03E42542A84934832D437889F40F87704A5B72CF52B51BFD3A655F8C717D` | `7B2B4D88DE37A1D143C6F8859B69A387ACEA1E640734B465694D8933E09BC7EE` | Illustrator white mask exported | 3481 / 962 | EXPORTED AND VALIDATED |

### Current export totals

- 49 base PNGs exported from named Illustrator `base_texture` items.
- 33 authored mask PNGs exported from named Illustrator `dye_mask` groups.
- 16 transparent mask placeholders retained according to the approved per-species matrix.
- 98 destination hashes verified byte-for-byte after integration.
- 85 tracked PNGs differ from the generated-placeholder baseline: 49 bases, 33 authored masks, and 3 corrected transparent stage-7 placeholders.
- 49 canonical model JSONs remain unchanged; no pass-specific models exist.
- Base/mask visible overlap: 58,516 pixels across the 33 authored-mask pairs, intentionally preserving base detail.
- Native re-export mismatch against current production: 0.
- Change relative to the M19 commit: 33 detailed bases changed; 16 base-only bases and all 33 authored masks remained byte-identical.

This ledger supersedes the pre-export proposal statuses above.

## Milestone 20 association and validation ledger

The 49 rows above remain the authoritative one-to-one species/stage associations. Milestone 20 re-enumerated the complete graph at commit `2e5ff48eba79c8f547ef1fe2379b1d89ec9edcc3`: 49 canonical model JSONs, 49 base PNGs, 49 mask PNGs, 0 pass-specific models, 0 duplicate mappings, 0 stale flower resources, and 0 orphan flower textures. Every model resolves its matching species/stage base and mask.

Milestone 20's alpha-disjoint export result is historical and was superseded by the owner's detail-preserving tint correction. Current Illustrator object-isolated export reproduces all 49 production bases and 33 authored masks byte-for-byte. All 98 production textures remain 128 x 128; authored masks contain only white visible RGB, all 16 approved placeholders are fully transparent, and the mask-bearing bases intentionally retain detailed visible pixels beneath their masks. Original-resolution and live front/side/elevated views found no one-pixel shift, crop defect beyond the approved 128 x 128 artboard boundary, UV mismatch, mirrored asset, seam, halo, or static Z-fighting.

Live persisted-state rows used the order Hyacinth, Lily, Campion, Poppy, Orfluer, Foxglove, Snowdrop with columns stages 1-7. Server-side identity and stage validation passed 49/49 on initial creation and 49/49 after clean save/restart; Poppy stage 7 was present and rendered. Milestone 20 result: **Pass; record changes uncommitted pending owner approval.**

## Post-Milestone 20 detail-preserving source ledger

- Current source SHA-256: `1B5407855D448B8385CC6D067858A347DF3C0B0993B390382A36EE1D42C0A9EE`.
- Native inventory/export: 49 named detailed bases, 33 flat-white masks, 33 hidden alpha-disjoint archive rasters, 49 base exports, and 33 mask exports.
- Production fidelity: 49/49 bases and 33/33 authored masks match the fresh source export; 33 bases changed from the M19 commit, 16 bases did not, and every mask stayed byte-identical.
- Pixel relationship: 58,516 visible mask pixels overlap visible detailed base pixels. Every authored-mask stage has multiple underlying base colours, preserving artwork variation for the 50% runtime tint blend.
- The table above now records the current production base hashes and visible-pixel counts. Mask hashes and the 16 placeholder dispositions are unchanged.

Detail-preserving source ledger status: **Current and validated; uncommitted.**
