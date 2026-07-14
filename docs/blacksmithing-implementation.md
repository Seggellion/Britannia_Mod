# Blacksmithing implementation

## Five-category interface

The existing screen presents exactly five localized icon buttons in this order: Repair, Smelt, Shields,
Armor, and Weapons. They use the anvil, blast furnace, Bronze Shield, Platemail Tunic, and Viking Sword
item stacks, with vanilla fallbacks. Standard buttons provide focus, hover, tooltip, and disabled-state
behavior; the selected workflow receives a gold outline.

Shields contains the 15 shield recipes. Armor combines Armor, Helmets, and wearable Miscellaneous armor.
Weapons combines Axes, Bashing, Bladed, Polearms, and Throwing. Cannons and other Miscellaneous recipes
remain in the underlying 202-row catalogue for later grouping.

## Horizontal carousel and filtering

The item area is a clipped horizontal carousel using actual `ItemStack` rendering. Arrow buttons,
mouse-wheel navigation, thumbnail clicking, and keyboard Left/Right are supported. Selection is preserved
by recipe ID when possible. Panel and visible-slot widths adapt to the current scaled screen dimensions.

Craft recipes are absent unless the synced/local client state satisfies every applicable condition:
canonical/secondary skills, learned recipe, race/gender, valid off-hand metal, sufficient off-hand count,
secondary inventory ingredients, hammer, and nearby anvil. A five-tick state signature detects off-hand,
inventory, skill, workflow, and target changes without rebuilding every render frame. The server repeats
all authoritative validation before mutation.

The off-hand is the only source for the selectable ingot requirement. Different metals are never
combined. Secondary ingredients are counted and consumed from normal inventory. `Retains Color = No`
still stores the underlying material but disables item tint. Two ammunition recipes use maximum possible
batch count constrained by every ingredient and stack size.

## Repair workflow

The supported Blacksmith-crafted target is held in the main hand; matching repair metal is held in the
off-hand. Opening the anvil with a supported target enters Repair when it is damaged.

The server resolves the original recipe and stored material, compares the target identity token, checks
damage, workstation, Blacksmithy skill, matching ingot type, and off-hand count, then calculates:

`repairCost = ceil(originalPrimaryIngots / 2)`

The same `ItemStack` is repaired with `setDamageValue(0)`. Quality, maker data, origin, material,
enchantments, custom name, profiles, and all compatible components remain unchanged. A valid repair calls
the existing skill-gain system once. A repaired item cannot be repaired again until damaged.

## Smelt workflow

Smelt accepts a supported main-hand equipment target with a resolvable stored metal and ingot-based
recipe. The details show original cost, recovery, and a destructive warning. The first action click enters
confirmation; `Smelt Item` submits the captured identity token and Cancel exits confirmation. Any target
signature change cancels the pending confirmation.

The server re-resolves all data and calculates:

`recoveredMaterial = floor(originalPrimaryIngots / 2)`

It destroys the exact validated target before creating recovery output. Only the stored ingot type is
returned; quality, durability, maker, enchantments, secondary ingredients, gems, cloth, wood, and rare
components never affect recovery.

## Networking and authority

The existing craft payload now contains `CRAFT`, `REPAIR`, or `SMELT`, plus only a stable recipe ID or
target token. Clients never submit costs, recovery, quality, statistics, region, or output data. The
server verifies workstation, target identity, catalogue metadata, stored material, skills, unlocks,
restrictions, inventory, and counts. Same-tick duplicate requests are suppressed, while post-operation
state prevents repeated Repair or Smelt rewards.

The open-screen payload synchronizes learned recipe keys and Bootstrap race/gender needed only to filter
the carousel; this data never authorizes the server operation. It also supplies a short-lived,
server-issued session token that every action must return, preventing packets from being replayed after
the workstation session expires.

## Metadata and legacy items

New crafted stacks store recipe ID and a persistent random instance ID alongside material, quality,
origin, and optional maker data. Existing stacks fall back to their stable item registry path and legacy
`Material` custom-data field. If recipe or material cannot be safely resolved, Repair/Smelt rejects the
item without mutation.

Six old cannon output IDs remain registered and old recipe IDs alias to the four canonical recipes.

## Models, translations, and tint

Every catalogue output and special placeholder ingredient has an item model. Existing final models were
preserved; missing models use the existing project placeholder texture. English translations cover the
five workflows, empty states, confirmation, all 202 outputs, and placeholder ingredients. Catalogue
equipment with retained colour uses the central material-profile tint; fixed-colour recipes return the
unmodified model colour.

## Validation and tests

Startup loading rejects an unsupported schema, missing resource, duplicate recipe/output, invalid
ingredient or skill data, missing armor/shield profiles, counts other than 202, or weapon counts other
than 120. Unit tests cover catalogue/category counts, compound skills, learned flags, retained colour,
Repair/Smelt rounding, five workflow ordering, and model coverage.

The dedicated-server smoke run reaches mod initialization, registry loading, recipes, and advancements.
This repository currently registers no NeoForge GameTest functions, so the runner then reports
`No test functions were given`; use `--no-configuration-cache` because its JavaExec task is incompatible
with this project's current Gradle configuration-cache state.
