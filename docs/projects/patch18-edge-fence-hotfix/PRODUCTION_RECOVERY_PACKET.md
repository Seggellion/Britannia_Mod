# Verified combined upload packet - 2026-10-09

**Packet prepared; production deployment is not authorized.** The owner explicitly
said not to deploy. Current fence live acceptance and the existing combined
release conditions remain pending. PATCH18_INTEGRATED is a source status.

## The one selected upload artifact

`C:/projects/britannia/mod/Britannia_Mod/tmp/fence-runtime-recovery-2026-10-09/release/britannia_mod-0.1.8d-all.jar`

- Filename: britannia_mod-0.1.8d-all.jar; **36,142,686 bytes**.
- Full SHA-256: **e64fe63c6ed0836f520e3d9b66ac7336b3e323677b63a433d65d48852c30fd70**.
- Clean source: **cf49b1700d1a8992f39396f2ae531f0cb26502d7**, dirty=false,
  version 0.1.8d; built **2026-10-08T22:31:37.806305200Z**.
- Portable sidecar: adjacent britannia_mod-0.1.8d-all.identity.json and tracked
  evidence/runtime-recovery-2026-10-09/SELECTED_ARTIFACT.json.
- The source is an approved Patch 18 ancestor. Later project changes are tools,
  fixtures and documentation only, excluded from the mod artifact; no rebuild
  merely for a new documentation commit. Source integration/normal non-force
  backup identity is recorded in the task CLOSEOUT.json after source publication;
  that receipt records pending live acceptance separately.
- This retains all integrated alligator/medallion/plaster/wall/fence fixes and
  pinned dependencies. It is byte-identical to the October 7 combined archive
  except provenance. Do not upload the thin jar or the historical fence-only jar.

## Current installed/loaded situation

The owner's remote `/grabby env` reports c04f3092fa577e4c98c4c4ee16febd5008403ccc98a0ebc064d860e3ac8981c6,
source 4435912197fba84630d6e5a0544f29fc3a736450, filename britannia_mod-0.1.8d.jar,
36,107,339 bytes, DedicatedServer. The historical client session also loaded
that old jar. The relevant JVM has now exited and only the active Britannia jar
has been replaced: exactly one selected -all jar passes FILE_MATCH. Ten other
mod files remain byte-identical; the original backup and retired old active jar
are preserved outside mods. A new client session has not been launched or verified. The disposable DedicatedServer on
127.0.0.1:25583 actually loaded the selected new hash/source. A restarted client
matching it and actual mouse/render/reconnect acceptance remain unverified.
See the dated matrix/acceptance; do not confuse FILE_MATCH with JVM load proof.

## Operator steps for an eventual authorized remote upgrade

1. Complete the pending matching disposable client/fence acceptance and retain
   the current combined release conditions. Do not treat this packet as rollout
   approval. Record the affected server's current artifact/build/server lines
   and actual profile/server directory, running MC 1.21.1 / NeoForge 21.1.72 /
   Java21 and full mod inventory before starting.
2. Use the host's established server control to **stop it fully**. Record the
   stopped session. Save a complete pre-upgrade backup of world dimensions,
   playerdata, region/level data, datapacks, server/mod configuration and original
   installed jar/mod inventory. Keep private configuration/credentials private
   on the backup host; this project neither needs nor collects them. Verify the
   backup is complete/readable and retain the old full hash/source above.
3. In the actual server's mods directory, move the old active Britannia jar into
   the backup outside the active mod scan. Preserve unrelated mods and disabled
   files. Upload **only the selected -all jar above**. Confirm its bytes/full SHA,
   matching portable source/clean metadata and exactly one active Britannia mod.
   Keep the existing version 0.1.8d; a matching filename/version is insufficient.
4. Fully exit each affected client before replacing its Britannia jar. Back up
   its old jar outside active mods, install the same selected artifact, and
   verify FILE_MATCH. The local instance is UltimaCraft - Britannia, not a dev
   run/mods folder. This task's closed-process guard never copies into a running
   client. The new file can legitimately be named -all even though the old file
   lacked that classifier; origin/hash determine identity.
5. Start the server through its normal host procedure and retain its new startup
   inventory/origin and session time. Run the existing read-only `/grabby env`.
   Required output: version 0.1.8d; **SHA e64fe63c6ed0836f520e3d9b66ac7336b3e323677b63a433d65d48852c30fd70**;
   **commit cf49b1700d1a8992f39396f2ae531f0cb26502d7**, dirtyWorkingTree=false,
   built=2026-10-08T22:31:37.806305200Z and DedicatedServer. Confirm MC/NeoForge/
   Java pins too. The embedded task branch is legitimate; do not require its
   string to be patch-18. If bytes/source differ, stop acceptance and re-identify
   the loaded origin/duplicate files; do not invent another fence patch.
6. Launch the intended updated client profile and retain its new process/startup
   origin and selected full hash/source. Connect only when both sides actually
   load matching selected artifacts. A jar copied while a previous JVM runs is
   not a loaded replacement; copying files alone never passes runtime acceptance.
7. At fresh disposable coordinates, verify layout_code appears and run the
   south/X and east/Z A-before/B-add/ticks/B-remove sequence with both directions
   and north/west controls. Keep the same A selected, compare authoritative and
   client full states, and inspect rails/UVs/outline. Complete support/endpoint/
   ground, oblique, real corner/decorator, reconnect and F3+T observations from
   RUNTIME_ACCEPTANCE.md. Do not edit the owner's original production fence
   coordinates as a diagnostic shortcut.
8. Record actual post-restart identities and accepted or failing phases. Only
   matched affected-runtime acceptance can close OWNER_FENCE_RUNTIME_ACCEPTED.
   Source push and the independent automatic engine checks cannot close it.

Old displaced panels keep their currently stored layout; repair only by
intentional rotation/replacement after acceptance/backups. In-place downgrade is
not certified as migration rollback. Any rollback uses the preserved pre-upgrade
world/config/original matching software under a separate tested operator procedure.

Current release requirements stay explicit: fence corner/decorator/art/reload
and this reported interaction, reopened plaster/paired-wall loaded appearance/
  outline, and alligator playback remain. Medallion UI and sustained owner movement
stay approved. Timing is follow-up, not a newly invented source/rollout barrier.
No main/ruleset bypass, version bump, release tag or download publication is part
of this recovery. No production backup/replacement/restart was performed here.
