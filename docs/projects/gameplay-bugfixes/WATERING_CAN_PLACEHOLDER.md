# Replaceable full watering-can placeholder

Added at the user's request on 2026-09-09 for project closeout. The base item model now selects `britannia_mod:item/watering_can_full` when the existing `britannia_mod:full` property is 1 (exactly 12 charges, including legacy missing charge data). Charges 0–11 keep the base model.

The full model is a copy of the current can geometry, with its texture references redirected to `britannia_mod:item/watering_can_full`. The full texture is a byte-for-byte copy of the base texture. It deliberately looks identical until replaced; this completes the resource wiring but does not constitute distinct final artwork or a visual acceptance pass. No existing artwork was edited.

Replace this file with the user's final PNG, preserving the existing UV layout:

`src/main/resources/assets/britannia_mod/textures/item/watering_can_full.png`

If the new art requires different geometry or UVs, also replace:

`src/main/resources/assets/britannia_mod/models/item/watering_can_full.json`

Keep the model ID and the `britannia_mod:full` override unchanged. The placeholder model contains no overrides of its own, so it cannot recurse back through the base model. After replacing resources, reload resources and compare 0, 1, 11, 12 and legacy stacks in inventory, both hands and dropped form.

Status: **PLACEHOLDER_INSTALLED / FINAL_USER_ART_PENDING**. The earlier `PENDING_USER_ASSET` evidence describes the original candidate before this explicit placeholder request.

Validation: JDK 21, `.\gradlew.bat test --tests 'com.seggellion.britannia_mod.farming.PlantingPresentationTest' --no-configuration-cache --console=plain` — **3 tests passed**, no skips/failures/errors. The new resource check verifies the full override, resolvable model/texture and absence of a recursive override. Existing checks retain the 0–11/12/legacy charge contract. Both source PNG hashes are `7daa80cc4d6d6f94d94b4ac3bd25deb2dd8fea48c5c55a44081a9928afcd9014` until the user replaces the placeholder.
