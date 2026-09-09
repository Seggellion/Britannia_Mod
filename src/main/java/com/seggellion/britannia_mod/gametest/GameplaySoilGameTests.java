package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.entity.*;
import com.seggellion.britannia_mod.farming.*;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.structure.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class GameplaySoilGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos P = new BlockPos(3, 2, 3);

    @GameTest(template=TEMPLATE)
    public static void exactFarmlandRestorationAtDeadlineAndNoEntitlement(GameTestHelper h) {
        long now = h.getLevel().getGameTime();
        for (int moisture = 0; moisture <= 7; moisture++) {
            BlockState prior = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, moisture);
            for (long elapsed : new long[]{
                    FarmingBlockEntity.COMMUNITY_SEED_WINDOW_TICKS - 1,
                    FarmingBlockEntity.COMMUNITY_SEED_WINDOW_TICKS,
                    FarmingBlockEntity.COMMUNITY_SEED_WINDOW_TICKS + 1}) {
                var soil = fertile(h);
                soil.beginFertilizerApplication(prior, 0, UUID.randomUUID(), now - elapsed);
                soil.consumeSuccessfulFertileHarvest();
                boolean expired = soil.expireEmptySoil(h.getLevel());
                h.assertTrue(expired == (elapsed >= FarmingBlockEntity.COMMUNITY_SEED_WINDOW_TICKS),
                        "wrong deadline boundary");
                if (expired) {
                    h.assertTrue(h.getBlockState(P).equals(prior), "prior farmland moisture changed");
                    h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(P)) == null, "fertilizer entitlement survived expiry");
                } else h.assertTrue(soil.getRemainingFertileHarvests() == 4, "unexpired uses changed");
            }
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void communityPreparationBudgetPausesAndCannotBeRefreshed(GameTestHelper h) {
        var player = ManagedResourceTestPlayers.survival(h.getLevel(), "soil-test");
        long now = h.getLevel().getGameTime();
        h.setBlock(P, BlockRegistry.COMMUNITY_HOED_FARM_BLOCK.get());
        var prepared = (CommunityFarmBlockEntity) h.getBlockEntity(P);
        prepared.resumePreparation(now, 9);
        equipFertilizer(player);
        h.assertTrue(FertilizedSoilService.apply(h.getLevel(), h.absolutePos(P), player, player.getMainHandItem()).consumesAction(), "community fertilization denied");
        var soil = (FarmingBlockEntity) h.getBlockEntity(P);
        h.assertTrue(soil.getSeedableUntilGameTime() == now + FarmingBlockEntity.COMMUNITY_SEED_WINDOW_TICKS
                && soil.getHydration() == 1, "application deadline/moisture drifted");
        CompoundTag saved = soil.saveWithoutMetadata(h.getLevel().registryAccess());
        saved.putLong("SeedableUntilGameTime", now);
        soil.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(soil.expireEmptySoil(h.getLevel()), "community did not expire");
        h.assertTrue(h.getBlockState(P).is(BlockRegistry.COMMUNITY_HOED_FARM_BLOCK.get()), "restored unprepared soil");
        prepared = (CommunityFarmBlockEntity) h.getBlockEntity(P);
        h.assertTrue(prepared.getPreparedExpiresAt() == now + 9, "preparation budget was refreshed or not paused");
        equipFertilizer(player);
        FertilizedSoilService.apply(h.getLevel(), h.absolutePos(P), player, player.getMainHandItem());
        saved = ((FarmingBlockEntity) h.getBlockEntity(P)).saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(saved.getLong("PausedPreparationTicks") == 9, "re-fertilization refreshed hoe time");
        player.server.getPlayerList().remove(player);
        h.succeed();
    }

    @GameTest(template=TEMPLATE, timeoutTicks=30)
    public static void loadedTickerReconcilesOverdueSavedSoilWithoutRandomTicks(GameTestHelper h) {
        var soil = fertile(h);
        BlockState prior = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 6);
        long now = h.getLevel().getGameTime();
        soil.beginFertilizerApplication(prior, 0, UUID.randomUUID(), now);
        CompoundTag saved = soil.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(saved.getLong("SeedableUntilGameTime")
                == now + FarmingBlockEntity.COMMUNITY_SEED_WINDOW_TICKS, "wrong saved clock");
        // Simulate an overdue persisted deadline; exercise the registered BE ticker, not randomTick.
        saved.putLong("SeedableUntilGameTime", now);
        h.getLevel().removeBlockEntity(h.absolutePos(P));
        var loaded = new FarmingBlockEntity(h.absolutePos(P), h.getBlockState(P));
        loaded.loadWithComponents(saved, h.getLevel().registryAccess());
        h.getLevel().setBlockEntity(loaded);
        h.runAfterDelay(2, () -> {
            h.assertTrue(h.getBlockState(P).equals(prior), "loaded deterministic ticker failed to reconcile");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE)
    public static void plantingAndFlowerForwardStateCancelDeadlineButRollbackPreservesIt(GameTestHelper h) {
        var soil = fertile(h);
        long now = h.getLevel().getGameTime();
        soil.beginFertilizerApplication(BlockRegistry.COMMUNITY_HOED_FARM_BLOCK.get().defaultBlockState(), 13, null, now);
        soil.consumeSuccessfulFertileHarvest();
        var snapshot = soil.exportFlowerConversionSnapshot(h.getBlockState(P));
        h.assertTrue(snapshot.soil().communitySeedableUntilGameTime() == 0, "flower retained empty deadline");
        soil.restoreFlowerConversionSnapshot(snapshot);
        h.assertTrue(soil.getSeedableUntilGameTime() == now + FarmingBlockEntity.COMMUNITY_SEED_WINDOW_TICKS
                && soil.getRemainingFertileHarvests() == 4, "rollback changed deadline/uses");
        soil.restoreUprootedCommunityFlowerSoil(snapshot.soil());
        h.assertTrue(soil.getSeedableUntilGameTime() == 0 && soil.getRemainingFertileHarvests() == 4, "uproot resurrected timer or reset uses");
        UUID owner = UUID.randomUUID();
        soil.beginFertilizerApplication(Blocks.FARMLAND.defaultBlockState(), 0, owner,
                now - FarmingBlockEntity.COMMUNITY_SEED_WINDOW_TICKS + 1);
        var privateSnapshot = soil.exportFlowerConversionSnapshot(h.getBlockState(P));
        var persistedSoil = FlowerSoilSnapshot.fromTag(privateSnapshot.soil().withHydration(2).toTag());
        soil.restoreUprootedFlowerSoil(persistedSoil);
        h.assertTrue(owner.equals(soil.getOwner()), "flower conversion or care lost private soil owner");
        soil.restoreFlowerConversionSnapshot(privateSnapshot);
        h.assertTrue(owner.equals(soil.getOwner()), "flower rollback lost soil owner");
        soil.plant(CropRegistry.byId("carrot").orElseThrow());
        h.assertTrue(soil.getSeedableUntilGameTime() == 0 && !soil.expireEmptySoil(h.getLevel()), "planting did not cancel private deadline");
        h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void ownerDimensionBoundsAndTargetWhitelistAreAuthoritative(GameTestHelper h) {
        var player = ManagedResourceTestPlayers.survival(h.getLevel(), "soil-test");
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(P);
        AABB box = new AABB(pos).inflate(0, 10, 0);
        var house = new StructureRecord(player.getUUID(), new AABB(pos), box, UUID.randomUUID(), "small", "SMALL_BRICK", null, 0, level.dimension());
        StructureRegionManager.registerStructure(house);
        try {
            for (GameType mode : new GameType[]{GameType.SURVIVAL, GameType.ADVENTURE, GameType.CREATIVE}) {
                player.setGameMode(mode);
                for (var block : new net.minecraft.world.level.block.Block[]{Blocks.DIRT, Blocks.COARSE_DIRT, BlockRegistry.COMMUNITY_FARM_BLOCK.get(), BlockRegistry.FARMING_BLOCK.get(), BlockRegistry.HOUSE_FARM_PLOT.get()}) {
                    h.setBlock(P, block); equipFertilizer(player);
                    h.assertTrue(FertilizedSoilService.apply(level, pos, player, player.getMainHandItem()) == ItemInteractionResult.FAIL, "invalid soil admitted in " + mode);
                    h.assertTrue(player.getMainHandItem().getCount() == 2 && h.getBlockState(P).is(block), "invalid target mutated");
                }
                h.setBlock(P, Blocks.FARMLAND); h.setBlock(P.above(), Blocks.WHEAT);
                h.assertTrue(!FertilizedSoilService.eligible(level, pos, player), "occupied farmland admitted");
                h.setBlock(P.above(), Blocks.AIR);
                h.assertTrue(FertilizedSoilService.eligible(level, pos, player), "owned empty farmland denied");
            }
            h.assertTrue(!HouseBuildRights.ownsHouseAt(Level.NETHER, pos, player.getUUID()), "cross-dimension owner accepted");
            h.assertTrue(!HouseBuildRights.ownsHouseAt(level, pos.east(), player.getUUID()), "outside full-box accepted");
            h.assertTrue(HouseBuildRights.ownsHouseAt(level, pos.below(9), player.getUUID()), "basement boundary lost");
            h.assertTrue(!HouseBuildRights.ownsHouseAt(level, pos, UUID.randomUUID()), "foreign owner accepted");
        } finally { StructureRegionManager.unregisterStructure(house); }
        player.setGameMode(GameType.SURVIVAL);
        h.setBlock(P, Blocks.FARMLAND); equipFertilizer(player);
        h.assertTrue(FertilizedSoilService.apply(level, pos, player, player.getMainHandItem()) == ItemInteractionResult.FAIL, "missing rehydration granted ownership");
        h.assertTrue(player.getMainHandItem().getCount() == 2, "stale ownership consumed input");
        player.server.getPlayerList().remove(player);
        h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void bowlsUseActualHandOnceAndFullOrProtectedSoilCostsNothing(GameTestHelper h) {
        var player = ManagedResourceTestPlayers.survival(h.getLevel(), "soil-test");
        var pos = h.absolutePos(P);
        player.setPos(pos.getX()+1, pos.getY()+1, pos.getZ());
        player.setGameMode(GameType.SURVIVAL);
        for (InteractionHand hand : InteractionHand.values()) {
            var soil = fertile(h); soil.setHydration(0);
            player.getInventory().clearContent();
            player.setItemInHand(hand, new ItemStack(ItemRegistry.BOWL_OF_WATER.get(), 2));
            useBothPhases(h, player);
            h.assertTrue(soil.getHydration() == 1, "bowl hydrated twice or not at all for " + hand);
            h.assertTrue(player.getItemInHand(hand).getCount() == 1 && player.getInventory().countItem(ItemRegistry.EMPTY_BOWL.get()) == 1, "wrong bowl/remainder charge");
            soil.setHydration(5); useBothPhases(h, player);
            h.assertTrue(player.getItemInHand(hand).getCount() == 1, "full soil consumed bowl");
            soil.setHydration(0); soil.setOwner(UUID.randomUUID()); useBothPhases(h, player);
            h.assertTrue(soil.getHydration() == 0 && player.getItemInHand(hand).getCount() == 1, "foreign soil watered");
        }
        player.server.getPlayerList().remove(player);
        h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void bowlsHydrateCultivatedFlowersAndProtectAdministratorOrigin(GameTestHelper h) {
        var player = ManagedResourceTestPlayers.survival(h.getLevel(), "soil-test"); player.setGameMode(GameType.SURVIVAL);
        var registry = FlowerRegistry.initial(); var definition = registry.byId(FlowerRegistry.POPPY).orElseThrow();
        var color = registry.color(definition.fallbackColorId()).orElseThrow().color();
        for (InteractionHand hand : InteractionHand.values()) {
            h.setBlock(P, Blocks.AIR); h.setBlock(P, BlockRegistry.FLOWER_BLOCK.get());
            var flower = (FlowerBlockEntity) h.getBlockEntity(P);
            var context = new FlowerPlantingContext(FlowerSoilSnapshot.privateSoil(0,0,0,0,0), new FlowerRegionProvenance("Test", FarmingClimate.TEMPERATE), FarmingClimate.TEMPERATE, h.absolutePos(P).getY(), FlowerPlantingOrigin.PLAYER, true);
            h.assertTrue(flower.initialize(FlowerPersistentState.newlyPlanted(definition, color, context, Optional.of(player.getUUID()), registry)), "flower init failed");
            player.getInventory().clearContent(); player.setItemInHand(hand, new ItemStack(ItemRegistry.BOWL_OF_WATER.get()));
            useBothPhases(h, player);
            h.assertTrue(flower.hydration() == 1 && player.getItemInHand(hand).is(ItemRegistry.EMPTY_BOWL.get()), "flower bowl/remainder mismatch");
        }
        var flower = (FlowerBlockEntity) h.getBlockEntity(P);
        var saved = flower.saveWithoutMetadata(h.getLevel().registryAccess());
        saved.getCompound("FlowerState").putString("PlantingOrigin", "ADMIN");
        saved.getCompound("FlowerState").putBoolean("Protected", true);
        flower.loadWithComponents(saved, h.getLevel().registryAccess());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.BOWL_OF_WATER.get()));
        useBothPhases(h, player);
        h.assertTrue(flower.hydration() == 1 && player.getMainHandItem().is(ItemRegistry.BOWL_OF_WATER.get()), "protected flower consumed bowl");
        player.server.getPlayerList().remove(player);
        h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void fullInventoryBowlRemainderDropsExactlyOnce(GameTestHelper h) {
        var player = ManagedResourceTestPlayers.survival(h.getLevel(), "soil-test"); player.setGameMode(GameType.SURVIVAL);
        var pos = h.absolutePos(P); player.setPos(pos.getX()+1.5, pos.getY()+1, pos.getZ()+0.5);
        var soil = fertile(h); soil.setHydration(0);
        for (int slot=0; slot<player.getInventory().items.size(); slot++) player.getInventory().items.set(slot,new ItemStack(Items.COBBLESTONE,64));
        player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(ItemRegistry.BOWL_OF_WATER.get(),2));
        useBothPhases(h,player);
        int dropped=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(pos).inflate(4)).stream()
                .filter(entity -> entity.getItem().is(ItemRegistry.EMPTY_BOWL.get())).mapToInt(entity -> entity.getItem().getCount()).sum();
        h.assertTrue(soil.getHydration()==1 && player.getOffhandItem().getCount()==1 && dropped==1, "full inventory lost or duplicated remainder");
        player.server.getPlayerList().remove(player); h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void legacyUntrackedPrivateSoilGetsNoInventedDeadlineOrUses(GameTestHelper h) {
        var soil = fertile(h);
        soil.loadWithComponents(new CompoundTag(), h.getLevel().registryAccess());
        h.assertTrue(soil.getRemainingFertileHarvests() == -1 && soil.getSeedableUntilGameTime() == 0, "legacy data gained entitlement or timer");
        h.assertTrue(!soil.expireEmptySoil(h.getLevel()), "untimed legacy soil expired");
        h.succeed();
    }

    static StructureRecord registerOwnedFarmland(GameTestHelper h, ServerPlayer player, BlockPos relative) {
        h.setBlock(relative, Blocks.FARMLAND);
        h.setBlock(relative.above(), Blocks.AIR);
        AABB box = new AABB(h.absolutePos(relative));
        var house = new StructureRecord(player.getUUID(), box, box, UUID.randomUUID(), "small", "SMALL_BRICK", null, 0, h.getLevel().dimension());
        StructureRegionManager.registerStructure(house);
        return house;
    }

    private static FarmingBlockEntity fertile(GameTestHelper h) {
        h.setBlock(P, Blocks.AIR); h.setBlock(P, BlockRegistry.FARMING_BLOCK.get());
        return (FarmingBlockEntity) h.getBlockEntity(P);
    }
    private static void equipFertilizer(ServerPlayer player) { player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ItemRegistry.FERTILIZED_DIRT.get(),2)); }
    private static void useBothPhases(GameTestHelper h, ServerPlayer player) {
        BlockPos pos=h.absolutePos(P); var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
        // Exercise the event dispatch used by the server, including a redundant other-hand callback.
        player.gameMode.useItemOn(player,h.getLevel(),player.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
        player.gameMode.useItemOn(player,h.getLevel(),player.getOffhandItem(),InteractionHand.OFF_HAND,hit);
    }
}
