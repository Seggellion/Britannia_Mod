package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.mining.MiningSkill;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.skill.SkillManager;
import com.seggellion.britannia_mod.wildresource.WildResourceEntries;
import com.seggellion.britannia_mod.wildresource.WildResourceEntry;
import com.seggellion.britannia_mod.wildresource.WildResourceNode;
import com.seggellion.britannia_mod.wildresource.WildResourceSavedData;
import com.seggellion.britannia_mod.wildresource.WildResourceSpawnScheduler;
import com.seggellion.britannia_mod.wildresource.WildResources;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class Patch18FullLoopGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final String NEXT_GATHER_TICK_TAG = "britannia_mod:dirt_gather_next_tick";
    private static final BlockPos DUNG_SUPPORT = new BlockPos(1, 1, 1);
    private static final BlockPos DUNG_TARGET = DUNG_SUPPORT.above();
    private static final BlockPos GATHER_TARGET = new BlockPos(3, 1, 1);
    private static final BlockPos WATER_SOURCE = new BlockPos(5, 1, 1);
    private static final BlockPos FARM = new BlockPos(3, 1, 4);

    private Patch18FullLoopGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void renewableIngredientsCompleteTheFiveHarvestLoopWithoutDuplication(
            GameTestHelper helper
    ) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ManagedResourceTestPlayers.survival(level, "patch18-full-loop");
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.getPersistentData().remove(NEXT_GATHER_TICK_TAG);

        BlockPos dungTarget = helper.absolutePos(DUNG_TARGET);
        WildResourceSavedData wildData = spawnTrackedDung(helper, dungTarget);
        moveNear(player, dungTarget);
        player.setItemInHand(
                InteractionHand.MAIN_HAND,
                ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 3)
        );
        check(player.gameMode.destroyBlock(dungTarget), "player could not harvest scheduled dung");
        check(wildData.nodeAt(dungTarget).isEmpty(), "dung harvest left its WildResource node");
        collectSingleDrop(level, player, dungTarget, ItemRegistry.DUNG.get(), "dung");

        BlockPos gatherTarget = helper.absolutePos(GATHER_TARGET);
        helper.setBlock(GATHER_TARGET, Blocks.DIRT);
        moveNear(player, gatherTarget);
        player.setItemInHand(
                InteractionHand.MAIN_HAND,
                ToolRegistry.createShovel(UOMetalToolMaterial.IRON, 3)
        );
        float miningBefore = SkillManager.getSkill(player, MiningSkill.SKILL_ID);
        PlayerInteractEvent.RightClickBlock gather = rightClick(player, gatherTarget);
        check(gather.isCanceled(), "custom-shovel dirt gather was not owned by the server handler");
        check(level.getBlockState(gatherTarget).is(Blocks.DIRT), "dirt gathering damaged terrain");
        check(player.getInventory().countItem(ItemRegistry.DIRT.get()) == 1,
                "one dirt gather did not grant exactly one custom dirt");
        check(SkillManager.getSkill(player, MiningSkill.SKILL_ID) == miningBefore,
                "dirt gathering changed Mining progression");
        rightClick(player, gatherTarget);
        check(player.getInventory().countItem(ItemRegistry.DIRT.get()) == 1,
                "spam click bypassed the player-scoped dirt cooldown");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.EMPTY_BOWL.get()));
        player.setItemInHand(InteractionHand.OFF_HAND,
                takeOne(player, ItemRegistry.DIRT.get(), "custom dirt"));
        check(useMain(player).getResult().consumesAction(), "empty bowl + dirt step failed");
        check(player.getMainHandItem().is(ItemRegistry.BOWL_OF_DIRT.get()),
                "dry dirt step produced the wrong bowl");

        player.setItemInHand(InteractionHand.OFF_HAND,
                takeOne(player, ItemRegistry.DUNG.get(), "dung"));
        check(useMain(player).getResult().consumesAction(), "bowl of dirt + dung step failed");
        check(player.getMainHandItem().is(ItemRegistry.BOWL_OF_FERTILE_DIRT.get()),
                "dry fertile step produced the wrong bowl");
        ItemStack fertileBowl = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        BlockPos waterSource = helper.absolutePos(WATER_SOURCE);
        level.setBlock(waterSource, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.EMPTY_BOWL.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        moveNear(player, waterSource);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(waterSource));
        check(useMain(player).getResult().consumesAction(), "source water did not fill the custom bowl");
        check(player.getMainHandItem().is(ItemRegistry.BOWL_OF_WATER.get()),
                "source water produced the wrong bowl");
        check(level.getFluidState(waterSource).is(FluidTags.WATER)
                        && level.getFluidState(waterSource).isSource(),
                "custom bowl filling consumed the water source");
        ItemStack waterBowl = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, fertileBowl);
        player.setItemInHand(InteractionHand.OFF_HAND, waterBowl);

        check(useMain(player).getResult().consumesAction(), "final fertile-dirt mix failed");
        check(player.getMainHandItem().is(ItemRegistry.FERTILIZED_DIRT.get())
                        && player.getMainHandItem().getCount() == 1,
                "final mix did not create one canonical fertilized dirt");
        check(player.getOffhandItem().is(ItemRegistry.EMPTY_BOWL.get())
                        && player.getOffhandItem().getCount() == 2,
                "final mix did not conserve both custom bowl containers");
        check(player.getInventory().countItem(Items.BOWL) == 0,
                "full chain created a vanilla bowl");

        BlockPos farmPos = helper.absolutePos(FARM);
        helper.setBlock(FARM, Blocks.DIRT);
        moveNear(player, farmPos);
        BlockHitResult farmHit = new BlockHitResult(
                Vec3.atCenterOf(farmPos), Direction.UP, farmPos, false);
        player.getMainHandItem().getItem().useOn(
                new UseOnContext(player, InteractionHand.MAIN_HAND, farmHit));
        check(level.getBlockState(farmPos).is(BlockRegistry.FARMING_BLOCK.get()),
                "canonical fertilized dirt did not create farm soil");
        assertRemaining(level, farmPos, 5);

        CropDefinition annual = CropRegistry.byId("vanilla_potato").orElseThrow();
        for (int expected = 4; expected >= 0; expected--) {
            mature(level, farmPos, annual);
            harvest(level, player, farmPos);
            if (expected == 3) {
                reloadFarmBlockEntity(level, farmPos);
            }
            if (expected > 0) {
                assertRemaining(level, farmPos, expected);
                if (expected == 4) {
                    harvest(level, player, farmPos);
                    assertRemaining(level, farmPos, 4);
                }
            } else {
                check(level.getBlockState(farmPos).is(Blocks.DIRT),
                        "fifth successful harvest did not exhaust fertility to dirt");
            }
        }

        player.server.getPlayerList().remove(player);
        helper.succeed();
    }

    private static WildResourceSavedData spawnTrackedDung(GameTestHelper helper, BlockPos target) {
        ServerLevel level = helper.getLevel();
        WildResourceSavedData data = WildResourceSavedData.get(level);
        data.removeNode(target);
        level.getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(3.0D))
                .forEach(ItemEntity::discard);
        helper.setBlock(DUNG_SUPPORT, Blocks.DIRT);
        helper.setBlock(DUNG_TARGET, Blocks.AIR);

        WildResourceEntry configured = WildResources.registry().find(WildResourceEntries.DUNG)
                .orElseThrow(() -> new GameTestAssertException("dung WildResource entry is missing"));
        WildResourceEntry fixedCandidate = new WildResourceEntry(
                configured.id(), configured.spawnWeight(), configured.maxNodesPerChunk(),
                configured.tuning(),
                (ignoredLevel, ignoredChunk, ignoredRandom) -> target,
                configured.environmentRule(), configured.substrateRule(), configured.biomeRule(),
                configured.nearbyRule(), configured.existingNodeValidator(),
                configured.placementStrategy(), configured.harvestStrategy(), configured.lootStrategy()
        );
        ChunkPos chunk = new ChunkPos(target);
        data.scheduleAttempt(chunk, configured.id(), level.getGameTime());
        WildResourceSpawnScheduler.AttemptResult result = WildResourceSpawnScheduler.attempt(
                fixedCandidate, chunk, level.getGameTime(), new RuntimeAttemptContext(level, data));
        check(result == WildResourceSpawnScheduler.AttemptResult.PLACED,
                "WildResource scheduler did not place dung: " + result);
        check(level.getBlockState(target).is(BlockRegistry.DUNG.get()),
                "scheduled dung block is absent");
        check(data.nodeAt(target).map(WildResourceNode::resourceId)
                        .filter(WildResourceEntries.DUNG::equals).isPresent(),
                "scheduled dung is absent from the WildResource ledger");
        return data;
    }

    private static void collectSingleDrop(
            ServerLevel level,
            ServerPlayer player,
            BlockPos position,
            net.minecraft.world.item.Item item,
            String label
    ) {
        List<ItemEntity> drops = level.getEntitiesOfClass(
                ItemEntity.class, new AABB(position).inflate(3.0D),
                entity -> entity.getItem().is(item));
        int count = drops.stream().map(ItemEntity::getItem).mapToInt(ItemStack::getCount).sum();
        check(count == 1, label + " harvest produced " + count + " items instead of one");
        check(player.getInventory().add(drops.getFirst().getItem().copy()),
                "player could not collect the " + label + " drop");
        drops.forEach(ItemEntity::discard);
    }

    private static ItemStack takeOne(
            ServerPlayer player,
            net.minecraft.world.item.Item item,
            String label
    ) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(item)) {
                return player.getInventory().removeItem(slot, 1);
            }
        }
        throw new GameTestAssertException("player did not own the required " + label);
    }

    private static PlayerInteractEvent.RightClickBlock rightClick(
            ServerPlayer player,
            BlockPos target
    ) {
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(target).add(0.0D, 0.5D, 0.0D),
                Direction.UP, target, false);
        return CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, target, hit);
    }

    private static InteractionResultHolder<ItemStack> useMain(ServerPlayer player) {
        return player.getMainHandItem().getItem().use(
                player.level(), player, InteractionHand.MAIN_HAND);
    }

    private static void mature(ServerLevel level, BlockPos position, CropDefinition crop) {
        FarmingBlockEntity farm = farm(level, position);
        farm.plantMigratedCrop(crop, "", crop.maxGrowthAge());
        level.setBlock(position,
                level.getBlockState(position).setValue(FarmingBlock.HAS_SEEDS, true), 3);
    }

    private static ItemInteractionResult harvest(
            ServerLevel level,
            ServerPlayer player,
            BlockPos position
    ) {
        FarmingBlockEntity farm = farm(level, position);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return FarmingBlock.tryHarvestCrop(
                farm, level.getBlockState(position), level, position, player,
                ItemStack.EMPTY, InteractionHand.MAIN_HAND, "patch18_full_loop");
    }

    private static void reloadFarmBlockEntity(ServerLevel level, BlockPos position) {
        FarmingBlockEntity current = farm(level, position);
        CompoundTag saved = current.saveWithoutMetadata(level.registryAccess());
        BlockState state = level.getBlockState(position);
        level.removeBlockEntity(position);
        FarmingBlockEntity reloaded = new FarmingBlockEntity(position, state);
        reloaded.loadWithComponents(saved, level.registryAccess());
        level.setBlockEntity(reloaded);
    }

    private static FarmingBlockEntity farm(ServerLevel level, BlockPos position) {
        if (level.getBlockEntity(position) instanceof FarmingBlockEntity farm) {
            return farm;
        }
        throw new GameTestAssertException("missing FarmingBlockEntity at " + position.toShortString());
    }

    private static void assertRemaining(ServerLevel level, BlockPos position, int expected) {
        int actual = farm(level, position).getRemainingFertileHarvests();
        check(actual == expected,
                "expected " + expected + " fertile harvests but found " + actual);
    }

    private static void moveNear(ServerPlayer player, BlockPos target) {
        player.setPos(target.getX() + 0.5D, target.getY(), target.getZ() + 2.5D);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private record RuntimeAttemptContext(
            ServerLevel level,
            WildResourceSavedData data
    ) implements WildResourceSpawnScheduler.AttemptContext {
        @Override public RandomSource random() { return level.random; }
        @Override public boolean isChunkLoaded(ChunkPos chunk) {
            return level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null;
        }
        @Override public boolean isPositionLoaded(BlockPos position) {
            return isChunkLoaded(new ChunkPos(position));
        }
        @Override public long nextAttempt(ChunkPos chunk, WildResourceEntry entry) {
            return data.nextAttempt(chunk, entry.id());
        }
        @Override public int countInChunk(ChunkPos chunk, WildResourceEntry entry) {
            return data.countInChunk(chunk, entry.id());
        }
        @Override public boolean spacingAllows(WildResourceEntry entry, BlockPos position) {
            return data.nodeAt(position).isEmpty()
                    && !data.hasSameTypeWithin(
                            entry.id(), position, entry.tuning().minimumSameTypeSpacing());
        }
        @Override public boolean recordPlacement(
                WildResourceEntry entry,
                BlockPos position,
                long gameTime
        ) {
            return data.registerNode(new WildResourceNode(entry.id(), position, gameTime));
        }
        @Override public void schedule(ChunkPos chunk, WildResourceEntry entry, long nextAttempt) {
            data.scheduleAttempt(chunk, entry.id(), nextAttempt);
        }
    }
}
