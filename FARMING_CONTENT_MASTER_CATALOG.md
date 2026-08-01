# Farming Content Master Catalog

Milestone: 12 discovery
Repository: `C:/projects/britannia/mod/Britannia_Mod`
Branch/HEAD inspected: `Farming` / `a2c2e73e0a7500c8c5c2628f51292fae55f67428`
Status: **Discovery inventory; no progression requirement is proposed or approved here.**

## Scope and counting rules

The catalog contains every active definition accepted by the new farming implementation: 67 `CropRegistry` definitions and seven `FlowerRegistry` definitions, for **74 unique species**. Each species occurs once. The nine `FruitTreeRegistry` entries are structure definitions for nine already-counted `CropRegistry` species, not additional species. Five lookup/save aliases and the old grape-vine placement route are recorded as compatibility paths, not additional species. Decorative grave flowers are not plantable farming content and are excluded.

The primary category is a discovery taxonomy used only to make coverage auditable; the repository does not store a general botanical category field. Categories therefore must not be treated as balance decisions.

| Primary category | Count | Species |
|---|---:|---|
| Grains and pulses | 8 | Corn, Wheat, Rye, Barley, Oats, Mustard, Beans, Rice |
| Roots and alliums | 11 | Carrot, Potato, Vanilla Potato, Yellow Onion, Green Onion, Garlic, Turnips, Radish, Parsnip, Yam, Rutabaga |
| Annual gourds and melons | 5 | Squash, Pumpkin, Watermelon, Vanilla Pumpkin, Vanilla Melon |
| Leaf, stalk, and brassica crops | 8 | Cabbage, Lettuce, Snow Peas, Peas, Broccoli, Cauliflower, Rhubarb, Celery |
| Annual fruit crop | 1 | Pineapple |
| Trellis and vine perennials | 7 | Tomato, Hops, Bell Peppers, Cucumbers, Honeydew, Cantaloupe, Grapes |
| Other perennial | 1 | Banana |
| Orchard trees | 9 | Cherries, Apple, Pear, Peach, Lemon, Lime, Orange, Olive, Plum |
| Berries | 8 | Strawberry, Blueberry, Raspberry, Cranberry, Blackberry, Huckleberry, Mulberry, Elderberry |
| Fibres | 3 | Cotton, Flax, Hemp |
| Medicinal or magical | 3 | Ginseng, Mandrake, Nightshade |
| Mushrooms | 2 | Brown Mushroom, Red Mushroom |
| Special leaf crop | 1 | Tobacco |
| Persistent flowers | 7 | Poppy, Snowdrop, Lily, Foxglove, Campion, Hyacinth, Orfluer |
| **Total** | **74** | **67 crops + 7 flowers** |

## Field contract and shared facts

The table below records the per-species identity and every varying planting contract. These shared facts complete the fields without repeating identical text 74 times:

- `CropRegistry` is authoritative for crop ID, display name, seed supplier, harvest item, tier, current growth data, nutrient/hydration/climate/altitude profile, lifecycle, support, yield, renewal, and harvest tool. The evidence line in each row points to the exact complete declaration. Altitude is resolved by `minAltitude`/`maxAltitude` at `CropRegistry.java:268-321`; lifecycle/support/renewal/tool are derived at lines 324-442.
- All 60 custom non-grape crop materials are `CropSeedItem` instances registered in `ItemRegistry.java:581-640`. They plant only on the top face of a `FarmingBlock`, ultimately through `FarmingBlock.tryPlantSeed`.
- Six compatibility materials use vanilla item classes: Potato, Wheat Seeds, Brown Mushroom, Red Mushroom, Pumpkin Seeds, and Melon Seeds. The table marks them `vanilla`. They enter the custom farming path through `FarmingBlock.useItemOn`/`CropRegistry.bySeed`, but retain vanilla behavior elsewhere.
- Grapes use one `GrapeSeedsItem` registry ID with stack-held variety data. It has both a FarmingBlock path and an active fallback `ItemNameBlockItem`/`GrapeVineBlock` placement path.
- Flower seeds are ordinary `Item` instances. `FlowerRegistry` is the sole one-to-one seed/species/harvest mapping. All seven plant through `FlowerPlantingService`, persist a server-selected colour, and regrow from stage 1 after harvest.
- Current minimum Farming skill to **plant** is `none` for all 74 entries. Normal crops award the existing `PLANT` action after success; flower planting deliberately awards none. The FarmingBlock grape branch currently omits the award while the duplicate item route awards it.
- There are no active registered bulb, tuber-item, sapling, cutting, start, spore-item, rhizome, pit, kernel, graft, slip, or transplant planting classes. Potato/Yam are described botanically as tubers but use seed-named `CropSeedItem`s; mushrooms use the mushroom item itself.
- No farming species has a JSON recipe, farming loot-table acquisition entry, merchant/trade entry, or world-generation acquisition entry under `src/main/resources/data`. Successful crop harvest can return the definition's seed item according to its current registered seed-return contract; `CropSeedExtractor` also supplies renewal for Corn, Lime, Orange, Olive, grapes, and all harvested flowers. Creative inventory supplies the explicitly listed custom seeds/flowers, dynamic grape varieties, and Wheat Seeds. These are renewal/administrative paths, not a complete initial survival acquisition or economic story.

