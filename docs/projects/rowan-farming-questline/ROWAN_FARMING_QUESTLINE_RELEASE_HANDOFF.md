# Rowan the Farmer — From Soil to Supper: release handoff

**Status: engineering complete, live acceptance not started.** Every milestone from M0 to M11 has
passed its gates and is committed locally in both repositories. **Nothing has been pushed, merged,
deployed, seeded to a live database, or uploaded.** The remaining work is the owner's: the live
acceptance walkthrough (M12) and the release actions listed at the end, in the order given.

---

## 1. Milestones and SHAs

| Milestone | NeoForge | Rails |
| --- | --- | --- |
| M0 Baselines, worktrees, executable contracts | `45ad0434` | `3400742` |
| M1 Quest security and permanent-item safety | `85291cb5` | — |
| M2 Rails reward-delivery ledger and replay | — | `78d640a` |
| M3 NeoForge reward reconciliation | `71f1713b` | — |
| M4 Rails action-objective and progress contract | `14cb55d6` (record) | `0fff0b3` |
| M5 NeoForge farming events, outbox, crop attribution | `473917c0` | — |
| M6 Rowan archetype and spawn integration | `6b783003` | — |
| M7 Rails questline content, seed, admin | `29fe5cfd` (record) | `abea5ff` |
| M8 Dialogue and journal UX | `75bdb1c5` | — |
| M9 Timing, skill recovery, player recovery | `6fe3f1e9` | `39d14b3` |
| M10 Achievement and advancement | `994d9b7c` | `0aed9dd` |
| M11 Hardening and automated acceptance | `8d2dbcfa` | `7602ede` |
| M12 Release handoff | this commit | — |

**Branches (local, unpushed):** `claude/rowan-farming-questline-mod` and
`claude/rowan-farming-questline-rails`.
**Bases:** the mod from `patch-18` at `421e2785`; Rails from `release/public` at `a9425ca`.
**Worktrees:** `C:\projects\britannia\mod\Britannia_Mod\.claude\worktrees\rowan-farming-questline-mod`
and `/home/dusti/ultimacraft-website/.claude/worktrees/rowan-farming-questline-rails`.

**Working-tree state:** both clean at the final commit. The canonical checkouts were never switched,
stashed, reset or modified; the owner's uncommitted work in the Rails checkout and the untracked
notes in the mod checkout are exactly as they were.

**Size of the change:** mod 183 files, +29,448 −490. Rails 90 files, +13,390 −172.

---

## 2. Test totals at the final commit

| Suite | Result |
| --- | --- |
| Mod unit tests | **3902 tests, 0 failures, 0 errors, 23 skipped** (487 suites; baseline at the branch point was 3479) |
| Mod GameTests | **1167 required tests passed, 0 failed** (baseline 1102) |
| Rails full suite | **3517 runs, 54,113 assertions, 13 failures, 1 error, 1 skip** |
| Rails quest selection | **838 runs, 0 failures** |
| Frozen contract fixtures | **12 of 12** verify against the manifest on both sides of the mirror |

The Rails failures are **entirely pre-existing** and are explained mechanism by mechanism in
`docs/rowan_farming_questline_preexisting_failures.md` in the Rails repository. Summary: seven tests
assert an unscoped count of administrative audit rows that two non-transactional concurrency files
commit and that a database trigger makes undeletable; four read unscoped bank aggregates that other
races deliberately leave behind; one follows a redirect into a controller whose layout an earlier
test disabled and never restored. Two fail deterministically rather than by ordering — one test
whose assertion contradicts the method it calls, and one genuine finding described in §7. All
thirteen reproduce at the commit this project branched from, at the same line numbers, and every
file involved is byte-identical between the two commits.

The 23 mod skips include 6 opt-in live-Rails tests that skip unless credentials are supplied.

---

## 3. Candidate build

Rebuild from the final commit on a clean tree:

```bash
"C:/Users/dusti/.gradle/wrapper/dists/gradle-8.9-bin/90cnw93cvbtalezasaz0blq0a/gradle-8.9/bin/gradle.bat" build -x test --no-configuration-cache --console=plain --gradle-user-home C:/Users/dusti/.gradle
```

| Artifact | Value |
| --- | --- |
| Jar | `build/libs/britannia_mod-0.1.8a.jar` |
| Fat jar | `build/libs/britannia_mod-0.1.8a-all.jar` |
| Mod version | `0.1.8a` (unchanged by this project) |
| Mod id / display name | `britannia_mod` / Britannia |
| Build metadata | `britannia_mod_build.properties` records `git.head`, `git.branch`, `git.dirty` |

