package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.*;
import com.seggellion.britannia_mod.placement.CreativeDecorationPolicy;
import com.seggellion.britannia_mod.registry.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class GameplayCreativeDecorationGameTests {
    private static final String TEMPLATE="service_npc_spawn_test_empty";

    @GameTest(template=TEMPLATE)
    public static void creativeOriginSurvivesSupportRemovalAndSerializationForEveryRecurringFamily(GameTestHelper h) {
        var player=ManagedResourceTestPlayers.survival(h.getLevel(),"M9Origin");
        var root=h.absolutePos(new BlockPos(4,4,4));
        var blocks=List.of(BlockRegistry.FERN.get(),BlockRegistry.HEDGE_BUSH.get(),BlockRegistry.POOL_OF_BLOOD.get(),
                BlockRegistry.STALACTITES.get(1).get(),BlockRegistry.IRON_FENCE_GATE.get());
        try {
            for(var block:blocks) {
                clear(h,root); player.setGameMode(GameType.CREATIVE); player.setPos(root.getX()+3,root.getY(),root.getZ()+3);
                var support=block instanceof StalactiteBlock ? root.above() : root.below();
                var face=block instanceof StalactiteBlock ? Direction.DOWN : Direction.UP;
                h.getLevel().setBlock(support,Blocks.OAK_SLAB.defaultBlockState(),Block.UPDATE_ALL);
                var stack=new ItemStack(block.asItem(),2); player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                var result=stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(support.getCenter(),face,support,false)));
                h.assertTrue(result.consumesAction() && h.getLevel().getBlockState(root).is(block),"creative substrate refused "+block+" "+result);
                h.assertTrue(stack.getCount()==2,"creative item consumed "+block);
                var placed=h.getLevel().getBlockState(root);
                h.assertTrue(CreativeDecorationPolicy.placedInCreative(placed),"origin missing "+block);
                var saved=NbtUtils.writeBlockState(placed);
                var restored=NbtUtils.readBlockState(h.getLevel().registryAccess().lookupOrThrow(Registries.BLOCK),saved);
                h.assertTrue(restored==placed,"origin/facing did not serialize "+block);
                h.getLevel().setBlock(support,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
                h.assertTrue(h.getLevel().getBlockState(root).is(block) && placed.canSurvive(h.getLevel(),root),"creative support update destroyed "+block);
                saved.getCompound("Properties").remove("creative_origin");
                var legacy=NbtUtils.readBlockState(h.getLevel().registryAccess().lookupOrThrow(Registries.BLOCK),saved);
                h.assertTrue(!CreativeDecorationPolicy.placedInCreative(legacy) && !legacy.canSurvive(h.getLevel(),root),"legacy default bypassed substrate "+block);
                if(block instanceof TripleMetalDoorBlock) {
                    h.assertTrue(h.getLevel().getBlockState(root.above()).is(block) && h.getLevel().getBlockState(root.above(2)).is(block),"creative door incomplete");
                    h.getLevel().removeBlock(root.above(),false);
                    h.assertTrue(!h.getLevel().getBlockState(root).is(block) && !h.getLevel().getBlockState(root.above(2)).is(block),"creative flag bypassed structural integrity");
                }
            }
        } finally { clear(h,root);h.getLevel().getServer().getPlayerList().remove(player); }
        h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void normalModesRetainTheirSubstrateAndPermissionRules(GameTestHelper h) {
        var player=ManagedResourceTestPlayers.survival(h.getLevel(),"M9Modes");
        var root=h.absolutePos(new BlockPos(4,4,4));
        try {
            for(var mode:List.of(GameType.SURVIVAL,GameType.ADVENTURE)) for(var block:List.of(BlockRegistry.FERN.get(),BlockRegistry.HEDGE_BUSH.get(),
                    BlockRegistry.POOL_OF_BLOOD.get(),BlockRegistry.STALACTITES.get(1).get(),BlockRegistry.IRON_FENCE_GATE.get())) {
                clear(h,root); player.setGameMode(mode); player.setPos(root.getX()+3,root.getY(),root.getZ()+3);
                var support=block instanceof StalactiteBlock?root.above():root.below();
                var face=block instanceof StalactiteBlock?Direction.DOWN:Direction.UP;
                h.getLevel().setBlock(support,Blocks.OAK_SLAB.defaultBlockState(),Block.UPDATE_ALL);
                var stack=new ItemStack(block.asItem(),2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,new BlockHitResult(support.getCenter(),face,support,false));
                var state=block.getStateForPlacement(context);
                h.assertTrue(state==null || !CreativeDecorationPolicy.placedInCreative(state),"noncreative origin set");
                // An underside of a bottom slab is a valid stalactite attachment; other unsupported surfaces refuse.
                boolean vanillaSupport=state!=null && state.canSurvive(h.getLevel(),root);
                if(!vanillaSupport) {
                    stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(support.getCenter(),face,support,false)));
                    h.assertTrue(!h.getLevel().getBlockState(root).is(block) && stack.getCount()==2,"normal unsupported placement consumed/placed "+mode+block);
                }
            }
        } finally {clear(h,root);h.getLevel().getServer().getPlayerList().remove(player);}
        h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void scarecrowCreativePlacementCoversSurfacesAndKeepsAllFourCells(GameTestHelper h) {
        var player=ManagedResourceTestPlayers.survival(h.getLevel(),"M9Scarecrow");
        var root=h.absolutePos(new BlockPos(4,3,4));
        var block=BlockRegistry.SCARECROW.get();
        try {
            for(var floor:List.of(Blocks.GRASS_BLOCK.defaultBlockState(),Blocks.STONE.defaultBlockState(),Blocks.DIRT_PATH.defaultBlockState(),
                    Blocks.OAK_SLAB.defaultBlockState(),Blocks.OAK_STAIRS.defaultBlockState(),BlockRegistry.COMMUNITY_FARM_BLOCK.get().defaultBlockState(),Blocks.SNOW.defaultBlockState())) {
                clear(h,root);player.setGameMode(GameType.CREATIVE);player.setPos(root.getX()+4,root.getY(),root.getZ()+4);
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)h.getLevel().setBlock(root.offset(x,-1,z),floor,Block.UPDATE_ALL);
                var stack=new ItemStack(ItemRegistry.SCARECROW_ITEM.get(),2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                // Hit air at the intended minimum cell: thin replaceable supports must not move the root down.
                var hit=new BlockHitResult(root.getCenter(),Direction.UP,root,false);
                var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,hit);
                var item=(com.seggellion.britannia_mod.item.DecorativeMultiblockItem)stack.getItem();
                var anchor=item.grabbyPlacementRoot(context);
                var result=stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
                h.assertTrue(result.consumesAction(),"creative scarecrow failed "+floor);
                var state=h.getLevel().getBlockState(anchor);
                h.assertTrue(state.is(block),"scarecrow anchor absent");
                var facing=state.getValue(DecorativeMultiblockBlock.FACING);
                h.assertTrue(block.cells().size()==4,"scarecrow fixture footprint changed");
                for(var cell:block.cells())h.assertTrue(h.getLevel().getBlockState(block.worldPosition(anchor,facing,cell)).is(block),"missing scarecrow cell");
                h.assertTrue(stack.getCount()==2,"creative scarecrow consumed item");
            }
        } finally {clear(h,root);h.getLevel().getServer().getPlayerList().remove(player);}
        h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void occupiedDoorAndStalactiteFootprintsRefuseWithoutPartialPlacement(GameTestHelper h) {
        var player=ManagedResourceTestPlayers.survival(h.getLevel(),"M9Footprint");
        var root=h.absolutePos(new BlockPos(4,4,4));
        try {
            player.setGameMode(GameType.CREATIVE);player.setPos(root.getX()+3,root.getY(),root.getZ()+3);
            for(boolean door:List.of(true,false)) {
                clear(h,root);var block=door?BlockRegistry.IRON_FENCE_GATE.get():BlockRegistry.STALACTITES.get(1).get();
                var occupied=door?root.above(2):root.below();
                h.getLevel().setBlock(occupied,Blocks.CHEST.defaultBlockState(),Block.UPDATE_ALL);
                var stack=new ItemStack(block.asItem(),2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(root.getCenter(),Direction.UP,root,false)));
                h.assertTrue(h.getLevel().getBlockState(occupied).is(Blocks.CHEST) && h.getLevel().getBlockEntity(occupied)!=null,"occupied BE overwritten");
                h.assertTrue(h.getLevel().getBlockState(root).isAir() && stack.getCount()==2,"invalid footprint partially placed or consumed");
            }
        } finally {clear(h,root);h.getLevel().getServer().getPlayerList().remove(player);}
        h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void creativeWineBottleNeedsNoPersistedFlagAndKeepsItsContents(GameTestHelper h) {
        var player=ManagedResourceTestPlayers.survival(h.getLevel(),"M9Bottle");
        var root=h.absolutePos(new BlockPos(4,4,4));
        try {
            clear(h,root);player.setGameMode(GameType.CREATIVE);player.setPos(root.getX()+3,root.getY(),root.getZ()+3);
            var block=BlockRegistry.WINE_BOTTLE_GREEN_BLOCK.get();
            var stack=new ItemStack(block.asItem(),2);
            com.seggellion.britannia_mod.item.WineBottleBlockItem.setWineData(stack,"M9 Winery","test",2026,83,"Jhelom","none");
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("M9 exact bottle"));
            player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            var hit=new BlockHitResult(root.getCenter(),Direction.UP,root,false);
            h.assertTrue(stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"creative bottle on unsupported surface refused");
            h.assertTrue(h.getLevel().getBlockState(root).is(block) && !h.getLevel().getBlockState(root).hasProperty(CreativeDecorationPolicy.ORIGIN),"bottle missing or unnecessary origin state added");
            var be=(com.seggellion.britannia_mod.block.entity.WineBottleBlockEntity)h.getLevel().getBlockEntity(root);
            h.assertTrue(ItemStack.matches(be.portableStack(block.asItem()),stack.copyWithCount(1)),"creative bottle lost contents/components");
            var restored=new com.seggellion.britannia_mod.block.entity.WineBottleBlockEntity(root,h.getLevel().getBlockState(root));
            restored.loadWithComponents(be.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(ItemStack.matches(restored.portableStack(block.asItem()),stack.copyWithCount(1)),"bottle contents lost on NBT reload");
            h.getLevel().setBlock(root.below(),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
            h.getLevel().removeBlock(root.below(),false);
            h.assertTrue(h.getLevel().getBlockState(root).is(block) && stack.getCount()==2,"bottle update/creative consumption regression");
        } finally {clear(h,root);h.getLevel().getServer().getPlayerList().remove(player);}
        h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void supportedSurvivalPlacementsConsumeOnceAndStillNeedTheirSupport(GameTestHelper h) {
        var player=ManagedResourceTestPlayers.survival(h.getLevel(),"M9Survival");
        var root=h.absolutePos(new BlockPos(4,4,4));
        try {
            for(var block:List.of(BlockRegistry.FERN.get(),BlockRegistry.HEDGE_BUSH.get(),BlockRegistry.POOL_OF_BLOOD.get(),
                    BlockRegistry.STALACTITES.get(1).get(),BlockRegistry.IRON_FENCE_GATE.get())) {
                clear(h,root);player.setGameMode(GameType.SURVIVAL);player.setPos(root.getX()+3,root.getY(),root.getZ()+3);
                var support=block instanceof StalactiteBlock?root.above():root.below();
                var face=block instanceof StalactiteBlock?Direction.DOWN:Direction.UP;
                h.getLevel().setBlock(support,Blocks.DIRT.defaultBlockState(),Block.UPDATE_ALL);
                var stack=new ItemStack(block.asItem(),2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                var hit=new BlockHitResult(support.getCenter(),face,support,false);
                h.assertTrue(stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"supported Survival refused "+block);
                h.assertTrue(h.getLevel().getBlockState(root).is(block) && stack.getCount()==1,"Survival cost/placement wrong "+block);
                h.getLevel().removeBlock(support,false);
                h.assertTrue(!h.getLevel().getBlockState(root).is(block),"normal block no longer needs support "+block);
            }
        } finally {clear(h,root);h.getLevel().getServer().getPlayerList().remove(player);}
        h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void adventureScarecrowRetainsCommunityOnlyPermissionAndInvalidFootprintsAreFree(GameTestHelper h) {
        var player=ManagedResourceTestPlayers.survival(h.getLevel(),"M9Adventure");
        var root=h.absolutePos(new BlockPos(4,3,4));
        try {
            for(int scenario=0;scenario<3;scenario++) {
                clear(h,root);player.setGameMode(GameType.ADVENTURE);player.setPos(root.getX()+4,root.getY(),root.getZ()+4);
                var floor=scenario==0?Blocks.STONE.defaultBlockState():BlockRegistry.COMMUNITY_FARM_BLOCK.get().defaultBlockState();
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)h.getLevel().setBlock(root.offset(x,-1,z),floor,Block.UPDATE_ALL);
                var stack=new ItemStack(ItemRegistry.SCARECROW_ITEM.get(),2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                if(scenario==2)h.getLevel().setBlock(root.above(),Blocks.CHEST.defaultBlockState(),Block.UPDATE_ALL);
                var hit=new BlockHitResult(root.getCenter(),Direction.UP,root,false);
                var result=((com.seggellion.britannia_mod.item.AdventureScarecrowItem)stack.getItem()).useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
                h.assertTrue(result.consumesAction()==(scenario==1),"Adventure community/footprint rule changed "+scenario);
                h.assertTrue(stack.getCount()==(scenario==1?1:2),"refused/accepted Adventure cost wrong");
                if(scenario==2)h.assertTrue(h.getLevel().getBlockEntity(root.above())!=null,"scarecrow overwrote chest");
            }
        } finally {clear(h,root);h.getLevel().getServer().getPlayerList().remove(player);}
        h.succeed();
    }

    private static void clear(GameTestHelper h,BlockPos root) {
        for(int x=-2;x<=2;x++)for(int y=-2;y<=3;y++)for(int z=-2;z<=2;z++)
            h.getLevel().setBlock(root.offset(x,y,z),Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
    }
}
