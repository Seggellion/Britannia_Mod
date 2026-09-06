package com.seggellion.britannia_mod.grabbyhands;

import com.seggellion.britannia_mod.item.InteriorDecoratorToolItem;
import net.minecraft.world.item.ItemStack;

/**
 * Decides, from hand contents and posture alone, which Grabby operation a right-click means.
 *
 * <p>Split out from the event handler so gesture recognition is testable without an event bus, and
 * so the rules sit in one readable place rather than being spread through nested conditionals.
 */
public final class GrabbyGesture {
    private GrabbyGesture() {
    }

    /**
     * Pickup is sneak + right-click with an empty main hand.
     *
     * <p>An empty main hand is what separates "pick this up" from "use this": right-clicking a chair
     * normally still seats you, and a container still opens.
     *
     * <p>Requiring the offhand to be empty too is deliberate. Vanilla's own rule
     * ({@code ServerPlayerGameMode.useItemOn}) only suppresses block use when a sneaking player holds
     * <em>something</em>, so allowing an occupied offhand here would make the same gesture mean
     * different things depending on what happened to be in the other hand.
     */
    public static boolean isPickupGesture(boolean sneaking, ItemStack mainHand, ItemStack offHand) {
        return sneaking
                && mainHand.isEmpty()
                && offHand.isEmpty();
    }

    /**
     * The pickup gesture, failed on the off hand alone.
     *
     * <p>This is the single most reported way Grabby Hands "does nothing", and it took a production
     * investigation to find because it is invisible from both ends. The player has emptied the hand
     * they can see, sneaked, and clicked their own chair; the off hand holds a torch or a shield they
     * stopped noticing hours ago. The handler then finds no pickup gesture, no axe gesture and
     * nothing to place, and returns without a word.
     *
     * <p>The rule itself is not being relaxed — see {@link #isPickupGesture} for why an occupied off
     * hand genuinely means something different. What changes is that the near miss is now
     * distinguishable from a dead feature.
     */
    public static boolean offHandBlocksPickup(boolean sneaking, ItemStack mainHand, ItemStack offHand) {
        return sneaking
                && mainHand.isEmpty()
                && !offHand.isEmpty();
    }

    /**
     * Holding a recognised axe means "destroy this", not "use this".
     *
     * <p>R-2.11.1. Deliberately narrow, because a general interaction ban would be a much bigger
     * change than the requirement asked for:
     *
     * <ul>
     *   <li>only for blocks Grabby Hands has enrolled as axe-destroyable, so a door, a workbench or
     *       an NPC behaves exactly as it does today;</li>
     *   <li>only the main hand, since that is the hand that swings;</li>
     *   <li>only for the player actually holding the axe - an empty-handed player at the same chair
     *       still sits on it.</li>
     * </ul>
     */
    public static boolean isAxeGesture(ItemStack mainHand) {
        return GrabbyAxes.isAxe(mainHand);
    }

    /**
     * Whether Grabby Hands should stay out of this interaction entirely.
     *
     * <p>The interior decorator tool has its own established right-click language across nudgeable
     * furniture, walls, signs, carpets and shrine anchors — including a rule in
     * {@code ChairBlock.useWithoutItem} that refuses to seat a player holding it in the offhand.
     * Grabby Hands must not shadow any of that.
     */
    public static boolean deferToDecoratorTool(ItemStack mainHand, ItemStack offHand) {
        return mainHand.getItem() instanceof InteriorDecoratorToolItem
                || offHand.getItem() instanceof InteriorDecoratorToolItem;
    }
}
