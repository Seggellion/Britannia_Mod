# Blacksmithing audit

## Reused implementation

- `BlacksmithyScreen` is the existing client screen and remains the only Blacksmithing interface.
- `CraftableRegistry`/`CraftableDef` remain the canonical recipe catalogue. The former 190 hard-coded
  rows have been migrated to `data/britannia_mod/blacksmithing/craftables.json`.
- `BlacksmithCrafting` remains the single server crafting/repair/recycling service.
- `CraftBlacksmithItemC2SPayload` remains the single action payload; it now carries an action enum,
  stable recipe ID, and target identity token.
- `LocalRecipes` remains the local recipe service and now persists learned Blacksmithing keys.
- `UOMetalToolMaterial`, `BlacksmithItemData`, `SkillManager`, `WeaponRegistry`, the armor/shield
  registries, and cached Bootstrap regions remain the material, metadata, skill, combat-profile, and
  origin integrations.
- the existing anvil interaction opens the screen. A hammer opens crafting; supported main-hand
  equipment opens Repair/Smelt. The off-hand remains exclusively the active metal source.

No second menu, recipe registry, material system, skill system, region system, or network channel was
introduced.

## Catalogue findings

The supplied checklist contains exactly 202 unique recipes with the requested distribution: Armor 34,
Axes 15, Bashing 17, Bladed 69, Cannons 4, Helmets 17, Miscellaneous 12, Polearms 16, Shields 15, and
Throwing 3. The previous catalogue had 190 rows, six non-canonical cannon rows, incomplete versioned
Gargish armor, and missing Wyrmscale, Shield Orb, rare armor, and siege entries.

All compound skills, secondary ingredients, retained-colour flags, maker eligibility, learned recipe
keys, batch-ammunition flags, and explicit Gargoyle/Female restrictions are now stored in the data file.
Six prior cannon IDs remain registered and resolve through aliases for compatibility.

## Repair and Smelt audit

No repair or recycling implementation existed. The chosen target pattern is the permitted main-hand
approach because the project has no Blacksmith container/menu or safe server-owned input-slot convention.
It avoids a client-only ghost slot and item-return/loss paths.

Repair and Smelt require a resolvable catalogue recipe, supported equipment type, stored material, an
identity-matching main-hand stack, and a nearby anvil. Repair additionally requires damage, skill, and
matching off-hand ingots. Both mutate inventory only after validation.

Items without an ingot primary ingredient cannot be safely repaired or smelted by this metal workflow.
This includes Dragon Scale-only equipment and Gloves of Feudal Grip. Components, deeds, cannon items,
and miscellaneous outputs are intentionally excluded. Per-item reasons are recorded in
`blacksmithing-coverage.json`.

## Remaining provisional integrations

- the separate 76-row weapon-stat reference was not supplied, so all 120 weapon combat profiles remain
  explicitly provisional;
- Wyrmscale resistance and Shield Orb resistance/durability are explicit provisional profiles;
- unusual armor families use the visible `provisional_armor` profile;
- maker UUID/name serialization exists, but a server-owned maker-choice prompt is still required before
  maker marks can safely be applied;
- profile values are displayed, but the repository still lacks complete armor/shield incoming-damage
  hooks from the earlier implementation phase.

