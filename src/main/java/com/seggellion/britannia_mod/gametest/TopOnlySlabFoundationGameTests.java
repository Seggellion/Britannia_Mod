package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.TopOnlySlabBlock;
import com.seggellion.britannia_mod.block.VariantTopOnlySlabBlock;
import com.seggellion.britannia_mod.block.entity.AdaptiveRoofBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class TopOnlySlabFoundationGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final ResourceLocation STONE_TEXTURE =
            ResourceLocation.withDefaultNamespace("block/stone");

    private TopOnlySlabFoundationGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void placementInWaterCreatesAnAuthoritativeWaterloggedTopRoof(
            GameTestHelper helper) {
        BlockPos target = new BlockPos(2, 2, 2);
        BlockPos absolute = helper.absolutePos(target);
        TopOnlySlabBlock roof = BlockRegistry.TILE_ROOF_FLAT.get();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        try {
            helper.setBlock(target, Blocks.WATER.defaultBlockState());
            ItemStack stack = new ItemStack(ItemRegistry.TILE_ROOF_FLAT_ITEM.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
            BlockPlaceContext context = new BlockPlaceContext(
                    player, InteractionHand.MAIN_HAND, stack, hit);

            check(context.getClickedPos().equals(absolute),
                    "the water cell was not the placement target");
            BlockState placed = roof.getStateForPlacement(context);
            check(placed != null, "top-only roof returned no placement state");
            check(placed.getValue(TopOnlySlabBlock.TYPE) == SlabType.TOP,
                    "water placement did not retain top-only geometry");
            check(placed.getValue(TopOnlySlabBlock.WATERLOGGED),
                    "water placement discarded the target fluid");
            check(!placed.getValue(TopOnlySlabBlock.SUPPORTS_LANTERN),
                    "fresh placement incorrectly claimed an acquired bottom");

            helper.setBlock(target, placed);
            check(helper.getBlockState(target).getFluidState().is(FluidTags.WATER),
                    "placed waterlogged roof does not expose water fluid state");
        } finally {
            disconnect(player);
        }

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void existingRoofFamiliesSynchronizeAcquiredBottomGeometryAndData(
            GameTestHelper helper) {
        List<TopOnlySlabBlock> roofs = List.of(
                BlockRegistry.TILE_ROOF_FLAT.get(),
                BlockRegistry.CEDAR_ROOF_FLAT.get(),
                BlockRegistry.THATCH_ROOF_FLAT.get());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        try {
            for (int index = 0; index < roofs.size(); index++) {
                TopOnlySlabBlock roof = roofs.get(index);
                BlockPos relative = new BlockPos(1 + index * 2, 2, 2);
                BlockPos absolute = helper.absolutePos(relative);
                helper.setBlock(relative, roof.defaultBlockState());

                check(helper.getBlockEntity(relative) instanceof AdaptiveRoofBlockEntity,
                        "existing roof family did not create its adaptive block entity");
                AdaptiveRoofBlockEntity blockEntity =
                        (AdaptiveRoofBlockEntity) helper.getBlockEntity(relative);
                blockEntity.setBottomTexture(STONE_TEXTURE);

                BlockState acquired = helper.getBlockState(relative);
                check(acquired.getValue(TopOnlySlabBlock.SUPPORTS_LANTERN),
                        "acquired bottom was not mirrored into synchronized block state");
                check(STONE_TEXTURE.equals(blockEntity.getBottomTexture()),
                        "acquired bottom texture was not retained");
                assertAllShapes(acquired, helper, absolute, 0.0D, "acquired");

                BlockHitResult hit = new BlockHitResult(
                        Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
                player.setItemInHand(
                        InteractionHand.MAIN_HAND,
                        new ItemStack(ItemRegistry.INTERIOR_DECORATOR_TOOL.get()));
                InteractionResult decoratorResult = acquired.useWithoutItem(
                        helper.getLevel(), player, hit);
                check(decoratorResult == InteractionResult.PASS,
                        "base fallback must leave decorator handling to the item");
                check(STONE_TEXTURE.equals(blockEntity.getBottomTexture()),
                        "base fallback changed data before the decorator item could run");

                player.setItemInHand(
                        InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_AXE));
                InteractionResult clearResult = acquired.useWithoutItem(
                        helper.getLevel(), player, hit);
                check(clearResult == InteractionResult.SUCCESS,
                        "wooden axe did not consume the acquired-bottom clear action");
                BlockState cleared = helper.getBlockState(relative);
                check(!cleared.getValue(TopOnlySlabBlock.SUPPORTS_LANTERN),
                        "clearing adaptive data left synchronized support enabled");
                check(blockEntity.getBottomTexture() == null,
                        "clearing adaptive data retained a bottom texture");
                assertAllShapes(cleared, helper, absolute, 0.5D, "cleared");
            }
        } finally {
            disconnect(player);
        }

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void canonicalSlateCyclesSixPersistentStatesAndLegacyIdsRemainValid(
            GameTestHelper helper) {
        check(BlockRegistry.SLATE_ROOF_FLAT.get().getClass()
                        == VariantTopOnlySlabBlock.class,
                "canonical slate roof is not the reusable six-variation block");
        check(BlockRegistry.SLATE_ROOF.get().getClass() == StairBlock.class,
                "historical slate stair changed block type");
        check(BlockRegistry.SLATE_ROOF_1_FLAT.get().getClass() == TopOnlySlabBlock.class,
                "first legacy slate flat changed block type");
        check(BlockRegistry.SLATE_ROOF_2_FLAT.get().getClass() == TopOnlySlabBlock.class,
                "second legacy slate flat changed block type");
        check(Item.byBlock(BlockRegistry.SLATE_ROOF.get()) == ItemRegistry.SLATE_ROOF_ITEM.get(),
                "historical slate stair item registration disappeared");
        check(Item.byBlock(BlockRegistry.SLATE_ROOF_1_FLAT.get())
                        == ItemRegistry.SLATE_ROOF_1_FLAT_ITEM.get(),
                "first legacy slate flat item registration disappeared");
        check(Item.byBlock(BlockRegistry.SLATE_ROOF_2_FLAT.get())
                        == ItemRegistry.SLATE_ROOF_2_FLAT_ITEM.get(),
                "second legacy slate flat item registration disappeared");

        VariantTopOnlySlabBlock slate = BlockRegistry.SLATE_ROOF_FLAT.get();
        BlockPos target = new BlockPos(2, 2, 2);
        BlockPos absolute = helper.absolutePos(target);
        BlockState defaultState = slate.defaultBlockState();
        check(defaultState.getValue(VariantTopOnlySlabBlock.VARIATION) == 0,
                "canonical slate does not default legacy states to variation zero");
        CompoundTag legacyStateTag = new CompoundTag();
        legacyStateTag.putString("Name", "britannia_mod:slate_roof_flat");
        CompoundTag legacyProperties = new CompoundTag();
        legacyProperties.putString("type", "top");
        legacyProperties.putString("waterlogged", "false");
        legacyProperties.putString("supports_lantern", "false");
        legacyStateTag.put("Properties", legacyProperties);
        BlockState decodedLegacyState = NbtUtils.readBlockState(
                BuiltInRegistries.BLOCK.asLookup(), legacyStateTag);
        check(decodedLegacyState.is(slate),
                "a saved canonical slate state without variation no longer resolves");
        check(decodedLegacyState.getValue(VariantTopOnlySlabBlock.VARIATION) == 0,
                "a saved canonical slate state without variation did not default to zero");
        helper.setBlock(target, defaultState);
        check(helper.getBlockEntity(target) instanceof AdaptiveRoofBlockEntity,
                "canonical slate did not retain adaptive block-entity support");
        AdaptiveRoofBlockEntity blockEntity =
                (AdaptiveRoofBlockEntity) helper.getBlockEntity(target);
        blockEntity.setBottomTexture(STONE_TEXTURE);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack decorator = new ItemStack(ItemRegistry.INTERIOR_DECORATOR_TOOL.get());
            player.setItemInHand(InteractionHand.OFF_HAND, decorator);
            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(absolute), Direction.UP, absolute, false);

            for (int cycle = 1; cycle <= 6; cycle++) {
                BlockState before = helper.getBlockState(target);
                ItemInteractionResult result = before.useItemOn(
                        decorator,
                        helper.getLevel(),
                        player,
                        InteractionHand.OFF_HAND,
                        hit);
                BlockState after = helper.getBlockState(target);

                check(result.consumesAction(),
                        "decorator did not consume slate cycle " + cycle);
                check(after.getValue(VariantTopOnlySlabBlock.VARIATION) == cycle % 6,
                        "slate cycle " + cycle + " selected variation "
                                + after.getValue(VariantTopOnlySlabBlock.VARIATION));
                check(after.getValue(TopOnlySlabBlock.TYPE) == SlabType.TOP,
                        "slate cycle changed top-only geometry");
                check(!after.getValue(TopOnlySlabBlock.WATERLOGGED),
                        "slate cycle changed waterlogging");
                check(after.getValue(TopOnlySlabBlock.SUPPORTS_LANTERN),
                        "slate cycle lost acquired-bottom geometry");
                check(helper.getBlockEntity(target) instanceof AdaptiveRoofBlockEntity,
                        "slate cycle removed its adaptive block entity");
                check(STONE_TEXTURE.equals(
                                ((AdaptiveRoofBlockEntity) helper.getBlockEntity(target))
                                        .getBottomTexture()),
                        "slate cycle lost acquired-bottom texture data");
            }
        } finally {
            disconnect(player);
        }

        helper.succeed();
    }

    private static void assertAllShapes(
            BlockState state,
            GameTestHelper helper,
            BlockPos absolute,
            double expectedMinY,
            String phase) {
        assertShape(
                state.getShape(helper.getLevel(), absolute),
                expectedMinY,
                phase + " selection");
        assertShape(
                state.getCollisionShape(helper.getLevel(), absolute),
                expectedMinY,
                phase + " collision");
        assertShape(
                state.getBlockSupportShape(helper.getLevel(), absolute),
                expectedMinY,
                phase + " support");
        assertShape(
                state.getOcclusionShape(helper.getLevel(), absolute),
                expectedMinY,
                phase + " occlusion");
    }

    private static void assertShape(VoxelShape shape, double expectedMinY, String label) {
        AABB bounds = shape.bounds();
        check(bounds.minX == 0.0D && bounds.minY == expectedMinY && bounds.minZ == 0.0D
                        && bounds.maxX == 1.0D && bounds.maxY == 1.0D && bounds.maxZ == 1.0D,
                label + " shape was " + bounds);
    }

    private static void disconnect(ServerPlayer player) {
        player.server.getPlayerList().remove(player);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
