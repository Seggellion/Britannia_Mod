package com.seggellion.britannia_mod.bowlpreparation;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Exact, server-authoritative empty-bowl to water-bowl conversion. */
public final class BowlWaterFillingService {
    private static final ResourceLocation EMPTY_BOWL_ID =
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "empty_bowl");

    private BowlWaterFillingService() {
    }

    /** Registry-identity check that stays safe before the deferred item holder is bound. */
    public static boolean supports(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        return !stack.isEmpty()
                && EMPTY_BOWL_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static Optional<BowlWaterFillingPlan> plan(ItemStack input) {
        if (!supports(input)) {
            return Optional.empty();
        }
        return Optional.of(new BowlWaterFillingPlan(input, ItemRegistry.BOWL_OF_WATER.get()));
    }

    static Optional<BowlWaterFillingPlan> plan(ItemStack input, Item emptyBowl, Item output) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(emptyBowl, "emptyBowl");
        Objects.requireNonNull(output, "output");
        return input.is(emptyBowl)
                ? Optional.of(new BowlWaterFillingPlan(input, output))
                : Optional.empty();
    }

    /** Revalidates and consumes one input even when the player has infinite materials. */
    public static Commit commit(BowlWaterFillingPlan plan, ItemStack liveInput) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(liveInput, "liveInput");
        if (!ItemStack.matches(plan.expectedInput(), liveInput)) {
            return Commit.stale();
        }

        liveInput.shrink(1);
        return Commit.applied(new ItemStack(plan.output()));
    }

    public static ApplyResult apply(
            ServerPlayer player,
            InteractionHand hand,
            BowlWaterFillingPlan plan
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(hand, "hand");
        Objects.requireNonNull(plan, "plan");

        ItemStack liveInput = player.getItemInHand(hand);
        Commit commit = commit(plan, liveInput);
        if (!commit.applied()) {
            return ApplyResult.STALE;
        }

        ItemStack output = commit.output();
        if (liveInput.isEmpty()) {
            player.setItemInHand(hand, output);
        } else {
            BowlPreparationOutput.giveOrDrop(player, output);
        }
        player.getInventory().setChanged();
        return ApplyResult.APPLIED;
    }

    public enum ApplyResult {
        APPLIED,
        STALE
    }

    public record Commit(ApplyResult result, ItemStack output) {
        public Commit {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(output, "output");
            output = output.copy();
            if ((result == ApplyResult.APPLIED) == output.isEmpty()) {
                throw new IllegalArgumentException(
                        "applied commits require one output; stale commits require none");
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
