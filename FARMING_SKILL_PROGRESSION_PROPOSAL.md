# Farming Skill Progression Proposal

Milestone: 13 - Progression Design and Owner Approval Gate
Status: Milestone 13 approval gate complete; awaiting explicit Milestone 14 authorization
Branch/starting authority: Farming at ca696c602ea9fe47b7a8ec30069ba17bc180d5cf
Document status: Design-only; no runtime requirement exists.
Progression-row approval: APPROVED 74/74 by direct owner instruction on 2026-08-01.
Behavior-decision approval: APPROVED 15/15; DECISION-001 through DECISION-015 are answered.

## 1. Scope and authority

This proposal uses the accepted Milestone 12 catalog as the sole roster authority. The owner approved one inclusive threshold:

~~~text
player Farming skill >= species minimum Farming skill
~~~

The same approved threshold controls identification and cultivation under DECISION-001. This document does not modify Java, registrations, resources, localization, recipes, loot, saves, networking, tests, or runtime behavior. Corrective Milestone 11 files are outside scope and remain untouched.

Authority was applied in the required order: latest owner instruction; Milestones 12-17; Corrective Milestone 11 only for the model defect; persistent flower design; original playbook; approved flower decisions; confirmed repository behavior; then clearly labeled recommendations.

Evidence vocabulary:

- Confirmed repository fact: directly established by the accepted catalog, registrations, source call chains, or completed flower records.
- Existing owner decision: an approved flower-system choice that remains applicable, such as Poppy stage 7 and flower protection.
- Design-document requirement: a controlling Milestone 13 rule, including full coverage and one threshold.
- Gameplay inference: a qualitative role inferred from category, yield, growth, or lifecycle; not a recipe or price claim.
- Low-confidence assumption: placement made despite missing acquisition, recipe, economy, or intended-role evidence.

## 2. Accepted catalog summary

- Confirmed repository fact: 74 active applicable species, 74 distinct planting-item IDs, 67 crops, and seven persistent flowers.
- Confirmed repository fact: all mappings are currently one-to-one at species level; grape varieties remain one grapes species.
- Confirmed repository fact: all active planting materials are seeds, seed-named CropSeedItems, one produce Potato item, whole mushroom items, one variety-bearing grape seed item, and ordinary flower seed items.
- Confirmed repository fact: no active bulbs, saplings, cuttings, starts, spores, roots-as-items, pits, kernels, grafts, slips, or transplants exist.
- Confirmed repository fact: all 74 are active/applicable. Inactive entries: 0. Uncertain catalog entries: 0. Peas is active but its intended design role is uncertain.
- Confirmed repository fact: initial survival acquisition, recipes, merchant placement, and economy evidence are incomplete or absent for much of the roster.
- Existing owner decision: native vanilla crops and their planting routes are not used by this project and are outside implementation scope. The six compatibility rows remain discovery records and approved reference values; they do not authorize new vanilla-route behavior.

## 3. Balancing framework

The factors are advisory, qualitative, and deliberately not combined into an objective score.

| Factor | Repository evidence | Placement influence | Where it must not dominate | Exceptions |
|---|---|---|---|---|
| cultivation_complexity | Tier, lifecycle, support, harvest tool, special crop/flower services | Later when establishment or care is materially more complex | A special code path alone is not gameplay difficulty | Rutabaga is delayed for distribution despite simple mechanics |
| environmental_sensitivity | Hydration, climate, altitude, darkness and nutrient rules | Narrow or demanding conditions can move a species later | Real-world botany is not evidence | Starter Lettuce remains 0 despite high hydration to preserve food choice |
| growth_duration | Registered tier/tick/stage profile | Slower cycles tend later | Duration alone cannot make a low-value crop endgame | Orchard trees combine long growth with recurrence/structure |
| yield_and_regrowth_value | Yield, seed renewal, perennial reset, orchard recurrence | Strong repeat output can move later | Perennials are not automatically late | Tomato and Cucumbers introduce recurring crops in midgame |
| economic_or_recipe_value | Registered output, recipes, merchants, crafting dependencies | Confirmed chains would raise importance | Missing evidence is never invented | Most rows explicitly mark value unknown |
| rarity_or_access | Registered acquisition and compatibility routes | Proven rarity may raise a band | Missing acquisition does not prove rarity | LOW confidence records alternatives |
| special_structure_or_tool_requirement | Trellis, tree structure, tall clearance, harvest tools | Establishment requirements can move later | Do not double-penalize when slow growth/access already supplies friction | Apple/Peach unlock at 55 despite orchard recurrence |
| processing_chain_value | Straw, produce-to-seed extraction, registered downstream use | Confirmed renewable chains can move later | Plausible brewing/fiber/medicine is only inference | Hops, fibers, Tobacco stay reviewable |
| risk_or_special_handling | Darkness, maximum nutrients, Poppy mastery, protected state | Meaningful handling can reserve late bands | UI/gating implementation risk is not balance | Vanilla compatibility routes do not raise basic crop values |
| progression_role | Food, fruit, utility, fiber, decorative, medicinal, economic variety | Fills meaningful gaps across the range | Category diversity cannot erase real complexity | Campion and Poppy intentionally place flowers early |

Intentional exceptions are identified in row rationales. No revision was silently made after distribution calculation.

## 4. Unlock-band strategy

The confirmed skill scale is float-based with a working 0-100 range. The proposal uses readable five-point bands from 0 through 95. Skill 100 is intentionally not used for ordinary planting: Poppy stage 7 already owns a meaningful Farming-100 mastery interaction, and no ordinary seed needs an artificial capstone value.

Phase model:

- Starter: 0
- Early game: 5-20
- Lower midgame: 25-40
- Upper midgame: 45-60
- Late game: 65-80
- Endgame: 85-100

Eligibility is inclusive. Values outside 0-100 are prohibited. All 74 rows and all 15 behavior decisions were explicitly answered by the owner on 2026-08-01.

## 5. Complete 74-row proposal table

