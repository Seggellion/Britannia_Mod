package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/** Shared crop/flower nutrient identification and normalized soil arithmetic. */
public final class FarmingSoilCare {
    private FarmingSoilCare() {
    }

    public static Optional<Fertilizer> fertilizerFor(ItemStack stack) {
        if (stack.is(Items.BONE_MEAL)) {
            return Optional.of(new Fertilizer("bone meal", 0.6f, 0.0f, 0.0f, 0.0f));
        }
        if (stack.is(ItemRegistry.TURQUOISE_POWDER.get())) {
            return Optional.of(new Fertilizer("turquoise", 0.0f, 0.3f, 0.0f, 0.0f));
        }
        if (stack.is(ItemRegistry.SULPHUROUS_ASH.get())) {
            return Optional.of(new Fertilizer("sulphurous ash", 0.0f, 0.0f, 0.8f, 0.0f));
        }
        if (stack.is(Items.ROTTEN_FLESH)) {
            return Optional.of(new Fertilizer("rotten flesh", 0.0f, 0.0f, 0.0f, 0.5f));
        }
        return Optional.empty();
    }

    public static float addNormalized(float current, float amount) {
        return Math.max(0.0f, Math.min(1.0f, current + amount));
    }

    public static FlowerSoilSnapshot apply(FlowerSoilSnapshot soil, Fertilizer fertilizer) {
        return soil.withNutrients(
                addNormalized(soil.nitrogen(), fertilizer.nitrogen()),
                addNormalized(soil.phosphorus(), fertilizer.phosphorus()),
                addNormalized(soil.potassium(), fertilizer.potassium()),
                addNormalized(soil.organicMatter(), fertilizer.organicMatter())
        ).withFertilizerLevel(Math.max(1, soil.fertilizerLevel()));
    }

    public record Fertilizer(
            String name,
            float nitrogen,
            float phosphorus,
            float potassium,
            float organicMatter
    ) {
    }
}
