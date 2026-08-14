# Post-Closure Asset Defect Report

Date: 2026-08-10  
Starting HEAD: `ec82f6b2` (`Close new assets project`)  
Status: implemented and intentionally uncommitted pending owner review

## Findings and corrections

| # | Reported defect | Analysis | Correction |
|---:|---|---|---|
| 1 | Ibis models are too large | White and scarlet share one renderer, so separate geometry edits would create variant drift. | `IbisRenderer` now applies one cumulative `0.64` render scale (20% below the earlier `0.8`) and a matching smaller shadow to both variants. Entity collision remains unchanged. |
| 2 | Ibis needs more eating, wandering, and grouping | The inherited passive-animal goals did not provide an explicit flock contract, and idle/eating selection was not weighted. | Added a pure 8:5 eating-to-idle selector (eating is exactly 60% more frequent), reduced random-stroll interval to 40 ticks with speed `1.15`, and added `IbisFlockGoal`. Nearby ibis follow a deterministic local leader within 14 blocks while retaining avoidance/panic priorities. |
| 3 | Merchant carts are too small | Directly multiplying JSON element coordinates makes the art exceed Minecraft's hard `-16..32` model parser boundary. | Added `DecorativeScaledModel`, which uniformly scales baked cart quads by `1.2` around the cart anchor. All six world and inventory models receive the scale without invalid source JSON. Replacement art should be authored at the final visual bounds listed in the manifest. |
| 4 | Moongate texture does not face the player | A static block model cannot continuously follow the camera. | Split the model into a static floor/base and vertical portal layers. `MoongateBlockEntityRenderer` rotates only the vertical translucent layers to the main camera yaw; teleport behavior and the final `moongate_block` ID remain unchanged. |
| 5 | Moonglow bush name and stacking are wrong | The existing ID had one fixed model and replaceable plant properties, so repeated use did not form a controlled column. | The block and item are now forcibly registered as `hedge_bush`; the old ID is deliberately absent and old saved instances may be lost. `HedgeBushBlock` derives bottom/middle/top state from vertical neighbors and uses three generated segment models. |
| 6 | Water well is too small | A direct 1.2 JSON transform exceeds the same vanilla element boundary as the carts. | `DecorativeScaledModel` applies a `1.2` baked-quad scale around the normalized well center. Source JSON remains parser-safe; replacement art should use the final visual bounds in the manifest. |
| 7 | Double-sided ladder is not usable in Adventure | It was tagged climbable but had no active ascent assist or landing at the third-block height. | Adventure placement remains atomic. Horizontal contact now supplies vanilla-style upward motion and resets fall distance; all three cells remain climbable. The top cell has a half-depth two-voxel landing so the player can climb through the front and stand at height three. |
| 8 | Scarecrow cannot be placed on community farms in Adventure | Community farm collision ends at voxel 15, so the generic full-face support test rejects it even after item-use authorization. | Added a narrow support hook to the transactional multiblock item. `AdventureScarecrowItem` authorizes only community-farm support in Adventure; other items and supports keep the original sturdy-face rule. |
| 9 | Dress-form top has a black gradient | The PNG has no corrupt black or partially transparent pixels. The head's top UV sampled a deliberate dark-to-light portion of the purchased atlas, and ambient shading amplified it. | Disabled ambient occlusion for this model, disabled shading on the head element, and mapped its top to a neutral single-pixel atlas sample. No source bitmap was destructively edited. |
| 10 | Eight pool-of-blood visuals are missing | `blood.zip` was inventoried but left unassigned. It contains exactly eight placeable visuals; the ninth art item is a non-placeable falling drop. | Registered `pool_of_blood` with variants `0..7`, four facings, thin non-colliding placement, support checks, creative exposure, loot, cutout rendering, and decorator-tool cycling. All eight models and textures are imported deterministically under Britannia-owned paths. |

## Dimensional results

- Cart source JSON bounds: approximately `35.90477 × 37.78982 × 47.24264` voxels. The baked `1.2` transform produces approximately `43.085724 × 45.347784 × 56.691168` voxels.
- Well source JSON bounds: `16 × 32 × 32` voxels. The baked `1.2` transform produces `19.2 × 38.4 × 38.4` voxels.
- The baked transform is intentional: those final coordinates cannot be represented directly by vanilla element JSON without parser failure. Future replacement models should be authored for the final visual size and may use OBJ/custom geometry if their coordinates exceed the vanilla element envelope.

## Automated evidence

- Focused defect, cross-system, and moongate contract tests: pass.
- Final Gradle build: 1,718 tests, zero failures or errors, 17 skipped.
- Dedicated GameTest server: all 348 required tests passed. The new test covers three-segment Hedge stacking and Adventure scarecrow placement; the ladder test covers placement, climbable tags, top landing, atomic teardown, and one drop.
- Development client: completed model baking, texture stitching, and block-atlas creation. No affected-asset missing-model, malformed-model, or missing-texture errors remain. The existing 44×44 moongate texture reports a harmless mip-level reduction from 4 to 2.

## Interactive checks retained

Animation frequency, flock feel, billboard presentation from all approach angles, perceived model scale, ladder movement feel, and visual acceptance of the repaired dress-form top and eight blood variants remain owner-observed checks in `LIVE_TEST_CHECKLIST.md`.

## Follow-up owner adjustments

- Jhelom Ibis replenishment is automatic on the server tick hook. It runs only in the Overworld, only at the city-spawn interval, only considers the three Jhelom areas, only selects loaded candidate chunks, and targets one shared maximum of 15 independently of generic city animals. Each pass is bounded by the configured spawn-attempt limit.
- Ibis visual scale is now `0.64`, a second multiplicative 20% reduction from `0.8`.
- Natural Scarlet selection is now 35%; White selection is 65%.
- `moonglow_bush` was removed as a registry/resource ID and replaced with `hedge_bush` without a migration alias, per the owner's explicit data-loss authorization.
- Added `britannia_mod:flamingo` as a mobile `CREATURE`, which makes it automatically available to `BritanniaSpawnableEntities`. The initial plushie fallback was superseded when the missing entity files were supplied: the final follow-up implementation uses one animated GeckoLib rig and synchronized/persistent Pink, Rose, and White textures.
- The Britannia spawn block calls `finalizeSpawn` with `MobSpawnType.SPAWNER`; that hook now selects each Flamingo color with equal one-third probability. The supplied MP3 is checksum-verified and converted to a Minecraft-compatible OGG ambient sound; Parrot hurt/death and Chicken step events remain explicit placeholders.
- Flamingo foot flicker was caused by coincident top and bottom faces on each supplied zero-height foot plane. Only the hidden bottom faces are removed during import, preserving all three supplied textures while eliminating z-fighting.
- Britannia spawn-block configuration now becomes eligible on the next server tick instead of retaining the previous randomized cooldown. Peaceful correctly blocks hostile `MONSTER` selections but permits passive `CREATURE` selections such as Flamingos, and the block tracks an entity only after the server accepts it into the world.

## Follow-up validation evidence

- Gradle 8.9 clean build: 1,723 tests, zero failures or errors, 17 skipped.
- Dedicated GameTest server: all 351 required tests passed on the confirmation run. The new end-to-end test configures a real Britannia spawn block, executes its server tick, and verifies exactly one live, tracked Flamingo. An initial run hit one unrelated intermittent textile dropped-item assertion; the unchanged rerun was fully green.
- Development client: resource reload, sound-engine startup, and atlas creation completed with no Flamingo model, animation, texture, or sound warning/error.
- Presentation and animation feel remain owner-observed checks in `LIVE_TEST_CHECKLIST.md`.
