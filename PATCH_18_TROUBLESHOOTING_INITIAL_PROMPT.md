# Patch 18 Troubleshooting — Initial Prompt

Paste the block below into a new conversation. Fill in the symptom list first — everything else
is already verified and current as of 2026-08-09.

---

## Prompt

```text
We are troubleshooting the Britannia Mod after a large multi-feature merge into patch-18.
This is diagnosis and repair work, not feature work.

WORKING COPY
  Worktree: C:\projects\britannia\mod\Britannia_Mod-troubleshooting
  Branch:   patch-18-troubleshooting  (branched from patch-18 @ 50061f07)
  Make every fix here. Do not work in the primary copy at
  C:\projects\britannia\mod\Britannia_Mod, which sits on patch-18 itself.
  Confirm with `git branch --show-current` before changing anything.

WHAT PATCH-18 IS
  patch-18 is the integration of six previously independent feature lines, merged
  2026-08-08 in this order: banking (M3), Farming (M5), blacksmithing (M6),
  shrines-monoliths (M7), banners-dyetub (M8), then villa as a post-project addendum.
  The full record is PATCH_18_INTEGRATION_LOG.md in the primary working copy (untracked
  there, by owner policy). Read it before forming any theory about a defect's origin —
  it lists the merge order, the compatibility audits, and every decision taken.

  The merge itself was validated: build SUCCESS, 1,679 unit tests with 0 failures and
  17 skips, all 333 gametests passing. So a failure you find now is most likely either
  (a) world-state dependent, (b) runtime-only, or (c) an interaction the automated
  suites do not cover. Two such defects have already been found and fixed post-merge:

    6295d544  creative-tab crash — duplicate britannia_mod:pike item id
    50061f07  server tick crash — CityDataSync built a payload from
              ServerAuthRegistry.credentials(...).orElseThrow() and crashed the ticking
              server on any instance with no Rails backend, triggered by a tracked
              trader being missing or dead at tick time

  Both were found by running against a copy of the owner's real world, not by tests.
  That is the most productive technique available here; prefer it.

KNOWN PRE-EXISTING CONDITIONS — DO NOT CHASE THESE
  Recorded during baseline validation before any merge. They are not integration defects
  and not yours to fix unless the owner asks:

    PRE-001  `:neoFormPatch` fails on the first build after `gradlew clean`
             (NeoGradle flake). An immediate retry succeeds.
    PRE-002  `gradlew runServer` and `runClient` fail under the configuration cache
             with "Could not load the value of field `provider` … null array".
             Always pass --no-configuration-cache to run tasks.
    PRE-003  RuntimeDistCleaner logs an ERROR for TitleScreenBackgroundMixin, which
             targets the client-only TitleScreen and is probed during DEDICATED_SERVER
             load. The mixin is skipped and the server continues. Cosmetic today, but a
             genuine client-class-on-server hazard, and three merged branches modify
             britannia_mod.mixins.json — do not make it worse.
    PRE-004  On a fresh runtime: missing config\britannia_mod.properties (defaults used,
             stack trace logged), and ChestHandler warns about no barrel at
             5213/66/8912. Expected.

OPEN RELEASE-GATE ITEM
  NET-008: the network envelope version. The compiled default is 1, which is the safe
  production value. A v3 override exists only in build.gradle's dev run configurations
  (`runs { configureEach { systemProperty ... } }`) and reaches no packaged resource, so
  a built jar ships v1. Do not bump it. It may only be raised once the production Rails
  instance is confirmed to carry the Milestone 16 contract.

DOCUMENTATION
  By standing owner directive, project .md files and logs/ are never merged into
  patch-18 — they live on feature branches. For this work the relevant documents have
  been copied into this worktree and are STAGED BUT UNCOMMITTED (40 files):
    - the complete Bank Interface Rebuild set (design, playbook, addenda, and the
      Milestone 0-19 reports)
    - docs/server_authentication.md, docs/known_environment_baseline.md, and the
      service_npc_spawn_* operational docs
  Commit them on this branch if useful. THEY MUST NOT REACH patch-18.

  docs/known_environment_baseline.md is worth reading early: it catalogues environment
  signatures that are already root-caused, so you recognise them instead of
  re-investigating. Correct it if you find it no longer matches reality.

REPOSITORY STATE
  Nothing is pushed. patch-18 is 181 commits ahead of origin/patch-18, and neither
  patch-18 nor this branch has ever been published. Do not push, merge, tag, or deploy
  without explicit authorization. Commit only when the owner asks.

  A Rails backend (ultimacraft-website, branch banking, in WSL at
  /home/dusti/ultimacraft-website) is the mod's server-side counterpart. Run it as the
  `ultimacraft` Linux user with `rails s -b 0.0.0.0`; the `dusti` account cannot reach
  the development database. Much of the mod tolerates the Rails backend being absent —
  when it does not, that is usually itself the bug, as 50061f07 was.

HOW TO WORK
  Diagnose before changing anything. For each symptom, establish the actual failing
  line from a crash report or log, not from the Gradle wrapper's stack trace, which is
  almost always noise. State what you verified and how. If a symptom turns out to be one
  of PRE-001..004, say so and stop rather than fixing it.

  Report findings plainly as you go. Do not write a formal investigation document unless
  asked. Fix one thing at a time and check in.

SYMPTOMS TO INVESTIGATE
  <<< describe what is actually going wrong: what you did, what you expected, what
      happened, and paste any crash report path or log excerpt >>>
```

---

## Notes for the owner

**Fill in the symptom list before sending.** Everything above is context; the symptoms are the
actual work. Without them the new session will start by asking, which wastes a turn.

**Where crash reports land.** `run\crash-reports\` for the client (integrated server crashes are
written there too, named `-server.txt`), `run\server\crash-reports\` for the dedicated server.
The newest file is the one that matters. Gradle's own `TaskExecutionException` stack trace never
contains the cause — go to the crash report or `run\logs\latest.log` instead.

**The most productive technique so far** has been running against a copy of the real world rather
than a freshly generated one. Both post-merge defects were world-state dependent and neither was
caught by 1,679 unit tests or 333 gametests.
