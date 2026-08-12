# UltimaCraft Managed Vegetation Regrowth System
## Milestone-Based Codex Playbook

**Purpose:** Implementation playbook for the Zelda-style managed grass/shrubbery/flower cutting and regrowth system.

**Companion document:** `UltimaCraft_Managed_Vegetation_Design.md`

---

## Operating Rules

Codex must follow these rules throughout the project:

1. Read the design document completely before changing code.
2. Inspect the existing repository and reuse established systems.
3. Do not introduce a second flower-growth system.
4. Do not introduce a second sword classification if `SwordType` already exists.
5. Do not globally alter vanilla grass/fern behavior.
6. Keep all authoritative mutation on the server.
7. Do not force-load chunks for vegetation.
8. Do not add world-wide per-tick scans.
9. Preserve unrelated player-placed vegetation.
10. Keep spawn entries and weights centralized/extensible.
11. Run focused tests after each implementation milestone.
12. Run the broader relevant/full suite before completion.
13. Commit at milestone boundaries with clear messages if this project workflow allows commits.
14. Do not push, merge, rebase shared branches, or modify unrelated work unless explicitly instructed.
15. Keep an implementation journal in the final report: discoveries, choices, files changed, tests, limitations.

---

# Milestone 0 - Worktree, Baseline, and Repository Discovery

## Objective

Establish an isolated working environment and document the systems this feature must integrate with.

## Tasks

- Inspect git status, branches, worktrees, and repository root.
- Determine the intended integration/base branch from current project state.
- If the current UltimaCraft workflow still uses `patch-18` as the integration base, verify that fact instead of assuming it.
- Create a dedicated feature branch and worktree when safe.
- Suggested branch/worktree name:
  - branch: `managed-vegetation`
  - worktree/directory: `managed-vegetation`
- Do not disturb the primary working directory or another agent's uncommitted work.
- Record baseline commit SHA.
- Run existing build/tests before changes.
- Discover:
  - NeoForge/Minecraft version,
  - Java version,
  - block registry conventions,
  - item registry conventions,
  - `SwordType`,
  - sword break/harvest hooks,
  - flower classes and seven-stage growth code,
  - flower species source of truth,
  - flower models/textures/blockstates,
  - server tick/scheduled tick infrastructure,
  - persistent world-state infrastructure,
  - custom SavedData if any,
  - admin/debug placement tooling,
  - event system,
  - farming systems that overlap vegetation,
  - Creative Tab registration,
  - test structure,
  - multiplayer/network authority conventions.

## Deliverable

Create a discovery note in the working report containing:

```text
Baseline SHA:
Base branch:
Feature branch:
Worktree:
Existing SwordType location:
Existing flower system location:
Flower species authority:
Existing persistence mechanism:
Existing scheduler/tick mechanism:
Existing cut/harvest events:
Existing test patterns:
Assets to reuse:
Potential conflicts:
Proposed integration classes:
```

## Gate

Do not proceed until the architecture is based on actual repository findings.

---

# Milestone 1 - Architecture and Persistent Node Model

## Objective

Implement the smallest server-side representation of a persistent managed vegetation node.

## Tasks

- Define the node identity by canonical plant position.
- Reuse existing persistent world state if available.
- Otherwise implement the project-appropriate per-dimension persistent data store.
- Add schema/version handling if introducing custom persisted data.
- Define lifecycle states.
- Define vegetation entry ID/state storage.
- Define next transition time.
- Add safe create/register/remove methods.
- Add validation helpers:
  - supporting substrate,
  - three-block vertical clearance,
  - replaceability,
  - node ownership.
- Ensure the persistence layer does not force-load chunks.
- Ensure a node can exist while its visible plant is temporarily absent.

## Required Tests

- register node,
- duplicate registration,
- remove node,
- save/load,
- invalid persisted data fails safely,
- substrate validation,
- three-block clearance validation.

## Gate

A node must persist and reload before any random plant spawning is added.

## Suggested Commit

`feat: add persistent managed vegetation nodes`

---

# Milestone 2 - Regrowth Controller Block and Placement Lifecycle

## Objective

Create the custom regrowth/controller block or equivalent project-standard mechanism.

## Tasks

- Register the new block.
- Make it non-obstructive for gameplay:
  - no collision where appropriate,
  - no solid full-cube behavior,
  - no accidental visual artifact in normal use.
