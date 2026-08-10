# Milestone 4 Report — Rails Service NPC Persistence and Economic Spawn Policy

Rails repo `~/ultimacraft-website` (WSL), branch `banking`, baseline `e584f27e`.
**Nothing committed, nothing pushed, no migration run.**

> **Read §6 first.** None of this Rails work has been executed — not the migrations, not the
> tests. The dev database password is not present anywhere in my environment and I did not go
> looking for it. Everything below is syntax-checked and reasoned, not verified.

Milestones **2** (spawn-block Guildmaster configuration) and **3** (entity behaviour / interaction
routing) were skipped on your instruction and remain outstanding.

---

## 1. Skills created, as instructed

`app/services/uo_skill_roster.rb` holds the canonical roster: **all 39 skills** taught by the twelve
RunUO guildmasters, mapped from RunUO's abbreviated `SkillName` enum to the real Ultima Online
display names. That mapping is the substance of the file — RunUO's `EvalInt` is *Evaluating
Intelligence*, `MagicResist` is *Resisting Spells*, `Swords` is *Swordsmanship*. Seeding the enum
spellings would have produced a roster no UO player recognises and slugs that don't match the ones
the mod already uses (`blacksmithy`, `lockpicking`, `magery`…).

`bin/rails db:seed:uo_skills` creates only what is missing. It is **non-destructive by design**: it
never renames, re-slugs, unpublishes or edits an existing row, because those carry rich-text
descriptions, images, categories and — critically — live `shard_user_skills.value` player progress.

Lookup is slug → case-insensitive name → alias list. The alias pass is what stops a *second*
`Blacksmithy` row appearing next to an existing `Blacksmith`; duplicate skill rows would silently
split a player's progress across two records. Anything matched by alias is printed for review
rather than silently accepted.

New rows get `published: false`. `published` gates the public play-guide CMS page, not gameplay —
`Api::SkillsController#skill_config` serves `Skill.all` regardless, so the mod and Guildmasters see
every skill immediately. Publishing 30-odd blank pages to your live site is your content decision,
not a side effect of a data seed.

---

## 2. Data model

| Change | Detail |
|---|---|
| `service_npc_type_taught_skills` | New join table. Many-to-many because **13 of the 39 skills belong to more than one guild** (Resisting Spells to four). A FK also makes a nonexistent skill unstorable — a jsonb slug array would accept a typo and only fail at training time, in front of a player who already paid. |
| `service_npc_types.minimum_city_supplies` | jsonb, default `{}`. The admin-editable economic gate. |
| `service_npc_types.staffing_baseline` / `_residents_per_increment` / `_max_per_city` | Integers, defaulting to **1 / 10 / 6** — the exact former `DesiredStaffing` constants, so `bank_teller` is arithmetically unchanged. |

Two check constraints mirror the existing `allowed_service_keys` array constraint, so a hand-written
`UPDATE` can't store a scalar where every reader expects an object.

`ServiceNpcType` now enforces the `guild.train` ⟺ taught-skills invariant **in both directions**,
and validates supply keys against a closed list. Both matter for the same reason: the NeoForge
parser rejects either incoherent shape, and a rejected registry falls back to the empty snapshot
*wholesale* — publishing a broken Guildmaster would break banking.

---

## 3. Registry publication

`guild.train` added to `ServiceNpc::ActionRegistry`. The serializer emits `taught_skill_slugs` for
every type (`[]` for non-Guildmasters), **sorted by slug**. That sort is load-bearing, not
cosmetic: `revision` is a digest of the payload and the world-bootstrap ETag is built from it, so
an unstable order would churn the ETag on every request and defeat caching.

Milestone 1 shipped the mod parser that accepts all of this, so the mod-first ordering you approved
holds. **The mod build must be live on servers before this seed runs.**

---

## 4. Economic spawn policy

`CityStaffing::EconomicEligibility` is the new gate. The thresholds are data on the type, seeded to
your stated minimum — `{"food" => 200, "silver" => 5, "alcohol" => 5}` — and editable per type
afterwards. An empty requirement is always satisfied, which is exactly why `bank_teller` is
untouched.

`CityStaffing` as a whole moved from banker-only to per-service-type:

- `DesiredStaffing` takes the three formula numbers as parameters instead of constants.
- `SpawnCapacity` and `Plan` are scoped by `service_npc_type` rather than `profession_key = "banker"`.
- `Reconcile` iterates every active + spawnable type inside the **same** transaction and city lock,
  in deterministic `order(:key)` — they compete for the same residents and spawn points, so each
  must see the previous one's committed effects.
- Deactivating a type is the global off-switch: target drops to zero and, because the planner is
  symmetric, its NPCs are unassigned on the next reconciliation.

**Your symmetric-threshold decision is implemented as asked.** A city that falls below its minimum
gets `target = 0` and every active assignment is closed, so existing Guildmasters despawn. Closures
carry `staffing_city_economy_below_minimum` rather than the ordinary demand-shrinkage reason,
because "the city ran out of food" and "the city needs fewer of these" are not the same event to
anyone reading the audit trail.

I'll restate the caveat once and then leave it with you: city supplies are drawn down continuously
by `CommodityConsumptionJob`, so a city hovering near 200 food will oscillate across the threshold
and its Guildmasters will visibly come and go. If that churn proves unwanted, a separate lower
retirement threshold is a small change to `minimum_city_supplies`'s shape.

`raw_target` is deliberately left untouched by an economic denial, so `capacity_deficit` keeps
meaning "capacity was the binding constraint" instead of quietly absorbing a different cause.

---

## 5. Files changed

**Modified (14):** `app/models/service_npc_type.rb`, `app/models/skill.rb`,
`app/models/service_npc/action_registry.rb`, `app/serializers/service_npc_registry_serializer.rb`,
`app/services/city_staffing.rb`, `city_staffing/{desired_staffing,spawn_capacity,plan,apply_plan,reconcile}.rb`,
`app/controllers/admin/city_staffing_reconciliations_controller.rb`,
`app/views/admin/city_staffing_reconciliations/show.html.erb`,
`test/services/city_staffing/{desired_staffing,spawn_capacity}_test.rb`.

**Added (9):** `app/models/service_npc_type_taught_skill.rb`,
`app/services/city_staffing/economic_eligibility.rb`, `app/services/uo_skill_roster.rb`,
two migrations, `db/seeds/{uo_skills,guildmaster_definitions}.rb`,
`lib/tasks/seed_guildmasters.rake`, `test/services/city_staffing/economic_eligibility_test.rb`.

Your uncommitted spawn-point-retirement work and the other pre-existing dirty files were not
touched. All files were written through WSL with CRLF stripped and verified at 0 CR, per
`docs/known_environment_baseline.md` §1.2.

The admin preview page now renders one plan per staffed type, with an explicit "Blocked by city
economy" banner naming the shortfall (`food 143.0/200`).

---

## 6. Verification status — incomplete, and here is exactly what is missing

| Check | Status |
|---|---|
| `ruby -c` on all 19 Ruby files + ERB parse of the view | **Passed** — 0 syntax failures |
| CRLF check on all 23 synced files | **Passed** — 0 files with CR |
| Migrations run | **Not run** |
| Rails test suite | **Not run** |
| Seeds run | **Not run** |

`bin/rails` now boots correctly (the earlier Ruby-version failure was just my non-interactive shell
missing the rbenv shims — rbenv has 3.2.2 installed), but every command dies at
`ActiveRecord::DatabaseConnectionError` for user `ultimacraft`. There is no `.env`, no `.pgpass`,
and no `DB_USERNAME`/`DB_PASSWORD` in any shell rc. I stopped there rather than hunting for the
password.

### Tests I knowingly left broken

I changed public signatures, so these still reference the old API and **will fail** until updated.
I fixed the two I could do mechanically and correctly; I did not want to write bulk edits to the
rest that I had no way to run:

- `test/services/city_staffing/plan_test.rb` — `Plan.call(city:)` now needs `service_npc_type:`
- `test/services/city_staffing/reconcile_test.rb` — assertions assume one banker plan
- `test/services/city_staffing_reconcile_concurrency_test.rb` — same
- `test/jobs/city_staffing_reconciliation_job_test.rb` — same
- `test/controllers/admin/city_staffing_reconciliations_controller_test.rb` — `@plan` is now `@plans`

### What you need to run

```bash
bin/rails db:migrate && bin/rails test
```

Then, once green:

```bash
bin/rails db:seed:uo_skills && bin/rails db:seed:guildmaster_definitions
```

The skill seed prints everything it created and flags anything matched by alias — that output is
the answer to the question I could not resolve myself, and worth reading rather than skimming.

---

## 7. Not done in this milestone

- **Milestones 2 and 3** — spawn-block configuration UI (including the taught-skill and
  economic-eligibility readouts from your Decision 4) and Guildmaster entity interaction routing.
- **The Minecraft half of Milestone 4's test list** — "allow decision spawns one NPC, deny spawns
  none". Structurally this already holds: denial means no `NpcSpawnAssignment`, and
  `ServiceNpcAssignmentReconciler` creates an entity only when an active assignment exists. But it
  is asserted nowhere yet, and the admin-readable denial reason currently stops at the Rails admin
  page rather than reaching the spawn block.
- **Idempotent-upsert proof** — the gate asks for a demonstration of no duplicate Rails row on
  repeated registration. `ServiceNpcSpawnPoints::ApplyOperation` is untouched by this milestone and
  its existing tests cover it, but I could not run them to show it.
