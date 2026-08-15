# Mining Progression Gap Analysis (Milestones 1-10 + owner decisions)

Date: 2026-08-14 · Branch `patch-18`
Original discovery at HEAD `40fa27d2`; rows updated as each milestone closed.

Classification vocabulary (per playbook §1.8):
`COMPLETE` · `PARTIAL` · `MISSING` · `DUPLICATE/CONFLICT` · `NOT APPLICABLE` ·
`OWNER/ARCHITECTURE DECISION REQUIRED` · `UNKNOWN` where unproven.

A resource is COMPLETE only if every layer it needs (block, item, assets, refining, worldgen
path, economy identity, restoration) exists and is coherent. The gate (M3), the award and
calibration (M4), the economy identity (M5) and the vein data (M5/M6) now exist, so rows are
scored on their own remaining gaps; cross-cutting systems are scored once in §3.

---

## 1. Metals (approved ladder)

| Material | Req | Classification | What exists | What is missing / conflicted |
|---|---:|---|---|---|
| Iron | 0.0 | **COMPLETE** | Vanilla ore + deepslate form managed at 0.0; 133 seeded veins; vanilla ingot; `ore/raw/iron` + `metal/ingots/iron`; economy identity resolved (M5) | — |
| Silver | 55.0 | **COMPLETE (M5)** — one cosmetic gap | Block+item+assets+lang; gated at 55.0; managed break → `PurityOreItem("Silver ore")` → forge → existing `silver_ingot`/`UOMetalToolMaterial.SILVER`; restoration incl. save/load proven; **24 vein rows now seeded** (Rails `db/seeds/silver_ore_veins.rb`, idempotent, `/populateores silver`); economy resolves to the seeded `ore/raw/silver` identity; ingot + mined-ore lang added; 8 unit + 5 GameTests | Ingot art is still the iron-ingot placeholder shared by all 7 custom ingots (M6 asset work). Rails seed is **uncommitted** and its DB was unreachable, so live vein/commodity state stays unverified until an operator runs the seed |
| Tin (replaces Dull Copper) | 65.0 | **COMPLETE** | Full vertical slice: block+item+assets+lang, ingot, `TIN` material, `LayeredVein`, 47 seeded veins, Rails ore+ingot rows; gated at 65.0; economy identity resolved (M5); ingot lang added (M6) | Ingot art still the shared placeholder (owner/asset task) |
| Shadow Iron | 70.0 | **COMPLETE (M6)** | Block+item+assets+lang, ingot, material identity, generator wired, Rails `ore/raw` row; economy identity resolved (M5); **12 veins seeded (M6)** | Ingot art still the shared placeholder (owner/asset task) |
| Copper | 75.0 | **COMPLETE (M6)** | Block+item+assets+lang, ingot, material identity, generator wired, Rails `ore/raw` row; economy identity resolved (M5); **14 (+1 existing) veins seeded (M6)** | Ingot art still the shared placeholder (owner/asset task) |
| Bronze | — (alloyed) | **COMPLETE — owner decision 2026-08-15** | Owner: *"created when tin and copper are both smelted together in the forge at the same time"*. `ForgeSmelting` settles the alloy before single-metal payouts: three Tin plus three Copper become two Bronze ingots, the same six-purity-to-two-ingots rate as any metal. Bronze ingot item, own art, lang, `UOMetalToolMaterial.BRONZE` between Copper and Iron, and the pre-existing Rails `metal/ingots/bronze` price now has a production path | Still **no Bronze ore**, exactly as design §6.3 requires — the two routes must never coexist |
| Gold | 85.0 | **COMPLETE (M6)** | Vanilla + deepslate + the custom block all resolve to one `gold` definition at 85.0 with one drop identity, so the duplicate cannot diverge. **10 veins seeded** (M6). The custom block now ships assets (referencing the vanilla texture) instead of rendering as a missing model | Retiring the redundant custom block outright is still an option, deliberately not taken: removing a registration risks existing saves that contain it |
| Agapite | 90.0 | **COMPLETE (M6)** | Block+item+assets+lang, ingot, material identity, generator wired, Rails `ore/raw` row; economy identity resolved (M5); **7 veins seeded (M6)** | Ingot art still the shared placeholder (owner/asset task) |
| Verite | 95.0 | **COMPLETE (M6)** | Block+item+assets+lang, ingot, material identity, generator wired, Rails `ore/raw` row; economy identity resolved (M5); **5 veins seeded (M6)** | Ingot art still the shared placeholder (owner/asset task) |
| Valorite | 99.0 | **COMPLETE (M6)** | Block+item+assets+lang, ingot, material identity, generator wired, Rails `ore/raw` row; economy identity resolved (M5); **3 veins seeded (M6)** | Ingot art still the shared placeholder (owner/asset task) |
| High-Purity Silver | — | **RETIRED — owner decision 2026-08-14** | Nothing: removed from the catalogue, the creative tab and `/populateores`, so it cannot be gated, mined, awarded or restored. Owner: *"there should only be one silver metal/ore"* | Registration, assets and the legacy economy mapping (→ `silver`) are deliberately kept so existing worlds keep loading, the block still renders, and previously-mined stacks stay sellable. Hard removal of the registration is available on request |
| Dull Copper | — | NOT APPLICABLE (by owner decision) | Nothing — the vestigial unused `ORE_TYPES` entry was deleted in M6 | Do not add; Tin holds 65.0 |
| GM Mining | 100.0 | MISSING | Rails Mining skill max_value 100.0 | Cap exists; no mastery hook (future expansion per design §7.1) |

