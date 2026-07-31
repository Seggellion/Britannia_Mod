# Banner Dyeing Release Notes — Draft

Status: Gate F approved release-candidate notes. Gate F passed on 2026-07-30; this document does not change the
project version, publish an artifact, create a tag, or perform a release.

## Highlights

- 35 approved banner definitions across extra-small, small, perpendicular medium, parallel medium, and parallel
  large families.
- Four fabric materials, seven pigments, and brass/iron mounts.
- Natural and authored-mask dye rendering in inventory, hands, dropped items, frames, preview, and placed banners.
- Server-authoritative one-use dye previews with cancel safety, revalidation, replay protection, and multiplayer
  isolation.
- Multi-cell, orientation-aware placed structures with one anchor block entity, exact drop/pick state, chunk-load
  integrity repair, and no per-tick banner ticker.
- Atomic data/resource reload publication and bounded client caches/diagnostics.

## Compatibility hardening

- All 14 historically operator-obtainable provisional IDs now migrate through one canonical alias table, including
  `end_01` and `end_02`.
- Migration preserves material, resolved colour, pigment provenance, and mount.
- Missing colour repair no longer guesses a natural colour when provenance is absent or unavailable.
- Packet strings, counts, payload regressions, replay tombstones, and diagnostics have explicit bounds.
- Schema, reload-cycle, missing-content, colour-evolution, dense-placement, cross-chunk, and dedicated-server
  structural regressions are covered.

## Gate F approval

- Product-owner dedicated-server and two-client review completed against candidate commit
  `4cd7aec6babc2fb856244238d74d5fcf393d3c4d`.
- Normal JAR SHA-256:
  `7CD283BCC929A645D2E5A08B3B9DF520E23FF7873BE5650000BFAA1F15F66539`.
- All-JAR SHA-256:
  `AA4DB58AAB9C59CE97BF3ABD395C03A1BF7379A549EDA1A55AE574572113F5D8`.
- Dedicated-server readiness, two-client concurrency and synchronization, lifecycle, reload/restart,
  representative visual sampling, diagnostics, and qualitative performance smoke passed.
- The product owner confirmed distribution rights for all included banner artwork, textures, masks, geometry, and
  related supplied assets.
- Known limitations were accepted, overall approval is `APPROVED`, and release blockers are `NONE`.

Crafting remains product-disabled. No merge, push, tag, version change, publication, or release has been performed.
