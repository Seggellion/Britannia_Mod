package com.seggellion.britannia_mod.bowlpreparation;

import com.seggellion.britannia_mod.registry.ItemRegistry;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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

        var dirt = HandRecipeRoles.match(mainHand, offhand, recipes.emptyBowl(), recipes.dirt());
        var fertile = HandRecipeRoles.match(mainHand, offhand, recipes.bowlOfDirt(), recipes.dung());
        if (dirt.isPresent() == fertile.isPresent()) return Optional.empty(); // absent or ambiguous recipe
        var roles = dirt.orElseGet(fertile::orElseThrow);
        return Optional.of(new BowlPreparationPlan(dirt.isPresent() ? BowlPreparationPlan.Step.DIRT : BowlPreparationPlan.Step.FERTILE_MIX,
                mainHand, offhand, dirt.isPresent() ? recipes.bowlOfDirt() : recipes.bowlOfFertileDirt(), roles.driverHand()));
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

        if (liveMainHand == liveOffhand || !ItemStack.matches(plan.expectedMainHand(), liveMainHand)
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

        BowlPreparationOutput.giveOrDrop(player, commit.output(), InteractionHand.MAIN_HAND);
        player.getInventory().setChanged();
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
