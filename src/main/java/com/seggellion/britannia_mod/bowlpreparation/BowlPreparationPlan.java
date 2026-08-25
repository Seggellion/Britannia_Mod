package com.seggellion.britannia_mod.bowlpreparation;

import java.util.Objects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Immutable snapshot of one recognized fixed-hand bowl preparation. */
public final class BowlPreparationPlan {
    private final Step step;
    private final ItemStack expectedMainHand;
    private final ItemStack expectedOffhand;
    private final Item output;

    BowlPreparationPlan(Step step, ItemStack expectedMainHand, ItemStack expectedOffhand, Item output) {
        this.step = Objects.requireNonNull(step, "step");
        Objects.requireNonNull(expectedMainHand, "expectedMainHand");
        Objects.requireNonNull(expectedOffhand, "expectedOffhand");
        this.output = Objects.requireNonNull(output, "output");
        if (expectedMainHand.isEmpty() || expectedOffhand.isEmpty()) {
            throw new IllegalArgumentException("preparation inputs must be non-empty");
        }
        this.expectedMainHand = expectedMainHand.copy();
        this.expectedOffhand = expectedOffhand.copy();
    }

    public Step step() {
        return step;
    }

    public ItemStack expectedMainHand() {
        return expectedMainHand.copy();
    }

    public ItemStack expectedOffhand() {
        return expectedOffhand.copy();
    }

    public Item output() {
        return output;
    }

    public enum Step {
        DIRT,
        FERTILE_MIX
    }
}
