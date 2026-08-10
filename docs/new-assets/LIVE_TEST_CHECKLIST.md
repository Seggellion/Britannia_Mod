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

## Milestone 6 — Ibis Entity, Variants, and Jhelom Population

The focused automated tests cover the regional policy and asset invariants. The checks below require an interactive client or multiplayer session and remain open until performed and accepted.

- [x] Launch the development client through resource reload and confirm no ibis-specific renderer or resource errors are logged. (2026-08-10; unrelated pre-existing resource warnings remain.)

### Art and entity behavior

- [ ] Confirm the ibis spawn egg appears exactly once in the Britannia creative tab and uses the `Ibis Spawn Egg` label.
- [ ] Spawn white and scarlet variants and inspect model scale, pivots, UV seams, transparency, beak/eye/leg preservation, hitbox, shadow, and the temporary scarlet palette.
- [ ] Observe walking and idle transitions; verify neither animation snaps, drifts, or leaves parts behind. Confirm the imported eating animation resource loads without errors even though no eating mechanic is authorized yet.
- [ ] Save and reload both variants and restart the server; verify each bird retains its variant.

### Jhelom population policy

- [ ] Visit and load each of the three configured Jhelom areas; verify ibis replenish only inside those boundaries.
- [ ] Keep all three Jhelom areas loaded simultaneously and verify the combined population never exceeds exactly 15, regardless of its distribution among the areas.
- [ ] Add or retain other city animals and verify they neither consume the 15-ibis allowance nor cause ibis to consume the generic city-animal allowance.
- [ ] Attempt spawn-egg/command spawning immediately outside Jhelom and after the combined population reaches 15; verify the server rejects the ibis without ghost entities.
- [ ] Restart with a mixed white/scarlet population and verify persistence plus cap reconciliation remain stable.

### Multiplayer

- [ ] With two clients in different Jhelom areas, verify both see the same variants and population, and that simultaneous area loading cannot race above 15.

### Milestone 6 acceptance record

- Tester/date:
- Client/dedicated-server result:
- Variant/art result:
- Regional population/cap result:
- Persistence/multiplayer result:
- Screenshots or log path:
- Accepted placeholder/replacement notes:

## Milestone 7 — Training Dummy Skill Trainer

Automated unit tests, the full build, the dedicated GameTest server, and client resource reload pass. The checks below require an interactive client connected to the real skill service.

- [x] Launch the development client through resource reload and confirm no training-dummy-specific model, blockstate, texture, animation, or renderer warnings/errors are logged. (2026-08-10; unrelated pre-existing warnings remain.)

### Structure and presentation

- [ ] Confirm the temporary `Training Dummy (Temporary Art)` item appears exactly once in the Britannia creative tab and places an atomic 2-wide × 3-high structure in every facing.
- [ ] Inspect world scale, pivots, UVs, transparency, item presentation, deliberate collision, and reachability of all six cells.
- [ ] Break each root/child position while sneaking; verify the whole structure is removed with exactly one item and no orphaned cells.

### Skill behavior

- [ ] Confirm the live skill service accepts the working slugs `swordsmanship`, `mace_fighting`, `fencing`, and `tactics`; record any canonical replacements before release.
- [ ] Strike with every axe class and representative ordinary Bladed items; verify Swordsmanship is the only weapon skill attempted.
- [ ] Strike with representative Bashing items; verify Mace Fighting is the only weapon skill attempted.
- [ ] Strike with representative Polearms and explicit thrusting blades (dagger, kryss, assassin spike, leafblade, sai, shortblade, and tekagi families); verify Fencing is the only weapon skill attempted.
- [ ] Try Throwing weapons, bows, tools, empty hand, offhand-only weapons, and unsupported items; verify no training, cooldown, animation, mining cancellation, or durability change occurs.
- [ ] Test at 24.9, 25.0, and above 25.0; verify no trained weapon skill can cross or gain above 25.0. Confirm no strike ever trains Wrestling, Anatomy, or Lumberjacking.
- [ ] Record enough accepted strikes to verify Tactics attempts are occasional (configured 10%), never substituted for the mapped weapon skill, and remain governed by the normal Tactics skill definition.

### Cooldown, animation, and multiplayer

- [ ] Spam left-click, hold attack, swap weapons, alternate root/child cells, and generate START/STOP/ABORT sequences; verify at most one accepted strike per player per 60 ticks and no weapon durability loss.
- [ ] Confirm every accepted strike replays the one-second hit animation even when the gain roll fails, while rejected cooldown spam does not restart it.
- [ ] With two clients striking the same dummy, verify cooldowns remain independent and both clients see identical server-triggered animation playback.

### Milestone 7 acceptance record

- Tester/date:
- Client/dedicated-server result:
- Skill slug/mapping/cap result:
- Cooldown/durability/multiplayer result:
- Art/animation/collision result:
- Screenshots or log path:
- Accepted placeholder/replacement notes:

## Milestone 8 — Textile Processing

Automated checks cover the exact ratios, supported/rejected material policy, resource shape, registry preservation, and dedicated-server transactions. The following presentation and interaction checks remain for a live client.

### Spinning wheel

