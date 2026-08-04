package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.client.screen.bank.BankItemIcon;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.service.banking.BankItemSummary;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Milestone 10: {@code item_key} → icon resolution.
 *
 * <p>GameTests rather than JUnit because {@code BuiltInRegistries.ITEM} needs a bootstrapped
 * game -- the one part of the render contract Architecture Decision 0's extraction cannot reach.
 * The governing property throughout: a key that cannot resolve degrades <b>that one cell</b> to
 * the unknown icon, and nothing ever fails the grid (design §9.5.2).
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankItemIconGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private BankItemIconGameTests() {
    }

    private static BankItemSummary summary(String itemKey, Integer count) {
        return new BankItemSummary(UUID.randomUUID(), 1.0, null, count, itemKey);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void aVanillaKeyResolvesToItsItemWithTheStoredCount(GameTestHelper helper) {
        ItemStack icon = BankItemIcon.iconFor(summary("minecraft:diamond_sword", null));
        check(icon.getItem() == Items.DIAMOND_SWORD, "expected a diamond sword, got " + icon);
        check(icon.getCount() == 1, "an absent count draws as a single item");

        ItemStack stacked = BankItemIcon.iconFor(summary("minecraft:arrow", 64));
        check(stacked.getItem() == Items.ARROW, "expected arrows");
        check(stacked.getCount() == 64, "the stored count must reach the decoration pass");
        helper.succeed();
    }

    /** This mod's own items resolve the same way -- the registry is the registry. */
    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void aModdedKeyResolvesToo(GameTestHelper helper) {
        ItemStack icon = BankItemIcon.iconFor(summary("britannia_mod:bank_cheque", null));
        check(icon.getItem() == ItemRegistry.BANK_CHEQUE.get(), "expected this mod's cheque item");
        helper.succeed();
    }

    /**
     * A well-formed key naming nothing in this client's registry -- an item from a mod this
     * client does not have. The commonest real degradation, and the one §9.5's compatibility
     * rules were written for.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void anUnregisteredKeyFallsBackToTheUnknownIcon(GameTestHelper helper) {
        ItemStack icon = BankItemIcon.iconFor(summary("some_removed_mod:widget", 5));
        check(icon.getItem() == Items.BARRIER, "expected the unknown-item fallback, got " + icon);
        check(!BankItemIcon.resolves(summary("some_removed_mod:widget", 5)), "resolves() must agree");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void aMalformedKeyFallsBackRatherThanThrowing(GameTestHelper helper) {
        for (String malformed : new String[]{"Not A Valid Key!", "UPPERCASE:THING", "a:b:c", ":", ""}) {
            ItemStack icon = BankItemIcon.iconFor(summary(malformed, null));
            check(icon.getItem() == Items.BARRIER, "expected fallback for '" + malformed + "', got " + icon);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void anAbsentKeyFallsBackToTheUnknownIcon(GameTestHelper helper) {
        ItemStack icon = BankItemIcon.iconFor(summary(null, 12));
        check(icon.getItem() == Items.BARRIER, "a pre-identity deposit draws as unknown, never as empty");
        check(!icon.isEmpty(), "the fallback must be drawable -- an empty stack reads as an empty cell");
        helper.succeed();
    }

    /**
     * {@code minecraft:air} is a real registry entry that draws as nothing. It must not sneak
     * through resolution and render an occupied cell as empty -- the exact misreading ("my items
     * disappeared") the identity program existed to fix.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void airResolvesToTheFallbackNotToAnInvisibleIcon(GameTestHelper helper) {
        ItemStack icon = BankItemIcon.iconFor(summary("minecraft:air", null));
        check(icon.getItem() == Items.BARRIER, "air must draw as unknown, got " + icon);
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
