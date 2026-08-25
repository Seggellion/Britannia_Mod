package com.seggellion.britannia_mod.bowlpreparation;

import java.util.Objects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Immutable snapshot of one accepted empty-bowl water fill. */
public final class BowlWaterFillingPlan {
    private final ItemStack expectedInput;
    private final Item output;

    BowlWaterFillingPlan(ItemStack expectedInput, Item output) {
        Objects.requireNonNull(expectedInput, "expectedInput");
        this.output = Objects.requireNonNull(output, "output");
        if (expectedInput.isEmpty()) {
            throw new IllegalArgumentException("water-filling input must be non-empty");
        }
        this.expectedInput = expectedInput.copy();
    }

    public ItemStack expectedInput() {
        return expectedInput.copy();
    }

    public Item output() {
        return output;
    }
}