| species | registry_id | category | planting_item_id | planting_material_type | proposed_minimum_farming_skill | unlock_band | progression_role | cultivation_complexity | environmental_sensitivity | growth_duration | yield_and_regrowth_value | economy_or_recipe_factors | rarity_or_access | special_structure_or_tool_requirement | processing_chain_value | risk_or_special_handling | environmental_factors | special_rules | rationale | confidence | alternative_placement | owner_status |
|---|---|---|---|---|---:|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Squash | squash | Annual gourd | britannia_mod:squash_seeds | seed / CropSeedItem | 10 | 10 | early food variety | LOW - ordinary unsupported annual | HIGH - hydration .65; Temperate/Tropical | tier 1; 5 ticks; 8 stages | 1-3; scissors | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | No registered processing-chain evidence | No additional registered risk | hydration .65; Temperate/Tropical | No special post-plant rule | Broad-climate annual with ordinary yield; follows starter staples without needing structure. | MEDIUM | 5 or 15 | APPROVED |
| Carrot | carrot | Root | britannia_mod:carrot_seeds | seed / CropSeedItem | 0 | 0 | starter food | LOW - ordinary unsupported annual | MEDIUM-HIGH - hydration .55; Temperate/Ice | tier 1; 5 ticks; 8 stages | 3; hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | None beyond ordinary farming | No registered processing-chain evidence | No additional registered risk | hydration .55; Temperate/Ice | Alias carrots is lookup-only | Practical food, short ordinary cycle, broad cool-climate access, and no structure. | HIGH | N/A | APPROVED |
| Corn | corn | Grain | britannia_mod:corn_seeds | seed / CropSeedItem | 30 | 30 | midgame staple and tall-crop introduction | HIGH - four-block clearance | MEDIUM - hydration .60; Temperate | tier 2; 7 ticks; 8 stages | 2-5; scissors; produce seed extraction | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Four-block clearance | Registered produce-to-seed renewal; downstream chain unknown | No additional registered risk | hydration .60; Temperate | Requires four-block tall clearance | Useful yield and seed renewal are balanced by slower growth and tall clearance. | MEDIUM | 25 or 35 | APPROVED |
| Cabbage | cabbage | Leaf/brassica | britannia_mod:cabbage_seeds | seed / CropSeedItem | 10 | 10 | early food | LOW - ordinary unsupported annual | MEDIUM-HIGH - hydration .65; Temperate/Ice | tier 1; 6 ticks; 8 stages | 1-3; scissors | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | No registered processing-chain evidence | No additional registered risk | hydration .65; Temperate/Ice | No special post-plant rule | Accessible cool-climate food with ordinary yield and no structural burden. | HIGH | 5 or 15 | APPROVED |
| Lettuce | lettuce | Leaf | britannia_mod:lettuce_seeds | seed / CropSeedItem | 0 | 0 | starter food | LOW - ordinary unsupported annual | MEDIUM-HIGH - hydration .80; Temperate/Ice | tier 1; 4 ticks; 8 stages | 1-3; scissors | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | No registered processing-chain evidence | No additional registered risk | hydration .80; Temperate/Ice | High hydration demand | Fast practical food offsets its high hydration preference and gives starter variety. | HIGH | N/A | APPROVED |
| Yellow Onion | yellow_onion | Allium | britannia_mod:yellow_onion_seeds | seed / CropSeedItem | 10 | 10 | early food and ingredient | LOW - ordinary unsupported annual | MEDIUM-HIGH - hydration .45; Temperate/Ice | tier 1; 5 ticks; 8 stages | 1-3; scissors | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | No registered processing-chain evidence | No additional registered risk | hydration .45; Temperate/Ice | No special post-plant rule | Low hydration demand and simple annual handling fit early progression. | MEDIUM | 5 or 15 | APPROVED |
| Green Onion | green_onion | Allium | britannia_mod:green_onion_seeds | seed / CropSeedItem | 0 | 0 | starter food and ingredient | LOW - ordinary unsupported annual | MEDIUM-HIGH - hydration .55; Temperate/Ice | tier 1; 4 ticks; 8 stages | 1-3; scissors | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | No registered processing-chain evidence | No additional registered risk | hydration .55; Temperate/Ice | No special post-plant rule | Fast ordinary annual provides a second starter food path with no structure. | HIGH | N/A | APPROVED |
| Pumpkin | pumpkin | Annual gourd | britannia_mod:pumpkin_seeds_custom | seed / CropSeedItem | 20 | 20 | early bulky food | LOW - ordinary unsupported annual | HIGH - hydration .70; Temperate/Tropical | tier 1; 7 ticks; 8 stages | 1-2; scissors | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | No registered processing-chain evidence | No additional registered risk | hydration .70; Temperate/Tropical | Distinct from vanilla pumpkin | Longer cycle and lower count justify placement after basic vegetables. | MEDIUM | 15 or 25 | APPROVED |
| Potato | potato | Root/tuber | britannia_mod:potato_seed | seed-named CropSeedItem | 5 | 5 | starter root food | LOW - ordinary unsupported annual | MEDIUM-HIGH - hydration .55; Temperate/Ice | tier 1; 5 ticks; 8 stages | 4; root shovel | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Root shovel at harvest | No registered processing-chain evidence | No additional registered risk | hydration .55; Temperate/Ice | Distinct from vanilla potato | Strong practical yield remains early but follows the initial starter set and introduces root-tool harvest. | HIGH | 0 or 10 | APPROVED |
| Watermelon | watermelon | Annual melon | britannia_mod:watermelon_seeds | seed / CropSeedItem | 50 | 50 | upper-mid fruit and hydration crop | LOW - ordinary unsupported annual | MEDIUM-HIGH - hydration .85; Tropical/Temperate | tier 1; 7 ticks; 8 stages | 1-2; scissors | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | No registered processing-chain evidence | No additional registered risk | hydration .85; Tropical/Temperate | High hydration demand | High water sensitivity and longer growth reserve it for established farms. | MEDIUM | 45 or 55 | APPROVED |
| Vanilla Potato | vanilla_potato | Root/compatibility | minecraft:potato | produce / vanilla Item | 5 | 5 | starter compatibility food | LOW - ordinary unsupported annual | MEDIUM - hydration .60; Temperate | tier 1; 5 ticks; 4 stages | 1-3; hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | None beyond ordinary farming | No registered processing-chain evidence | Compatibility route affects future gate coverage, not placement | hydration .60; Temperate | Native vanilla planting route remains | Basic food belongs early; compatibility complexity is implementation risk, not a balance penalty. | HIGH | 0 or 10 | APPROVED |
| Wheat | wheat | Grain | minecraft:wheat_seeds | seed / vanilla seed item | 0 | 0 | starter staple and fiber byproduct | LOW - ordinary unsupported annual | MEDIUM - hydration .55; Temperate | tier 1; 5 ticks; 8 stages | 6 plus straw; grain blade | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Grain blade at harvest | Registered grain plus straw output; downstream recipes unknown | Compatibility route affects future gate coverage, not placement | hydration .55; Temperate | Native vanilla planting route remains | Core staple and straw source must support progression from Farming 0 despite its strong yield. | HIGH | N/A | APPROVED |
| Rye | rye | Grain | britannia_mod:rye_seeds | seed / CropSeedItem | 10 | 10 | early grain alternative | LOW - ordinary unsupported annual | MEDIUM-HIGH - hydration .45; Temperate/Ice | tier 1; 5 ticks; 8 stages | 6 plus straw; grain blade | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Grain blade at harvest | Registered grain plus straw output; downstream recipes unknown | No additional registered risk | hydration .45; Temperate/Ice | No special post-plant rule | Broad cool access and straw yield make a useful early grain without crowding Farming 0. | MEDIUM | 5 or 15 | APPROVED |
| Barley | barley | Grain | britannia_mod:barley_seeds | seed / CropSeedItem | 15 | 15 | early grain variety | LOW - ordinary unsupported annual | MEDIUM-HIGH - hydration .48; Temperate/Ice | tier 1; 5 ticks; 8 stages | 6 plus straw; grain blade | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Grain blade at harvest | Registered grain plus straw output; downstream recipes unknown | No additional registered risk | hydration .48; Temperate/Ice | No special post-plant rule | Strong grain/straw output is delayed modestly to pace staple choices. | MEDIUM | 10 or 20 | APPROVED |
| Oats | oats | Grain | britannia_mod:oat_seeds | seed / CropSeedItem | 15 | 15 | early grain variety | LOW - ordinary unsupported annual | MEDIUM-HIGH - hydration .62; Temperate/Ice | tier 1; 5 ticks; 8 stages | 6 plus straw; grain blade | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Grain blade at harvest | Registered grain plus straw output; downstream recipes unknown | No additional registered risk | hydration .62; Temperate/Ice | No special post-plant rule | Comparable grain value to barley supports choice within the same readable band. | MEDIUM | 10 or 20 | APPROVED |
| Mustard | mustard | Grain/seed crop | britannia_mod:mustard_seeds | seed / CropSeedItem | 30 | 30 | midgame seed/ingredient crop | MODERATE - ordinary tier-2 crop | MEDIUM-HIGH - hydration .50; Temperate/Ice | tier 2; 5 ticks; 8 stages | 6 plus straw; grain blade; harvest item is seed | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Grain blade at harvest | Registered grain plus straw output; downstream recipes unknown | No additional registered risk | hydration .50; Temperate/Ice | Planting and harvest item coincide | Higher tier and self-renewing planting material merit midgame placement. | MEDIUM | 25 or 35 | APPROVED |
| Beans | beans | Pulse | britannia_mod:bean_seeds | seed / CropSeedItem | 25 | 25 | lower-mid food and pulse | MODERATE - ordinary tier-2 crop | HIGH - hydration .60; Temperate/Tropical | tier 2; 6 ticks; 8 stages | 6 plus straw; grain blade | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Grain blade at harvest | Registered grain plus straw output; downstream recipes unknown | No additional registered risk | hydration .60; Temperate/Tropical | No trellis requirement is registered | High useful yield and broad climate fit justify an early-mid unlock. | MEDIUM | 20 or 30 | APPROVED |
| Rice | rice | Grain | britannia_mod:rice_seeds | seed / CropSeedItem | 75 | 75 | late specialized staple | MODERATE-HIGH - slower tier-3 crop | HIGH - hydration .95; Wetland | tier 3; 7 ticks; 5 stages | 2-4; hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - acquisition evidence missing; material owner review required | None beyond ordinary farming | No registered processing-chain evidence | No additional registered risk | hydration .95; Wetland | Wetland-only profile | Extreme hydration and narrow wetland access create meaningful late specialization; acquisition evidence is missing. | LOW | 70 or 80 | APPROVED |
| Tomato | tomato | Trellis perennial | britannia_mod:tomato_seeds | seed / CropSeedItem | 40 | 40 | lower-mid perennial food | HIGH - support structure plus recurring lifecycle | HIGH - hydration .72; Temperate/Tropical | tier 2; 6 ticks; 8 stages | 2-4; reset 4; scissors | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Trellis required | No registered processing-chain evidence | No additional registered risk | hydration .72; Temperate/Tropical | Requires trellis; recurring harvest | Recurring food becomes available in midgame while trellis investment prevents it from replacing starters. | MEDIUM | 35 or 45 | APPROVED |
| Garlic | garlic | Allium/root | britannia_mod:garlic_seeds | seed / CropSeedItem | 20 | 20 | early ingredient and root crop | MODERATE-HIGH - slower tier-3 crop | MEDIUM-HIGH - hydration .45; Temperate/Ice | tier 3; 7 ticks; 8 stages | 2-4; root shovel | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Root shovel at harvest | No registered processing-chain evidence | No additional registered risk | hydration .45; Temperate/Ice | Root-shovel harvest | Higher tier and tool-specific harvest place it after simpler alliums but before midgame structures. | MEDIUM | 15 or 25 | APPROVED |
| Ginseng | ginseng | Medicinal | britannia_mod:ginseng_seeds | seed / CropSeedItem | 70 | 70 | late medicinal crop | HIGH - tier-4 slow/special crop | HIGH - hydration .55; Magical/Temperate | tier 4; 9 ticks; 8 stages | 1-2; scissors | No recipe, merchant, or price evidence; medicinal/magical role is inference | UNKNOWN - acquisition evidence missing; material owner review required | Scissors at harvest | No registered processing-chain evidence | No additional registered risk | hydration .55; Magical/Temperate | Medicinal role inferred; no recipe evidence | Slow high-tier growth and magical climate justify late placement; recipe, acquisition, and economic evidence are missing. | LOW | 65 or 75 | APPROVED |
| Mandrake | mandrake | Magical/medicinal | britannia_mod:mandrake_seeds | seed / CropSeedItem | 90 | 90 | endgame magical crop | HIGH - tier-4 slow/special crop | HIGH - hydration .55; Magical/Temperate/Ice | tier 4; 10 ticks; 8 stages | 1-2; root shovel | No recipe, merchant, or price evidence; medicinal/magical role is inference | UNKNOWN - acquisition evidence missing; material owner review required | Root shovel at harvest | No registered processing-chain evidence | Repository-confirmed special environmental handling | hydration .55; Magical/Temperate/Ice | Maximum-nutrient mode; alias mandrake_root | Longest high-tier cycle plus maximum-nutrient handling supports endgame; uses and acquisition remain undocumented. | LOW | 85 or 95 | APPROVED |
| Nightshade | nightshade | Magical/medicinal | britannia_mod:nightshade_seeds | seed / CropSeedItem | 85 | 85 | endgame underground crop | HIGH - tier-4 slow/special crop | HIGH - hydration .45; Magical/Underground | tier 4; 9 ticks; 8 stages | 1-2; grain blade | No recipe, merchant, or price evidence; medicinal/magical role is inference | UNKNOWN - acquisition evidence missing; material owner review required | Grain blade at harvest | No registered processing-chain evidence | Repository-confirmed special environmental handling | hydration .45; Magical/Underground | Maximum-nutrient and darkness handling | Darkness, underground climate, high tier, and special nutrient handling justify a high requirement; recipes are unknown. | LOW | 80 or 90 | APPROVED |
| Brown Mushroom | brown_mushroom | Mushroom | minecraft:brown_mushroom | whole mushroom / vanilla Item | 55 | 55 | upper-mid specialist food/material | MODERATE - ordinary tier-2 crop | HIGH - hydration .75; Underground/Wetland | tier 2; 6 ticks; 4 stages | 1-3; hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | None beyond ordinary farming | No registered processing-chain evidence | No additional registered risk | hydration .75; Underground/Wetland | Native vanilla placement remains | Dark or wet conditions create a specialized farm after ordinary field crops. | MEDIUM | 50 or 60 | APPROVED |
| Red Mushroom | red_mushroom | Mushroom | minecraft:red_mushroom | whole mushroom / vanilla Item | 60 | 60 | upper-mid specialist food/material | MODERATE - ordinary tier-2 crop | HIGH - hydration .75; Underground/Wetland | tier 2; 6 ticks; 4 stages | 1-3; hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | None beyond ordinary farming | No registered processing-chain evidence | No additional registered risk | hydration .75; Underground/Wetland | Native vanilla placement remains | Paired specialist content is staggered to create another unlock without implying identical requirements. | MEDIUM | 55 or 65 | APPROVED |
| Vanilla Pumpkin | vanilla_pumpkin | Gourd/compatibility | minecraft:pumpkin_seeds | seed / vanilla seed item | 25 | 25 | lower-mid compatibility gourd | LOW - ordinary unsupported annual | MEDIUM - hydration .65; Temperate | tier 1; 6 ticks; 4 stages | 1-2; hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | None beyond ordinary farming | No registered processing-chain evidence | Compatibility route affects future gate coverage, not placement | hydration .65; Temperate | Native vanilla planting route remains | Ordinary mechanics but bulky crop value place it after the custom pumpkin to spread choices. | MEDIUM | 20 or 30 | APPROVED |
| Vanilla Melon | vanilla_melon | Melon/compatibility | minecraft:melon_seeds | seed / vanilla seed item | 45 | 45 | upper-mid fruit | LOW - ordinary unsupported annual | HIGH - hydration .75; Temperate/Tropical | tier 1; 6 ticks; 4 stages | 2-5; hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | None beyond ordinary farming | No registered processing-chain evidence | Compatibility route affects future gate coverage, not placement | hydration .75; Temperate/Tropical | Native vanilla planting route remains | Useful yield and higher hydration support a later unlock than basic gourds. | MEDIUM | 40 or 50 | APPROVED |
| Pineapple | pineapple | Annual fruit | britannia_mod:pineapple_seeds | seed / CropSeedItem | 65 | 65 | late tropical fruit | MODERATE-HIGH - slower tier-3 crop | MEDIUM-HIGH - hydration .60; Tropical/Temperate | tier 3; 8 ticks; 8 stages | 1; bare hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | None beyond ordinary farming | No registered processing-chain evidence | No additional registered risk | hydration .60; Tropical/Temperate | Single-output harvest | Slow high-tier cycle and single output reserve this fruit for late progression without structural friction. | MEDIUM | 60 or 70 | APPROVED |
| Strawberry | strawberry | Berry | britannia_mod:strawberry_seeds | seed / CropSeedItem | 15 | 15 | early fruit | MODERATE - ordinary tier-2 crop | MEDIUM - hydration .65; Temperate | tier 2; 6 ticks; 8 stages | 4; bare hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | None beyond ordinary farming | No registered processing-chain evidence | No additional registered risk | hydration .65; Temperate | Alias strawberries is lookup-only | An early berry keeps fruit available before orchards while its yield delays it beyond starter crops. | MEDIUM | 10 or 20 | APPROVED |
| Blueberry | blueberry | Berry | britannia_mod:blueberry_seeds | seed / CropSeedItem | 35 | 35 | lower-mid berry | MODERATE - ordinary tier-2 crop | MEDIUM-HIGH - hydration .72; Temperate/Ice | tier 2; 7 ticks; 8 stages | 6; bare hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | None beyond ordinary farming | No registered processing-chain evidence | No additional registered risk | hydration .72; Temperate/Ice | Alias blueberries is lookup-only | High berry yield and longer cycle justify midgame placement. | MEDIUM | 30 or 40 | APPROVED |
| Raspberry | raspberry | Berry | britannia_mod:raspberry_seeds | seed / CropSeedItem | 25 | 25 | lower-mid berry | MODERATE - ordinary tier-2 crop | MEDIUM-HIGH - hydration .62; Temperate/Ice | tier 2; 6 ticks; 8 stages | 5; bare hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | None beyond ordinary farming | No registered processing-chain evidence | No additional registered risk | hydration .62; Temperate/Ice | No special post-plant rule | Good yield and broad cool access make a useful early-mid fruit choice. | MEDIUM | 20 or 30 | APPROVED |
| Cranberry | cranberry | Berry | britannia_mod:cranberry_seeds | seed / CropSeedItem | 70 | 70 | late wetland berry | MODERATE - ordinary tier-2 crop | HIGH - hydration .88; Wetland/Temperate | tier 2; 7 ticks; 8 stages | 6; bare hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | None beyond ordinary farming | No registered processing-chain evidence | No additional registered risk | hydration .88; Wetland/Temperate | Very high hydration | High yield is balanced by demanding hydration and wetland preference. | MEDIUM | 65 or 75 | APPROVED |
| Blackberry | blackberry | Berry | britannia_mod:blackberry_seeds | seed / CropSeedItem | 30 | 30 | midgame berry | MODERATE - ordinary tier-2 crop | MEDIUM-HIGH - hydration .62; Temperate/Ice | tier 2; 6 ticks; 8 stages | 5; bare hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | None beyond ordinary farming | No registered processing-chain evidence | No additional registered risk | hydration .62; Temperate/Ice | No special post-plant rule | Reliable yield adds fruit choice to the first dense midgame band. | MEDIUM | 25 or 35 | APPROVED |
| Huckleberry | huckleberry | Berry | britannia_mod:huckleberry_seeds | seed / CropSeedItem | 40 | 40 | lower-mid berry | MODERATE - ordinary tier-2 crop | MEDIUM-HIGH - hydration .58; Temperate/Ice | tier 2; 7 ticks; 8 stages | 5; bare hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | None beyond ordinary farming | No registered processing-chain evidence | No additional registered risk | hydration .58; Temperate/Ice | No special post-plant rule | Longer cycle extends berry progression without relying on invented economic value. | MEDIUM | 35 or 45 | APPROVED |
| Mulberry | mulberry | Berry | britannia_mod:mulberry_seeds | seed / CropSeedItem | 45 | 45 | upper-mid berry | MODERATE - ordinary tier-2 crop | HIGH - hydration .60; Temperate/Tropical | tier 2; 6 ticks; 8 stages | 5; bare hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | None beyond ordinary farming | No registered processing-chain evidence | No additional registered risk | hydration .60; Temperate/Tropical | No special post-plant rule | Broad warm access and good yield support upper-mid fruit diversity. | MEDIUM | 40 or 50 | APPROVED |
| Elderberry | elderberry | Berry | britannia_mod:elderberry_seeds | seed / CropSeedItem | 50 | 50 | upper-mid berry | MODERATE - ordinary tier-2 crop | MEDIUM-HIGH - hydration .70; Temperate/Ice | tier 2; 6 ticks; 8 stages | 5; bare hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | None beyond ordinary farming | No registered processing-chain evidence | No additional registered risk | hydration .70; Temperate/Ice | Medicinal use not confirmed | Higher hydration and inferred utility justify a modest step beyond common berries. | MEDIUM | 45 or 55 | APPROVED |
| Cherries | cherries | Orchard | britannia_mod:cherry_seeds | seed / CropSeedItem | 60 | 60 | upper-mid orchard fruit | HIGH - multi-block orchard establishment | MEDIUM - hydration .60; Temperate | tier 3; 10 ticks; 4 stages | 2-5; renewable tree; hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | FruitTreeRegistry structure and clearance | No registered processing-chain evidence | No additional registered risk | hydration .60; Temperate | FruitTreeRegistry structure overlay | Long growth and recurring orchard output justify a substantial requirement without pushing every tree late. | MEDIUM | 55 or 65 | APPROVED |
| Cotton | cotton | Fibre | britannia_mod:cotton_seeds | seed / CropSeedItem | 40 | 40 | lower-mid fiber utility | MODERATE - ordinary tier-2 crop | HIGH - hydration .50; Temperate/Tropical | tier 2; 8 ticks; 8 stages | 2-4; scissors | No recipe, merchant, or price evidence; utility/fiber role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | Fiber role inferred; no registered processing chain found | No additional registered risk | hydration .50; Temperate/Tropical | Fiber processing not registered | Slow annual growth introduces a second utility track in midgame; processing value remains unproven. | MEDIUM | 35 or 45 | APPROVED |
| Flax | flax | Fibre | britannia_mod:flax_seeds | seed / CropSeedItem | 20 | 20 | early fiber utility | MODERATE - ordinary tier-2 crop | MEDIUM-HIGH - hydration .55; Temperate/Ice | tier 2; 6 ticks; 8 stages | 2-4; grain blade | No recipe, merchant, or price evidence; utility/fiber role is inference | UNKNOWN - initial survival acquisition not demonstrated | Grain blade at harvest | Fiber role inferred; no registered processing chain found | No additional registered risk | hydration .55; Temperate/Ice | Fiber processing not registered | Early utility diversity is valuable and mechanics are ordinary; no recipe value is assumed. | MEDIUM | 15 or 25 | APPROVED |
| Hemp | hemp | Fibre | britannia_mod:hemp_seeds | seed / CropSeedItem | 50 | 50 | upper-mid fiber utility | MODERATE - ordinary tier-2 crop | HIGH - hydration .55; Temperate/Tropical | tier 2; 6 ticks; 8 stages | 2-4; scissors | No recipe, merchant, or price evidence; utility/fiber role is inference | UNKNOWN - acquisition evidence missing; material owner review required | Scissors at harvest | Fiber role inferred; no registered processing chain found | No additional registered risk | hydration .55; Temperate/Tropical | Fiber processing not registered | Staggers fiber choices across progression; exact utility, processing, economy, and access are missing. | LOW | 45 or 60 | APPROVED |
| Hops | hops | Trellis perennial | britannia_mod:hops_seeds | seed / CropSeedItem | 60 | 60 | upper-mid processing input | HIGH - support structure plus recurring lifecycle | MEDIUM - hydration .70; Temperate | tier 3; 7 ticks; 8 stages | 2-4; reset 4; scissors | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Trellis required | Likely processing role is inference; no registered chain found | No additional registered risk | hydration .70; Temperate | Recurring harvest; trellis | Recurring yield, trellis, and higher tier justify upper-mid placement; brewing value is not treated as confirmed. | MEDIUM | 55 or 65 | APPROVED |
| Snow Peas | snow_peas | Leaf/pulse | britannia_mod:snow_pea_seeds | seed / CropSeedItem | 20 | 20 | early food variety | MODERATE - ordinary tier-2 crop | MEDIUM-HIGH - hydration .60; Temperate/Ice | tier 2; 5 ticks; 8 stages | 2-4; scissors | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | No registered processing-chain evidence | No additional registered risk | hydration .60; Temperate/Ice | Explicitly no trellis | Fast tier-2 pulse offers early food variety without a structure requirement. | HIGH | 15 or 25 | APPROVED |
| Peas | peas | Leaf/pulse | britannia_mod:pea_seeds | seed / CropSeedItem | 5 | 5 | starter-adjacent food | MODERATE - ordinary tier-2 crop | MEDIUM - hydration .60; Temperate | tier 2; 6 ticks; 4 stages | 2-4; hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - acquisition evidence missing; material owner review required | None beyond ordinary farming | No registered processing-chain evidence | No additional registered risk | hydration .60; Temperate | Registry note is blank | Mechanics support early food, but intended role, acquisition, recipes, and economy are undocumented. | LOW | 10 | APPROVED |
| Turnips | turnips | Root | britannia_mod:turnip_seeds | seed / CropSeedItem | 5 | 5 | starter root food | LOW - ordinary unsupported annual | MEDIUM-HIGH - hydration .55; Temperate/Ice | tier 1; 5 ticks; 8 stages | 1-3; scissors | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | No registered processing-chain evidence | No additional registered risk | hydration .55; Temperate/Ice | No special post-plant rule | Simple broad-climate root expands the first earned band without structural dependence. | HIGH | 0 or 10 | APPROVED |
| Apple | apple | Orchard | britannia_mod:apple_seeds | seed / CropSeedItem | 55 | 55 | first orchard fruit | HIGH - multi-block orchard establishment | MEDIUM - hydration .60; Temperate | tier 3; 10 ticks; 4 stages | 2-5; renewable tree; hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | FruitTreeRegistry structure and clearance | No registered processing-chain evidence | No additional registered risk | hydration .60; Temperate | FruitTreeRegistry structure overlay | Introduces recurring orchard value before late game while long growth and structure supply friction. | MEDIUM | 50 or 60 | APPROVED |
| Pear | pear | Orchard | britannia_mod:pear_seeds | seed / CropSeedItem | 60 | 60 | upper-mid orchard fruit | HIGH - multi-block orchard establishment | MEDIUM - hydration .65; Temperate | tier 3; 10 ticks; 4 stages | 2-5; renewable tree; hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | FruitTreeRegistry structure and clearance | No registered processing-chain evidence | No additional registered risk | hydration .65; Temperate | FruitTreeRegistry structure overlay | Recurring yield and long structure growth justify upper-mid placement. | MEDIUM | 55 or 65 | APPROVED |
| Peach | peach | Orchard | britannia_mod:peach_seeds | seed / CropSeedItem | 55 | 55 | early orchard alternative | HIGH - multi-block orchard establishment | MEDIUM - hydration .65; Temperate | tier 3; 10 ticks; 4 stages | 2-5; renewable tree; hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | FruitTreeRegistry structure and clearance | No registered processing-chain evidence | No additional registered risk | hydration .65; Temperate | FruitTreeRegistry structure overlay | Pairs with apple to make the first orchard band a choice rather than a single unlock. | MEDIUM | 50 or 60 | APPROVED |
| Lemon | lemon | Orchard | britannia_mod:lemon_seeds | seed / CropSeedItem | 70 | 70 | late tropical orchard | HIGH - multi-block orchard establishment | HIGH - hydration .55; Tropical | tier 3; 10 ticks; 4 stages | 2-5; renewable tree; hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | FruitTreeRegistry structure and clearance | No registered processing-chain evidence | No additional registered risk | hydration .55; Tropical | FruitTreeRegistry structure overlay | Recurring tree output plus tropical restriction justify late placement. | MEDIUM | 65 or 75 | APPROVED |
| Lime | lime | Orchard | britannia_mod:lime_seeds | seed / CropSeedItem | 75 | 75 | late tropical orchard | HIGH - multi-block orchard establishment | HIGH - hydration .70; Tropical | tier 3; 10 ticks; 4 stages | 2-5; renewable tree; hand; produce seed extraction | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | FruitTreeRegistry structure and clearance | Registered produce-to-seed renewal; downstream chain unknown | No additional registered risk | hydration .70; Tropical | FruitTreeRegistry overlay; produce can recover seed | High hydration, tropical restriction, renewable tree output, and extra seed renewal support a later band. | MEDIUM | 70 or 80 | APPROVED |
| Orange | orange | Orchard | britannia_mod:orange_seeds | seed / CropSeedItem | 70 | 70 | late orchard fruit | HIGH - multi-block orchard establishment | MEDIUM-HIGH - hydration .60; Tropical/Temperate | tier 3; 10 ticks; 4 stages | 2-5; renewable tree; hand; produce seed extraction | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | FruitTreeRegistry structure and clearance | Registered produce-to-seed renewal; downstream chain unknown | No additional registered risk | hydration .60; Tropical/Temperate | FruitTreeRegistry overlay; produce can recover seed | Broad warm fit tempers strong recurring output and seed renewal. | MEDIUM | 65 or 75 | APPROVED |
| Olive | olive | Orchard | britannia_mod:olive_seeds | seed / CropSeedItem | 75 | 75 | late arid orchard utility | HIGH - multi-block orchard establishment | HIGH - hydration .40; Arid/Temperate | tier 3; 10 ticks; 4 stages | 2-5; renewable tree; hand; produce seed extraction | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | FruitTreeRegistry structure and clearance | Registered produce-to-seed renewal; downstream chain unknown | No additional registered risk | hydration .40; Arid/Temperate | FruitTreeRegistry overlay; produce can recover seed | Distinct arid access, recurring yield, and seed extraction make a meaningful late orchard unlock. | MEDIUM | 70 or 80 | APPROVED |
| Plum | plum | Orchard | britannia_mod:plum_seeds | seed / CropSeedItem | 65 | 65 | late cool orchard | HIGH - multi-block orchard establishment | MEDIUM-HIGH - hydration .62; Temperate/Ice | tier 3; 9 ticks; 4 stages | 2-5; renewable tree; hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | FruitTreeRegistry structure and clearance | No registered processing-chain evidence | No additional registered risk | hydration .62; Temperate/Ice | FruitTreeRegistry structure overlay | Slightly faster tree growth and cool tolerance place it before the tropical/arid orchard tier. | MEDIUM | 60 or 70 | APPROVED |
| Bell Peppers | bell_peppers | Trellis perennial | britannia_mod:bell_pepper_seeds | seed / CropSeedItem | 45 | 45 | upper-mid perennial food | HIGH - support structure plus recurring lifecycle | MEDIUM-HIGH - hydration .62; Tropical/Temperate | tier 2; 6 ticks; 8 stages | 2-4; reset 4; scissors | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Trellis required | No registered processing-chain evidence | No additional registered risk | hydration .62; Tropical/Temperate | Recurring harvest; trellis | Recurring output and trellis justify a step beyond tomato while retaining warm-climate choice. | MEDIUM | 40 or 50 | APPROVED |
| Cucumbers | cucumbers | Trellis perennial | britannia_mod:cucumber_seeds | seed / CropSeedItem | 35 | 35 | lower-mid perennial food | HIGH - support structure plus recurring lifecycle | HIGH - hydration .82; Temperate/Tropical | tier 2; 6 ticks; 8 stages | 2-4; reset 4; scissors | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Trellis required | No registered processing-chain evidence | No additional registered risk | hydration .82; Temperate/Tropical | Recurring harvest; trellis | Early trellis introduction is balanced by high hydration and structure investment. | MEDIUM | 30 or 40 | APPROVED |
| Honeydew | honeydew | Trellis perennial | britannia_mod:honeydew_seeds | seed / CropSeedItem | 60 | 60 | upper-mid perennial fruit | HIGH - support structure plus recurring lifecycle | MEDIUM-HIGH - hydration .86; Tropical/Temperate | tier 2; 7 ticks; 8 stages | 1-3; reset 4; scissors | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Trellis required | No registered processing-chain evidence | No additional registered risk | hydration .86; Tropical/Temperate | Recurring harvest; trellis | High hydration, longer growth, trellis, and recurring fruit justify upper-mid placement. | MEDIUM | 55 or 65 | APPROVED |
| Cantaloupe | cantaloupe | Trellis perennial | britannia_mod:cantaloupe_seeds | seed / CropSeedItem | 55 | 55 | upper-mid perennial fruit | HIGH - support structure plus recurring lifecycle | HIGH - hydration .74; Tropical/Temperate/Arid | tier 2; 7 ticks; 8 stages | 1-3; reset 4; scissors | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Trellis required | No registered processing-chain evidence | No additional registered risk | hydration .74; Tropical/Temperate/Arid | Recurring harvest; trellis | Broad climate access moderates recurring trellis value and makes it the earlier perennial melon. | MEDIUM | 50 or 60 | APPROVED |
| Banana | banana | Other perennial | britannia_mod:banana_seeds | seed / CropSeedItem | 80 | 80 | late high-value perennial fruit | HIGH - four-block clearance | HIGH - hydration .88; Tropical | tier 3; 9 ticks; 8 stages | 2-5; reset 5; bare hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Four-block clearance | No registered processing-chain evidence | No additional registered risk | hydration .88; Tropical | Four-block tall; recurring harvest | Tall clearance, high hydration, tropical restriction, slow growth, and recurring yield combine into a strong late unlock. | MEDIUM | 75 or 85 | APPROVED |
| Broccoli | broccoli | Brassica | britannia_mod:broccoli_seeds | seed / CropSeedItem | 25 | 25 | lower-mid food | MODERATE - ordinary tier-2 crop | MEDIUM-HIGH - hydration .70; Temperate/Ice | tier 2; 6 ticks; 8 stages | 2-4; scissors | No recipe, merchant, or price evidence; special role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | No registered processing-chain evidence | No additional registered risk | hydration .70; Temperate/Ice | No special post-plant rule | Moderate tier and yield make a natural lower-mid food choice. | MEDIUM | 20 or 30 | APPROVED |
| Cauliflower | cauliflower | Brassica | britannia_mod:cauliflower_seeds | seed / CropSeedItem | 30 | 30 | midgame food | MODERATE - ordinary tier-2 crop | MEDIUM-HIGH - hydration .70; Temperate/Ice | tier 2; 7 ticks; 8 stages | 1-3; scissors | No recipe, merchant, or price evidence; special role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | No registered processing-chain evidence | No additional registered risk | hydration .70; Temperate/Ice | No special post-plant rule | Longer growth and lower yield stagger it after broccoli. | MEDIUM | 25 or 35 | APPROVED |
| Rhubarb | rhubarb | Stalk | britannia_mod:rhubarb_seeds | seed / CropSeedItem | 45 | 45 | upper-mid ingredient crop | MODERATE - ordinary tier-2 crop | MEDIUM-HIGH - hydration .60; Temperate/Ice | tier 2; 6 ticks; 8 stages | 2-4; scissors | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | No registered processing-chain evidence | No additional registered risk | hydration .60; Temperate/Ice | No registered processing use | Ordinary mechanics but later ingredient variety smooths upper-mid progression; recipe value is unknown. | MEDIUM | 40 or 50 | APPROVED |
| Celery | celery | Stalk | britannia_mod:celery_seeds | seed / CropSeedItem | 40 | 40 | lower-mid wet crop | MODERATE - ordinary tier-2 crop | HIGH - hydration .85; Wetland/Temperate | tier 2; 6 ticks; 8 stages | 2-4; scissors | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | No registered processing-chain evidence | No additional registered risk | hydration .85; Wetland/Temperate | High hydration and wetland fit | High hydration and wetland preference create a meaningful farm-management step. | MEDIUM | 35 or 45 | APPROVED |
| Tobacco | tobacco | Special leaf | britannia_mod:tobacco_seeds | seed / CropSeedItem | 65 | 65 | late economic/processing candidate | MODERATE-HIGH - slower tier-3 crop | MEDIUM-HIGH - hydration .55; Tropical/Temperate | tier 3; 7 ticks; 8 stages | 2-4; scissors | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - acquisition evidence missing; material owner review required | Scissors at harvest | Likely processing role is inference; no registered chain found | No additional registered risk | hydration .55; Tropical/Temperate | No registered processing or economy use | Higher tier and presumed special role support late placement, but all processing, market, recipe, and access evidence is missing. | LOW | 55 or 70 | APPROVED |
| Radish | radish | Root | britannia_mod:radish_seeds | seed / CropSeedItem | 5 | 5 | starter fast root | LOW - ordinary unsupported annual | MEDIUM-HIGH - hydration .55; Temperate/Ice | tier 1; 4 ticks; 8 stages | 1-3; scissors | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | No registered processing-chain evidence | No additional registered risk | hydration .55; Temperate/Ice | No special post-plant rule | Fast simple crop makes the first earned band immediately useful. | HIGH | 0 or 10 | APPROVED |
| Parsnip | parsnip | Root | britannia_mod:parsnip_seeds | seed / CropSeedItem | 15 | 15 | early root variety | LOW - ordinary unsupported annual | MEDIUM-HIGH - hydration .55; Temperate/Ice | tier 1; 5 ticks; 8 stages | 1-3; scissors | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | No registered processing-chain evidence | No additional registered risk | hydration .55; Temperate/Ice | No special post-plant rule | Ordinary root spreads food unlocks through early progression. | HIGH | 10 or 20 | APPROVED |
| Yam | yam | Root/tuber | britannia_mod:yam_seeds | seed-named CropSeedItem | 35 | 35 | lower-mid warm root | MODERATE - ordinary tier-2 crop | MEDIUM-HIGH - hydration .60; Tropical/Temperate | tier 2; 6 ticks; 8 stages | 1-3; root shovel | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Root shovel at harvest | No registered processing-chain evidence | No additional registered risk | hydration .60; Tropical/Temperate | Alias sweet_potato is lookup-only | Higher tier, root tool, and warm profile justify lower-mid placement. | MEDIUM | 30 or 40 | APPROVED |
| Rutabaga | rutabaga | Root | britannia_mod:rutabaga_seeds | seed / CropSeedItem | 35 | 35 | lower-mid root variety | LOW - ordinary unsupported annual | MEDIUM-HIGH - hydration .55; Temperate/Ice | tier 1; 6 ticks; 8 stages | 1-3; scissors | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | No registered processing-chain evidence | No additional registered risk | hydration .55; Temperate/Ice | No special post-plant rule | Later placement is an intentional progression-diversity exception among simple roots, not a claim of botanical difficulty. | MEDIUM | 30 or 40 | APPROVED |
| Grapes | grapes | Vine perennial | britannia_mod:grape_seeds | seed / GrapeSeedsItem plus variety data | 80 | 80 | late economic perennial | HIGH - support structure plus recurring lifecycle | MEDIUM - hydration .60; Temperate | tier 3; 8 ticks; 8 stages | 8-12; reset 4; bare hand | No recipe, merchant, or price evidence; food/ingredient role is inference | UNKNOWN - acquisition evidence missing; material owner review required | Grape-vine support/legacy route | Likely processing role is inference; no registered chain found | Variant identity and legacy route; do not treat variety as species | hydration .60; Temperate | Variant-bearing; legacy direct placement; award-path inconsistency | Exceptional recurring yield, variety identity, and vine handling justify late placement; acquisition, processing, and economy are not evidenced. | LOW | 75 or 85 | APPROVED |
| Poppy | britannia_mod:poppy | Persistent flower | britannia_mod:poppy_seeds | seed / ordinary Item | 20 | 20 | early decorative perennial | MODERATE - persistent state and reset lifecycle | MEDIUM - hydration .30-.55 ideal; Temperate; Y45-125 ideal | 5 ticks; natural stages 1-6; absolute 7 | 1 flower; reset 1; scissors | No recipe, merchant, or price evidence; decorative role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | Harvested flower can produce seed; other processing unknown | Stage-7 mastery remains a separate post-plant rule | hydration .30-.55 ideal; Temperate; Y45-125 ideal | Ordinary planting separate from stage 7; stage 7 needs Farming 100 plus skinning knife | Ordinary Poppy adds early decorative choice; the stage-7 mastery rule does not raise its seed unlock to 100. | HIGH | 15 or 25 | APPROVED |
| Snowdrop | britannia_mod:snowdrop | Persistent flower | britannia_mod:snowdrop_seeds | seed / ordinary Item | 40 | 40 | lower-mid cool decorative | MODERATE - persistent state and reset lifecycle | MEDIUM-HIGH - hydration .50-.75; Ice; Y55-155 | 7 ticks; 7 stages | 1 flower; reset 1; scissors | No recipe, merchant, or price evidence; decorative role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | Harvested flower can produce seed; other processing unknown | No additional registered risk | hydration .50-.75; Ice; Y55-155 | Persistent colour and stage-1 regrowth | Cool-climate sensitivity and recurring decorative output support midgame placement. | MEDIUM | 35 or 45 | APPROVED |
| Lily | britannia_mod:lily | Persistent flower | britannia_mod:lily_seeds | seed / ordinary Item | 50 | 50 | upper-mid decorative perennial | MODERATE - persistent state and reset lifecycle | MEDIUM - hydration .45-.70; Temperate; Y45-150 | 8 ticks; 7 stages | 1 flower; reset 1; scissors | No recipe, merchant, or price evidence; decorative role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | Harvested flower can produce seed; other processing unknown | No additional registered risk | hydration .45-.70; Temperate; Y45-150 | Persistent colour and stage-1 regrowth | Slower growth and recurring value place it at the midpoint without making all flowers late. | MEDIUM | 45 or 55 | APPROVED |
| Foxglove | britannia_mod:foxglove | Persistent flower | britannia_mod:foxglove_seeds | seed / ordinary Item | 65 | 65 | late decorative/medicinal-themed flower | MODERATE - persistent state and reset lifecycle | MEDIUM-HIGH - hydration .50-.75; Temperate; Y70-180 | 9 ticks; 7 stages | 1 flower; reset 1; scissors | No recipe, merchant, or price evidence; decorative role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | Harvested flower can produce seed; other processing unknown | No additional registered risk | hydration .50-.75; Temperate; Y70-180 | Persistent colour; high-altitude preference | Slow growth and higher-altitude band justify late placement; medicinal use is not treated as confirmed. | MEDIUM | 60 or 70 | APPROVED |
| Campion | britannia_mod:campion | Persistent flower | britannia_mod:campion_seeds | seed / ordinary Item | 10 | 10 | early decorative perennial | MODERATE - persistent state and reset lifecycle | MEDIUM - hydration .45-.70; Temperate; Y45-150 | 6 ticks; 7 stages | 1 flower; reset 1; scissors | No recipe, merchant, or price evidence; decorative role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | Harvested flower can produce seed; other processing unknown | No additional registered risk | hydration .45-.70; Temperate; Y45-150 | Persistent colour and stage-1 regrowth | Fast ordinary flower supplies early decorative and perennial variety. | HIGH | 5 or 15 | APPROVED |
| Hyacinth | britannia_mod:hyacinth | Persistent flower | britannia_mod:hyacinth_seeds | seed / ordinary Item | 30 | 30 | midgame decorative perennial | MODERATE - persistent state and reset lifecycle | MEDIUM - hydration .35-.60; Temperate; Y40-120 | 7 ticks; 7 stages | 1 flower; reset 1; scissors | No recipe, merchant, or price evidence; decorative role is inference | UNKNOWN - initial survival acquisition not demonstrated | Scissors at harvest | Harvested flower can produce seed; other processing unknown | No additional registered risk | hydration .35-.60; Temperate; Y40-120 | Persistent colour and stage-1 regrowth | Moderate timing and ordinary temperate profile fit the first dense midgame band. | MEDIUM | 25 or 35 | APPROVED |
| Orfluer | britannia_mod:orfluer | Persistent flower | britannia_mod:orfluer_seeds | seed / ordinary Item | 95 | 95 | endgame magical decorative | MODERATE - persistent state and reset lifecycle | HIGH - hydration .40-.65; Magical/Ice; Y110-220 | 9 ticks; 7 stages | 1 flower; reset 1; scissors | No recipe, merchant, or price evidence; decorative role is inference | UNKNOWN - acquisition evidence missing; material owner review required | Scissors at harvest | Harvested flower can produce seed; other processing unknown | Fictional placement relies on implemented profile, not botany | hydration .40-.65; Magical/Ice; Y110-220 | Fictional; persistent colour; highland profile | Fictional magical/ice climate, high altitude, and slow growth make a justified capstone; acquisition and economic purpose are undocumented. | LOW | 90 or 100 | APPROVED |