## 2. Rocks (proposed §8 baseline vs repository)

| Rock | Proposed req | Classification | Notes |
|---|---:|---|---|
| Stone | 0.0 | **COMPLETE — owner decision 2026-08-15** | Owner: mining Stone yields Cobblestone, sold for the lowest amount. Cobblestone is the floor of the price ladder at 1.0; the separate `stone` commodity is simply not a mined product |
| Natural Cobblestone | 0.0 | **COMPLETE (M7)** | Managed; player-placed cobblestone is now distinguished from natural and yields no Mining, no managed drop and no restoration |
| Limestone | 5.0 | PARTIAL (via alias) | No limestone block; **Calcite is the limestone source** (`deduceStoneType` alias). Keep alias; do not add a block unless owner wants one |
| Calcite | 5.0 | PARTIAL | Managed; sells as limestone (intended alias E5) |
| Diorite | 10.0 | PARTIAL | Managed + sellable; needs only gate/award |
| Andesite | 15.0 | PARTIAL | Managed + sellable |
| Granite | 20.0 | PARTIAL | Managed + sellable (ordinary rock — distinct from UO High Quality Granite, design §8.1) |
| Tuff | 25.0 | PARTIAL | Managed + sellable |
| Deepslate | 30.0 | **COMPLETE** | Managed, gated, and now seeded as `stone/metamorphic/deepslate` at 4.0, its step on the progressive ladder |
| Cobbled Deepslate | 30.0 | **COMPLETE** (one open question) | Seeded as `stone/rubble/cobbled_deepslate` at 4.0 | Predominantly player-made, so whether it belongs in the *natural* progression at all is still worth an owner view |
| Dripstone Block | 35.0 | **COMPLETE (M6)** | Activated at the approved 35.0 as a pure data change (no Java touched) — the proof that the definition layer is genuinely extensible. Still no stone commodity (E4, M8) |
| Basalt / Smooth Basalt | 40.0 | PARTIAL | Both managed; both sell as `basalt` |
| Blackstone | 45.0 | **COMPLETE (M8)** | Managed; the established `"Blackrock"` drop name is now mapped onto the seeded `stone/volcanic/blackstone` commodity rather than renaming the drop, so previously-mined stacks stay valid |
| Quartz-bearing rock | 50.0 | NOT APPLICABLE (currently) | No Overworld quartz block; `quartz` commodity exists; owner may add a source later |
| Obsidian | — | **NOT APPLICABLE — owner decision 2026-08-14** | Removed from the catalogue entirely. Owner: *"no use for Obsidian tools (not a part of Ultima Online)"*. Vanilla Obsidian behaviour is untouched, and the tool-tier check M6 had identified as its prerequisite is no longer needed |
| Igneous/Metamorphic/Volcanic/Glacial Rock | data-driven | **COMPLETE for economy**; worldgen still open | All four now seeded and priced on the ladder (metamorphic 4.0, glacial 5.0, igneous 6.0, volcanic 7.0) | Still no discovered placement path, so they reach players only via creative or structures |

## 3. Systems

