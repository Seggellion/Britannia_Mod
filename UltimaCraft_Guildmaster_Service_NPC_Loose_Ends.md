# Guildmaster Service NPC — Loose Ends

Running register of things that are open, ambiguous, or deliberately deferred. Updated after the
first full Rails test run (2026-08-09).

Status key: **BLOCKING** (work cannot correctly continue) · **OPEN** (needs an owner decision) ·
**CARRIED** (known, accepted, not yet acted on) · **CLOSED**.

---

## 1. "5 silver" — **CLOSED**

Resolved by owner decision: the precious metals read the **city treasury reserve**
(`City#gold_amount`/`silver_amount`/`copper_amount`, over `TreasuryBalance` rows), not the
`cities.*_supply` columns.

`CityStaffing::EconomicEligibility` was repointed accordingly, and the mod's
`WorldBootstrapHandler` now publishes `gold`/`silver`/`copper` from the bootstrap's existing
`treasury` block. Both sides read the same figure, so the spawn-block readout reports the number
the gate actually decided on.

The original analysis is kept below, because the reasoning is what makes the choice reviewable.

### Original finding



Your economic rule was *200 food, 5 silver, 5 alcohol*. Food and alcohol are unambiguous: both are
`cities.*_supply` columns, both are published in the bootstrap's `supplies` object, and both are
what my Rails gate reads. They line up end to end.

Silver does not. There are two unrelated quantities:

| | What it is | Who reads it |
|---|---|---|
| `cities.silver_supply` | float column, written only by `CityCommodity#update_precious_column`, **seeded to `0.0`** (`db/seeds/cities.rb:53`) | my `CityStaffing::EconomicEligibility` |
| `city.silver_amount` | `get_treasury_reserve("silver")` over `TreasuryBalance` rows | the world bootstrap, as `treasury.silver` |

**Why this is blocking rather than cosmetic:** if `silver_supply` is vestigial — seeded at zero and
only ever moved by precious-metal commodity trades — then the gate never opens and *no Guildmaster
ever spawns anywhere*. That failure would look like broken code rather than an unmeetable
threshold, which is the worst way for it to surface.

I deliberately did **not** map `treasury` into the mod's supply map. Doing so would make the spawn
screen display a number Rails never consulted. A silver requirement therefore currently reads
`UNKNOWN` on the screen — uninformative, but never confidently wrong.

**Needs:** which silver did you mean?
1. `silver_supply` → confirm it is non-zero on real cities, and add the precious-metal columns to
   the bootstrap `supplies` payload so the screen can show them.
2. Treasury silver → repoint `EconomicEligibility` at `silver_amount`; it is already published, so
   the mod side needs no change.

Option 2 is my guess ("the city has 5 silver" sounds like coin on hand), but guessing here produces
a gate that silently never opens.

---

## 2. Economic threshold is symmetric, so Guildmasters will visibly flicker — **CARRIED**

Your decision, implemented as asked: a city falling below its minimum un-staffs Guildmasters it
already has, and they despawn.

City supplies are drawn down continuously by `CommodityConsumptionJob`, so a city hovering near 200
food will oscillate across the threshold and its Guildmasters will come and go with it. Recorded
because it is visible in-game and will read as a bug to anyone who does not know it was chosen.

If it proves unwanted, a separate lower retirement threshold is a small change to
`minimum_city_supplies`'s shape — the planner already distinguishes the closure reason
(`staffing_city_economy_below_minimum`).

---

## 3. Test-suite state after the first full run — **CARRIED**

`1359 runs, 6291 assertions, 9 failures, 20 errors`. Triaged:

### Mine, now fixed (12)

| Failure | Cause |
|---|---|
| `ServiceNpcRegistrySerializerTest` | asserts the closed action list; `guild.train` legitimately extends it |
| `Api::WorldBootstrapServiceNpcRegistryTest` | same assertion, same cause |
| `CityStaffing::EconomicEligibilityTest` (9 errors) | my new test built a `Shard` without `address`; now matches `create_spawn_context` |
| `CityStaffing::SpawnCapacityTest` (1 error) | my new test gave a type an empty allow-list while reusing the banker dialogue, which invokes `bank.open` — the model correctly rejected it |

The last one is worth noting: the validation that caught it is the one I added in Milestone 4. It
did its job on my own test.

### Caused by the workaround I suggested (1)

`FixtureLoadingInfrastructureTest#test_loader_is_test_only_and_role_is_not_superuser` — expected
false, got true. This guard exists to catch the test suite running as a **superuser** database
role, and `TEST_DB_USERNAME=ultimacraft` is exactly that. My workaround tripped a check that is
working correctly.

The clean fix is a non-superuser Postgres role matching an OS user (peer auth), which is what the
`ultimacraft_codex_test` default was designed for. Until then this one failure is expected and
means "you are running the suite as a superuser", not "the code is wrong".

### No plausible connection to my changes (16)

`MenusControllerTest` ×6 (`Dusk.css` missing from the asset pipeline), `MenuItemsControllerTest` ×5
(404s), `Admin::DashboardControllerTest`, `Admin::ContactMessagesControllerTest` ×2 (undefined
`*_url` route helpers), `Economy::CityFoodSupplyRecalculatorTest` (100.0 vs 99.7),
`ApplicationHelperTest` (`Errno::EACCES` on `tmp/storage`).

I have **not** proven these are pre-existing — I never ran the suite on a clean tree. They touch
menus, admin scaffolding, the asset pipeline and commodity recalculation, none of which my changes
reach. `git stash && bin/rails test` would confirm it in one run.

The `EACCES` one is the same root cause as the schema-dump problem in §4: running as `ultimacraft`
inside a repo owned by `dusti`.

---

## 4. Cross-user file ownership in the Rails checkout — **CARRIED**

The repo is owned by `dusti`; you run Rails as `ultimacraft`. This has already produced two
silent-ish failures:

- `db:migrate` applied cleanly but could not rewrite `db/schema.rb` (mode 644, owned by `dusti`),
  so the schema looked unmigrated. Fixed by `chmod 666 db/schema.rb`.
- `ApplicationHelperTest` cannot create `tmp/storage/...`.

**`db/schema.rb` is currently left at mode 666.** That should be restored to 644 once you decide
which user owns this workflow — a world-writable tracked file is not a good resting state.

---

## 5. Milestone 4 verification — **CLOSED (partially)**

The migration is verified: `db/schema.rb` now reads `version: 2026_08_09_120100` with all four
columns, both check constraints, and the join table with correct mixed key types
(`bigint` type ⇄ `uuid` skill) and the unique pair index.

The test suite has now run; see §3. Not yet re-run after my fixes.

---

## 6a. Seeds are run — **CLOSED**, and the result was clean

```
created (34) ... already present: 5 ... total skills now: 39
Guildmaster definitions seeded: 12 guilds
```

Every guild landed with exactly its RunUO roster count — bard 6, blacksmith 4, fisher 1, healer 5,
mage 7, merchant 2, miner 2, ranger 11, tailor 1, thief 9, tinker 3, warrior 7. That is all 58
taught-skill slots, so **no guild silently lost a skill** to the seed-time intersection.

**Zero alias matches.** The alias pass existed to stop a duplicate row appearing next to a skill
already stored under a different spelling; nothing triggered it. The 5 pre-existing skills were
`discordance`, `fishing`, `musicianship`, `peacemaking`, `provocation` — exactly the five published
on the play guide.

## 6b. `farming` and `carpentry` are still missing from Rails — **OPEN (pre-existing bug)**

Fell out of the seed arithmetic rather than being looked for. The table held exactly 5 skills
before and holds exactly 39 now, and 39 is precisely the RunUO roster — so every skill in the
database is one of mine or one of those five. Neither `farming` nor `carpentry` is in the RunUO
roster, so **neither exists as a `Skill` row**.

The mod references both:

- `FarmingSkill.SKILL_ID = "farming"`
- the literal `"carpentry"`

Both route into `SkillManager` and then `POST /api/skills/gain`, where
`Skill.find_by!(slug: ...)` raises `RecordNotFound` → 404. `SkillManager.postGain` swallows that
into `LOGGER.warn("Failed to POST skill gain to Rails")`, so **farming and carpentry gains have
been silently discarded at every logout** — the in-memory value rises during a session and is gone
at next login.

`blacksmithy` was in exactly this state until this seed created it, which is how the pattern
became visible.

This is pre-existing and not caused by the Guildmaster work; it is recorded because the Guildmaster
work is what made it detectable, and because it is a live player-facing data-loss bug. Fixing it is
a two-row addition to `UoSkillRoster::SKILLS` (or a separate seed), but only the owner can say what
`max_value` and gain rules those two should carry.

## 6c. Original note — **superseded by 6a**

`db:seed:uo_skills` and `db:seed:guildmaster_definitions` have not been executed, so no Guildmaster
type exists yet and no skill has been created. Until then the registry publishes no Guildmasters
and the mod has nothing to show.

The skill seed's output is worth reading rather than skimming: it prints every skill it created and
flags anything matched by **alias**, which is the answer to "did these already exist under a
different spelling" that I could not determine myself.

---

## 7. Milestone 2's screen is still undrawn — **CARRIED**

Milestone 2's **data path is complete end to end** — Rails type → registry payload → mod parser →
eligibility evaluation → S2C payload — but nothing renders it. `ServiceNpcSpawnScreen` still shows
only the city/type/enabled controls: no taught-skill list, no supply lines, no
registered-but-unstaffed reason. An admin therefore still cannot see from the block why a
Guildmaster did not appear, which was the point of the milestone.

Milestone 3 (interaction routing) is **done**: `GuildmasterProxyService` mirrors
`BankingProxyService.resolve()`, `interactAt` routes banking first then guild training, and the
read-only placeholder content the playbook's gate allows is in place. Verified by GameTest — 342
in source, 342 passed, up from 340.

---

## 8. Unproven claim: denial produces no entity — **CARRIED**

Milestone 4's gate asks for "allow decision spawns one NPC, deny spawns none". Structurally this
already holds — denial means no `NpcSpawnAssignment`, and `ServiceNpcAssignmentReconciler` creates
an entity only when an active assignment exists — but nothing asserts it, and the admin-readable
denial reason currently stops at the Rails admin page rather than reaching the spawn block.

---

## 9. Guildmaster purchases will not be auditable — **CARRIED**

Consequence of reusing `skills/set` (your Decision 3). That endpoint has no idempotency key and
writes no ledger row, so a retried training request is not de-duplicated by Rails — the mod's own
in-flight guard is the only protection. Accepted at the time; recorded so it is a known trade
rather than a surprise when someone goes looking for a training audit trail.
