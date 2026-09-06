# Patch 18 weapon integration

Source: `C:\projects\britannia\raw fiels\weapons` (intentional spelling). All 17 files were inspected recursively. There are six distinct items, with no animations, alternate weapon variants, or backups. The five `.bbmodel` projects are authoring counterparts of the supplied JSON exports, not additional items. All embedded Blockbench texture pixels match the corresponding exported PNGs (their PNG compression differs). The source folder is unchanged; its SHA-256 inventory is in [PATCH18_WEAPON_ASSETS.json](PATCH18_WEAPON_ASSETS.json).

Implementation started from clean `patch-18` at `a2f6391c`. No isolation was needed. Changes remain uncommitted on that branch; no push, deployment, Fabric, or Rails changes. No applicable `AGENTS.md` was found in the repository or its parent directories.

The build actually pins Minecraft **1.21.1**, NeoForge **21.1.72**, Java **21**, GeckoLib **4.6.6**, Gradle **8.9**, and mod version **0.1.8a**. The wrapper is launched with installed Temurin **21.0.9+10**, because the machine's default `java` is Java 8. Gradle selects its configured Java 21 toolchains for compilation and game runs; the dedicated-server run reports Microsoft **21.0.8**.

## Asset-to-item inventory

Every ID below has the `britannia_mod:` namespace. Runtime models are in `assets/britannia_mod/models/item/<id>.json`; runtime textures are in `assets/britannia_mod/textures/item/weapons/<id>.png`.

| Source item | Source files relative to `weapons` | Before this completion | Registry ID / final disposition | Ingots | Blacksmithy | Training skill |
|---|---|---|---|---:|---:|---|
| Dagger | `dagger/dagger.json`, `dagger/dagger.png` | Real sword item, supplied model, existing recipe; incorrect wear override and missing attack attributes | `dagger`; retain artwork, registry ID and oyster harvesting; fix shared sword combat/wear | 3 | 0.0 | Fencing |
| Viking Sword | `viking_sword.bbmodel`, `viking_sword/viking_sword.json`, `viking_sword/viking_sword.png` | Real sword item and recipe, placeholder flat model | `viking_sword`; import supplied 7-element model and texture | 14 | 24.3 | Swordsmanship |
| Katana | `katana.bbmodel`, `katana/katana.json`, `katana/katana.png` | Catalogue placeholder item, model and recipe | `katana`; promote same ID to functional sword; import 5-element model | 8 | 44.1 | Swordsmanship |
| Rapier | `rapier.bbmodel`, `rapier/rapier.json`, `rapier/rapier.png` | Absent | `rapier`; functional sword, 9-element model, new Bladed recipe | 8* | 36.7* | Fencing |
| Halberd | `halberd.bbmodel`, `halberd/halberd.json`, `halberd/halberd.png` | Catalogue placeholder item, model and Polearms recipe | `halberd`; promote same ID to functional melee weapon; import 10-element model | 20 | 39.1 | Fencing (existing Polearms convention) |
| Decorative Shield | `decorative_shield.bbmodel`, `decorative_shield - Converted.json`, `decorative_shield.png` | No wearable item or recipe; an unused wall-model export exists separately | `decorative_shield`; functional blocking shield, 16-element supplied model, new Shields recipe | 14* | 0.0* | Shield; not a training weapon |

`*` Provisional analogue, explicitly marked in recipe data: rapier copies the existing **kryss** requirements; decorative shield copies **metal_shield** requirements and its 50–65 initial durability (maximum 65). Its gold decorative artwork retains its original colors, so its recipe disables material tint. Existing `decorative_shield_1`, `_1bw`, `_2`, and `_2bw` wall decorations keep their IDs and artwork. They are not alternate exports in this source folder.

The catalogue now has **204 entries / 121 weapons**: the previous 202 imported recipes are unchanged, plus rapier and decorative shield. All six items use Blacksmithing; none belongs to another implemented profession.

## Runtime assets and gameplay decisions