## 6. Band unlock counts and cumulative unlocks

| skill band | new unlock count | cumulative unlock count | species unlocked | category mix |
|---:|---:|---:|---|---|
| 0 | 4 | 4 | Carrot, Lettuce, Green Onion, Wheat | Root, Leaf, Allium, Grain |
| 5 | 5 | 9 | Potato, Vanilla Potato, Peas, Turnips, Radish | Root/tuber, Root/compatibility, Leaf/pulse, Root |
| 10 | 5 | 14 | Squash, Cabbage, Yellow Onion, Rye, Campion | Annual gourd, Leaf/brassica, Allium, Grain, Persistent flower |
| 15 | 4 | 18 | Barley, Oats, Strawberry, Parsnip | Grain, Berry, Root |
| 20 | 5 | 23 | Pumpkin, Garlic, Flax, Snow Peas, Poppy | Annual gourd, Allium/root, Fibre, Leaf/pulse, Persistent flower |
| 25 | 4 | 27 | Beans, Vanilla Pumpkin, Raspberry, Broccoli | Pulse, Gourd/compatibility, Berry, Brassica |
| 30 | 5 | 32 | Corn, Mustard, Blackberry, Cauliflower, Hyacinth | Grain, Grain/seed crop, Berry, Brassica, Persistent flower |
| 35 | 4 | 36 | Blueberry, Cucumbers, Yam, Rutabaga | Berry, Trellis perennial, Root/tuber, Root |
| 40 | 5 | 41 | Tomato, Huckleberry, Cotton, Celery, Snowdrop | Trellis perennial, Berry, Fibre, Stalk, Persistent flower |
| 45 | 4 | 45 | Vanilla Melon, Mulberry, Bell Peppers, Rhubarb | Melon/compatibility, Berry, Trellis perennial, Stalk |
| 50 | 4 | 49 | Watermelon, Elderberry, Hemp, Lily | Annual melon, Berry, Fibre, Persistent flower |
| 55 | 4 | 53 | Brown Mushroom, Apple, Peach, Cantaloupe | Mushroom, Orchard, Trellis perennial |
| 60 | 5 | 58 | Red Mushroom, Cherries, Hops, Pear, Honeydew | Mushroom, Orchard, Trellis perennial |
| 65 | 4 | 62 | Pineapple, Plum, Tobacco, Foxglove | Annual fruit, Orchard, Special leaf, Persistent flower |
| 70 | 4 | 66 | Ginseng, Cranberry, Lemon, Orange | Medicinal, Berry, Orchard |
| 75 | 3 | 69 | Rice, Lime, Olive | Grain, Orchard |
| 80 | 2 | 71 | Banana, Grapes | Other perennial, Vine perennial |
| 85 | 1 | 72 | Nightshade | Magical/medicinal |
| 90 | 1 | 73 | Mandrake | Magical/medicinal |
| 95 | 1 | 74 | Orfluer | Persistent flower |

