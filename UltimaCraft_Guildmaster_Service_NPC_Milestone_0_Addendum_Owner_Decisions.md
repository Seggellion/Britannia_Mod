# Milestone 0 Addendum — Owner Decisions Applied

Companion to `UltimaCraft_Guildmaster_Service_NPC_Milestone_0_Report.md`. Records the owner's
answers to the six open questions, the RunUO source data those answers depend on, and the resulting
architecture delta. **Still no functional changes.**

---

## A. RunUO Guildmaster → skill grouping (source data)

Fetched from `runuo/runuo` at pinned commit `71b2794f12eb6f948b1c5598ae8b350401a22d4d`,
`Scripts/Mobiles/Vendors/NPC/Guildmasters/`. Twelve concrete guildmasters plus `BaseGuildmaster`.

In RunUO the skills a guildmaster *teaches* are exactly the skills the class gives *itself* via
`SetSkill(...)` — `BaseVendor`'s teaching logic keys off the vendor's own skill values. So each
constructor's `SetSkill` list **is** the taught-skill group.

| # | Guild (`base("...")`) | `NpcGuild` | Taught skills (RunUO `SkillName`) | Count |
|---|---|---|---|---|
| 1 | bard | BardsGuild | Archery, Discordance, Musicianship, Peacemaking, Provocation, Swords | 6 |
| 2 | blacksmith | BlacksmithsGuild | ArmsLore, Blacksmith, Macing, Parry | 4 |
| 3 | fisher | FishermensGuild | Fishing | 1 |
| 4 | healer | HealersGuild | Anatomy, Healing, Forensics, MagicResist, SpiritSpeak | 5 |
| 5 | mage | MagesGuild | EvalInt, Inscribe, MagicResist, Magery, Wrestling, Meditation, Macing | 7 |
| 6 | merchant | MerchantsGuild | ItemID, ArmsLore | 2 |
| 7 | miner | MinersGuild | ItemID, Mining | 2 |
| 8 | ranger | RangersGuild | AnimalLore, Camping, Hiding, MagicResist, Tactics, Archery, Tracking, Stealth, Fencing, Herding, Swords | 11 |
| 9 | tailor | TailorsGuild | Tailoring | 1 |
| 10 | thief | ThievesGuild | DetectHidden, Hiding, Lockpicking, Snooping, Poisoning, Stealing, Fencing, Stealth, RemoveTrap | 9 |
| 11 | tinker | TinkersGuild | Lockpicking, Tinkering, RemoveTrap | 3 |
| 12 | warrior | WarriorsGuild | ArmsLore, Parry, MagicResist, Tactics, Swords, Macing, Fencing | 7 |

**58 taught-skill slots across 39 distinct skills.**

**Skills taught by more than one guild** — this is the fact that determines the data model:

| Skill | Guilds |
|---|---|
| MagicResist | healer, mage, ranger, warrior (**4**) |
| ArmsLore | blacksmith, merchant, warrior |
| Macing | blacksmith, mage, warrior |
| Swords | bard, ranger, warrior |
| Fencing | ranger, thief, warrior |
| Archery | bard, ranger |
| Hiding | ranger, thief |
| Stealth | ranger, thief |
| Lockpicking | thief, tinker |
| RemoveTrap | thief, tinker |
| Tactics | ranger, warrior |
| Parry | blacksmith, warrior |
| ItemID | merchant, miner |