- Use the same baked Java item-model pipeline as the dagger. No renderer, GeckoLib conversion, additional dependency, or animation controller is needed. Client-only color and shield-blocking property registration stays in `ClientModSetup`.
- Preserve all supplied melee geometry, rotations, UVs, textures, and seven display transforms. Remove unused exporter metadata (`format_version`, `texture_size`, `groups`) from newly imported runtime JSON and replace unqualified `block/<name>` texture references with lowercase namespaced paths. PNGs are byte-identical copies. Some flat blade elements are deliberate texture planes.
- Apply existing material tint to metallic elements of viking sword, katana, and rapier, leaving their grips untinted. Halberd keeps its existing `retains_color=false` recipe. Dagger artwork/tint indices stay unchanged.
- The decorative shield export has no item transforms and is centered for a wall at `[8,16,14.95]`. Translate it by `[0,-8,-6.95]` to the item center, preserving shape and UVs. Add GUI, held, ground, frame and blocking transforms. The Blockbench project has a stale 65-pixel height setting; the supplied export, UVs and actual PNG use 64×64, which the runtime preserves.
- All five melee items use the existing `QualitySwordItem` and iron tier: 250 durability, one item per stack, vanilla sword abilities/enchantment tags, and one wear per combat hit. Missing Minecraft balance uses the conservative iron-sword analogue: **6 total attack damage / 1.6 attacks per second** at normal quality. The existing intended `quality - 1` bonus now affects the actual attack attribute: newly exceptional quality 2 gives **7 total damage**. Legacy quality values 1–4 retain their extra damage steps and stored data.
- These are provisional Minecraft combat values, not imported Ultima Online damage statistics. The existing `WeaponProfile` values are provisional descriptive catalogue metadata, not a live combat engine; this completion does not introduce strength gates, special moves, reach changes, or an offhand restriction system. Halberd keeps the existing two-handed profile metadata and Polearms/Fencing training classification.
- The existing material selection, tint and metadata are preserved. Material tiers/multipliers are not newly applied to melee durability or attack power: those were not applied by the dagger. Do not treat the dormant material-profile multipliers as new balance decisions.
- `QualitySwordItem.getDamage` previously returned attack power as Minecraft wear, preventing correct durability and repair. Removing that override restores normal damage components; stack-sensitive attack attributes implement the intended quality bonus. Modern smithing stacks now show the same Normal/Exceptional quality and origin tooltip as the rest of blacksmithing; legacy stacks retain their legacy quality labels. Promoted katana/halberd stacks also read their pre-existing catalogue-only material and quality metadata, preserving old crafted colors and quality without requiring migration.
- All six appear in the existing Britannia items creative tab. The five melee items are in `minecraft:swords` and the existing grain-harvest-blade tags; decorative shield is in `c:tools/shield`.

## Complete crafting path

1. Put a blacksmith's hammer in the main hand and one supported ingot type in the offhand; right-click an ordinary anvil. `BlacksmithInteractionEvent` opens the existing screen with a server-owned session token.
2. The screen reads `CraftableRegistry`, lists Bladed/Polearms under Weapons and the shield under Shields, and sends `CraftBlacksmithItemC2SPayload`. It never supplies costs or skill values.
3. `NetworkHandler` validates the session and resolves the ID from the authoritative catalogue. `BlacksmithCrafting` rechecks hammer, nearby anvil, current skill, selected metal, resources, free output slot and any existing learned-recipe/race/gender constraints. These six have no learned-recipe, race, or gender restriction.
4. Required Blacksmithy is the greater of the recipe minimum and metal threshold: iron 0, silver 55, tin 65, shadow iron 70, copper 75, bronze 80, gold 85, agapite 90, verite 95, valorite 99. No separate material-variant item IDs are added. Required ingots must be together in the offhand.
5. An accepted attempt consumes the listed ingots once. The existing success formula is `clamp(0.50 + (skill - recipeMinimum)/100, 0.05, 1)`; failure loses those resources and creates nothing. Successful crafting rolls Exceptional with `clamp((skill - recipeMinimum)/50, 0.01, 1)`. A 100-skill smith always succeeds exceptionally for these six recipes. Skill gain follows the existing service. Region, recipe, quality, material and instance identity are server-authored; existing maker-mark eligibility is retained, without adding the unimplemented maker prompt.
6. Hold the crafted equipment in the main hand and matching ingots in the offhand, then right-click an anvil for Repair/Smelt. Repair costs `ceil(originalIngots/2)`, checks shape and metal skill, resets wear and retains identity. Smelt consumes the validated item and returns `floor(originalIngots/2)` matching ingots. New recipe IDs participate in this same workflow. No vanilla crafting recipe was added.

