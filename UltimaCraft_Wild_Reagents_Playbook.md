# UltimaCraft Wild Reagent & Swamp Environment System
## Milestone-Based Codex Playbook

**Companion document:** `UltimaCraft_Wild_Reagents_Design.md`

## Operating Rules

1. Read the design document in full before implementation.
2. Treat the completed managed vegetation system as an existing dependency to inspect and reuse.
3. Do not duplicate managed vegetation persistence, weighting, scheduling, or cut logic where reuse is appropriate.
4. Do not force-load chunks.
5. Do not scan every block in every loaded chunk every tick.
6. Keep spawning, harvesting, loot, and accounting server-authoritative.
7. Keep swamp rendering client-only and dedicated-server safe.
8. Search for existing items, blocks, tags, and weapon classifications before adding new ones.
9. Discover the actual limestone registry ID rather than assuming it.
10. Discover the actual dagger identity mechanism rather than inventing one immediately.
11. Generate original placeholder assets locally; do not import web artwork.
12. Keep tuning values centralized/configurable.
13. Run focused tests after each implementation milestone.
14. Run the relevant/full regression suite before completion.
15. Do not push, merge, or rebase shared branches unless explicitly instructed.
16. Record discoveries, decisions, files changed, tests, and limitations in the final report.

---

# Milestone 0 - Baseline and Post-Vegetation Discovery

## Objective

Understand the repository after the managed vegetation feature has landed.

## Tasks

Inspect:

- git status,
- integration/base branch,
- baseline SHA,
- managed vegetation manager/node classes,
- managed vegetation profiles,
- managed vegetation persistence,
- managed vegetation scheduler,
- managed vegetation cut/harvest event,
- `SwordType`,
- biome helpers,
- existing reagent items,
- existing Sulfurous Ash/Blood Moss/Black Pearl/oyster content,
- limestone block,
- vanilla calcite usage,
- dagger classes/tags/types,
- Adventure-mode break hooks,
- loot conventions,
- chunk load/unload hooks,
- server scheduler,
- persistent world/chunk state,
- client biome/fog rendering hooks,
- asset conventions,
- tests.

Verify the real base branch first.

## Required Discovery Report

```text
Repository root:
Baseline SHA:
Base branch:
Feature branch/worktree:
Managed vegetation manager:
Managed vegetation profile implementation:
Managed vegetation persistence:
Managed vegetation scheduler:
Managed vegetation harvest event:
SwordType:
Dagger identity:
Adventure break hooks:
Limestone registry ID:
Existing Sulfurous Ash:
Existing Blood Moss:
Existing Black Pearl:
Existing oyster content:
Chunk lifecycle hooks:
Persistence mechanism:
Server scheduler:
Biome helpers:
Client fog/render hooks:
Relevant tests:
Asset conventions:
Potential conflicts:
Recommended architecture:
```

## Gate

Do not implement until the relationship between managed vegetation and the new wild-resource subsystem is explicit.

## Suggested Commit

No feature commit required unless discovery documentation is stored in-repo.

---

# Milestone 1 - Wild Resource Entry Framework

## Objective

Create the generic configuration/strategy model for environmental resources.

## Tasks

Define or reuse an abstraction supporting:

```text
resource id
spawn frequency / weight
max per chunk
attempt cooldown
max probes per attempt
candidate generator
environment validator
substrate restrictions
biome restrictions
nearby block/fluid rule
harvest strategy
loot/result
```

Reuse existing project codec/config/registry conventions.

Do not put sulfur and oyster logic into one giant event handler.

## Required Tests

- entries register/load,
- unknown IDs fail safely,
- centralized values can be changed,
- per-entry strategy dispatch works.

## Gate

Adding a future reagent must not require rewriting scheduler internals.

## Suggested Commit

`feat: add wild resource entry framework`

---

# Milestone 2 - Bounded Loaded-Chunk Spawn Scheduler

## Objective

Create efficient random spawn attempts for already-loaded chunks.

## Tasks

- hook into existing chunk/server lifecycle,
- track eligible loaded chunks,
- schedule occasional resource attempts,
- bound random probes per attempt,
- centralize attempt cadence,
- enforce max-per-chunk/resource caps,
- add cooldown,
- avoid full-chunk scans,
- persist enough accounting to prevent reload abuse,
- remove/deactivate chunks cleanly on unload.

