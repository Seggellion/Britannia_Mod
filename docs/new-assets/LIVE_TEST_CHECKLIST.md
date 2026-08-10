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