| System | Classification | Evidence / gap |
|---|---|---|
| Mining break interception | COMPLETE (as a hook point) | `CustomBlockBreakHandler` @ `BlockEvent.BreakEvent`; earliest safe denial point identified (discovery §1.5) |
| Mining skill gate | **COMPLETE (M3)** | `mining/MiningBreakGate` (FarmingCultivationGate-shaped policy core) + `MiningGateHandler` at `BlockEvent.BreakEvent`, `EventPriority.HIGH` — server-authoritative, covers every player break whatever the tool, inclusive threshold, creative/op(2) bypass via server game mode, fake-player default deny, skill-unavailable deny. Denial proven side-effect-free by GameTests. Vanilla-tool breaks by *sufficiently skilled* players still bypass the managed drop/restore flow (unchanged M1 finding; M6/M7 scope). |
| Mining skill award | **COMPLETE (M4)** | `mining/MiningSkill.awardForBreak` at the managed flow's success boundary → `SkillManager.awardSkillGain(…, 0.1f)`; per-material `challenge` feeds a Mining-only roll; gate re-check excludes bypass/automation/unavailable/unmanaged by construction; `(player,pos,tick)` dedup. Calibrated to **18,000** activations (−0.00 % vs target) — see [MINING_SKILL_CALIBRATION_REPORT.md](MINING_SKILL_CALIBRATION_REPORT.md) |
| Skill engine (shared) | COMPLETE | `SkillManager` + Rails persistence + sync + states + trainer; reuse as-is |
| Guildmaster Mining training | COMPLETE | `miner` guild teaches Mining; purchase flow is skill-agnostic and unchanged by M4 (commits in Rails, reaches the mod via `applyConfirmedValue`, never rolls). Its 40.0 ceiling replaces 1,231 field activations (6.8 % of the journey) |
| Restoration engine | **COMPLETE (M7)** | Same single scheduler and 6h cadence, now: **every dimension** (was Overworld-only, which silently stranded Nether Basalt/Blackstone once Mining governed them) and **never overwrites** — it restores only into a free, unoccupied cell and otherwise waits, instead of deleting a player's build or burying them. Still no force-loading; unloaded cells retried later. 8 GameTests + 7 source contracts |
| Provenance / place-break exploit control | **COMPLETE (M7)** | `mining/MiningProvenance` (per-level `SavedData`, positions as a packed `long[]`) + `MiningProvenanceHandler` on `EntityPlaceEvent`. **Absence means natural**, so existing worlds need no migration and only standing player construction costs storage; entries clear on break (LOWEST-priority listener, after every reader). A self-placed mineable resolves NOT_APPLICABLE: no gate, no managed drop, no restoration, no award — and players can always dismantle their own gated builds. Proven end to end through the real placement path |
| Data-driven mineable definitions | **COMPLETE (M2 + M6)** | `data/britannia_mod/mining/mineables.json` + `mining/MineableCatalog`/`Mineables` with fail-fast validation and ACTIVE/DEFERRED status. **M6 removed the second source of truth**: `PickaxeMiningRules` and `BlockBreakUtils` now delegate to the catalogue for the managed set and every drop name (all 29 names pinned byte-identical by test), `Mineables.resolve` uses an identity map for the mining hot path, and the dead `ORE_TYPES`/`dull_copper` map is gone. Adding a rock is now a JSON edit — proven by activating Dripstone with zero Java changes. |
| Worldgen (vein placement) | COMPLETE (mechanism) / PARTIAL (data) | Rails-driven `/populateores` + 6 generators incl. silver's preferred `VerticalLayeredVein`; seed data covers tin/iron only (+1 copper) |
| Ore economy identity | **RESOLVED vs seeds (M5)** | E1 fixed: `CommodityMappings.oreCommodityKey` maps every mined ore display name to its seeded `ore/raw` identity (High-Purity Silver reuses `silver`, no duplicate), wired into both the sale payload and the sale-matching path — the latter also closed a real defect where a Silver request could reserve a different metal's purity ore. Live DB shape still UNVERIFIED (Rails DB unreachable); confirm before deploy. E2 (stone subcategory `blocks` vs seeded families) remains open for M8 |
| Stone economy identity | **RESOLVED where a commodity exists (M8)** | E2 fixed: stone sales now post the **seeded family** (rubble/common/igneous/volcanic/sedimentary/mineral) instead of the literal `blocks`, which no commodity row has ever carried — every stone sale previously failed Rails' exact lookup. Fixed in all three sites (sale payload, trader listing, commodity table). E4 partly closed: Blackstone's `Blackrock` drop now maps to the seeded `blackstone` commodity. Remaining: Deepslate, Cobbled Deepslate, Dripstone and the 4 custom rocks have **no commodity in Rails at all** — adding one means inventing a price, so it is an owner decision, not a silent edit. E3 (mined Stone yields cobblestone) unchanged and now an explicit owner question |
| Refining | COMPLETE (generic) | Forge purity system; no skill participation (documented as future UO parity hook per design §17); high-purity silver unrefinable (see §1) |
| Blacksmithing consumption | COMPLETE | `UOMetalToolMaterial` covers all 9 approved metals incl. Silver |
| Localization for mining messages | PARTIAL | Denial keys added and wired (M2/M3), asserted to interpolate current/required/material. Success-path break messages ("You mined X stone. Grade: Y") remain hard-coded literals — pre-existing, cosmetic, and untouched by this project |
| Denial feedback / thresholds UX | **COMPLETE (M9)** | Localized action-bar denial (material/current/required), now throttled per player so a held click cannot rewrite the same line every dig while a *different* message still appears immediately. Never written to chat (asserted) |
| Tool durability on mining | MISSING (currently none consumed) | Baseline invariant to preserve in denial tests; whether successful mining *should* consume durability is UNKNOWN intent — flag for owner in M3 only if tests require a definition |
| Tests (mining/restore/economy) | MISSING | None exist; JUnit + GameTest infrastructure ready |
| Assets: ores/rocks | PARTIAL | Complete except custom `gold_ore` and `high_purity_silver_ore` (nothing) |
| Assets: ingots | **COMPLETE** | All 8 metals (7 mined + Bronze) now have their own generated 16×16 art and lang keys. Drawn from an original template in `tools/generate_metal_ingot_textures.py`, deterministic and reproducible (`--check`), create-only so owner replacement art is never clobbered. No Mojang texture is copied |
| Assets: mined items | PARTIAL | purity/grade models+textures exist (shared across metals); no lang keys |
| Raw asset reuse | NOT APPLICABLE (silver) / PARTIAL (rocks) | `raw fiels` has no silver/ore art; stone texture packs exist for future rocks (license: UNKNOWN — verify before use) |