## Required Tests

- loaded chunk eligible,
- unloaded chunk not processed,
- scheduler does not force-load,
- probe count bounded,
- cooldown respected,
- per-chunk cap respected,
- restart reloads scheduling/accounting.

## Gate

A test resource can spawn through the scheduler without a full chunk scan.

## Suggested Commit

`feat: add loaded chunk wild resource scheduler`

---

# Milestone 3 - Shared 20-Block Environmental Validators

## Objective

Add reusable proximity validation for lava and water.

## Tasks

Implement safe helpers for:

- lava proximity,
- water proximity,
- distance/radius calculation,
- loaded-position checks,
- early exit once a source is found.

Do not blindly run a full 41x41x41 scan at high frequency.

Prefer project-standard optimized world queries where available.

Document the chosen distance metric.

## Required Tests

- source at distance <=20 accepted,
- source at >20 rejected,
- boundary exactly 20 handled correctly,
- unloaded neighboring area does not force-load,
- water fluid states recognized,
- lava fluid/block states recognized.

## Gate

Both resource types can reuse the same environmental-query framework.

## Suggested Commit

`feat: add wild resource proximity validators`

---

# Milestone 4 - Sulfurous Ash Resource

## Objective

Implement Sulfurous Ash spawning near lava.

## Tasks

- search again for existing ash content,
- register missing block/item only if needed,
- create an original placeholder texture/model,
- create the Sulfurous Ash resource entry,
- require <=20-block lava proximity,
- require safe replaceable placement,
- require suitable support beneath,
- enforce cap/cooldown,
- implement project-standard harvesting,
- do not invent a special tool restriction.

## Placeholder Direction

- pale yellow/yellow-gray,
- ash or crystal pile,
- low-profile,
- visually distinct from sand/gravel.

## Required Tests

- spawn near lava,
- reject beyond radius,
- reject unsafe position,
- no force load,
- cap respected,
- documented drop behavior works.

## Gate

Ash uses the generic scheduler, not its own tick loop.

## Suggested Commit

`feat: add sulfurous ash wild resource`

---

# Milestone 5 - Black-lipped Oyster Placement

## Objective

Implement oyster spawning on limestone/calcite near water.

## Tasks

- discover actual limestone block,
- include vanilla calcite,
- centralize allowed oyster substrates,
- register oyster block,
- create original placeholder model/texture,
- create wild-resource entry,
- require safe target space,
- require allowed substrate,
- require <=20-block water proximity,
- apply cap/cooldown.

## Placeholder Direction

- dark black-purple shell,
- lighter lip/interior,
- low-profile cluster,
- readable against limestone/calcite.

## Required Tests

- limestone valid,
- calcite valid,
- other substrate invalid,
- water <=20 valid,
- water >20 invalid,
- unloaded area does not force-load,
- cap/cooldown respected.

## Gate

Oyster placement is correct before special harvesting is added.

## Suggested Commit

`feat: add black-lipped oyster spawning`

---

# Milestone 6 - Dagger Harvest and Black Pearl

## Objective

Implement Adventure-mode oyster harvesting.

## Tasks

- inspect existing dagger identity,
- reuse it where possible,
- enforce server-side Adventure dagger requirement,
- reject non-dagger attempts without destroying oyster,
- search for existing Black Pearl item,
- add it if absent,
- generate original placeholder item texture/model if needed,
- successful authorized harvest gives exactly one Black Pearl,
- update resource accounting/cooldown exactly once,
- prevent duplicate loot under simultaneous harvest attempts.

## Required Mode Report

Document final behavior for:

```text
Adventure:
Survival:
Creative:
```

Adventure dagger-only behavior is mandatory.

## Required Tests

- Adventure hand rejected,
- Adventure sword-but-not-dagger rejected,
- Adventure valid dagger succeeds,
- exactly one Black Pearl awarded,
- concurrent harvest yields one logical result,
- Survival behavior matches documented decision,
- Creative behavior matches documented convention.

## Gate

No client action can bypass server-side authorization.

## Suggested Commit

`feat: add dagger oyster harvesting and black pearl`

---

# Milestone 7 - Blood Moss Managed Vegetation Integration

## Objective

Add Blood Moss through the existing managed vegetation framework.

