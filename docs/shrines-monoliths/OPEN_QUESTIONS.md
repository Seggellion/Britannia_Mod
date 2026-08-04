# Shrine and Monolith Open Questions

## Post-Milestone 9A owner recheck remains open

- The owner must visually confirm that all four texture quadrants form one correctly oriented symbol
  with no row wrap or seam displacement, that the inner surface reaches 14 model voxels and the
  supplied black granite exterior reaches 15, and that the shrine stays centered inside an
  eight-oak-stair frame for North, East, South, and West with no penetration, empty side, thin stripe,
  overlap, or z-fighting artifact. Parsed geometry and envelope tests are not GPU-render evidence.
- The owner must confirm that the existing Britannia Decorative & Graveyard Creative tab now shows
  exactly one `britannia_mod:shrine` entry and one `britannia_mod:monolith` entry, that they place
  `shrine/honesty` and `monolith/diagnostic_missing_content`, and that no anchor or part item is exposed.
- A remaining live GPU concern is the seam between the virtue surface and separate granite material
  pass, plus the shrine/stair boundary. The
  corrected one-model-voxel horizontal inset and non-intersecting envelope remove the proven
  perimeter-cell overlap mathematically, but only the prescribed owner recheck can close the visual
  result.
- Milestone 10 remains suspended until that evidence is supplied and separately accepted.

No critical architecture, schema, security, saved-state, or compatibility decision is otherwise unresolved.

The missing Milestone 9 multiplayer evidence is not an unanswered owner decision. On 2026-08-04 the project owner approved proceeding without the prescribed two-authenticated-client session. The later owner geometry and Creative-tab correction supersedes the prior visual and Creative recheck wording above without claiming live GPU evidence.

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
