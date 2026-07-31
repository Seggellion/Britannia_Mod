# Gate F Live Evidence

## Decision

Gate F: **PASS**

Overall product-owner approval: **APPROVED**

Release blockers: **NONE**

Release performed: **NO**

## Candidate identity

- Branch: `banners-dyetub`
- Candidate commit: `4cd7aec6babc2fb856244238d74d5fcf393d3c4d`
- Candidate message: `chore(banners): prepare Gate F release candidate`
- Normal JAR: `Britannia_Mod-0.1.7k.jar`
- Normal JAR SHA-256:
  `7CD283BCC929A645D2E5A08B3B9DF520E23FF7873BE5650000BFAA1F15F66539`
- All-JAR: `Britannia_Mod-0.1.7k-all.jar`
- All-JAR SHA-256:
  `AA4DB58AAB9C59CE97BF3ABD395C03A1BF7379A549EDA1A55AE574572113F5D8`

Both artifact hashes were recalculated before this record was created and matched the reviewed release candidate.

## Review identity and environment

- Reviewer: Product Owner
- Review date: 2026-07-30
- Dedicated-server identity: product-owner test environment
- Client A identity: product-owner test client A
- Client B identity: product-owner test client B
- Minecraft: 1.21.1
- NeoForge: 21.1.72
- Java: 21
- Resource packs: not supplied
- Shaders: not supplied

The product owner confirmed that the two-client Gate F review was completed, every required check passed, no Gate F
evidence is missing, and the desired outcome is to close Gate F as passed.

## Distribution rights

The product owner confirms that the project has the necessary rights to distribute all banner artwork, base
textures, dye masks, geometry, and related supplied assets included in this release candidate.

## Candidate and dedicated server

| Check | Result |
|---|---|
| Dedicated server reaches ready state | PASS |
| Exact candidate commit tested | PASS |
| Exact normal JAR hash verified | PASS |
| Exact all-JAR hash verified | PASS |
| Java 21 | PASS |
| Minecraft 1.21.1 | PASS |
| NeoForge 21.1.72 | PASS |
| Britannia Mod loads successfully | PASS |
| 35 active definitions published | PASS |
| 35 complete definitions published | PASS |
| 0 validation errors | PASS |
| 0 validation warnings | PASS |
| No client-only server class-loading failure | PASS |

## Two-client connection and catalogue

| Check | Result |
|---|---|
| Two distinct clients connect | PASS |
| Both clients receive all 35 definitions | PASS |
| No partial display snapshot | PASS |
| Late client tracking | PASS |
| Late reconnect | PASS |
| No decode failure | PASS |
| No unexpected disconnect | PASS |

## Dye concurrency

| Check | Result |
|---|---|
| Different banners and different pigments | PASS |
| Same pigment and different materials | PASS |
| Material-specific palette resolution | PASS |
| Stale preview rejection | PASS |
| Cross-player token isolation | PASS |
| Cancel/apply overlap | PASS |
| Concurrent re-dye | PASS |
| No player-state cross-contamination | PASS |
| Unlimited dye tubs remain unchanged | PASS |
| Failed confirmation causes no partial mutation | PASS |

## Cross-client rendering

| Check | Result |
|---|---|
| Cross-client placed-banner consistency | PASS |
| Natural banner synchronization | PASS |
| Dyed banner synchronization | PASS |
| Brass mount synchronization | PASS |
| Iron mount synchronization | PASS |
| Parallel synchronization | PASS |
| Perpendicular synchronization where supported | PASS |
| Large parallel-only enforcement | PASS |
| Four-facing synchronization | PASS |
| Late-tracked banners render correctly | PASS |
| No duplicate rendering | PASS |
| Only anchor renders | PASS |
| Child parts remain invisible | PASS |
| No purple fallback | PASS |
| No missing model | PASS |
| No stale colour | PASS |

## Lifecycle

| Check | Result |
|---|---|
| Placement | PASS |
| Obstructed placement rejection | PASS |
| Incomplete-support rejection | PASS |
| Break anchor | PASS |
| Break child | PASS |
| Single-drop behaviour | PASS |
| No duplicate drop | PASS |
| No orphan parts | PASS |
| Support-loss cleanup | PASS |
| Pick block from anchor | PASS |
| Pick block from child | PASS |
| Re-placement | PASS |
| State preservation | PASS |

