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

## Milestone 4 — Crate Container Family

Build and dedicated-server GameTests pass. The checks below require an interactive client or multiplayer session and remain open until performed and accepted.

- [x] Launch the development client through resource reload and confirm no small-, medium-, or large-crate model/texture failures are logged. (2026-08-10; unrelated pre-existing resource warnings remain.)

### Placement and visuals

- [ ] Confirm small, medium, and large crate items appear exactly once in the Britannia decor creative tab.
- [ ] Place all three crates in all four orientations and verify model alignment, UVs, inventory icons, fitted collision, and opening sounds.
- [ ] Confirm the small and large labels identify temporary art and the magenta/black medium crate is unmistakably a placeholder.
- [ ] Verify the large crate occupies the intended 2x2x2 volume and blocked placement leaves no partial structure or consumed item.
- [ ] Save and reload each placed crate in every orientation.

### Inventory and persistence

- [ ] Confirm small crate exposes 9 slots, medium 27 slots, and large 54 slots using the expected vanilla chest screens.
- [ ] Insert partial and full stacks into the first/last slots of each crate; save, exit, reload, and verify exact items/counts persist.
- [ ] Shift-click items in and out until each crate is full; verify no loss, duplication, or inaccessible slots.
- [ ] Break each crate while empty and while populated; verify stored contents plus exactly one matching crate item drop.
- [ ] Break the large crate from its root and from several different child cells; verify identical whole-structure teardown and one inventory drop.

### Multiplayer

- [ ] Open the same crate simultaneously with two clients; move items from both clients and verify both views stay synchronized.
- [ ] Have one client break a populated large crate while another has it open; verify the menu closes safely and contents drop once.
- [ ] Place/break crates on a dedicated server and confirm no client-only menu or block-entity errors appear in logs.

### Milestone 4 acceptance record

- Tester/date:
- Client/dedicated-server result:
- Persistence result:
- Multiplayer result:
- Screenshots or log path:
- Accepted placeholders/replacement notes:

## Milestone 5 — Water Well and Adventure Ladder

The focused tests, full dedicated-server GameTest suite, and automated client resource startup pass. The checks below require an interactive client or multiplayer session and remain open until performed and accepted.

- [x] Launch the development client through resource reload and confirm no `water_well` or `ladder` model/texture failures are logged. (2026-08-10; unrelated pre-existing resource warnings remain.)

### Water well

- [ ] Confirm the water well item appears exactly once in the Britannia decor creative tab and is labeled as temporary art.
- [ ] Place the 1x2x2 well in all four orientations; verify scale, UVs, roof/base alignment, interaction reach, fitted collision, and obstruction rollback.
- [ ] In Survival, fill a partially used watering can and verify it becomes exactly 12 charges without duplicating the stack.
- [ ] Fill one vanilla bucket and one empty pitcher; verify the correct water bucket and water-only pitcher results, stack handling, sound, and no duplication.
- [ ] Try glass bottles and unrelated items; verify the well does not consume or transform them.
- [ ] Interact with every well part, then break root and child parts; verify whole teardown and exactly one well item drop.

### Adventure ladder

- [ ] Confirm the ladder item appears exactly once in the Britannia decor creative tab and is labeled as temporary art.
- [ ] In Adventure mode, place the ladder against ordinary ground in all four orientations and verify no unrelated block-placement permission was granted.
- [ ] Block each required cell in turn; verify placement fails atomically without consuming the item or leaving partial blocks.
- [ ] Climb from both faces and verify all three cells are climbable with usable narrow stair/rung collision.
- [ ] Break the ladder from bottom, middle, and top using several vanilla axes plus the two-handed axe; verify whole teardown and exactly one ladder drop.
- [ ] Verify non-axe tools cannot break the custom ladder in Adventure mode and that axe Adventure permission did not expand to unrelated blocks.
- [ ] Repeat placement/breaking in Creative and Survival and save/reload all four orientations.

### Multiplayer

- [ ] Repeat well filling and Adventure ladder placement/breaking with two clients on a dedicated server; verify authoritative inventory changes, permissions, and teardown remain synchronized.

### Milestone 5 acceptance record

- Tester/date:
- Client/dedicated-server result:
- Water-container result:
- Adventure permission/climbing result:
- Screenshots or log path:
- Accepted placeholders/replacement notes:
