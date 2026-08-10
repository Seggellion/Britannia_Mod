# UltimaCraft New Assets — Live Test Checklist

## Milestone 2 — Low-Risk Decorative Assets

Build and dedicated-server game tests pass. The checks below require an interactive client and remain open until performed and accepted.

### Global setup

- [ ] Launch a development client with no missing-model or missing-texture messages in `latest.log`.
- [ ] Open the Britannia decor creative tab and confirm each Milestone 2 block appears exactly once.
- [ ] Confirm each inventory icon is legible and uses a sensible camera transform.
- [ ] Place and break every asset in Survival; confirm exactly one matching item drops. Verify an axe is correct for the wooden globe and a pickaxe for sandstone/metal props.
- [ ] Save, exit, reload, and confirm every placed asset persists with the same orientation.

### Imported temporary art

- [ ] `globe`: place in all four horizontal orientations; verify pedestal alignment, UVs, scale, and fitted collision.
- [ ] `fern`: verify cutout transparency, no opaque atlas background, dirt/farmland/moss support, replacement behavior, no collision, and break-on-support-removal.
- [ ] `moonglow_bush`: verify cutout transparency, purple-flower UV selection, substrate rules, no collision, and break-on-support-removal.
- [ ] `folded_cloth`: place in all four orientations; verify stack scale, surface alignment, UVs, and shallow collision.
- [ ] Confirm all four names explicitly identify the art as temporary.

### Code-authored placeholders

- [ ] `bolt_of_cloth`: verify the magenta/black placeholder is unmistakable and the roll shape/collision is usable.
- [ ] `pewter_mug`: verify small tabletop scale, handle silhouette, orientation, and fitted collision.
- [ ] `kettle`: verify tabletop scale, handle/spout silhouette, orientation, and fitted collision.
- [ ] `plates_and_silverware`: verify the combined setting remains visible on a table and uses very shallow collision.
- [ ] Confirm all four names explicitly identify them as placeholders.

### Existing sandstone family

- [ ] `custom_sandstone_brick`: place enough blocks to observe all four weighted visual variants.
- [ ] `regular_sandstone_wall`: verify the requested 16x5 straight profile and neighbor connections.
- [ ] Verify straight, corner, end, and vertical/stacked behavior for both sandstone windows, both posts, battlement, column, ornate wall, and block wall.
- [ ] Break each sandstone family block with a pickaxe and confirm one matching drop.
- [ ] Confirm no duplicate sandstone IDs or creative entries were introduced.

### Acceptance record

- Tester/date:
- Client launch result:
- Visual/collision result:
- Screenshots or log path:
- Accepted placeholders/replacement notes:

## Milestone 3 — Large Decorative Multiblocks

Build and dedicated-server GameTests pass. The checks below require an interactive client and remain open until performed and accepted.

### Global multiblock behavior

- [ ] Confirm all ten new items appear exactly once in the Britannia decor creative tab with their temporary/placeholder labels.
- [ ] Place every asset facing north, east, south, and west; verify the selected minimum cell remains the intended placement anchor.
- [ ] Obstruct each occupied cell in turn and verify placement fails without consuming the item or leaving partial blocks.
- [ ] Verify placement fails when any base-layer cell lacks solid support.
- [ ] Break the root and at least one child cell in Survival; verify the whole structure disappears and exactly one matching item drops.
- [ ] Save, exit, and reload with each structure placed; verify every part, orientation, and collision shape persists.

### Merchant carts

- [ ] Place red, purple, blue, green, yellow, and white carts side by side; verify each approved color is unmistakable.
- [ ] Verify every cart occupies its intended centered 3x3x3 cell volume and does not block the entire volume with full-cube collision.
- [ ] Verify wheel/body collision is usable in all four orientations.
- [ ] Confirm all six treatments are acceptable as temporary art pending replacement.

### Fountain

- [ ] Verify the fountain occupies 2x2x3 cells, renders from the middle-layer root without duplicate geometry, and aligns to the ground.
- [ ] Verify animated water renders translucent without opaque squares, z-fighting, or missing texture frames.
- [ ] Walk around/into the basin and verify the deliberate basin/pillar collision is usable.

### Scarecrow, dress form, and loom

- [ ] `scarecrow`: verify the 2x1x2 structure, cutout edges, narrow post/body collision, and ground alignment.
- [ ] `dress_form`: verify the 1x1x2 structure, cutout edges, narrow base/torso collision, and that it has no armor-stand behavior.
- [ ] `loom`: verify the re-authored 32x48x16-voxel model fills its 2x1x3 structure, is not vertically distorted, and has usable frame collision.
- [ ] Confirm the loom has no crafting/storage UI yet and does not consume yarn/thread in this milestone.

### Milestone 3 acceptance record

- Tester/date:
- Client launch result:
- Placement/rollback/teardown result:
- Visual/collision result:
- Screenshots or log path:
- Accepted placeholders/replacement notes:
