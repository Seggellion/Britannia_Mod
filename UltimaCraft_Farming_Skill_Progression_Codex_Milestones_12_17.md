# UltimaCraft Farming System
## Codex Milestones 12–17 — Crop Discovery, Farming-Skill Progression, and Seed Identification

**Target branch:** `farming`  
**Milestone type:** Post-launch feature expansion  
**Scope:** Complete farming-content inventory, progression design, planting eligibility, and player-specific seed identification

---

# 1. Feature Objective

Add a complete Farming-skill progression across **every plantable crop and flower currently supported by the new UltimaCraft farming system**.

The progression must not be created from memory or from a partial crop list. Codex must first discover the complete implemented catalog from registrations, species definitions, planting logic, data, assets, and tests.

After discovery, every applicable plantable species receives an owner-approved minimum Farming skill.

The current intended rule is one unified threshold:

```text
player Farming skill >= species minimum Farming skill
```

That threshold controls both:

1. whether the player can identify the seed or planting item; and
2. whether the player can plant and cultivate that species.

When a player is below the requirement:

- the item displays the generic player-facing name `Crop Seeds`;
- the normal species identity is hidden from ordinary item names and tooltips;
- planting fails safely;
- the item is not consumed;
- the target block is not changed;
- no crop or flower state is initialized;
- no flower colour is rolled;
- no planting experience, statistic, or advancement credit is awarded; and
- the server returns approved localized feedback.

When the player meets the requirement:

- the actual species-specific name is revealed; and
- planting may proceed through every existing environmental, structural, item, and protection rule.

Reuse the same authoritative Farming-skill system already used by Poppy stage 7. Do not create a second Farming-skill store.

---

# 2. Existing Requirements That Remain Active

This feature must preserve all existing farming and flower behavior, including:

- all work remains on the existing `farming` branch;
- tilling or preparing soil does not create a flower block;
- a `FarmingBlock` becomes a flower block only after successful use of a valid flower seed;
- flower colour is selected once at successful planting and persists;
- flowers reuse the existing hydration, nutrient, region, altitude, quality, and growth systems;
- perennial flowers return to stage 1 while retaining species and colour;
- Adventure-mode sword cutback remains intact;
- admin/Creative-planted flower protection remains intact;
- Poppy grows naturally only to stage 6;
- Poppy stage 7 requires Farming 100 plus the approved skinning knife;
- every flower species/stage uses one canonical multi-plane model;
- that model uses exactly two in-world PNG textures: fixed `base_texture` and grayscale tinted `dye_mask`; and
- no separate dye-mask stage model is permitted.

Poppy's Farming 100 stage-7 requirement is separate from the lower or equal requirement used to identify and plant ordinary Poppy seeds.

---

# 3. Interpretation Rules

## 3.1 Discovery before balance

No skill values may be implemented until Codex has:

1. discovered the complete plantable catalog;
2. documented relevant gameplay and technical attributes;
3. proposed a complete progression table;
4. asked material owner questions;
5. received owner approval or corrections; and
6. recorded the final approved values.

## 3.2 Complete farming content

Discovery must inspect every farming-related category in the repository, including where applicable:

- annual field crops;
- perennial and regrowing crops;
- grains, vegetables, herbs, and root crops;
- fiber and medicinal crops;
- trellis and tall crops;
- berries;
- orchard or fruit-tree crops;
- flowers;
- fictional or special plants;
- bulbs, tubers, cuttings, starts, saplings, spores, or other planting materials;
- compatibility or legacy crops still plantable through the new system; and
- code-driven and data-driven species.

The flower inventory must include at least:

```text
Poppy
Snowdrop
Lily
Foxglove
Campion
Hyacinth
Orfluer
```

Use repository registry IDs and established project spelling. Do not silently rename `Orfluer`.

## 3.3 One threshold by default

The current owner requirement links identification and cultivation. The default data model is one field equivalent to:

```text
minimum_farming_skill
```

Do not split identification and cultivation into separate requirements unless repository constraints make the unified behavior impractical or the owner explicitly approves a split.

## 3.4 Skill range and comparison

Use the repository's actual Farming-skill representation. Verify whether it is integer, decimal, fixed-point, or another form.

If the established scale is 0–100, proposed values must stay within that inclusive range.

Eligibility is inclusive:

```text
player_farming_skill >= minimum_farming_skill
```

## 3.5 Player-specific identity

Identity depends on the viewing or using player. Two players with different Farming skills may see different player-facing names for the same underlying item type.

Do not mutate the stack or convert it to another item merely to change its displayed identity. Registry IDs and saved item data remain stable.

The requirement applies to ordinary player-facing inventory names, hover text, interaction messages, narration, and supported gameplay interfaces. Administrative IDs or debug data that cannot reasonably be hidden must be documented.

## 3.6 No accidental item loss

On an insufficient-skill planting attempt:

- do not decrement the stack;
- do not damage an item;
- do not convert a `FarmingBlock`;
- do not place a crop or flower block;
- do not create a block entity;
- do not initialize plant state;
- do not roll flower colour;
- do not grant Farming experience; and
- do not play successful planting effects.

---

# 4. Branch and Repository Safety

At the beginning of every milestone, run and report:

```bash
git rev-parse --show-toplevel
git branch --show-current
git status --short --branch
git log -5 --oneline
```

The active branch must be exactly:

```text
farming
```

If it is not, stop and report the mismatch.

Do not create or switch branches, merge, rebase, reset history, push, delete unrelated files, use destructive cleanup, or commit unless the owner explicitly authorizes a commit.

Read all applicable root and nested guidance, including `AGENTS.md`, `CLAUDE.md`, `README.md`, and contribution instructions.

Also read all existing farming and flower project documents in the repository root, including the design document, original playbook, Corrective Milestone 11, discovery reports, decision logs, implementation-status files, test matrices, and asset manifests.

---

# 5. Milestone 12 — Complete Farming Catalog and Skill-System Discovery

## 5.1 Purpose

Produce a repository-grounded inventory of every applicable plantable crop and flower and trace all technical integration points.

This milestone is discovery only.

Do not assign balance values. Do not modify production code, registrations, resources, models, textures, localization, or tests.

## 5.2 Registration and content discovery

Trace every route by which plantable content enters the farming system:

- block and block-entity registrations;
- item, seed, bulb, tuber, cutting, and sapling registrations;
- species definitions and codecs;
- custom registries;
- tags;
- JSON data and data generators;
- recipes and loot tables;
- creative tabs;
- planting interaction maps;
- world generation;
- NPC or shop content;
- compatibility layers; and
- test fixtures.

Follow actual registration and planting call chains. Do not rely on filenames alone.

## 5.3 Required catalog fields

For every discovered species, record:

```text
display_name
registry_id
category
fictional_or_real
seed_or_planting_item_id
planting_material_type
crop_or_flower_block_id
block_entity_type
definition_or_registry_source
growth_stage_count
natural_max_stage
special_stage_rules
annual_or_perennial
regrows_after_harvest
harvest_item_ids
seed_recovery
base_yield
growth_time_or_tick_profile
hydration_attributes
nutrient_attributes
regional_attributes
altitude_attributes
other_environmental_rules
quality_integration
market_or_recipe_uses
special_tools_or_structures
protection_rules
existing_skill_requirement
player_facing_name_source
tooltip_source
planting_entry_point
test_coverage
notes
```

Use `N/A` when a field does not apply and `UNKNOWN` when it cannot be confirmed. Cite inspected repository paths and symbols.

## 5.4 Farming-skill discovery

Trace the existing Farming skill end to end:

- authoritative storage;
- server-side query API;
- client synchronization;
- numeric range and precision;
- level-change events;
- Poppy stage-7 skill check;
- existing planting checks;
- skill gain sources;
- current skill-dependent UI;
- Creative/admin behavior;
- fake-player and automation behavior;
- dedicated-server safety; and
- test utilities.

Identify the exact repository-consistent API later milestones must reuse.

## 5.5 Player-specific naming discovery

Determine how the current Minecraft/NeoForge version and repository architecture can support a display name based on the viewing player.

Investigate:

- item name methods;
- hover-text methods and available context;
- synchronized player skill data;
- item components;
- translation components;
- creative inventory and search;
- containers and remote inventories;
- dropped item labels;
- narration/accessibility;
- recipe-viewer integrations such as JEI, REI, or EMI, if installed;
- localization conventions; and
- resource/client lifecycle.

If the normal item-name API lacks player context, evaluate at least two viable approaches. Assess correctness for two players with different skills, client/server authority, synchronization, stale-name risk, localization, compatibility, maintainability, and performance.

Do not implement an approach during discovery.

## 5.6 Planting-path discovery

Trace all applicable planting paths, including where present:

- item use on `FarmingBlock`;
- use on other soils;
- direct block-item placement;
- trellises;
- orchard plots;
- multi-block structures;
- dispensers and automation;
- NPCs;
- custom GUIs;
- commands; and
- world generation.

Identify the earliest safe server-authoritative point where the skill check can occur before any state mutation or item consumption.

If no shared point exists, map the required check for every path.

## 5.7 Deliverables

Create or update only:

```text
FARMING_SKILL_PROGRESSION_DISCOVERY_REPORT.md
FARMING_CONTENT_MASTER_CATALOG.md
```

The master catalog must include a complete Markdown table and a machine-editable representation using the repository's preferred format. If no format exists, include a fenced CSV section.

The discovery report must include:

1. branch and repository baseline;
2. guidance and project documents reviewed;
3. content-registration map;
4. total species count by category;
5. complete planting-item count;
6. species found through special or non-obvious paths;
7. Farming-skill architecture;
8. player-specific naming options;
9. planting-gate integration points;
10. localization and tooltip architecture;
11. Creative/admin and automation behavior;
12. compatibility integrations;
13. testing architecture;
14. likely files affected by Milestones 14–17;
15. risks and unknowns; and
16. consolidated owner questions.

## 5.8 Hard stop

After discovery:

- report the total catalog count;
- list every category and species;
- identify unknown or ambiguous entries;
- ask all material owner questions in one consolidated response;
- state that no balance values or implementation changes were made; and
- stop.

Do not proceed automatically to Milestone 13.

## 5.9 Acceptance criteria

Milestone 12 is complete only when:

- all actual planting registration paths were inspected;
- every applicable species appears exactly once in the catalog;
- every planting item maps to a species or is documented as shared/ambiguous;
- all seven flowers are included;
- Farming-skill retrieval is traced;
- player-specific naming options are documented;
- planting-gate points are documented;
- unknowns are clearly marked; and
- no progression values or production changes were made.

---

# 6. Milestone 13 — Progression Design and Owner Approval Gate

## 6.1 Purpose

Use the approved complete catalog to propose a fair Farming-skill progression across the entire implemented roster.

This milestone is design only. Do not enforce requirements in code.

## 6.2 Progression principles

The proposal should:

- use the full available skill range without forcing every integer to be occupied;
- provide meaningful unlocks throughout early, middle, and late progression;
- preserve useful starter crops;
- include multiple choices at most progression bands;
- distribute food, utility, economic, medicinal, fiber, fruit, and decorative options;
- account for perennial and regrowing value;
- account for structure-dependent crops;
- account for growth time, yield, processing chains, and market/recipe value;
- account for broad versus narrow environmental tolerance;
- reserve high requirements for meaningful complexity, value, rarity, risk, or special handling;
- avoid using real-world botanical difficulty as the sole factor;
- avoid making all flowers late-game simply because they are decorative;
- keep Poppy stage 7 separate; and
- avoid circular progression where a required progression resource unlocks too late.

At least one practical food crop must be available at the starting Farming value.

## 6.3 Balancing framework

Before assigning values, propose a transparent advisory framework that considers:

```text
cultivation_complexity
environmental_sensitivity
growth_duration
yield_and_regrowth_value
economic_or_recipe_value
rarity_or_access
special_structure_or_tool_requirement
processing_chain_value
risk_or_special_handling
progression_role
```

Do not present a mathematical score as objective truth. Explain intentional exceptions.

## 6.4 Unlock bands

If repository discovery confirms a 0–100 scale, prefer readable bands such as:

```text
0, 5, 10, 15, 20, 25, 30, 35, 40, 45,
50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100
```

A sparser or denser distribution may be proposed when justified by the actual catalog size.

Do not assign fake precision merely to make every species unique.

## 6.5 Required proposal

Create:

```text
FARMING_SKILL_PROGRESSION_PROPOSAL.md
```

For every applicable catalog entry, include:

```text
species
registry_id
category
proposed_minimum_farming_skill
unlock_band
progression_role
difficulty_factors
economy_or_recipe_factors
environmental_factors
special_rules
rationale
confidence
owner_status
```

Initial `owner_status` is `PROPOSED`.

Also report:

- unlock count by band;
- cumulative unlocks by band;
- early/mid/late category distribution;
- gaps with no unlocks;
- overcrowded bands;
- starter experience;
- midgame variety;
- endgame rewards;
- flower placement;
- perennial placement;
- special planting-material treatment; and
- alternative placement for each low-confidence entry.

## 6.6 Required owner questions

Ask only questions not answered by repository evidence or existing decisions. Include a recommendation and implementation consequence for each.

Potential questions include:

- Do Creative players bypass cultivation requirements?
- Do authorized admins or operators bypass them outside Creative mode?
- Do unidentified flower seeds also display `Crop Seeds`, or `Flower Seeds`?
- Do bulbs, tubers, cuttings, and saplings use `Crop Seeds` or category-specific names?
- Is the exact required skill visible before identification?
- Does failed planting reveal the exact requirement?
- May recipe viewers or Creative search reveal identity?
- Is identity based on current skill or permanently learned knowledge?
- What happens after skill loss or respec?
- Can a player interact with an already-planted crop after falling below its requirement?
- How do shared or ambiguous planting items work?
- How are dispensers and automated planting treated?

## 6.7 Approval gate

The owner must approve every applicable progression row and every blocking behavior decision before implementation.

After feedback:

1. update affected rows;
2. mark approved rows `APPROVED`;
3. record decisions in the established decision log;
4. recalculate coverage and band distribution; and
5. stop for explicit authorization to begin Milestone 14.

Silence is not approval.

## 6.8 Acceptance criteria

- Every catalog species has a proposed value.
- Every proposal has a rationale.
- Distribution is analyzed.
- Owner questions are consolidated.
- Every applicable row is explicitly approved or revised.
- Final approved values are the sole implementation authority.
- Production behavior is unchanged.

---

# 7. Milestone 14 — Data Model and Registration Integration

## 7.1 Purpose

Add the approved requirement to the authoritative species-definition architecture without yet changing planting or player-facing item identity.

## 7.2 Source of truth

Add one repository-consistent field equivalent to:

```text
minimumFarmingSkill
```

Attach it to the authoritative species or cultivation definition.

Do not duplicate the table independently in seed items, crop blocks, flower blocks, tooltip code, planting handlers, localization, and tests.

Every consumer must resolve the same source of truth.

## 7.3 Coverage and compatibility

Every owner-approved applicable species must have an explicit value.

Do not silently use a permissive production default for missing species. A legacy deserialization fallback is permitted only when needed for save compatibility and must be documented and tested.

Poppy's stage-7 Farming 100 rule remains a separate special interaction.

## 7.4 Validation

Add validation that fails when:

- a plantable species lacks a requirement;
- a value is out of range;
- definitions conflict;
- a planting item maps to a missing species;
- an approved progression row has no implementation entry; or
- an implementation entry has no approved row.

## 7.5 Deliverables and hard stop

Create or update:

```text
FARMING_SKILL_PROGRESSION_IMPLEMENTATION_STATUS.md
FARMING_SKILL_PROGRESSION_TEST_MATRIX.md
```

Update the established project decision log with the final schema and compatibility behavior.

Compile and validate data, demonstrate complete coverage, confirm gameplay is not yet gated, provide the closeout report, and stop.

## 7.6 Acceptance criteria

- One authoritative field exists.
- Every applicable species has an approved value.
- No duplicate hardcoded progression table exists.
- Save compatibility is preserved or documented.
- Poppy stage 7 remains separate.
- Compilation and data validation pass.
- Seed names and planting behavior remain unchanged.

---

# 8. Milestone 15 — Server-Authoritative Cultivation Gate

## 8.1 Purpose

Prevent players from planting species for which they do not meet the approved minimum Farming skill.

## 8.2 Required behavior

At the earliest safe server-authoritative point, compare the authoritative player skill to the authoritative species requirement.

If insufficient:

- prevent fallback placement;
- do not consume the item;
- do not mutate the target;
- do not create a block entity;
- do not initialize species state;
- do not roll flower colour;
- do not trigger successful effects, statistics, or advancements;
- do not grant Farming experience; and
- send approved localized feedback.

If sufficient, continue through the unchanged existing planting-validation pipeline.

Meeting the skill requirement never bypasses soil, hydration, nutrients, region, altitude, structure, protection, or other planting rules.

## 8.3 All planting paths

Gate every applicable player planting route found in Milestone 12. Do not secure only direct use on `FarmingBlock` if other systems can plant the same content.

For automation and non-player planting, implement the owner-approved policy.

## 8.4 Authority and synchronization

The server is authoritative. Client prediction must not permit planting or item loss when client and server values differ.

Test repeated clicks, both hands, latency, skill changes, and open containers.

## 8.5 Acceptance criteria

- Every applicable planting path is gated.
- Failure never consumes the planting item.
- Failure never mutates the world.
- Flower colour is not rolled on failure.
- Success still obeys all existing restrictions.
- Only approved bypasses work.
- Multiplayer behavior is server-authoritative.

---

# 9. Milestone 16 — Player-Specific Seed Identification and UI

## 9.1 Purpose

Hide species identity from players who cannot cultivate it and reveal the identity once they meet the approved requirement.

## 9.2 Unidentified display

For an under-skilled viewer, use the approved localized generic name. The current default is:

```text
Crop Seeds
```

If the owner approves category-specific names in Milestone 13, use those exact names.

Do not mutate the stack or convert it to a different item for display purposes.

## 9.3 Identified display

For a qualified viewer, show the existing localized species-specific name.

Do not duplicate species names in code when translations or registry-backed components are authoritative.

## 9.4 Prevent ordinary UI identity leaks

An under-skilled player must not learn the species through ordinary:

- item names;
- tooltips;
- flavor text;
- environmental details unique to the species;
- harvest-product names;
- colour palettes;
- special-mechanic text;
- search labels;
- recipe usages;
- narration; or
- supported compatibility UI.

Follow the owner-approved policy for requirement values, advanced tooltips, Creative inventory, and recipe viewers.

Document unavoidable administrative or debug disclosures.

## 9.5 Skill updates

Identity must update correctly after skill changes without permanently caching viewer-dependent text on the stack.

Test:

- gaining the threshold with inventory open;
- skill loss/respec if supported;
- reconnecting;
- changing dimensions;
- server restart;
- remote containers;
- item pickup;
- dropped item labels;
- Creative inventory and search;
- recipe viewers; and
- two players with different skill levels.

## 9.6 Localization and accessibility

Add repository-consistent localized components for the generic name, failure feedback, and any approved tooltip text.

Narration and accessibility labels must obey the same identification rule as visual text.

## 9.7 Acceptance criteria

- Under-skilled players see only the approved generic identity in ordinary gameplay UI.
- Qualified players see the correct species identity.
- Two players can see different presentation for the same underlying item type.
- Registry IDs and saved stacks do not change.
- Names update after skill changes.
- Tooltips do not leak unapproved identity.
- Localization and narration are correct.
- Client skill synchronization is reliable.
- Dedicated server does not load client-only UI classes.

---

# 10. Milestone 17 — Progression Validation, Gameplay QA, and Closeout

## 10.1 Automated coverage

Add or update tests proving that:

- every applicable catalog species has an approved requirement;
- every planting item resolves a species and requirement;
- approved rows and implementation entries match exactly;
- values are in range;
- comparisons are inclusive;
- Poppy stage 7 remains separate;
- insufficient planting is side-effect free;
- sufficient planting still obeys existing rules;
- identification state matches cultivation eligibility;
- player-specific display does not mutate stacks;
- protected flowers remain protected;
- flower colour persistence is unchanged; and
- pre-existing farming and flower tests still pass.

## 10.2 Progression report

Generate a final report showing:

- unlocks and cumulative unlocks by skill band;
- category diversity by band;
- food, utility, medicinal, fiber, fruit, economic, and decorative availability;
- longest unlock gap;
- largest cluster;
- starter, midgame, and endgame choices;
- flowers and perennials by band;
- structure-dependent species by band; and
- species with special post-planting mechanics.

Flag suspicious distributions. Do not silently rebalance approved values.

## 10.3 Manual boundary testing

Test representative species at:

```text
requirement - 1
requirement
requirement + 1
```

Use repository-appropriate decimal boundaries if skills are not integers.

Include at least:

- a starter crop;
- a low-tier flower;
- a mid-tier annual;
- a perennial;
- a trellis or structure crop;
- an orchard crop if present;
- a high-tier crop;
- ordinary Poppy planting;
- Poppy stage 7;
- Creative/admin behavior;
- protected flowers;
- multiplayer players with different skills;
- skill gain while inventory is open; and
- failed planting with a one-item stack.

## 10.4 Regression scope

Confirm no regressions to:

- FarmingBlock preparation;
- crop planting;
- flower seed conversion;
- hydration, nutrients, region, altitude, and quality;
- growth and harvesting;
- perennial regrowth;
- sword cutback;
- hoe and shovel behavior;
- admin protection;
- flower colour selection and persistence;
- one-model/two-texture flower rendering;
- item stacking;
- recipes and loot;
- data generation;
- dedicated server; and
- existing saves.

## 10.5 Final documents

Finalize:

```text
FARMING_CONTENT_MASTER_CATALOG.md
FARMING_SKILL_PROGRESSION_PROPOSAL.md
FARMING_SKILL_PROGRESSION_IMPLEMENTATION_STATUS.md
FARMING_SKILL_PROGRESSION_TEST_MATRIX.md
```

Document final values, schema, UI behavior, planting behavior, bypass policy, unavoidable identity disclosures, test commands, QA evidence, and deferred balancing observations.

## 10.6 Acceptance criteria

The feature is complete only when:

- catalog coverage is complete;
- every value is owner-approved;
- every applicable species is gated;
- identification and cultivation eligibility agree;
- failure is side-effect free;
- multiplayer presentation works;
- progression distribution has been reviewed;
- automated and manual tests pass;
- existing farming and flower invariants remain intact; and
- documentation matches implementation.

---

# 11. Required Owner Decisions

Record approved answers for at least:

```text
DECISION-001  Unified identification/cultivation threshold
DECISION-002  Generic unidentified name by planting-material category
DECISION-003  Requirement visibility before identification
DECISION-004  Insufficient-skill message
DECISION-005  Creative-mode bypass
DECISION-006  Admin/operator bypass
DECISION-007  Automation and dispenser behavior
DECISION-008  Recipe-viewer and Creative-inventory identity policy
DECISION-009  Skill-loss or respec behavior
DECISION-010  Existing planted-crop behavior after skill loss
DECISION-011  Shared or ambiguous planting-item handling
DECISION-012  Final progression-table approval
```

Use the repository's established decision-log format. Do not create a competing decision system.

---

# 12. Milestone Closeout Format

Every milestone must end with:

```text
Milestone:
Branch and HEAD before work:
Branch and HEAD after work:
Working tree before:
Working tree after:

Documents reviewed:
Files inspected:
Files added:
Files modified:
Files removed:

Confirmed findings:
Implementation summary:
Catalog coverage:
Progression coverage:
Tests added or updated:
Commands run:
Results:
Manual QA:
Known limitations:
Risks:
Deferred work:
Owner decisions required:
Suggested commit message:
```

Do not commit unless explicitly authorized.

Suggested informational commit messages:

```text
docs(farming): inventory plantable content and skill integration points
docs(farming): propose crop skill progression
feat(farming): add species cultivation requirements
feat(farming): gate planting by farming skill
feat(farming): reveal seed identity by farming skill
test(farming): validate skill progression and seed identification
```

---

# 13. Initial Codex Prompt

Use this prompt to begin:

> Read all repository guidance and every farming and flower project document in the project root, including `UltimaCraft_Farming_Skill_Progression_Codex_Milestones_12_17.md`. Work only on the existing `farming` branch. Execute **Milestone 12 only**. Do not assign balance values and do not modify production code or resources. Discover every plantable crop, flower, tree, berry, trellis plant, perennial, special plant, and planting material integrated with the new farming system. Trace the authoritative Farming-skill API, every planting path, and viable player-specific item-naming approaches. Create the required complete master catalog and discovery report with repository-relative evidence. Ask all material owner questions in one consolidated response, provide the milestone closeout report, and stop. Do not proceed to Milestone 13 without explicit authorization.
