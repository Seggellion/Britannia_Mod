# UltimaCraft Client Branding Codex Playbook

**Project:** UltimaCraft  
**Feature branch:** `client-branding`  
**Base branch:** `patch-18`  
**Primary repo/worktree:** existing main worktree remains untouched  
**Recommended new worktree:** sibling worktree dedicated to this feature  
**Execution style:** milestone-gated, evidence-first, one isolated commit per approved milestone

---

## 1. Purpose

This playbook governs implementation of the UltimaCraft client-branding project described in:

```text
UltimaCraft_Client_Branding_Design.md
```

The feature replaces the visible Minecraft title treatment with an original UltimaCraft title, adds `Version 18`, preserves the existing main-menu background, blackens other pre-world menu backgrounds, and replaces vanilla splash text with a researched Ultima-themed corpus.

The feature is intentionally client-only.

---

## 2. Repository / Worktree Rules

The current primary local project is expected to be the `britannia_mod` worktree with current integration work on `patch-18`.

Do not implement this feature directly in that worktree.

### Required setup

From the repository that contains `patch-18`:

1. verify the current branch and repository root,
2. verify `patch-18` exists,
3. verify the base branch is current locally,
4. create a new branch named:

```text
client-branding
```

5. create a new sibling worktree for that branch.

Recommended Windows naming:

```text
C:\projects\britannia\mod\Britannia_Mod_client_branding
```

If that exact path conflicts with the actual repository layout, choose a sibling path with the same intent and document it.

### Never do without explicit owner authorization

- modify the main `britannia_mod` worktree for this project,
- merge another feature branch,
- merge `client-branding` back into `patch-18`,
- push,
- force-push,
- rebase shared work,
- tag,
- release,
- deploy,
- rewrite unrelated history.

---

## 3. Milestone Discipline

Each milestone follows this pattern:

1. state current milestone,
2. inspect relevant files/source,
3. make only milestone-scoped changes,
4. run milestone validation,
5. record evidence,
6. report blockers,
7. commit exactly once only after all milestone gates pass and owner authorization permits it,
8. stop before the next milestone unless the owner explicitly authorizes progression.

A milestone failure is not permission to broaden scope.

Never "fix while here" outside the milestone.

---

# Milestone 0 — Reconnaissance, Baseline and Worktree Establishment

## Objective

Establish the exact Minecraft 1.21.1/NeoForge implementation used by this project before changing visual behavior.

## Tasks

### Worktree

- create `client-branding` from `patch-18`,
- create dedicated worktree,
- record base commit,
- record branch divergence,
- record clean/dirty state.

### Project reconnaissance

Search for all existing title/menu branding work.

At minimum search for:

```text
TitleScreen
ScreenEvent
panorama
panorama_overlay
menu_background
menu_list_background
title
splash
splashes.txt
minecraft.png
edition
background
GuiGraphics
renderBackground
Screen
```

Inspect:

- Java client hooks,
- mixins if any,
- `assets/minecraft`,
- `assets/<ultimacraft namespace>`,
- title/menu textures,
- resource-pack-like overrides,
- existing approved main-menu background assets,
- client configuration related to menu rendering.

### Vanilla/NeoForge source reconnaissance

Use the actual local Minecraft 1.21.1/NeoForge development dependencies.

Do not assume resource paths.

Record:

- exact class responsible for the title screen,
- exact resource path(s) for the title/logo,
- exact splash resource/loading behavior,
- exact panorama resources,
- exact generic menu background resources,
- exact list/header/footer background resources,
- relevant NeoForge screen events/hooks,
- whether `Screen#renderBackground` or another method can be safely intercepted,
- whether pre-world state can be reliably distinguished by active level/world state.

### Existing background protection

Determine exactly how the already-replaced main-menu background works.

Classify it as one or more of:

- panorama face replacement,
- panorama overlay,
- custom static title texture,
- custom Java renderer,
- other.

Take hashes or otherwise record the protected asset(s).

### Screen inventory

Build `docs/client-branding/SCREEN_MATRIX.md`.

Inventory all accessible pre-world screens and note their current background source.

### Documentation

Create:

```text
docs/client-branding/INVENTORY.md
docs/client-branding/SCREEN_MATRIX.md
docs/client-branding/ASSET_MANIFEST.md
docs/client-branding/IMPLEMENTATION_LOG.md
```

