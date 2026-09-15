package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.*;
import com.seggellion.britannia_mod.block.entity.*;
import com.seggellion.britannia_mod.farming.*;
import com.seggellion.britannia_mod.registry.*;
import com.seggellion.britannia_mod.skill.SkillManager;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class GameplayHarvestGameTests {
    private static final String TEMPLATE="service_npc_spawn_test_empty";
    private static final BlockPos P=new BlockPos(3,2,3);

    @GameTest(template=TEMPLATE,timeoutTicks=200)
    public static void everySoilSpeciesRefusesBelowThresholdAndChargesSuccessOrDestructiveFailureOnce(GameTestHelper h) {
        var player=player(h,"AllCropHarvests"); var level=h.getLevel(); var pos=h.absolutePos(P);
        var events=new AtomicInteger(); java.util.function.Consumer<FarmingHarvestCommittedEvent> listener=e->{if(e.player()==player) events.incrementAndGet();};
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            for(var crop:CropRegistry.all()) if(!crop.treeCrop()) for(boolean success:new boolean[]{false,true}) {
                var soil=soil(h,crop,player); ItemStack tool=tool(crop); player.setItemInHand(InteractionHand.MAIN_HAND,tool);
                SkillManager.applyConfirmedValue(player,"farming",crop.minimumFarmingSkill()-.01f);
                var beforeEvents=events.get();
                harvest(h,player,soil,tool);
                h.assertTrue(soil.isMature() && soil.getRemainingFertileHarvests()==5 && drops(h).isEmpty(),"below-threshold mutation for "+crop.id());
                SkillManager.applyConfirmedValue(player,"farming",crop.minimumFarmingSkill()); seedOutcome(level,success);
                harvest(h,player,soil,tool);
                h.assertTrue(soil.getRemainingFertileHarvests()==4 && !soil.isMature(),"wrong lifecycle/fertility for "+crop.id());
                h.assertTrue(soil.hasCrop()==crop.persistsAfterHarvest(),"annual/perennial mismatch for "+crop.id());
                h.assertTrue(events.get()==beforeEvents+(success?1:0),"wrong committed success signal for "+crop.id());
                if(success) h.assertTrue(drops(h).stream().anyMatch(e->e.getItem().is(crop.harvestItem().get())),"missing successful produce for "+crop.id());
                else h.assertTrue(drops(h).isEmpty(),"failure emitted produce/byproducts for "+crop.id());
                if(!tool.isEmpty()) h.assertTrue(tool.getDamageValue()==1,"missing normal tool cost for "+crop.id());
                int dropCount=drops(h).size(); harvest(h,player,soil,tool);
                h.assertTrue(soil.getRemainingFertileHarvests()==4 && drops(h).size()==dropCount,"repeat charged/yielded twice for "+crop.id());
                drops(h).forEach(ItemEntity::discard);
            }
        } finally { NeoForge.EVENT_BUS.unregister(listener); player.server.getPlayerList().remove(player); }
        h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void sharedBoundaryDrawsOnceAfterReadinessAndRejectsStaleOrReentrantRoot(GameTestHelper h) {
        var player=player(h,"HarvestBoundary"); var crop=CropRegistry.byId("grapes").orElseThrow(); var soil=soil(h,crop,player); var pos=h.absolutePos(P);
        var rolls=new AtomicInteger(); var practices=new AtomicInteger(); var commits=new AtomicInteger();
        java.util.function.BooleanSupplier live=()->soil.isMature() && h.getLevel().getBlockEntity(pos)==soil;
        try {
            // No skill value has been confirmed for this player's UUID.
            var unavailable=FarmingHarvestService.execute(h.getLevel(),pos,player,crop.seedItem().get(),live,o->{commits.incrementAndGet();return true;},practices::incrementAndGet,()->{rolls.incrementAndGet();return 0;});
            h.assertTrue(unavailable==FarmingHarvestService.Outcome.REFUSED && rolls.get()==0 && commits.get()==0 && practices.get()==0,"unavailable skill reached outcome");
            SkillManager.applyConfirmedValue(player,"farming",80);
            var failure=FarmingHarvestService.execute(h.getLevel(),pos,player,crop.seedItem().get(),live,o->{
                h.assertTrue(o==FarmingHarvestService.Outcome.FAILURE,"wrong injected outcome");
                var nested=FarmingHarvestService.execute(h.getLevel(),pos,player,crop.seedItem().get(),live,ignored->{throw new AssertionError("reentered commit");},()->{},()->{throw new AssertionError("reentered roll");});
                h.assertTrue(nested==FarmingHarvestService.Outcome.REFUSED,"root lock missing"); commits.incrementAndGet();return true;
            },practices::incrementAndGet,()->{rolls.incrementAndGet();return .75;});
            h.assertTrue(failure==FarmingHarvestService.Outcome.FAILURE && rolls.get()==1 && practices.get()==1 && commits.get()==1,"failure did not draw/practice once");
            var stale=FarmingHarvestService.execute(h.getLevel(),pos,player,crop.seedItem().get(),live,o->{throw new AssertionError("stale root committed");},()->{throw new AssertionError("stale root practiced");},()->{soil.clearCrop();return 0;});
            h.assertTrue(stale==FarmingHarvestService.Outcome.REFUSED,"post-roll root was not revalidated");
            player.setGameMode(GameType.CREATIVE);
            var free=FarmingHarvestService.execute(h.getLevel(),pos,player,crop.seedItem().get(),()->true,o->{h.assertTrue(o.free() && o.successful(),"Creative not free");return true;},()->{throw new AssertionError("Creative practiced");},()->{throw new AssertionError("Creative rolled");});
            h.assertTrue(free==FarmingHarvestService.Outcome.FREE_SUCCESS,"Creative bypass refused");
        } finally { player.server.getPlayerList().remove(player); }
        h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void wrongToolImmaturityForeignOwnerAndCreativeCostsUseLiveSoil(GameTestHelper h) {
        var player=player(h,"HarvestRights"); var stranger=player(h,"HarvestStranger"); var crop=CropRegistry.byId("green_onion").orElseThrow();
        try {
            var soil=soil(h,crop,player); var tool=new ItemStack(ItemRegistry.SCISSORS.get());
            SkillManager.applyConfirmedValue(player,"farming",100); SkillManager.applyConfirmedValue(stranger,"farming",100);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK)); harvest(h,player,soil,player.getMainHandItem());
            stranger.setItemInHand(InteractionHand.MAIN_HAND,tool); harvest(h,stranger,soil,tool);
            h.assertTrue(soil.isMature() && soil.getRemainingFertileHarvests()==5 && drops(h).isEmpty(),"wrong tool or owner harvested");
            player.setItemInHand(InteractionHand.MAIN_HAND,tool); player.setGameMode(GameType.CREATIVE);
            harvest(h,player,soil,tool);
            h.assertTrue(!soil.isMature() && soil.getRemainingFertileHarvests()==5 && tool.getDamageValue()==0 && !drops(h).isEmpty(),"Creative harvest charged or failed");
        } finally { player.server.getPlayerList().remove(player); stranger.server.getPlayerList().remove(stranger); }
        h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void bothGrapePlantingEntrypointsRequireEightyAndRetainVariety(GameTestHelper h) {
        var player=player(h,"GrapePlanting"); var level=h.getLevel(); var pos=h.absolutePos(P);
        try {
            for(boolean direct:new boolean[]{false,true}) for(float skill:new float[]{79,80}) {
                h.setBlock(P,Blocks.AIR);h.setBlock(P,BlockRegistry.FARMING_BLOCK.get());
                var seeds=new ItemStack(ItemRegistry.GRAPE_SEEDS.get(),2); player.setItemInHand(InteractionHand.MAIN_HAND,seeds);
                SkillManager.applyConfirmedValue(player,"farming",skill);
                var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
                if(direct) seeds.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
                else player.gameMode.useItemOn(player,level,seeds,InteractionHand.MAIN_HAND,hit);
                var soil=(FarmingBlockEntity)level.getBlockEntity(pos);
                h.assertTrue(soil.hasCrop()==(skill>=80) && seeds.getCount()==(skill>=80?1:2),"grape planting gate/cost bypass");
                if(skill>=80) h.assertTrue(com.seggellion.britannia_mod.item.GrapeSeedsItem.getVariety(seeds).equals(soil.getStoredSeed()),"grape variety lost");
            }
        } finally { player.server.getPlayerList().remove(player); }
        h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void allSevenFlowersGateResetAndConsumeFailureOrSuccessUse(GameTestHelper h) {
        var player=player(h,"FlowerHarvests"); var level=h.getLevel(); var pos=h.absolutePos(P); var registry=FlowerRegistry.initial();
        try {
            for(var definition:registry.definitions().values()) for(boolean success:new boolean[]{false,true}) {
                h.setBlock(P,Blocks.AIR);h.setBlock(P,BlockRegistry.FLOWER_BLOCK.get());
                var flower=(FlowerBlockEntity)h.getBlockEntity(P);
                var soil=FlowerSoilSnapshot.privateSoil(0,0,0,0,0).withOwner(player.getUUID());
                var soilTag=soil.toTag(); soilTag.putInt("RemainingFertileHarvests",5); soil=FlowerSoilSnapshot.fromTag(soilTag);
                var context=new FlowerPlantingContext(soil,new FlowerRegionProvenance("Test",FarmingClimate.TEMPERATE),FarmingClimate.TEMPERATE,pos.getY(),FlowerPlantingOrigin.PLAYER,true);
                var persistent=FlowerPersistentState.newlyPlanted(definition,registry.color(definition.fallbackColorId()).orElseThrow().color(),context,Optional.of(player.getUUID()),registry);
                h.assertTrue(flower.initialize(persistent),"flower init");
                var saved=flower.saveWithoutMetadata(level.registryAccess()); saved.getCompound("FlowerState").putInt("GrowthStage",definition.naturalMaximumStage());flower.loadWithComponents(saved,level.registryAccess());
                player.getInventory().clearContent(); var scissors=new ItemStack(ItemRegistry.SCISSORS.get());player.setItemInHand(InteractionHand.MAIN_HAND,scissors);
                var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
                SkillManager.applyConfirmedValue(player,"farming",definition.minimumFarmingSkill()-.01f);
                player.gameMode.useItemOn(player,level,scissors,InteractionHand.MAIN_HAND,hit);
                h.assertTrue(flower.flowerState().orElseThrow().growthStage()==definition.naturalMaximumStage() && scissors.getDamageValue()==0,"below-threshold flower mutated");
                SkillManager.applyConfirmedValue(player,"farming",definition.minimumFarmingSkill());seedOutcome(level,success);
                player.gameMode.useItemOn(player,level,scissors,InteractionHand.MAIN_HAND,hit);
                var after=flower.flowerState().orElseThrow();
                h.assertTrue(after.growthStage()==1 && after.soil().remainingFertileHarvests()==4 && scissors.getDamageValue()==1,"flower lifecycle/cost failed for "+definition.id());
                int count=player.getInventory().countItem(BuiltInRegistries.ITEM.get(definition.harvestedItemId()));
                h.assertTrue(count==(success?1:0) && drops(h).isEmpty(),"flower outcome yield mismatch for "+definition.id());
            }
        } finally {player.server.getPlayerList().remove(player);}
        h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void ordinaryTallAndTrellisDispatchSerializesTwoPlayersAndRetainsFiniteUsesAfterReload(GameTestHelper h) {
        var first=player(h,"TallHarvester");var second=player(h,"TallCompetitor");var level=h.getLevel();var pos=h.absolutePos(P);
        try {
            for(String id:new String[]{"corn","banana","grapes","tomato","hops"}) {
                var crop=CropRegistry.byId(id).orElseThrow(); var soil=soil(h,crop,first);
                // Community plots allow both actors; a root that the first attempt reset cannot pay twice.
                soil.setOwner(null);
                var tool=tool(crop);first.setItemInHand(InteractionHand.MAIN_HAND,tool);second.setItemInHand(InteractionHand.MAIN_HAND,tool.copy());
                SkillManager.applyConfirmedValue(first,"farming",crop.minimumFarmingSkill());SkillManager.applyConfirmedValue(second,"farming",crop.minimumFarmingSkill());
                BlockPos clicked=crop.tallCrop()?pos.above(2):pos.above();
                var hit=new BlockHitResult(Vec3.atCenterOf(clicked),Direction.UP,clicked,false);
                seedOutcome(level,false);
                first.gameMode.useItemOn(first,level,first.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
                second.gameMode.useItemOn(second,level,second.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
                h.assertTrue(soil.getRemainingFertileHarvests()==4 && !soil.isMature() && drops(h).isEmpty(),"part dispatch/two-player failure cost mismatch: "+id);
                var saved=soil.saveWithoutMetadata(level.registryAccess());var reloaded=new FarmingBlockEntity(pos,level.getBlockState(pos));
                reloaded.loadWithComponents(saved,level.registryAccess());
                h.assertTrue(reloaded.getRemainingFertileHarvests()==4 && reloaded.hasCrop()==crop.persistsAfterHarvest(),"paid outcome did not persist: "+id);
            }
        } finally {first.server.getPlayerList().remove(first);second.server.getPlayerList().remove(second);}
        h.succeed();
    }

    @GameTest(template=TEMPLATE,timeoutTicks=200)
    public static void allFruitSpeciesScissorsAxeFruitAndAxeTreeUseOnePaidOutcome(GameTestHelper h) {
        var player=player(h,"TreeHarvests"); var level=h.getLevel(); var pos=h.absolutePos(P);
        player.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()-2);
        var emitted=new java.util.ArrayList<ItemStack>();
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> listener=e->{
            if(e.getLevel()==level && e.getEntity() instanceof ItemEntity item && item.position().distanceTo(Vec3.atCenterOf(pos))<15) emitted.add(item.getItem().copy());
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            for(var definition:FruitTreeRegistry.all()) for(int route=0;route<3;route++) for(boolean success:new boolean[]{false,true}) {
                var crop=CropRegistry.byId(definition.id()).orElseThrow(); var soil=soil(h,crop,player);
                BlockPos rootPos=pos.above();level.setBlock(rootPos,definition.rootBlock().get().defaultBlockState(),3);
                var root=(OrangeTreeRootBlockEntity)level.getBlockEntity(rootPos);root.initializeFromFarm(soil,level.getRandom(),definition.id());
                var plan=OrangeTreeStructurePlanner.plan(definition,root.getTreeSeed(),definition.maxGrowthStep(),rootPos);
                BlockPos fruitPos=plan.fruit().stream().min(java.util.Comparator.comparingDouble(p->p.distSqr(rootPos))).orElseThrow();
                level.setBlock(fruitPos,definition.fruitBlock().get().defaultBlockState().setValue(OrangeFruitBlock.RIPE,true),3);
                var tool=new ItemStack(route==0?ItemRegistry.SCISSORS.get():ItemRegistry.TWO_HANDED_AXE.get());
                player.getInventory().clearContent();
                if(route==0) for(int slot=0;slot<36;slot++) player.getInventory().setItem(slot,new ItemStack(Items.COBBLESTONE,64));
                player.setItemInHand(InteractionHand.MAIN_HAND,tool); emitted.clear();
                SkillManager.applyConfirmedValue(player,"farming",crop.minimumFarmingSkill()-.01f);
                if(route==2) FruitTreeHarvestService.chop(level,rootPos,root,player);
                else FruitTreeHarvestService.pick(level,fruitPos,root,player,InteractionHand.MAIN_HAND,tool,route==0);
                h.assertTrue(level.getBlockEntity(rootPos)==root && level.getBlockState(fruitPos).getValue(OrangeFruitBlock.RIPE) && soil.getRemainingFertileHarvests()==5 && emitted.isEmpty(),"below-threshold tree changed");
                SkillManager.applyConfirmedValue(player,"farming",crop.minimumFarmingSkill());
                level.setBlock(fruitPos,definition.fruitBlock().get().defaultBlockState().setValue(OrangeFruitBlock.RIPE,false),3);
                boolean unripe=route==2?FruitTreeHarvestService.chop(level,rootPos,root,player):FruitTreeHarvestService.pick(level,fruitPos,root,player,InteractionHand.MAIN_HAND,tool,route==0);
                h.assertTrue(!unripe && soil.getRemainingFertileHarvests()==5 && tool.getDamageValue()==0 && emitted.isEmpty(),"unripe tree produced an outcome");
                level.setBlock(fruitPos,definition.fruitBlock().get().defaultBlockState().setValue(OrangeFruitBlock.RIPE,true),3);
                seedOutcome(level,success);
                boolean applied=route==2?FruitTreeHarvestService.chop(level,rootPos,root,player):FruitTreeHarvestService.pick(level,fruitPos,root,player,InteractionHand.MAIN_HAND,tool,route==0);
                h.assertTrue(applied && soil.getRemainingFertileHarvests()==4 && tool.getDamageValue()==1,"tree outcome/cost mismatch: "+definition.id()+" route="+route);
                int fruitCount=player.getInventory().countItem(definition.fruitItem().get())+emitted.stream().filter(i->i.is(definition.fruitItem().get())).mapToInt(ItemStack::getCount).sum();
                h.assertTrue(success?fruitCount>0:fruitCount==0,"tree fruit outcome mismatch: "+definition.id()+" route="+route);
                if(!success) h.assertTrue(emitted.isEmpty(),"failed tree produced a byproduct");
                if(route==2) h.assertTrue(level.getBlockState(rootPos).isAir() && !soil.hasCrop(),"axe failure/success did not remove tree");
                else h.assertTrue(level.getBlockEntity(rootPos)==root && !level.getBlockState(fruitPos).is(definition.fruitBlock().get()),"fruit failure/success did not regrow");
                root.cleanupTree(level,player,false);
            }
        } finally { NeoForge.EVENT_BUS.unregister(listener);player.server.getPlayerList().remove(player); }
        h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void adventureCompletedTreeBreakConsumesOneFailedOutcomeWithoutFruit(GameTestHelper h) {
        var player=player(h,"AdventureTree");var level=h.getLevel();var pos=h.absolutePos(P);
        try {
            var crop=CropRegistry.byId("orange").orElseThrow();var soil=soil(h,crop,player);var definition=FruitTreeRegistry.byIdOrDefault(crop.id());
            BlockPos rootPos=pos.above();level.setBlock(rootPos,definition.rootBlock().get().defaultBlockState(),3);
            var root=(OrangeTreeRootBlockEntity)level.getBlockEntity(rootPos);root.initializeFromFarm(soil,level.getRandom(),crop.id());
            var plan=OrangeTreeStructurePlanner.plan(definition,root.getTreeSeed(),definition.maxGrowthStep(),rootPos);
            BlockPos fruit=plan.fruit().stream().findFirst().orElseThrow();level.setBlock(fruit,definition.fruitBlock().get().defaultBlockState().setValue(OrangeFruitBlock.RIPE,true),3);
            var axe=new ItemStack(ItemRegistry.TWO_HANDED_AXE.get());
            axe.set(net.minecraft.core.component.DataComponents.CAN_BREAK,com.seggellion.britannia_mod.resource.extraction.ExtractionToolPredicates.predicateFor(axe));
            player.setItemInHand(InteractionHand.MAIN_HAND,axe);player.setGameMode(GameType.ADVENTURE);SkillManager.applyConfirmedValue(player,"farming",crop.minimumFarmingSkill());
            seedOutcome(level,false);player.gameMode.destroyBlock(rootPos);
            h.assertTrue(level.getBlockState(rootPos).isAir() && soil.getRemainingFertileHarvests()==4 && axe.getDamageValue()==1,"Adventure tree harvest bypassed one failure cost");
            h.assertTrue(drops(h).isEmpty(),"failed Adventure tree yielded items");
        } finally {player.server.getPlayerList().remove(player);}
        h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void lastFlowerUseExpiresOnFailureAndCreativeHarvestPreservesTheUse(GameTestHelper h) {
        var player=player(h,"LastFlowerUse");var level=h.getLevel();var pos=h.absolutePos(P);var registry=FlowerRegistry.initial();var definition=registry.byId(FlowerRegistry.POPPY).orElseThrow();
        try {
            for(boolean creative:new boolean[]{false,true}) {
                h.setBlock(P,Blocks.AIR);h.setBlock(P,BlockRegistry.FLOWER_BLOCK.get());
                var flower=(FlowerBlockEntity)h.getBlockEntity(P);
                var tag=FlowerSoilSnapshot.privateSoil(0,0,0,0,0).withOwner(player.getUUID()).toTag();tag.putInt("RemainingFertileHarvests",1);
                var context=new FlowerPlantingContext(FlowerSoilSnapshot.fromTag(tag),new FlowerRegionProvenance("Test",FarmingClimate.TEMPERATE),FarmingClimate.TEMPERATE,pos.getY(),FlowerPlantingOrigin.PLAYER,true);
                flower.initialize(FlowerPersistentState.newlyPlanted(definition,registry.color(definition.fallbackColorId()).orElseThrow().color(),context,Optional.of(player.getUUID()),registry));
                var saved=flower.saveWithoutMetadata(level.registryAccess());saved.getCompound("FlowerState").putInt("GrowthStage",6);flower.loadWithComponents(saved,level.registryAccess());
                player.getInventory().clearContent();
                if(creative) for(int slot=0;slot<36;slot++) player.getInventory().setItem(slot,new ItemStack(Items.COBBLESTONE,64));
                var scissors=new ItemStack(ItemRegistry.SCISSORS.get());player.setItemInHand(InteractionHand.MAIN_HAND,scissors);player.setGameMode(creative?GameType.CREATIVE:GameType.SURVIVAL);
                SkillManager.applyConfirmedValue(player,"farming",20);seedOutcome(level,false);
                // Direct adapter avoids a previous replacement's intentional ten-tick input suppression.
                FlowerInteractionService.interact(level,pos,level.getBlockState(pos),player,InteractionHand.MAIN_HAND,scissors,flower);
                if(creative) h.assertTrue(flower.flowerState().orElseThrow().growthStage()==1 && flower.flowerState().orElseThrow().soil().remainingFertileHarvests()==1 && scissors.getDamageValue()==0
                        && drops(h).stream().anyMatch(e->e.getItem().is(BuiltInRegistries.ITEM.get(definition.harvestedItemId()))),"Creative flower charged or discarded its full-inventory yield");
                else h.assertTrue(level.getBlockState(pos).is(Blocks.DIRT) && scissors.getDamageValue()==1 && player.getInventory().countItem(BuiltInRegistries.ITEM.get(definition.harvestedItemId()))==0,"last failed flower use did not exhaust without yield");
            }
        } finally {player.server.getPlayerList().remove(player);}
        h.succeed();
    }

    static void seedOutcome(ServerLevel level,boolean success) {
        for(long seed=0;seed<10000;seed++) { level.getRandom().setSeed(seed);double draw=level.getRandom().nextDouble();if(success?draw<.75:draw>=.95){level.getRandom().setSeed(seed);return;} }
        throw new GameTestAssertException("no deterministic outcome seed");
    }
    static FarmingBlockEntity soil(GameTestHelper h,CropDefinition crop,ServerPlayer player) {
        for(int i=1;i<=4;i++) h.setBlock(P.above(i),Blocks.AIR);
        h.setBlock(P,Blocks.AIR);h.setBlock(P,BlockRegistry.FARMING_BLOCK.get());
        var soil=(FarmingBlockEntity)h.getBlockEntity(P);soil.beginFertilizerApplication(Blocks.FARMLAND.defaultBlockState(),0,player.getUUID(),h.getLevel().getGameTime());
        if(crop.requiresSupport() && !crop.treeCrop()) h.setBlock(P.above(),BlockRegistry.TRELLIS_BLOCK.get());
        soil.plantMigratedCrop(crop,"",crop.maxGrowthAge());
        var pos=h.absolutePos(P);h.getLevel().setBlock(pos,h.getLevel().getBlockState(pos).setValue(FarmingBlock.HAS_SEEDS,true),3);
        return soil;
    }
    private static ItemStack tool(CropDefinition crop) {
        return switch(crop.harvestTool()) {
            case HAND,BARE_HAND->ItemStack.EMPTY;
            case SCISSORS->new ItemStack(ItemRegistry.SCISSORS.get());
            case GRAIN_BLADE->new ItemStack(Items.IRON_SWORD);
            case ROOT_SHOVEL->new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("britannia_mod","britannia_shovel")));
        };
    }
    private static ServerPlayer player(GameTestHelper h,String name) {
        var player=ManagedResourceTestPlayers.survival(h.getLevel(),name);var pos=h.absolutePos(P);
        player.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()-2);return player;
    }
    private static void harvest(GameTestHelper h,ServerPlayer player,FarmingBlockEntity soil,ItemStack tool) {
        var pos=h.absolutePos(P);FarmingBlock.tryHarvestCrop(soil,h.getLevel().getBlockState(pos),h.getLevel(),pos,player,tool,InteractionHand.MAIN_HAND,"gameplay_contract");
    }
    private static java.util.List<ItemEntity> drops(GameTestHelper h) {return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(P)).inflate(4));}
}
