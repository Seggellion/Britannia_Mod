# Adding a Banner

This is a release-controlled workflow. A definition does not become `complete` until its exact assets and runtime
behavior have passed product-owner Gate E review.

1. Reserve a stable namespaced ID. Never reuse a removed ID for different art.
2. Add an intake under `content/banner-final-intake/submissions/<id>/` and record source identity, display name,
   dimensions (1-3 wide, 1-2 high), supported orientations, brass/iron mounts, default material/mount, placement
   profile, geometry, hashes, and two 128 x 128 RGBA textures.
3. The base texture must be the complete natural appearance. The dye mask must select only dyeable cloth; fixed
   heraldry, hardware, highlights, and shadows remain in the base.
4. Add/update `content/banner_catalogue.yml` without reordering existing indices.
5. Run `./gradlew scaffoldBanners`, then `./gradlew scaffoldBanners
   -PbannerScaffoldArgs="--check"`. Generated definition JSON, client asset index, language keys, and asset
   references must be byte-stable on the check run.
6. Run focused catalogue, registry, asset, render, placement, persistence, reload, and preview tests.
7. Perform the family Gate E live procedure for natural/dyed item, preview, placed rendering, every supported
   orientation/facing/mount, save/reload, initial/late tracking, F3+T, `/reload`, break/drop, support loss, pick,
   and replacement. Bind approval to the exact commit and SHA-256 asset hashes.
8. Change status to `complete` only after Gate E PASS, then rerun the full banner suite, full tests, build, and
   release artifact audit.

Crafting recipes and banner-pattern content are intentionally out of scope and must not be added without a new
product decision. Placeholder art, provisional dimensions, missing hashes, provisional IDs, and `in_progress`
status block release.
