package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.TopOnlySlabBlock;
import com.seggellion.britannia_mod.block.VariantTopOnlySlabBlock;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.CreativeTabRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class StoneRoofCompatibilityGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private StoneRoofCompatibilityGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void frozenSlateIdsKeepHistoricalTypesStatesAndNoMigration(
            GameTestHelper helper) {
        Block base = BlockRegistry.SLATE_ROOF_BASE.get();
        StairBlock stair = BlockRegistry.SLATE_ROOF.get();
        VariantTopOnlySlabBlock canonical = BlockRegistry.SLATE_ROOF_FLAT.get();
        TopOnlySlabBlock firstNumbered = BlockRegistry.SLATE_ROOF_1_FLAT.get();
        TopOnlySlabBlock secondNumbered = BlockRegistry.SLATE_ROOF_2_FLAT.get();

        assertBlockIdentity(base, "slate_roof_base");
        assertBlockIdentity(stair, "slate_roof");
        assertBlockIdentity(canonical, "slate_roof_flat");
        assertBlockIdentity(firstNumbered, "slate_roof_1_flat");
        assertBlockIdentity(secondNumbered, "slate_roof_2_flat");
        check(base.getClass() == Block.class, "slate_roof_base changed block type");
        check(stair.getClass() == StairBlock.class, "slate_roof changed stair type");
        check(canonical.getClass() == VariantTopOnlySlabBlock.class,
                "slate_roof_flat changed canonical block type");
        check(firstNumbered.getClass() == TopOnlySlabBlock.class,
                "slate_roof_1_flat changed compatibility block type");
        check(secondNumbered.getClass() == TopOnlySlabBlock.class,
                "slate_roof_2_flat changed compatibility block type");

        check(stair.getStateDefinition().getProperties().containsAll(List.of(
                        StairBlock.FACING, StairBlock.HALF,
                        StairBlock.SHAPE, StairBlock.WATERLOGGED)),
                "historical stair lost a vanilla stair property");
        check(stair.getStateDefinition().getPossibleStates().size() == 80,
                "historical stair no longer has 80 vanilla stair states");
        check(firstNumbered.getStateDefinition().getPossibleStates().size() == 12,
                "first numbered flat changed its 12-state contract");
        check(secondNumbered.getStateDefinition().getPossibleStates().size() == 12,
                "second numbered flat changed its 12-state contract");

        BlockState stairState = stair.defaultBlockState()
                .setValue(StairBlock.FACING, net.minecraft.core.Direction.WEST)
                .setValue(StairBlock.HALF, Half.TOP)
                .setValue(StairBlock.SHAPE, StairsShape.OUTER_LEFT)
                .setValue(StairBlock.WATERLOGGED, true);
        BlockState canonicalState = canonical.defaultBlockState()
                .setValue(VariantTopOnlySlabBlock.VARIATION, 5)
                .setValue(TopOnlySlabBlock.WATERLOGGED, true)
                .setValue(TopOnlySlabBlock.SUPPORTS_LANTERN, true);
        BlockState firstNumberedState = firstNumbered.defaultBlockState()
                .setValue(TopOnlySlabBlock.TYPE, SlabType.DOUBLE)
                .setValue(TopOnlySlabBlock.WATERLOGGED, true)
                .setValue(TopOnlySlabBlock.SUPPORTS_LANTERN, true);
        BlockState secondNumberedState = secondNumbered.defaultBlockState()
                .setValue(TopOnlySlabBlock.TYPE, SlabType.BOTTOM)
                .setValue(TopOnlySlabBlock.WATERLOGGED, true)
                .setValue(TopOnlySlabBlock.SUPPORTS_LANTERN, true);

        assertStateRoundTrip(helper, base.defaultBlockState(), "slate_roof_base");
        assertStateRoundTrip(helper, stairState, "slate_roof");
        assertStateRoundTrip(helper, canonicalState, "slate_roof_flat");
        assertStateRoundTrip(helper, firstNumberedState, "slate_roof_1_flat");
        assertStateRoundTrip(helper, secondNumberedState, "slate_roof_2_flat");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void historicalSlateItemsSurviveSavedInventoryRoundTrip(
            GameTestHelper helper) {
        List<Item> expectedItems = List.of(
                ItemRegistry.SLATE_ROOF_ITEM.get(),
                ItemRegistry.SLATE_ROOF_FLAT_ITEM.get(),
                ItemRegistry.SLATE_ROOF_1_FLAT_ITEM.get(),
                ItemRegistry.SLATE_ROOF_2_FLAT_ITEM.get());
        List<Block> expectedBlocks = List.of(
                BlockRegistry.SLATE_ROOF.get(),
                BlockRegistry.SLATE_ROOF_FLAT.get(),
                BlockRegistry.SLATE_ROOF_1_FLAT.get(),
                BlockRegistry.SLATE_ROOF_2_FLAT.get());
        NonNullList<ItemStack> saved = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int slot = 0; slot < expectedItems.size(); slot++) {
            Item item = expectedItems.get(slot);
            Block block = expectedBlocks.get(slot);
            check(Item.byBlock(block) == item, "legacy block item identity changed at slot " + slot);
            saved.set(slot, new ItemStack(item, slot + 1));
        }

        CompoundTag inventoryTag = new CompoundTag();
        ContainerHelper.saveAllItems(inventoryTag, saved, helper.getLevel().registryAccess());
        NonNullList<ItemStack> restored = NonNullList.withSize(9, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(
                inventoryTag, restored, helper.getLevel().registryAccess());

        for (int slot = 0; slot < expectedItems.size(); slot++) {
            ItemStack stack = restored.get(slot);
            Item expected = expectedItems.get(slot);
            check(stack.is(expected), "saved inventory remapped legacy slot " + slot);
            check(stack.getCount() == slot + 1,
                    "saved inventory changed count in legacy slot " + slot);
            check(BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(
                            BuiltInRegistries.ITEM.getKey(expected)),
                    "saved inventory changed item registry identity in slot " + slot);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void normalCreativeTabShowsOnlyCanonicalSlateRoof(GameTestHelper helper) {
        CreativeModeTab tab = CreativeTabRegistry.CREATIVE_WORLD_TAB.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                helper.getLevel().enabledFeatures(), false,
                helper.getLevel().registryAccess()));

        check(count(tab, ItemRegistry.SLATE_ROOF_FLAT_ITEM.get()) == 1,
                "normal creative tab does not contain exactly one canonical Slate Roof");
        check(count(tab, ItemRegistry.SLATE_ROOF_ITEM.get()) == 0,
                "historical slate stair is exposed as a normal creative choice");
        check(count(tab, ItemRegistry.SLATE_ROOF_1_FLAT_ITEM.get()) == 0,
                "first numbered slate flat is exposed as a normal creative choice");
        check(count(tab, ItemRegistry.SLATE_ROOF_2_FLAT_ITEM.get()) == 0,
                "second numbered slate flat is exposed as a normal creative choice");
        helper.succeed();
    }

    private static void assertBlockIdentity(Block block, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, path);
        check(BuiltInRegistries.BLOCK.getKey(block).equals(id),
                path + " has the wrong registry key");
        check(BuiltInRegistries.BLOCK.get(id) == block,
                path + " does not resolve to its registered block");
    }

    private static void assertStateRoundTrip(
            GameTestHelper helper, BlockState expected, String id) {
        BlockState restored = NbtUtils.readBlockState(
                helper.getLevel().registryAccess().lookupOrThrow(
                        net.minecraft.core.registries.Registries.BLOCK),
                NbtUtils.writeBlockState(expected));
        check(restored.equals(expected), id + " changed during saved-state decode: " + restored);
    }

    private static long count(CreativeModeTab tab, Item item) {
        return tab.getDisplayItems().stream().filter(stack -> stack.is(item)).count();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
