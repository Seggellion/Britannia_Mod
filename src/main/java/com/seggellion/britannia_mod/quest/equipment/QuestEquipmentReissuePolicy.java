package com.seggellion.britannia_mod.quest.equipment;

import com.seggellion.britannia_mod.client.gui.QuestScreenText;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the game decides before Rails is asked for a replacement tool, and what the player is told
 * afterwards (Rowan farming questline M9 item 7).
 *
 * <p>Kept free of Minecraft server state and of HTTP on purpose: everything here is a pure
 * function of an inventory and an answer, so the rules can be tested exactly rather than
 * approximately.
 *
 * <p><b>The division of labour.</b> Rails owns the bound -- how many replacements a player has had
 * for a given quest and item, durably, across reconnects and quest restarts -- because only a
 * durable server-side row can survive those. The game owns the inventory question, because only
 * the game can see an inventory. Neither of us can see a chest, an ender chest or the bank, which
 * is why {@link QuestScreenText#EQUIPMENT_STORAGE_CAVEAT} is said out loud rather than left for the
 * player to discover by losing an allowance.
 */
public final class QuestEquipmentReissuePolicy {

    /**
     * The mandatory, non-currency tutorial equipment, and how many of each a stage needs in hand.
     * Mirrors {@code QuestEquipmentReissue::REISSUABLE_ITEM_IDS} -- Rails validates the same list,
     * so a disagreement is refused there rather than granted here.
     *
     * <p>No coin appears here and none ever will: a coin is a reward for work already done, and
     * handing one out again would be minting money, not restoring a tool. Seed, produce and
     * fertilized dirt are absent for the opposite reason -- they are renewable by regathering
     * (item 8), so a replacement would be a duplication.
     */
    public static final Map<String, Integer> REQUIRED_EQUIPMENT;

    static {
        Map<String, Integer> required = new LinkedHashMap<>();
        required.put("britannia_mod:britannia_shovel", 1);
        required.put("britannia_mod:farming_hoe", 1);
        required.put("britannia_mod:watering_can", 1);
        required.put("britannia_mod:empty_bowl", 2);
        required.put("minecraft:bucket", 1);
        REQUIRED_EQUIPMENT = Map.copyOf(required);
    }

    /** Item ids that are never reissued, whatever else changes. */
    public static final List<String> CURRENCY_ITEM_IDS = List.of(
            "britannia_mod:gold_coin",
            "britannia_mod:silver_coin",
            "britannia_mod:copper_coin"
    );

    private QuestEquipmentReissuePolicy() {
    }

    /** Whether this id is something the game is willing to ask Rails to replace. */
    public static boolean reissuable(String itemId) {
        return itemId != null && !CURRENCY_ITEM_IDS.contains(itemId) && REQUIRED_EQUIPMENT.containsKey(itemId);
    }

    /**
     * How many of {@code itemId} the player is carrying right now.
     *
     * <p>Counts the whole player inventory -- main, hotbar, off hand and armour -- and nothing
     * else. A chest, an ender chest, a bank slot and an item on the ground are all invisible here,
     * by design and not by omission: reissuing against them would be guesswork, and the player is
     * told as much.
     */
    public static int carriedCount(Inventory inventory, String itemId) {
        if (inventory == null) {
            return 0;
        }
        Item item = resolve(itemId).orElse(null);
        if (item == null) {
            return 0;
        }
        int carried = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                carried += stack.getCount();
            }
        }
        return carried;
    }

    /**
     * Whether the player is short of this piece of equipment. "Short", not "absent": a stage that
     * needs two bowls is blocked by holding one.
     */
    public static boolean isMissing(Inventory inventory, String itemId) {
        Integer needed = REQUIRED_EQUIPMENT.get(itemId);
        return needed != null && carriedCount(inventory, itemId) < needed;
    }

    /** The equipment this player is short of, in the order the questline hands it out. */
    public static List<String> missingEquipment(Inventory inventory) {
        return REQUIRED_EQUIPMENT.keySet().stream()
                .filter(itemId -> isMissing(inventory, itemId))
                .toList();
    }

    private static Optional<Item> resolve(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Optional.empty();
        }
        ResourceLocation location = ResourceLocation.tryParse(itemId);
        if (location == null || !BuiltInRegistries.ITEM.containsKey(location)) {
            return Optional.empty();
        }
        return Optional.of(BuiltInRegistries.ITEM.get(location));
    }

    /**
     * The message for one answer from Rails, or for a refusal the game made itself.
     *
     * <p>{@code null} means "say nothing", which is the right answer when the player was not
     * asking for anything -- an interaction that found nothing missing should not produce a
     * sentence about equipment.
     */
    public static String messageKeyFor(Outcome outcome) {
        return switch (outcome) {
            case GRANTED -> QuestScreenText.EQUIPMENT_REISSUED;
            case ALREADY_CARRIED -> QuestScreenText.EQUIPMENT_ALREADY_CARRIED;
            case LIMIT_REACHED -> QuestScreenText.EQUIPMENT_LIMIT_REACHED;
            case UNAVAILABLE -> QuestScreenText.EQUIPMENT_UNAVAILABLE;
            case NOTHING_MISSING -> null;
        };
    }

    /**
     * Maps Rails' refusal codes onto the four things the player can usefully be told.
     *
     * <p>{@code not_reissuable} and {@code quest_not_active} are answers to a question the player
     * did not knowingly ask -- the game only asks about equipment the questline requires while the
     * quest is active -- so they read as "unavailable" rather than as a rule the player broke.
     */
    public static Outcome outcomeFor(boolean granted, String error) {
        if (granted) {
            return Outcome.GRANTED;
        }
        if (error == null) {
            return Outcome.UNAVAILABLE;
        }
        return switch (error) {
            case "still_carried" -> Outcome.ALREADY_CARRIED;
            case "limit_reached" -> Outcome.LIMIT_REACHED;
            default -> Outcome.UNAVAILABLE;
        };
    }

    public enum Outcome {
        /** Rails granted a replacement; a delivery is on its way. */
        GRANTED,
        /** The player already has it, here or in Rails' view. */
        ALREADY_CARRIED,
        /** The player's allowance for this item on this quest is spent. */
        LIMIT_REACHED,
        /** Rails could not be reached, or refused for a reason the player cannot act on. */
        UNAVAILABLE,
        /** Nothing was missing, so nothing was asked. */
        NOTHING_MISSING
    }
}