## Default Swamp Profile

```text
Grass family: 75%
Fern: 20%
Blood Moss: 5%
Normal random flower: 0%
```

## Tasks

- define/reuse centralized swamp-biome eligibility,
- generate original Blood Moss placeholder asset,
- add Blood Moss vegetation entry,
- add item only if existing loot architecture requires it,
- implement biome-aware vegetation profile selection,
- replace the normal 5% flower slot with Blood Moss in swamp,
- preserve the non-swamp profile,
- reuse node persistence,
- reuse regrowth scheduling,
- reuse `SwordType` cutting by default.

## Required Tests

- Blood Moss unavailable outside swamp profile,
- Blood Moss configured at exactly 5%,
- normal flower slot absent from default swamp profile,
- non-swamp vegetation profile unchanged,
- Blood Moss regrows through managed vegetation,
- SwordType cutting works,
- persistence works.

## Gate

There is no second vegetation manager or duplicate node system.

## Suggested Commit

`feat: add swamp blood moss vegetation`

---

# Milestone 8 - Swamp Gas Environmental Hue

## Objective

Create a subtle green atmospheric effect in swamp biomes.

## Tasks

- inspect NeoForge/project client rendering hooks,
- prefer biome fog/color adjustment,
- use the same centralized swamp-biome predicate as Blood Moss,
- add configurable intensity,
- keep rendering client-only,
- avoid harsh full-screen green overlay,
- avoid texture mutation,
- add smooth biome enter/exit interpolation if practical,
- handle joining inside swamp,
- handle dimension transitions,
- keep dedicated server classloading safe.

## Required Validation

Test:

- inside swamp,
- outside swamp,
- biome boundary,
- joining inside swamp,
- leaving swamp,
- dimension change,
- rain,
- night,
- underwater if relevant,
- dedicated server startup.

## Gate

Effect is subtle, readable, swamp-only, and dedicated-server safe.

## Suggested Commit

`feat: add swamp gas biome atmosphere`

---

# Milestone 9 - Persistence and Resource Reconciliation

## Objective

Make wild resources stable over long-running servers.

## Tasks

Handle:

- server restart,
- chunk unload/reload,
- removed lava/water source,
- removed oyster substrate,
- externally deleted resource block,
- player-built obstruction,
- invalid persisted entry,
- cooldown recovery.

Never overwrite unrelated player blocks during reconciliation.

Never force-load chunks.

## Required Tests

- restart preserves accounting,
- chunk reload does not duplicate,
- removed source does not trigger unsafe replacement,
- externally removed node eventually reconciles,
- invalid resource ID fails safely,
- cooldown survives restart as designed.

## Gate

Repeated loading/unloading cannot create an instant resource farm.

## Suggested Commit

`feat: persist wild resource spawn state`

---

# Milestone 10 - Harvest Event and Future Profession Hook

## Objective

Expose successful gathering for future professions without adding economy logic now.

## Tasks

Add or reuse a project-standard event equivalent to:

```text
WildResourceHarvestEvent
```

Include as available:

```text
player
resource id
position
dimension
result item
tool category
```

For Blood Moss, reuse the managed vegetation cut event if that remains the cleanest architecture.

Do not:

- pay currency,
- award profession XP,
- call Rails,
- modify NPCs,
- create quests.

## Required Tests

- successful resource harvest emits once,
- rejected oyster harvest emits zero,
- concurrent harvest emits one logical event.

## Suggested Commit

`feat: expose wild resource harvest events`

---

# Milestone 11 - Placeholder Asset Audit

## Objective

Ensure every new unfinished visual has a valid in-game placeholder.

## Audit/Create as Needed

- Sulfurous Ash block texture/model,
- Sulfurous Ash item icon if separate,
- Black-lipped Oyster block texture/model,
- Black Pearl item icon,
- Blood Moss block texture/model,
- Blood Moss item icon if required,
- localization,
- blockstates,
- loot tables,
- item models.

## Rules

- original placeholders only,
- follow project texture resolution/style,
- stable resource paths,
- document all placeholder files,
- no missing-model purple/black rendering,
- no external artwork.

## Suggested Commit

`chore: finalize wild reagent placeholder assets`

---

# Milestone 12 - Regression, Performance, and Dedicated Server Pass

## Objective

