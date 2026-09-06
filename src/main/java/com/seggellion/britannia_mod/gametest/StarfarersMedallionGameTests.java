package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.registry.ItemRegistry;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.registries.BuiltInRegistries;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Starfarer M5 gate: the medallion resolves and is usable in a running game.
 *
 * <p>The contract test proves the class and its assets in plain JUnit. This proves the
 * thing the gate actually asks for -- that a real server, having loaded the mod,
 * resolves {@code britannia_mod:starfarers_medallion} from the live registry and can put
 * one in a real player's hands.
 *
 * <p>Deliberately no delivery here. Nothing in M5 fetches from Rails, mints an
 * instance_uuid or writes a receipt; the medallion is stamped by hand below exactly as
 * BlessedItemInventorySync would stamp it, purely to show the item carries that identity
 * like any other blessed item.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class StarfarersMedallionGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    @GameTest(template = TEMPLATE)
    public static void theMedallionResolvesFromTheLiveRegistry(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                BritanniaMod.MODID, "starfarers_medallion");

        Item resolved = BuiltInRegistries.ITEM.get(id);

        check(resolved == ItemRegistry.STARFARERS_MEDALLION.get(),
                "the live registry resolves the same item the mod registered");
        check(BuiltInRegistries.ITEM.getKey(resolved).equals(id),
                "and it round trips back to britannia_mod:starfarers_medallion");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aPlayerCanHoldOneAndItDoesNotStack(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Item medallion = ItemRegistry.STARFARERS_MEDALLION.get();

        player.getInventory().add(new ItemStack(medallion));
        player.getInventory().add(new ItemStack(medallion));

        long slots = player.getInventory().items.stream()
                .filter(stack -> stack.is(medallion))
                .count();
        int total = player.getInventory().items.stream()
                .filter(stack -> stack.is(medallion))
                .mapToInt(ItemStack::getCount)
                .sum();

        check(total == 2, "both medallions are present, found " + total);
        check(slots == 2,
                "two medallions must occupy two slots rather than merging into one -- a "
                        + "merged stack is how a duplicate hides. Found " + slots + " slot(s).");
        helper.succeed();
    }

    /**
     * The blessed stamp travels on the medallion like any other blessed item. Written by
     * hand here, not by the sync, because M5 delivers nothing.
     */
    @GameTest(template = TEMPLATE)
    public static void itCarriesBlessedIdentityLikeAnyOtherBlessedItem(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        ItemStack stack = new ItemStack(ItemRegistry.STARFARERS_MEDALLION.get());
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("blessed", true);
        tag.putString("owner", player.getStringUUID());
        tag.putString("deed_id", "entitlement-uuid");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        player.getInventory().add(stack);

        ItemStack held = player.getInventory().items.stream()
                .filter(candidate -> candidate.is(ItemRegistry.STARFARERS_MEDALLION.get()))
                .findFirst()
                .orElseThrow();
        CustomData data = held.get(DataComponents.CUSTOM_DATA);

        check(data != null, "the medallion kept its blessed data");
        check(data.copyTag().getBoolean("blessed"), "blessed flag survived");
        check(player.getStringUUID().equals(data.copyTag().getString("owner")), "owner survived");
        check("entitlement-uuid".equals(data.copyTag().getString("deed_id")), "deed_id survived");
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