## Validation

Run the normal clean compile/test baseline appropriate to the repository.

Also verify a client can start before feature changes if the local environment supports client launch.

## Gate 0

Pass only if:

- separate worktree/branch is established,
- exact title/splash/background resource paths are documented from local source,
- current main-menu background implementation is understood,
- no protected background asset has been modified,
- baseline build state is known,
- no unrelated files changed.

## Commit

Recommended:

```text
docs(client-branding): establish client branding baseline
```

Stop and report Gate 0.

---

# Milestone 1 — Background Policy Foundation

## Objective

Create the architecture that can make pre-world screens black without damaging the approved main title background or in-world UI.

## Decision

Choose the least invasive approach proven by Milestone 0.

Priority:

1. resource overrides,
2. NeoForge client screen/render events,
3. narrow screen-aware renderer/helper,
4. mixin only when the first three are demonstrably insufficient.

## Tasks

Implement a deterministic policy equivalent to:

```text
Title screen:
    preserve approved UltimaCraft background

Other pre-world screen:
    opaque black

In-world screen:
    unchanged
```

Where exact resources are standalone and safe to override, use black replacement PNGs rather than code.

Programmatically generated black textures are allowed.

Do not replace functional control sprites.

## Important Regression Risk

If the approved title background is currently implemented by replacing the same panorama resources used by nested menus, decouple title rendering before blackening those resources.

Never destroy the approved title appearance as a shortcut.

## Validation

Check at minimum:

- title screen,
- options,
- language,
- accessibility,
- video settings,
- controls,
- resource packs,
- world selection,
- multiplayer.

Confirm:

- title background unchanged,
- nested backgrounds black,
- controls remain visible,
- no black background injected in-world.

## Gate 1

Pass only if the policy works without title-background regression.

## Commit

Recommended:

```text
feat(client): establish non-game black background policy
```

Stop and report Gate 1.

---

# Milestone 2 — UltimaCraft Title Graphic

## Objective

Replace the visible Minecraft title art with the final UltimaCraft wordmark.

## Asset Workflow

### Step 1: Measure

Record:

- actual title render dimensions,
- title anchor,
- effective display size at common GUI scales,
- current texture resource dimensions,
- safe horizontal/vertical space before buttons and splash.

### Step 2: Generate

The LLM/agent is responsible for graphic generation.

Preferred source asset:

```text
1024x256 transparent PNG
```

Use the design-document prompt as a baseline.

Generate multiple candidates if the runtime supports image generation.

### Step 3: Evaluate

Reject candidates that:

- look like Minecraft logo clones,
- look like official Ultima logo copies,
- lose letter identity when scaled down,
- have unreadable bevel/detail,
- include accidental backgrounds,
- crop the first/last letter,
- depend on tiny ornament,
- contain misspelled text.

### Step 4: Integrate

Prefer overriding the exact vanilla title asset path discovered in Milestone 0 so the normal title screen continues to own layout.

If code rendering is required, suppress only the old title art and draw the new asset without replacing the complete title screen.

### Step 5: Asset Hygiene

Production JAR should contain only the selected final logo unless the project intentionally retains source candidates outside runtime assets.

Document:

- source dimensions,
- shipped dimensions,
- alpha state,
- final file hash,
- exact resource path.

## Validation

Test the title at:

- 1280x720,
- 1920x1080,
- 2560x1440,
- one ultrawide resolution if practical,
- GUI scales Auto, 2, 3, and 4 where available.

Confirm no visible Minecraft title artwork remains.

## Gate 2

Pass only with final-quality art.

A placeholder cannot pass Gate 2.

If Codex has no image-generation capability, Gate 2 must remain blocked with a fully prepared asset request rather than being falsely marked complete.

## Commit

Recommended:

```text
feat(client): replace title branding with UltimaCraft
```

Stop and report Gate 2.

---

# Milestone 3 — `Version 18` Subtitle and Responsive Title Layout

## Objective

Add the visible subtitle:

```text
Version 18
```

## Tasks

- implement a client-only title render hook if no existing title overlay hook exists,
- anchor subtitle to the title/logo region,
- center it,
- keep it independent from splash placement,
- handle resize and GUI-scale changes,
- preserve narration/accessibility behavior.

Do not bake the subtitle into the logo unless Milestone 0 proves rendering text independently is technically worse.

## Validation

At all title-screen test resolutions verify:

- centered placement,
- no overlap with logo,
- no overlap with splash,
- no overlap with buttons,
- no clipping,
- no shift after resize,
- readable contrast.

## Gate 3

Pass only if responsive.

## Commit

Recommended:

```text
feat(client): add Version 18 title subtitle
```

Stop and report Gate 3.

---

# Milestone 4 — Splash Research Corpus

## Objective

Replace vanilla splashes with a researched, funny, relevant Ultima/UltimaCraft corpus.

This milestone must not be treated as "ask an LLM for 150 jokes."

## Research Tasks

Research official Ultima Online material first.

At minimum cover:

- Virtues and Principles,
- Britain,
- Moonglow,
- Yew,
- Minoc,
- Trinsic,
- Serpent's Hold,
- Vesper,
- buccaneers den,
- Lord British,
- The Avatar,
- shroud of the avatar,
- Ultima Online,
- Minax the enchantress,
- Mondain the wizard,
- The Guardian,
- The shadowlords,
- The Codex of Wisdom,
- Iolo,
- Dupre,
- Gwenllian "Gwenno",
- Shamino,
- Sosaria,
- Britannia,
- Moongate,
- Trammel,
- Felucca,
- Gem of Immortality,
- Town of Cove,
- Reagents,
- Skara Brae,
- Jhelom,
- Magincia,
- moongates,
- town criers,
- banks/currency,
- shrines,
- travel,
- skills/training.

Also inspect the current UltimaCraft project for stable project-specific facts suitable for splash jokes.

Where classic mechanic accuracy is needed, inspect RunUO or another authoritative source rather than relying on memory.

## Research Artifact

Create:

```text
docs/client-branding/SPLASH_RESEARCH.md
```

For each source/topic record:

- source name,
- source location,
- factual notes in original wording,
- candidate concepts.

Do not paste long copyrighted passages.

## Corpus Requirements

Target:

```text
120–180 accepted lines
```

Editorial targets:

- ideal <= 55 characters,
- hard ceiling 70 unless live testing justifies an exception,
- no empty lines,
- no exact duplicates,
- no case-only duplicates,
- no obvious near-duplicate punchline spam,
- no vanilla Minecraft splashes,
- no unsupported factual claims,
- no long copied quotes.

## Humor Review

Score candidates using the rubric from the design document.

Reject factual lines with an accuracy score of 0.

## Implementation

Select either:

- exact vanilla splash resource replacement, or
- mod-owned splash source/renderer,

based on Milestone 0 evidence.

## Automated Validation

Add a splash validator/test that checks:

- empties,
- whitespace,
- duplicates,
- max length,
- encoding,
- accidental vanilla content where feasible.

## Live Validation

Restart/re-enter title screen enough times to establish:

- only UltimaCraft corpus appears,
- text remains within title layout,
- no malformed characters,
- no overlap with `Version 18`,
- randomization/reload behavior is acceptable.

## Gate 4

Pass only with research artifact + validated corpus + live evidence.

## Commit

Recommended:

```text
feat(client): replace vanilla splashes with UltimaCraft corpus
```

Stop and report Gate 4.

---

# Milestone 5 — Complete Pre-World Background Sweep

## Objective

Expand black-background coverage from the foundation to the complete discovered screen matrix.

## Tasks

Walk every pre-world screen in `SCREEN_MATRIX.md`.

Include, when available:

- Options
- Accessibility
- Language
- Video
- Controls
- Resource Packs
- World Selection
- World Creation
- Multiplayer
- Add Server
- Direct Connection
- Realms
- Credits/Acknowledgements
- pre-world connecting/status screens
- any NeoForge-added or project-added pre-world menus

For each screen record:

- old background source,
- final mechanism,
- black result,
- functional controls verified.

## Flash/Transition Test

Watch transitions between screens.

A screen that is black after one frame but briefly flashes a vanilla panorama does not pass.

## In-World Guard

Re-test:

- pause menu,
- options while in-world,
- inventory,
- containers,
- custom UltimaCraft screens.

These must remain unchanged unless the design is explicitly amended.

## Gate 5

Pass only when all practical pre-world screens are black and no gameplay UI regression is present.

## Commit

Recommended:

```text
feat(client): complete pre-world background cleanup
```

