# Rowan the Farmer — From Soil to Supper: release handoff

> **Read §5 before deploying anything.** This document was written for the earlier
> reward-delivery release and revised on 2026-09-09 for **strict item hand-in**. §1 and §2
> are a historical record of that earlier integration and are marked where they no longer
> describe what is being released; §3, §5 and §9 have been corrected. The authoritative
> release order is `ROWAN_FARMING_QUESTLINE_PROTOCOL.md` §1.5.7, reproduced in §5. The mod
> half of the hand-in work is recorded in `ROWAN_FARMING_QUESTLINE_HANDIN_STATUS.md`.

**Status: engineering complete and locally integrated, live acceptance not started.** Every
milestone from M0 to M11 passed its gates, and a later integration milestone merged both feature
branches into their local target branches: `patch-18` in the mod, `release/public` in Rails.
**Nothing has been pushed, deployed, or seeded to a live database.** The remaining work
is the owner's: the live acceptance walkthrough (M12) and the release actions listed at the end, in
the order given.

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

**Feature branches (local, unpushed):** `claude/rowan-farming-questline-mod` at `fc08112c` and
`claude/rowan-farming-questline-rails` at `7602ede`. Both are preserved; neither was deleted.
**Bases:** the mod from `patch-18` at `421e2785`; Rails from `release/public` at `a9425ca`.
**Worktrees:** `C:\projects\britannia\mod\Britannia_Mod\.claude\worktrees\rowan-farming-questline-mod`
and `/home/dusti/ultimacraft-website/.claude/worktrees/rowan-farming-questline-rails`.

**Integration (local, unpushed):** the mod merged into `patch-18` as `30d3e2c4`, a true merge
commit, because the target had moved on to `b9a0662f` (see §7). Rails fast-forwarded `release/public`
to `7602ede`, since the target had not moved. Both target branches are ahead of their remotes and
neither has been pushed.

**Working-tree state:** both feature worktrees clean at their final commits. The canonical checkouts
were never switched, stashed, reset or modified; the owner's uncommitted work in the Rails checkout
and the untracked notes in the mod checkout are exactly as they were, verified by content hash
before and after integration.

**Size of the change:** mod 184 files, +29,739 −490 against the base. Rails 90 files, +13,390
−172. The integrated `patch-18` differs from that same base by 185 files, +29,959 −490 — the extra
file and lines are the target's own spawner-persistence fix and the regression tests this
integration added around it.

---

## 2. Test totals at the integrated commit

> **Superseded — these are not the gate results for the release.** They were measured on the
> integration commit `2c63b207`, which **predates the entire strict item hand-in half of this
> release**: 18 commits — eleven of them the hand-in work — and 64 source files later (§3). Read this section as a record of the
> reward-delivery integration and nothing more. The hand-in gates are in
> `ROWAN_FARMING_QUESTLINE_HANDIN_STATUS.md`.

Measured on the integration commit, not on the feature branch.

| Suite | Result |
| --- | --- |
| Mod unit tests | **3908 tests, 0 failures, 0 errors, 23 skipped** (487 suites; 3902 at the feature tip, 3479 at the branch point) |
| Mod GameTests | **1172 required tests passed, 0 failed** (1167 at the feature tip, 1102 at the branch point) |
| Rails full suite | **3517 runs, 54,214 assertions, 9 failures, 0 errors, 1 skip** |
| Rails quest selection | **1297 runs, 7,606 assertions, 0 failures** |
| Rails known-family control, fresh database | **56 runs, 248 assertions, 0 failures** |
| Frozen contract fixtures | **12 of 12** verify against the manifest on both sides of the mirror |

The mod gains 5 GameTests and 6 unit tests over the feature tip. Both deltas are exact and account
for every new test: 3 GameTests came in with the target's own commit, 2 more were added by the merge
to assert the two NBT keys together, and the 6 unit tests cover the two behavioural audit fixes.

The Rails run is the same suite against the integrated `release/public`; the failure count differs
from run to run because most of these failures are order-dependent, and the set below is a subset of
the set analysed at M11, not a new one.

The Rails failures are **entirely pre-existing** and are explained mechanism by mechanism in
`docs/rowan_farming_questline_preexisting_failures.md` in the Rails repository. Summary: seven tests
assert an unscoped count of administrative audit rows that two non-transactional concurrency files
commit and that a database trigger makes undeletable; four read unscoped bank aggregates that other
races deliberately leave behind; one follows a redirect into a controller whose layout an earlier
test disabled and never restored. Two fail deterministically rather than by ordering — one test
whose assertion contradicts the method it calls, and one genuine finding described in §7. All
thirteen reproduce at the commit this project branched from, at the same line numbers, and every
file involved is byte-identical between the two commits.

