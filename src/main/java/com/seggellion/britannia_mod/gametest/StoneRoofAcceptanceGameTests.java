package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.TopOnlySlabBlock;
import com.seggellion.britannia_mod.block.VariantTopOnlySlabBlock;
import com.seggellion.britannia_mod.block.entity.AdaptiveRoofBlockEntity;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
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
public final class StoneRoofAcceptanceGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final ResourceLocation STONE_TEXTURE =
            ResourceLocation.withDefaultNamespace("block/stone");

    private StoneRoofAcceptanceGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void registeredBlockItemsActuallyPlaceAndSampleEveryVariation(
            GameTestHelper helper) {
        List<VariantTopOnlySlabBlock> roofs = List.of(
                BlockRegistry.SLATE_ROOF_FLAT.get(),
                BlockRegistry.SANDSTONE_ROOF.get(),
                BlockRegistry.LIMESTONE_ROOF.get());
        List<BlockItem> items = List.of(
                ItemRegistry.SLATE_ROOF_FLAT_ITEM.get(),
                ItemRegistry.SANDSTONE_ROOF_ITEM.get(),
                ItemRegistry.LIMESTONE_ROOF_ITEM.get());
        List<String> ids = List.of("slate_roof_flat", "sandstone_roof", "limestone_roof");
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        List<Bounds> referenceGeometry = null;

        try {
            for (int material = 0; material < roofs.size(); material++) {
                VariantTopOnlySlabBlock roof = roofs.get(material);
                BlockItem item = items.get(material);
                String id = ids.get(material);
                BlockPos relative = new BlockPos(2 + material * 3, 2, 2);
                BlockPos absolute = helper.absolutePos(relative);
                player.setPos(absolute.getX() + 0.5D, absolute.getY() + 3.0D,
                        absolute.getZ() + 0.5D);
                BlockHitResult hit = new BlockHitResult(
                        Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
                Set<Integer> sampled = new HashSet<>();

                for (int attempt = 0; attempt < 256; attempt++) {
                    helper.setBlock(relative, Blocks.WATER.defaultBlockState());
                    ItemStack stack = new ItemStack(item);
                    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                    BlockPlaceContext context = new BlockPlaceContext(
                            player, InteractionHand.MAIN_HAND, stack, hit);
                    InteractionResult result = item.place(context);
                    check(result.consumesAction(), id + " BlockItem placement was not consumed");

                    BlockState placed = helper.getBlockState(relative);
                    check(placed.is(roof), id + " BlockItem placed " + placed.getBlock());
                    check(placed.getValue(TopOnlySlabBlock.TYPE) == SlabType.TOP,
                            id + " actual placement lost top-only type");
                    check(placed.getValue(TopOnlySlabBlock.WATERLOGGED),
                            id + " actual placement discarded source water");
                    check(placed.getFluidState().is(FluidTags.WATER),
                            id + " waterlogged placement exposes no water fluid state");
                    int variation = placed.getValue(VariantTopOnlySlabBlock.VARIATION);
                    check(variation >= 0 && variation < 6,
                            id + " actual placement selected illegal variation " + variation);
                    sampled.add(variation);
                    check(helper.getBlockEntity(relative) instanceof AdaptiveRoofBlockEntity,
                            id + " actual BlockItem placement created no adaptive block entity");
                }

                check(sampled.equals(Set.of(0, 1, 2, 3, 4, 5)),
                        id + " actual placements did not reach all variations: " + sampled);
                BlockState finalState = helper.getBlockState(relative);
                List<Bounds> geometry = geometry(finalState, helper, absolute);
                if (referenceGeometry == null) {
                    referenceGeometry = geometry;
                } else {
                    check(referenceGeometry.equals(geometry),
                            id + " geometry differs from canonical Slate Roof");
                }
            }
        } finally {
            player.server.getPlayerList().remove(player);
        }

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void canonicalStatesAdaptiveDataAndClientTagsSurviveReloadRoundTrips(
            GameTestHelper helper) {
        List<VariantTopOnlySlabBlock> roofs = List.of(
                BlockRegistry.SLATE_ROOF_FLAT.get(),
                BlockRegistry.SANDSTONE_ROOF.get(),
                BlockRegistry.LIMESTONE_ROOF.get());
        List<String> ids = List.of("slate_roof_flat", "sandstone_roof", "limestone_roof");

        for (int material = 0; material < roofs.size(); material++) {
            VariantTopOnlySlabBlock roof = roofs.get(material);
            String id = ids.get(material);
            BlockPos relative = new BlockPos(2 + material * 3, 2, 5);
            BlockPos absolute = helper.absolutePos(relative);
            BlockState initial = roof.defaultBlockState()
                    .setValue(VariantTopOnlySlabBlock.VARIATION, 3 + material)
                    .setValue(TopOnlySlabBlock.WATERLOGGED, true);
            helper.setBlock(relative, initial);
            check(helper.getBlockEntity(relative) instanceof AdaptiveRoofBlockEntity,
                    id + " reload setup created no adaptive block entity");
            AdaptiveRoofBlockEntity entity =
                    (AdaptiveRoofBlockEntity) helper.getBlockEntity(relative);
            check(entity.getType() == BlockEntityRegistry.ADAPTIVE_ROOF.get(),
                    id + " reload setup created wrong block-entity type");

            entity.setBottomTexture(STONE_TEXTURE);
            BlockState synchronizedState = helper.getBlockState(relative);
            check(synchronizedState.getValue(TopOnlySlabBlock.SUPPORTS_LANTERN),
                    id + " server update did not synchronize acquired-bottom state");
            check(synchronizedState.getValue(TopOnlySlabBlock.WATERLOGGED),
                    id + " synchronized update lost waterlogging");
            check(synchronizedState.getValue(VariantTopOnlySlabBlock.VARIATION) == 3 + material,
                    id + " synchronized update lost variation");
            check(STONE_TEXTURE.equals(entity.getBottomTexture()),
                    id + " synchronized update lost BottomTexture");

            BlockState restoredState = NbtUtils.readBlockState(
                    BuiltInRegistries.BLOCK.asLookup(),
                    NbtUtils.writeBlockState(synchronizedState));
            check(restoredState.equals(synchronizedState),
                    id + " state changed during disk round trip: " + restoredState);

            CompoundTag disk = entity.saveWithoutMetadata(helper.getLevel().registryAccess());
            AdaptiveRoofBlockEntity diskCopy =
                    new AdaptiveRoofBlockEntity(absolute, restoredState);
            diskCopy.loadWithComponents(disk, helper.getLevel().registryAccess());
            check(STONE_TEXTURE.equals(diskCopy.getBottomTexture()),
                    id + " BottomTexture changed during disk reload");

            CompoundTag clientTag = entity.getUpdateTag(helper.getLevel().registryAccess());
            check(clientTag.contains("BottomTexture"),
                    id + " client update tag omitted BottomTexture");
            AdaptiveRoofBlockEntity clientCopy =
                    new AdaptiveRoofBlockEntity(absolute, synchronizedState);
            clientCopy.loadWithComponents(clientTag, helper.getLevel().registryAccess());
            check(STONE_TEXTURE.equals(clientCopy.getBottomTexture()),
                    id + " client update tag did not reproduce BottomTexture");
            check(entity.getUpdatePacket() != null,
                    id + " provides no client block-entity update packet");
        }

        helper.succeed();
    }

    private static List<Bounds> geometry(
            BlockState state, GameTestHelper helper, BlockPos absolute) {
        return List.of(
                bounds(state.getShape(helper.getLevel(), absolute)),
                bounds(state.getCollisionShape(helper.getLevel(), absolute)),
                bounds(state.getBlockSupportShape(helper.getLevel(), absolute)),
                bounds(state.getOcclusionShape(helper.getLevel(), absolute)));
    }

    private static Bounds bounds(VoxelShape shape) {
        AABB box = shape.bounds();
        return new Bounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private record Bounds(
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
    }
}