## Reproduce verification and check in game

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot'
python tools/new-assets/import_patch18_weapons.py --check
.\gradlew.bat build --no-configuration-cache --console=plain
.\gradlew.bat runGameTestServer --no-configuration-cache --console=plain
.\gradlew.bat runClient -Pdev --no-configuration-cache
```

The importer rejects unaccounted source files and checks source hashes and runtime imports. To regenerate from unchanged source, omit `--check`; use `--source <path>` if the raw folder is mounted elsewhere.

In an operator-enabled test world, use these exact commands:

```mcfunction
/give @s britannia_mod:dagger
/give @s britannia_mod:viking_sword
/give @s britannia_mod:katana
/give @s britannia_mod:rapier
/give @s britannia_mod:halberd
/give @s britannia_mod:decorative_shield
/give @s britannia_mod:blacksmith_hammer
/give @s minecraft:iron_ingot 64
/give @s minecraft:anvil
/skill @s blacksmithy 100
```

For crafting, clear a slot, place the anvil on solid ground, equip the hammer and offhand ingots, right-click the anvil and select each entry. Confirm exact consumption from the table and one exceptional output. Repeat with fewer ingots and `/skill @s blacksmithy 0` to check refusals for recipes above zero. Test `/give @s britannia_mod:valorite_ingot 64` at skill 50 for material refusal, then at skill 100 for successful colored equipment.

Inspect all six in inventory, both first-person hands, third-person views, dropped on the ground with Q, and item frames. Hold right-click with the shield, including offhand use, to check its raised view and blocking. Strike a mob with each melee item and check durability increases by one wear; damage an item, then use Repair and Smelt as above. Give `/give @s britannia_mod:training_dummy`, place it, and test the five melee disciplines (the existing training cap is 25). Confirm a dagger still harvests black-lipped oysters and the rapier does not.

## Executed verification

- Baseline before edits: unit suite **3,476 total**, **0 failures/errors**, **17 skipped**.
- Final sequential command: `gradlew.bat build runGameTestServer --no-configuration-cache --console=plain` — **BUILD SUCCESSFUL**, September 6, 2026. Unit suite: **3,478 total / 3,461 passed / 17 skipped / 0 failures or errors**. Dedicated server: **all 1,094 GameTests passed**, including all six new weapon tests, existing Blacksmithy skill/metal gates and training regressions.
- New GameTests execute the real C2S handler with server session validation; check all six successful crafts and exact consumption, insufficient resources and skill, metal gating, failed rolls, invalid sessions/IDs, missing hammer, remote crafting and same-tick duplicates; exercise quality, combat wear, training classification, legacy catalogue metadata, repair identity and smelt recovery. The shield test advances the real simulated player ticks, blocks an incoming frontal mob attack and checks seven wear for six blocked damage.
- Asset tests use Minecraft 1.21.1's actual model parser and check texture references, lowercase paths, UV ranges and seven display contexts. Importer `--check` accounts for all 17 source files, compares source hashes and verifies runtime output. All 202 original recipe objects and all 1,693 existing translations are preserved. No vanilla crafting bypass was found. `git diff --check` passes.
- Packaging audit: all six model/texture pairs present; GeckoLib 4.6.6 and NanoHTTPD bundled; no GameTests or `.bbmodel` files packaged; no client-class references in the equipment classes.
- Client startup was executed in isolated `build/weapon-client` with Temurin 21.0.9. It completed texture/model reload and reached world creation; there were **no model or texture errors for these exact six IDs**. **The user stopped Computer Use with Escape.** Inventory, held views, dropped items, blocking transforms and the graphical blacksmith menu were **not visually verified**. The practical checks above remain for a graphical client; compilation and server tests are not visual verification.
- No unresolved implementation or asset blockers. The remaining limitation is graphical QA after Computer Use was stopped.

Built distribution JAR: `C:\projects\britannia\mod\Britannia_Mod\build\libs\britannia_mod-0.1.8a-all.jar` (34,602,148 bytes).

SHA-256: `1d47325df71c8f5f755252b7f12d83a3ad014f878a0683d4fcc6715f56c27613`.

The artifact records `patch-18`, base commit `a2f6391cdcc61fe8563545169af69145fd7d94f3`, and `git.dirty=true`; there is no new commit. Build log: `build-weapon-complete.log`; unit report: `build/reports/tests/test/index.html`; dedicated-server log: `run/gametest/logs/latest.log`.
