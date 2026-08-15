# Mining Progression Gap Analysis (Milestone 1)

Date: 2026-08-14 · Branch `patch-18` · HEAD `40fa27d2`

Classification vocabulary (per playbook §1.8):
`COMPLETE` · `PARTIAL` · `MISSING` · `DUPLICATE/CONFLICT` · `NOT APPLICABLE` ·
`OWNER/ARCHITECTURE DECISION REQUIRED` · `UNKNOWN` where unproven.

A resource is COMPLETE only if every layer it needs (block, item, assets, refining, worldgen
path, economy identity, restoration) exists and is coherent. **No resource is fully COMPLETE
against the approved progression because the Mining gate/award layer does not exist at all** —
that global gap is scored once in §3 rather than repeated per row.

---

## 1. Metals (approved ladder)

| Material | Req | Classification | What exists | What is missing / conflicted |
|---|---:|---|---|---|
| Iron | 0.0 | PARTIAL | Vanilla ore + deepslate form managed; `VerticalVein`; 133 seeded veins; vanilla ingot; `ore/raw/iron` + `metal/ingots/iron` | Sale identity mismatch E1 (all ores); no gate/award |
| Silver | 55.0 | **COMPLETE (M5)** — one cosmetic gap | Block+item+assets+lang; gated at 55.0; managed break → `PurityOreItem("Silver ore")` → forge → existing `silver_ingot`/`UOMetalToolMaterial.SILVER`; restoration incl. save/load proven; **24 vein rows now seeded** (Rails `db/seeds/silver_ore_veins.rb`, idempotent, `/populateores silver`); economy resolves to the seeded `ore/raw/silver` identity; ingot + mined-ore lang added; 8 unit + 5 GameTests | Ingot art is still the iron-ingot placeholder shared by all 7 custom ingots (M6 asset work). Rails seed is **uncommitted** and its DB was unreachable, so live vein/commodity state stays unverified until an operator runs the seed |
| Tin (replaces Dull Copper) | 65.0 | PARTIAL | Full vertical slice: block+item+assets+lang, ingot+model, `TIN` material, `LayeredVein`, 47 seeded veins, Rails ore+ingot rows | Placeholder ingot texture; no ingot lang; E1; no gate/award |
| Shadow Iron | 70.0 | PARTIAL | Block+item+assets+lang, ingot, `SHADOW_IRON`, `VerticalVein` wired, Rails rows | 0 seeded veins; placeholder ingot texture; E1 |
| Copper | 75.0 | PARTIAL | Block+item+assets+lang, ingot, `COPPER`, `ClusterVein`, 1 seeded vein, Rails rows | Effectively 1 vein; placeholder ingot texture; E1 |
| Bronze | (80.0 cond.) | OWNER/ARCHITECTURE DECISION REQUIRED — **as a mineable: NOT APPLICABLE** | Rails `metal/ingots/bronze` commodity only; cosmetic `bronze_shield` name | Repository model is **refined-metal-only, no ore** (no bronze ore commodity, no block, no ingot item, no alloy recipe). Per design §6.3: do **not** create Bronze ore. Open owner question (outside Mining scope): should a Copper+Tin alloy route ever produce bronze ingots to back the existing commodity? Mining M2–M6 proceed without Bronze. |
| Gold | 85.0 | DUPLICATE/CONFLICT | Vanilla `minecraft:gold_ore` (+deepslate) managed, `SnakeVein` places vanilla gold; **and** custom `britannia_mod:gold_ore` registered (lang only, no assets, no placement path) — two gold ore identities | Resolve in M2/M6: retire or complete the asset-less custom block; both currently resolve to `GOLD`/vanilla ingot so drops are coherent, but the duplicate block invites divergence |
| Agapite | 90.0 | PARTIAL | Block+item+assets+lang, ingot, `AGAPITE`, `GeodeVein` wired, Rails rows | 0 seeded veins; placeholder ingot texture; E1 |
| Verite | 95.0 | PARTIAL | Block+item+assets+lang, ingot, `VERITE`, `ClusterVein` wired, Rails rows | 0 seeded veins; placeholder ingot texture; E1 |
| Valorite | 99.0 | PARTIAL | Block+item+assets+lang, ingot, `VALORITE`, `VerticalVein` wired, Rails rows | 0 seeded veins; placeholder ingot texture; E1 |
| High-Purity Silver | UNKNOWN | DUPLICATE/CONFLICT + OWNER DECISION | Block+item+lang registered; managed by mining rules; Rails: nothing | No assets at all; unrefinable (name matches no material); `/populateores` accepts the type but generates 0; no commodity. Is this an intended premium silver node (define tier + fix refine/commodity) or vestigial (retire)? Silver economy identity must not be duplicated either way. |
| Dull Copper | — | NOT APPLICABLE (by owner decision) | Vestigial unused map entry only | Do not add; Tin holds 65.0 |
| GM Mining | 100.0 | MISSING | Rails Mining skill max_value 100.0 | Cap exists; no mastery hook (future expansion per design §7.1) |

## 2. Rocks (proposed §8 baseline vs repository)

| Rock | Proposed req | Classification | Notes |
|---|---:|---|---|
| Stone | 0.0 | PARTIAL | Managed; sells as **cobblestone** (E3) — `stone` commodity never supplied by mining |
| Natural Cobblestone | 0.0 | PARTIAL | Managed; no provenance model exists, so "naturally generated" cannot currently be distinguished (design §8 note depends on M-later provenance decision) |
| Limestone | 5.0 | PARTIAL (via alias) | No limestone block; **Calcite is the limestone source** (`deduceStoneType` alias). Keep alias; do not add a block unless owner wants one |
| Calcite | 5.0 | PARTIAL | Managed; sells as limestone (intended alias E5) |
| Diorite | 10.0 | PARTIAL | Managed + sellable; needs only gate/award |
| Andesite | 15.0 | PARTIAL | Managed + sellable |
| Granite | 20.0 | PARTIAL | Managed + sellable (ordinary rock — distinct from UO High Quality Granite, design §8.1) |
| Tuff | 25.0 | PARTIAL | Managed + sellable |
| Deepslate | 30.0 | PARTIAL + CONFLICT | Managed; **no stone commodity mapping → unsellable** (E4) |
| Cobbled Deepslate | 30.0? | PARTIAL + UNKNOWN | Managed today; unsellable; whether it belongs in the progression is an M2 catalog decision |
| Dripstone Block | 35.0 | MISSING (from managed set) | Exists in game; not in `PickaxeMiningRules`, no economy identity |
| Basalt / Smooth Basalt | 40.0 | PARTIAL | Both managed; both sell as `basalt` |
| Blackstone | 45.0 | PARTIAL + CONFLICT | Managed; drops as `"Blackrock"` which maps to **no commodity** (E4) — rename or map in M2/M8 |
| Quartz-bearing rock | 50.0 | NOT APPLICABLE (currently) | No Overworld quartz block; `quartz` commodity exists; owner may add a source later |
| Obsidian | 60.0 | MISSING (from managed set) | Vanilla rules intact; adding requires managed-set membership without overriding tool rules |
| Igneous/Metamorphic/Volcanic/Glacial Rock | data-driven | PARTIAL + CONFLICT | Managed with full assets; **no commodity mapping → unsellable** (E4); no placement path found (UNKNOWN how they enter the world) |

## 3. Systems