- Determine whether it needs an item.
- Add localization/model/blockstate/loot assets only as necessary.
- Integrate placement with node registration.
- Reject or suspend placement where:
  - substrate is not valid grass,
  - three-block vertical space is not available.
- Add an admin/development method to place and remove nodes using existing tooling if possible.
- Avoid creating duplicate admin infrastructure.

## Required Tests

- valid placement above grass,
- invalid placement above non-grass,
- blocked vertical space,
- controller does not overwrite protected blocks,
- removal unregisters cleanly when intended.

## Gate

A node can be placed, validated, persisted, and removed without spawning vegetation yet.

## Suggested Commit

`feat: add vegetation regrowth controller block`

---

# Milestone 3 - Data-Driven Vegetation Entry Table

## Objective

Create an extensible selection system.

## Default Profile

```text
Grass family: 75%
Fern: 20%
Random flower: 5%
```

The 5% flower value is required.

The 75/20 grass/fern split is an initial configurable balance value.

## Tasks

- Reuse existing project config/data/codec conventions.
- Define vegetation entries without scattering hard-coded branch logic.
- Implement deterministic weighted selection with injectable/controlled RNG for tests.
- Define growth strategies:
  - grass family,
  - static fern,
  - existing flower system.
- Resolve flower species from the project's authoritative registration/data source.
- Do not duplicate the flower list.

## Required Tests

- weight table loads/constructs correctly,
- total weights handled correctly,
- exact flower entry configured at 5%,
- controlled RNG maps into expected entries,
- invalid/unknown entry IDs fail safely,
- flower selection uses authoritative flower species.

## Gate

New entries must be addable through the selected centralized mechanism without changing the random selection algorithm.

## Suggested Commit

`feat: add configurable managed vegetation entries`

---

# Milestone 4 - Vanilla Short Grass Spawn and Growth to Tall Grass

## Objective

Implement the first complete vegetation lifecycle.

## Required Lifecycle

```text
REGROWING
-> vanilla short grass
-> vanilla tall/long grass
-> SwordType cut
-> REGROWING
```

## Tasks

- Spawn vanilla short grass at the node.
- Record node ownership/state.
- Schedule short-to-tall transition.
- Before growth, revalidate:
  - substrate,
  - vertical clearance,
  - expected current block.
- Place vanilla tall grass correctly as a double plant.
- Handle both halves atomically/safely.
- If growth is blocked, suspend or retry using a bounded strategy.
- Do not overwrite unrelated blocks.

## Required Tests

- node spawns short grass,
- short grass uses vanilla block,
- short grass grows to vanilla tall grass,
- obstruction prevents unsafe tall growth,
- top/bottom tall-grass halves are correct,
- node state remains consistent.

## Gate

A server restart/chunk unload must not permanently break the lifecycle.

## Suggested Commit

`feat: add managed grass growth lifecycle`

---

# Milestone 5 - SwordType-Only Cutting

## Objective

Make managed vegetation destructible only by the existing `SwordType` abstraction.

## Tasks

- Locate the correct server-side break/interact event hook.
- Resolve any clicked half of a multi-block plant back to its node.
- If target is managed:
  - reject non-`SwordType` attempts,
  - accept existing `SwordType`,
  - remove the complete managed plant,
  - transition node once to regrowing,
  - schedule next spawn.
- If target is unmanaged:
  - preserve existing Minecraft/UltimaCraft behavior.
- Preserve existing sword durability/animation/sound behavior where appropriate.
- Do not add a duplicate sword tag/type.
- Do not add new loot behavior.

## Required Tests

- hand cannot break managed short grass,
- axe/pickaxe/hoe cannot break managed short grass,
- valid `SwordType` cuts short grass,
- same cases for tall grass,
- unmanaged vanilla vegetation unaffected,
- cutting top half of tall grass removes whole plant,
- simultaneous/double cut does not schedule twice.

## Gate

The server must be authoritative and unmanaged vanilla vegetation must remain unchanged.

## Suggested Commit

`feat: restrict managed vegetation cutting to swords`

---

# Milestone 6 - Fern Support

## Objective

Add vanilla fern as a managed vegetation outcome.

## Tasks

