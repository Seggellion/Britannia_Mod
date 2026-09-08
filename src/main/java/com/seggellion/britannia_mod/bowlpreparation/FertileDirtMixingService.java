package com.seggellion.britannia_mod.bowlpreparation;

import com.seggellion.britannia_mod.quest.action.QuestActionEvents;
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
     * Why a pair of hands did not produce a final mix, for the message the player gets.
     *
     * <p>Rowan farming questline M8 item 8. The final mix failed silently for the same reason the
     * dry steps did, and it is the step most likely to be attempted the wrong way round because
     * both hands hold a bowl. Diagnosis only: {@link #plan} is untouched.
     */
    public enum Diagnosis {
        /** Not an attempted mix. Stays silent, as before. */
        NONE,
        /** Both bowls, in the wrong two hands. */
        SWAP_HANDS,
        /**
         * The main hand holds the bowl the mix needs and the off hand holds the wrong thing. The
         * message names what belongs there.
         */
        MISSING_OFF_HAND,
        /** One half of the mix is in hand and the other half is not. */
        WRONG_BOWL
    }

    public static Diagnosis diagnose(ItemStack mainHand, ItemStack offhand) {
        return diagnose(mainHand, offhand, new RecipeSet(
                ItemRegistry.BOWL_OF_FERTILE_DIRT.get(),
                ItemRegistry.BOWL_OF_WATER.get(),
                ItemRegistry.FERTILIZED_DIRT.get(),
                ItemRegistry.EMPTY_BOWL.get()));
    }

    public static Diagnosis diagnose(ItemStack mainHand, ItemStack offhand, RecipeSet recipes) {
        Objects.requireNonNull(mainHand, "mainHand");
        Objects.requireNonNull(offhand, "offhand");
        Objects.requireNonNull(recipes, "recipes");

        if (plan(mainHand, offhand, recipes).isPresent()) {
            return Diagnosis.NONE;
        }
        if (mainHand.is(recipes.bowlOfWater()) && offhand.is(recipes.bowlOfFertileDirt())) {
            return Diagnosis.SWAP_HANDS;
        }
        // An empty off hand is not an attempted mix. FertileDirtMixingItem is registered on the
        // Bowl of Fertile Dirt and nothing else, so its use() runs on every right-click while that
        // bowl is held -- and the old test "main hand holds half the mix" was therefore true every
        // single time. The player got "That bowl is not ready for this. Use an Empty Bowl to
        // gather, and a filled bowl to mix." while holding a filled bowl, on every click, and it
        // never named what was missing. BOWL_OF_DIRT against an empty off hand is silent in
        // BowlPreparationService for exactly this reason; this now matches it.
        if (offhand.isEmpty()) {
            return Diagnosis.NONE;
        }
        // The bowl in hand is the right one and the other half is wrong: name what belongs in the
        // off hand rather than telling the player the bowl they are holding is not ready.
        if (mainHand.is(recipes.bowlOfFertileDirt())) {
            return Diagnosis.MISSING_OFF_HAND;
        }
        // One half of the mix is in hand and the other is not: the player is trying, and the
        // silence is what makes it unsolvable.
        if (offhand.is(recipes.bowlOfWater())
                || mainHand.is(recipes.bowlOfWater()) || offhand.is(recipes.bowlOfFertileDirt())) {
            return Diagnosis.WRONG_BOWL;
        }
        return Diagnosis.NONE;
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
        // Read the identity before delivering it: giving a stack away can empty it.
        String outputItemId = QuestActionEvents.itemId(fertilizedDirt);
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
        // Rowan questline M5 (protocol section 2.1): both inputs are paid for and both outputs are
        // delivered before the mix is reported; a stale plan returned above having changed nothing.
        QuestActionEvents.fertileDirtMix(player, outputItemId);
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