- [ ] Confirm `Spinning Wheel (Placeholder)` appears exactly once in the Britannia creative tab, places in all four facings, drops itself, and is axe-mineable.
- [ ] Inspect its unmistakable magenta/black temporary appearance, one-cell footprint, collision, item presentation, and processing sound.
- [ ] Right-click with every vanilla wool color and verify exactly one wool becomes exactly one `ball_of_yarn` per interaction.
- [ ] Right-click separately with cotton and flax and verify exactly one input becomes exactly one `spool_of_thread` per interaction.
- [ ] Try spiders' silk, hemp, empty hand, yarn, thread, and unrelated items; verify the wheel consumes and produces nothing.

### Loom

- [ ] Interact with every cell of the existing 2×3 loom using exactly five yarn; verify one folded cloth is produced and the whole input is consumed.
- [ ] Repeat with exactly five thread and then with larger stacks; verify each interaction consumes exactly five and produces exactly one folded cloth.
- [ ] Try zero through four yarn/thread and unsupported items; verify no item mutation or output occurs.
- [ ] Confirm the existing loom still places/tears down atomically in all facings and its temporary model, collision, and item presentation are unchanged.

### Inventory and multiplayer safety

- [ ] Fill the player inventory, perform each valid conversion, and verify the single output drops safely without loss or duplication.
- [ ] Spam interactions and have two players use the same wheel/loom simultaneously; verify every accepted server interaction has exactly one input debit and one output credit.
- [ ] Confirm no silk item exists yet and `britannia_mod:spiders_silk` never converts to thread.

### Milestone 8 acceptance record

- Tester/date:
- Client/dedicated-server result:
- Spinning-wheel conversion result:
- Loom conversion result:
- Full-inventory/multiplayer result:
- Art/collision/sound result:
- Screenshots or log path:
- Accepted placeholder/replacement notes:

## Milestone 9 — Connected Decorative Display Cases

Automated checks cover the two-cell lifecycle, server-derived connections, absence of block entities, teardown, and exact item drop. The following visual and interaction checks require a live client.

### Placement and presentation

- [ ] Confirm `Display Case (Placeholder)` appears exactly once in the Britannia creative tab and places only on a sturdy floor with two clear vertical cells.
- [ ] Inspect the magenta/black/glass replacement art, 16×16×32-voxel envelope, inventory presentation, cutout transparency, selection shape, collision, and reach from both cells.
- [ ] Place and break cases in every player facing; verify the symmetric structure remains aligned and an axe is the assigned mining tool.
- [ ] Break both lower and upper cells in Survival; verify the whole structure disappears with exactly one item and no orphan state.

### Neighbor forms

- [ ] Place one case and verify the independent form retains all four exterior glass sides.
- [ ] Place two adjacent cases and verify the shared panes disappear and both cases become clean end pieces.
- [ ] Place a straight run of at least three and verify only the outer cases are ends while interior cases are middle pieces.
- [ ] Build all four rotated L layouts and verify the corner case opens exactly the two shared faces.
- [ ] Add/remove cases rapidly, including T and cross layouts, and verify every neighbor updates immediately without stale panes or visual holes on exterior faces.

### Decorative-only boundary

- [ ] Right-click every cell with empty hand and representative items; verify no menu, storage, displayed-item slot, insertion, or item loss occurs.
- [ ] Save/reload a mixed connected layout and verify all structures and derived connections return correctly.

### Milestone 9 acceptance record

- Tester/date:
- Client/resource result:
- Placement/teardown result:
- Independent/end/middle/corner result:
- Decorative-only boundary result:
- Screenshots or log path:
- Accepted placeholder/replacement notes:

## Milestone 10 — One-Block City Moongate

Automated checks cover the single-cell structure, exact 16×32×16 model envelope, source-atlas dimensions, legacy-top cleanup, pass-through collision, registry preservation, and the unchanged teleport contract. The following presentation and live-world checks require a client.

### Art and interaction

- [ ] Confirm `Moongate` appears exactly once in the Britannia creative tab and `Legacy Moongate Top` does not appear there.
- [ ] Place the city moongate and inspect the temporary layered portal, animated atlases, translucent sorting, floor swirl, lighting, particles, hum, inventory model, and exact 32-voxel visible height.
- [ ] Walk through from both faces and from slightly off-center; verify its one-cell pass-through trigger volume feels deliberate and does not require an invisible upper block.
- [ ] Test Survival targeting/break behavior and confirm the unbreakable/no-drop contract remains unchanged.

### Teleport preservation

- [ ] Travel repeatedly after each cooldown and confirm random-city destinations still include Jhelom and the other established cities.
- [ ] Verify the five-second/100-tick re-entry cooldown prevents immediate looping and permits a later trip.
- [ ] Travel while mounted and with an active escort; confirm both arrive with the player and momentum/navigation remain stable.
- [ ] Confirm the linking wand and paired dungeon moongates still link and teleport independently of the city gate.

### Legacy-world migration

- [ ] Load a backed-up world containing old `moongate_block` + `moongate_top` pairs; verify the new full-height visual appears immediately and old top cells disappear without drops or missing-registry warnings.
- [ ] Load an inventory containing a legacy top item; verify the stack remains loadable even though it is hidden from creative search.
- [ ] Save/reload migrated chunks and confirm no top cells return and city gates still teleport.

### Milestone 10 acceptance record

- Tester/date:
- Client/resource result:
- Single-cell/32-voxel presentation result:
- City teleport/mount/escort result:
- Dungeon-pair regression result:
- Legacy-world migration result:
- Screenshots or log path:
- Accepted placeholder/replacement notes:
