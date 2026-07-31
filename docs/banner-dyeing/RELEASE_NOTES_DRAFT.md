# Banner Dyeing Release Notes — Draft

Status: release-candidate notes for product-owner review. This document does not change the project version,
publish an artifact, create a tag, or declare Gate F PASS.

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

Crafting remains product-disabled. Manual two-client dedicated-server Gate F review remains a product-owner action.