Band-count total: 74. Final cumulative total: 74.

## 7. Category distribution

Starter (0) has four practical food options across grain, leaf, allium, and root categories. Early game (5-20) adds 19 unlocks spanning roots, pulses, grains, gourds, brassicas, berry fruit, fiber, and two flowers. Lower midgame (25-40) adds 18 unlocks including the first trellis perennials, wet-crop management, tall Corn, berries, fiber, and two more flowers. Upper midgame (45-60) adds 17 unlocks including orchard trees, trellis perennials, mushrooms, melons, fiber, berries, Hops, and Lily. Late game (65-80) adds 13 unlocks across tropical fruit, orchards, wetland crops, medicinal crops, Tobacco, Foxglove, Banana, and Grapes. Endgame (85-95) contains three deliberate specialist unlocks: Nightshade, Mandrake, and Orfluer.

Food remains available in every phase. Fruit begins at 15 with Strawberry, expands through berries/melons, introduces orchards at 55, and culminates in Banana/Grapes at 80. Fiber unlocks at 20, 40, and 50. Decorative flowers span 10, 20, 30, 40, 50, 65, and 95. Medicinal/magical content is concentrated at 70, 85, and 90 because its implemented mechanics are higher tier or special, though recipe value remains unknown. Utility/economic candidates are distributed through Flax, Cotton, Hemp, Hops, Tobacco, orchards, and Grapes rather than reserved for one phase.

