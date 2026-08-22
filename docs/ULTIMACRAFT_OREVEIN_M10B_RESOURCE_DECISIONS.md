# UltimaCraft OreVein — Milestone 10B
## Missing Vanilla Replacement Resource Design Decisions

**Date:** 2026-08-21
**Type:** Design / owner-decision milestone. **No implementation performed.**
**Branch:** `claude/ultimacraft-orevein-remediation-70bfeb`
**M10A commit:** **`263f9b3f`** — `feat(resources): add natural iron gold and copper generation`

---

## Objective

Produce an implementation-ready decision record for the five denied vanilla economic families with
no managed replacement — coal, redstone, diamond, lapis, emerald — so that the next milestone can be
handed over without another broad investigation.

**The headline finding changes the shape of the question.** This was framed as "five resources need
designing". The repository says otherwise:

> **UltimaCraft's mining ladder is an Ultima Online ladder, and four of these five are Minecraft
> materials with no place in it. Two of them — diamond and emerald — already exist in Britannia
> under different names, as UO gems, consumed by 202 blacksmithing recipes and supplied by nothing.**

So the real work is not "design five ore veins". It is one genuine mining resource (coal), one
pre-existing unsupplied gem economy (diamond/emerald), and two materials with no demonstrated role
at all (redstone, lapis).

---

## Starting Git state

| | |
|---|---|
| Branch | `claude/ultimacraft-orevein-remediation-70bfeb` |
| HEAD | **`263f9b3f`** (M10A) |
| Worktree | **clean** — 0 changes; this milestone was read-only |
| Pushed | no — no upstream configured |
| Merged into `patch-18` | no — `patch-18` remains `c2bc44f3` |
| Stash | 1 entry, unrelated (`shrines-monoliths`), untouched |

### M10A regression state carried forward

- JUnit **2,989 run / 5 known failures / 17 skipped**
- GameTests **738 run / 2 known failures**
- No deterministic new regression.

---

## Current Mining progression

`mineables.json` is the single source of truth (26 rows). `required_mining` is the inclusive access
gate; `challenge` is the skill-gain difficulty and is currently equal to it for every row.

| Resource | Family | Req | Shape | Radius | Yield | Regen | Natural |
|---|---|---:|---|---|---|---|---|
| iron | ore | **0** | vertical | 1–128 | purity_ore | 6h | **YES** |
| stone / cobblestone | stone | 0 | — | — | graded_stone | 6h | — |
| calcite, sandstone | stone | 5 | — | — | graded_stone | 6h | — |
| diorite | stone | 10 | — | — | graded_stone | 6h | — |
| andesite | stone | 15 | — | — | graded_stone | 6h | — |
| granite | stone | 20 | — | — | graded_stone | 6h | — |
| tuff | stone | 25 | — | — | graded_stone | 6h | — |
| deepslate, cobbled_deepslate, metamorphic_rock | stone | 30 | — | — | graded_stone | 6h | — |
| glacial_rock, dripstone | stone | 35 | — | — | graded_stone | 6h | — |
| basalt, igneous_rock | stone | 40 | — | — | graded_stone | 6h | — |
| blackstone, volcanic_rock | stone | 45 | — | — | graded_stone | 6h | — |
| **silver** | ore | **55** | vertical_layered | 1–96 | purity_ore | 6h | — |
| **tin** | ore | **65** | **layered** | 1–96 | purity_ore | 6h | — |
| **shadow_iron** | ore | **70** | vertical | 1–128 | purity_ore | 6h | — |
| **copper** | ore | **75** | cluster | 1–22 | purity_ore | 6h | **YES** |
| **gold** | ore | **85** | snake | 10–128 | purity_ore | 6h | **YES** |
| **agapite** | ore | **90** | geode | 3–16 | purity_ore | 6h | — |
| **verite** | ore | **95** | cluster | 1–22 | purity_ore | 6h | — |
| **valorite** | ore | **99** | vertical | 1–128 | purity_ore | 6h | — |
| silica_sand_deposit | sediment | n/a | sedimentary_lens | 8–13 | item | **24h** | **YES** |

**Fact:** the nine `ore` rows are exactly the Ultima Online metal progression (iron, dull-copper-as-
silver, tin, shadow iron, copper, gold, agapite, verite, valorite). The `stone` rows occupy 0–45 and
the metals 55–99, leaving a deliberate gap at 46–54.

**Fact — the governing precedent.** `mineables.json`'s own comment records:

> *"Owner decision 2026-08-14: exactly one Silver metal/ore exists, and **Obsidian is not a Mining
> resource (not an Ultima Online material)**."*

An identical question has therefore already been answered once, and answered by exclusion.

**Evidence limitation:** `docs/mining/MINING_SKILL_DESIGN.md` and
`docs/mining/MINING_IMPLEMENTATION_LOG.md` are cited by that comment but are **not present in either
checkout**. Sections 7/8 of the design doc define the requirement/challenge semantics and could not
be read. Where that matters below it is marked.

---

## Current economy / resource usage evidence

### What the economy recognises

`CommodityMappings.SUPPORTED_ORE_COMMODITIES` is exactly the nine UO metals — the set Rails seeds
under `ore/raw`. `STONE_COMMODITY_FAMILIES` maps 18 stone commodities to Rails subcategories
(`rubble`, `common`, `igneous`, `volcanic`, `sedimentary`, `metamorphic`, `mineral`).

**None of coal, redstone, diamond, lapis or emerald appears anywhere in the `economy` package.**

Useful precedent: `quartz` is mapped to the `mineral` subcategory but has **no** `mineables.json`
row — a commodity mapping can exist without a mining route, and a `mineral` subcategory already
exists in Rails for a non-metal.

### Usage of the five across the whole mod

| Material | Recipes | Economy | Traders | Loot | Java (non-test) | Verdict |
|---|---|---|---|---|---|---|
| **coal** | none | none | none | none | `PopulateOresCommand` (withdrawal notice only) | **withdrawn, awaiting a definition** |
| **redstone** | none | none | none | none | **none — zero mentions anywhere** | **no role** |
| **diamond** | none | none | none | none | one item tag: `grain_harvest_blades` | **tools removed; gem exists separately** |
| **lapis** | none | none | none | none | **none — zero mentions anywhere** | **no role** |
| **emerald** | none | none | none | none | none (test fixtures only) | **gem exists separately** |

Diamond and emerald otherwise appear only inside banking/trader **GameTests**, as arbitrary stacks
chosen to exercise deposit routing — not as economic content.

### The finding that reframes diamond and emerald

`blacksmithing/craftables.json` (202 recipes) consumes a full **Ultima Online gem set**:

| Gem key | Recipe uses |
|---|---|
| `blue_diamond` | 9 |
| `brilliant_amber` | 5 |
| `dark_sapphire` | 5 |
| `ecru_citrine` | 4 |
| `perfect_emerald` | 4 |
| `fire_ruby` | 4 |
| `white_pearl` | 4 |
| `amethyst` | 1 |
| `tourmaline` | 1 |

These are auto-registered as plain `britannia_mod:<key>` items by
`BlacksmithItemRegistry.populate()`, which registers any ingredient key it does not already map.
`en_us.json` carries proper display names ("Blue Diamond", "Perfect Emerald", "Fire Ruby"), and
`ItemRegistry` separately registers `star_sapphire`, `ruby`, `citrine`, `amber` alongside the UO
reagent set (bat wing, daemon blood, nox crystal, grave dust, pig iron). Item textures exist for
amber, amethyst, black_pearl, citrine, ruby, star_sapphire, tourmaline.

> **`britannia_mod:blue_diamond` is Britannia's diamond. `britannia_mod:perfect_emerald` is
> Britannia's emerald. Neither is `minecraft:diamond` or `minecraft:emerald`.**

**And they have no acquisition route.** Searching every loot table, trader definition, drop handler
and Java reference finds the gems **only** as craft inputs — nothing in this repository produces
one. Nine gem types feed 202 recipes and the supply is zero.

*Limitation:* Rails-backed vendors carry 707 seeded retail rows which cannot be read from this
repository. Gems may be purchasable there. That is checkable in one Rails query and is listed as an
owner question, not asserted either way.

### Vanilla gameplay loops — does suppression break anything?

- **No fuel mechanic exists in the mod.** No `fuel`, `burnTime` or `isFuel` reference anywhere;
  `LargeForgeBlockEntity` consumes no fuel. Britannia's own smelting is fuel-free. Coal's only fuel
  role is the **vanilla** furnace, for which **charcoal is a complete and renewable substitute**.
- **Vanilla diamond tools were deliberately removed.** `data/minecraft/recipes/diamond_pickaxe.json`
  is an all-`minecraft:air` recipe-disabling stub, added by commit `23767084` **"#6 Removed Diamond
  tools"**.
- **That removal is almost certainly inert.** The file sits in `data/minecraft/**recipes**/`
  (plural). Minecraft 1.21 singularised these directories, and this repository's own recipes live in
  `data/britannia_mod/**recipe**/` (singular, 3 files) with `loot_table/`, `advancement/` and
  `tags/block/` likewise singular. The plural directory is not read by the 1.21.1 loader, so the
  diamond pickaxe recipe is still craftable today. **Owner intent is unambiguous; the mechanism has
  silently stopped working.** Reported, not fixed — M10B performs no implementation.

---

## Coal

### Facts

- Coal **was** a Britannia ore type. `ore_veins.json`, the curated Rails vein fixture, still carries
  `{"oreType": "coal", "x": 80, "y": -50, "z": 100, "radius": 50}` — one of ten rows, alongside the
  nine ladder metals.
- M0 recorded that its legacy placement used the **Layered** shape and placed `minecraft:coal_ore`.
- M1 **withdrew** it. `PopulateOresCommand`'s own documentation states the reason: it placed
  `minecraft:coal_ore`, *"which the Mining catalogue does not govern, so it broke with vanilla drops,
  no requirement and no restoration. A managed generation route must not manufacture unmanaged
  economic material."*
- A live GameTest, `coalIsNoLongerPlaceableByTheLegacyCommand`, asserts coal stays out
  **"until it has a canonical resource definition"** and pins the placeable set at exactly 10.
- Coal has **no** `mineables.json` row and **no** `resources.json` entry.
- 2 vanilla features suppressed: `ore_coal_upper`, `ore_coal_lower`.

**Coal is the only one of the five the project has already decided it wants.** The condition for its
return was written down, tested, and is still waiting. Rails is holding a vein for it.

### Recommendations

| Question | Recommendation | Basis |
|---|---|---|
| Managed identity | **Custom managed block**, `britannia_mod:coal_ore` | Matches the 8 existing custom ore blocks (`copper_ore`, `tin_ore`, …), all `BaseOreBlock`, no block item, no loot table. Naming follows `<material>_ore`, not `_deposit` — `_deposit` is the sediment family's convention (clay, silica). |
| Output | **`yield.mode: item` → `minecraft:coal`, count 1** | The smallest change that preserves torches, vanilla fuel and every vanilla coal recipe. `purity_ore` would produce a `PurityOreItem` that no existing recipe accepts, breaking torches for no gain. Coal is not a UO metal and has no purity concept. |
| Mining requirement | **Range 0–15, preferred `10`** | Coal is a starter utility, not a progression prize. Iron sits at 0 and the stone ladder runs 0–45; 10 places coal beside diorite — trivially reachable but not free. **Requires the unavailable design doc to confirm requirement/challenge semantics; treat as provisional.** |
| Category | `ore`, or a new `mineral` category | `ore` currently means "UO metal" and coal is not one. Rails already has a `mineral` stone subcategory (quartz). **See owner question 3.** |
| Shape | **`layered`** | Repository precedent (M0 records Layered for legacy coal) *and* correct geology — `LayeredPlanner` is documented as *"a bed: a broad, thin, horizontal seam, richest in the middle and petering out at the margins."* Shared with tin, which is normal here (vertical is shared by three resources). |
| Regeneration | **6h default** | Coal is a bulk utility material; the sensitivity that earned silica 24h does not apply. |
| Host | `britannia_mod:ore_hosts` | Same stone/deepslate replaceables as every other ore. |
| Distribution | Shallow-to-mid, broad, common — the **most** common of any managed resource | Vanilla coal is the most abundant ore and it is a starter fuel. Numbers deliberately not proposed. |

### `OWNER DECISION` — Coal

1. **Final Mining requirement** (recommend `10`, range 0–15).
2. **Category** — reuse `ore`, or introduce `mineral`.
3. **Rails commodity + price** — a `coal` commodity must be seeded, or coal ships with no buyer.

---

## Redstone

### Facts

- **Zero mentions in the entire repository** — no recipe, no item, no tag, no economy entry, no
  test, no Java reference. Of the five it is the only one with literally nothing.
- No Ultima Online analogue exists; UO has no redstone-equivalent material.
- M0 recorded that legacy `/populateores redstone` returned 0 — it never had a placement route.
- 2 vanilla features suppressed: `ore_redstone`, `ore_redstone_lower`.

### Recommendations

| Question | Recommendation |
|---|---|
| Managed identity | **None — do not create a managed redstone resource** |
| Output | n/a |
| Mining requirement | n/a |
| Shape | n/a |
| Regeneration | n/a |
| Natural supply | **No geological replacement** |

Creating a redstone deposit would mean inventing a Mining requirement, a commodity and a price for a
material the mod has never referenced, in a ladder whose governing precedent already excluded
obsidian for exactly this reason.

**The real question is not geology, it is whether redstone machinery belongs in Britannia at all.**
Suppression removes redstone *ore* from new chunks; it does not remove redstone dust from vanilla
mob drops (witches) or chest loot, so redstone likely remains obtainable in small quantities without
any ore. Whether that is a deliberate "redstone is trace-only" position or an accident should be
stated rather than inherited.

### `OWNER DECISION` — Redstone

4. **Does redstone machinery belong in UltimaCraft?**
   - Recommended: **no managed resource**; accept redstone as trace-only from non-ore vanilla routes.
   - Alternative: keep vanilla redstone ore *unsuppressed* — legitimate, and cheaper than building a
     managed replacement for a material with no demonstrated demand.

---

## Diamond

### Facts

- **Britannia's diamond already exists and is not `minecraft:diamond`.** It is
  `britannia_mod:blue_diamond`, the Ultima Online gem, used in **9** blacksmithing recipes and
  registered with the display name "Blue Diamond".
- Vanilla diamond **tools were deliberately removed** by commit `23767084` "#6 Removed Diamond
  tools" — though that removal is inert (see above), so vanilla diamond gear is currently still
  craftable.
- `minecraft:diamond`'s only real mod reference is the `grain_harvest_blades` item tag — a
  *tool-material* tag, not an economic one.
- M0 recorded legacy `/populateores diamond` returned 0; it never had a placement route.
- 4 vanilla features suppressed — the most stratified family: `ore_diamond`, `ore_diamond_medium`,
  `ore_diamond_large`, `ore_diamond_buried`.

### Recommendations

| Question | Recommendation |
|---|---|
| Managed identity | **None for vanilla diamond.** Do not create a managed `minecraft:diamond` ore. |
| Output | If a gem source is built, it should yield **`britannia_mod:blue_diamond`**, not `minecraft:diamond` |
| Mining requirement | n/a for a vanilla-diamond resource |
| Shape | n/a (if gems ever become a mining byproduct, they need no shape — see below) |
| Regeneration | n/a |
| Natural supply | **No geological replacement for vanilla diamond** |

A managed vanilla-diamond ore would re-supply exactly the gear progression the owner has already
tried to delete, and would sit alongside `blue_diamond` as a second, competing diamond.

**Do not assign a Mining requirement of 100.** The ladder tops out at valorite 99, and inserting a
non-UO material above the UO endgame would invert the progression's meaning.

**On renewable diamond.** Any renewable high-value deposit on the platform's 6-hour cycle is an
infinite faucet limited only by player time — its long-run price is set by respawn rate, not by
scarcity. That is fine for iron and coal and dangerous for gems. **This is an economic design
decision, not numeric tuning:** no radius or rarity value makes a 6-hour renewable diamond
economically equivalent to a finite one.

### `OWNER DECISION` — Diamond

5. **Confirm vanilla diamond has no managed geological replacement** (recommended: confirm), and
   **confirm the intent behind "#6 Removed Diamond tools"** — the stub is currently inert, so vanilla
   diamond gear is still obtainable.

---

## Lapis

### Facts

- **Zero mentions in the entire repository** — identical to redstone.
- No UO analogue.
- Vanilla role is enchanting. **No enchanting-table, `EnchantmentMenu` or enchanting reference exists
  anywhere in the mod**; Britannia advances characters through its own skill system.
- M0 recorded legacy `/populateores lapis` returned 0.
- 2 vanilla features suppressed: `ore_lapis`, `ore_lapis_buried`.

### Recommendation

**Lapis should remain suppressed and not be replaced.** The milestone brief explicitly allowed this
outcome, and lapis is its clearest instance: it has no UltimaCraft gameplay function, no economic
demand, no crafting use and no thematic place. Creating a managed lapis resource would be creating a
resource because Minecraft has one.

The one caveat is vanilla enchanting: if players are expected to use vanilla enchanting tables, lapis
is required for them. Nothing in the repository indicates they are.

### `OWNER DECISION` — Lapis

6. **Is vanilla enchanting a supported UltimaCraft activity?**
   - If **no** (recommended, and what the evidence suggests): lapis stays suppressed with no
     replacement, permanently.
   - If **yes**: lapis needs a supply route, though a trader row would serve better than an ore vein.

---

## Emerald

### Facts

- **Britannia's emerald already exists**: `britannia_mod:perfect_emerald`, the UO gem, used in **4**
  blacksmithing recipes including `emerald_mace` (Blacksmithy 75) — and displayed as
  "Perfect Emerald".
- `minecraft:emerald` appears **only** in GameTest fixtures as an arbitrary deposit-routing item.
- Vanilla emerald is villager currency. Britannia has its **own** Rails-backed economy with a coin
  currency, banking and 31 active vendors. A second currency-like item conflicts conceptually.
- 1 vanilla feature suppressed: `ore_emerald`.

### Recommendation

Of the three legitimate possibilities in the brief, the evidence supports **option 3: vanilla
emerald ore suppressed with no natural replacement**, because emerald already enters Britannia
through a different channel — as `perfect_emerald`, a blacksmithing gem, not as currency.

Option 1 (renewable geological emerald) carries the same faucet problem as diamond *plus* a currency
conflict. Option 2 (tightly controlled rare resource) is option 1 with smaller numbers and does not
address either objection.

**This is prominently a game-design decision, not an engineering one**, and it is the same decision
as diamond's: whether UO gems ever become mineable.

### `OWNER DECISION` — Emerald

7. Covered by question 8 below (the gem-supply question), plus confirmation that vanilla emerald has
   no geological replacement (recommended: confirm).

---

## The question underneath diamond and emerald

**Nine UO gem types feed 202 blacksmithing recipes, and nothing in this mod produces a single one.**

That is a live content gap independent of ore suppression, and it is where "diamond" and "emerald"
actually live in Britannia. In Ultima Online, gems come from **mining as a byproduct** and from
monster loot and treasure chests — not from dedicated gem veins. A faithful implementation would
therefore be a *drop rule on existing mining*, not five new deposit types, and would need **no new
shape, no new distribution, and no `natural` block at all**.

### `OWNER DECISION` — Gems

8. **How do UO gems enter the world?** Recommended: a low-probability byproduct of managed ore
   extraction (UO-faithful, reuses the existing extraction path, no new geology), with rarer gems
   weighted to higher-requirement ores. Alternatives: monster/treasure loot; Rails vendor purchase
   only; or leave unsupplied.
9. **Do Rails vendors already sell gems?** One query settles it and it changes the priority of
   question 8. Not answerable from this repository.

---

## Decision matrix

*Recommendations, not facts, except where the Basis column cites repository evidence.*

| Resource | Managed identity recommendation | Output | Mining requirement | Shape | Regen | Natural supply? | Main owner decision |
|---|---|---|---:|---|---|---|---|
| **Coal** | **Custom block** `britannia_mod:coal_ore` (fact: matches 8 existing) | `minecraft:coal` via `item` mode | **10** (range 0–15) | **`layered`** (fact: M0 precedent + planner doc) | 6h default | **YES — the only one** | Requirement + category + Rails price |
| **Redstone** | **None** | n/a | n/a | n/a | n/a | **No** | Does redstone belong in UltimaCraft at all? |
| **Diamond** | **None** for vanilla diamond; Britannia's is `blue_diamond` (fact) | — | n/a | n/a | n/a | **No** | Confirm no replacement; confirm tool-removal intent |
| **Lapis** | **None** | n/a | n/a | n/a | n/a | **No** | Is vanilla enchanting supported? |
| **Emerald** | **None** for vanilla emerald; Britannia's is `perfect_emerald` (fact) | — | n/a | n/a | n/a | **No** | How do UO gems enter the world? |

**One of five needs a geological resource.** Four need a decision recorded, not a deposit built.

---

## Regeneration / economic risk

| Resource | Recommendation |
|---|---|
| Coal | **6h default.** Bulk utility; no sensitivity. |
| Redstone / Lapis | n/a — no managed resource proposed. |
| Diamond / Emerald | **Do not put gems on a 6-hour cycle without an explicit decision.** |

The platform default is 6h (27 of 28 resources); silica is the sole override at 24h, and its
justification was volume, not value. No third timing value should be introduced casually.

**The structural point for high-value materials:** a renewable deposit's long-run supply is set by
its respawn interval, not its rarity. Making a deposit rarer reduces how *often* a player finds one;
it does not reduce how much a *found* deposit yields per day forever. For iron and coal that is the
intended behaviour. For a gem it converts a prestige material into an annuity. If gems ever become
mineable, the correct lever is drop *probability per extraction*, not deposit regeneration — which
is a further argument for the byproduct model in question 8.

---

## Asset / content requirements

Template established by silica and the eight custom ore blocks. A new managed ore block needs:

| Item | Needed | Note |
|---|---|---|
| Block registration | yes | `BaseOreBlock::new`, as `copper_ore` etc. |
| Block texture | yes | `textures/block/<id>.png` — 7 ore textures exist as reference |
| Blockstate + block model | yes | 1 file each |
| Item model | **no** | Managed ore blocks have **no block item** |
| **Loot table** | **NO — deliberately** | Fact: zero loot tables exist for any ore block. Managed extraction bypasses loot tables entirely; adding one would create exactly the unmanaged economic route M1 removed coal to prevent. |
| Localization | yes | 1 `en_us.json` line |
| Creative tab | no | Ore blocks are not tab-registered (no block item) |
| Extraction tag | **reuse** `britannia_mod:mining_pickaxes` | Used by 26 of 28 resources |
| Host tag | **reuse** `britannia_mod:ore_hosts` | Used by every ore |
| Biome tag | only if biome-restricted | Coal: not recommended — Overworld-wide |

**On visual identity:** a distinct texture is preferable to referencing `minecraft:coal_ore`'s. The
legacy vanilla block must stay visually distinguishable from the managed one, because after cutover
they behave differently — one is economic, one is decorative. Every existing managed ore has its own
texture; coal should too.

**Only coal needs assets.** The other four need none under these recommendations.

---

## Legacy-world policy

Confirmed direction, unchanged: **existing vanilla blocks in old chunks must not silently become
managed deposits.** M10A's `isNewChunk()` guard structurally guarantees no retro-population, and M8's
retrofit tooling remains the deliberate migration path.

| Resource | Recommended legacy behaviour |
|---|---|
| **Coal** | **Decorative / non-economic.** Legacy `minecraft:coal_ore` breaks with ordinary vanilla behaviour and yields vanilla coal, but is *not* a managed deposit: no requirement, no skill, no restoration, no commodity. Consistent with M6's ambient-rock exemption, which already distinguishes managed cells from vanilla material by namespace. |
| Redstone, Diamond, Lapis, Emerald | **Ordinary vanilla behaviour.** With no managed identity there is nothing to distinguish them from, so no policy is required. Legacy chunks keep working; new chunks simply have none. |

A subtlety worth stating: **for coal the legacy and managed blocks would be different blocks**
(`minecraft:coal_ore` vs `britannia_mod:coal_ore`), so "decorative" needs no runtime discrimination
— it is automatic. That is a further argument for the custom-block recommendation.

---

## Production supply strategy

The gate rule is that every intentionally denied family needs an **approved supply strategy**, not
necessarily another ore vein.

| Family | Suppressed features | Supply strategy after M10B decisions |
|---|---|---|
| iron | 3 | **Managed natural generation** (M10A) ✔ |
| copper | 2 | **Managed natural generation** (M10A) ✔ |
| gold | 2 (+1 badlands) | **Managed natural generation** (M10A) ✔ |
| **coal** | 2 | **Managed natural generation** — to be implemented |
| **redstone** | 2 | **Intentionally none** — trace-only via non-ore vanilla routes *(needs decision 4)* |
| **diamond** | 4 | **Intentionally none** — Britannia's diamond is `blue_diamond` *(needs decision 8)* |
| **lapis** | 2 | **Intentionally none** — no gameplay function *(needs decision 6)* |
| **emerald** | 1 | **Intentionally none** — Britannia's emerald is `perfect_emerald` *(needs decision 8)* |

**Four of the five can reach completeness by decision alone.** Only coal requires engineering.

One caveat that should be verified before the gate moves rather than assumed: suppression removes
*ore features*, not *items*. Redstone, lapis, diamond and emerald remain reachable via vanilla mob
drops, chest loot and villager trades unless those are separately disabled. That likely *helps* —
trace availability without an economic faucet — but it should be measured, not presumed.

---

## Implementation complexity

| Resource | Block | Item | mineables | resources | Extraction tag | Natural | Tags | Assets | Economy | Tests | **Class** |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **Coal** | new | reuse `minecraft:coal` | 1 row | 1 entry | reuse | 1 block | reuse both | texture + 2 models | new Rails commodity | lifecycle + distribution | **SMALL CONTENT** |
| Redstone | — | — | — | — | — | — | — | — | — | 1 guard test | **OWNER DESIGN REQUIRED** |
| Diamond | — | — | — | — | — | — | — | — | — | 1 guard test | **OWNER DESIGN REQUIRED** |
| Lapis | — | — | — | — | — | — | — | — | — | 1 guard test | **OWNER DESIGN REQUIRED** |
| Emerald | — | — | — | — | — | — | — | — | — | 1 guard test | **OWNER DESIGN REQUIRED** |

"1 guard test" means a test asserting the family stays absent — cheap, and it converts a silent
omission into a stated policy.

Coal is **SMALL CONTENT** rather than DATA ONLY solely because of the block registration and its
texture. Everything else reuses existing tags and infrastructure.

---

## Recommended milestone decomposition

The example grouping in the brief (M11 coal, M12 redstone+lapis, M13 diamond+emerald) assumed five
comparable resources. The evidence does not support that: four of the five need no implementation
milestone at all.

### M11 — Coal, and the replacement-completeness closure

One milestone, because the four no-replacement families are a *data-and-test* task once decided, not
a content task, and splitting them across three milestones would triple the review overhead for
work that is mostly assertions.

- Implement coal end-to-end: block, texture, `mineables.json`, `resources.json`, natural generation,
  Rails commodity, lifecycle and distribution tests — the M10A pattern, now proven.
- Record the four no-replacement decisions as guard tests and a policy note.
- Re-run the replacement-completeness matrix: **8 of 8 families with an approved strategy.**
- Fix the inert `data/minecraft/recipes/` → `recipe/` directory if decision 5 confirms the intent.

**Outcome: the production gate's *replacement* precondition is met.**

### M12 — UO gem supply

Independent of ore suppression and valuable regardless: 202 recipes currently cannot be crafted.
Scope set by decisions 8 and 9. Should follow M11 because it is content design, not a gate blocker.

### M13 — Production suppression rollout

Enable the M5/M8.5 suppression set, live QA, rollback rehearsal. Gated on M11, not on M12.

### M14 — Britannia ore natural-generation backlog

The six below, as one data milestone.

---

## Britannia-specific natural-generation backlog

Verified: each of the six already has a full managed identity **and** a `generation` block with a
shape, radius range and host tag. **Each lacks only a `natural` block** — the same one-object data
edit M10A applied three times.

| Resource | Req | Shape | Radius | Host | Missing |
|---|---|---|---|---|---|
| silver | 55 | vertical_layered | 1–96 | ore_hosts | `natural` only |
| tin | 65 | layered | 1–96 | ore_hosts | `natural` only |
| shadow_iron | 70 | vertical | 1–128 | ore_hosts | `natural` only |
| agapite | 90 | geode | 3–16 | ore_hosts | `natural` only |
| verite | 95 | cluster | 1–22 | ore_hosts | `natural` only |
| valorite | 99 | vertical | 1–128 | ore_hosts | `natural` only |

Confirmed **DATA ONLY**, and confirmed **not blocking** the production gate — none is a suppressed
vanilla family. Two cautions carried from M9/M10A for whoever picks this up:

- `verite` uses **cluster**, the shape whose radius-22 configuration plans **15,152 cells**. It must
  get a measured natural radius, exactly as copper did — not the curated value.
- `shadow_iron` and `valorite` use **vertical**, whose radius is a column *height*, so their search
  window will overshoot. Keep the natural radius small enough to stay inside the cost budget.

---

## Production gate

**Status: BLOCKED.** What remains, by category:

### Engineering missing
1. **Coal**: managed resource, end-to-end (M11). *The only engineering on the critical path.*
2. Guard tests recording the four no-replacement decisions (M11).
3. The inert `data/minecraft/recipes/` stub, if decision 5 confirms intent (M11).

### Owner / design missing
4. Coal's Mining requirement, category, and commodity identity *(questions 1–3)*.
5. Redstone's place in UltimaCraft *(question 4)*.
6. Vanilla enchanting support, which determines lapis *(question 6)*.
7. Confirmation that diamond and emerald get no geological replacement *(questions 5, 7)*.

### External Rails action
8. Seed a `coal` commodity with a price *(question 3)*.
9. Confirm whether vendors already sell UO gems *(question 9)*.
10. Carried forward: **live agapite Rails radius `55 → 8`**. Note the repository fixture
    `ore_veins.json` already records agapite at radius 8; the outstanding change is to the **live**
    Rails row, unverifiable from here.

### Balancing-only follow-up
11. M10A copper natural balancing — provisionally accepted at radius 8–10.
12. Gold's 5.5% altitude rejection (band tops at y=90 against depth 85).
13. Silver/tin **KEEP** recommendation at radius 35 (~1,220 cells) — no action.
14. Known flakes: 5 JUnit (asset/report hash), 2 GameTest (housing plot, GrapeArbor legacy vine).
    Uninvestigated by standing instruction.

**The gate can move to READY after M11**, provided questions 1–9 are answered. It does **not** depend
on M12 (gems) or M14 (Britannia ore backlog).

---

## Questions for owner

1. **Coal Mining requirement?** → Recommend **10** (range 0–15).
2. **Coal category:** reuse `ore`, or add `mineral`? → Recommend **`mineral`**; `ore` currently means
   "UO metal" and coal is not one.
3. **Coal Rails commodity and price?** → `OWNER DECISION: price/economic valuation`. Coal can ship
   with no buyer — it would be mineable and usable but unsellable, which is survivable but odd for a
   bulk material.
4. **Does redstone belong in UltimaCraft?** → Recommend **no managed resource**; accept trace-only
   supply from non-ore vanilla routes.
5. **Confirm vanilla diamond gets no managed replacement** → Recommend **confirm**. Also: the
   "#6 Removed Diamond tools" stub is **inert** — should vanilla diamond gear actually be removed?
6. **Is vanilla enchanting a supported activity?** → Evidence says no. If confirmed, **lapis stays
   suppressed permanently with no replacement.**
7. **Confirm vanilla emerald gets no managed replacement** → Recommend **confirm**; Britannia's
   emerald is `perfect_emerald`.
8. **How do UO gems enter the world?** → Recommend a **low-probability byproduct of managed ore
   extraction**, weighted by ore tier. 202 recipes currently have zero supply.
9. **Do Rails vendors already sell gems?** → One query; changes the urgency of question 8.

---

## Git state

**No implementation work was performed.**

- Worktree **clean** — `git status --porcelain` returns 0 entries.
- HEAD **`263f9b3f`** (M10A), the only commit made this session.
- No block registered, no `resources.json` or `mineables.json` edit, no natural distribution, no
  texture, no commodity mapping, no suppression change, no Mining requirement change, no trader
  change, no Rails change.
- Nothing pushed; no upstream. `patch-18` untouched at `c2bc44f3`. Stash untouched.
- This report is written to the main checkout's `docs/`, outside the worktree, per the established
  convention — so the code worktree stays clean and nothing needs committing for M10B.

---

## Recommendation

**Next implementation milestone: M11 — Coal, and the replacement-completeness closure.**

Answer questions 1–7 and M11 can start immediately; it is a well-understood repeat of M10A's pattern
against a resource the project already decided it wants, whose shape is already on record, whose
curated Rails vein already exists, and whose return condition — *"until it has a canonical resource
definition"* — is already written into a passing test.

Questions 8 and 9 do not block M11 and should be answered before M12.

The most valuable outcome of this milestone is the reframing: **four of the five "missing resources"
should not be built.** Building them would have added five Minecraft materials to an Ultima Online
progression that had already excluded obsidian on precisely that principle — and would have created
a second diamond and a second emerald alongside the ones Britannia already has.
