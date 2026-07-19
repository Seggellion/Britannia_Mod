package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bank.item.BankItemNesting;
import com.seggellion.britannia_mod.bank.item.BankItemWeight;
import com.seggellion.britannia_mod.item.QualitySwordItem;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.item.WeightedCommodityItem;
import com.seggellion.britannia_mod.item.WeightedFishItem;
import com.seggellion.britannia_mod.item.WeightedWoodItem;
import com.seggellion.britannia_mod.registry.FishRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.WeaponRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Milestone 8 Slice 2: {@link BankItemWeight} in isolation. Pure weight resolution -- no
 * eligibility policy (ADR-009/010 already resolved that), no bank-item persistence, no
 * transfer protocol.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankItemWeightGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private BankItemWeightGameTests() {
    }

    // ---------- Existing Weighted* classes: regression, not reimplementation ----------

    @GameTest(template = TEMPLATE)
    public static void woodItemResolvesThroughItsOwnExistingGetWeight(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ItemRegistry.WEIGHTED_WOOD_ITEM.get());
        WeightedWoodItem woodItem = (WeightedWoodItem) stack.getItem();
        woodItem.setWeight(stack, 2.5);

        check(BankItemWeight.resolve(stack) == 2.5,
                "wood item did not resolve to its own WeightedWoodItem#getWeight value: " + BankItemWeight.resolve(stack));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fishItemResolvesThroughItsOwnExistingGetWeight(GameTestHelper helper) {
        // Constructing a fresh WeightedFishItem at test time (rather than using an already
        // DeferredRegister-registered instance) throws "Registry is already frozen" -- item
        // registration must happen during mod loading, not inside a running GameTest. Use one
        // of FishRegistry's real statically-declared fish ids instead.
        WeightedFishItem fishItem = (WeightedFishItem) FishRegistry.FISH_ITEMS.get("mud_puppy").get();
        ItemStack stack = new ItemStack(fishItem);
        fishItem.setWeight(stack, 1.75);

        check(BankItemWeight.resolve(stack) == 1.75,
                "fish item did not resolve to its own WeightedFishItem#getWeight value: " + BankItemWeight.resolve(stack));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void commodityItemResolvesThroughItsOwnExistingGetWeight(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ItemRegistry.RAW_PORK.get());
        WeightedCommodityItem.setWeight(stack, 0.6);

        check(BankItemWeight.resolve(stack) == 0.6,
                "commodity item did not resolve to its own WeightedCommodityItem#getWeight value: " + BankItemWeight.resolve(stack));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void commodityItemWithNoExplicitWeightResolvesToTheClasssOwnDefault(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ItemRegistry.RAW_PORK.get());
        // Never call setWeight -- WeightedCommodityItem.getWeight itself defaults to 1.0.
        check(BankItemWeight.resolve(stack) == 1.0,
                "commodity item with no explicit weight tag did not fall through to "
                        + "WeightedCommodityItem's own 1.0 default: " + BankItemWeight.resolve(stack));
        helper.succeed();
    }

    // ---------- Unmapped items: default weight, not zero, not an exception ----------

    @GameTest(template = TEMPLATE)
    public static void unmappedItemResolvesToTheDefaultUnitWeight(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.DIAMOND);
        check(BankItemWeight.resolve(stack) == BankItemWeight.DEFAULT_UNIT_WEIGHT,
                "unmapped item did not resolve to DEFAULT_UNIT_WEIGHT: " + BankItemWeight.resolve(stack));
        check(BankItemWeight.DEFAULT_UNIT_WEIGHT != 0.0, "DEFAULT_UNIT_WEIGHT must not be zero");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void unmappedItemWithUnusualDataComponentsStillResolvesCleanly(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS), 5);
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Weird Sword"));

        double weight = BankItemWeight.resolve(stack);
        check(isFinitelyNonNegative(weight), "unmapped item with unusual components produced a bad weight: " + weight);
        check(weight == BankItemWeight.DEFAULT_UNIT_WEIGHT,
                "unmapped item's weight should not be affected by unrelated components: " + weight);
        helper.succeed();
    }

    // ---------- Count scaling ----------

    @GameTest(template = TEMPLATE)
    public static void weightScalesWithStackCountRatherThanBeingFlat(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ItemRegistry.WEIGHTED_WOOD_ITEM.get(), 10);
        WeightedWoodItem woodItem = (WeightedWoodItem) stack.getItem();
        woodItem.setWeight(stack, 3.0);

        check(BankItemWeight.resolve(stack) == 30.0,
                "a stack of 10 did not weigh 10x a single item: " + BankItemWeight.resolve(stack));

        ItemStack singleUnmapped = new ItemStack(Items.DIAMOND, 1);
        ItemStack tenUnmapped = new ItemStack(Items.DIAMOND, 10);
        check(BankItemWeight.resolve(tenUnmapped) == BankItemWeight.resolve(singleUnmapped) * 10,
                "an unmapped item's weight did not scale linearly with count");
        helper.succeed();
    }

    // ---------- Nested containers (ADR-009): contents-inclusive, proven with an exact number ----------

    @GameTest(template = TEMPLATE)
    public static void shulkerBoxWeightIsContentsInclusiveWithAnExactExpectedTotal(GameTestHelper helper) {
        ItemStack wood = new ItemStack(ItemRegistry.WEIGHTED_WOOD_ITEM.get(), 2);
        WeightedWoodItem woodItem = (WeightedWoodItem) wood.getItem();
        woodItem.setWeight(wood, 4.0); // 2 * 4.0 = 8.0

        ItemStack commodity = new ItemStack(ItemRegistry.RAW_PORK.get(), 3);
        WeightedCommodityItem.setWeight(commodity, 0.5); // 3 * 0.5 = 1.5

        ItemStack unmapped = new ItemStack(Items.DIAMOND, 2); // 2 * DEFAULT_UNIT_WEIGHT (1.0) = 2.0

        ItemStack shulkerBox = new ItemStack(Items.SHULKER_BOX);
        shulkerBox.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(wood, commodity, unmapped)));

        // Expected: the box's own base weight (unmapped -> DEFAULT_UNIT_WEIGHT, 1.0)
        // plus the exact sum of its contents: 1.0 + 8.0 + 1.5 + 2.0 = 12.5
        double expected = BankItemWeight.DEFAULT_UNIT_WEIGHT + 8.0 + 1.5 + 2.0;
        double actual = BankItemWeight.resolve(shulkerBox);
        check(actual == expected,
                "shulker box weight was not contents-inclusive with the exact expected total: expected " + expected + " got " + actual);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void shulkerBoxWeightChangesWhenContentsChangeButOuterItemDoesNot(GameTestHelper helper) {
        ItemStack lightContents = new ItemStack(Items.DIAMOND, 1);
        ItemStack heavyWood = new ItemStack(ItemRegistry.WEIGHTED_WOOD_ITEM.get(), 5);
        WeightedWoodItem woodItem = (WeightedWoodItem) heavyWood.getItem();
        woodItem.setWeight(heavyWood, 10.0);

        ItemStack lightBox = new ItemStack(Items.SHULKER_BOX);
        lightBox.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(lightContents)));

        ItemStack heavyBox = new ItemStack(Items.SHULKER_BOX);
        heavyBox.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(heavyWood)));

        check(BankItemWeight.resolve(heavyBox) > BankItemWeight.resolve(lightBox),
                "a shulker box with heavier contents did not resolve to a greater weight than one with lighter contents");

        ItemStack emptyBox = new ItemStack(Items.SHULKER_BOX);
        check(BankItemWeight.resolve(emptyBox) == BankItemWeight.DEFAULT_UNIT_WEIGHT,
                "an empty shulker box's own base weight was not the default unit weight: " + BankItemWeight.resolve(emptyBox));
        helper.succeed();
    }

    // ---------- Bundles: the same recursive path as shulker boxes ----------
    // BundleContents is handled by the exact same branch shape in
    // BankItemWeight.resolveContainerContentsWeight as ItemContainerContents (both call
    // resolveAtDepth on every contained stack and sum the results) -- confirmed by reading
    // BankItemWeight.java directly, not assumed from the prior report. This test proves it
    // with the same rigor as the shulker box test: an exact expected total, including one
    // nested item carrying this mod's own custom data (a quality sword), not just vanilla
    // items.

    @GameTest(template = TEMPLATE)
    public static void bundleWeightIsContentsInclusiveWithAnExactExpectedTotal(GameTestHelper helper) {
        ItemStack sword = new ItemStack(WeaponRegistry.VIKING_SWORD.get());
        QualitySwordItem.setQuality(sword, 4);
        QualitySwordItem.setMaterial(sword, UOMetalToolMaterial.VALORITE); // custom data, unmapped weight -> 1 * 1.0 = 1.0

        ItemStack commodity = new ItemStack(ItemRegistry.RAW_PORK.get(), 4);
        WeightedCommodityItem.setWeight(commodity, 0.25); // 4 * 0.25 = 1.0

        ItemStack unmapped = new ItemStack(Items.DIAMOND, 3); // 3 * DEFAULT_UNIT_WEIGHT (1.0) = 3.0

        ItemStack bundle = new ItemStack(Items.BUNDLE);
        bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(sword, commodity, unmapped)));

        // Expected: the bundle's own base weight (unmapped -> DEFAULT_UNIT_WEIGHT, 1.0)
        // plus the exact sum of its contents: 1.0 + 1.0 + 1.0 + 3.0 = 6.0
        double expected = BankItemWeight.DEFAULT_UNIT_WEIGHT + 1.0 + 1.0 + 3.0;
        double actual = BankItemWeight.resolve(bundle);
        check(actual == expected,
                "bundle weight was not contents-inclusive with the exact expected total: expected " + expected + " got " + actual);
        helper.succeed();
    }

    // ---------- Guards: never negative, NaN, or infinite ----------

    @GameTest(template = TEMPLATE)
    public static void corruptWoodWeightTagIsClampedToTheDefaultRatherThanPropagated(GameTestHelper helper) {
        ItemStack nanStack = corruptWoodWeightStack(Double.NaN);
        ItemStack negativeStack = corruptWoodWeightStack(-500.0);
        ItemStack infiniteStack = corruptWoodWeightStack(Double.POSITIVE_INFINITY);

        for (ItemStack stack : List.of(nanStack, negativeStack, infiniteStack)) {
            double weight = BankItemWeight.resolve(stack);
            check(isFinitelyNonNegative(weight), "corrupt weight data was not sanitized: " + weight);
            check(weight == BankItemWeight.DEFAULT_UNIT_WEIGHT,
                    "corrupt weight data was not clamped to the default unit weight (fell back to a different value): " + weight);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void everyConstructedTestStackProducesAFiniteNonNegativeWeight(GameTestHelper helper) {
        List<ItemStack> stacks = List.of(
                new ItemStack(Items.DIAMOND, 64),
                new ItemStack(ItemRegistry.WEIGHTED_WOOD_ITEM.get(), 64),
                new ItemStack(ItemRegistry.RAW_PORK.get(), 64),
                new ItemStack(Items.SHULKER_BOX),
                corruptWoodWeightStack(Double.NaN)
        );
        for (ItemStack stack : stacks) {
            double weight = BankItemWeight.resolve(stack);
            check(isFinitelyNonNegative(weight), "a real ItemStack produced a bad weight: " + stack + " -> " + weight);
        }
        helper.succeed();
    }

    // ---------- Multi-item collection weight ----------

    @GameTest(template = TEMPLATE)
    public static void resolveTotalSumsAnArbitraryCollectionOfStacksCorrectly(GameTestHelper helper) {
        ItemStack wood = new ItemStack(ItemRegistry.WEIGHTED_WOOD_ITEM.get(), 2);
        WeightedWoodItem woodItem = (WeightedWoodItem) wood.getItem();
        woodItem.setWeight(wood, 4.0); // 8.0

        ItemStack commodity = new ItemStack(ItemRegistry.RAW_PORK.get(), 3);
        WeightedCommodityItem.setWeight(commodity, 0.5); // 1.5

        ItemStack unmapped = new ItemStack(Items.DIAMOND, 2); // 2.0

        double expected = 8.0 + 1.5 + 2.0;
        double actual = BankItemWeight.resolveTotal(List.of(wood, commodity, unmapped));
        check(actual == expected, "resolveTotal did not sum the collection correctly: expected " + expected + " got " + actual);
        helper.succeed();
    }

    // ---------- Nesting-depth guard (BankItemNesting.MAX_DEPTH) ----------
    // Unlike BankItemCodec/BankItemFingerprint, resolve() never throws here -- it must always
    // return a usable number for any real ItemStack. An over-limit structure is charged one
    // flat DEFAULT_UNIT_WEIGHT for whatever's beyond the cutoff instead of being walked
    // further, or recursed into unboundedly. These tests use a deliberately heavy item at the
    // very bottom of the chain (weight 1000.0) to prove the guard actually truncates -- if the
    // walk were still fully descending, the result would be dominated by that 1000.0; if it
    // is genuinely bounded, the result stays small regardless of what the hidden item weighs.

    @GameTest(template = TEMPLATE)
    public static void resolveIsFullyAccurateWhenNestedExactlyAtMaxDepth(GameTestHelper helper) {
        // MAX_DEPTH - 1 shulker boxes (each unmapped, base weight 1.0) wrapping one heavy
        // WeightedWoodItem at the deepest position. Expected: every level's own 1.0 base plus
        // the heavy item's real weight, fully accounted for -- (MAX_DEPTH - 1) * 1.0 + 1000.0.
        ItemStack chain = nestedShulkerBoxChainWithInnermost(BankItemNesting.MAX_DEPTH, heavyWoodStack(1000.0));
        double expected = (BankItemNesting.MAX_DEPTH - 1) * BankItemWeight.DEFAULT_UNIT_WEIGHT + 1000.0;
        double actual = BankItemWeight.resolve(chain);
        check(actual == expected,
                "a stack nested exactly at MAX_DEPTH was not fully and accurately resolved: expected " + expected + " got " + actual);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void resolveTruncatesRatherThanCountingTheRealWeightWhenNestedBeyondMaxDepth(GameTestHelper helper) {
        // One level deeper than the previous test: MAX_DEPTH shulker boxes wrapping the same
        // heavy item, now one level past what the walk is willing to descend into. If the
        // 1000.0 item were still being counted, the result would be far larger than this.
        ItemStack chain = nestedShulkerBoxChainWithInnermost(BankItemNesting.MAX_DEPTH + 1, heavyWoodStack(1000.0));
        // MAX_DEPTH box levels, each contributing its own 1.0 base weight, plus one extra flat
        // DEFAULT_UNIT_WEIGHT charged at the depth-MAX_DEPTH box for detecting (but not
        // descending into) the hidden heavy item beyond it: (MAX_DEPTH + 1) * 1.0.
        double expected = (BankItemNesting.MAX_DEPTH + 1) * BankItemWeight.DEFAULT_UNIT_WEIGHT;
        double actual = BankItemWeight.resolve(chain);
        check(actual == expected,
                "a stack nested one level beyond MAX_DEPTH was not truncated to the expected flat-charged total: expected "
                        + expected + " got " + actual);
        check(actual < 1000.0, "the hidden heavy item's real weight leaked through the depth guard: " + actual);
        helper.succeed();
    }

    // ---------- Helpers ----------

    private static ItemStack heavyWoodStack(double weight) {
        ItemStack stack = new ItemStack(ItemRegistry.WEIGHTED_WOOD_ITEM.get());
        ((WeightedWoodItem) stack.getItem()).setWeight(stack, weight);
        return stack;
    }

    /**
     * A chain of {@code depth} nested shulker boxes wrapping {@code innermost} at the bottom:
     * {@code depth == 1} returns {@code innermost} itself (no container), {@code depth == N}
     * wraps it in N - 1 shulker boxes. Built by direct component construction, not any normal
     * gameplay action, specifically to exercise the {@link BankItemNesting#MAX_DEPTH} guard.
     */
    private static ItemStack nestedShulkerBoxChainWithInnermost(int depth, ItemStack innermost) {
        ItemStack current = innermost;
        for (int level = 1; level < depth; level++) {
            ItemStack box = new ItemStack(Items.SHULKER_BOX);
            box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(current)));
            current = box;
        }
        return current;
    }

    private static ItemStack corruptWoodWeightStack(double corruptValue) {
        ItemStack stack = new ItemStack(ItemRegistry.WEIGHTED_WOOD_ITEM.get());
        CompoundTag tag = new CompoundTag();
        tag.put("WoodWeight", DoubleTag.valueOf(corruptValue));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static boolean isFinitelyNonNegative(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0.0;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