## 8. Starter progression

Farming 0 species: Carrot, Lettuce, Green Onion, Wheat.
Farming 5 species: Potato, Vanilla Potato, Peas, Turnips, Radish.

The Farming 0 roster provides practical food, a staple grain, short cycles, ordinary unsupported planting, seed renewal through normal registered crop behavior, and only ordinary harvest tools. Lettuce has high hydration preference, but Carrot, Green Onion, and Wheat prevent hydration from becoming a single starter failure point. No Farming 0 crop requires a trellis, orchard structure, tall clearance, rare climate, darkness, or a higher-level crop input.

Starter planting materials are registered and present in Creative/admin supply, but survival acquisition is not comprehensively demonstrated. Therefore the loop is mechanically sustainable after successful harvest/seed return, while initial survival obtainability remains an explicit limitation rather than an invented acquisition promise.

## 9. Early-game progression

Bands 5-20 add basic roots, compatibility food, pulses, grains, berry fruit, gourds, a brassica, a fiber crop, and Campion/Poppy. This avoids a food-only ladder and gives decorative/perennial exposure before midgame. No early unlock requires a trellis or orchard structure. Garlic introduces root-shovel harvesting; grains introduce grain blades and straw. Poppy ordinary planting at 20 is independent of its Farming-100 stage-7 mastery.