- Spawn vanilla fern from the fern entry.
- Apply node ownership.
- Apply the same `SwordType` restriction.
- Return to regrowth after cutting.
- Preserve vanilla fern behavior when unmanaged.
- Keep fern implementation inside the same generic entry/lifecycle framework.

## Required Tests

- fern can be selected,
- fern spawns only on valid node,
- non-sword cannot cut managed fern,
- `SwordType` can cut managed fern,
- managed fern regrows,
- unmanaged fern unaffected.

## Gate

Adding fern should not require a fern-specific persistence subsystem.

## Suggested Commit

`feat: add managed fern vegetation`

---

# Milestone 7 - Existing Seven-Stage Flower Integration

## Objective

Integrate managed wild flowers with the existing UltimaCraft flower system.

## Tasks

- Re-read the authoritative flower implementation discovered in M0.
- Obtain flower species from the established registry/list/data.
- When the flower selection entry wins:
  - select species,
  - spawn the existing stage-1 implementation,
  - initialize any required existing flower state.
- Allow the normal flower system to advance stages 1 through 7.
- Ensure the managed node can recognize all seven stages.
- Apply managed `SwordType` cutting at every stage.
- After cutting, return the node to regrowth.
- Preserve existing player/admin flower protection semantics.
- Avoid duplicating:
  - stage logic,
  - growth timer logic,
  - species tables,
  - model mappings,
  - textures.

## Required Tests

- managed flower starts at stage 1,
- each existing stage remains compatible,
- growth reaches stage 7 using existing logic,
- sword can cut managed flower at stages 1-7,
- non-sword cannot cut managed flower,
- unmanaged/player flowers retain existing behavior,
- flower species selection comes from authoritative source.

## Gate

All existing flower tests must still pass unchanged unless an intentional compatibility update is documented.

## Suggested Commit

`feat: integrate managed vegetation with flower growth`

---

# Milestone 8 - Regrowth Timing, Randomization, and Persistence Recovery

## Objective

Make repeated cutting/regrowth stable over long-running servers.

## Tasks

- Centralize:
  - cut-to-regrow minimum/maximum delay,
  - short-to-tall minimum/maximum delay,
  - retry delay.
- Use existing project scheduling infrastructure where possible.
- Avoid full-node scans every tick.
- Handle:
  - chunk unload,
  - chunk reload,
  - restart,
  - overdue transitions.
- Do not force-load chunks.
- Revalidate before mutation.
- Do not overwrite unrelated blocks on recovery.
- Mark persistent data dirty only when state changes.

## Required Tests

- random delay is within configured bounds,
- saved next-transition time reloads,
- overdue node transitions after safe chunk availability,
- unloaded chunks are not force-loaded,
- invalid environment suspends/retries safely,
- repeated cut/regrow cycles remain consistent.

## Gate

Run a high-cycle simulation/test if the test framework allows it.

## Suggested Commit

`feat: persist managed vegetation regrowth timing`

---

# Milestone 9 - Future Grass-Cutting Service Hook

## Objective

Provide a clean integration surface for future jobs/services without implementing the economy feature.

## Tasks

- Add or reuse a project-standard event/callback when a successful managed cut occurs.
- Event data should include, as available:
  - server player,
  - dimension,
  - node position,
  - vegetation entry ID,
  - flower species/stage if applicable.
- Ensure one logical cut emits one logical event.
- Do not:
  - pay currency,
  - call Rails,
  - create quests,
  - modify NPCs,
  - add job progression.

## Required Tests

- successful sword cut emits once,
- rejected cut emits zero,
- unmanaged vegetation emits zero,
- multi-block grass emits once.

## Gate

The vegetation system must remain independent from business/economy code.

## Suggested Commit

`feat: expose managed vegetation cut event`

---

# Milestone 10 - Assets, Creative/Admin UX, and Cleanup

## Objective

Finish project integration and remove temporary scaffolding.

## Tasks

- Audit all new assets.
- Confirm vanilla grass/fern assets are reused.
- Confirm flower assets are reused.
- Add controller block localization.
- Add item model only if required.
- Add to Creative Tab only if consistent with admin/developer workflow.
- Remove temporary debug texture/model unless intentionally retained.
- Add comments only where lifecycle/persistence rules are not obvious.
- Remove dead code and duplicated helpers.
- Check warnings, resource locations, namespace consistency.

