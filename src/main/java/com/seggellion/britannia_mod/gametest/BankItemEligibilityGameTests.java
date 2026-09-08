package com.seggellion.britannia_mod.gametest;

import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bank.item.BankItemCodec;
import com.seggellion.britannia_mod.bank.item.BankItemEligibility;
import com.seggellion.britannia_mod.quest.QuestRewardService;
import com.seggellion.britannia_mod.quest.network.QuestModels;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Milestone 8: item-eligibility policy. Confirms {@link BankItemEligibility} recognizes all
 * three real currency items (gold/silver/copper coins -- the complete set, confirmed against
 * {@code ItemRegistry} and this mod's own {@code MerchantEconomyService}/
 * {@code ServerEconomyService} denomination handling, which reference exactly these three and
 * nothing else), a real quest-reward-stamped item (ADR-013), and an unsupported mod origin
 * (ADR-014) -- that none of these can bypass the check by being nested inside a container, that
 * a near-miss (similar but not actually matching) is not over-matched, and that
 * {@link BankItemCodec#serialize} actually rejects each with the correct typed reason, in the
 * documented check order, rather than silently banking an ineligible item as a generic one.
 *
 * A real running server is required for the same reason {@link BankItemCodecGameTests} needs
 * one: the coin items and the quest-reward pipeline are this mod's own
 * {@code DeferredRegister}-registered/real-service machinery, not populated or runnable outside
 * an actual mod-loading lifecycle.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankItemEligibilityGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private BankItemEligibilityGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void goldCoinIsRecognizedAsCurrency(GameTestHelper helper) {
        check(BankItemEligibility.isCurrency(new ItemStack(ItemRegistry.GOLD_COIN.get())),
            "gold coin was not recognized as currency");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void silverCoinIsRecognizedAsCurrency(GameTestHelper helper) {
        check(BankItemEligibility.isCurrency(new ItemStack(ItemRegistry.SILVER_COIN.get())),
            "silver coin was not recognized as currency");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void copperCoinIsRecognizedAsCurrency(GameTestHelper helper) {
        check(BankItemEligibility.isCurrency(new ItemStack(ItemRegistry.COPPER_COIN.get())),
            "copper coin was not recognized as currency");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void eachCoinTypeIsRejectedBySerializeWithTheCorrectTypedReason(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        for (Item coin : List.of(ItemRegistry.GOLD_COIN.get(), ItemRegistry.SILVER_COIN.get(), ItemRegistry.COPPER_COIN.get())) {
            BankItemEligibility.IneligibleItemException caught = null;
            try {
                BankItemCodec.serialize(new ItemStack(coin), registries);
            } catch (BankItemEligibility.IneligibleItemException exception) {
                caught = exception;
            }
            check(caught != null, "serialize() did not reject coin item " + coin);
            check(caught.reason() == BankItemEligibility.IneligibilityReason.CURRENCY_MUST_USE_BALANCE_PROTOCOL,
                "serialize() rejected the coin for the wrong reason: " + caught.reason());
        }
        helper.succeed();
    }

    // The whole point of this test: container recursion must not let a coin slip through just
    // because the outer stack (a shulker box) is not itself a coin.
    @GameTest(template = TEMPLATE)
    public static void aCoinNestedInsideABankableContainerIsAlsoRejected(GameTestHelper helper) {
        ItemStack shulkerBoxWithCoin = shulkerBoxStack(List.of(
            new ItemStack(Items.DIAMOND, 1),
            new ItemStack(ItemRegistry.SILVER_COIN.get(), 5)
        ));

        check(BankItemEligibility.isCurrency(shulkerBoxWithCoin),
            "a coin nested inside a shulker box was not detected -- container recursion bypassed the currency check");

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        BankItemEligibility.IneligibleItemException caught = null;
        try {
            BankItemCodec.serialize(shulkerBoxWithCoin, registries);
        } catch (BankItemEligibility.IneligibleItemException exception) {
            caught = exception;
        }
        check(caught != null, "serialize() did not reject a shulker box containing a coin");

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aSimilarButDifferentItemIsNotRejected(GameTestHelper helper) {
        // Not an actual registered coin -- must not be treated as currency just because it is
        // gold-themed or superficially similar.
        ItemStack goldIngot = new ItemStack(Items.GOLD_INGOT, 1);
        check(!BankItemEligibility.isCurrency(goldIngot), "a plain gold ingot was incorrectly treated as currency");

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        BankItemCodec.serialize(goldIngot, registries); // must not throw
        helper.succeed();
    }

    // ---------- Quest-bound (ADR-013) ----------

    @GameTest(template = TEMPLATE)
    public static void aRealQuestRewardItemIsRejectedAsQuestBound(GameTestHelper helper) {
        ItemStack rewardStack = grantRealQuestReward(helper);

        check(BankItemEligibility.isQuestBound(rewardStack), "a real quest reward item was not recognized as quest-bound");

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        BankItemEligibility.IneligibleItemException caught = null;
        try {
            BankItemCodec.serialize(rewardStack, registries);
        } catch (BankItemEligibility.IneligibleItemException exception) {
            caught = exception;
        }
        check(caught != null, "serialize() did not reject a real quest reward item");
        check(caught.reason() == BankItemEligibility.IneligibilityReason.QUEST_BOUND,
            "serialize() rejected the quest reward item for the wrong reason: " + caught.reason());

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aQuestRewardItemNestedInsideAContainerIsAlsoRejected(GameTestHelper helper) {
        ItemStack rewardStack = grantRealQuestReward(helper);
        ItemStack shulkerBoxWithQuestItem = shulkerBoxStack(List.of(new ItemStack(Items.DIAMOND, 1), rewardStack));

        check(BankItemEligibility.isQuestBound(shulkerBoxWithQuestItem),
            "a quest reward item nested inside a shulker box was not detected -- container recursion bypassed the quest-bound check");

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        BankItemEligibility.IneligibleItemException caught = null;
        try {
            BankItemCodec.serialize(shulkerBoxWithQuestItem, registries);
        } catch (BankItemEligibility.IneligibleItemException exception) {
            caught = exception;
        }
        check(caught != null, "serialize() did not reject a shulker box containing a quest reward item");
        check(caught.reason() == BankItemEligibility.IneligibilityReason.QUEST_BOUND,
            "wrong rejection reason for a nested quest item: " + caught.reason());

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anItemWithSimilarButUnrelatedCustomDataIsNotRejectedAsQuestBound(GameTestHelper helper) {
        // Deliberately close to the real quest tags (a plausible near-miss key and a key that
        // is a substring/superset of "quest_item") but never the actual "quest_item" key
        // QuestRewardService stamps -- this must not be treated as quest-bound.
        ItemStack stack = new ItemStack(Items.DIAMOND, 1);
        CompoundTag tag = new CompoundTag();
        tag.putString("quest_items", "not_the_real_key");
        tag.putString("quest_note", "unrelated");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        check(!BankItemEligibility.isQuestBound(stack),
            "an item with similar-but-not-matching custom data was incorrectly treated as quest-bound");

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        BankItemCodec.serialize(stack, registries); // must not throw
        helper.succeed();
    }

    // ---------- Unsupported mod origin (ADR-014) ----------

    // No other mod's item is loaded in this test environment, and the item registry is frozen
    // by the time any test runs, so a real foreign-namespace ItemStack cannot be constructed --
    // see BankItemEligibility#isSupportedOriginKey's own documentation. This tests the actual
    // allowlist-membership rule directly against a fabricated ResourceLocation instead, which is
    // the closest available proof of the real rejection logic.
    @GameTest(template = TEMPLATE)
    public static void anUnrecognizedNamespaceIsRejectedByTheAllowlist(GameTestHelper helper) {
        check(!BankItemEligibility.isSupportedOriginKey(ResourceLocation.fromNamespaceAndPath("some_other_mod", "fake_item")),
            "an unrecognized namespace was incorrectly accepted by the origin allowlist");
        check(!BankItemEligibility.isSupportedOriginKey(null),
            "a null registry key (an unregistered item) was incorrectly accepted by the origin allowlist");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void vanillaAndBritanniaModNamespacesAreAcceptedByTheAllowlist(GameTestHelper helper) {
        check(BankItemEligibility.isSupportedOriginKey(ResourceLocation.fromNamespaceAndPath("minecraft", "diamond")),
            "the minecraft namespace was incorrectly rejected by the origin allowlist");
        check(BankItemEligibility.isSupportedOriginKey(ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "anything")),
            "the " + BritanniaMod.MODID + " namespace was incorrectly rejected by the origin allowlist");
        helper.succeed();
    }

    // ---------- No false positives on ordinary items ----------

    @GameTest(template = TEMPLATE)
    public static void aNormalVanillaItemPassesEligibilityCleanly(GameTestHelper helper) {
        ItemStack diamond = new ItemStack(Items.DIAMOND, 1);
        check(BankItemEligibility.isFromSupportedOrigin(diamond), "a plain vanilla item failed the origin check");
        check(!BankItemEligibility.isCurrency(diamond) && !BankItemEligibility.isQuestBound(diamond),
            "a plain vanilla item was incorrectly flagged as currency or quest-bound");

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        BankItemCodec.serialize(diamond, registries); // must not throw
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aNormalBritanniaModItemPassesEligibilityCleanly(GameTestHelper helper) {
        ItemStack ring = new ItemStack(ItemRegistry.ONE_RING.get(), 1); // a plain, unstamped britannia_mod item
        check(BankItemEligibility.isFromSupportedOrigin(ring), "a plain britannia_mod item failed the origin check");
        check(!BankItemEligibility.isCurrency(ring) && !BankItemEligibility.isQuestBound(ring),
            "a plain britannia_mod item was incorrectly flagged as currency or quest-bound");

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        BankItemCodec.serialize(ring, registries); // must not throw
        helper.succeed();
    }

    // ---------- Check order ----------

    // Constructs the one multi-violation combination that is actually buildable without a real
    // foreign-origin item: a coin that also carries the quest_item marker (as if some quest had
    // rewarded a coin). Proves checkEligible's documented order -- currency before quest-bound
    // -- is what actually happens, not an unstated first-match-wins accident.
    @GameTest(template = TEMPLATE)
    public static void whenBothCurrencyAndQuestBoundApplyCurrencyIsReportedFirst(GameTestHelper helper) {
        ItemStack coin = new ItemStack(ItemRegistry.GOLD_COIN.get(), 1);
        CompoundTag tag = new CompoundTag();
        tag.putString("quest_item", "some_quest_reward_id");
        coin.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        check(BankItemEligibility.isCurrency(coin) && BankItemEligibility.isQuestBound(coin),
            "test setup is invalid -- the stack must trigger both the currency and quest-bound checks");

        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        BankItemEligibility.IneligibleItemException caught = null;
        try {
            BankItemCodec.serialize(coin, registries);
        } catch (BankItemEligibility.IneligibleItemException exception) {
            caught = exception;
        }
        check(caught != null, "serialize() did not reject a coin that is also quest-bound");
        check(caught.reason() == BankItemEligibility.IneligibilityReason.CURRENCY_MUST_USE_BALANCE_PROTOCOL,
            "expected currency to be reported first per the documented check order, got: " + caught.reason());

        helper.succeed();
    }

    private static ItemStack grantRealQuestReward(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();

        QuestModels.ItemData reward = new QuestModels.ItemData();
        reward.id = "magic_ring"; // QuestRewardService's own special-cased id, guaranteed to resolve to a real item
        reward.count = 1;

        QuestModels.QuestResponse response = new QuestModels.QuestResponse();
        response.success = true;
        response.quest_id = 42L;
        response.questStateId = "gametest-quest-state";
        response.granted_items = List.of(reward);
        // Rowan farming questline M1: only a TEMPORARY reward carries the quest stamp, and a
        // reward is temporary when the destination node's destroy objective names it -- the
        // magic ring's real shape. A plain grant is the player's to keep and is never stamped.
        JsonObject destroyTrigger = new JsonObject();
        destroyTrigger.addProperty("trigger_key", "ring_destroyed");
        destroyTrigger.addProperty("item_tag", "magic_ring");
        QuestModels.QuestNode node = new QuestModels.QuestNode();
        node.metadata = new JsonObject();
        node.metadata.add("destroy_trigger", destroyTrigger);
        response.currentNode = node;

        QuestRewardService.apply(player, response);

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) return stack.copy();
        }
        throw new IllegalStateException("QuestRewardService did not grant the expected reward item");
    }

    private static ItemStack shulkerBoxStack(List<ItemStack> contents) {
        ItemStack stack = new ItemStack(Items.SHULKER_BOX);
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        return stack;
    }

    /**
     * Throws {@link GameTestAssertException}, never {@link IllegalStateException}. When a check runs
     * inside a {@code succeedWhen} or sequence callback -- directly or through any helper called
     * from one -- {@code GameTestSequence.tickAndContinue} swallows only that one type, which is how
     * a polled condition retries until it holds. {@code GameTestInfo} ticks its sequences outside
     * any try/catch, so anything else escapes into the server tick loop and crashes the whole
     * GameTest server, ending the run and every result in it.
     */
    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
