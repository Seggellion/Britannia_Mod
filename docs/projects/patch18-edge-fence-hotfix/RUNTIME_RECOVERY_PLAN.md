# Fence runtime recovery plan - 2026-10-09

Execute the owner's supplied runtime recovery plan on current Patch 18 / 0.1.8d.
The October 8 layout design remains the selected implementation. The owner now
provided the affected remote server's `/grabby env`: both observed sides load
the same old normalization jar. Source integration is separate from runtime
installation and acceptance. No production deployment is authorized.

## Baseline and selected artifact

Canonical/remote and retained task checkout initially agree at
5a8cab9a38fac52253dea20431dea60cd15812c0. Reuse
`tmp/fence-runtime-rediscovery-2026-10-08/source`; retain its fixtures and prior
red/green traces. Preserve the owner's combined/plaster handoff changes,
untracked playbooks/discovery and every unrelated worktree. The retained checkout
subsequently fast-forwarded normally to c88e0539412fbecef1a494e120388b17a84da1a7,
preserving both concurrent plaster documentation commits. Application inputs
still match the selected clean source.

Select the existing clean combined jar, without rebuilding for tools/docs:
`tmp/fence-runtime-recovery-2026-10-09/release/britannia_mod-0.1.8d-all.jar`.
36,142,686 bytes; SHA-256
e64fe63c6ed0836f520e3d9b66ac7336b3e323677b63a433d65d48852c30fd70;
source cf49b1700d1a8992f39396f2ae531f0cb26502d7, dirty=false,
built 2026-10-08T22:31:37.806305200Z. It is an ancestor of current Patch 18;
later changes are documentation/test/tooling only. All 10,698 entries previously
matched the October 7 combined jar except provenance. Preserve alligator,
medallion, plaster/wall, dependencies, artwork and legacy layout behavior.

## Milestones and required evidence

1. **M0:** read current instructions/release reassessment; verify refs, dirty
   owner files, source ancestry and selected full artifact identity. Keep Java 21,
   MC 1.21.1, NeoForge 21.1.72 and version 0.1.8d.
2. **M1:** retain the owner's affected-server artifact/build/server output and
   recheck the actual CurseForge instance, active jars, current process and
   startup origin. Record installed files and loaded sessions independently.
3. **M2:** reuse Gradle provenance and `/grabby env`; add only the missing
   read-only installation preflight. Test hash/source mismatch, same-version
   different bytes, renamed duplicates, disabled `.old` files and file/runtime
   distinction. Keep selected identity sidecar free of user paths/credentials.
4. **M3:** back up the original client jar outside the active mod scan. Replace
   only Britannia after the relevant client fully exits; preserve other mods,
   settings and worlds. Verify copied bytes before launch. Start a disposable
   dedicated server with the exact selected jar and a copied fixture world.
   Verify its runtime hash/source, then match a restarted client to that server.
   Actual mouse/client state/render acceptance remains separate from command
   engine probes and prior constructed BlockItem traces.
5. **M4:** change behavior only after a new defect on matching corrected builds
   has direct red evidence. Otherwise skip application changes. No new fence
   schema, migration repair, gates/waterlogging or launcher speculation.
6. **M5:** commit/integrate only this project's tooling/fixture/evidence/docs,
   preserving concurrent owner edits. Normally push Patch 18 and verify the live
   remote. Do not reset history, force-push, import Patch 19 or bypass main rules.
7. **M6:** deliver one upload jar/sidecar and exact backup/offline replacement/
   restart/loaded-identity/fresh-panel verification packet. Production remains
   unchanged under the owner's explicit instruction. Preserve release conditions.

## Completion boundaries

PATCH18_INTEGRATED never implies OWNER_FENCE_RUNTIME_ACCEPTED. FILE_MATCH never
implies LOADED_RUNTIME_VERIFIED. The new client runtime must be observed after
restart; the remote's current old identity is known, not remotely replaced.
The same A coordinate must survive B addition/ticks/removal/reconnect; inspect
full client and authoritative states plus displayed rails/UVs/hit outlines.

The interaction helper currently fails before initialization. Continue backups,
preflight, dedicated engine probes, prepared manual scenes and source handoff.
Do not replace a jar while its real client is running, simulate a visual pass,
repeat broken helper initialization indefinitely or force an unverified
production connection. Retain the reproduction environment for the remaining
checks. Medallion UI/movement remain approved; fence corner/decorator/reload,
reopened plaster/paired-wall appearance and alligator playback remain pending.
Timing repetitions remain follow-up.
