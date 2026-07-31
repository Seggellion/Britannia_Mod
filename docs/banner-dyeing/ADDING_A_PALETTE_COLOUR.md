# Adding a Palette Colour

Palette colours are material-scoped persisted identities.

1. Reserve a stable `ResolvedColourId` in one material palette.
2. Add its display sRGB, tags, optional allow/exclude tags, and deterministic priority.
3. Do not duplicate IDs or move an existing colour ID between materials.
4. If a pigment should target it exactly, update that palette's `pigment_overrides`.
5. Test the resolver explanation, explicit/nearest/tie-break result, synchronized display data, item/preview/placed
   rendering, reload, and old saved states.

Never reinterpret an existing stable colour ID as a different semantic colour. Mapping changes affect future dye
operations only; display-RGB edits affect every saved state using that colour ID and therefore require visual
compatibility review.
