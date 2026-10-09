# Fence runtime acceptance - 2026-10-09

Status: **LOCAL_RUNTIME_PENDING_ACCEPTANCE**. The affected remote's old loaded
identity is now verified by owner-provided /grabby env; REMOTE_IDENTITY_PENDING
no longer applies. **OWNER_FENCE_RUNTIME_ACCEPTED is not established.**
Source/integration and runtime installation are distinct.

## Current identities

| Runtime/file | Identity and result |
| --- | --- |
| Selected current combined jar | cf49b1700d1a8992f39396f2ae531f0cb26502d7; e64fe63c6ed0836f520e3d9b66ac7336b3e323677b63a433d65d48852c30fd70; 36,142,686 bytes; clean 0.1.8d |
| Recovered actual CurseForge files | Old PID47684 exited before replacement; exactly one active -all jar, e64fe63c6ed0836f520e3d9b66ac7336b3e323677b63a433d65d48852c30fd70 / cf49b1700d1a8992f39396f2ae531f0cb26502d7; FILE_MATCH. New client session/load unverified. |
| Affected remote DedicatedServer | Owner's actual /grabby env confirms exactly the same old full hash/source/36,107,339 bytes, not an inference from the client |
| Disposable DedicatedServer | 127.0.0.1:25583, copied acceptance world; actual loaded e64fe63c6ed0836f520e3d9b66ac7336b3e323677b63a433d65d48852c30fd70 / cf49b1700d1a8992f39396f2ae531f0cb26502d7 / dirty=false |

See RUNTIME_IDENTITY_MATRIX.json, portable SELECTED_ARTIFACT.json and the
owner-supplied three lines. Remote origin directory and fresh live states were
not supplied; no production block was edited. The known compiled contents of
that exact old artifact include the old normalizer/no selector. This establishes
both sides were old; it does not prove a later installed/restarted session.

## New executed checks

- Read-only preflight: 12 meaningful tests passed, including same-version wrong
  bytes/source/hash, renamed duplicate active jars, disabled .old, thin/dirty jar,
  wrong instance, absent jar, abbreviated hash and file-versus-loaded distinction.
  Real initial profile emitted FILE_MISMATCH; recovered profile now emits FILE_MATCH.
  Both filesystem checks correctly emit LOADED_RUNTIME_UNVERIFIED.
- Original old client jar is copied and full-hash verified outside active mods at
  `.britannia-recovery-backups/2026-10-09-e64fe63c/britannia_mod-0.1.8d.jar`.
  This backup can be made without modifying the running installation.
- Replacement was guarded and stopped before modifying installed files because
  the actual client remained running. On the owner's subsequent normal quit,
  sanitized process inventory and the installer's repeated guard verified exit.
  Only Britannia was replaced with the selected jar; all **10 other mod files**
  hash unchanged. Original backup and retired active old jar remain outside mods.
  No token/full launch argument was printed. Settings/worlds and production are unchanged.
- Exact selected jar launched through the existing packaged-server SDK recipe:
  project build roots and separate bundled dependencies excluded, MOD_CLASSES
  cleared, actual loaded origin/environment verified. Only a copied disposable
  world and test-only datapacks are used. No production credentials are copied;
  baseline missing-backend warnings do not establish backend parity.
- Four **command-created**, same-coordinate owners A: north=320,80,204;
  east=324,80,204; south=328,80,204; west=332,80,204. B extends X for north/south,
  Z for east/west. All six actual state properties match at isolated, after-B
  immediate, five-tick settled, immediate removal and five-tick removed-settled:
  **20 successful full-state assertions**. Flags change with contacts; facing and
  layout_code=0 retain the same A. Four exact saved states also match after
  orderly dedicated save/exit. A new dedicated JVM then loaded the exact selected
  hash/source and verified the four saved owners unchanged (four conditional
  full-state matches; see DEDICATED_RESTART_TRACE.txt). This is **zero registered GameTests**, and is
  not BlockItem placement, mouse/packet, collision probing or rendered acceptance.