## 4. Ranked risk register (feeds Milestones 2–3 planning)

1. **E1/E2 economy identity verification** — must inspect the live Rails DB (not just seeds)
   before any Mining-driven supply lands, or ore/stone sales may silently fail (`MissingCommodity`).
2. ~~**Place-break/no-provenance exploit**~~ — **closed in M7** by the provenance model; the
   place-break loop now awards nothing and schedules nothing.
3. **Vanilla-tool bypass** — *narrowed by M3*: the gate sits on `BreakEvent` for all players, so
   under-skilled players can no longer destroy gated nodes with any tool. Remaining exposure:
   an at/above-threshold player with a vanilla tool still destroys custom ore droplessly with no
   restoration record (M6/M7 to close by loot/managed-flow alignment).
4. **Gold duplication / High-Purity Silver incoherence** — resolve block identities in M2 before
   encoding requirements.
5. **Unsellable stones (E4)** — decide mappings before Stone becomes the primary training loop.
6. ~~**Restoration Overworld-only + overwrite semantics**~~ — **fixed in M7**: all dimensions are
   swept, and restoration waits rather than overwriting construction or burying an entity.

## 5. Owner questions

**Answered 2026-08-14** (see the implementation log's owner-decisions section):

- **High-Purity Silver** — retired. There is exactly one Silver metal/ore.
- **Obsidian** — not a Mining resource; not an Ultima Online material.
- **Dripstone** — activated at the approved 35.0 (M6).

**Still open, none blocking:**

- **Bronze alloy route** — Rails carries a `metal/ingots/bronze` commodity with no production path.
  Outside Mining scope; only matters if bronze ingots must become obtainable.
- **Cobbled Deepslate membership** — managed today, but it is predominantly a player-made block.
- **Custom rock economy mappings and worldgen** — igneous/metamorphic/volcanic/glacial are mineable
  but unsellable and have no placement path (E4, M8).
- **Silver ingot art** — all seven custom ingots share the vanilla iron-ingot reference; original
  art is an owner/asset task.
