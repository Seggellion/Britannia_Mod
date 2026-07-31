# Banner Dyeing Test Plan

## Automated release sequence

Run from a clean worktree:

```text
./gradlew test --tests '*Banner*' --tests '*Dye*'
./gradlew scaffoldBanners
./gradlew scaffoldBanners -PbannerScaffoldArgs="--check"
./gradlew clean test
./gradlew build
```

Run the closest dedicated-server validation task exposed by Gradle. If no automated GameTest runner exists,
record the structural server-safety suite and an attempted bounded `runServer` startup; do not claim a live
dedicated-server PASS without observed startup and shutdown evidence.

Required automated domains: 35-definition release lock; all four materials, four palettes, seven pigments and two
mounts; asset dimensions/mode/atlas; natural/dyed item state; all placement profiles/orientations/facings/mounts;
state/tub/placement codec round trips and malformed/future schema rejection; legacy-ID migration; preview/apply,
stale/replayed/cross-player sessions and unlimited tubs; save/update packet/pick/drop fidelity; missing-content
matrix; reload atomicity; cache/diagnostic bounds; packet budgets; no server-side client linkage or per-tick banner
ticker.

## Deterministic density fixtures

- Four copies of each released definition: 140 placed banners.
- Optional eight copies: 280 placed banners.
- Exercise every definition, supported orientation, all horizontal facings across the set, both mounts/materials
  across the QA matrix, codec save/reload, and a cross-chunk footprint.
- These service-level fixtures are deterministic regression evidence, not a substitute for live world rendering.

## Manual Gate F matrix

Before product-owner review, run two clients against a dedicated server and cover simultaneous different/same
pigments, different materials, one player changing hands, cross-player token attempts, stale confirmation,
cancel/apply in parallel, and unlimited tubs. Then verify all definition families in item, preview, placed natural
and dyed states; every supported orientation, N/S/E/W, brass/iron; save/reload, relog/late tracking, F3+T,
`/reload`, chunk boundary, support removal, break/drop, pick, and re-placement.

Gate F may be marked only `READY FOR PRODUCT-OWNER REVIEW` by automation. Only the product owner can record PASS.
