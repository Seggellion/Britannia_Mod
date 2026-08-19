# Codex Kickoff Prompt — Managed Vegetation `patch-18`

Copy and paste the prompt below into a new Codex task opened in the Britannia Mod workspace.

---

You are working in the Britannia Mod repository. Implement and verify the managed vegetation remediation described in:

`MANAGED_VEGETATION_PATCH_18_TASKS.md`

Treat that document as the source of truth. Read it completely before making changes. A previous investigation identified the likely causes, but the project may have changed since the document was prepared, so confirm every relevant assumption against the current code before editing.

Work on the `patch-18` branch. At the start:

1. Confirm the current branch is `patch-18` and inspect the current commit and worktree status.
2. Preserve all unrelated user changes and untracked files. Do not reset, overwrite, reformat, or include unrelated work.
3. Re-read the managed vegetation implementation, its configuration, registry entries, event handlers, client Adventure-mode mixin, and existing vegetation tests.
4. Update your working plan from the task document, then implement the feature rather than stopping after analysis.

The implementation must satisfy all five requirements:

1. Increase the natural vegetation spawn interval by 50%, from the current effective `8192` opportunity interval to `12288`, without changing lifecycle/regrowth timers. Existing server config files must receive the slower behavior without requiring deletion.
2. When a successfully placed block claims a managed vegetation position, retire the node's ownership while preserving the placed block. Cover single- and multi-block placement, cancelled events, tall vegetation, and all reserved positions.
3. Remove the post-cut ghost-block behavior. The invisible regrowth controller must be replaceable, stale metadata must not impose sword-only behavior on unrelated blocks, and reconciliation must never overwrite construction or retry stale obstruction forever.
4. Creative players and permission-level-2 administrators must be able to cut managed vegetation with a bare hand or any item, in any game mode, without item durability loss. Non-admin Survival and Adventure players remain sword/recognized-harvest-blade only.
5. Managed ferns must use Britannia's existing `BlockRegistry.FERN` / `britannia_mod:fern`, not `Blocks.FERN`. Reuse the existing model, texture, block, and item; do not create duplicate fern assets or alter unrelated vanilla-fern behavior.

Maintain these invariants:

- Vegetation grows in air above ordinary grass/dirt-family substrate and never replaces its supporting grass block.
- Construction wins over managed vegetation.
- Break interception is allowed only when persisted ownership and the live block representation agree.
- Managed cuts remain server-authoritative, atomic, no-drop operations that use the existing regrowth lifecycle.
- Unmanaged plants and unrelated placed blocks retain normal behavior.

Use tests to reproduce the defects before or alongside the fixes. Add focused unit/integration coverage for timing, ownership handoff, controller replacement, stale-node reconciliation, all permission/tool combinations, custom fern spawning/cutting/regrowth, save/reload behavior, and the Adventure client path. Include a real event/world-level test where a unit seam cannot prove NeoForge event ordering or replacement behavior.

Run at minimum:

```powershell
.\gradlew test --tests "com.seggellion.britannia_mod.vegetation.*"
```

Then run any relevant GameTests or broader verification available in the repository. If a test cannot run because of an environmental issue, distinguish that clearly from a code failure and continue with all safe verification still available.

Before finishing:

1. Re-read every checkbox and the definition of done in `MANAGED_VEGETATION_PATCH_18_TASKS.md`.
2. Inspect the final diff for accidental or unrelated changes.
3. Confirm the effective timing behavior for both new and existing configuration files.
4. Report the files changed, the behavioral decisions made, and exact test results.
5. Call out any remaining manual in-game checks or genuine blockers. Do not claim completion while required work remains.

Do not commit, push, or open a pull request unless I explicitly request it.
