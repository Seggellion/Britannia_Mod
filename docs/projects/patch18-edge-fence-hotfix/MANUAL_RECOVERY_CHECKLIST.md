# Recovered-client acceptance — 2026-10-09

Installed recovery is complete; new loaded client identity and all observations
below are pending. The Windows helper failed during initialization before input.
Do not replace these observations with the completed command/state checks.

Launch CurseForge profile **UltimaCraft - Britannia**, player **Seggellion**.
Use Multiplayer → Direct Connection → **127.0.0.1:25583**. This is the retained
localhost-only copied-world server, creative/offline, MC **1.21.1**, NeoForge
**21.1.72**, Java **21**. Test-server operator access was explicitly approved
and granted for Seggellion; production permissions and installation are unchanged.
Do not connect the recovered client to the old production server for acceptance.

1. **Loaded identity:** retain the recovered client's new startup/session and
   Britannia mod origin from its `logs/latest.log`. Its installed file is
   `mods/britannia_mod-0.1.8d-all.jar`. If the startup environment report prints
   artifact/build, verify those too. In the test server run `/grabby env`:
   SHA **e64fe63c6ed0836f520e3d9b66ac7336b3e323677b63a433d65d48852c30fd70**,
   clean source **cf49b1700d1a8992f39396f2ae531f0cb26502d7**. That server command
   alone does not certify the client's JVM. Fresh panels must expose layout_code.
2. **Same owner before/after:** run `/function closeout_probe:prepare` once,
   then `/function fence_recovery:case_24_start`. Slot 1: place fresh south-facing
   A at **240,80,218**, using F3 to calibrate the floor target. Record A's full
   state and visible rails/UVs/outline. Run `fence_recovery:case_24_extend`,
   inspect the exposed east endpoint and click to place B at **241,80,218**.
   Keep **A** selected; compare immediately and after ticks. Run
   `fence_recovery:case_24_verify`; remove **B** by a normal click and verify A
   again. A must keep facing, layout and physical footprint while contact flags
   change. Repeat `case_12_start/extend/verify` for fresh east-facing
   **A=264,80,206**, **B=264,80,207**. Add north/west controls (cases 00/36).
   Remaining support/ground, reverse and oblique variants are in the 48-case
   manifest; cameras are hints, not validated raycasts.
3. **Corner/decorator:** `/function closeout_probe:fence`. Slot 1: place the
   fresh gap at **230,80,27**, expecting north/layout_code=12. Slot 2: one
   decorator click rotates the whole corner to east/code9. Use `fence_view` to
   inspect both panels, rails/UVs/outline and the reflected control at 233,80,27.
   Record the corner before/after removing and reconnecting an arm too.
4. **Paired wall/lifecycle:** `/function closeout_probe:wall`. Empty slot 1,
   decorator offhand: edit upper **220,81,60** once. Both halves must become
   east/corner/branch_right=false/mirrored=true. `closeout_probe:verify` checks
   fence/wall states only. Disconnect/rejoin **127.0.0.1:25583**, press **F3+T**,
   then inspect the same fresh A owners and edited corner/wall using `fence_view`
   and `wall_view`. Preserve state, appearance and targeting outlines.
5. **Plaster exterior:** `/function reopened_plaster:setup`, then
   `reopened_plaster:exterior_a_lower`, `exterior_a_upper`, `exterior_b_lower`,
   `exterior_b_upper`. Inspect real supports A=8,80,8 and B=20,80,8. Junction
   timber must span both visible heights without a gap in **both branches**;
   preserve interior/style. Y84 displays are forced art, not acceptance.
6. **Alligator:** `/function closeout_probe:alligators`. Watch eight ten-second
   phases (1,600 ticks): dry walk/aim, grounded wading, stopped surface,
   stopped submerged, AI dive, ascent, bank exit, land reset/aim. Check gait,
   bind pose/reset and clipping; there are no separate swim/idle/attack clips.
   The sequence returns creative. No repeated medallion or movement acceptance.

Record client versus authoritative A states and each observation separately.
Missing conditional markers, wrong hit/owner or visible problems are findings,
not passes. Report fence/wall/plaster A+B/alligator: pass or failing phase.
Normal setup/camera functions and completed automated evidence do not certify
mouse/packet, outline, UV or GPU appearance. Production rollout remains prohibited.
