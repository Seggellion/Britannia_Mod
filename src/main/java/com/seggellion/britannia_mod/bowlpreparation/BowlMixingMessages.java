package com.seggellion.britannia_mod.bowlpreparation;

import com.seggellion.britannia_mod.client.gui.QuestScreenText;
import com.seggellion.britannia_mod.registry.ItemRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Rowan farming questline M8 item 8: what the bowl steps say when they refuse.
 *
 * <p>Messages only. Nothing here decides whether a preparation is valid -- {@code plan} still does,
 * and both callers return exactly what they returned before. This turns the three silent
 * {@code Optional.empty()} paths into a sentence.
 *
 * <p>Keys come from {@link QuestScreenText} rather than being written inline so the localization
 * test can walk one list. That class holds constants and nothing else -- no Minecraft types, no
 * client types -- so reading it from server-side code costs nothing; the {@code client.gui} package
 * it sits in is where the rest of the questline's vocabulary lives.
 */
public final class BowlMixingMessages {

    private BowlMixingMessages() {
    }

    /** The dry preparation steps. */
    public static void send(Player player, ItemStack mainHand, BowlPreparationService.Diagnosis diagnosis) {
        switch (diagnosis) {
            case SWAP_HANDS -> swapHands(player, correctMainHandFor(mainHand), mainHand);
            case WRONG_BOWL -> say(player, QuestScreenText.MIX_WRONG_BOWL);
            case WRONG_DIRT -> say(player, QuestScreenText.MIX_WRONG_DIRT);
            case NONE -> { }
        }
    }

    /** The final mix. */
    public static void send(Player player, FertileDirtMixingService.Diagnosis diagnosis) {
        switch (diagnosis) {
            case SWAP_HANDS -> swapHands(player,
                    new ItemStack(ItemRegistry.BOWL_OF_FERTILE_DIRT.get()),
                    new ItemStack(ItemRegistry.BOWL_OF_WATER.get()));
            case MISSING_OFF_HAND -> needsOffHand(player,
                    new ItemStack(ItemRegistry.BOWL_OF_WATER.get()));
            case WRONG_BOWL -> say(player, QuestScreenText.MIX_WRONG_BOWL);
            case NONE -> { }
        }
    }

    /**
     * Names the half of the mix that is not in hand.
     *
     * <p>"That bowl is not ready for this" was both wrong -- the bowl in hand is exactly right --
     * and useless, because the requirement it left unstated is the whole answer.
     */
    private static void needsOffHand(Player player, ItemStack offHand) {
        player.displayClientMessage(
                Component.translatable(QuestScreenText.MIX_MISSING_OFF_HAND, offHand.getHoverName())
                        .withStyle(ChatFormatting.YELLOW),
                true);
    }

    /**
     * Names both hands explicitly. "Swap your hands" alone is not actionable when the player is
     * holding four similar bowls; the two item names are the whole point of the message.
     */
    private static void swapHands(Player player, ItemStack mainHand, ItemStack offHand) {
        player.displayClientMessage(
                Component.translatable(QuestScreenText.MIX_SWAP_HANDS,
                                mainHand.getHoverName(), offHand.getHoverName())
                        .withStyle(ChatFormatting.YELLOW),
                true);
    }

    /**
     * Given what the player has in their main hand for a reversed dry recipe, what should be there.
     *
     * <p>Both reversed recipes put the ingredient in the main hand, so the bowl that belongs there
     * is the one that takes that ingredient.
     */
    private static ItemStack correctMainHandFor(ItemStack heldIngredient) {
        if (heldIngredient.is(ItemRegistry.DUNG.get())) {
            return new ItemStack(ItemRegistry.BOWL_OF_DIRT.get());
        }
        return new ItemStack(ItemRegistry.EMPTY_BOWL.get());
    }

    private static void say(Player player, String key) {
        player.displayClientMessage(
                Component.translatable(key).withStyle(ChatFormatting.YELLOW), true);
    }
}
