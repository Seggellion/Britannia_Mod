package com.seggellion.britannia_mod.bowlpreparation;

import com.seggellion.britannia_mod.registry.ItemRegistry;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Atomic final mix with explicit two-container conservation. */
public final class FertileDirtMixingService {
    public static final int RETURNED_BOWL_COUNT = 2;

    private FertileDirtMixingService() {
    }

    public static Optional<FertileDirtMixingPlan> plan(ItemStack mainHand, ItemStack offhand) {
        return plan(mainHand, offhand, new RecipeSet(
                ItemRegistry.BOWL_OF_FERTILE_DIRT.get(),
                ItemRegistry.BOWL_OF_WATER.get(),
                ItemRegistry.FERTILIZED_DIRT.get(),
                ItemRegistry.EMPTY_BOWL.get()));
    }

    public static Optional<FertileDirtMixingPlan> plan(
            ItemStack mainHand,
            ItemStack offhand,
            RecipeSet recipes
    ) {
        Objects.requireNonNull(mainHand, "mainHand");
        Objects.requireNonNull(offhand, "offhand");
        Objects.requireNonNull(recipes, "recipes");
        if (!mainHand.is(recipes.bowlOfFertileDirt())
                || !offhand.is(recipes.bowlOfWater())) {
            return Optional.empty();
        }
        return Optional.of(new FertileDirtMixingPlan(
                mainHand,
                offhand,
                recipes.fertilizedDirt(),
                recipes.emptyBowl()));
    }

    /**
     * Revalidates both live stacks and consumes one of each in every game mode. On success the
     * commit contains the canonical fertile dirt and both conserved custom bowls.
     */
    public static Commit commit(
            FertileDirtMixingPlan plan,
            ItemStack liveMainHand,
            ItemStack liveOffhand
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(liveMainHand, "liveMainHand");
        Objects.requireNonNull(liveOffhand, "liveOffhand");
        if (!ItemStack.matches(plan.expectedMainHand(), liveMainHand)
                || !ItemStack.matches(plan.expectedOffhand(), liveOffhand)) {
            return Commit.stale();
        }

        liveMainHand.shrink(1);
        liveOffhand.shrink(1);
        return Commit.applied(
                new ItemStack(plan.fertilizedDirt()),
                new ItemStack(plan.emptyBowl(), RETURNED_BOWL_COUNT));
    }

    /** Applies one live transaction and delivers both exact outputs without Creative force-clear. */
    public static ApplyResult apply(ServerPlayer player, FertileDirtMixingPlan plan) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(plan, "plan");

        ItemStack liveMainHand = player.getMainHandItem();
        ItemStack liveOffhand = player.getOffhandItem();
        Commit commit = commit(plan, liveMainHand, liveOffhand);
        if (!commit.applied()) {
            return ApplyResult.STALE;
        }

        ItemStack fertilizedDirt = commit.fertilizedDirt();
        ItemStack returnedBowls = commit.returnedBowls();
        if (liveMainHand.isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, fertilizedDirt);
        } else {
            BowlPreparationOutput.giveOrDrop(player, fertilizedDirt);
        }
        if (liveOffhand.isEmpty()) {
            player.setItemInHand(InteractionHand.OFF_HAND, returnedBowls);
        } else {
            BowlPreparationOutput.giveOrDrop(player, returnedBowls);
        }
        player.getInventory().setChanged();
        return ApplyResult.APPLIED;
    }

    public enum ApplyResult {
        APPLIED,
        STALE
    }

    public record RecipeSet(
            Item bowlOfFertileDirt,
            Item bowlOfWater,
            Item fertilizedDirt,
            Item emptyBowl
    ) {
        public RecipeSet {
            Objects.requireNonNull(bowlOfFertileDirt, "bowlOfFertileDirt");
            Objects.requireNonNull(bowlOfWater, "bowlOfWater");
            Objects.requireNonNull(fertilizedDirt, "fertilizedDirt");
            Objects.requireNonNull(emptyBowl, "emptyBowl");
        }
    }

    public record Commit(
            ApplyResult result,
            ItemStack fertilizedDirt,
            ItemStack returnedBowls
    ) {
        public Commit {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(fertilizedDirt, "fertilizedDirt");
            Objects.requireNonNull(returnedBowls, "returnedBowls");
            fertilizedDirt = fertilizedDirt.copy();
            returnedBowls = returnedBowls.copy();
            boolean hasBothOutputs = !fertilizedDirt.isEmpty() && !returnedBowls.isEmpty();
            if ((result == ApplyResult.APPLIED) != hasBothOutputs) {
                throw new IllegalArgumentException(
                        "applied commits require both outputs; stale commits require neither");
            }
            if (result == ApplyResult.STALE
                    && (!fertilizedDirt.isEmpty() || !returnedBowls.isEmpty())) {
                throw new IllegalArgumentException("stale commits cannot contain outputs");
            }
        }

        static Commit applied(ItemStack fertilizedDirt, ItemStack returnedBowls) {
            return new Commit(ApplyResult.APPLIED, fertilizedDirt, returnedBowls);
        }

        static Commit stale() {
            return new Commit(ApplyResult.STALE, ItemStack.EMPTY, ItemStack.EMPTY);
        }

        public boolean applied() {
            return result == ApplyResult.APPLIED;
        }

        @Override
        public ItemStack fertilizedDirt() {
            return fertilizedDirt.copy();
        }

        @Override
        public ItemStack returnedBowls() {
            return returnedBowls.copy();
        }
    }
}