| System | Classification | Evidence / gap |
|---|---|---|
| Mining break interception | COMPLETE (as a hook point) | `CustomBlockBreakHandler` @ `BlockEvent.BreakEvent`; earliest safe denial point identified (discovery §1.5) |
| Mining skill gate | **COMPLETE (M3)** | `mining/MiningBreakGate` (FarmingCultivationGate-shaped policy core) + `MiningGateHandler` at `BlockEvent.BreakEvent`, `EventPriority.HIGH` — server-authoritative, covers every player break whatever the tool, inclusive threshold, creative/op(2) bypass via server game mode, fake-player default deny, skill-unavailable deny. Denial proven side-effect-free by GameTests. Vanilla-tool breaks by *sufficiently skilled* players still bypass the managed drop/restore flow (unchanged M1 finding; M6/M7 scope). |
| Mining skill award | **COMPLETE (M4)** | `mining/MiningSkill.awardForBreak` at the managed flow's success boundary → `SkillManager.awardSkillGain(…, 0.1f)`; per-material `challenge` feeds a Mining-only roll; gate re-check excludes bypass/automation/unavailable/unmanaged by construction; `(player,pos,tick)` dedup. Calibrated to **18,000** activations (−0.00 % vs target) — see [MINING_SKILL_CALIBRATION_REPORT.md](MINING_SKILL_CALIBRATION_REPORT.md) |
| Skill engine (shared) | COMPLETE | `SkillManager` + Rails persistence + sync + states + trainer; reuse as-is |
| Guildmaster Mining training | COMPLETE | `miner` guild teaches Mining; purchase flow is skill-agnostic and unchanged by M4 (commits in Rails, reaches the mod via `applyConfirmedValue`, never rolls). Its 40.0 ceiling replaces 1,231 field activations (6.8 % of the journey) |
| Restoration engine | COMPLETE (functional) with known limitations | 6h wall-clock, Overworld-only handler, no occupancy check, no duplicate/overwrite guard, no provenance, no tests (discovery §2) — extend, don't replace; limitations feed M7 test matrix |
| Provenance / place-break exploit control | MISSING | No natural-vs-player-placed model; place-break loop is live today (discovery §2.1) — design §13 requires a decision in M2/M7 sized to the smallest sufficient mechanism |
| Data-driven mineable definitions | **COMPLETE (M2)** | `data/britannia_mod/mining/mineables.json` + `mining/MineableCatalog`/`Mineables` (CraftableRegistry pattern): 27 definitions, fail-fast validation (duplicates, ranges, identities, malformed/unregistered block refs), ACTIVE/DEFERRED status, exactly-once block resolution, data-only extensibility proven by test. Legacy `PickaxeMiningRules`/`BlockBreakUtils` chains still drive the live break flow until M3 swaps consumption over. |
| Worldgen (vein placement) | COMPLETE (mechanism) / PARTIAL (data) | Rails-driven `/populateores` + 6 generators incl. silver's preferred `VerticalLayeredVein`; seed data covers tin/iron only (+1 copper) |
| Ore economy identity | **RESOLVED vs seeds (M5)** | E1 fixed: `CommodityMappings.oreCommodityKey` maps every mined ore display name to its seeded `ore/raw` identity (High-Purity Silver reuses `silver`, no duplicate), wired into both the sale payload and the sale-matching path — the latter also closed a real defect where a Silver request could reserve a different metal's purity ore. Live DB shape still UNVERIFIED (Rails DB unreachable); confirm before deploy. E2 (stone subcategory `blocks` vs seeded families) remains open for M8 |
| Stone economy identity | PARTIAL + CONFLICT | Blackrock/Deepslate/custom rocks unsellable (E4); stone→cobblestone funnel (E3) |
| Refining | COMPLETE (generic) | Forge purity system; no skill participation (documented as future UO parity hook per design §17); high-purity silver unrefinable (see §1) |
| Blacksmithing consumption | COMPLETE | `UOMetalToolMaterial` covers all 9 approved metals incl. Silver |
| Localization for mining messages | **PARTIAL (M2)** | Generic requirement-denial keys added to `en_us.json` (`message.britannia_mod.mining.insufficient/skill_unavailable/automation_blocked/unresolved`), mirroring Farming's key shape; not wired (M3/M9). Success-path break messages remain hard-coded literals. |
| Denial feedback / thresholds UX | **PARTIAL (M3)** | Localized action-bar denial wired (`mining.insufficient` with material/current/required, `skill_unavailable`, `automation_blocked`), Farming's formatting conventions. Held-click spam tuning, admin inspection tooling → M9 |
| Tool durability on mining | MISSING (currently none consumed) | Baseline invariant to preserve in denial tests; whether successful mining *should* consume durability is UNKNOWN intent — flag for owner in M3 only if tests require a definition |
| Tests (mining/restore/economy) | MISSING | None exist; JUnit + GameTest infrastructure ready |
| Assets: ores/rocks | PARTIAL | Complete except custom `gold_ore` and `high_purity_silver_ore` (nothing) |
| Assets: ingots | PARTIAL | All 7 models placeholder iron-ingot texture; no ingot lang keys |
| Assets: mined items | PARTIAL | purity/grade models+textures exist (shared across metals); no lang keys |
| Raw asset reuse | NOT APPLICABLE (silver) / PARTIAL (rocks) | `raw fiels` has no silver/ore art; stone texture packs exist for future rocks (license: UNKNOWN — verify before use) |

## 4. Ranked risk register (feeds Milestones 2–3 planning)

1. **E1/E2 economy identity verification** — must inspect the live Rails DB (not just seeds)
   before any Mining-driven supply lands, or ore/stone sales may silently fail (`MissingCommodity`).
2. **Place-break/no-provenance exploit** — becomes a skill exploit the moment awards exist (M4);
   the pending-restore clobber can also destroy ore nodes today.
3. **Vanilla-tool bypass** — *narrowed by M3*: the gate sits on `BreakEvent` for all players, so
   under-skilled players can no longer destroy gated nodes with any tool. Remaining exposure:
   an at/above-threshold player with a vanilla tool still destroys custom ore droplessly with no
   restoration record (M6/M7 to close by loot/managed-flow alignment).
4. **Gold duplication / High-Purity Silver incoherence** — resolve block identities in M2 before
   encoding requirements.
5. **Unsellable stones (E4)** — decide mappings before Stone becomes the primary training loop.
6. **Restoration Overworld-only + overwrite semantics** — acceptable to preserve, but M7 tests
   must encode the actual (not imagined) behavior.

## 5. Genuinely blocking owner questions

None for Milestone 2. Items flagged OWNER/ARCHITECTURE DECISION REQUIRED (Bronze alloy route,
High-Purity Silver intent, Dripstone/Obsidian/Cobbled-Deepslate membership, custom-rock economy
mappings) can all be carried as explicit TODOs in the M2 definition layer without blocking it —
M2 encodes the approved metal ladder and the confirmed rock set first.