## Reload, restart, and reconnect

| Check | Result |
|---|---|
| Save/reload | PASS |
| Chunk unload/reload | PASS |
| Server restart | PASS |
| Resource reload | PASS |
| Data reload | PASS |
| Late reconnect | PASS |

## Visual release sampling

| Check | Result |
|---|---|
| Natural visual sampling | PASS |
| Selective recolour sampling | PASS |
| Fixed artwork remains unchanged | PASS |
| Highlights and shadows preserved | PASS |
| Brass mount sampling | PASS |
| Iron mount sampling | PASS |
| Orientation sampling | PASS |
| 128 x 128 fidelity | PASS |
| Alpha edges | PASS |
| No blur | PASS |
| No halo | PASS |
| No atlas fallback | PASS |
| No purple fallback | PASS |

The approved sampling covers the representative extra-small, small, perpendicular-medium, parallel-medium, and
parallel-large architecture branches. The previous family Gate E records remain the full-catalogue visual evidence.

## Diagnostics

| Check | Result |
|---|---|
| No repeated diagnostic spam | PASS |
| No server exceptions | PASS |
| No client exceptions | PASS |
| No unexpected migration spam | PASS |
| No orphan warnings | PASS |
| No duplicate-drop warnings | PASS |

No dedicated-server or client log artifact from the reviewed session was supplied to Codex in the current
environment. The product owner explicitly confirmed that the live review passed and that nothing is missing from
the Gate F decision. No raw-log finding is inferred or invented in this record.

## Qualitative performance smoke

| Check | Result |
|---|---|
| Server responsiveness | PASS |
| Placement responsiveness | PASS |
| Preview responsiveness | PASS |
| Apply responsiveness | PASS |
| Chunk-load responsiveness | PASS |
| Dense-area rendering smoke | PASS |
| Reload responsiveness | PASS |
| Reconnect responsiveness | PASS |

These are qualitative product-owner smoke-test approvals. No FPS, TPS, latency, or timing measurement is claimed.

## Known limitations

- Known limitations reviewed: PASS
- Known limitations accepted: PASS
- Banner crafting and pattern content remain product-disabled.
- No optional deferred banner feature was added as part of this closeout.

## Closeout regression validation

| Check | Result |
|---|---|
| Banner scaffold `--check` | PASS — 35 manifest entries, 35 definitions, 35 active, 0 disabled/provisional |
| Focused banner/dye tests | PASS — 689 tests / 70 suites, 0 failures/errors/skips |
| Standalone clean | PASS |
| Clean full tests | PASS — 695 tests / 71 suites, 0 failures/errors/skips |
| Production build | PASS |
| Runtime-content integrity audit | PASS |

The documentation-only closeout does not package any of the changed files. The rebuild retained the reviewed
artifact sizes and entry counts and passed the same semantic audit: zero duplicate entries, 35 complete definitions,
70 final textures, 25 geometry models, required index/atlas resources, and no banner recipes, patterns, legacy asset
keys, intake YAML, or Gate E Markdown.

The rebuilt archives have different byte hashes because the clean build regenerated ZIP entry timestamps and other
archive metadata:

- Rebuilt normal JAR SHA-256:
  `0F0D37F35EA01B1C59732773B0322CDC32776E4180B252F69E923417593851FE`
- Rebuilt all-JAR SHA-256:
  `08960E1E8B5EACDB2AC98A9F0FEE902D84F487A8F308DD0DD64169837C84C00E`

These rebuilt hashes are not reviewed release-candidate hashes and do not replace the product-owner-approved hashes
recorded above. No rebuilt artifact was published.

## Overall approval

- Two-client Gate F review completed: YES
- Every required Gate F check passed: YES
- Desired Gate F outcome: CLOSE AS PASSED
- Missing Gate F evidence: NONE
- Distribution rights for included banner assets: CONFIRMED
- Release blockers: NONE
- Overall product-owner approval: APPROVED

Gate F: **PASS**