Legend: lifecycle `A` annual, `T` trellis perennial (requires trellis), `P` other perennial, `O` orchard/tree structure, `F` persistent flower. Growth is `tier / ticks / stages`; flowers have no crop tier. Yield is the current min-max result and renewal age where applicable. `H` is hand, `BH` bare hand, `S` scissors, `GB` grain blade, `RS` root shovel. `compat` means a vanilla item also has a non-custom route. `legacy` means another active route exists. Environment/nutrient details are intentionally source-linked instead of copied into a second balance table that could drift.

## Complete Markdown catalog

| # | Registry species / display | Primary category | Planting item (display) | Material/class | Lifecycle/support | Growth | Yield/renewal/tool | Environment profile | Current status and special handling | Evidence |
|---:|---|---|---|---|---|---|---|---|---|---|
| 1 | `squash` / Squash | Annual gourd | `britannia_mod:squash_seeds` (Squash Seeds) | seed / `CropSeedItem` | A / none | 1/5/8 | 1-3, S | H .65; Temperate/Tropical | active | `CropRegistry.java:55` |
| 2 | `carrot` / Carrot | Root | `britannia_mod:carrot_seeds` (Carrot Seeds) | seed / `CropSeedItem` | A / none | 1/5/8 | 3, H | H .55; Temperate/Ice | active; alias `carrots` | `CropRegistry.java:56` |
| 3 | `corn` / Corn | Grain | `britannia_mod:corn_seeds` (Corn Seeds) | seed / `CropSeedItem` | A / tall (4 blocks) | 2/7/8 | 2-5, S | H .60; Temperate | active; produce can extract seed | `CropRegistry.java:57` |
| 4 | `cabbage` / Cabbage | Leaf/brassica | `britannia_mod:cabbage_seeds` (Cabbage Seeds) | seed / `CropSeedItem` | A / none | 1/6/8 | 1-3, S | H .65; Temperate/Ice | active | `CropRegistry.java:58` |
| 5 | `lettuce` / Lettuce | Leaf | `britannia_mod:lettuce_seeds` (Lettuce Seeds) | seed / `CropSeedItem` | A / none | 1/4/8 | 1-3, S | H .80; Temperate/Ice | active | `CropRegistry.java:59` |
| 6 | `yellow_onion` / Yellow Onion | Allium | `britannia_mod:yellow_onion_seeds` (Yellow Onion Seeds) | seed / `CropSeedItem` | A / none | 1/5/8 | 1-3, S | H .45; Temperate/Ice | active | `CropRegistry.java:60` |
| 7 | `green_onion` / Green Onion | Allium | `britannia_mod:green_onion_seeds` (Green Onion Seeds) | seed / `CropSeedItem` | A / none | 1/4/8 | 1-3, S | H .55; Temperate/Ice | active | `CropRegistry.java:61` |
| 8 | `pumpkin` / Pumpkin | Annual gourd | `britannia_mod:pumpkin_seeds_custom` (Pumpkin Seeds) | seed / `CropSeedItem` | A / none | 1/7/8 | 1-2, S | H .70; Temperate/Tropical | active; distinct from vanilla pumpkin | `CropRegistry.java:62` |
| 9 | `potato` / Potato | Root/tuber | `britannia_mod:potato_seed` (Potato Seed) | seed / `CropSeedItem` | A / none | 1/5/8 | 4, RS | H .55; Temperate/Ice | active; distinct from vanilla potato | `CropRegistry.java:63` |
| 10 | `watermelon` / Watermelon | Annual melon | `britannia_mod:watermelon_seeds` (Watermelon Seeds) | seed / `CropSeedItem` | A / none | 1/7/8 | 1-2, S | H .85; Tropical/Temperate | active | `CropRegistry.java:64` |
| 11 | `vanilla_potato` / Vanilla Potato | Root/compatibility | `minecraft:potato` (Potato) | produce / vanilla `Item` | A / none | 1/5/4 | 1-3, H | H .60; Temperate | active compat; native vanilla route outside FarmingBlock | `CropRegistry.java:65` |
| 12 | `wheat` / Wheat | Grain | `minecraft:wheat_seeds` (Wheat Seeds) | seed / vanilla seed item | A / none | 1/5/8 | 6 + straw, GB | H .55; Temperate | active compat; native vanilla route outside FarmingBlock | `CropRegistry.java:66` |
| 13 | `rye` / Rye | Grain | `britannia_mod:rye_seeds` (Rye Seeds) | seed / `CropSeedItem` | A / none | 1/5/8 | 6 + straw, GB | H .45; Temperate/Ice | active | `CropRegistry.java:67` |
| 14 | `barley` / Barley | Grain | `britannia_mod:barley_seeds` (Barley Seeds) | seed / `CropSeedItem` | A / none | 1/5/8 | 6 + straw, GB | H .48; Temperate/Ice | active | `CropRegistry.java:68` |
| 15 | `oats` / Oats | Grain | `britannia_mod:oat_seeds` (Oat Seeds) | seed / `CropSeedItem` | A / none | 1/5/8 | 6 + straw, GB | H .62; Temperate/Ice | active | `CropRegistry.java:69` |
| 16 | `mustard` / Mustard | Grain/seed crop | `britannia_mod:mustard_seeds` (Mustard Seeds) | seed / `CropSeedItem` | A / none | 2/5/8 | 6 + straw, GB | H .50; Temperate/Ice | active; seed item is also harvest item | `CropRegistry.java:70` |
| 17 | `beans` / Beans | Pulse | `britannia_mod:bean_seeds` (Bean Seeds) | seed / `CropSeedItem` | A / none | 2/6/8 | 6 + straw, GB | H .60; Temperate/Tropical | active | `CropRegistry.java:71` |
| 18 | `rice` / Rice | Grain | `britannia_mod:rice_seeds` (Rice Seeds) | seed / `CropSeedItem` | A / none | 3/7/5 | 2-4, H | H .95; Wetland | active | `CropRegistry.java:72` |
| 19 | `tomato` / Tomato | Trellis perennial | `britannia_mod:tomato_seeds` (Tomato Seeds) | seed / `CropSeedItem` | T / trellis | 2/6/8 | 2-4; reset 4; S | H .72; Temperate/Tropical | active | `CropRegistry.java:73` |
| 20 | `garlic` / Garlic | Allium/root | `britannia_mod:garlic_seeds` (Garlic Seeds) | seed / `CropSeedItem` | A / none | 3/7/8 | 2-4, RS | H .45; Temperate/Ice | active | `CropRegistry.java:74` |
| 21 | `ginseng` / Ginseng | Medicinal | `britannia_mod:ginseng_seeds` (Ginseng Seeds) | seed / `CropSeedItem` | A / none | 4/9/8 | 1-2, S | H .55; Magical/Temperate | active | `CropRegistry.java:75` |
| 22 | `mandrake` / Mandrake | Magical/medicinal | `britannia_mod:mandrake_seeds` (Mandrake Seeds) | seed / `CropSeedItem` | A / none | 4/10/8 | 1-2, RS | H .55; Magical/Temperate/Ice | active; max-nutrient mode; aliases `mandrake_root` | `CropRegistry.java:76` |
| 23 | `nightshade` / Nightshade | Magical/medicinal | `britannia_mod:nightshade_seeds` (Nightshade Seeds) | seed / `CropSeedItem` | A / none | 4/9/8 | 1-2, GB | H .45; Magical/Underground | active; max-nutrient/darkness special handling | `CropRegistry.java:77` |
| 24 | `brown_mushroom` / Brown Mushroom | Mushroom | `minecraft:brown_mushroom` (Brown Mushroom) | whole mushroom / vanilla item | A / none | 2/6/4 | 1-3, H | H .75; Underground/Wetland | active compat; vanilla placement remains | `CropRegistry.java:78` |
| 25 | `red_mushroom` / Red Mushroom | Mushroom | `minecraft:red_mushroom` (Red Mushroom) | whole mushroom / vanilla item | A / none | 2/6/4 | 1-3, H | H .75; Underground/Wetland | active compat; vanilla placement remains | `CropRegistry.java:79` |
| 26 | `vanilla_pumpkin` / Vanilla Pumpkin | Gourd/compatibility | `minecraft:pumpkin_seeds` (Pumpkin Seeds) | seed / vanilla seed item | A / none | 1/6/4 | 1-2, H | H .65; Temperate | active compat; native vanilla route outside FarmingBlock | `CropRegistry.java:80` |
| 27 | `vanilla_melon` / Vanilla Melon | Melon/compatibility | `minecraft:melon_seeds` (Melon Seeds) | seed / vanilla seed item | A / none | 1/6/4 | 2-5, H | H .75; Temperate/Tropical | active compat; native vanilla route outside FarmingBlock | `CropRegistry.java:81` |
| 28 | `pineapple` / Pineapple | Annual fruit | `britannia_mod:pineapple_seeds` (Pineapple Seeds) | seed / `CropSeedItem` | A / none | 3/8/8 | 1, BH | H .60; Tropical/Temperate | active | `CropRegistry.java:82` |
| 29 | `strawberry` / Strawberry | Berry | `britannia_mod:strawberry_seeds` (Strawberry Seeds) | seed / `CropSeedItem` | A / none | 2/6/8 | 4, BH | H .65; Temperate | active; alias `strawberries` | `CropRegistry.java:83` |
| 30 | `blueberry` / Blueberry | Berry | `britannia_mod:blueberry_seeds` (Blueberry Seeds) | seed / `CropSeedItem` | A / none | 2/7/8 | 6, BH | H .72; Temperate/Ice | active; alias `blueberries` | `CropRegistry.java:84` |
| 31 | `raspberry` / Raspberry | Berry | `britannia_mod:raspberry_seeds` (Raspberry Seeds) | seed / `CropSeedItem` | A / none | 2/6/8 | 5, BH | H .62; Temperate/Ice | active | `CropRegistry.java:85` |
| 32 | `cranberry` / Cranberry | Berry | `britannia_mod:cranberry_seeds` (Cranberry Seeds) | seed / `CropSeedItem` | A / none | 2/7/8 | 6, BH | H .88; Wetland/Temperate | active | `CropRegistry.java:86` |
| 33 | `blackberry` / Blackberry | Berry | `britannia_mod:blackberry_seeds` (Blackberry Seeds) | seed / `CropSeedItem` | A / none | 2/6/8 | 5, BH | H .62; Temperate/Ice | active | `CropRegistry.java:87` |
| 34 | `huckleberry` / Huckleberry | Berry | `britannia_mod:huckleberry_seeds` (Huckleberry Seeds) | seed / `CropSeedItem` | A / none | 2/7/8 | 5, BH | H .58; Temperate/Ice | active | `CropRegistry.java:88` |
| 35 | `mulberry` / Mulberry | Berry | `britannia_mod:mulberry_seeds` (Mulberry Seeds) | seed / `CropSeedItem` | A / none | 2/6/8 | 5, BH | H .60; Temperate/Tropical | active | `CropRegistry.java:89` |
| 36 | `elderberry` / Elderberry | Berry | `britannia_mod:elderberry_seeds` (Elderberry Seeds) | seed / `CropSeedItem` | A / none | 2/6/8 | 5, BH | H .70; Temperate/Ice | active | `CropRegistry.java:90` |
| 37 | `cherries` / Cherries | Orchard | `britannia_mod:cherry_seeds` (Cherry Seeds) | seed / `CropSeedItem` | O / tree structure | 3/10/4 | 2-5; tree renewal; H | H .60; Temperate | active; `FruitTreeRegistry` overlay | `CropRegistry.java:91` |
| 38 | `cotton` / Cotton | Fibre | `britannia_mod:cotton_seeds` (Cotton Seeds) | seed / `CropSeedItem` | A / none | 2/8/8 | 2-4, S | H .50; Temperate/Tropical | active | `CropRegistry.java:92` |
| 39 | `flax` / Flax | Fibre | `britannia_mod:flax_seeds` (Flax Seeds) | seed / `CropSeedItem` | A / none | 2/6/8 | 2-4, GB | H .55; Temperate/Ice | active | `CropRegistry.java:93` |
| 40 | `hemp` / Hemp | Fibre | `britannia_mod:hemp_seeds` (Hemp Seeds) | seed / `CropSeedItem` | A / none | 2/6/8 | 2-4, S | H .55; Temperate/Tropical | active | `CropRegistry.java:94` |
| 41 | `hops` / Hops | Trellis perennial | `britannia_mod:hops_seeds` (Hops Seeds) | seed / `CropSeedItem` | T / trellis; 2-block visual | 3/7/8 | 2-4; reset 4; S | H .70; Temperate | active | `CropRegistry.java:95` |
| 42 | `snow_peas` / Snow Peas | Leaf/pulse | `britannia_mod:snow_pea_seeds` (Snow Pea Seeds) | seed / `CropSeedItem` | A / none | 2/5/8 | 2-4, S | H .60; Temperate/Ice | active; explicitly no trellis | `CropRegistry.java:96` |
| 43 | `peas` / Peas | Leaf/pulse | `britannia_mod:pea_seeds` (Pea Seeds) | seed / `CropSeedItem` | A / none | 2/6/4 | 2-4, H | H .60; Temperate | active; registry note is blank | `CropRegistry.java:97` |
| 44 | `turnips` / Turnips | Root | `britannia_mod:turnip_seeds` (Turnip Seeds) | seed / `CropSeedItem` | A / none | 1/5/8 | 1-3, S | H .55; Temperate/Ice | active | `CropRegistry.java:98` |
| 45 | `apple` / Apple | Orchard | `britannia_mod:apple_seeds` (Apple Seeds) | seed / `CropSeedItem` | O / tree structure | 3/10/4 | 2-5; tree renewal; H | H .60; Temperate | active; `FruitTreeRegistry` overlay | `CropRegistry.java:99` |
| 46 | `pear` / Pear | Orchard | `britannia_mod:pear_seeds` (Pear Seeds) | seed / `CropSeedItem` | O / tree structure | 3/10/4 | 2-5; tree renewal; H | H .65; Temperate | active; `FruitTreeRegistry` overlay | `CropRegistry.java:100` |
| 47 | `peach` / Peach | Orchard | `britannia_mod:peach_seeds` (Peach Seeds) | seed / `CropSeedItem` | O / tree structure | 3/10/4 | 2-5; tree renewal; H | H .65; Temperate | active; `FruitTreeRegistry` overlay | `CropRegistry.java:101` |
| 48 | `lemon` / Lemon | Orchard | `britannia_mod:lemon_seeds` (Lemon Seeds) | seed / `CropSeedItem` | O / tree structure | 3/10/4 | 2-5; tree renewal; H | H .55; Tropical | active; `FruitTreeRegistry` overlay | `CropRegistry.java:102` |
| 49 | `lime` / Lime | Orchard | `britannia_mod:lime_seeds` (Lime Seeds) | seed / `CropSeedItem` | O / tree structure | 3/10/4 | 2-5; tree renewal; H | H .70; Tropical | active; `FruitTreeRegistry` overlay; produce can extract seed | `CropRegistry.java:103` |
| 50 | `orange` / Orange | Orchard | `britannia_mod:orange_seeds` (Orange Seeds) | seed / `CropSeedItem` | O / tree structure | 3/10/4 | 2-5; tree renewal; H | H .60; Tropical/Temperate | active; `FruitTreeRegistry` overlay; produce can extract seed | `CropRegistry.java:104` |
| 51 | `olive` / Olive | Orchard | `britannia_mod:olive_seeds` (Olive Seeds) | seed / `CropSeedItem` | O / tree structure | 3/10/4 | 2-5; tree renewal; H | H .40; Arid/Temperate | active; `FruitTreeRegistry` overlay; produce can extract seed | `CropRegistry.java:105` |
| 52 | `plum` / Plum | Orchard | `britannia_mod:plum_seeds` (Plum Seeds) | seed / `CropSeedItem` | O / tree structure | 3/9/4 | 2-5; tree renewal; H | H .62; Temperate/Ice | active; `FruitTreeRegistry` overlay | `CropRegistry.java:106` |
| 53 | `bell_peppers` / Bell Peppers | Trellis perennial | `britannia_mod:bell_pepper_seeds` (Bell Pepper Seeds) | seed / `CropSeedItem` | T / trellis | 2/6/8 | 2-4; reset 4; S | H .62; Tropical/Temperate | active | `CropRegistry.java:107` |
| 54 | `cucumbers` / Cucumbers | Trellis perennial | `britannia_mod:cucumber_seeds` (Cucumber Seeds) | seed / `CropSeedItem` | T / trellis | 2/6/8 | 2-4; reset 4; S | H .82; Temperate/Tropical | active | `CropRegistry.java:108` |
| 55 | `honeydew` / Honeydew | Trellis perennial | `britannia_mod:honeydew_seeds` (Honeydew Seeds) | seed / `CropSeedItem` | T / trellis | 2/7/8 | 1-3; reset 4; S | H .86; Tropical/Temperate | active | `CropRegistry.java:109` |
| 56 | `cantaloupe` / Cantaloupe | Trellis perennial | `britannia_mod:cantaloupe_seeds` (Cantaloupe Seeds) | seed / `CropSeedItem` | T / trellis | 2/7/8 | 1-3; reset 4; S | H .74; Tropical/Temperate/Arid | active | `CropRegistry.java:110` |
| 57 | `banana` / Banana | Other perennial | `britannia_mod:banana_seeds` (Banana Seeds) | seed / `CropSeedItem` | P / tall (4 blocks) | 3/9/8 | 2-5; reset 5; BH | H .88; Tropical | active | `CropRegistry.java:111` |
| 58 | `broccoli` / Broccoli | Brassica | `britannia_mod:broccoli_seeds` (Broccoli Seeds) | seed / `CropSeedItem` | A / none | 2/6/8 | 2-4, S | H .70; Temperate/Ice | active | `CropRegistry.java:112` |
| 59 | `cauliflower` / Cauliflower | Brassica | `britannia_mod:cauliflower_seeds` (Cauliflower Seeds) | seed / `CropSeedItem` | A / none | 2/7/8 | 1-3, S | H .70; Temperate/Ice | active | `CropRegistry.java:113` |
| 60 | `rhubarb` / Rhubarb | Stalk | `britannia_mod:rhubarb_seeds` (Rhubarb Seeds) | seed / `CropSeedItem` | A / none | 2/6/8 | 2-4, S | H .60; Temperate/Ice | active | `CropRegistry.java:114` |
| 61 | `celery` / Celery | Stalk | `britannia_mod:celery_seeds` (Celery Seeds) | seed / `CropSeedItem` | A / none | 2/6/8 | 2-4, S | H .85; Wetland/Temperate | active | `CropRegistry.java:115` |
| 62 | `tobacco` / Tobacco | Special leaf | `britannia_mod:tobacco_seeds` (Tobacco Seeds) | seed / `CropSeedItem` | A / none | 3/7/8 | 2-4, S | H .55; Tropical/Temperate | active | `CropRegistry.java:116` |
| 63 | `radish` / Radish | Root | `britannia_mod:radish_seeds` (Radish Seeds) | seed / `CropSeedItem` | A / none | 1/4/8 | 1-3, S | H .55; Temperate/Ice | active | `CropRegistry.java:117` |
| 64 | `parsnip` / Parsnip | Root | `britannia_mod:parsnip_seeds` (Parsnip Seeds) | seed / `CropSeedItem` | A / none | 1/5/8 | 1-3, S | H .55; Temperate/Ice | active | `CropRegistry.java:118` |
| 65 | `yam` / Yam | Root/tuber | `britannia_mod:yam_seeds` (Yam Seeds) | seed / `CropSeedItem` | A / none | 2/6/8 | 1-3, RS | H .60; Tropical/Temperate | active; alias `sweet_potato` | `CropRegistry.java:119` |
| 66 | `rutabaga` / Rutabaga | Root | `britannia_mod:rutabaga_seeds` (Rutabaga Seeds) | seed / `CropSeedItem` | A / none | 1/6/8 | 1-3, S | H .55; Temperate/Ice | active | `CropRegistry.java:120` |
| 67 | `grapes` / Grapes | Vine perennial | `britannia_mod:grape_seeds` (variety-specific `… Seeds`) | seed / `GrapeSeedsItem` + custom data | P / grape vine | 3/8/8 | 8-12; reset 4; BH | H .60; Temperate | active; variant-bearing; legacy direct placement; award-path inconsistency | `CropRegistry.java:121`; `GrapeSeedsItem.java` |
| 68 | `britannia_mod:poppy` / Poppy | Persistent flower | `britannia_mod:poppy_seeds` (Poppy Seeds) | seed / ordinary `Item` | F / `FlowerBlock` | –/5/6 natural, 7 absolute | 1 flower; reset 1; S | Flower profile: H .30-.55 ideal; Temperate; alt 45-125 ideal | active; server-selected persistent colour; stage 7 separately requires Farming 100 + knife | `FlowerRegistry.java:86-97` |
| 69 | `britannia_mod:snowdrop` / Snowdrop | Persistent flower | `britannia_mod:snowdrop_seeds` (Snowdrop Seeds) | seed / ordinary `Item` | F / `FlowerBlock` | –/7/7 | 1 flower; reset 1; S | H .50-.75; Ice; alt 55-155 | active; server-selected persistent colour | `FlowerRegistry.java:99-108` |
| 70 | `britannia_mod:lily` / Lily | Persistent flower | `britannia_mod:lily_seeds` (Lily Seeds) | seed / ordinary `Item` | F / `FlowerBlock` | –/8/7 | 1 flower; reset 1; S | H .45-.70; Temperate; alt 45-150 | active; server-selected persistent colour | `FlowerRegistry.java:110-121` |
| 71 | `britannia_mod:foxglove` / Foxglove | Persistent flower | `britannia_mod:foxglove_seeds` (Foxglove Seeds) | seed / ordinary `Item` | F / `FlowerBlock` | –/9/7 | 1 flower; reset 1; S | H .50-.75; Temperate; alt 70-180 | active; server-selected persistent colour | `FlowerRegistry.java:123-134` |
| 72 | `britannia_mod:campion` / Campion | Persistent flower | `britannia_mod:campion_seeds` (Campion Seeds) | seed / ordinary `Item` | F / `FlowerBlock` | –/6/7 | 1 flower; reset 1; S | H .45-.70; Temperate; alt 45-150 | active; server-selected persistent colour | `FlowerRegistry.java:136-146` |
| 73 | `britannia_mod:hyacinth` / Hyacinth | Persistent flower | `britannia_mod:hyacinth_seeds` (Hyacinth Seeds) | seed / ordinary `Item` | F / `FlowerBlock` | –/7/7 | 1 flower; reset 1; S | H .35-.60; Temperate; alt 40-120 | active; server-selected persistent colour | `FlowerRegistry.java:148-159` |
| 74 | `britannia_mod:orfluer` / Orfluer | Persistent flower | `britannia_mod:orfluer_seeds` (Orfluer Seeds) | seed / ordinary `Item` | F / `FlowerBlock` | –/9/7 | 1 flower; reset 1; S | H .40-.65; Magical/Ice; alt 110-220 | active; fictional species; server-selected persistent colour | `FlowerRegistry.java:161-172` |

