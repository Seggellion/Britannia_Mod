# Adding a Fabric Material

1. Reserve a stable `FabricMaterialId` and add a schema-1 JSON definition under
   `data/britannia_mod/fabric_materials/`.
2. Define its natural `ResolvedColourId` and one material-owned palette ID.
3. Add the matching palette under `material_palettes/`; its `material_id` must match, and its entries must include
   the natural colour.
4. Add display and command translation keys.
5. Verify natural creation, all seven current pigments, preview/apply/re-dye, item/placed consistency, reload, and
   persistence. A palette belongs to exactly one material.
6. Update `content/banner_release_contract.json` only when the material is explicitly approved for a future
   release.

Material IDs and resolved colour IDs are persisted authority. Renaming either requires an explicit migration.
Changing display RGB may change the appearance of existing saved colour IDs; document and visually approve it.
