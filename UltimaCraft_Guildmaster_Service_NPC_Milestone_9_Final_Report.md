# Milestone 9 — Final Report

```text
Feature:                  Guildmaster Service NPC
Branch:                   feature/guildmaster-service-npc (mod) · banking (Rails)
Worktree:                 C:\projects\britannia\mod\britannia_mod_guildmaster
                          ~/ultimacraft-website (WSL)
Starting baseline commit: 50061f07 (mod) · e584f27e (Rails)
Final commits:            c473b723 (mod, 17 commits) · 35653f5 (Rails, 6 commits)
Minecraft files changed:  56 files, +7933 / -20
Rails files changed:      36 files, +1589 / -340
Database migrations:      2 (both applied and verified against the regenerated schema)
Tests:                    1752+ JUnit · 349 GameTest · 1345 Rails — all green
Build:                    clean
Dedicated server:         NOT validated — see §4
Owner live validation:    PARTIAL, then owner-overridden — see §3
Rails persistence:        verified for registry, types, taught skills, policy
Spawn policy:             verified live on a real client
Known limitations:        §5
Uncommitted/untracked:    §6
Push/merge status:        nothing pushed, nothing merged, no tags
```

---

## 1. What was built

A Guildmaster is a Rails-published `ServiceNpcType`, not a new NPC framework. No `ServiceNpcKind`
enum was created — the existing `service_npc_types` registry plus `allowed_service_keys` already
was that abstraction, and adding an enum would have been the parallel system the design forbids.

Thirteen guilds: the twelve RunUO rosters (pinned at commit `71b2794f`) plus a Farming Guildmaster
with no UO counterpart, added by owner decision. 58 taught-skill slots across 41 skills.

The training rule is the supplied UO one — 1 gold per 0.1 skill, 40.0 ceiling — computed entirely
in **integer tenths**. Milestone 0 proved the canonical precision is one decimal place, so a price
computed through `float` could differ by a coin from the price a player was shown.

## 2. Decisions that shaped it

| Decision | Consequence |
|---|---|
| Guilds teach **groups** of skills | Dissolved the "canonical skill index" problem entirely: 13 of 39 skills belong to more than one guild, so the binding is many-to-many and no index is ever persisted |
| Payment from **inventory coins**, pure-UO "takes what it takes" | Sub-gold change buys nothing, because one gold is the smallest unit that buys anything |
| Training gold credits the **city treasury** | Forced a purpose-built `guild_training` endpoint: two Rails calls cannot be atomic from the mod, and either failure order is unrecoverable |
| The spawn gate reads **treasury gold** | `silver_supply` is vestigial and seeded to 0.0 — gating on it would have meant no Guildmaster ever spawned anywhere |
| Threshold is **symmetric** | A city falling below its minimum un-staffs Guildmasters it already has. Recorded because supplies oscillate and this is visible in-game |

## 3. Owner live validation — partial, then overridden

**Observed on a real client** against Rails at `localhost:3000`:

- Bootstrap populates 23 cities and 14 service types (`bank_teller` + 13 guilds)
- The spawn block renders the taught-skill list, wrapped two per row
- The city economy readout computes against real data with correct colouring
  (`Alcohol 0/5` and `Food 0/200` red, `Gold 9/1` green)
- The gold line confirms the treasury path end to end: `City#gold_amount` → bootstrap
  `treasury.gold` → mod supply map → gate
- Spawn point reaches `PENDING_REGISTRATION` and registers once `minecraft_server_key` is set

**Not observed.** The owner explicitly approved and overrode the remaining gate, so this is recorded
as an override rather than as evidence:

- A Guildmaster entity spawning at a post
- The `{Name} the {Guild} Guildmaster` nameplate in world
- The training screen opened against a live Guildmaster
- **Any training purchase at all** — no coin has moved, no skill has risen, and no treasury credit
  has been observed outside of tests
- Persistence of a spawned Guildmaster across a restart
- A Guildmaster being un-staffed by the symmetric threshold

Against the Design Document's Definition of Done, the unmet items are *"owner performs successful
live in-game validation"* and, strictly, *"40.0 ceiling works"* and *"economic policy can permit or
deny spawning"* as observed behaviour rather than as tested behaviour. All three are covered by
automated tests; none has been seen working by a person.

`db/seeds/local_guildmaster_validation.rb` exists to make that run one command when wanted.

## 4. Not validated

- **Dedicated server.** Everything live was a single-player client with an integrated server.
  `allow_integrated_server=true` was required for that; a dedicated server does not need it, and
  that path has never been exercised.
- **Multi-client.** Transaction isolation is covered by GameTest, not by two real clients.

## 5. Known limitations

1. A crash between Rails committing and coins being taken gives free training. Deliberate: failing
   toward the player keeping their gold is recoverable; charging for a skill never persisted is not.
2. A replay (`ALREADY_APPLIED`) charges nothing, because this process cannot know whether the
   original attempt took the coins. Free training beats double billing.
3. `GuildTrainingService` and `GuildTrainingClient` have no direct unit coverage; the decisions they
   compose (`GuildTrainingQuote`, `GuildTrainingCharge`) have 23 cases, and the money-at-stake
   wiring has 7 GameTests.
4. A `MISSING` registry — Rails omitting the member — is silent. Only `REJECTED` warns, so an
   operator would see "Registry unavailable" with nothing in the log.
5. `gain_on_failure` is `false` on all 41 skills. Research found RunUO has **no per-skill flag**;
   the real axis is era (pre-AOS gains on failure at reduced weight, AOS does not), so the current
   setting is a coherent AOS ruleset rather than a defect.
6. Guildmaster staffing is admin-triggered, like banker staffing. Nothing spawns until a
   reconciliation runs.
7. `db/schema.rb` is at mode `666` from the cross-user ownership workaround; restore to `644`.

## 6. Uncommitted / untracked

**Mod worktree:**
- `gradle/wrapper/gradle-wrapper.jar` — an unsmudged Git LFS pointer restored by copying from the
  owner's tree. Must never be committed.
- `UltimaCraft_Guildmaster_Service_NPC_Initial_Prompt.md` — a prompt, not project documentation.
- `run/config/britannia_mod-server.properties` — holds a shard secret; gitignored.

**Rails:** the owner's in-flight spawn-point retirement work (controller, service, test, views,
`config/routes.rb`, `local_banking_test_bootstrap.rb`, a bank cheque design doc) was left untouched
throughout. One exception, flagged at the time: a one-line `Plan.call` signature fix was applied to
`test/controllers/admin/service_npc_spawn_point_retirements_controller_test.rb`, because this
feature's change to `CityStaffing::Plan` broke it.

## 7. Push / merge status

Nothing pushed. Nothing merged. No tags. No deploys. Both branches are local only.
