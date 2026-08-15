# Mining Skill Calibration Report (Milestone 4)

Date: 2026-08-14 · Branch `patch-18`
Source of every number below: `MiningCalibrationTest` → `build/reports/mining/calibration.txt`,
computed from the **production** `MiningSkill.gainChance` (the test calls the shipped method, it
does not restate the formula). Deterministic expected values, not a sampled simulation.

---

## 1. Award mechanics

| Property | Value |
|---|---|
| Skill engine | Existing `SkillManager` — no new XP, capability, persistence, or sync layer |
| Award API | `SkillManager.awardSkillGain(player, "mining", 0.1f)` (same call `FarmingSkill` uses) |
| Gain unit | **0.1** per successful activation (the engine's own unit; nothing fractional accrues) |
| Cap | Rails `max_value` for Mining = 100.0 (engine-enforced) |
| Persistence | Rails `SKILL_GAIN` POST, fired by the engine, unchanged |
| Qualifying activation | One completed **managed** Mining break: Britannia pickaxe, catalogued block, gate result `ELIGIBLE` |
| Dedup | One `(player, position, tick)` awards at most once (`MiningSkill.acceptActivation`) |
| Global impact | **None.** No other skill's math changes; Rails' Mining row is not edited |

### Excluded from awards (verified by test, not by call-site placement)

The award re-evaluates the break gate and proceeds only on `ELIGIBLE`, so every exclusion falls
out of the policy itself: denied breaks (cancelled before the award exists), Creative/operator
bypass (`APPROVED_BYPASS`), fake players/automation (`NON_PLAYER_POLICY`), unloaded skill data
(`SKILL_DATA_UNAVAILABLE`), unmanaged blocks (`NOT_APPLICABLE`), invalid-tool breaks (never reach
the managed flow — design §9.1 excludes them), and restoration, worldgen, commands, explosions and
pistons (none fire `BlockEvent.BreakEvent`).

## 2. Chance formula

```text
chance(current, challenge) = BASE_CHANCE
                           × (100 − current) / 100          # engine-shaped difficulty ramp
                           × materialFactor(current, challenge)

materialFactor(current, challenge):
    delta = current − challenge
    delta ≤ 0 → 1.0                                          # at or above your skill: full value
    delta > 0 → max(0.25, 1 − delta / 100)                   # decays as you outgrow it, never zero
```

| Constant | Value | Why |
|---|---:|---|
| `BASE_CHANCE` | **0.4252** | The sole calibration lever. Expected activations scale exactly as `1/BASE_CHANCE`, so the 18,000 target was hit in one algebraic step, not by search |
| `MATERIAL_DECAY_SPAN` | 100.0 | A resource loses its progression value gradually across the whole skill range |
| `MINIMUM_MATERIAL_FACTOR` | 0.25 | Floor that keeps Stone (and any trailing resource) trainable at 99.9 — design §9.2 |

The `(100 − current)/100` term is deliberately the same shape `SkillManager.trySkillGain` already
uses, so Mining's curve is the repository's established curve rather than a second convention.

**Why the roll lives in `MiningSkill` rather than `trySkillGain`:** that path exposes only a
per-skill Rails modifier and has no per-material input, while this project requires RunUO's
separation of *access requirement* from *progression difficulty* (design §4). Keeping the roll
mod-side also means calibration touched no Rails data and no other skill — satisfying "do not
change global skill gain" literally.

**Material difficulty input:** the catalogue's per-definition `challenge` field, which milestone 2
seeded equal to each resource's `required_mining`. No data change was needed in this milestone;
future re-balancing can move `challenge` independently of the access gate, which is exactly the
separation the design asks for.

## 3. Expected activations by band

Recommended route = always working the hardest resource the gate permits at your current skill
(derived from the shipped catalogue, so adding a tier re-derives the route).

| Band | Recommended route | Stone only |
|---|---:|---:|
| 0 → 25 | 693 | 783 |
| 25 → 50 | 977 | 1,565 |
| 50 → 65 | 891 | 2,011 |
| 65 → 80 | 1,348 | 4,773 |
| 80 → 90 | 1,705 | 6,497 |
| 90 → 99 | 5,451 | 21,246 |
| 99 → 100 | 6,934 | 27,554 |
| **TOTAL 0 → 100** | **18,000** | **64,429** |

- **Acceptance target: 17,100–18,900.** Recommended route = **18,000** (deviation **−0.00 %**).
- **Stone-only = 64,429** — 3.6× the recommended route: appropriate material is decisively the
  better path, while Stone alone never becomes impossible. Both properties are asserted by tests.

## 4. Effect of legitimate Guildmaster training

Rails' `miner` guild teaches Mining, and the Guildmaster purchase flow caps training at **40.0**
(the stricter of 40.0 and the skill's own cap). Buying that ceiling replaces **1,231** field
activations — **6.8 %** of the journey — leaving **16,768** to reach 100.0.

Training is therefore a real head start without undermining the 18,000-activation design target,
which describes field progression from 0.0. No change was made to the training path; it commits
in Rails first and reaches the mod through `applyConfirmedValue`, which does not roll or award.

## 5. Balance observation for the owner (not a defect)

The journey is **tail-heavy**: 90 → 100 accounts for **12,385** of the 18,000 activations (**69 %**),
and the final point alone (99 → 100) costs **6,934**. This is a direct consequence of the
`(100 − current)/100` ramp the existing skill engine already uses for every other skill, and it
matches Ultima Online's famously punishing last point — so it is consistent by construction rather
than accidental.

If a flatter late game is ever wanted, the smallest change is to soften that term (for example
`((100 − current)/100)^0.5`) and re-run `MiningCalibrationTest`, which will re-tune and re-publish
the table. That would be a Mining-only change and still touch no other skill. **Not done here:**
the approved target was the 0→100 total, and reshaping the curve was not requested.

## 6. Reproducing

```bash
./gradlew.bat test --tests "com.seggellion.britannia_mod.mining.MiningCalibrationTest"
```

Writes `build/reports/mining/calibration.txt` and fails the build if the recommended route leaves
the 17,100–18,900 window, if Stone ever stops granting Mining, or if stone-only stops being the
worse path.