## Required Validation

- client resources load with no missing model/texture warnings,
- dedicated server starts without client-only class loading errors,
- no duplicate registry entries,
- no unused generated assets.

## Suggested Commit

`chore: finalize managed vegetation assets and tooling`

---

# Milestone 11 - Full Compatibility and Regression Pass

## Objective

Prove the feature does not regress existing systems.

## Required Test Areas

Run the repository's applicable full suite plus focused checks for:

- flower system,
- farming system,
- sword/tool handling,
- block interactions,
- persistence,
- multiplayer/server authority,
- world loading,
- resource/model loading,
- dedicated server startup.

## Manual Test Matrix

Test at least:

```text
1. Place/register node above grass with 3 clear blocks.
2. Observe short grass spawn.
3. Observe short -> tall grass growth.
4. Attempt hand break: rejected.
5. Attempt non-sword tool: rejected.
6. Cut with valid SwordType: succeeds.
7. Wait/force regrowth.
8. Observe fern outcome.
9. Cut fern.
10. Force/select flower outcome.
11. Verify flower begins at stage 1.
12. Verify flower reaches stages 2-7.
13. Cut flower at an early stage.
14. Cut flower at stage 7.
15. Restart server while node is regrowing.
16. Restart server while short grass is waiting to grow.
17. Unload/reload chunk during pending transition.
18. Remove grass substrate and verify no unsafe spawn.
19. Obstruct vertical space and verify safe retry/suspension.
20. Restore valid environment and verify recovery.
21. Confirm nearby unmanaged vanilla grass can still be broken normally.
22. Test two players attempting the same cut.
```

## Performance Review

Document:

- number of persistent records per node,
- scheduling strategy,
- whether any per-tick iteration exists,
- chunk-load behavior,
- expected cost for thousands of nodes,
- opportunities for future optimization.

## Gate

No known regressions, no forced chunk loading, no global vanilla behavior change.

---

# Milestone 12 - Documentation and Final Handoff

## Objective

Produce enough evidence that another Codex session or developer can safely continue the system.

## Required Final Report

Include:

```text
Baseline SHA
Final SHA
Branch/worktree
Files added
Files modified
Existing systems reused
Existing assets reused
Persistence design
Scheduler design
Spawn weighting implementation
Flower integration design
SwordType integration design
Future extension path
Automated tests run
Manual tests run
Build results
Known limitations
Deferred ideas
```

## Documentation Updates

Update project docs where appropriate with:

- what a managed vegetation node is,
- how to add a new vegetation entry,
- how to change weights,
- how to change regrowth timing,
- how flowers are sourced,
- how future job systems can subscribe to cut events,
- how admins place/remove/test nodes.

## Final Acceptance Checklist

- [ ] Managed nodes only function above valid grass substrate.
- [ ] Three-block vertical clearance is enforced.
- [ ] Vanilla short grass is reused.
- [ ] Short grass grows into vanilla tall/long grass.
- [ ] Vanilla fern is reused.
- [ ] Flower selection is 5% by default.
- [ ] Existing flower species are reused.
- [ ] Existing seven-stage flower growth is reused.
- [ ] Managed vegetation is sword-only through existing `SwordType`.
- [ ] Unmanaged vegetation behavior is unchanged.
- [ ] Regrowth repeats after cutting.
- [ ] State survives restart/chunk unload.
- [ ] No forced chunk loading.
- [ ] No global per-tick node scan.
- [ ] Spawn entries are extensible/configurable.
- [ ] Future grass-cutting job hook exists without economy coupling.
- [ ] Focused tests pass.
- [ ] Broader/full relevant tests pass.
- [ ] Dedicated server validation passes.
- [ ] Asset audit passes.
- [ ] Final report is complete.

---

# Stop Conditions

Stop and document the conflict rather than guessing if repository discovery shows that:

- `SwordType` has incompatible semantics,
- the flower system cannot safely represent a managed wild flower without changing ownership/protection behavior,
- another branch already implements a vegetation manager,
- the intended base branch is ambiguous and choosing one could lose or overwrite active work,
- a required system is mid-refactor and duplicate implementation would be likely.

For ordinary implementation uncertainties, prefer the smallest compatible design based on existing project patterns and document the choice.
