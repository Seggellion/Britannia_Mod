package com.seggellion.britannia_mod.bowlpreparation;

import java.util.Objects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Immutable snapshot of one accepted final fertile-dirt mix. */
public final class FertileDirtMixingPlan {
    private final ItemStack expectedMainHand;
    private final ItemStack expectedOffhand;
    private final Item fertilizedDirt;
    private final Item emptyBowl;

    FertileDirtMixingPlan(
            ItemStack expectedMainHand,
            ItemStack expectedOffhand,
            Item fertilizedDirt,
            Item emptyBowl
    ) {
        Objects.requireNonNull(expectedMainHand, "expectedMainHand");
        Objects.requireNonNull(expectedOffhand, "expectedOffhand");
        this.fertilizedDirt = Objects.requireNonNull(fertilizedDirt, "fertilizedDirt");
        this.emptyBowl = Objects.requireNonNull(emptyBowl, "emptyBowl");
        if (expectedMainHand.isEmpty() || expectedOffhand.isEmpty()) {
            throw new IllegalArgumentException("final-mix inputs must be non-empty");
        }
        this.expectedMainHand = expectedMainHand.copy();
        this.expectedOffhand = expectedOffhand.copy();
    }

    public ItemStack expectedMainHand() {
        return expectedMainHand.copy();
    }

    public ItemStack expectedOffhand() {
        return expectedOffhand.copy();
    }

    public Item fertilizedDirt() {
        return fertilizedDirt;
    }

    public Item emptyBowl() {
        return emptyBowl;
    }
}