The nine that surfaced in this ordering map onto that catalogue with nothing left over:

| Failing class | Count | Mechanism |
| --- | --- | --- |
| `Admin::RedeemShardResetsControllerTest` | 4 | unscoped `AdminActionAudit.count` |
| `Admin::RedeemsControllerTest` | 3 | unscoped `AdminActionAudit.count` |
| `Admin::BankReconciliationsControllerTest` | 1 | unscoped bank aggregate |
| `Economy::CityFoodSupplyRecalculatorTest` | 1 | deterministic; the assertion contradicts the method |

All four classes pass together on a fresh database — the control run in the table above, 56 runs with no
failures. `ShardPlatformQueryBudgetTest` did not fail in this ordering, which is a property of the
ordering and not a fix: the two public endpoints are still over budget, and that finding in §7
stands unchanged.

The 23 mod skips include 6 opt-in live-Rails tests that skip unless credentials are supplied.

---

## 3. Candidate build

> **Superseded for the Patch 18 release.** This section used to record a `0.1.8a` candidate —
> the version already deployed, so a rebuild of it could not be told apart from what is running.
> The release now goes out as **`0.1.8b`**, and the artifact is
> `build/libs/britannia_mod-0.1.8b-all.jar`. The dependency-free jar is now named
> `britannia_mod-0.1.8b-thin.jar` rather than holding the unclassified name, and
> `./gradlew check` runs `verifyDeployableJar`, which fails the build if the deployable jar
> has lost GeckoLib or nanohttpd. See "Deploying — which jar" in `README.md`.
>
> The artifact table below has been corrected to `0.1.8b`. The pinned candidate identity that
> used to follow it has been **removed rather than refreshed** — that candidate was built
> before the hand-in work existed, and the jar is not byte-reproducible, so no digest written
> here would survive a rebuild.

| Artifact | Value |
| --- | --- |
| **Deployable jar** | `build/libs/britannia_mod-0.1.8b-all.jar` — **deploy this one.** Only one copy exists, and it is *not* in the canonical checkout; the absolute path is in "Where the release artifact is" below |
| Thin jar | `build/libs/britannia_mod-0.1.8b-thin.jar` — **NOT deployable.** No bundled GeckoLib or nanohttpd; it boots clean and then throws `NoClassDefFoundError` at the first animated render |
| Mod version | `0.1.8b` — bumped from `0.1.8a` by the release commit, so a rebuild can be told apart from what is already running |
| Mod id / display name | `britannia_mod` / Britannia |
| Build metadata | `britannia_mod_build.properties` records `mod.version`, `git.head`, `git.branch`, `git.dirty`, `build.timestamp` |

Go by the **`-all`** classifier in the filename, never by file size: the two jars are within a few
percent of each other. `./gradlew check` runs `verifyDeployableJar`, which fails the build if the
`-all` jar has lost either bundled dependency or cannot say which commit produced it.

### Where the release artifact is, and the four stale jars that are not it

**The release artifact, by absolute path — the only copy of it that exists:**

```
C:\projects\britannia\mod\Britannia_Mod\.claude\worktrees\rowan-mod-remediation\build\libs\britannia_mod-0.1.8b-all.jar
```

