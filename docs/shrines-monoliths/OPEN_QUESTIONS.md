# Shrine and Monolith Open Questions

No critical architecture, schema, security, saved-state, compatibility, or merge-package decision remains unresolved for conditional code review and a separately owner-authorized merge.

The missing live-validation evidence is not an unanswered owner decision. On 2026-08-04 the project owner approved proceeding to Milestone 10 without the prescribed two-authenticated-client Milestone 9 session, limited to final audit and conditional merge-readiness preparation. The unperformed checks are not complete and remain individually `UNVERIFIED` in `MERGE_READINESS.md`; the full matrix remains mandatory in `POST_MERGE_VALIDATION.md` before production promotion unless the owner issues a separate explicit release-risk waiver.

## Non-critical follow-up: final monolith content

- The owner approved exactly two provisional diagnostic variants: `monolith/diagnostic_missing_content` and `monolith/diagnostic_alternate`.
- Final monolith variant count, lore/display names, final geometry/textures, and any replacement/migration package remain unspecified.
- This does not block conditional review or merge because the exact provisional package is explicitly approved and honestly identified. It does block representing either diagnostic as final artwork.
- A future owner package should supply stable IDs and localized names, exact approved source/production paths, geometry bounds/pivot/default forward direction, texture dimensions/alpha/UV compatibility, and an explicit keep/disable/migrate decision for each diagnostic ID.

## Non-critical integration follow-up: banner branch

- A separate banner implementation exists on local/remote branch lines but is not present in the exact audited `patch-18` target.
- No banner path overlaps the current merge simulation because target `62df1dc97c5113a86f9c0f258cb90538f31efe89` has zero post-branch changes.
- If banner or any other integration work lands first, the target tip has changed and this merge-readiness audit must be refreshed. Do not infer compatibility from the current no-conflict result.

## Settled decisions

- Shrine dimensions, four-cell footprint, shared geometry, nine stable identities, and owner-approved provisional asset package are settled.
- Monolith dimensions, eighteen-cell footprint, exactly two current provisional variants, and the positive 16-voxel visual-only Y correction are settled.
- `SOLID_CELL`, cell-bounded shapes, no waterlogging, blocked piston reaction, and render/collision independence are settled.
- One anchor block entity owns each logical structure; parts carry only facing/local offset and do not render complete geometry.
- Persisted footprints remain authoritative and current definitions cannot resize saved structures.
- Interior Decorator cycling is logical-server authoritative, creative-or-permission-level-2, same-family only, and changes only `variant_id`.
- No shrine/monolith registry ID existed before this feature, so no prior-feature alias or migration is required.
- The Milestone 9 owner limitation decision permits conditional audit/review progression but establishes no authenticated-client, multiplayer, live-gameplay, visual, or performance result.
