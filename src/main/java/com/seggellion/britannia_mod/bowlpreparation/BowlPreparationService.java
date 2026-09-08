package com.seggellion.britannia_mod.bowlpreparation;

import com.seggellion.britannia_mod.quest.action.QuestActionEvents;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Pure planning followed by a live-stack, server-authoritative preparation commit. */
public final class BowlPreparationService {
    private BowlPreparationService() {
    }

    public static Optional<BowlPreparationPlan> plan(ItemStack mainHand, ItemStack offhand) {
        return plan(mainHand, offhand, new RecipeSet(
                ItemRegistry.EMPTY_BOWL.get(),
                ItemRegistry.DIRT.get(),
                ItemRegistry.BOWL_OF_DIRT.get(),
                ItemRegistry.DUNG.get(),
                ItemRegistry.BOWL_OF_FERTILE_DIRT.get()));
    }

    public static Optional<BowlPreparationPlan> plan(
            ItemStack mainHand,
            ItemStack offhand,
            RecipeSet recipes
    ) {
        Objects.requireNonNull(mainHand, "mainHand");
        Objects.requireNonNull(offhand, "offhand");
        Objects.requireNonNull(recipes, "recipes");

        if (mainHand.is(recipes.emptyBowl()) && offhand.is(recipes.dirt())) {
            return Optional.of(new BowlPreparationPlan(
                    BowlPreparationPlan.Step.DIRT,
                    mainHand,
                    offhand,
                    recipes.bowlOfDirt()));
        }
        if (mainHand.is(recipes.bowlOfDirt()) && offhand.is(recipes.dung())) {
            return Optional.of(new BowlPreparationPlan(
                    BowlPreparationPlan.Step.FERTILE_MIX,
                    mainHand,
                    offhand,
                    recipes.bowlOfFertileDirt()));
        }
        return Optional.empty();
    }

    /**
     * Why a pair of hands did not produce a plan, for the message the player gets.
     *
     * <p>Rowan farming questline M8 item 8. Both dry preparation steps used to fail in complete
     * silence: {@link #plan} returned empty and the item returned {@code pass}, so a player holding
     * the right two items in the wrong two hands got no feedback and no way to find out why. This
     * names the failure. It is diagnosis only -- {@link #plan} is untouched and the caller's return
     * value is unchanged -- so nothing about what is or is not a valid preparation moves.
     */
    public enum Diagnosis {
        /** Not an attempted preparation. Stays silent, as before. */
        NONE,
        /** The right two items, in the wrong two hands. */
        SWAP_HANDS,
        /** A bowl that cannot take this ingredient. */
        WRONG_BOWL,
        /** Something dirt-like that is not this mod's Dirt. */
        WRONG_DIRT
    }

    public static Diagnosis diagnose(ItemStack mainHand, ItemStack offhand) {
        return diagnose(mainHand, offhand, new RecipeSet(
                ItemRegistry.EMPTY_BOWL.get(),
                ItemRegistry.DIRT.get(),
                ItemRegistry.BOWL_OF_DIRT.get(),
                ItemRegistry.DUNG.get(),
                ItemRegistry.BOWL_OF_FERTILE_DIRT.get()));
    }

    public static Diagnosis diagnose(ItemStack mainHand, ItemStack offhand, RecipeSet recipes) {
        Objects.requireNonNull(mainHand, "mainHand");
        Objects.requireNonNull(offhand, "offhand");
        Objects.requireNonNull(recipes, "recipes");

        if (plan(mainHand, offhand, recipes).isPresent()) {
            return Diagnosis.NONE;
        }
        // The two recipes, reversed.
        if ((mainHand.is(recipes.dirt()) && offhand.is(recipes.emptyBowl()))
                || (mainHand.is(recipes.dung()) && offhand.is(recipes.bowlOfDirt()))) {
            return Diagnosis.SWAP_HANDS;
        }
        // This mod's Dirt against a bowl that is already full, or Dung against an empty one.
        if ((mainHand.is(recipes.bowlOfFertileDirt()) || mainHand.is(recipes.bowlOfDirt()))
                && offhand.is(recipes.dirt())) {
            return Diagnosis.WRONG_BOWL;
        }
        if (mainHand.is(recipes.emptyBowl()) && offhand.is(recipes.dung())) {
            return Diagnosis.WRONG_BOWL;
        }
        // An empty bowl against vanilla dirt: the commonest way to get nothing at all, because the
        // recipe wants this mod's Dirt and the two look alike in the hand.
        if (mainHand.is(recipes.emptyBowl()) && isVanillaDirt(offhand)) {
            return Diagnosis.WRONG_DIRT;
        }
        return Diagnosis.NONE;
    }

    private static boolean isVanillaDirt(ItemStack stack) {
        return stack.is(Items.DIRT) || stack.is(Items.COARSE_DIRT) || stack.is(Items.ROOTED_DIRT)
                || stack.is(Items.GRASS_BLOCK) || stack.is(Items.PODZOL);
    }

    /**
     * Revalidates and consumes one item from each snapshot stack. This method does not know about
     * game mode by design: Creative follows the same exact conservation rule as Survival.
     */
    public static Commit commit(
            BowlPreparationPlan plan,
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
        return Commit.applied(new ItemStack(plan.output()));
    }

    /** Applies a plan to the player's current live hands and delivers exactly one output. */
    public static ApplyResult apply(ServerPlayer player, BowlPreparationPlan plan) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(plan, "plan");

        ItemStack liveMainHand = player.getMainHandItem();
        ItemStack liveOffhand = player.getOffhandItem();
        Commit commit = commit(plan, liveMainHand, liveOffhand);
        if (!commit.applied()) {
            return ApplyResult.STALE;
        }

        ItemStack output = commit.output();
        // Read the identity before delivering it: giving a stack away can empty it.
        String outputItemId = QuestActionEvents.itemId(output);
        if (liveMainHand.isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, output);
        } else {
            BowlPreparationOutput.giveOrDrop(player, output);
        }
        player.getInventory().setChanged();
        // Rowan questline M5 (protocol section 2.1): both dry preparation steps report the same
        // action and are told apart by the output they produced. A stale plan -- the hands changed
        // between the packet and the commit -- returned above without consuming or producing
        // anything, so it reports nothing.
        QuestActionEvents.bowlPrepare(player, outputItemId);
        return ApplyResult.APPLIED;
    }

    public enum ApplyResult {
        APPLIED,
        STALE
    }

    public record RecipeSet(
            Item emptyBowl,
            Item dirt,
            Item bowlOfDirt,
            Item dung,
            Item bowlOfFertileDirt
    ) {
        public RecipeSet {
            Objects.requireNonNull(emptyBowl, "emptyBowl");
            Objects.requireNonNull(dirt, "dirt");
            Objects.requireNonNull(bowlOfDirt, "bowlOfDirt");
            Objects.requireNonNull(dung, "dung");
            Objects.requireNonNull(bowlOfFertileDirt, "bowlOfFertileDirt");
        }
    }

    public record Commit(ApplyResult result, ItemStack output) {
        public Commit {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(output, "output");
            output = output.copy();
            if ((result == ApplyResult.APPLIED) == output.isEmpty()) {
                throw new IllegalArgumentException("applied commits require one output; stale commits require none");
            }
        }

        static Commit applied(ItemStack output) {
            return new Commit(ApplyResult.APPLIED, output);
        }

        static Commit stale() {
            return new Commit(ApplyResult.STALE, ItemStack.EMPTY);
        }

        public boolean applied() {
            return result == ApplyResult.APPLIED;
        }

        @Override
        public ItemStack output() {
            return output.copy();
        }
    }
}
