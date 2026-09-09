package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.CustomSandstoneBrickBlock;
import com.seggellion.britannia_mod.block.TopOnlySlabBlock;
import com.seggellion.britannia_mod.block.VariantTopOnlySlabBlock;
import com.seggellion.britannia_mod.block.entity.AdaptiveRoofBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the real server dispatcher, including block-before-item and sneak bypass. */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class RoofBottomDecorationGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos TARGET = new BlockPos(2, 3, 2);
    private static final ResourceLocation STONE = ResourceLocation.withDefaultNamespace("block/stone");
    private static final ResourceLocation SANDSTONE = ResourceLocation.fromNamespaceAndPath(
            BritanniaMod.MODID, "block/structure/sandstone/custom_sandstone_brick_0");

    @GameTest(template = TEMPLATE)
    public static void mainHandClearsEveryRoofStateExactlyOnceAndRepeatedClearIsFree(GameTestHelper helper) {
        exerciseClear(helper, Items.STONE, STONE);
    }

    @GameTest(template = TEMPLATE)
    public static void sandstoneBrickAppliesPersistsSynchronizesAndClearsAcrossEveryRoof(GameTestHelper helper) {
        exerciseClear(helper, ItemRegistry.CUSTOM_SANDSTONE_BRICK_ITEM.get(), SANDSTONE);
    }

    private static void exerciseClear(GameTestHelper helper, Item material, ResourceLocation texture) {
        ServerPlayer player = player(helper);
        try {
            for (TopOnlySlabBlock roof : roofs()) {
                for (BlockState initial : roof.getStateDefinition().getPossibleStates()) {
                    if (initial.getValue(TopOnlySlabBlock.SUPPORTS_LANTERN)) continue;
                    helper.setBlock(TARGET, initial);
                    CountingRoofEntity entity = new CountingRoofEntity(helper.absolutePos(TARGET), initial);
                    helper.getLevel().setBlockEntity(entity);
                    entity.getPersistentData().putString("unrelated", "keep me");
                    helper.getLevel().getChunkAt(entity.getBlockPos()).setUnsaved(false);
                    player.setShiftKeyDown(false);
                    ItemStack materialStack = new ItemStack(material, 8);
                    player.setItemInHand(InteractionHand.MAIN_HAND, materialStack);
                    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

                    InteractionResult applied = click(helper, player, InteractionHand.MAIN_HAND);
                    check(texture.equals(entity.getBottomTexture()), roof + " stored wrong bottom identifier");
                    check(applied == InteractionResult.SUCCESS, roof + " rejected bottom material: " + applied);
                    check(entity.mutations == 1, "material application must mutate once");
                    check(materialStack.getCount() == 8, "texture application consumed material");
                    BlockState decorated = initial.setValue(TopOnlySlabBlock.SUPPORTS_LANTERN, true);
                    check(helper.getBlockState(TARGET).equals(decorated), "application changed unrelated state");
                    check(helper.getLevel().getChunkAt(entity.getBlockPos()).isUnsaved(), "application not marked dirty");
                    assertRoundTrip(helper, entity, texture);

                    ItemStack tool = new ItemStack(ItemRegistry.INTERIOR_DECORATOR_TOOL.get());
                    ItemStack toolBefore = tool.copy();
                    player.setItemInHand(InteractionHand.MAIN_HAND, tool);
                    // Both tools present: the main-hand clear must consume the gesture before
                    // Minecraft can try the offhand cycle. No extra hand callback is fabricated.
                    player.setItemInHand(InteractionHand.OFF_HAND, tool.copy());
                    check(click(helper, player, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                            roof + " did not consume main-hand clear");
                    check(entity.getBottomTexture() == null, roof + " decorator retained bottom texture");
                    check(helper.getBlockEntity(TARGET) == entity, "clear replaced the block entity");
                    check(helper.getBlockState(TARGET).equals(initial), "clear changed unrelated roof properties");
                    check(entity.mutations == 2, "clear did not mutate exactly once");
                    check(ItemStack.matches(toolBefore, player.getMainHandItem()), "clear charged tool resources");
                    assertRoundTrip(helper, entity, null);

                    helper.getLevel().getChunkAt(entity.getBlockPos()).setUnsaved(false);
                    check(click(helper, player, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                            "already-clear roof did not consume its safe no-op");
                    check(entity.mutations == 2, "already-clear roof wrote data again");
                    check(!helper.getLevel().getChunkAt(entity.getBlockPos()).isUnsaved(), "no-op dirtied chunk");
                    check(helper.getBlockState(TARGET).equals(initial), "no-op cycled or changed roof");

                    // Sneaking skips block use; the item handler must still clear correctly.
                    entity.setBottomTexture(texture);
                    player.setShiftKeyDown(true);
                    check(click(helper, player, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME,
                            "sneak bypass missed the item clear handler");
                    check(entity.getBottomTexture() == null && entity.mutations == 4, "sneak clear mutated incorrectly");
                    check(helper.getBlockState(TARGET).equals(initial), "sneak clear changed roof state");
                    check("keep me".equals(entity.getPersistentData().getString("unrelated")), "lost unrelated entity data");
                    check(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            new net.minecraft.world.phys.AABB(entity.getBlockPos()).inflate(0.4)).isEmpty(),
                            "decoration created item drops");
                }
            }
        } finally {
            player.server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void offhandCyclesOnlyTheTopOnceWithAndWithoutSneak(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            for (TopOnlySlabBlock roof : roofs()) {
                for (boolean sneaking : List.of(false, true)) {
                    BlockState initial = roof.defaultBlockState().setValue(TopOnlySlabBlock.WATERLOGGED, true);
                    helper.setBlock(TARGET, initial);
                    AdaptiveRoofBlockEntity entity = (AdaptiveRoofBlockEntity) helper.getBlockEntity(TARGET);
                    entity.setBottomTexture(SANDSTONE);
                    player.setShiftKeyDown(sneaking);
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ItemRegistry.INTERIOR_DECORATOR_TOOL.get()));
                    for (int cycle = 1; cycle <= 6; cycle++) {
                        BlockState before = helper.getBlockState(TARGET);
                        check(click(helper, player, InteractionHand.MAIN_HAND) == InteractionResult.PASS,
                                "empty main hand blocked offhand cycle");
                        check(click(helper, player, InteractionHand.OFF_HAND) == InteractionResult.CONSUME,
                                "offhand roof action was not consumed");
                        BlockState expected = roof instanceof VariantTopOnlySlabBlock
                                ? before.setValue(VariantTopOnlySlabBlock.VARIATION, cycle % 6) : before;
                        check(helper.getBlockState(TARGET).equals(expected), "offhand changed wrong property or cycled twice");
                        check(helper.getBlockEntity(TARGET) == entity && SANDSTONE.equals(entity.getBottomTexture()),
                                "offhand cycle replaced entity or cleared the independent bottom");
                    }
                }
            }
        } finally {
            player.server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void nonRoofCyclingRotationAndSpectatorRejectionRemainIntact(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.INTERIOR_DECORATOR_TOOL.get()));
            BlockState brick = BlockRegistry.CUSTOM_SANDSTONE_BRICK.get().defaultBlockState();
            helper.setBlock(TARGET, brick);
            check(click(helper, player, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS, "brick cycle result changed");
            check(helper.getBlockState(TARGET).equals(brick.setValue(CustomSandstoneBrickBlock.TEXTURE_VARIANT, 1)),
                    "non-roof decorator cycling changed");
            BlockState stairs = BlockRegistry.SLATE_ROOF.get().defaultBlockState();
            helper.setBlock(TARGET, stairs);
            check(click(helper, player, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME, "stair rotation result changed");
            check(helper.getBlockState(TARGET).equals(stairs.setValue(net.minecraft.world.level.block.StairBlock.FACING,
                    stairs.getValue(net.minecraft.world.level.block.StairBlock.FACING).getClockWise())), "legacy stair rotation changed");

            helper.setBlock(TARGET, BlockRegistry.SLATE_ROOF_FLAT.get().defaultBlockState());
            AdaptiveRoofBlockEntity entity = (AdaptiveRoofBlockEntity) helper.getBlockEntity(TARGET);
            entity.setBottomTexture(STONE);
            player.setGameMode(GameType.SPECTATOR);
            check(click(helper, player, InteractionHand.MAIN_HAND) == InteractionResult.PASS, "spectator clear was accepted");
            check(STONE.equals(entity.getBottomTexture()), "spectator cleared data");
        } finally {
            player.server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    private static void assertRoundTrip(GameTestHelper helper, AdaptiveRoofBlockEntity entity, ResourceLocation expected) {
        try {
            CompoundTag saved = new CompoundTag();
            saved.put("state", NbtUtils.writeBlockState(entity.getBlockState()));
            CompoundTag disk = entity.saveWithoutMetadata(helper.getLevel().registryAccess());
            check(disk.contains("BottomTexture") == (expected != null), "disk clear must omit BottomTexture");
            saved.put("entity", disk);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.write(saved, new DataOutputStream(bytes));
            CompoundTag loaded = NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), loaded.getCompound("state"));
            check(state.equals(entity.getBlockState()), "serialized block state changed");
            AdaptiveRoofBlockEntity copy = new AdaptiveRoofBlockEntity(entity.getBlockPos(), state);
            copy.loadWithComponents(loaded.getCompound("entity"), helper.getLevel().registryAccess());
            check(java.util.Objects.equals(expected, copy.getBottomTexture()), "disk round trip changed bottom");
            check("keep me".equals(copy.getPersistentData().getString("unrelated")), "disk round trip lost unrelated data");

            var packet = entity.getUpdatePacket();
            check(packet != null && !packet.getTag().isEmpty(), "no usable synchronization packet");
            check(packet.getTag().getString("BottomTexture").equals(expected == null ? "minecraft:block/air" : expected.toString()),
                    "packet contains wrong texture or clear sentinel");
            copy.setBottomTexture(STONE); // Prove the clear packet replaces stale observing-client data.
            copy.handleUpdateTag(packet.getTag(), helper.getLevel().registryAccess());
            check(java.util.Objects.equals(expected, copy.getBottomTexture()), "packet retained stale bottom");
            check(java.util.Objects.equals(expected,
                    copy.getModelData().get(AdaptiveRoofBlockEntity.BOTTOM_TEXTURE_MODEL_PROPERTY)), "model data disagrees with packet");
        } catch (java.io.IOException exception) {
            throw new GameTestAssertException("NBT round trip failed: " + exception);
        }
    }

    private static List<TopOnlySlabBlock> roofs() {
        return List.of(BlockRegistry.TILE_ROOF_FLAT.get(), BlockRegistry.CEDAR_ROOF_FLAT.get(),
                BlockRegistry.THATCH_ROOF_FLAT.get(), BlockRegistry.SLATE_ROOF_1_FLAT.get(),
                BlockRegistry.SLATE_ROOF_2_FLAT.get(), BlockRegistry.SLATE_ROOF_FLAT.get(),
                BlockRegistry.SANDSTONE_ROOF.get(), BlockRegistry.LIMESTONE_ROOF.get());
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(TARGET);
        player.absMoveTo(pos.getX() + 1.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }

    private static InteractionResult click(GameTestHelper helper, ServerPlayer player, InteractionHand hand) {
        BlockPos pos = helper.absolutePos(TARGET);
        return player.gameMode.useItemOn(player, helper.getLevel(), player.getItemInHand(hand), hand,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.DOWN, pos, false));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static final class CountingRoofEntity extends AdaptiveRoofBlockEntity {
        private int mutations;

        private CountingRoofEntity(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        @Override
        public void setBottomTexture(ResourceLocation texture) {
            mutations++;
            super.setBottomTexture(texture);
        }
    }
}