## 10. Midgame progression

Lower and upper midgame (25-60) contain meaningful food, economic/utility candidates, berries, three fiber tiers, wet crops, mushrooms, the first trellis perennials, the first orchard trees, Hops, and three flowers. Cucumbers at 35 and Tomato at 40 introduce recurring trellis output; Bell Peppers, Cantaloupe, Honeydew, and Hops deepen that structure path through 60. Apple and Peach at 55 make orchard introduction a choice; Cherries and Pear follow at 60.

Processing-chain value is not fabricated: grains plus straw and specified produce-to-seed renewal are confirmed, while brewing, fiber manufacture, medicinal crafting, and Tobacco processing remain unknown.

## 11. Late-game progression

Bands 65-80 reserve space for high-tier/slow crops, restrictive environments, renewable orchards, high-hydration Wetland Rice/Cranberry, late utility candidates, medicinal Ginseng, tall recurring Banana, and high-yield recurring Grapes. Not every perennial is late: Tomato and Cucumbers appear at 35-40, and orchards begin at 55.

## 12. Endgame rewards

Nightshade at 85 combines tier 4, darkness, Underground/Magical climate, and maximum-nutrient handling. Mandrake at 90 combines the longest registered cycle, tier 4, maximum nutrients, and magical/cool climate. Orfluer at 95 is a fictional, slow, high-altitude Magical/Ice capstone. No ordinary species is assigned 100 merely because the band exists.

## 13. Flower placement

- Campion 10
- Poppy 20
- Hyacinth 30
- Snowdrop 40
- Lily 50
- Foxglove 65
- Orfluer 95

Flowers are intentionally distributed from early through endgame. Persistent regrowth and fixed colour increase long-term value, while timing, climate, altitude, and fictional status distinguish their bands. Ordinary Poppy identification/planting is approved at 20. Existing owner decision: a stage-6 Poppy requires Farming 100 plus the approved skinning knife to reach stage 7. That rule is unchanged and separate.

## 14. Perennial placement

Perennial/recurring content includes trellis crops, Banana, Grapes, orchard trees, and all flowers. Recurring yield reduces replanting cost but occupies land persistently, can influence public/community plots, and creates renewable economic loops even where prices are unknown. The proposal spreads perennials across multiple phases: flowers start at 10, trellis food at 35, orchards at 55, and the strongest tall/vine output at 80.

Skill reduction affects only future planting under approved DECISION-010. Existing planted perennials keep growing, can be cared for and harvested, and remain governed by independent protection rules.

## 15. Structure-dependent content

Trellis crops: Cucumbers 35, Tomato 40, Bell Peppers 45, Cantaloupe 55, Hops 60, Honeydew 60.
Orchard crops: Apple 55, Peach 55, Cherries 60, Pear 60, Plum 65, Lemon 70, Orange 70, Lime 75, Olive 75.
Tall crops: Corn 30 and Banana 80.
Grape-vine crop: Grapes 80.
Special environment/handling: Celery 40; Brown Mushroom 55; Red Mushroom 60; Cranberry 70; Rice 75; Nightshade 85; Mandrake 90; Orfluer 95.

Structure is not double-counted where slow growth and recurring value already provide friction. Apple/Peach open orchards at 55; Cucumbers open trellises at 35.

## 16. Special planting-material treatment

The requirement belongs to the resolved species, never the item suffix. Seed-named custom Potato/Yam items remain species requirements, produce/mushroom materials use Unidentified Planting Material where applicable, flower seeds use Unidentified Flower Seeds, and all grape varieties resolve to Grapes at 80. Native vanilla crop routes receive no feature behavior under DECISION-012. No currently absent bulb/tuber-item/sapling/cutting/start/spore/root/pit/kernel category is assigned speculative runtime behavior. Future categories require an owner-approved generic name and deterministic mapping before use.

## 17. Economy and recipe considerations

Confirmed repository evidence contains no farming recipes, merchant prices, or complete acquisition economy for this roster. The proposal therefore uses registered yield, recurrence, seed renewal, structure, timing, and environment, while describing food/utility/medicinal/economic roles as gameplay inference. It does not invent prices, brewing, medicine, textiles, or Tobacco processing.

Potential circularity review found no registered crop-dependent trellis/orchard/tool recipe that unlocks after its crop, because those recipe chains are absent. That absence prevents proving circularity; it does not prove the future economy safe. Milestone 14 must not add acquisition or recipe content under this approval gate.

## 18. Tier gaps

Longest interval with no new ordinary unlock: 5 Farming points. Every used band from 0 through 95 is separated by exactly 5. Skill 100 has no ordinary unlock by design because Poppy stage 7 already supplies a distinct mastery action.

Food is continuously available. Fruit begins at 15. Fiber spans 20-50. Flowers span 10-95. Orchards span 55-75. Trellises span 35-60. No long empty early/midgame stretch exists.

## 19. Tier clusters

Largest cluster: 5 species. Bands 5, 10, 20, 30, 40, and 60 each contain five unlocks. These are broad mixed-category clusters, not one-category dumps. Overcrowded bands: none above five. Underused bands: 80 has two; 85, 90, and 95 have one each. Their sparse spacing is intentional endgame specialization.

Bands with a single primary category family occur only in the intentionally sparse endgame bands. Suspicious late-game concentration: none; 71 of 74 species unlock by 80. Suspicious early abundance: 23 unlock by 20, which is deliberate to sustain starter choice but should be owner-reviewed if acquisition proves scarce.

