# Plaster corner implementation ledger

Base: exact `patch-18`, `7ca44a78d2438ae2204c4f64271ef84bb0672740`, refreshed 2026-10-06. Cached origin/patch-18 matches; no push/fetch performed. Version remains 0.1.8d, Minecraft 1.21.1, NeoForge 21.1.72, Java 21, GeckoLib 4.6.6. Tracked/index baseline clean; owner untracked playbooks and discovery reports preserved.

Contract: relocate the existing single pillar to the architectural edge junction, restore finished plaster at its old end, preserve seven-pixel collision and all state/texture/placement semantics. No second free-end post. Canonical candidate post bounds (-0.8,0,-0.1)..(6.7,32,2.9); actual client acceptance remains required.

- [x] M0 baseline/contract — refreshed source; original exact owner state reproduced in a fresh no-pack client; saved lower, upper and neighbors dumped.
- [x] M1 bounded derivation — four Python tests pass; scoped freshness/idempotence verified, derivative LF pinned.
- [x] M2 repair — authored corner/reflection repaired; actual half derivative generated; bounded resource diff reviewed.
- [ ] M3 validation — 23 targeted JUnit tests pass; original oracle fails three new geometry assertions; resource errors zero, warnings 88 to 73 with no new identities. Client matrix remains open.
- [ ] M4 clean local candidate — clean source integration/build/GameTests/packaged smoke in progress.
- [ ] M5 release handoff — prepared draft, final identity/evidence pending.

Release holds: existing fence packet/art/interactive visuals, alligator client visuals, medallion motion/cape/swimming visuals remain. Plaster does not clear them. Publication/deployment/launcher/live-world changes are not authorized.