Prove the feature is safe for multiplayer/server operation.

## Automated Areas

Run the relevant/full suite covering:

- managed vegetation,
- farming,
- weapons,
- dagger logic,
- block interaction,
- chunk lifecycle,
- persistence,
- loot,
- biome logic,
- resource loading,
- dedicated server.

## Manual Matrix

```text
1. Load a chunk near lava and force/observe ash attempts.
2. Confirm ash cannot spawn >20 blocks from lava.
3. Load calcite near water and force/observe oyster attempts.
4. Load limestone near water and force/observe oyster attempts.
5. Confirm oyster cannot spawn on other substrate.
6. Confirm oyster cannot spawn >20 blocks from water.
7. Adventure: try hand on oyster.
8. Adventure: try non-dagger sword on oyster.
9. Adventure: harvest with dagger.
10. Confirm exactly one Black Pearl.
11. Test two-player simultaneous oyster harvest.
12. Enter swamp.
13. Confirm subtle green environmental hue.
14. Leave swamp.
15. Confirm hue clears/fades.
16. Force swamp vegetation selections.
17. Confirm Blood Moss occupies the 5% special slot.
18. Confirm default normal flower slot is not also active in swamp.
19. Cut Blood Moss.
20. Confirm managed regrowth.
21. Restart server with active resources.
22. Unload/reload chunks.
23. Confirm no duplicate resource burst.
24. Remove lava/water source and verify safe behavior.
25. Start dedicated server and verify no client-class errors.
```

## Performance Report

Document:

- scheduler cadence,
- max chunk attempts per cycle,
- max probes per attempt,
- radius-query strategy,
- persistence size,
- loaded-chunk handling,
- expected cost with many players/chunks,
- evidence that no chunk is force-loaded.

## Gate

No full-world/per-tick scans, no forced chunk loads, no regressions.

---

# Milestone 13 - Documentation and Final Handoff

## Required Final Report

```text
Baseline SHA:
Final SHA:
Branch/worktree:
Files added:
Files modified:

Managed vegetation systems reused:
Wild resource architecture:
Spawn scheduler:
Persistence:
Lava proximity rule:
Water proximity rule:
Oyster substrate IDs:
Dagger identity used:
Adventure harvest behavior:
Survival behavior:
Creative behavior:
Black Pearl loot:
Swamp biome definition:
Blood Moss swamp profile:
Swamp hue implementation:

Balance/config locations:
Placeholder asset files:
Tests run:
Build results:
Dedicated server result:
Known limitations:
Deferred future reagents:
```

## Final Acceptance Checklist

- [ ] Sulfurous Ash appears only within 20 blocks of lava.
- [ ] Sulfurous Ash uses the generic wild-resource scheduler.
- [ ] Oyster appears only on limestone/calcite.
- [ ] Oyster appears only within 20 blocks of water.
- [ ] Adventure oyster harvest requires a recognized dagger.
- [ ] Authorized oyster harvest gives exactly one Black Pearl.
- [ ] Blood Moss is swamp-only.
- [ ] Default swamp vegetation profile gives Blood Moss 5%.
- [ ] Default swamp profile does not also retain the normal 5% flower slot.
- [ ] Existing managed vegetation system is reused.
- [ ] Swamp receives a subtle green atmospheric hue.
- [ ] Placeholder art exists for all missing visuals.
- [ ] Spawn rates/caps/cooldowns are centralized.
- [ ] No full loaded-chunk scan occurs every tick.
- [ ] No chunk is force-loaded.
- [ ] Persistence prevents trivial restart/reload duplication.
- [ ] Server authority is preserved.
- [ ] Dedicated server is client-class safe.
- [ ] Focused tests pass.
- [ ] Relevant/full regression suite passes.
- [ ] Final report documents all important choices.

---

# Stop Conditions

Stop and document a conflict before creating a competing architecture if:

- the managed vegetation feature is not yet stable/available,
- another branch already adds a wild-resource scheduler,
- the existing dagger system has incompatible semantics that cannot be resolved safely,
- the actual limestone identity cannot be determined,
- client biome rendering is undergoing a conflicting refactor,
- the selected base branch risks overwriting another agent's active work.

For ordinary implementation uncertainty, inspect existing code, choose the smallest compatible solution, document it, and continue.