## 20. Circular-progression risks

No confirmed circular dependency is present in registered farming recipes because required structure/tool recipes and downstream crop recipes are not established in the accepted evidence. Risks to revisit are: trellis/orchard construction sources; crop-specific processing machines; harvest-tool acquisition; seed acquisition; and compatibility routes. Grapes at 80 must not become an ingredient required to construct its own vine support. Flax/Cotton/Hemp, Hops, Tobacco, medicinal crops, and orchard processing must be rechecked if recipes are added.

## 21. Low-confidence entries and alternatives

- Rice (rice), recommended 75; alternative 70 or 80. Missing evidence: initial acquisition plus wetland availability and recipe/economy use.
- Ginseng (ginseng), recommended 70; alternative 65 or 75. Missing evidence: initial acquisition plus recipe, economy, and access details.
- Mandrake (mandrake), recommended 90; alternative 85 or 95. Missing evidence: initial acquisition plus recipe, economy, and access details.
- Nightshade (nightshade), recommended 85; alternative 80 or 90. Missing evidence: initial acquisition plus recipe, economy, and access details.
- Hemp (hemp), recommended 50; alternative 45 or 60. Missing evidence: initial acquisition plus recipe, economy, and access details.
- Peas (peas), recommended 5; alternative 10. Missing evidence: initial acquisition plus intended role and recipe/economy use.
- Tobacco (tobacco), recommended 65; alternative 55 or 70. Missing evidence: initial acquisition plus processing, market, and recipe use.
- Grapes (grapes), recommended 80; alternative 75 or 85. Missing evidence: initial acquisition plus processing/economy value and stable variety acquisition.
- Orfluer (britannia_mod:orfluer), recommended 95; alternative 90 or 100. Missing evidence: initial acquisition plus fictional progression/economy purpose.

Confidence does not remove any row. Every LOW row still has a recommended band and at least one alternative.

## 22. Approved behavior matrices

### Unidentified naming

| Material/surface | Approved treatment |
|---|---|
| Ordinary custom crop seeds | Unidentified Seeds |
| Flower seeds | Unidentified Flower Seeds |
| Potato produce and whole mushrooms | Unidentified Planting Material |
| Seed-named Potato/Yam items | Unidentified Seeds because that is the registered material contract |
| Grape varieties | Exact localized name a brown seed while under Farming 80; do not alter the existing grape system |
| Native vanilla crop routes | Outside feature scope; no identification or planting behavior added |
| Bulbs, tubers-as-items, saplings, cuttings, starts, spores, roots, pits, kernels | No active category; decide when registered |
| Shared/ambiguous items | Approved DECISION-011; block unresolved mappings until deterministic species context resolves |

### Requirement disclosure

Approved: passive unidentified names, tooltips, and narration hide the exact minimum. Identified presentation may show the progression requirement. A failed planting attempt gives current and required Farming values in the approved localized action-bar message while retaining the generic material name.

### Bypasses and non-player actors

| Actor/path | Approved policy |
|---|---|
| Survival/Adventure player | Current Farming skill governs |
| Creative player | Identification and planting bypass |
| Operator level 2+ outside Creative | Identification and planting bypass |
| Commands/admin tools | Separate authorized system domain; do not pretend to be item planting |
| Dispenser/null actor | Deny unless a future trustworthy owner contract exists |
| Fake player/machine/script | Use attributed owner skill only when trustworthy; otherwise deny |
| World generation | Separate system domain; unaffected by item identity |

### UI identity exposure

Normal inventory, tooltips, narration, dropped labels, chat links, vanilla Creative inventory/search, open/remote containers, pickup labels, and supported gameplay interfaces must be viewer-correct. Advanced tooltips, registry IDs, commands, data/component inspection, logs, administrative/debug tools, and external recipe viewers are documented best-effort disclosure boundaries. No recipe or recipe-viewer behavior is added because no applicable farming recipes exist. Creative/admin bypass viewers may see identity.

### Skill changes and open state

Approved scope: no special skill-loss or respec workflow is added because Farming does not normally decrease. Identification and planting read the current authoritative skill when evaluated. Skill gain, reconnect, dimension change, and synchronization must refresh viewer-local presentation without mutating the stack; open and remote containers must re-render. Explicitly setting Farming lower is outside this feature's validation scope.

### Existing plantings

Approved: skill reduction does not uproot, pause, hide, or invalidate existing crops. Growth, care, harvest, and perennial reset remain allowed. Only future planting is gated. Flower/crop protection remains independent.

### Shared items

Approved: current mapping is one-to-one. Grapes uses Farming 80 for every variety. Future multi-species items must use deterministic NBT/component context; lowest/highest generic thresholds are rejected because they either leak/bypass or overblock. Unresolved mappings fail closed.

### Vanilla compatibility scope

Approved: the project does not use vanilla crops. Do not add native vanilla planting interception, skill gating, identity behavior, or recipe behavior. Existing compatibility catalog rows and values remain documentation only unless a later owner instruction changes scope.

### Acquisition and economy scope

Approved deferral: acquisition, recipes, merchants, and economy are future-sprint work. They are not part of Milestone 14 implementation authority, and the 74 approved progression values remain unchanged for now.

## 23. Consolidated approved owner decisions

The milestone document's generic list labels final-table approval DECISION-012, but the accepted Milestone 12 discovery already uses DECISION-012 for vanilla compatibility. To preserve established identifiers and avoid a competing numbering system, this proposal retains discovery DECISION-012 and uses its already-reserved DECISION-016 for final-table approval.


### DECISION-001 - Unified threshold

Decision ID: DECISION-001
Status: APPROVED
Category: Unified threshold
Repository finding: Repository has one Farming skill source and no species threshold today.
Recommended answer: APPROVED ANSWER - Use one inclusive threshold for identification and cultivation.
Alternatives: Split thresholds only if a later technical proof requires it.
Reason: Keeps player expectation and data authority coherent.
Implementation consequence: One species field and resolver feed UI and server gate.
Progression consequence: Each approved row has one value.
Blocking or non-blocking: RESOLVED
Documents affected: Proposal; future decision log, schema, UI, gate, tests
Follow-up validation: Boundary tests at requirement-1, requirement, requirement+1.


### DECISION-002 - Generic names by planting material

Decision ID: DECISION-002
Status: APPROVED
Category: Generic names by planting material
Repository finding: Active materials are custom/vanilla seeds, two seed-named tubers, produce Potato, whole mushrooms, grape variety seed, and flower seeds; no bulbs, saplings, cuttings, starts, spores, pits, or kernels are registered.
Recommended answer: APPROVED ANSWER - Use Unidentified Seeds, Unidentified Flower Seeds, and Unidentified Planting Material for produce/mushrooms; do not reveal structure or species.
Alternatives: Use one universal Crop Seeds label; or add future category names only when such materials actually register.
Reason: Category-aware labels avoid calling mushrooms seeds without leaking species.
Implementation consequence: Requires translated categories and a material resolver; no stack mutation.
Progression consequence: No effect on numeric rows; affects comprehension.
Blocking or non-blocking: RESOLVED
Documents affected: Proposal; future localization, UI policy, tests
Follow-up validation: Two-player name tests for every active material class; future material registration test.


### DECISION-003 - Requirement disclosure

Decision ID: DECISION-003
Status: APPROVED
Category: Requirement disclosure
Repository finding: Viewer identity can be hidden while a numeric requirement remains viewer-neutral.
Recommended answer: APPROVED ANSWER - Hide the numeric requirement in passive UI until the species is identified. DECISION-004 separately permits current and required values in feedback after a failed planting attempt.
Alternatives: Hide the number; or show it only after identification.
Reason: Passive inventory presentation must not disclose the threshold before identification, while explicit failure feedback remains actionable.
Implementation consequence: Unidentified names and tooltips omit the threshold; identified presentation may show it; failed planting uses the separately approved action-bar policy.
Progression consequence: Players discover the exact requirement only through identification or an attempted planting failure.
Blocking or non-blocking: RESOLVED
Documents affected: Proposal; future UI/localization/tests
Follow-up validation: Tooltip and narration leak audit below/at threshold.


### DECISION-004 - Insufficient-skill message

Decision ID: DECISION-004
Status: APPROVED
Category: Insufficient-skill message
Repository finding: Denial must occur server-side before mutation, consumption, colour roll, effects, or skill award.
Recommended answer: APPROVED ANSWER - Use a localized action-bar message with current and required Farming; use the generic material name if unidentified.
Alternatives: Generic denial without values; chat message; or no feedback.
Reason: Explains failure while preserving identity.
Implementation consequence: Recipient-specific server component at every planting entry point.
Progression consequence: Makes requirements understandable and testable.
Blocking or non-blocking: RESOLVED
Documents affected: Proposal; future gate/localization/tests
Follow-up validation: Assert exact component plus zero side effects on all paths.


### DECISION-005 - Creative bypass

Decision ID: DECISION-005
Status: APPROVED
Category: Creative bypass
Repository finding: Existing planting uses instabuild for consumption; flower administration recognizes Creative.
Recommended answer: APPROVED ANSWER - Creative bypasses identification and cultivation for testing/admin use.
Alternatives: Bypass planting only; or no bypass.
Reason: Consistent with current administrative workflow.
Implementation consequence: Central policy checks Creative before skill; still runs environmental/protection validation.
Progression consequence: Does not change ordinary-player rows.
Blocking or non-blocking: RESOLVED
Documents affected: Proposal; future gate/UI/tests
Follow-up validation: Creative planting, naming, no-consumption, and environmental-rule tests.


### DECISION-006 - Operator bypass

Decision ID: DECISION-006
Status: APPROVED
Category: Operator bypass
Repository finding: FlowerProtectionService already uses permission level 2, but Poppy mastery has no general bypass.
Recommended answer: APPROVED ANSWER - Reuse the established administrator predicate: Creative or operator permission level 2+. Non-Creative administrators satisfying that predicate bypass identification and cultivation; direct commands remain separate system actions.
Alternatives: Creative-only bypass; higher permission; or no operator bypass.
Reason: Reuses the established administrator predicate.
Implementation consequence: One shared admin policy prevents divergence.
Progression consequence: No ordinary progression effect; admin-only.
Blocking or non-blocking: RESOLVED
Documents affected: Proposal; future gate/UI/tests
Follow-up validation: Permission levels 1/2 boundary tests outside Creative.


### DECISION-007 - Automation and dispensers

