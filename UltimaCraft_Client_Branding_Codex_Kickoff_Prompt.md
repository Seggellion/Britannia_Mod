# Codex Kickoff Prompt — UltimaCraft Client Branding

You are beginning a new milestone-based feature project for the UltimaCraft Minecraft mod.

Your role is a senior Minecraft 1.21.1 / NeoForge client engineer, UI rendering investigator, resource-pack/resource-loading specialist, and implementation researcher.

The feature is **client branding**.

Read and follow these two project-root documents before making implementation decisions:

```text
UltimaCraft_Client_Branding_Design.md
UltimaCraft_Client_Branding_Codex_Playbook.md
```

Treat them as authoritative unless the project source proves a technical assumption wrong. If source contradicts an assumption, document the contradiction and propose the narrowest compliant adjustment.

---

## Project Goal

We need to transform Minecraft's pre-world presentation into UltimaCraft branding.

The required final behavior is:

1. The main title must display a custom graphic reading:

```text
UltimaCraft
```

2. Directly beneath it, display:

```text
Version 18
```

3. The existing custom main-menu background has already been replaced and is **protected**. Preserve its current appearance.

4. Remove vanilla Minecraft panorama/background artwork from other non-game/pre-world client screens wherever possible.

5. When removal is not practical, use an opaque black background.

6. Replace vanilla Minecraft splash text with a large researched corpus of Ultima/Ultima Online/UltimaCraft facts, jokes, and short original references.

7. The LLM/agent owns the graphics work. Do not simply tell the project owner to make the logo. Use image-generation capability if the environment exposes it. If final image generation is unavailable, prepare a precise asset-generation request and stop at that future asset gate rather than silently shipping placeholder art.

8. Do not alter in-world HUDs, inventories, containers, custom UltimaCraft service screens, or gameplay backgrounds as part of this feature.

---

## Expected Runtime Context

Verify rather than blindly assume, but current project context is expected to be approximately:

```text
Minecraft 1.21.1
NeoForge 21.1.72
Java 21
```

The primary integration branch is expected to be:

```text
patch-18
```

The current main local worktree is expected to be the `britannia_mod` folder.

This new feature must not be implemented directly in that main worktree.

---

## Worktree / Branch Requirement

Create a dedicated feature branch:

```text
client-branding
```

Create a new sibling worktree for it.

Recommended path if compatible with the actual repository layout:

```text
C:\projects\britannia\mod\Britannia_Mod_client_branding
```

Base it on `patch-18`.

Before creating anything:

- identify the actual repository root,
- inspect current branches,
- verify `patch-18`,
- verify the proposed branch/worktree does not already exist,
- record the starting commit and worktree state.

Do not modify, reset, clean, stash, or otherwise disturb the main `britannia_mod` worktree.

Do not push, merge, tag, release, or deploy.

---

# Execute Milestone 0 Only

Begin with **Milestone 0 — Reconnaissance, Baseline and Worktree Establishment** from the playbook.

Do not progress into Milestone 1 until I explicitly authorize it after reviewing your Milestone 0 report.

Milestone 0 is not a superficial file search. Perform robust source-level reconnaissance.

---

## Milestone 0 Required Investigation

### A. Existing UltimaCraft implementation

Search the complete project for anything related to:

```text
TitleScreen
ScreenEvent
panorama
panorama_overlay
menu_background
menu_list_background
title
logo
splash
splashes.txt
minecraft.png
edition
background
GuiGraphics
renderBackground
Screen
assets/minecraft
```

Find the exact implementation that currently replaces the main-menu background.

Record every resource and Java hook involved.

The current title background is protected. Take file hashes or otherwise record its exact current assets before later work.

### B. Minecraft 1.21.1 client source

Use the project's actual decompiled/mapped Minecraft source and NeoForge dependencies.

Do not guess based on memory or old tutorials.

Determine and document:

- the exact title-screen class,
- the exact resource(s) used to render the Minecraft title/logo,
- the exact splash loading resource and selection path,
- the exact panorama resources,
- the exact generic menu background resources,
- the exact menu list/header/footer resources,
- how nested pre-world screens obtain their background,
- which methods are responsible for the relevant rendering,
- which NeoForge screen events are available in this exact dependency version,
- whether a supported event can add `Version 18`,
- whether a supported event can implement a screen-aware black-background policy without cancelling widgets.

### C. Resource precedence

Confirm from the actual project/runtime that mod resources can override the required `minecraft` namespace assets.

Record exact paths.

### D. Screen state policy

Prove how to distinguish:

```text
main title screen
other pre-world screens
in-world screens
```

The intended policy is:

```text
main title:
    preserve approved UltimaCraft background

other pre-world screen:
    black background

in-world:
    unchanged
```

Do not implement this yet in Milestone 0. Establish the correct hook/resource strategy.

### E. Scope inventory

Enumerate pre-world screens that can reasonably be reached.

At minimum investigate:

```text
Main Title
Options
Language
Accessibility
Video Settings
Controls
Resource Packs
World Selection
Create World
Multiplayer
Add Server
Direct Connection
Realms, if present
Credits/Acknowledgements
pre-world connection/status screens
NeoForge/project-added pre-world screens
```

Record the current background mechanism for each.

### F. Splash-content architecture

Determine whether the cleanest 1.21.1 solution is:

1. exact replacement of the vanilla splash resource, or
2. a mod-owned splash resource and small title renderer.

Do not write the final corpus yet.

### G. Current rendering caveat

Investigate the current NeoForge 1.21.1 panorama-overlay behavior and avoid designing a solution that depends critically on translucent `panorama_overlay.png` behavior if the project's runtime is affected.

---

## Milestone 0 Documentation Deliverables

Create:

```text
docs/client-branding/INVENTORY.md
docs/client-branding/SCREEN_MATRIX.md
docs/client-branding/ASSET_MANIFEST.md
docs/client-branding/IMPLEMENTATION_LOG.md
```

`INVENTORY.md` must include exact class/resource paths and source findings.

`SCREEN_MATRIX.md` must include each pre-world screen, its current background source, and the likely implementation strategy.

`ASSET_MANIFEST.md` must identify the protected existing main-menu background files and hashes.

`IMPLEMENTATION_LOG.md` must record branch/worktree/base state and Milestone 0 findings.

---

## Baseline Validation

Run the repository's normal baseline compile/test workflow.

Use the project itself to discover canonical commands rather than inventing new ones.

If a client smoke launch is already part of the project's supported local workflow, perform it without changing gameplay data.

Record:

- command,
- result,
- failures,
- whether failures pre-existed your feature changes.

Milestone 0 should contain no production branding changes.

---

## Architecture Preference

The default implementation philosophy for later milestones is:

```text
resource override first
NeoForge event/render hook second
narrow helper/custom renderer third
mixin only when proven necessary
full TitleScreen replacement only as a last resort
```

We want to preserve vanilla navigation, buttons, narration, accessibility, and mod compatibility.

Do not build a replacement title screen merely because it is easy to control.

---

## Future Logo Requirement

Do not implement the final logo during Milestone 0, but measure and document what it will require.

The future title should be an original transparent `UltimaCraft` fantasy wordmark, not a Minecraft-logo clone and not a direct copy of an official Ultima logo.

The likely source-generation canvas is around:

```text
1024x256 transparent PNG
```

but verify the shipped/display dimensions from actual client rendering before locking this.

The final logo cannot be a placeholder.

---

## Future Splash Research Standard

The future splash milestone must be research-driven.

Primary factual research should start with official Ultima Online material, especially:

- Virtues and Principles
- Britain
- Moonglow
- other UltimaCraft cities
- moongates
- town criers
- banks/currency
- skills/training
- shrines and travel

Project-specific splashes must reflect systems that actually exist in UltimaCraft.

When a classic mechanic needs confirmation, inspect RunUO or another authoritative source rather than relying on memory.

The final corpus target is approximately:

```text
120–180 lines
```

with length, duplication, accuracy, and humor validation.

Do not populate the final corpus in Milestone 0.

---

## Hard Scope Restrictions

Do not modify:

- gameplay balance,
- blocks,
- entities,
- farming,
- economy,
- Rails,
- network protocols,
- server logic,
- custom service NPC UIs,
- in-world HUD/inventory visuals,
- unrelated branches,
- the approved title background.

Do not use a broad cleanup command against a dirty repository.

Do not delete unknown files because they "look generated."

---

## Gate 0

Milestone 0 passes only if all of the following are true:

- dedicated `client-branding` branch exists,
- dedicated worktree exists,
- correct `patch-18` base is recorded,
- protected current title background is identified,
- exact 1.21.1 title/splash/background resources are documented from source,
- screen matrix exists,
- likely architecture is justified,
- baseline build/test state is recorded,
- no production branding behavior has been changed,
- no unrelated files have been modified.

If Gate 0 passes and project-owner commit rules permit the milestone commit, create exactly one isolated local commit:

```text
docs(client-branding): establish client branding baseline
```

Then stop.

---

## Final Milestone 0 Report Format

Return:

```text
Milestone: 0 — Reconnaissance, Baseline and Worktree Establishment
Status:
Branch:
Worktree:
Base branch:
Starting HEAD:
Ending HEAD:
Commit:
Working tree before:
Working tree after:

Protected main-menu background:
- implementation:
- files:
- hashes:

Minecraft 1.21.1 title findings:
- class:
- title resource(s):
- splash resource/loader:
- panorama resources:
- menu background resources:
- relevant methods:
- relevant NeoForge hooks:

Recommended later architecture:
- title logo:
- Version 18:
- splashes:
- black pre-world backgrounds:
- mixin required?: yes/no/unknown

Documentation created:
- ...

Validation:
- command:
- result:

Gate 0:
- PASS / BLOCKED

Blockers:
- ...

Next milestone:
Milestone 1 — Background Policy Foundation

Progression authorized:
No
```

Do not begin Milestone 1.
