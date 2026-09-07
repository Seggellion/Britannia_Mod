# M7 canonical produce mapping and price review

Canonical packaged source: src/main/resources/economy/supported_produce.json. Rails keeps a byte-identical mirror under db/seeds/data/supported_produce.json. This explicit file accounts for all 67 CropRegistry definitions: 50 supported and 17 excluded, plus legacy produce items and vanilla controls. It contains 55 exact item IDs / 51 canonical commodity keys, including 36 newly seeded keys.

All prices are base copper values; actual quotes use the unchanged Rails current-price scarcity curve, live stock, form=other, weight stock with quantity×unit_weight fallback, copper denomination and nearest-whole payout rule. There is no new skill/quality multiplier. New rows open at zero stock. Existing rows, including every new row after its first insertion, retain their prices, stock, flags, caps and curve parameters. Legacy rows are unchanged by the named rollout.

| Commodity key | Exact item IDs | Crop definitions | Base price / cap on first insertion | Existing comparable and rationale |
|---|---|---|---|---|
| produce|fruit|apple | britannia_mod:apple, minecraft:apple | apple | Existing; unchanged | Legacy mapping preserved; vanilla controls explicit. |
| produce|fruit|banana | britannia_mod:banana | banana | Existing; unchanged | Legacy mapping preserved; vanilla controls explicit. |
| produce|fruit|concord_grapes | britannia_mod:concord_grapes | Legacy only | Existing; unchanged | Legacy mapping preserved; vanilla controls explicit. |
| produce|fruit|peaches | britannia_mod:peaches | peach | Existing; unchanged | Legacy mapping preserved; vanilla controls explicit. |
| produce|fruit|pears | britannia_mod:pears | pear | Existing; unchanged | Legacy mapping preserved; vanilla controls explicit. |
| produce|fruit|berries | britannia_mod:berries | Legacy only | Existing; unchanged | Legacy mapping preserved; vanilla controls explicit. |
| produce|vegetable|squash | britannia_mod:squash | squash | Existing; unchanged | Legacy mapping preserved; vanilla controls explicit. |
| produce|vegetable|carrots | britannia_mod:carrots, minecraft:carrot | carrot | Existing; unchanged | Legacy mapping preserved; vanilla controls explicit. |
| produce|vegetable|corn | britannia_mod:corn | corn | Existing; unchanged | Legacy mapping preserved; vanilla controls explicit. |
| produce|vegetable|cabbage | britannia_mod:cabbage | cabbage | Existing; unchanged | Legacy mapping preserved; vanilla controls explicit. |
| produce|vegetable|lettuce | britannia_mod:lettuce | lettuce | Existing; unchanged | Legacy mapping preserved; vanilla controls explicit. |
| produce|vegetable|onion | britannia_mod:onion | Legacy only | Existing; unchanged | Legacy mapping preserved; vanilla controls explicit. |
| produce|vegetable|pumpkin | britannia_mod:pumpkin, minecraft:pumpkin | pumpkin, vanilla_pumpkin | Existing; unchanged | Legacy mapping preserved; vanilla controls explicit. |
| produce|vegetable|potato | britannia_mod:potato, minecraft:potato | potato, vanilla_potato | Existing; unchanged | Legacy mapping preserved; vanilla controls explicit. |
| produce|vegetable|tomato | britannia_mod:tomato | tomato | Existing; unchanged | Legacy mapping preserved; vanilla controls explicit. |
| produce|vegetable|yellow_onion | britannia_mod:yellow_onion | yellow_onion | 1.50 / 3000 | onion: Existing allium family price, below other tier-1 annual vegetables; legacy onion has no crop tier. |
| produce|vegetable|green_onion | britannia_mod:green_onion | green_onion | 1.50 / 3000 | onion: Existing allium family price, below other tier-1 annual vegetables; legacy onion has no crop tier. |
| produce|fruit|watermelon | britannia_mod:watermelon | watermelon | 2.20 / 3000 | squash: Tier-1 annual gourd; conservative below pumpkin 3.0. |
| produce|vegetable|beans | britannia_mod:beans | beans | 2.00 / 3000 | corn: Tier-2 annual produce comparator at 2.0; no new skill or quality premium. |
| produce|fruit|melon_slice | minecraft:melon_slice | vanilla_melon | 2.20 / 3000 | squash: Tier-1 annual gourd; conservative below pumpkin 3.0. |
| produce|fruit|pineapple | britannia_mod:pineapple | pineapple | 2.00 / 2000 | apple: Tier-3 fruit baseline 2.0; no seeded tier-3 annual fruit exists, so use the lowest same-tier fruit price without a lifecycle premium. |
| produce|fruit|strawberry | britannia_mod:strawberry | strawberry | 2.00 / 2000 | corn: Tier-2 annual produce comparator at 2.0; no new skill or quality premium. |
| produce|fruit|blueberry | britannia_mod:blueberry | blueberry | 2.00 / 2000 | corn: Tier-2 annual produce comparator at 2.0; no new skill or quality premium. |
| produce|fruit|raspberry | britannia_mod:raspberry | raspberry | 2.00 / 2000 | corn: Tier-2 annual produce comparator at 2.0; no new skill or quality premium. |
| produce|fruit|cranberry | britannia_mod:cranberry | cranberry | 2.00 / 2000 | corn: Tier-2 annual produce comparator at 2.0; no new skill or quality premium. |
| produce|fruit|blackberry | britannia_mod:blackberry | blackberry | 2.00 / 2000 | corn: Tier-2 annual produce comparator at 2.0; no new skill or quality premium. |
| produce|fruit|huckleberry | britannia_mod:huckleberry | huckleberry | 2.00 / 2000 | corn: Tier-2 annual produce comparator at 2.0; no new skill or quality premium. |
| produce|fruit|mulberry | britannia_mod:mulberry | mulberry | 2.00 / 2000 | corn: Tier-2 annual produce comparator at 2.0; no new skill or quality premium. |
| produce|fruit|elderberry | britannia_mod:elderberry | elderberry | 2.00 / 2000 | corn: Tier-2 annual produce comparator at 2.0; no new skill or quality premium. |
| produce|fruit|cherries | britannia_mod:cherries | cherries | 2.00 / 2000 | apple: Tier-3 tree fruit comparator at the lowest existing tree price 2.0. |
| produce|vegetable|snow_peas | britannia_mod:snow_peas | snow_peas | 2.00 / 3000 | corn: Tier-2 annual produce comparator at 2.0; no new skill or quality premium. |
| produce|vegetable|peas | britannia_mod:peas | peas | 2.00 / 3000 | corn: Tier-2 annual produce comparator at 2.0; no new skill or quality premium. |
| produce|vegetable|turnips | britannia_mod:turnips | turnips | 1.60 / 3000 | potato: Tier-1 annual root/tuber; conservative below carrots 1.8. |
| produce|fruit|lemon | britannia_mod:lemon | lemon | 2.00 / 2000 | apple: Tier-3 tree fruit comparator at the lowest existing tree price 2.0. |
| produce|fruit|lime | britannia_mod:lime | lime | 2.00 / 2000 | apple: Tier-3 tree fruit comparator at the lowest existing tree price 2.0. |
| produce|fruit|orange | britannia_mod:orange | orange | 2.00 / 2000 | apple: Tier-3 tree fruit comparator at the lowest existing tree price 2.0. |
| produce|fruit|olive | britannia_mod:olive | olive | 2.00 / 2000 | apple: Tier-3 tree fruit comparator at the lowest existing tree price 2.0. |
| produce|fruit|plum | britannia_mod:plum | plum | 2.00 / 2000 | apple: Tier-3 tree fruit comparator at the lowest existing tree price 2.0. |
| produce|vegetable|bell_peppers | britannia_mod:bell_peppers | bell_peppers | 2.00 / 3000 | tomato: Tier-2 regrowing trellis produce comparator at 2.0; no new skill or quality premium. |
| produce|vegetable|cucumbers | britannia_mod:cucumbers | cucumbers | 2.00 / 3000 | tomato: Tier-2 regrowing trellis produce comparator at 2.0; no new skill or quality premium. |
| produce|fruit|honeydew | britannia_mod:honeydew | honeydew | 2.00 / 2000 | tomato: Tier-2 regrowing trellis produce comparator at 2.0; no new skill or quality premium. |
| produce|fruit|cantaloupe | britannia_mod:cantaloupe | cantaloupe | 2.00 / 2000 | tomato: Tier-2 regrowing trellis produce comparator at 2.0; no new skill or quality premium. |
| produce|vegetable|broccoli | britannia_mod:broccoli | broccoli | 2.00 / 3000 | corn: Tier-2 annual produce comparator at 2.0; no new skill or quality premium. |
| produce|vegetable|cauliflower | britannia_mod:cauliflower | cauliflower | 2.00 / 3000 | corn: Tier-2 annual produce comparator at 2.0; no new skill or quality premium. |
| produce|vegetable|rhubarb | britannia_mod:rhubarb | rhubarb | 2.00 / 3000 | corn: Tier-2 annual produce comparator at 2.0; no new skill or quality premium. |
| produce|vegetable|celery | britannia_mod:celery | celery | 2.00 / 3000 | corn: Tier-2 annual produce comparator at 2.0; no new skill or quality premium. |
| produce|vegetable|radish | britannia_mod:radish | radish | 1.60 / 3000 | potato: Tier-1 annual root/tuber; conservative below carrots 1.8. |
| produce|vegetable|parsnip | britannia_mod:parsnip | parsnip | 1.60 / 3000 | potato: Tier-1 annual root/tuber; conservative below carrots 1.8. |
| produce|vegetable|yam | britannia_mod:yam | yam | 2.00 / 3000 | corn: Tier-2 annual produce comparator at 2.0; no new skill or quality premium. |
| produce|vegetable|rutabaga | britannia_mod:rutabaga | rutabaga | 1.60 / 3000 | potato: Tier-1 annual root/tuber; conservative below carrots 1.8. |
| produce|fruit|grapes | britannia_mod:grapes | grapes | 2.50 / 2000 | banana: Tier-3 perennial fruit comparator at 2.5; below legacy concord_grapes 3.0; same persistent-root lifecycle. |

