# Initial Codex Prompt — UltimaCraft New Assets Project

You are starting a new milestone-based feature project for the UltimaCraft Minecraft mod.

Your task in this prompt is **Milestone 0 only: Workspace Bootstrap and Repository Reconnaissance**.

Do not implement gameplay assets yet.

## Authoritative project documents

Read these files from the project root before making implementation decisions:

- `UltimaCraft_New_Assets_Design_Document.md`
- `UltimaCraft_New_Assets_Codex_Playbook.md`

Treat them as authoritative for scope, safety rules, milestone boundaries, and validation.

## Repository/worktree requirements

The current primary repository working copy is expected at:

`C:\projects\britannia\britannia_mod`

The project base branch is:

`patch-18`

This new project must use:

- branch: `new-assets`
- worktree directory: `C:\projects\britannia\new-assets`

The raw source assets are at:

`C:\projects\britannia\raw fiels\models to import`

The spelling of `raw fiels` is intentional here. Verify the actual path on disk rather than silently correcting it.

Other agents may continue working in `C:\projects\britannia\britannia_mod`, so do not perform feature work there.

## Git safety

Before creating anything:

1. inspect the current repository state;
2. confirm `patch-18` exists;
3. check whether branch `new-assets` already exists;
4. check whether `C:\projects\britannia\new-assets` already exists;
5. inspect `git worktree list`.

If the branch/worktree does not exist, create it from `patch-18` with the equivalent of:

```powershell
cd C:\projects\britannia\britannia_mod
git worktree add -b new-assets C:\projects\britannia\new-assets patch-18
```

If either the branch or directory already exists, do **not** delete or overwrite it. Verify its state and either safely reuse it or stop with a blocker report.

Once the worktree exists, perform all project work inside:

`C:\projects\britannia\new-assets`

Do not merge, rebase, push, force-push, tag, or modify unrelated branches.

Do not create a milestone commit unless I explicitly authorize the commit after reviewing your Milestone 0 report.

## Milestone 0 objective

Reconnoiter the existing UltimaCraft codebase so later milestones extend existing systems instead of creating parallel ones.

You must identify and document the best existing precedents for:

1. block and item registration;
2. entity registration;
3. entity variants and synchronized/persistent variant data;
4. GeckoLib or other animation systems used in the mod;
5. regional spawning/population control;
6. the exact Jhelom region/city identifiers and how regional caps are enforced;
7. dedicated-server population/bootstrap integration;
8. multi-block structures and atomic placement/breaking;
9. block entities;
10. container/menu/inventory implementations;
11. Adventure-mode custom placement allowances;
12. axe harvest/destruction rules and block/item tags;
13. water containers and any existing water-acquisition systems;
14. UltimaCraft skill storage and skill-gain APIs;
15. Swordsmanship, Mace Fighting, Fencing, Tactics, Wrestling, Anatomy, and Lumberjacking identifiers;
16. weapon-to-skill classification;
17. event hooks for player attacks/hits and weapon durability;
18. existing machine/processing/crafting patterns;
19. textile-related items such as wool, cotton, flax, silk, thread, yarn, cloth, or tailoring systems;
20. neighbor-aware blocks such as fences, walls, stairs, connected models, or custom connection state;
21. Creative Tab registration;
22. localization, loot table, tags, and datagen conventions;
23. any existing moongate implementation, especially the known Dungeon Shame moongate behavior.

Also inspect the repository for any existing asset names that would collide with or duplicate the requested new assets.

## Documentation to create

Inside the new worktree, create:

`docs/new-assets/IMPLEMENTATION_LOG.md`

For Milestone 0, record:

- project start date;
- branch;
- worktree path;
- base branch;
- starting HEAD;
- working-tree state;
- each architectural precedent found;
- exact relevant file/class/resource paths;
- any existing registry IDs that overlap the requested assets;
- likely technical risks;
- any requirement that appears ambiguous only after code inspection.

Do not create the full asset manifest yet. That is Milestone 1.

## Raw asset directory in Milestone 0

Only verify that the raw directory exists and record its top-level structure.

Do not perform the full recursive asset inventory yet. That is Milestone 1.

Do not modify source files in that directory.

## Important project requirements to keep in mind during recon

The later milestones will add:

- Ibis bird, white/scarlet, Jhelom-only, regional maximum 15;
- Moongate;
- six merchant cart colors;
- 3-high x 2-wide animated training dummy that trains Swordsmanship/Mace Fighting/Fencing up to 25.0, has a 3-second per-player cooldown, small Tactics chance, does not train Wrestling/Anatomy/Lumberjacking, and does not damage weapon durability;
- 2x2x3 fountain;
- Moonglow bushes;
- sandstone brick walls;
- globe;
- small/medium/large crate containers;
- 2-high water well that provides water;
- 3-high double-sided climbable ladder placeable by Adventure players on the ground and destroyable with an axe;
- 2-high scarecrow;
- fern;
- 2-high dress form;
- folded cloth;
- 2-wide x 3-high loom;
- bolt of cloth;
- spinning wheel converting wool to ball of yarn and cotton/flax/silk to spool of thread;
- 1x2 display cases that visually combine into independent/end/middle/corner forms based on neighboring display cases;
- pewter mug;
- kettle;
- plates and silverware.

Missing models/textures will eventually receive placeholders. Scarlet ibis art should later be derived from white art if no scarlet texture exists.

## Scope boundary

Milestone 0 is reconnaissance only.

Do not:

- register new blocks/items/entities;
- import models/textures;
- generate the scarlet ibis texture;
- implement training logic;
- implement containers;
- implement processing;
- change spawn tables;
- change moongates;
- modify unrelated files;
- proceed to Milestone 1 automatically.

## Required final report

When Milestone 0 is complete, stop and report:

```text
Milestone: 0 — Workspace Bootstrap and Repository Reconnaissance
Branch:
Worktree:
Base branch:
Starting HEAD:
Current HEAD:

Workspace status:
- ...

Recon findings:
- registration:
- entities/variants:
- animation:
- regional spawning/Jhelom:
- server population:
- multi-block structures:
- containers:
- Adventure placement:
- axe/tool behavior:
- water systems:
- skills:
- weapon classification:
- hit/durability hooks:
- crafting/processing:
- textiles:
- neighbor-aware blocks:
- creative/localization/datagen:
- moongates:

Potential naming collisions:
- ...

Top-level raw asset folder check:
- ...

Risks/blockers:
- ...

Files changed:
- ...

Validation:
- ...

Commit status:
- Not committed; awaiting owner authorization.
```

Do not commit until I explicitly approve Milestone 0.