- Historical Oct 8 evidence remains: exact old artifact fails 24/48 constructed
  cases; corrected combined passes 48 and 64 legacy conversions; final exact
  e64 jar restarts 112 saved fixtures. Prior 4,129 JUnit/24 focused GameTests are
  historical totals, not new runs. No broad suite or candidate rebuild is needed
  for these Python tools/docs/test-only fixture changes.

## Actual mouse and visual checks still required

The computer-use skill uses node_repl + @oai/sky. Initialization after reset
and one retry failed with `windows sandbox failed: helper_unknown_error: setup
refresh had errors` before any window/input operation. A scripted state probe
or prepared camera is not a screenshot, raycast or actual-click pass.

The actual client is now closed/recovered and FILE_MATCH is recorded. A fresh
Windows helper initialization and one retry again failed before any app/window
selection or input. No client launch was performed. Launch the intended profile
and retain new startup origin/session/full hash.
Connect to **127.0.0.1:25583**, not the old remote server. Confirm both newly loaded
hashes/source and a fresh panel's layout_code. Do not certify a live production
connection merely because files now match.

The portable datapack under fixture/runtime-recovery prepares **48 manual
scenes** (four facings x endpoint/support/ground x both directions x cardinal/
oblique). None is executed acceptance. Camera hints require actual F3 owner/hit
calibration. The manifest permanently identifies A and B for every case.

Start with case 24 (south/X plus endpoint) and case 12 (east/Z plus endpoint),
then north/west controls and the support/ground/reverse/oblique variants:

1. `/function fence_recovery:case_24_start`: select slot 1 and place **fresh A**
   at the manifest position. Record full client and authoritative A states,
   visible model, rails/UVs, targeting outline and footprint. This setup places
   no fence; A is not pre-created by a command.
2. `/function fence_recovery:case_24_extend`: only positions the camera/prepares
   a support where required; inspect the actual target and place B. Keep A's
   coordinates selected for immediate and settled before/after records.
3. `/function fence_recovery:case_24_verify`: conditional full-state markers
   corroborate A/B; capture actual appearance/outline separately. Remove B with
   a normal click and repeat A verification. The same coordinate is mandatory.
4. Repeat case 12, then cases 00/36 and remaining manifest variants. Record every
   phase/time/hit/held item/owner and client versus authoritative result. Do not
   substitute B or different owners for an A transition.
5. True sequential corner: existing `/function closeout_probe:fence` uses arms
   229,80,27 and 230,80,28 with gap 230,80,27. Fresh gap placement expects code12;
   slot2 decorator rotates the whole corner to facing=east/code9. Remove an arm,
   reconnect and F3+T; inspect original/reflected controls using fence_view.
6. Keep saved/decoded legacy samples separate from fresh placements. Existing
   displaced blocks retain their saved footprint; only deliberate rotation or
   replacement repairs them. No world-wide repair or safe downgrade is claimed.

All new actual-client state/screenshots, immediate packet timing, support/
endpoint/ground clicks, corner/decorator, rails/UVs/outline and client reconnect/
resource reload are pending. Medallion UI/sustained movement remain approved.
Reopened plaster visual/paired-wall appearance and alligator playback remain;
timing repetitions stay follow-up. See PRODUCTION_RECOVERY_PACKET.md.

## Manual continuation prepared after installation

[Manual checklist](MANUAL_RECOVERY_CHECKLIST.md) uses the recovered real profile
and matching localhost server, rather than the historical comparison profile
or port25569. Seggellion test-server operator access was explicitly approved
after an automatic-review rejection and then granted; production permissions
are unchanged. Existing reopened_plaster setup and four exterior viewpoints
are enabled on the copied server world after successful reload. Fixture reload
is preparation only; no new mouse/render/plaster/alligator pass is claimed.
The prior 12 tool tests, 20 command phases and four restart assertions were not
rerun or counted as new checks during this installation continuation.
