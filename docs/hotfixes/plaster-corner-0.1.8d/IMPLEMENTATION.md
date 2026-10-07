# Plaster support corner repair

The false-branch support corner now has one full-height timber pillar at the architectural junction. The old pillar's segment is finished plaster. The canonical timber bounds are (-0.8,0,-0.1)..(6.7,32,2.9); the reflected authored model preserves face swaps, UV handedness and grain rotation. The existing half derivative ends at Y16 with explicit caps. Full lower art still spans Y0..32 and upper selectors remain air.

Application scope is three existing model files. No Java gameplay code, selectors, property domains, registry IDs, textures or other family models change. Trim/plaster cuts eliminate internal faces around the relocated pillar and restore the vacated branch through z16. Collision and selection remain the existing seven-pixel strips.

Use only the bounded workflow:

```powershell
python -B tools/generate_half_walls.py --support-corner-only
python -B tools/generate_half_walls.py --support-corner-only --check
python -B tools/plaster-corner/test_generation.py
```

The scoped command validates canonical geometry and the actual four false-branch half selectors before writing one existing derivative. It checks canonical output bytes for freshness. Its file has an LF Git attribute so clean Windows checkouts retain those bytes. Tests use temporary fixtures to prove idempotence, only-one-output writes, stale detection, and no writes on selector/cap/scope failures. Unscoped generator behavior is unchanged; the retained broad baseline check fails on unrelated support straight trim. Do not run broad junction or blockstate generation.

Evidence is under `implementation/`: retained original three-file oracle; baseline/corrected geometry and resource reports; exact warning delta; four generation tests; red and green JUnit XML and logs. The independent five-test regression fails on old pillar, missing old-end plaster and old half pillar, while reflection/state controls pass. Corrected regression plus four existing suites execute 23 tests with no failures. It independently covers all eight affected rotations/mirrors, both vertical sections, branch plaster probes, UV reflection, half caps/references, all 96 full mappings and collision/selection unions.

The original fresh client loaded pre-repair compiled resources, reproduced the exact owner's south/false/unmirrored lower state, and saved baseline screenshots and 26-case state/neighbor dumps. Its lower and upper owner halves match. E/W command scenes normalized to N/S on notifications, as expected from existing connection code; representative straight/T requests with two corner arms also normalized to corners. These normalized cases are not passed as E/W or straight/T visual acceptance. Client acceptance status is explicit in RELEASE_HANDOFF.md.

The task runner uses existing build property names and disposable run directories only, strips service credential environment prefixes without displaying values, and rejects credential-bearing properties files. The packaged smoke init removes compiled project class/resource outputs and MOD_CLASSES, and relies on the candidate's bundled dependencies. A successful development launch alone does not prove packaged smoke.

Correctly saved corner states need only updated resources/restart, without migration or re-placement. State normalization, paired-half/decorator behavior and shader-pack acceptance remain separate runtime checks; this asset correction does not change shared placement behavior.

Final clean build, packaged screenshots, persistence comparison and acceptance disposition are recorded in RELEASE_HANDOFF.md and CLIENT_ACCEPTANCE.md. The resumed scoped render matrix is complete, but plaster acceptance remains blocked by proven pre-existing wall-pair collision and mirrored topology defects. MIRROR_MISMATCH_DIAGNOSIS.md and WALL_PAIR_FOLLOWUP_PROPOSAL.md record evidence and a bounded proposal without changing shared Java. Sustained player walking/jumping and any owner resource overrides remain unverified. Evidence text copies trim trailing whitespace; original full logs remain in the task tmp directory. The corrected smoke script filters the SDK's legacy classpath copy so its standalone nanohttpd cannot duplicate the bundled dependency; it now supports a disposable dedicated restart too. This SDK configuration and verification tooling do not change candidate bytes or require a new application build.