The remediation fixed the build. It could not reach back into artifacts that were already written,
and **the canonical checkout holds no `0.1.8b` at all.**
`C:\projects\britannia\mod\Britannia_Mod\build\libs\` — the first place an operator looks —
holds two `0.1.8a` jars instead, and both are traps:

| Stale file in the canonical checkout's `build\libs\` | Size | Why it is dangerous |
| --- | --- | --- |
| `britannia_mod-0.1.8a-all.jar` | 35,044,450 B | **The dangerous one, precisely because it works.** It bundles GeckoLib and nanohttpd, it was built from `57136c93`, and it *can* perform a hand-in — so it boots, runs, and behaves. But it records `mod.version=0.1.8a`, **the version already deployed**, so nothing a running server reports separates it from the build that is already there: the same version string, leaving only the SHA-256 to tell them apart. It also records **`git.dirty=true`** — its bytes came from a working tree that no commit names, so `57136c93` says where it started, not what is in it. |
| `britannia_mod-0.1.8a.jar` | 34,472,812 B | The **unclassified thin jar** — the exact trap the `-thin` classifier removed from the build, still sitting on disk under the name an operator reads as "the release". Its `META-INF/jarjar/` holds nothing, so a server given this file starts clean and then throws `NoClassDefFoundError` at the first animated render. Also `git.dirty=true`. |

Two more `0.1.8a` pairs sit in other worktrees. Both are **pre-hand-in**: neither commit contains
`1e298361`, so neither jar can perform a hand-in at all.

* `...\.claude\worktrees\integration-rowan-patch18\build\libs\` — `britannia_mod-0.1.8a-all.jar`
  (34,925,373 B) and `britannia_mod-0.1.8a.jar` (34,353,735 B), both from `2c63b207`. The `-all` one
  is the withdrawn candidate the next subsection tells you not to deploy — clean-tree, correctly
  packaged, entirely convincing, and **still physically on disk**.
* `...\.claude\worktrees\rowan-farming-questline-mod\build\libs\` — `britannia_mod-0.1.8a-all.jar`
  (34,924,717 B) and `britannia_mod-0.1.8a.jar` (34,353,079 B), both from `402f5585` on the feature
  branch, older still.

**None of these five files has been deleted, and none of them should be trusted.** They are kept as
evidence of what was built when — one of them is the closest local reference to what production is
actually running. Treat every `build\libs\` other than the one named at the top of this subsection
as empty: **clear it or ignore it.** Do not open a `build\libs\` and take the newest file, and do
not go by size — the four stale jars are within two percent of the real one, and two of them carry
the `-all` classifier that means "deployable".

**Verify the candidate before uploading it, and again after the restart.**

1. `./gradlew artifactIdentity` prints the size and SHA-256 of the jar it just built, marks which of
   the two is deployable, and prints at all only for a jar that passed `verifyDeployableJar`. Take
   the digest from **the same invocation you deploy from** — the jar is not byte-reproducible (see
   below), so a digest from an earlier run belongs to a different file.
2. On the **still-running** server, `/grabby env` reports the live build's `commit=` and `sha256=`.
   Record both before you upload anything. If the candidate's SHA-256 equals the one already
   running, you are about to upload the build that is already there. If a candidate reports
   `version=0.1.8a` beside `commit=57136c93`, you have picked up the stale jar from the canonical
   checkout rather than the release.
3. Upload only when the digest of the file on disk matches what `artifactIdentity` printed. After
   the restart, read `/grabby env` again: `commit=` must be the commit you meant to release,
   `sha256=` must be that same digest, and `dirtyWorkingTree=` must be `false`.

**No size or SHA-256 for the `0.1.8b` jar is written here, on purpose.** It is rebuilt from the
release-branch tip after these documents are committed, so any number pinned here would name a file
that no longer exists — the same reason the old candidate's digests were deleted rather than
refreshed. The artifact's identity is printed by `./gradlew artifactIdentity` and embedded in the
jar as `britannia_mod_build.properties`, which is exactly what `/grabby env` reads back off a
running server. That pair is the identity; a number copied into a document is not.

### There is no standing candidate. Do not deploy the `2c63b207` jar.

The candidate this section used to pin — built clean-tree from `2c63b207` on
`integration/rowan-patch18` — **must not be deployed.** Its `git.head`, branch, timestamp, sizes
and SHA-256 digests have been deleted rather than replaced; see "not byte-reproducible" below for
why no new digest is pinned in their place.

**The claim that made that jar look safe was false.** This document previously stated that
`git diff 2c63b207 patch-18 -- src/` was empty and that the single commit after `2c63b207` was
"this documentation record and nothing else". Re-verified 2026-09-09 with Windows git:

| Check | Recorded here before | Actual |
| --- | --- | --- |
| Commits in `2c63b207..patch-18` | "one commit … documentation" | **18 commits** |
| `git diff --stat 2c63b207 patch-18 -- src/` | "is empty" | **64 files changed, 8,677 insertions, 155 deletions** |
| `git diff --stat 2c63b207 96948296 -- src/` (release-prep tip) | — | **65 files changed, 8,878 insertions, 155 deletions** (20 commits) |

**Eleven of those 18 commits are the strict item hand-in half of this release** — `1e298361`
through `57136c93` inclusive. This sentence previously called all 18 of them the hand-in work while
naming a range that holds 11, so the count and the range disagreed; both were re-counted with
Windows git on 2026-09-09 (`git rev-list --count 2c63b207..patch-18` → 18,
`git rev-list --count 1e298361~1..57136c93` → 11). Those eleven are the hand-in contract and its
mirrored fixtures, signed transport for both hand-in endpoints, the hand-in ledger across the
mutation boundary, taking the item and never losing it afterwards, the dialogue that says what
happened to the player's goods, and three rounds of audit fixes.

The other **seven** — `f9ef224a` through `a8017b81`, the oldest end of the range — are
two documentation commits, three dialogue-rendering fixes, one quest-action payload fix and a
roof-decorator fix. None of them is hand-in work,
which is why the corrected count matters: it is the eleven, not the eighteen, that a candidate jar
has to contain. The conclusion is unchanged and if anything sharper — **a `2c63b207` jar cannot
perform a hand-in at all**, because `2c63b207` predates every one of the eleven
(`git merge-base --is-ancestor 1e298361 2c63b207` fails). An operator who trusted the old text
would deploy a pre-hand-in jar; combined with the wrong deployment order this document also used
to carry, that is precisely the progression-losing window §5 exists to close.

**Build the candidate from the release branch tip, after these documents are committed**, so the
`git.head` baked into the artifact names the final commit rather than an ancestor of it.

```bash
"C:/Users/dusti/.gradle/wrapper/dists/gradle-8.9-bin/90cnw93cvbtalezasaz0blq0a/gradle-8.9/bin/gradle.bat" build artifactIdentity --no-configuration-cache --console=plain --gradle-user-home C:/Users/dusti/.gradle
```

**How a candidate is verified — by identity, not by digest.** Read `git.head` and `git.dirty`
from `britannia_mod_build.properties` inside the jar; a running server reports both through
`/grabby env`, which is how a deployed file is matched back to a commit. A candidate is valid when
`git.head` is the commit you intended to release and `git.dirty` is `false`. `./gradlew
artifactIdentity` prints that same identity for both jars, marks which one is deployable, and
depends on `verifyDeployableJar`, so the identity is only ever printed for an artifact that passed
the packaging gate.

**Build from a worktree, not the canonical checkout.** `git.dirty` is computed from
`git status --porcelain`, which counts untracked files, and the canonical checkout permanently
holds untracked owner playbooks that must be preserved. A build there stamps `git.dirty=true`, and
a dirty build is not a release candidate — a rule `verifyDeployableJar` now **enforces** rather than
merely asserting here: it fails the build on `git.dirty=true`, and the only way past it is
`-PallowDirty`, which exists for local and dev builds and makes `artifactIdentity` label the result
a local build instead of "deploy this one". `git.branch` will then name the worktree's branch rather
than `patch-18`; that is expected and harmless, because `git.head` is the authoritative field —
confirm the relationship with `git merge-base --is-ancestor <git.head> patch-18` if it matters.

**The jar is not byte-reproducible** on this project — a known property of this build.
`generateBuildInfo` is deliberately never up to date, so every invocation stamps a fresh
`build.timestamp` and therefore yields a different digest; an earlier build of identical source,
differing only in one uncommitted documentation file, produced a jar one byte larger with a
completely different SHA-256. That is why **no size or digest is pinned in this document**: any
value written here goes stale on the next rebuild, and a stale digest sitting beside the words
"the candidate" is worse than no digest, because it reads as proof. Take the SHA-256 from the same
`artifactIdentity` invocation you deploy from, and use it only to confirm the file survived the
copy to the server.

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

**Seed, run once per shard — after migration *and* after the hand-in-capable jar is deployed and
the server restarted. Not before.** See §5; this is step 4 of five, and running it early is the
one ordering mistake that costs players progression.

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

**The authoritative order is `ROWAN_FARMING_QUESTLINE_PROTOCOL.md` §1.5.7, and step 2 is the
barrier.** It is reproduced below; where this document and the protocol disagree, the protocol
wins. Rails is backward compatible with the currently deployed mod; the new mod is *not* useful
without the new Rails.

1. **Deploy the Rails code and migrations.** The release phase migrates (§4 — never run
   `db:migrate` by hand). Old mod builds keep working: the reward delivery is additive (they read
   `granted_items` as before), the new endpoints are simply unused, and the journal gains fields
   old clients ignore.
2. **Withhold the Rowan seed.** Do **not** apply it yet. Until it lands, no node carries hand-in
   metadata and every quest behaves exactly as it did before.
3. **Deploy the hand-in-capable mod jar** — `britannia_mod-0.1.8b-all.jar` (§3) — then restart
   the Minecraft server.
4. **Apply the Rowan seed**, once per shard (§4).
5. **Live acceptance** (§9).

**Why step 2 is a barrier and not a convenience.** An earlier version of this section prescribed
Rails → seed → mod jar → place Rowan. **That order is wrong and has been removed.** Seeding
before the jar is deployed opens the exact window the barrier exists to close: hand-in nodes go
live while the server still runs a mod that cannot perform a hand-in, and every player who reaches
Rowan in that gap silently loses progression.

The failure is *contained*, not harmless. An old mod meeting a hand-in-enabled node **fails
closed**: it receives `handin_required` with `completed: false` and no granted items, the node does
not move, no delivery row is created and the quest does not complete. No free reward can be
obtained, because none is ever created — asserted, not assumed, by
`rowan_farming_questline_play_test.rb`, "an old client that cannot hand in gets no reward and no
completion". But a player who walks away from a questline that silently refuses to advance is a
real cost, and keeping the order above avoids it entirely.

**Open question for the owner — where does "place Rowan" belong?** The four-step order this
section used to carry ended with "Place Rowan through the quest-giver spawn block". The canonical
five-step order in §1.5.7 has no such step, because it is a Rails/mod *release* order and placing
Rowan is an in-world operator action rather than a deploy action. It must happen after step 4 —
the seed installs the questline content the spawner's archetype resolves against — and before any
acceptance walkthrough can begin, which puts it at the head of **step 5**, where §9's
live-acceptance prerequisites already list "Two Rowans placed" and §6 gives the full placement and
infrastructure procedure. **This document therefore treats placement as the first action inside
step 5, but that is a reading, not a ruling — whether §1.5.7 should gain an explicit placement
step is the owner's call.** §1.5.7 was deliberately not edited: it is mirrored byte-for-byte into
the Rails repository and cannot be changed from one side.

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
4. **Portrait: already in place — just place Rowan as `male`.** This was previously recorded as an
   outstanding upload. It is not. `portraits/male/Rowan.png` has been in the bucket since
   2026-03-29, uploaded in the same batch as the rest of the cast, and the existing portrait system
   resolves it with no upload, no configuration and no Rails involvement: the client composes
   `https://storage.googleapis.com/ultimacraft/portraits/<gender>/<Personal_Name>.png` from the
   NPC's personal name and the gender configured on the spawner (`PortraitFetch.portraitUrl`,
   reached from `QuestGiverEntity.getGender()`).
   The one thing that matters is the gender you configure. `male` resolves;
   `portraits/female/Rowan.png` does not exist, so placing Rowan as female 404s — and the
   pre-existing client behaviour that one missing portrait pins the generic fallback for the rest
   of that session would make that configuration mistake look like a broken portrait system.

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
* The quest-giver spawn block did not persist an operator's custom Rails identity, so a Generic
  Combat spawner would lose it if it had to respawn after a chunk reload. Raised as a separate task,
  which the owner ran; it is `b9a0662f` on `patch-18` and is merged with this work. **Fixed.**

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
because no live acceptance has been performed**. These restate §5's order and are not an order of
their own — in particular, the jar precedes the seed:

1. Rails deployed and migrated (§5 step 1).
2. The `0.1.8b` `-all` jar deployed and the Minecraft server restarted (§5 step 3) — **before**
   the seed, never after it.
3. The questline seeded on the target shard (§5 step 4).
4. Two Rowans placed in different locations, with the infrastructure of §6 near each. Record both
   positions.
5. A test account that is **not** an operator, starting with an empty inventory.
6. `randomTickSpeed` and the region's climate recorded, since crop growth depends on both.
7. Rowan configured as `male`, so the existing `portraits/male/Rowan.png` resolves. Nothing to
   upload.

Record observed inventory accounting, coordinates, timings, screenshots, logs and persistence
results. **Expected source behaviour is not live evidence.**

---

## 10. The owner-only actions this project deliberately did not take

* `git push` of any branch. `patch-18` and `release/public` both carry this work locally and both
  are ahead of their remotes.
* Any deployment, including the Heroku release phase.
* Any seed against a development or production database. Every seed and migration in this project
  ran only against disposable databases, which were dropped.

Every one is prepared and documented above so that each is a single reviewable step.

The local integration itself — both merges, the target-branch updates and the candidate build — was
carried out by a later milestone under explicit authorization, and is recorded in §1. None of it
reached a remote, a server or a live database.
