package com.seggellion.britannia_mod.client.renderer;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.farming.FlowerDefinition;
import com.seggellion.britannia_mod.farming.FlowerRegistry;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client-only, authoritative flower species/stage model and texture resolver. */
@OnlyIn(Dist.CLIENT)
public final class FlowerVisualModels {
    public static final int MIN_STAGE = 1;
    public static final int MAX_STAGE = 7;
    public static final ResourceLocation FALLBACK_SPECIES = FlowerRegistry.POPPY;
    public static final int FALLBACK_STAGE = 1;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<ResourceLocation> SUPPORTED_SPECIES = List.of(
            FlowerRegistry.POPPY,
            FlowerRegistry.SNOWDROP,
            FlowerRegistry.LILY,
            FlowerRegistry.FOXGLOVE,
            FlowerRegistry.CAMPION,
            FlowerRegistry.HYACINTH,
            FlowerRegistry.ORFLUER
    );
    private static final Map<ResourceLocation, List<StageModel>> MODELS = buildModels();
    private static final List<ModelResourceLocation> ALL_MODELS = collectAllModels();
    private static final List<Pass> PASS_ORDER = List.of(Pass.BASE, Pass.DYE_MASK);
    private static final Set<String> REPORTED_UNEXPECTED_STATE = ConcurrentHashMap.newKeySet();

    private FlowerVisualModels() {
    }

    public static RenderPlan resolve(ResourceLocation savedSpecies, int savedStage, int savedTint) {
        boolean unknownSpecies = !MODELS.containsKey(savedSpecies);
        ResourceLocation visualSpecies = unknownSpecies ? FALLBACK_SPECIES : savedSpecies;
        int visualStage;
        if (unknownSpecies) {
            visualStage = FALLBACK_STAGE;
            warnOnce("species:" + savedSpecies,
                    "[flower rendering] Unknown synchronized species {}; using {} stage {} without changing saved state",
                    savedSpecies, FALLBACK_SPECIES, FALLBACK_STAGE);
        } else {
            visualStage = Math.max(MIN_STAGE, Math.min(MAX_STAGE, savedStage));
            if (visualStage != savedStage) {
                warnOnce("stage:" + savedSpecies + ":" + savedStage,
                        "[flower rendering] Invalid synchronized stage {} for {}; using visual stage {} without changing saved state",
                        savedStage, savedSpecies, visualStage);
            }
        }

        boolean invalidTint = savedTint < 0 || savedTint > 0xFFFFFF;
        int visualTint = savedTint;
        if (unknownSpecies) {
            visualTint = 0xFFFFFF;
        } else if (invalidTint) {
            FlowerRegistry registry = FlowerRegistry.initial();
            FlowerDefinition definition = registry.byId(savedSpecies).orElseThrow();
            visualTint = registry.fallbackColor(definition).tintValue();
            warnOnce("tint:" + savedSpecies + ":" + savedTint,
                    "[flower rendering] Invalid synchronized tint {} for {}; using deterministic visual fallback without changing saved state",
                    savedTint, savedSpecies);
        }

        StageModel model = stageModel(visualSpecies, visualStage);
        return new RenderPlan(
                savedSpecies,
                visualSpecies,
                savedStage,
                visualStage,
                model,
                savedTint,
                visualTint,
                Tint.WHITE,
                Tint.fromRgb(visualTint),
                unknownSpecies,
                unknownSpecies || visualStage != savedStage,
                unknownSpecies || invalidTint
        );
    }

    public static StageModel stageModel(ResourceLocation species, int stage) {
        List<StageModel> stages = MODELS.get(species);
        if (stages == null) {
            return MODELS.get(FALLBACK_SPECIES).get(FALLBACK_STAGE - 1);
        }
        int clampedStage = Math.max(MIN_STAGE, Math.min(MAX_STAGE, stage));
        return stages.get(clampedStage - 1);
    }

    public static StageModel fallbackModel() {
        return stageModel(FALLBACK_SPECIES, FALLBACK_STAGE);
    }

    public static List<ResourceLocation> supportedSpecies() {
        return SUPPORTED_SPECIES;
    }

    public static List<ModelResourceLocation> allModelLocations() {
        return ALL_MODELS;
    }

    public static List<Pass> passOrder() {
        return PASS_ORDER;
    }

    /** Only bounded diagnostics are reset; cached identifiers remain reload-stable. */
    public static void onModelsReloaded() {
        REPORTED_UNEXPECTED_STATE.clear();
    }

    private static Map<ResourceLocation, List<StageModel>> buildModels() {
        Map<ResourceLocation, List<StageModel>> models = new LinkedHashMap<>();
        for (ResourceLocation species : SUPPORTED_SPECIES) {
            List<StageModel> stages = new ArrayList<>(MAX_STAGE);
            for (int stage = MIN_STAGE; stage <= MAX_STAGE; stage++) {
                ResourceLocation canonical = modelId(species, stage);
                stages.add(new StageModel(
                        canonical,
                        ModelResourceLocation.standalone(canonical),
                        textureId(species, stage, "base_texture"),
                        textureId(species, stage, "dye_mask")
                ));
            }
            models.put(species, List.copyOf(stages));
        }
        return Map.copyOf(models);
    }

    private static ResourceLocation modelId(ResourceLocation species, int stage) {
        return ResourceLocation.fromNamespaceAndPath(
                BritanniaMod.MODID,
                "block/flowers/" + species.getPath() + "/stage_" + stage
        );
    }

    private static ResourceLocation textureId(ResourceLocation species, int stage, String suffix) {
        return ResourceLocation.fromNamespaceAndPath(
                BritanniaMod.MODID,
                "block/flowers/" + species.getPath() + "/stage_" + stage + "_" + suffix
        );
    }

    private static List<ModelResourceLocation> collectAllModels() {
        List<ModelResourceLocation> models = new ArrayList<>(SUPPORTED_SPECIES.size() * MAX_STAGE);
        for (ResourceLocation species : SUPPORTED_SPECIES) {
            for (StageModel model : MODELS.get(species)) {
                models.add(model.canonicalModel());
            }
        }
        return List.copyOf(models);
    }

    private static void warnOnce(String key, String message, Object... arguments) {
        if (REPORTED_UNEXPECTED_STATE.add(key)) {
            LOGGER.warn(message, arguments);
        }
    }

    public enum Pass {
        BASE,
        DYE_MASK
    }

    public record StageModel(
            ResourceLocation canonicalId,
            ModelResourceLocation canonicalModel,
            ResourceLocation baseTextureId,
            ResourceLocation dyeMaskTextureId
    ) {
    }

    public record Tint(float red, float green, float blue, float alpha) {
        public static final Tint WHITE = new Tint(1.0F, 1.0F, 1.0F, 1.0F);

        public static Tint fromRgb(int rgb) {
            return new Tint(
                    ((rgb >> 16) & 0xFF) / 255.0F,
                    ((rgb >> 8) & 0xFF) / 255.0F,
                    (rgb & 0xFF) / 255.0F,
                    1.0F
            );
        }
    }

    public record RenderPlan(
            ResourceLocation savedSpecies,
            ResourceLocation visualSpecies,
            int savedStage,
            int visualStage,
            StageModel model,
            int savedTint,
            int visualTint,
            Tint baseTint,
            Tint dyeMaskTint,
            boolean fallbackSpecies,
            boolean fallbackStage,
            boolean fallbackTint
    ) {
    }
}
