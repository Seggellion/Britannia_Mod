# Shrine and Monolith Open Questions

## Flush-envelope owner recheck

- Owner screenshots identified the former 30 by 30 model-voxel presentation's exact one-voxel gap
  against adjacent stairs. The correction expands the complete exterior to 32 by 32 model voxels,
  exactly matching the horizontal four-cell footprint boundary while preserving center, UV mapping,
  14/15-voxel heights, and the two-voxel granite rim.
- Owner recheck should confirm no visible gap on all four sides/facings and no new overlap,
  z-fighting, texture seam, or entry into adjacent stair cells. Deterministic AABB tests prove exact
  boundary equality and zero volumetric intersection but are not GPU evidence.

## Chaos light-granite owner recheck

- The requested resource rule is explicit: `shrine/chaos` uses
  `textures/block/shrine/light_granite.png`; the other eight shrine variants use
  `textures/block/shrine/granite.png`.
- The supplied `light_granite.png` is a distinct path but currently byte-identical to
  `granite.png` (128 by 128, 13,635 bytes, SHA-256
  `A1D3C1A881B6DC6990EB56932B702CDA78AE0BBF10355FDA90B8A3133B4CCA77`). A visible difference
  is therefore not expected until the owner supplies distinct approved content at the light path.
- After distinct bytes are supplied, live cycling should confirm only Chaos changes rim appearance,
  all eight other variants retain the original granite, and the surface/rim seam remains clean.

## No unresolved critical owner decision

On 2026-08-04 the owner accepted the corrected shrine result with `Excellent. Commit. Move to next
step in this project.` That decision approves the committed UV-row and 14/15-voxel profile correction
at `b26165350227e6d46cfde3af91ff738a42e48c11` and authorizes the refreshed Milestone 10 audit. It
does not convert checks that were not demonstrated into passing evidence.

The following remain explicit release-validation items rather than unanswered design decisions:

- North/East/South/West GPU review of the complete symbol, virtue/rim-material seam, stair boundary, and
  absence of a gap, penetration, empty sides, stripes, overlap, or z-fighting.
- Representative variant cycling, collision, save/reload, and exact Creative-tab contents in a live
  game, including the absence of anchor and part items.
- The two-authenticated-client and dependent live matrix already waived for conditional audit by the
  owner. Every unperformed item remains `UNVERIFIED` in `POST_MERGE_VALIDATION.md`.

Headless geometry, registered-state, package, and server-startup evidence supports conditional review
but is not substituted for those live checks. There is no unresolved critical architecture, schema,
security, saved-state, compatibility, or content-selection decision. Milestone 10 is authorized to
proceed conditionally; production promotion remains gated by the post-merge checklist unless the owner
issues a separate explicit release-risk waiver.

## Non-critical follow-up: final monolith content

- The owner approved exactly three provisional diagnostic variants:
  `monolith/diagnostic_missing_content`, `monolith/diagnostic_alternate`, and
  `monolith/diagnostic_crystalline`.
- Final monolith variant count, lore/display names, final geometry/textures, and any replacement/migration package remain unspecified.
- This does not block conditional review or merge because the exact provisional package is explicitly approved and honestly identified. It does block representing either diagnostic as final artwork.
- A future owner package should supply stable IDs and localized names, exact approved source/production paths, geometry bounds/pivot/default forward direction, texture dimensions/alpha/UV compatibility, and an explicit keep/disable/migrate decision for each diagnostic ID.

## Non-critical integration follow-up: banner branch

- A separate banner implementation exists on local/remote branch lines but is not present in the exact audited `patch-18` target.
- No banner path overlaps the current merge simulation because target `62df1dc97c5113a86f9c0f258cb90538f31efe89` has zero post-branch changes.
- If banner or any other integration work lands first, the target tip has changed and this merge-readiness audit must be refreshed. Do not infer compatibility from the current no-conflict result.

## Settled decisions

- Shrine dimensions, four-cell footprint, shared geometry, nine stable identities, and owner-approved provisional asset package are settled.
- Monolith dimensions, eighteen-cell footprint, exactly three current provisional variants, and the positive 16-voxel visual-only Y correction are settled.
- `SOLID_CELL`, cell-bounded shapes, no waterlogging, blocked piston reaction, and render/collision independence are settled.
- One anchor block entity owns each logical structure; parts carry only facing/local offset and do not render complete geometry.
- Persisted footprints remain authoritative and current definitions cannot resize saved structures.
- Interior Decorator cycling is logical-server authoritative, creative-or-permission-level-2, same-family only, and changes only `variant_id`.
- No shrine/monolith registry ID existed before this feature, so no prior-feature alias or migration is required.
- The Milestone 9 owner limitation decision permits conditional audit/review progression but establishes no authenticated-client, multiplayer, live-gameplay, visual, or performance result.