Decision ID: DECISION-007
Status: APPROVED
Category: Automation and dispensers
Repository finding: No custom dispenser planting exists; fake/null players lack trustworthy Farming attribution.
Recommended answer: APPROVED ANSWER - Deny unattributed automation; use a trustworthy owner skill only when an owner UUID contract exists; world generation and commands are separate system domains.
Alternatives: Always deny; allow all; or assign fixed automation skill.
Reason: Avoids treating null/fake actors as Farming 100.
Implementation consequence: Planting context must distinguish human, attributed automation, and system mutation.
Progression consequence: Prevents automation bypassing rows.
Blocking or non-blocking: RESOLVED
Documents affected: Proposal; future gate/context/tests
Follow-up validation: Dispenser, fake-player, null actor, owner attribution, command/worldgen tests.


### DECISION-008 - Creative/search/recipe-viewer identity

Decision ID: DECISION-008
Status: APPROVED
Category: Creative/search/recipe-viewer identity
Repository finding: No JEI/REI/EMI integration exists; registry/debug surfaces can reveal IDs.
Recommended answer: APPROVED ANSWER - Cover ordinary inventory, tooltips, narration, dropped labels, chat links, and vanilla Creative inventory/search. External viewers and debug commands are documented best-effort disclosures. No recipe or recipe-viewer behavior is added because the farming catalog has no applicable recipes.
Alternatives: Hide only normal inventory; or attempt unsupported universal secrecy.
Reason: Defines a testable privacy boundary.
Implementation consequence: Multiple client presentation hooks may be required; no persisted custom names and no recipe integration.
Progression consequence: No row change; affects knowledge pacing.
Blocking or non-blocking: RESOLVED
Documents affected: Proposal; future UI/compatibility docs/tests
Follow-up validation: Surface-by-surface two-player leak audit.


### DECISION-009 - Skill loss/respec

Decision ID: DECISION-009
Status: APPROVED
Category: Skill loss/respec
Repository finding: Farming does not normally decrease. The only identified decrease is an explicit administrative/manual operation that sets the value lower.
Recommended answer: APPROVED ANSWER - Do not add a skill-loss or respec workflow in this feature. Explicitly setting Farming lower is outside this prompt. Ordinary identification and planting checks continue to read the current authoritative skill value when evaluated.
Alternatives: Permanent learned identity; or permanent eligibility.
Reason: No normal gameplay skill-loss lifecycle exists to support, migrate, or test.
Implementation consequence: Add no permanent discovery flag, respec listener, loss notification, or special lower-skill transition workflow.
Progression consequence: No separate loss behavior is introduced; manual lowering remains outside the feature scope.
Blocking or non-blocking: RESOLVED
Documents affected: Proposal; future UI/sync/gate/tests
Follow-up validation: Open inventory, remote container, reconnect, dimension, debuff/respec tests.


### DECISION-010 - Existing plantings after skill loss

Decision ID: DECISION-010
Status: APPROVED
Category: Existing plantings after skill loss
Repository finding: Existing plant state has no requirement field and growth/harvest are separate interactions.
Recommended answer: APPROVED ANSWER - Gate only new planting; existing crops continue growing, receiving care, and being harvested. Existing protection rules continue independently.
Alternatives: Block care/harvest; pause growth; or uproot.
Reason: Avoids stranding worlds and community plots.
Implementation consequence: No migration or retrospective checks on planted state.
Progression consequence: Investment remains usable after respec; new planting still blocked.
Blocking or non-blocking: RESOLVED
Documents affected: Proposal; future gate/tests
Follow-up validation: Lose skill with annual, perennial, orchard, and protected flower already planted.


### DECISION-011 - Shared/ambiguous items

Decision ID: DECISION-011
Status: APPROVED
Category: Shared/ambiguous items
Repository finding: Current 74 item IDs map one-to-one to species; grape varieties share one species requirement.
Recommended answer: APPROVED ANSWER - Use the resolved species requirement; all grape varieties use Grapes at Farming 80. Block unresolved multi-species mappings until stack context resolves deterministically.
Alternatives: Lowest mapped value; highest mapped value; or block unresolved mapping.
Reason: Prevents ambiguity and hidden bypasses.
Implementation consequence: Resolver must fail closed on ambiguous mapping and may use NBT/components only as deterministic species context.
Progression consequence: One row remains authoritative per species.
Blocking or non-blocking: RESOLVED
Documents affected: Proposal; future resolver/schema/tests
Follow-up validation: All 74 mappings, every grape variety, unknown component, and future ambiguity fixture.


### DECISION-012 - Vanilla compatibility scope

Decision ID: DECISION-012
Status: APPROVED
Category: Vanilla compatibility scope
Repository finding: Six accepted species retain native vanilla planting routes outside FarmingBlock.
Recommended answer: APPROVED ANSWER - The project does not use vanilla crops. Do not add skill gating, interception, or other feature behavior to native vanilla planting routes. The six repository-discovered compatibility rows remain catalog records and approved values, but native vanilla behavior is outside implementation scope unless separately authorized.
Alternatives: Gate only FarmingBlock; or exclude compatibility species.
Reason: The owner explicitly excludes vanilla crops from the project scope for this feature stream.
Implementation consequence: Do not add targeted native-route interception for potato, wheat, mushrooms, pumpkin, or melon. Do not remove or modify existing compatibility registrations during Milestone 13.
Progression consequence: Approved compatibility values remain documented but do not authorize native vanilla crop support.
Blocking or non-blocking: RESOLVED
Documents affected: Proposal; future gate/tests
Follow-up validation: Each native path at requirement-1/requirement plus non-plant use regression.


### DECISION-013 - Grape persistent custom names

Decision ID: DECISION-013
Status: APPROVED
Category: Grape persistent custom names
Repository finding: Creative grape variants currently persist CUSTOM_NAME and can leak identity across viewers.
Recommended answer: APPROVED ANSWER - Do not touch the existing grape system or migrate its stored names/components. When a player does not meet Grapes' Farming 80 requirement, viewer-facing grape-seed presentation displays the exact localized generic name a brown seed for every grape variety.
Alternatives: Grandfather all names; strip only new stacks; or accept leakage.
Reason: Viewer-local masking preserves the existing grape implementation while hiding variety identity from under-skilled players.
Implementation consequence: Add only the viewer-facing masking needed by the identification layer; do not modify grape variety data, registration, persistence, acquisition, or planting behavior beyond the shared future skill gate.
Progression consequence: Grape remains one requirement at 80.
Blocking or non-blocking: RESOLVED
Documents affected: Proposal; future migration/UI/tests
Follow-up validation: Legacy/new grape stacks for two players and save round trip.


### DECISION-014 - Skill-service outage

Decision ID: DECISION-014
Status: APPROVED
Category: Skill-service outage
Repository finding: Rails load failure currently appears as effective skill zero without an observable loaded state.
Recommended answer: APPROVED ANSWER - Block new planting and show skill data unavailable; never fail open.
Alternatives: Treat as zero with normal message; last-known cache; or fail open.
Reason: Prevents unauthorized planting and misleading balance feedback.
Implementation consequence: SkillManager needs explicit load-state observation for precise messaging.
Progression consequence: Rows remain unchanged during outage.
Blocking or non-blocking: RESOLVED
Documents affected: Proposal; future skill service/gate/localization/tests
Follow-up validation: Login failure, delayed sync, reconnect, and recovery tests.


### DECISION-015 - Incomplete acquisition/economy

Decision ID: DECISION-015
Status: APPROVED
Category: Incomplete acquisition/economy
Repository finding: Initial survival acquisition, recipes, merchants, and economy are absent or incomplete for much of the active roster.
Recommended answer: APPROVED ANSWER - Defer acquisition and economy work to future sprints. Keep all 74 approved progression rows unchanged for this feature stream and do not add acquisition, recipe, merchant, or economy content now.
Alternatives: Defer rows without acquisition; or infer real-world value.
Reason: Preserves complete coverage without inventing content.
Implementation consequence: No acquisition changes in this milestone or implementation unless separately authorized.
Progression consequence: Some values may need revision when actual economy exists.
Blocking or non-blocking: RESOLVED BY DEFERRAL
Documents affected: Proposal; future acquisition/economy docs if authorized
Follow-up validation: Revisit distribution when repository gains recipes/trades/acquisition.


### DECISION-016 - Final progression-table approval

Decision ID: DECISION-016
Status: APPROVED
Category: Final progression-table approval
Repository finding: The proposal contains 74 complete rows, and the owner explicitly answered: Approve all proposed progression rows.
Recommended answer: APPROVED as answered on 2026-08-01; retain the approved bands unless the owner later issues a specific revision.
Alternatives: Approve by category/band; revise individual rows; reject and request rebalance.
Reason: The direct instruction explicitly approves every proposed row and satisfies the row-level approval gate.
Implementation consequence: The progression values are authorized for a later Milestone 14, but implementation remains blocked by unresolved blocking behavior decisions and separate authorization to begin Milestone 14.
Progression consequence: The 74 approved values are now the sole progression-table authority.
Blocking or non-blocking: RESOLVED for progression rows; Milestone 14 remains blocked on behavior decisions.
Documents affected: Proposal; the established decision log may be updated when the remaining decisions are answered without mixing into pre-existing Corrective Milestone 11 work.
Follow-up validation: Continue verifying 74 approved rows and recalculate distribution after any owner revision.


## 24. Explicit approval instructions

Every progression row is APPROVED by direct owner instruction dated 2026-08-01.

DECISION-001 through DECISION-015 are APPROVED. DECISION-015 is resolved by explicit deferral to future sprints.

Silence is not approval.

The Milestone 13 approval gate is satisfied. Milestone 14 cannot begin until the owner gives separate explicit authorization to start it.

Practical response forms:

~~~text
Revise approved progression rows: <IDs and new bands>

Approve decision <ID>: <selected option>

Revise species <registry_id> from <old band> to <new band>

Reject decision <ID>: <reason>
~~~

The owner's direct progression instruction approves all 74 rows. Subsequent direct answers approve DECISION-001 through DECISION-015, including the native-vanilla exclusion, grape masking exception, and future-sprint acquisition/economy deferral.

## 25. Hard stop

Milestone 13 is design-only and remains uncommitted. All progression rows and behavior decisions are approved. No production behavior, tests, resources, registrations, localization, data, saves, networking, or Milestone 14 work has begun.

Status: Milestone 13 approval gate complete; awaiting explicit Milestone 14 authorization.
