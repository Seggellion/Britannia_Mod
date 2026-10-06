# Wooden edge fence recovery handoff

Source baseline: current Patch 18 c8044e4565281514253e290167c555413c7a5293.
Target version remains **0.1.8d**. Implementation status:
**IMPLEMENTATION_COMPLETE_PENDING_ACCEPTANCE**. Candidate status:
**CANDIDATE_PREPARED_RELEASE_HELD**.

Tested implementation 07359f98277417b8ffdafd9d57b55b828804ab5b is integrated
by fast-forward into current `patch-18` and backed up with a normal non-force
push (live remote verified). Closeout documentation is a descendant with no
application changes; final branch/remote/cleanup identities are preserved in
`tmp/edge-fence-0.1.8d/CLOSEOUT.json` in the canonical checkout.

Held candidate: `tmp/edge-fence-0.1.8d/release/britannia_mod-0.1.8d-all.jar`.
36,140,545 bytes; SHA-256
`89c3f936d2f942a0967ddc500542a8b0f587f5987a0902b2833596c099b486cb`.
Embedded source 07359f98277417b8ffdafd9d57b55b828804ab5b, dirty=false;
build timestamp 2026-10-06T20:38:59.278362500Z. Normal clean packaging,
dependency/provenance gates and complete JUnit/GameTests pass. Pinned Java21,
Gradle8.9, MC1.21.1, NeoForge21.1.72, GeckoLib4.6.6 remain unchanged.

Remaining fence acceptance: the desktop locked at the Windows PIN screen
before the matching local-client world scene. Actual mouse packets,
displayed state/render agreement, art/UVs, hit outlines, keyboard walk/jump,
decorator edits and visual reload must be checked after unlocking. Actual
64-layout save/restart and chunk eviction/load are already automated passes.
Three measured heap samples show +8.32 MiB median Java heap; repeated startup
variance, isolated model-bake and frame timings remain unmeasured. See
ACCEPTANCE.md and COST_MEASUREMENTS.md. Neither source tests nor main-menu
screenshots are presented as these missing client checks.

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
