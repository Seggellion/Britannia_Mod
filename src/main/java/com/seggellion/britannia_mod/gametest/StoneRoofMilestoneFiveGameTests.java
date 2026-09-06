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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class StoneRoofMilestoneFiveGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final ResourceLocation STONE_TEXTURE =
            ResourceLocation.withDefaultNamespace("block/stone");

    private StoneRoofMilestoneFiveGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void canonicalMaterialsRegisterRandomizeCyclePersistAndKeepAdaptiveData(
            GameTestHelper helper) {
        List<VariantTopOnlySlabBlock> roofs = List.of(
                BlockRegistry.SLATE_ROOF_FLAT.get(),
                BlockRegistry.SANDSTONE_ROOF.get(),
                BlockRegistry.LIMESTONE_ROOF.get());
        List<Item> items = List.of(
                ItemRegistry.SLATE_ROOF_FLAT_ITEM.get(),
                ItemRegistry.SANDSTONE_ROOF_ITEM.get(),
                ItemRegistry.LIMESTONE_ROOF_ITEM.get());
        List<String> ids = List.of(
                "slate_roof_flat",
                "sandstone_roof",
                "limestone_roof");
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        try {
            for (int material = 0; material < roofs.size(); material++) {
                VariantTopOnlySlabBlock roof = roofs.get(material);
                Item item = items.get(material);
                String id = ids.get(material);
                check(roof.getClass() == VariantTopOnlySlabBlock.class,
                        id + " does not use the shared variant roof class");
                check(roof.getStateDefinition().getPossibleStates().size() == 72,
                        id + " does not have the complete six-variation state architecture");
                check(ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, id).equals(
                                BuiltInRegistries.BLOCK.getKey(roof)),
                        id + " has the wrong block registry identity");
                check(Item.byBlock(roof) == item,
                        id + " does not resolve to its registered block item");

                BlockPos relative = new BlockPos(1 + material * 3, 2, 2);
                BlockPos absolute = helper.absolutePos(relative);
                ItemStack stack = new ItemStack(item);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                BlockHitResult hit = new BlockHitResult(
                        Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
                BlockPlaceContext context = new BlockPlaceContext(
                        player, InteractionHand.MAIN_HAND, stack, hit);
                Set<Integer> sampledVariations = new HashSet<>();
                for (int placement = 0; placement < 1_024; placement++) {
                    BlockState placed = roof.getStateForPlacement(context);
                    check(placed != null, id + " returned no placement state");
                    check(placed.getValue(TopOnlySlabBlock.TYPE) == SlabType.TOP,
                            id + " placement lost top-only geometry");
                    check(!placed.getValue(TopOnlySlabBlock.WATERLOGGED),
                            id + " air placement became waterlogged");
                    sampledVariations.add(
                            placed.getValue(VariantTopOnlySlabBlock.VARIATION));
                }
                check(sampledVariations.equals(Set.of(0, 1, 2, 3, 4, 5)),
                        id + " server placement did not sample all variations: "
                                + sampledVariations);

                BlockState saved = roof.defaultBlockState()
                        .setValue(VariantTopOnlySlabBlock.VARIATION, 5);
                CompoundTag savedTag = NbtUtils.writeBlockState(saved);
                BlockState decoded = NbtUtils.readBlockState(
                        BuiltInRegistries.BLOCK.asLookup(), savedTag);
                check(decoded.is(roof), id + " did not retain its identity after state decode");
                check(decoded.getValue(VariantTopOnlySlabBlock.VARIATION) == 5,
                        id + " did not persist variation five");

                helper.setBlock(relative, roof.defaultBlockState());
                check(helper.getBlockEntity(relative) instanceof AdaptiveRoofBlockEntity,
                        id + " did not create an adaptive block entity");
                AdaptiveRoofBlockEntity blockEntity =
                        (AdaptiveRoofBlockEntity) helper.getBlockEntity(relative);
                check(blockEntity.getType() == BlockEntityRegistry.ADAPTIVE_ROOF.get(),
                        id + " created the wrong block-entity type");
                blockEntity.setBottomTexture(STONE_TEXTURE);

                ItemStack decorator = new ItemStack(ItemRegistry.INTERIOR_DECORATOR_TOOL.get());
                player.setItemInHand(InteractionHand.MAIN_HAND, decorator);
                for (int cycle = 1; cycle <= 6; cycle++) {
                    BlockState before = helper.getBlockState(relative);
                    ItemInteractionResult result = before.useItemOn(
                            decorator,
                            helper.getLevel(),
                            player,
                            InteractionHand.MAIN_HAND,
                            hit);
                    BlockState after = helper.getBlockState(relative);
                    check(result.consumesAction(),
                            id + " decorator cycle " + cycle + " was not consumed");
                    check(after.getValue(VariantTopOnlySlabBlock.VARIATION) == cycle % 6,
                            id + " decorator cycle " + cycle + " selected variation "
                                    + after.getValue(VariantTopOnlySlabBlock.VARIATION));
                    check(after.getValue(TopOnlySlabBlock.TYPE) == SlabType.TOP,
                            id + " decorator cycle changed geometry");
                    check(after.getValue(TopOnlySlabBlock.SUPPORTS_LANTERN),
                            id + " decorator cycle lost acquired-bottom state");
                    check(helper.getBlockEntity(relative) instanceof AdaptiveRoofBlockEntity,
                            id + " decorator cycle removed its block entity");
                    check(STONE_TEXTURE.equals(
                                    ((AdaptiveRoofBlockEntity) helper.getBlockEntity(relative))
                                            .getBottomTexture()),
                            id + " decorator cycle lost adaptive texture data");
                }
            }
        } finally {
            player.server.getPlayerList().remove(player);
        }

        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