Tier policy: tier-1 gourds use squash 2.20 (below pumpkin 3.00), tier-1 roots use potato 1.60 (below carrots 1.80); alliums use the established onion 1.50 family baseline, whose legacy item has no CropRegistry definition. Tier-2 regrowing produce uses tomato 2.00; tier-2 annual produce uses corn 2.00. Tier-3 tree fruit uses apple 2.00, below pears 2.60/peaches 2.80. Perennial grapes use banana 2.50, below legacy concord_grapes 3.00. Pineapple has no seeded tier-3 annual fruit comparator; apple is the conservative same-tier fruit fallback, without adding a lifecycle premium. Caps retain common-crop 3000 / common-food 2000 conventions.

Exact exclusions: wheat, rye, barley, oats, mustard, rice, garlic, ginseng, mandrake, nightshade, brown_mushroom, red_mushroom, cotton, flax, hemp, hops, tobacco. All custom seed packets and cultivated flowers are excluded; native carrot/potato remain intentional produce-as-seed exceptions. Processed foods, grains, reagents, textiles and tobacco are not accepted by the produce category. Beans are explicitly approved despite the grain-blade harvest tool. Vanilla melon harvest is minecraft:melon_slice → produce|fruit|melon_slice; pear/peach/carrot keep pears/peaches/carrots.

Rollout: Rails db:seed:gameplay_produce_rollout invokes CommoditySeeder.backfill_gameplay_produce! with update_existing:false through the existing stock/form/current-price derivation. General new-city seeding includes the same 36 definitions, marked preserve_existing so subsequent ordinary runs cannot reset these rows. It does not activate NPC types/posts or edit shard policies. insert_all signals the existing City.mark_economic_staffing_dirty mechanism. Each catalog open already fetches live Rails rows; the existing catalog_revision hash changes with row availability/value. No added cache layer.

Rollback: reverting the mapping/rollout removes support from the code but does not delete already-created city rows. Do not reverse it by destructive cleanup or broad reset. No automatic production seed or migration is part of this project.

Validation and live acceptance are recorded in the main handoff and scratchpad. Read-only production Jhelom/Britannia evidence remains pending; local fixture evidence is not production evidence.