**Verified clean-tree build** at `402f5585`, the commit that introduced this document:

| Property | Value |
| --- | --- |
| `git.head` | `402f5585e63a08dce303e646dd48d9ed96d731cb` |
| `git.branch` | `claude/rowan-farming-questline-mod` |
| `git.dirty` | `false` |
| Build timestamp | 2026-09-08T10:23:38Z |
| `britannia_mod-0.1.8a.jar` | 34,353,079 bytes, SHA-256 `3ae8ddbd9299013fcacffcd3b4d6ab8b4c8a5dfef9fe04b5a441abd98e96ebe5` |
| `britannia_mod-0.1.8a-all.jar` | 34,924,717 bytes, SHA-256 `d10b982273bbb5f0849be89fd50d232a629d3b7df3017c340c8c7ec79f2ef09e` |

**The jar is not byte-reproducible** on this project — a known property of this build. An earlier
build of the identical source, differing only in that one documentation file was uncommitted,
produced a jar one byte larger with a completely different digest. Verify a candidate by reading
`git.head` and `git.dirty` from `britannia_mod_build.properties` inside the jar, not by comparing
digests across machines or rebuilds.

---

## 4. Rails migrations and seeds

**Migrations, in order.** Heroku runs `db:migrate` itself in the release phase — **never run it by
hand**:

1. `20260906120000_create_quest_reward_deliveries`
2. `20260907120000_add_journal_fields_to_quests`
3. `20260907121000_create_quest_action_events`
4. `20260907130000_create_quest_equipment_reissues`

Schema stamp `2026_09_07_130000`. Each was proven reversible and re-appliable on a disposable
database, with an in-memory dumper comparison against the hand-edited `db/schema.rb`. **Never
re-dump `schema.rb`** on this PostgreSQL version.

**Seed, run once per shard after migration:**

```bash
ROWAN_QUESTLINE_SHARD="Britannia" bin/rails db:seed:rowan_farming_questline
```

It is idempotent: running it twice leaves the rows byte-identical, does not touch a timestamp, and
does not disturb a player already partway through. It installs five quests and twenty-five nodes and
resolves the prerequisite chain. It is **narrow** — it touches only this questline's content and no
other seed.

**New API endpoints** (all on the signed v2 tier with a per-server key):

```
GET  /api/v2/quest_reward_deliveries/pending
POST /api/v2/quest_reward_deliveries/:delivery_uuid/result
POST /api/v2/quest_action_events
POST /api/v2/quest_equipment_reissues
```

The world bootstrap gains an additive `pending_reward_deliveries` array.

---

## 5. Deployment order and rollback

**Order matters.** Rails is backward compatible with the current mod; the new mod is *not* useful
without the new Rails.

1. **Deploy Rails first.** The release phase migrates. Old mod builds keep working: the reward
   delivery is additive (they read `granted_items` as before), the new endpoints are simply unused,
   and the journal gains fields old clients ignore.
2. **Seed the questline** per shard. Until Rowan is placed, nothing is reachable by players.
3. **Deploy the mod jar**, then restart the Minecraft server.
4. **Place Rowan** through the existing quest-giver spawn block (§6).

**Rollback.**

* *Mod only:* redeploy the previous jar. Rails keeps its rows; deliveries already acknowledged stay
  acknowledged; pending ones simply wait. No data is lost, no migration is reversed.
* *Rails:* the four migrations are reversible and were proven so, but rolling them back **destroys
  the reward-delivery, action-event and reissue history**, including deliveries a shard has not yet
  acknowledged. Prefer redeploying the previous application version and leaving the tables in place
  — they are inert to older code.
* *Content only:* the questline can be withdrawn without a rollback by setting the five quests
  inactive in the admin interface. Players mid-questline keep their journal rows.

---

## 6. Operator setup

**Required before a player can start.**

1. **Place Rowan** with `britannia_mod:quest_giver_spawn_block`, choose the **Rowan** archetype, set
   the city, gender, wander radius and optionally the local directions hint, then Save. Configuring
   requires Creative or permission level 2 **and** standing within reach of the block. Two or more
   Rowans may be placed; they share one player's progress and can carry different local directions.
2. **Public infrastructure near each Rowan**, since the questline uses only ordinary gameplay:
   * exposed dirt or coarse dirt (not grass) within roughly 20 blocks, so tracked dung can spawn;
   * a **Water Well** — in Adventure mode a bucket cannot be filled from natural water;
   * at least four base **Community Farm Blocks** outside spawn protection.
