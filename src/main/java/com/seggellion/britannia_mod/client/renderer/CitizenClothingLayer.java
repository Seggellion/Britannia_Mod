package com.seggellion.britannia_mod.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seggellion.britannia_mod.entity.CitizenEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.Color;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CitizenClothingLayer<T extends CitizenEntity> extends GeoRenderLayer<T> {

    // --- NEW: A global flag for the renderer to read ---
    public static List<String> CURRENT_TARGET_BONES = null;

    private static final String[] SLOTS = {
        "shoes", "pants", "skirt", "shirt", "apron", "chest", "cape", "facial_hair", "hair"
    };

    private static final Map<String, List<String>> SLOT_BONES = Map.of(
        "shirt", List.of("shirtRightShoulder", "shirtLeftShoulder", "shirtTop", "shirtBottom", "shirtSides", "shirtCollar", "shirtNeck", "shirtHips", "shirtLeftElbow", "shirtRightElbow"),
        "pants", List.of("pantRightAnkle", "pantRightKnee", "pantLeftAnkle", "pantLeftKnee", "pantsBeltBuckle", "pantsRoot","pantLeftHip","pantRightHip"),
        "shoes", List.of("shoesLeftTop", "shoesAnkleLeft", "shoesRightTop", "shoesAnkleRight", "shoesLeftFront", "shoesRightFront", "shoesRight", "shoesRightHeel", "shoesLeftHeel", "shoesRightBase", "shoesLeftBase"),
        "hair", List.of("hair"),
        "facial_hair", List.of("facial_hair"),
        "cape", List.of("capeChest", "cape", "cape2", "shirtNeck"),
        "chest", List.of("chest_main", "capeChest", "shirtTop"),
        "apron", List.of("shirtBottom", "shirtSides", "apron_knot_back"),
        "skirt", List.of("skirt", "skirt_front", "skirt_back")
    );

    private static final Set<String> DEFAULT_SLOTS = Set.of(
        "shoes", "pants", "shirt", "chest", "cape", "facial_hair", "hair"
    );

    private static final Map<String, Map<String, String>> OUTFIT_TEXTURES = Map.of(
        "wood_trader", Map.of(
            "shoes", "boots",
            "apron", "half_apron",
            "chest", "",
            "cape", ""
        ),
        "fish_trader", Map.of(
            "shoes", "sandals",
            "pants", "kilt_socks",
            "skirt", "kilt",
            "chest", "",
            "cape", ""
        ),
        "salvage_trader", Map.of(
            "shoes", "boots",
            "chest", "chest",
            "cape", "cape"
        )
    );

    public CitizenClothingLayer(GeoRenderer<T> entityRendererIn) {
        super(entityRendererIn);
    }

@Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, 
                       RenderType renderType, MultiBufferSource bufferSource, 
                       VertexConsumer buffer, float partialTick, 
                       int packedLight, int packedOverlay) {
        
        String gender = animatable.getGender();
        gender = gender == null ? "female" : gender.toLowerCase(Locale.ROOT);
        if (!"male".equals(gender) && !"female".equals(gender)) gender = "female";
        String outfitKey = animatable.getOutfitKey();

        for (String slot : SLOTS) {
            if (gender.equals("female") && slot.equals("facial_hair")) continue;

            int index = animatable.getClothingIndex(slot);
            String textureSlot = textureSlotFor(outfitKey, slot);
            if (textureSlot.isBlank()) continue;

            String textureName = gender + "_" + textureSlot + "_" + index + ".png";
            
            String folderPath = gender.equals("male") ? "textures/entity/human/male/" : "textures/entity/human/female/";
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("britannia_mod", folderPath + textureName);

            RenderType slotRenderType = RenderType.entityCutoutNoCull(texture);
            VertexConsumer slotBuffer = bufferSource.getBuffer(slotRenderType);

            List<String> targetBones = SLOT_BONES.get(slot);
            
            if (targetBones != null) {
                // 1. Set the flag for the citizen-style entity renderer.
                CURRENT_TARGET_BONES = targetBones;
                
                // 2. Use Geckolib's native renderer (which perfectly handles live animations)
                getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, 
                                       slotRenderType, slotBuffer, 
                                       partialTick, packedLight, packedOverlay, 
                                       Color.WHITE.argbInt());
                                       
                // 3. Clear the flag
                CURRENT_TARGET_BONES = null;
            }
        }
    }

    private static String textureSlotFor(String outfitKey, String slot) {
        String safeOutfitKey = outfitKey == null ? "" : outfitKey;
        Map<String, String> outfit = OUTFIT_TEXTURES.get(safeOutfitKey);
        if (outfit != null && outfit.containsKey(slot)) {
            return outfit.get(slot);
        }
        return DEFAULT_SLOTS.contains(slot) ? slot : "";
    }
}
