# Mining Skill Progression — Implementation Status

Date: 2026-08-15 · Branch `patch-18` · Mod repo `Britannia_Mod`, Rails repo `ultimacraft-website`

This is the closeout summary. Per-milestone narrative lives in
[MINING_IMPLEMENTATION_LOG.md](MINING_IMPLEMENTATION_LOG.md); per-requirement test evidence lives in
[MINING_SKILL_TEST_MATRIX.md](MINING_SKILL_TEST_MATRIX.md).

---

## 1. What was built

Mining is now a real progression system layered on the **existing** renewable-resource, skill,
restoration and economy systems. Nothing was forked or replaced: no second skill engine, no second
restoration scheduler, no duplicate commodities, no new worldgen framework.

| Layer | Where it lives |
|---|---|
| Progression data | `data/britannia_mod/mining/mineables.json` — 25 definitions, all active |
| Catalogue + validation | `mining/MineableCatalog`, `MineableDefinition`, `Mineables` |
| Break gate | `mining/MiningBreakGate` + `MiningGateHandler` (`BreakEvent`, HIGH priority) |
| Skill award | `mining/MiningSkill` → existing `SkillManager.awardSkillGain` |
| Provenance | `mining/MiningProvenance` + `MiningProvenanceHandler` |
| Managed break flow | existing `CustomBlockBreakHandler`, now catalogue-driven |
| Restoration | existing `blockrestore` package + `BlockRestoreHandler` |
| Economy identity | `economy/CommodityMappings` (ore + stone families) |
| Operator tooling | `commands/MiningDebugCommand` (`/mining`, read-only) |
| Vein data | Rails `db/seeds/silver_ore_veins.rb`, `db/seeds/colored_metal_ore_veins.rb` |

## 2. Final acceptance criteria

| Criterion | Status | Evidence |
|---|---|---|
| Silver complete | **MET** | Mined at 55.0 into its existing identity, refines to the existing ingot, 24 veins seeded, restoration proven across save/load |
| Tin at 65.0 | **MET** | Catalogue + boundary tests; no Dull Copper anywhere |
| UO metal thresholds enforced | **MET** | 0/55/65/70/75/85/90/95/99 pinned by unit and in-world ladder tests |
| Rock family extensible | **MET** | Dripstone activated at 35.0 as a pure data edit, no Java change |
| Stone grants progression | **MET** | Trainable at 0.0, 50.0 and 99.9; never stops being a valid source |
| Under-skilled blocks do not break | **MET** | Denied for every tool, not just the mod pickaxe |
| Denial side-effect free | **MET** | Block, drops, durability, skill and restoration deltas all zero |
| Skill engine reused | **MET** | `SkillManager.awardSkillGain`, Rails persistence, existing sync |
| Restoration reused | **MET** | One scheduler, no force-loading; corrected to sweep all dimensions and to wait on occupied cells |
| 18,000 ±5% | **MET** | 18,000 exactly (−0.00%); the calibration test fails the build outside 17,100–18,900 |
| Economy identities coherent | **MET** | Ore and stone resolve to seeded rows; no duplicates; no invented prices |
| Dedicated server passes | **MET** | GameTestServer boots and runs the full suite |
| Multiplayer passes | **MET (automated)** | Two-player GameTests; live two-client run is owner-run (§5) |
| Save/restart passes | **MET** | Restoration and provenance both round-trip |
| No unrelated regressions | **MET** | Full unit suite and GameTest suite green |

## 3. Automated verification (final run)

```text
./gradlew.bat compileJava --no-configuration-cache            SUCCESS
./gradlew.bat test --rerun-tasks --no-configuration-cache     2,296 tests · 0 failures · 0 errors
./gradlew.bat runGameTestServer --rerun-tasks --no-configuration-cache   491 GameTests · all passed
./gradlew.bat build --rerun-tasks --no-configuration-cache    SUCCESS (compile, tests, resources, jar)
git diff --check                                              clean
```

Mining-owned coverage: **75 unit tests** across 9 classes and **31 GameTests** across 6 classes.

## 4. Deliberate non-goals and open owner decisions

Nothing below is a defect; each was a decision to *not* act unilaterally.

| Item | Position |
|---|---|
| Bronze | Refined-metal commodity only. No ore, per design §6.3. Whether a Copper+Tin alloy route should ever back it is outside Mining |
| Unsellable rocks (Deepslate, Cobbled Deepslate, Dripstone, 4 custom rocks) | Rails seeds no commodity for them; adding one means choosing a price, which design §16 reserves for the owner. Convention-derived proposals are in the log |
| Mined Stone yields Cobblestone | Long-standing behaviour; the seeded `stone` commodity therefore has no mined supply. Changing it moves drop identity and refining together |
| Custom rock worldgen | Igneous/metamorphic/volcanic/glacial have no placement path; they may be scenery rather than resources |
| Ingot art | All seven custom ingots share the vanilla iron-ingot reference. Original art is an owner/asset task; Mojang art must not be embedded |
| High-Purity Silver registration | Soft-retired. Hard removal of the registration is available on request |
| Success-path break messages | Still hard-coded English literals (pre-existing); denial messages are localized |
| GM Mining (100.0) | Cap exists; no mastery hook, as design §7.1 intends for later |

## 5. Live acceptance runbook (owner-run)

This environment cannot drive game clients, and the Rails database was unreachable for the whole
project, so the following must be run by the owner. Everything it depends on is automated-tested;
this validates the integration in a real shard.

**Prerequisites**

```bash
bin/rails db:seed:silver_ore_veins
bin/rails db:seed:colored_metal_ore_veins
```

Then in game, as an operator: `/populateores silver`, and per metal as desired
(`shadow_iron`, `copper`, `gold`, `agapite`, `verite`, `valorite`).

**Sequence**

1. Confirm the commodity shape matches the seeds before trading:
   `ore/raw/<metal>` and `stone/<family>/<name>`. If production carries legacy rows, migrate first.
2. `LowMiner` at 54.9 Mining, `HighMiner` at 55.0 (set through Rails, not the local cache).
3. Both attack separate Silver nodes. LowMiner must fail with the block unchanged and no drop;
   HighMiner must mine, receive one purity ore, and see one restoration scheduled.
4. Raise LowMiner to 55.0 **through Rails**; retry **without reconnecting** — it must now succeed.
5. Repeat spot checks at Tin 65, Gold 85, Verite 95, Valorite 99.
6. Mine regular Stone repeatedly and confirm Mining rises.
7. Place a stone block, break it: no skill, no drop, no restoration.
8. Restart the server with pending restorations and confirm they survive and later restore.
9. Sell mined ore and stone to the relevant traders; confirm both resolve.
10. Visually inspect ore blocks and ingots for missing textures.

Useful throughout: `/mining debug` (looked-at block), `/mining skill <player>`,
`/mining restorations`.

## 6. Risks carried into production

1. **Live Rails data is unverified.** All economy work is proven against the seeded identities,
   which are the version-controlled truth. If production holds legacy commodity rows, verify before
   deploying — this is the single highest-value pre-deploy check.
2. **Vein data is inert until seeded and placed.** The seeds add rows; an operator must still run
   `/populateores` per metal.
3. **A permanently built-over restoration cell waits indefinitely.** Non-destructive by choice; an
   expiry policy may be wanted eventually.
4. **Provenance markers for blocks destroyed outside `BreakEvent`** (explosions) are not cleared.
   The error direction is always "treat as construction", i.e. deny a gain, never grant one.