## Planting-item reconciliation

| Item class/path | Distinct registry IDs | Species mappings | Notes |
|---|---:|---:|---|
| Custom `CropSeedItem` | 60 | 60 | One ID per crop, top-face FarmingBlock path |
| `GrapeSeedsItem` | 1 | 1 | Many variety-bearing stacks, one species |
| Vanilla compatibility items | 6 | 6 | Also retain vanilla routes outside FarmingBlock |
| Ordinary flower seed `Item` | 7 | 7 | One-to-one via `FlowerRegistry` |
| **Total** | **74** | **74** | No shared registry ID between species |

Non-species lookup aliases are: `carrots -> carrot`, `sweet_potato -> yam`, `mandrake_root -> mandrake`, `strawberries -> strawberry`, and `blueberries -> blueberry`. They do not introduce additional planting items.

## Machine-editable representation

This repository has no established catalog data format, so the required representation is CSV. `profile_ref` points to the complete authoritative nutrient/hydration/climate declaration; `current_plant_skill` is intentionally `NONE`, not a proposed value.

```csv
ordinal,species_id,display_name,primary_category,planting_item_id,material,item_class,lifecycle,support,current_plant_skill,status,profile_ref
1,squash,Squash,annual_gourd,britannia_mod:squash_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:55
2,carrot,Carrot,root,britannia_mod:carrot_seeds,seed,CropSeedItem,annual,none,NONE,active_alias_carrots,CropRegistry.java:56
3,corn,Corn,grain,britannia_mod:corn_seeds,seed,CropSeedItem,annual,tall_4,NONE,active,CropRegistry.java:57
4,cabbage,Cabbage,leaf_brassica,britannia_mod:cabbage_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:58
5,lettuce,Lettuce,leaf,britannia_mod:lettuce_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:59
6,yellow_onion,Yellow Onion,allium,britannia_mod:yellow_onion_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:60
7,green_onion,Green Onion,allium,britannia_mod:green_onion_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:61
8,pumpkin,Pumpkin,annual_gourd,britannia_mod:pumpkin_seeds_custom,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:62
9,potato,Potato,root_tuber,britannia_mod:potato_seed,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:63
10,watermelon,Watermelon,annual_melon,britannia_mod:watermelon_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:64
11,vanilla_potato,Vanilla Potato,root_compatibility,minecraft:potato,produce,vanilla_Item,annual,none,NONE,active_compat,CropRegistry.java:65
12,wheat,Wheat,grain,minecraft:wheat_seeds,seed,vanilla_seed_item,annual,none,NONE,active_compat,CropRegistry.java:66
13,rye,Rye,grain,britannia_mod:rye_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:67
14,barley,Barley,grain,britannia_mod:barley_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:68
15,oats,Oats,grain,britannia_mod:oat_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:69
16,mustard,Mustard,grain_seed,britannia_mod:mustard_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:70
17,beans,Beans,pulse,britannia_mod:bean_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:71
18,rice,Rice,grain,britannia_mod:rice_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:72
19,tomato,Tomato,trellis_perennial,britannia_mod:tomato_seeds,seed,CropSeedItem,trellis,trellis,NONE,active,CropRegistry.java:73
20,garlic,Garlic,allium_root,britannia_mod:garlic_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:74
21,ginseng,Ginseng,medicinal,britannia_mod:ginseng_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:75
22,mandrake,Mandrake,magical_medicinal,britannia_mod:mandrake_seeds,seed,CropSeedItem,annual,none,NONE,active_alias_mandrake_root,CropRegistry.java:76
23,nightshade,Nightshade,magical_medicinal,britannia_mod:nightshade_seeds,seed,CropSeedItem,annual,none,NONE,active_darkness_special,CropRegistry.java:77
24,brown_mushroom,Brown Mushroom,mushroom,minecraft:brown_mushroom,whole_mushroom,vanilla_Item,annual,none,NONE,active_compat,CropRegistry.java:78
25,red_mushroom,Red Mushroom,mushroom,minecraft:red_mushroom,whole_mushroom,vanilla_Item,annual,none,NONE,active_compat,CropRegistry.java:79
26,vanilla_pumpkin,Vanilla Pumpkin,gourd_compatibility,minecraft:pumpkin_seeds,seed,vanilla_seed_item,annual,none,NONE,active_compat,CropRegistry.java:80
27,vanilla_melon,Vanilla Melon,melon_compatibility,minecraft:melon_seeds,seed,vanilla_seed_item,annual,none,NONE,active_compat,CropRegistry.java:81
28,pineapple,Pineapple,annual_fruit,britannia_mod:pineapple_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:82
29,strawberry,Strawberry,berry,britannia_mod:strawberry_seeds,seed,CropSeedItem,annual,none,NONE,active_alias_strawberries,CropRegistry.java:83
30,blueberry,Blueberry,berry,britannia_mod:blueberry_seeds,seed,CropSeedItem,annual,none,NONE,active_alias_blueberries,CropRegistry.java:84
31,raspberry,Raspberry,berry,britannia_mod:raspberry_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:85
32,cranberry,Cranberry,berry,britannia_mod:cranberry_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:86
33,blackberry,Blackberry,berry,britannia_mod:blackberry_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:87
34,huckleberry,Huckleberry,berry,britannia_mod:huckleberry_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:88
35,mulberry,Mulberry,berry,britannia_mod:mulberry_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:89
36,elderberry,Elderberry,berry,britannia_mod:elderberry_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:90
37,cherries,Cherries,orchard,britannia_mod:cherry_seeds,seed,CropSeedItem,tree,tree_structure,NONE,active,CropRegistry.java:91
38,cotton,Cotton,fibre,britannia_mod:cotton_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:92
39,flax,Flax,fibre,britannia_mod:flax_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:93
40,hemp,Hemp,fibre,britannia_mod:hemp_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:94
41,hops,Hops,trellis_perennial,britannia_mod:hops_seeds,seed,CropSeedItem,trellis,trellis,NONE,active,CropRegistry.java:95
42,snow_peas,Snow Peas,leaf_pulse,britannia_mod:snow_pea_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:96
43,peas,Peas,leaf_pulse,britannia_mod:pea_seeds,seed,CropSeedItem,annual,none,NONE,active_note_unknown,CropRegistry.java:97
44,turnips,Turnips,root,britannia_mod:turnip_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:98
45,apple,Apple,orchard,britannia_mod:apple_seeds,seed,CropSeedItem,tree,tree_structure,NONE,active,CropRegistry.java:99
46,pear,Pear,orchard,britannia_mod:pear_seeds,seed,CropSeedItem,tree,tree_structure,NONE,active,CropRegistry.java:100
47,peach,Peach,orchard,britannia_mod:peach_seeds,seed,CropSeedItem,tree,tree_structure,NONE,active,CropRegistry.java:101
48,lemon,Lemon,orchard,britannia_mod:lemon_seeds,seed,CropSeedItem,tree,tree_structure,NONE,active,CropRegistry.java:102
49,lime,Lime,orchard,britannia_mod:lime_seeds,seed,CropSeedItem,tree,tree_structure,NONE,active,CropRegistry.java:103
50,orange,Orange,orchard,britannia_mod:orange_seeds,seed,CropSeedItem,tree,tree_structure,NONE,active,CropRegistry.java:104
51,olive,Olive,orchard,britannia_mod:olive_seeds,seed,CropSeedItem,tree,tree_structure,NONE,active,CropRegistry.java:105
52,plum,Plum,orchard,britannia_mod:plum_seeds,seed,CropSeedItem,tree,tree_structure,NONE,active,CropRegistry.java:106
53,bell_peppers,Bell Peppers,trellis_perennial,britannia_mod:bell_pepper_seeds,seed,CropSeedItem,trellis,trellis,NONE,active,CropRegistry.java:107
54,cucumbers,Cucumbers,trellis_perennial,britannia_mod:cucumber_seeds,seed,CropSeedItem,trellis,trellis,NONE,active,CropRegistry.java:108
55,honeydew,Honeydew,trellis_perennial,britannia_mod:honeydew_seeds,seed,CropSeedItem,trellis,trellis,NONE,active,CropRegistry.java:109
56,cantaloupe,Cantaloupe,trellis_perennial,britannia_mod:cantaloupe_seeds,seed,CropSeedItem,trellis,trellis,NONE,active,CropRegistry.java:110
57,banana,Banana,other_perennial,britannia_mod:banana_seeds,seed,CropSeedItem,perennial,tall_4,NONE,active,CropRegistry.java:111
58,broccoli,Broccoli,brassica,britannia_mod:broccoli_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:112
59,cauliflower,Cauliflower,brassica,britannia_mod:cauliflower_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:113
60,rhubarb,Rhubarb,stalk,britannia_mod:rhubarb_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:114
61,celery,Celery,stalk,britannia_mod:celery_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:115
62,tobacco,Tobacco,special_leaf,britannia_mod:tobacco_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:116
63,radish,Radish,root,britannia_mod:radish_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:117
64,parsnip,Parsnip,root,britannia_mod:parsnip_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:118
65,yam,Yam,root_tuber,britannia_mod:yam_seeds,seed,CropSeedItem,annual,none,NONE,active_alias_sweet_potato,CropRegistry.java:119
66,rutabaga,Rutabaga,root,britannia_mod:rutabaga_seeds,seed,CropSeedItem,annual,none,NONE,active,CropRegistry.java:120
67,grapes,Grapes,vine_perennial,britannia_mod:grape_seeds,seed,GrapeSeedsItem,perennial,grape_vine,NONE,active_variant_legacy_route,CropRegistry.java:121
68,britannia_mod:poppy,Poppy,persistent_flower,britannia_mod:poppy_seeds,seed,Item,persistent_flower,FlowerBlock,NONE,active_stage7_special,FlowerRegistry.java:86
69,britannia_mod:snowdrop,Snowdrop,persistent_flower,britannia_mod:snowdrop_seeds,seed,Item,persistent_flower,FlowerBlock,NONE,active,FlowerRegistry.java:99
70,britannia_mod:lily,Lily,persistent_flower,britannia_mod:lily_seeds,seed,Item,persistent_flower,FlowerBlock,NONE,active,FlowerRegistry.java:110
71,britannia_mod:foxglove,Foxglove,persistent_flower,britannia_mod:foxglove_seeds,seed,Item,persistent_flower,FlowerBlock,NONE,active,FlowerRegistry.java:123
72,britannia_mod:campion,Campion,persistent_flower,britannia_mod:campion_seeds,seed,Item,persistent_flower,FlowerBlock,NONE,active,FlowerRegistry.java:136
73,britannia_mod:hyacinth,Hyacinth,persistent_flower,britannia_mod:hyacinth_seeds,seed,Item,persistent_flower,FlowerBlock,NONE,active,FlowerRegistry.java:148
74,britannia_mod:orfluer,Orfluer,persistent_flower,britannia_mod:orfluer_seeds,seed,Item,persistent_flower,FlowerBlock,NONE,active_fictional,FlowerRegistry.java:161
```

## Unknown and ambiguous catalog facts

- `peas` is active and complete enough to plant/grow/harvest, but its registry notes field is empty; intended role is not documented.
- Grapes are unambiguous at species level but ambiguous at variety/presentation level because one registry ID represents Rails-loaded variety stacks and a fallback `wild_grape` value.
- Initial survival acquisition is incomplete or absent for much of the roster. The catalog records active planting definitions, not guaranteed survival availability.
- The six vanilla compatibility items have two policy domains: the custom FarmingBlock route and native vanilla placement. Whether future skill gating must cover both is an owner decision.
- The category taxonomy is inferred from mechanics and names because no authoritative category field exists. It does not assign future progression weight.
