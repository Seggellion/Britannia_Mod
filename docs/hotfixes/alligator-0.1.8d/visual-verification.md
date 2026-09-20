## Current combined closeout — 2026-10-07

The alligator implementation remains unchanged in the final clean `986ed746`/`f02d2876…482bd` candidate. Existing movement/survival/performance tests and the full 1,366-required-GameTest gate pass. Actual animation remains **PENDING_HUMAN_PLAYBACK**; state/physics tests do not establish visible gait, stopped water/idle bind pose, transition reset or aim. A disposable localhost 1,600-tick (80-second nominal) sequence is prepared; its forced stopped controls are distinguished from actual AI travel. Fixture preparation/preflight is not visual acceptance. See [single manual session](../combined-0.1.8d/MANUAL_SESSION.md) and [current handoff](../combined-0.1.8d/RELEASE_HANDOFF.md). Medallion UI and owner sustained movement are approved and must not be repeated. Source publication is independently authorized; no deployment follows.

---

# Remaining client visual gate

NOT_RUN: this session exposes no native Minecraft UI/playback capture. Asset-key checking and dedicated-server loading pass, but do not certify visible animation. Existing medallion sustained motion/cape/swimming visual acceptance remains a separate release hold.

Executable disposable client launch (Java 21; no production credential inheritance):

```powershell
$env:JAVA_HOME='C:/Program Files/Microsoft/jdk-21.0.8.9-hotspot'
& tools/alligator/run-isolated.ps1 -GradleTasks @('runClient') -RunDirectory tmp/alligator-0.1.8d/client-visual
```

The client override leaves normal run worlds unchanged, enables GameTests only for this scoped client, and starts in a task-owned directory. Create a new creative superflat world there with cheats. Do not join a live server. `/gametest run verticaldescent` and `/gametest run bankexit` provide the retained fixtures, although successful tests remove their entities. For sustained observation, build a source-water pond with floor at Y=64, interior X/Z=1..14/Y=65..72 and glass perimeter X/Z=0/15; summon `britannia_mod:alligator` at 6,72.3,6 with `{PersistenceRequired:1b}`. Observe ≥1,200 ticks. Use a nearby supported stone bank for transitions and a separate dry platform for land stroll. A controlled survival player may exercise attack aim; return to creative for safe observation.

Record pass/fail and captured scene for: dry/wading walk gait; still surface pose; downward/upward motion; submerged travel/hold; water-to-land reset; attack response and aim. The deliberate interim water/idle presentation is stopped/reset bind pose because the shipped asset contains only walk. There is no swim/idle/attack clip or new art. Damage timing is server-side. Check that a preceding land walk does not remain looping in idle/water.

Clear the Alligator visual gate only after actual playback is observed. Clear the medallion gate through its existing Patch 18 acceptance checklist; this hotfix does not supersede that hold. No deployment/publication follows automatically.
