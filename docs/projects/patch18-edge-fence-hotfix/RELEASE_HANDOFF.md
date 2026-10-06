# Wooden edge fence recovery handoff

Source baseline: current Patch 18 c8044e4565281514253e290167c555413c7a5293.
Target version remains **0.1.8d**. Source integration, remote backup and exact
candidate identity will be appended after the normal final gates.

Existing wooden panels keep their saved edge and physical layout when
neighbors are placed, removed or reloaded. New compatible wooden panels join
through their actual edge contacts. Other fence materials and gates do not
automatically join. Corners keep both panels when an arm is removed.

To build a corner, place the two straight arms, leaving the corner cell
empty, then place a fence in the corner gap. If that cell already contains a
single panel, remove it and place it again after both arms are present.
Clicking a horizontal support retains the edge opposite your viewing
direction. The decorator rotates the whole wooden panel or corner. Use
deliberate rotation/replacement to correct a previously displaced build;
the update does not guess its original intended location.

Before an eventual authorized upgrade, save a complete pre-upgrade world
backup with its current server/mod configuration and installed artifact
identity. Install the **same bundled `-all` candidate and SHA-256 on client
and server**, with Minecraft 1.21.1 / NeoForge 21.1.72; do not leave duplicate
0.1.8d jars. This project performs no production backup, world migration,
installed-binary replacement, deployment or publishing.

Downgrade is not certified as rollback: old code ignores the selector and
can normalize facing/layout again. Restoring the pre-upgrade backup and
matching prior software requires a separate tested operator procedure.
No in-place downgrade safety has been established.

The medallion motion/cape/swimming visual hold and Alligator client visual
hold remain. Fence acceptance cannot clear either hold. Local clean held
candidate preparation is permitted by README and the current fence kickoff;
dirty-source/provenance/dependency gates will not be bypassed. The prior
Alligator task's no-push scope is superseded for this fence work by the
owner's explicit authorization of normal non-force remote backup.

Task-created worktrees, branches and disposable worlds will be removed only
after source/evidence are clean, integrated and safely preserved. Owner
kickoffs/discovery files and unrelated worktrees/branches remain untouched.