Stop and report Gate 5.

---

# Milestone 6 — Automated Regression Coverage

## Objective

Make the implementation maintainable.

## Tasks

Add or finalize tests for:

- splash corpus validation,
- production asset presence,
- production asset dimensions,
- title asset alpha,
- black replacement asset opacity,
- client-only class separation,
- resource path existence,
- no accidental candidate/source files in runtime assets where practical.

Run:

- focused tests,
- full repository tests,
- clean build,
- dedicated-server-safe build/run check as supported by project.

## Gate 6

Pass only with all applicable automated validation green.

## Commit

Recommended:

```text
test(client): cover branding resources and splash corpus
```

Stop and report Gate 6.

---

# Milestone 7 — Live Client Acceptance

## Objective

Perform full owner-facing visual validation.

## Required Matrix

### Resolutions

- 1280x720
- 1920x1080
- 2560x1440
- ultrawide if practical

### GUI Scales

- Auto
- 2
- 3
- 4 where available

### Main Menu

Verify:

- approved background preserved,
- UltimaCraft title is final quality,
- Minecraft title absent,
- Version 18 present,
- splash present,
- no collisions,
- no unintended panorama motion,
- buttons work,
- resize works.

### Pre-World Menus

Verify black backgrounds and readable controls.

### In-World

Verify no regression.

## Evidence

Update:

```text
docs/client-branding/VALIDATION.md
docs/client-branding/IMPLEMENTATION_LOG.md
```

Use screenshots/file hashes where normal project practice permits.

## Gate 7

Requires explicit owner validation/approval for visual acceptance.

## Commit

Recommended only after owner approves evidence:

```text
docs(client-branding): record live acceptance evidence
```

Stop and report Gate 7.

---

# Milestone 8 — Final Audit and Handoff

## Objective

Ensure the branch is ready for later integration without merging it.

## Tasks

- clean working tree,
- inspect final diff from merge base,
- confirm only client-branding files changed,
- confirm all milestone commits exist,
- confirm documentation matches implementation,
- confirm no placeholder asset remains,
- confirm no temporary generation files are shipped,
- confirm build/test results,
- confirm branch divergence,
- prepare concise merge notes.

Do not merge.

## Gate 8

Pass when branch is integration-ready.

## Optional Final Commit

Only if documentation needs a final isolated correction:

```text
docs(client-branding): finalize implementation handoff
```

Do not create an empty ceremonial commit.

---

## 4. Research Source Policy

### Technical

Prefer:

1. actual local decompiled/mapped Minecraft 1.21.1 source,
2. actual local vanilla 1.21.1 resources,
3. NeoForge 1.21.1 documentation/source,
4. official issue/PR history when a current rendering defect matters.

Avoid copying a solution from an old Forge/Fabric tutorial without verifying the actual 1.21.1 API.

### Ultima

Prefer:

1. official `uo.com`,
2. current project implementation/canon,
3. RunUO source for classic mechanics,
4. secondary sources only as cross-checks.

---

## 5. Scope Guard

Changes permitted:

- client-only Java needed for branding,
- title/menu resources,
- splash content,
- tests,
- client-branding documentation.

Changes not permitted without approval:

- gameplay systems,
- entities,
- blocks,
- farming,
- economy,
- Rails,
- server protocols,
- networking unrelated to UI,
- custom service UI redesign,
- existing approved background redesign,
- other branches.

---

## 6. Agent Reporting Format

After each milestone report:

```text
Milestone:
Status:
Branch:
Worktree:
Starting HEAD:
Ending HEAD:
Commit:
Files changed:
Validation run:
Validation result:
Protected main-menu background status:
Known blockers:
Next milestone:
Progression authorized: No
```

For a failed gate, do not commit unless the playbook or owner explicitly authorizes a diagnostic/documentation commit.

---

## 7. Stop Conditions

Stop immediately and report if:

- the new worktree cannot be safely created,
- `patch-18` cannot be identified,
- existing title background ownership cannot be determined,
- required visual asset would be destroyed,
- the proposed hook affects in-world screens,
- the only apparent implementation requires a broad invasive patch,
- image generation is unavailable at the final-art gate,
- live validation shows unreadable controls,
- unrelated branch changes contaminate the worktree.

A stop condition is a request for an architectural decision, not permission to improvise across scope.
