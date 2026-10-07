# Plaster corner implementation ledger

Base: exact `patch-18`, `7ca44a78d2438ae2204c4f64271ef84bb0672740`, refreshed 2026-10-06. Cached origin/patch-18 matches; no push/fetch performed. Version remains 0.1.8d, Minecraft 1.21.1, NeoForge 21.1.72, Java 21, GeckoLib 4.6.6. Tracked/index baseline clean; owner untracked playbooks and discovery reports preserved.

Contract: relocate the existing single pillar to the architectural edge junction, restore finished plaster at its old end, preserve seven-pixel collision and all state/texture/placement semantics. No second free-end post. Canonical candidate post bounds (-0.8,0,-0.1)..(6.7,32,2.9); actual client acceptance remains required.

- [x] M0 baseline/contract — refreshed source; original exact owner state reproduced in a fresh no-pack client; saved lower, upper and neighbors dumped.
- [x] M1 bounded derivation — four Python tests pass; scoped freshness/idempotence verified, derivative LF pinned.
- [x] M2 repair — authored corner/reflection repaired; actual half derivative generated; bounded resource diff reviewed.
- [ ] M3 validation — automated gates pass; original oracle fails three new assertions. Actual packaged owner/mirror/half/placement/persistence checks recorded; full client matrix remains open, with reproduced independent-half decorator risk and E/W normalization.
- [x] M4 local candidate preparation — integrated source ac6b2e80, clean detached task checkout, normal bundled build/artifactIdentity pass; 4,126 JUnit cases (23 skipped), zero failures/errors; all 1,362 required GameTests pass; actual bundled candidate smoke and saved-world comparison recorded. Release acceptance remains held by M3 and other hotfixes.
- [x] M5 release handoff — exact hash/identity, executed evidence, explicit remaining matrix, combined holds and rollback recorded. Publication/deployment not performed.

Final status: **IMPLEMENTED_PENDING_CLIENT_VALIDATION**. Candidate: **CANDIDATE_PREPARED_RELEASE_HELD**. These describe local preparation, not completion of every client acceptance gate.

Release holds: existing fence packet/art/interactive visuals, alligator client visuals, medallion motion/cape/swimming visuals remain. Plaster does not clear them. Publication/deployment/launcher/live-world changes are not authorized.