Also recorded for context (not required by the owner's rules, but it is what RunUO does):
`BaseGuildmaster.JoinCost = 500` gold for *guild membership*, which is a separate mechanic from
skill training and is **out of scope** here.

---

## B. Decisions applied, and what each one changes

### Decision 1 — Guildmasters teach a *group* of skills

**This settles the Option A / Option B question in favour of Option A, and removes the "canonical
skill index" problem entirely.**

Twelve `ServiceNpcType` rows (`warrior_guildmaster`, `mage_guildmaster`, …), each with a taught-skill
**set**. The spawn block picks a guild from the service-type dropdown that already exists. No skill
index is ever persisted, so registry reordering cannot retarget an existing Guildmaster.

The one change from the Milestone 0 recommendation: because 13 skills appear in 2+ guilds, a single
`taught_skill_id` FK on `service_npc_types` is wrong. It must be **many-to-many**.

Recommended Rails shape — a join table rather than a jsonb array, because it gives referential
integrity, prevents a typo'd slug from ever being published, and gets an admin UI for free:

```ruby
create_table :service_npc_type_taught_skills do |t|
  t.references :service_npc_type, null: false, foreign_key: true
  t.references :skill, null: false, foreign_key: true, type: :uuid
  t.timestamps
  t.index [:service_npc_type_id, :skill_id], unique: true
end
```

Serializer emits a sorted array so the registry `revision` digest stays deterministic:

```ruby
taught_skill_slugs: npc_type.taught_skills.order(:slug).pluck(:slug)
```

Mod side: `ServiceNpcTypeDefinition` gains `List<String> taughtSkillSlugs` (empty list for
`bank_teller`, and for any older Rails server that omits the member).

`bank_teller` is untouched. No `service_npc_spawn_points` migration. No spawn wire-protocol change.

### Decision 2 — Payment from inventory coins only; more coins = more skill, up to the maximum

This is UO's real behaviour and it *simplifies* the transaction — no target stepper is needed.

Authoritative arithmetic, all in **integer tenths** (Milestone 0 §6.3):

```
currentTenths    = round(currentSkillValue * 10)
capTenths        = min(400, round(skillMaxValue * 10))        // 400 == the 40.0 NPC ceiling
headroomTenths   = max(0, capTenths - currentTenths)
availableGold    = countCoins(player) / COPPER_PER_GOLD       // integer division, floors
spendCapGold     = player's requested spend (defaults to availableGold)
purchasedTenths  = min(headroomTenths, availableGold, spendCapGold)
costGold         = purchasedTenths                            // 1 gold == 0.1 skill
costCopper       = costGold * CoinConversion.COPPER_PER_GOLD
```

Consequences to confirm in Milestone 5:

- **Silver and copper cannot buy training.** The smallest purchasable unit is 1 gold = 0.1 skill, so
  `availableGold` floors and any sub-gold remainder buys nothing. Charging is still done in copper
  through the existing `countCoins` / `removeAllCoins` / `giveChange` idiom, so the player's change
  comes back in the correct denominations.
- `purchasedTenths == 0` is a **rejection with a reason**, never a silent no-op charge — "Thou hast
  not enough gold", or "I can teach thee no more" at the 40.0 ceiling.
- **I am proposing an explicit spend field rather than silently spending everything the player is
  carrying.** It defaults to "max affordable" so the common case is one click, and it reuses
  `client/screen/bank/BankAmountInput` verbatim. Say the word if you want the pure-UO behaviour of
  simply taking what it takes.

### Decision 3 — Reuse the existing `skills/set` endpoint

Unblocks Milestone 0 blocker #1 (`skills#gain` rejects any bump > 1.0). `skills#set_skill` has no
delta limit — only `max_allowed = powerscroll ? 120.0 : skill_def.max_value`, and `.round(1)`.

Three things Milestone 5 must handle, none of them showstoppers:

1. **Ordering must be Rails-first.** `SkillManager.setSkillAdmin` writes the local map and then
   fire-and-forgets `postSetSkill` (exceptions are only logged). For a *paid* purchase that would let
   a Rails outage take the player's gold and silently lose the skill at next login. The Guildmaster
   path needs a new confirmed client — `ServerHttpExecutor` + `CompletableFuture` +
   `server.execute(...)` marshalling, exactly like `BankingOpenClient` — that **awaits a 204 before
   any coin is deducted**. Residual risk: a crash between the 204 and the deduction gives free
   training. That is strictly the better failure direction and will be documented.
2. **`skills/set` takes an absolute value,** so the mod must send `current + purchased`. The mod's
   cached value is refreshed at login and after every gain, but for a transaction I recommend
   re-reading the authoritative value from `PLAYER_SKILLS` in the same request cycle before computing
   the absolute, so a stale cache can never overwrite a newer Rails value.
3. **No idempotency key and no ledger row.** Guildmaster purchases will not be auditable in Rails,
   and a retried request is not de-duplicated (the mod's own in-flight set is the only guard).
   Accepted per your decision; recorded here so it is a known trade rather than a surprise.

Also confirmed: unknown slug → `Skill.find_by!` raises → 404; over-cap → 422 with an error body. Both
must be treated as rejections that charge nothing. Endpoint rate limit is 120/min per shard+IP.

### Decision 4 — "Recommended action" on registry version coupling

**My reading: ship the mod's parser change first, and deploy it before Rails publishes any
Guildmaster type or the `guild.train` action.** No behaviour change to the shared parser — an
unknown service key still rejects the whole registry, exactly as today.

Rationale for reading it that way: the alternative (make unknown service keys skip a single type)
changes a shared, load-bearing parser that bank tellers also depend on, and Milestone 0 §12 listed
mod-first as the lower-risk option. If you meant the parser-tolerance change instead, it is a small
reversal — say so and I will take that branch instead.

Operational consequence: **the mod update must be live on the server before the Rails seed runs.**
If Rails publishes `guild.train` to an older mod build, the entire Service NPC registry falls back to
empty and **bank tellers stop working too**.

### Decision 5 — Spawn gated on economic rules defined in Rails Admin

Minimum rule given: **200 food, 5 silver, 5 alcohol.**

Good news: `cities` already has exactly these columns as floats —
`food_supply`, `silver_supply`, `alcohol_supply` (`db/schema.rb:334`), alongside `wood_supply`,
`metal_supply`, `stone_supply`, `textile_supply`, `technology_supply`, `gold_supply`,
`copper_supply`, `reagents_supply`, plus `population`, `is_starving` and `consumption_rates`.

Recommended shape — one admin-editable jsonb column on the service type, rather than a new model:

```ruby
add_column :service_npc_types, :minimum_city_supplies, :jsonb, null: false, default: {}
# warrior_guildmaster => { "food" => 200, "silver" => 5, "alcohol" => 5 }
# bank_teller         => {}   (no requirement — existing behaviour preserved exactly)
```

Validated against a closed list of real `cities` supply columns so a typo can never silently disable
spawning. Generalises to every future service type, needs no new admin screen scaffolding, and keeps
`bank_teller` behaviour bit-for-bit unchanged.

Enforcement point: a new `CityStaffing::EconomicEligibility` check consulted from
`CityStaffing::Plan`, **not** hidden inside `SpawnCapacity.eligible_spawn_points`. Keeping it separate
preserves the honesty of `capacity_deficit` and lets the denial reason be reported per spawn point
rather than silently reducing capacity.

**One genuinely open question this raises.** City supplies fluctuate — `CommodityConsumptionJob`
draws them down continuously. So:

> When a city that already has a staffed Guildmaster drops below the threshold, should the existing
> Guildmaster be **unassigned and despawn**, or should the rule only block **new** spawns?

My recommendation is **block new spawns only**, with an optional separate, lower "retirement"
threshold if you want them to leave eventually. Enforcing a single threshold symmetrically will make
Guildmasters visibly flicker in and out as supplies oscillate around 200 food. `CityStaffing::Plan`
already has the vocabulary for this (`close` instructions with a `reason` string), so either
behaviour is cheap — but they are meaningfully different games and I do not want to pick for you.

### Decision 5 (cont.) — "The screen will have to be modified"

Concrete proposal for `client/screen/ServiceNpcSpawnScreen.java`, all additive to
`ServiceNpcSpawnStateS2CPayload` (no C2S change, no Rails wire-protocol change):

1. **Taught-skill list** for the currently selected service type, read-only, from the registry
   snapshot — so an admin placing a Warrior Guildmaster can see exactly what it will teach.
2. **Economic eligibility readout** for the selected city + type, e.g.
   `Food 143 / 200 ✗ · Silver 12 / 5 ✓ · Alcohol 7 / 5 ✓`.
3. **Reason line when registered but unstaffed**, so "I configured it and nothing appeared" is
   self-diagnosing rather than a support question.

Item 2 needs current city supply values on the mod side. They are **not** in the Service NPC caches
today; the bootstrap city payload would need to carry them (additive), or the readout limits itself
to showing the requirement without the live value. Confirm which you want — the fuller version is
more useful but touches the city bootstrap payload.

### Decision 6 — Use RunUO for the guild/skill content

Done; see §A. But it surfaces a new blocker — §C.

---

## C. New blocker: skill coverage gap between RunUO and UltimaCraft

RunUO's twelve guildmasters reference **39 distinct skills**. UltimaCraft's skill system is
substantially smaller.

What I could establish without database access:

| Source | Skills |
|---|---|
| Referenced in mod Java source | `blacksmithy` (`BlacksmithCrafting.PRIMARY_SKILL_ID`), `farming` (`FarmingSkill.SKILL_ID`), plus literals `blacksmith`, `carpentry`, `discordance`, `fishing`, `lockpicking`, `magery`, `musicianship`, `peacemaking`, `provocation`, `tailoring` |
| Published on the live play guide (`/playguide/skills`) | `discordance`, `fishing`, `musicianship`, `peacemaking`, `provocation` — 5 only |

Neither is the real list. `skills/config` returns `Skill.all` (including unpublished rows), and I
could not enumerate the table: `bin/rails runner` fails on a benign Ruby version mismatch
(**installed 3.2.3 vs Gemfile 3.2.2** — I did not modify the Gemfile), and `psql` requires a password
I do not have and did not go looking for.

Two consequences:

1. **The RunUO `SkillName` → UltimaCraft slug mapping is not mechanical.** RunUO's `Blacksmith`
   appears to be UltimaCraft's `blacksmithy`; `EvalInt`, `MagicResist`, `ItemID`, `DetectHidden`,
   `RemoveTrap`, `SpiritSpeak`, `AnimalLore` and `ArmsLore` have no confirmed counterparts at all.
   This needs an explicit, owner-reviewed mapping table before seeding.
2. **Most of the 39 skills probably do not exist as `skills` rows,** and of those that do, only a
   handful have gameplay effects in the mod (fishing, blacksmithy, lockpicking, farming, the bard
   skills, magery). A Guildmaster selling `Camping` or `Forensics` would be taking real gold for a
   number that does nothing.

**Recommendation:** seed each guild's taught list as the **intersection** of the RunUO group with the
skills that actually exist in the `skills` table, and have the seed print exactly what it skipped.
A Guildmaster then never sells a skill that does not exist, the seed is re-runnable as you add
skills, and the RunUO groupings stay the canonical source.

**What I need from you:** either the output of `Skill.order(:slug).pluck(:slug)` from the development
database, or confirmation to use the seed-time-intersection approach and let the data decide.

---

## D. Architecture delta vs. the Milestone 0 report

| Milestone 0 said | Now |
|---|---|
| One `ServiceNpcType` per **skill** (~N rows) | One per **guild** — exactly **12** rows |
| `taught_skill_id`, single nullable FK | Many-to-many join table; 13 skills are shared across guilds |
| Serializer emits `taught_skill_slug` | Emits `taught_skill_slugs: []` (sorted) |
| Training UI needs a target stepper (`- [XX.X] +`) | Skill picker + optional spend field; amount drives the gain |
| Recommended a new `guild_training` endpoint + ledger | Reuse `skills/set`; no ledger, no idempotency key (accepted) |
| Recommended prepare/confirm for atomicity | Rails-first-then-charge ordering; documented residual crash window |
| `CityStaffing` parameterised by profession | Same, **plus** a `minimum_city_supplies` economic gate |
| Spawn screen unchanged (Option A's main selling point) | Screen **is** modified — additive S2C fields only; C2S and the Rails spawn wire contract stay unchanged |

Unchanged from Milestone 0: integer-tenths arithmetic, the 400-tenth ceiling, no total skill cap,
`slug` as the stable identity, compose-don't-inherit for the Bank UI shell, inventory coins charged
via `MerchantEconomyService`, and the mod needing **no** `ServiceNpcSpawnPolicy` seam.

---

## E. Revised file list (deltas only — see Milestone 0 §11 for the base)

**Added to the Rails side:** `db/migrate/*_create_service_npc_type_taught_skills.rb`,
`db/migrate/*_add_minimum_city_supplies_to_service_npc_types.rb`,
`app/models/service_npc_type_taught_skill.rb`,
`app/services/city_staffing/economic_eligibility.rb`,
`db/seeds/guildmaster_definitions.rb` (12 types + dialogue + RunUO groups, intersected),
admin views for taught skills and minimum supplies.

**Removed from the Milestone 0 plan:** `app/controllers/api/guild_training_controller.rb`,
`app/services/guild_training/*`, `db/migrate/*_create_guild_training_operations.rb` — all superseded
by Decision 3.

**Added to the mod side:** `service/guild/GuildmasterSkillSetClient.java` (+ `ClientPort`) — the
Rails-confirmed `skills/set` caller; additive fields on `ServiceNpcSpawnStateS2CPayload`.

**Changed on the mod side:** `ServiceNpcTypeDefinition` takes `List<String> taughtSkillSlugs`;
`GuildmasterTrainingScreen` gains a skill picker; `ServiceNpcSpawnScreen` gains the taught-skill and
eligibility readouts.

---

## F. Test plan deltas

New JUnit: taught-skill-list parsing (multi-skill, empty for `bank_teller`, absent member on an older
server, duplicate slug rejected); coin-scaled quote arithmetic — `availableGold` floors sub-gold
remainders, `purchasedTenths = min(headroom, gold, spendCap)`, zero-purchase rejections at the ceiling
and at zero gold, exact-funds purchase, 400-tenth ceiling.

New GameTest: a guild teaching a skill it does not own is rejected; two guilds sharing `MagicResist`
both train it correctly; Rails 404/422/timeout each leave the player's coins untouched; a successful
purchase deducts exactly `purchasedTenths` gold and returns correct change.

New Rails Minitest: join-table uniqueness; serializer array is sorted and revision-stable;
`minimum_city_supplies` key validation; `EconomicEligibility` at/above/below each threshold;
`bank_teller` unaffected by an empty requirement; seed intersection skips unknown slugs and is
idempotent.

---

## G. Second round of owner decisions — resolved

| # | Question | Owner decision |
|---|---|---|
| 1 | Skill slugs, or approval for seed-time intersection | **"Whatever is recommended"** → seed-time intersection of each RunUO group with the real `skills` table; seed prints what it skipped |
| 2 | Economic-gate symmetry | **Dropping below threshold despawns an existing Guildmaster** (symmetric enforcement) |
| 3 | Spend field | **Pure UO — "takes what it takes."** No amount field |
| 4 | Screen eligibility readout | **Show both** the live city supply values and the requirement |
| 5 | Registry version coupling | See §H — resolved as tolerant parsing |

### G.1 Consequences of Decision 2 (symmetric despawn) — two new work items

**Despawn itself is already free.** `ServiceNpcAssignmentReconciler.reconcileBlock` calls
`findActiveAssignment(...)`; when it returns null it runs `nearby.forEach(ServiceNpcEntity::discard)`
and clears the block entity's assignment fields. So closing the Rails assignment is the only trigger
needed — no mod change at all.

But two things on the Rails side do not currently exist:

1. **Nothing runs staffing reconciliation automatically.** `config/recurring.yml` schedules only
   `WorldStateChangesPruneExpiredJob` (hourly), `AccountReconciliationSweepJob` (daily 4am) and
   `WorldSyncFallbacksPruneExpiredJob` (hourly). `CityStaffingReconciliationJob` is queued **only**
   from `Admin::CityStaffingReconciliationsController#create` — an admin button. For supplies to
   actually despawn a Guildmaster, a recurring all-cities staffing task must be added to
   `config/recurring.yml`. **Cadence is now a gameplay decision, not just an ops one:** it sets how
   long a Guildmaster lingers after the city goes hungry, and how much they flicker as supplies
   oscillate around 200 food. Proposal: **hourly**, matching the existing world-state cadence.
2. **A scheduled run has no admin user to attribute.** `CityStaffing::Reconcile.call(city:, admin:)`
   writes `AdminActionAudit.record!(admin: admin, ...)` inside its transaction, and
   `admin_action_audits.admin_user_id` is `null: false`. A system-triggered reconciliation therefore
   cannot write that audit row as-is. Two clean options: a dedicated system `User` row used as the
   actor for automated runs (recommended — keeps the audit trail complete and the schema unchanged),
   or making the audit optional for system runs (loses the trail). **Recommending the system user.**

Optional hysteresis, if flicker turns out to be visible in play: a separate, lower retirement
threshold (e.g. spawn at 200 food, retire below 150). Not building it now — noted so it is a known
lever rather than a rediscovery.

### G.2 Consequence of Decision 3 (pure UO) — the training screen gets simpler

No amount field, so `BankAmountInput` is no longer needed. The screen becomes: NPC portrait and name,
`{Guild} Guildmaster` role line, the list of skills this guild teaches with the player's current
value beside each, and a Train button per skill. One click = one server request carrying only the
chosen skill slug. The server does the rest:

```
purchasedTenths = min(headroomTenths, availableGold)
costGold        = purchasedTenths
```

Rejections still need distinct, diegetic reasons — no gold, already at the 40.0 ceiling, skill not
taught by this guild, Guildmaster no longer available.

### G.3 Consequence of Decision 4 — city supplies must reach the mod

The spawn screen needs live `food_supply` / `silver_supply` / `alcohol_supply` for the selected city.
These are **not** in any mod-side cache today. `BootstrapCityDefinition` carries only `publicId` and
`displayName`. Required: additive supply fields on the bootstrap city payload →
`BootstrapCityDefinition` → `ServiceNpcSpawnStateS2CPayload`. All additive; no existing consumer
changes. Values are a cached snapshot, so the screen must label them as such rather than implying
they are live-to-the-second.

---

## H. Decision 5, restated plainly — registry version coupling

**The problem.** The mod checks every service name Rails sends against a hardcoded list — today just
`bank.open` and `bank.create_check` (`ServiceNpcRegistryParser.java:21`). Guildmasters add
`guild.train`. When the mod meets a name it does not recognise it does not skip that one NPC type; it
throws away the **entire** Service NPC configuration and falls back to the empty snapshot. Bank
tellers live in that same configuration and are resolved through it
(`BankingCapability.supportsBankOpen`). So **an old mod build plus a new Rails deploy means bank
tellers stop working**, not just "Guildmasters don't appear."

**Option A — be careful about deploy order.** Update the mod first, then run the Rails seed. No code
changes. Simple, but fragile: reverse the order once and banking is down until the mod catches up.

**Option B — make the mod skip what it does not recognise.** An unknown service name skips only that
NPC type. Deploy order stops mattering permanently: an old mod ignores Guildmasters, bankers keep
working, and the Guildmasters appear when the mod is updated.

**Decision: Option B, narrowly scoped.** Keep rejecting the whole registry for genuinely broken data
— missing fields, wrong shapes, dangling dialogue references, bad interpolation — because that
signals corruption. Treat an unrecognised service *name* differently: that signals "this type came
from a newer version", so skip the type and keep the rest. This is the standard forward-compatibility
rule for versioned protocols, and it removes a whole class of wrong-order-deploy outages for every
future service type, not just this one.

This is a deliberate, tested change to a shared parser that banking depends on, so per the playbook's
change discipline it is flagged rather than made silently. It carries its own tests: an unknown
service key skips exactly one type and leaves `bank_teller` intact; a type left with an empty
`allowed_service_keys` after skipping is itself dropped; structurally malformed input still rejects
the whole registry as it does today.

---

## I. Remaining gate

Everything is now decided. The only outstanding item is **explicit authorisation to begin
Milestone 1** — the playbook gates implementation on owner review of Milestone 0.

---

*Still Milestone 0. No functional changes, nothing committed, nothing pushed.*