3. **Farming skill** must exist on the shard (slug `farming`).
4. **Portrait**: upload `Rowan.png` to the portrait bucket under the gender you placed. **Not done —
   this is one of the owner-only actions.** Until then the generic portrait renders, which is not a
   questline failure. Note the pre-existing client behaviour that one missing portrait pins the
   fallback for the rest of that client session.

**Public-plot timing changed for everyone, not just questers.** A hoed public plot now reverts after
600 seconds rather than 180, and a fertilized one after 300 rather than 60, which also lengthens the
post-harvest replant window. Private and house plots are untouched. These are the playbook's own
values and they make public farming more forgiving, but they are live for all players.

---

## 7. Unresolved risks and owner decisions

**Needs a decision before launch:**

* **Completing the questline announces shard-wide.** This matches the existing challenge
  achievement, but it is a policy choice.
* **Players who already finished quest 5** would hold the website achievement without the in-game
  advancement, with no authoritative response left to grant one. A backfill would be a separate
  deliberate task.
* **Reward values** are the playbook's provisional constants — 2 gold, 50 copper, 20 silver, 3 gold,
  5 gold — kept in one table. No newer economy authority exists in the repository for quest coin
  rewards; two of the five are independently pinned by the frozen fixtures.

**Genuine defects found in passing, outside this project, not fixed here:**

* **Two public unauthenticated endpoints exceed the query budget the platform set for itself**, with
  identical measurements at this branch and at the commit it branched from. This is a real
  performance finding and should not be silenced.
* One economy test's assertion contradicts the method it calls, so it fails deterministically while
  the economy computes the right number.
* Another questline's advancement is untranslatable and its icon item has no localization entry, so
  players see a raw identifier. Guarded by a test that fails once either is fixed.
* The quest-giver spawn block never persists an operator's custom Rails identity, so a Generic
  Combat spawner would lose it if it had to respawn after a chunk reload. Raised as a separate task.

**Known limitations of this work:**

* The configuration screen cannot show a spawner's **current** directions hint, so a hint can be
  replaced but not cleared from the interface.
* Countdown warnings are not persisted, so a plot reloaded mid-window may re-announce its current
  mark.
* The Rails test suite **cannot run in parallel on this machine** — its host-based authentication
  trusts specific database names, so forked workers fail to authenticate. Every full run is serial
  and takes 15 to 25 minutes.
* Clientbound custom payloads cannot be observed in the GameTest harness, which is why the payload
  sanitization is asserted through the payload's own codec instead.

---

## 8. What automated testing cannot establish

These need a running client and a live server, and are exactly what the acceptance walkthrough is
for:

* the parchment art, Rowan's portrait, item sprites and real font metrics — the 32 committed
  artifacts are **layout renderings, not screenshots**;
* the achievement toast and its sound;
* mouse and keyboard navigation as behaviour rather than as geometry and widget order;
* a long-localized-string dialogue, the one visual-acceptance row with no artifact;
* whether the expedited dung schedule actually produces a findable pile in populated terrain, since
  caps, spacing and substrate rules may still refuse every probe;
* whether 600 and 300 seconds are the right *feel* rather than merely the right *numbers*;
* end-to-end skill recovery against a real Rails outage.

---

## 9. Live acceptance prerequisites

Before starting `ROWAN_FARMING_QUESTLINE_ACCEPTANCE_DRAFT.md` — **58 items, all still unchecked,
because no live acceptance has been performed**:

1. Rails deployed and migrated; the questline seeded on the target shard.
2. The candidate jar deployed and the Minecraft server restarted.
3. Two Rowans placed in different locations, with the infrastructure of §6 near each. Record both
   positions.
4. A test account that is **not** an operator, starting with an empty inventory.
5. `randomTickSpeed` and the region's climate recorded, since crop growth depends on both.
6. The portrait uploaded, or the generic fallback explicitly accepted for the run.

Record observed inventory accounting, coordinates, timings, screenshots, logs and persistence
results. **Expected source behaviour is not live evidence.**

---

## 10. The owner-only actions this project deliberately did not take

* `git push` of either branch.
* Any merge into `patch-18` or `release/public`.
* Any deployment, including the Heroku release phase.
* Any seed against a development or production database. Every seed and migration in this project
  ran only against disposable databases, which were dropped.
* The `Rowan.png` portrait upload.

Every one is prepared and documented above so that each is a single reviewable step.
