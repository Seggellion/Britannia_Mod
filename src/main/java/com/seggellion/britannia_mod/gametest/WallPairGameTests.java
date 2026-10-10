package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.block.DoubleWallBlock;
import com.seggellion.britannia_mod.block.MirrorableWallBlock;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the actual item entry point on registered shared consumers, not a state-only mock. */
@GameTestHolder("britannia_wall_pair")
@PrefixGameTestTemplate(false)
public final class WallPairGameTests {
    @GameTest(batch="wall_pair", template="service_npc_spawn_test_empty", timeoutTicks=100)
    public static void decoratorFromEitherHalfUpdatesTheWholeRegisteredPair(GameTestHelper h) {
        var player=ManagedResourceTestPlayers.survival(h.getLevel(),"WallPair");
        player.setGameMode(GameType.CREATIVE);
        var base=h.absolutePos(new BlockPos(4,4,4));
        try {
            for(var block:new DoubleWallBlock[]{BlockRegistry.PLASTER_WALL_AND_SUPPORT_BLANK.get(),
                BlockRegistry.PLASTER_WALL_LARGE_WINDOW.get(),BlockRegistry.ORNATE_WALL_LARGE_WINDOW.get(),
                BlockRegistry.SANDSTONE_WINDOW.get(),BlockRegistry.ORNATE_SANDSTONE_WINDOW.get(),
                BlockRegistry.SANDSTONE_BATTLEMENT.get(),BlockRegistry.PLASTER_WALL_BLANK.get()}) {
                for(var hand:InteractionHand.values()) for(var half:DoubleBlockHalf.values()) {
                    if(hand==InteractionHand.OFF_HAND && !(block instanceof MirrorableWallBlock)) continue;
                    var lower=block.defaultBlockState();
                    h.getLevel().setBlock(base,lower,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
                    h.getLevel().setBlock(base.above(),lower.setValue(DoubleWallBlock.HALF,DoubleBlockHalf.UPPER),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
                    var clicked=half==DoubleBlockHalf.LOWER?base:base.above();
                    player.setItemInHand(hand,new ItemStack(ItemRegistry.INTERIOR_DECORATOR_TOOL.get()));
                    var hit=new BlockHitResult(Vec3.atCenterOf(clicked),Direction.SOUTH,clicked,false);
                    var result=player.getItemInHand(hand).useOn(new UseOnContext(player,hand,hit));
                    h.assertTrue(result.consumesAction(),"item did not handle wall");
                    var actual=h.getLevel().getBlockState(base);
                    var upper=h.getLevel().getBlockState(base.above());
                    h.assertTrue(upper==actual.setValue(DoubleWallBlock.HALF,DoubleBlockHalf.UPPER),"paired item edit diverged: "+block+hand+half+actual+upper);
                    if(hand==InteractionHand.OFF_HAND) h.assertTrue(actual.getValue(MirrorableWallBlock.MIRRORED),"upper click failed to mirror visible lower model");
                    else h.assertTrue(actual.getValue(DoubleWallBlock.FACING)==Direction.EAST,"upper click failed to rotate visible lower model");
                }
            }
        } finally { h.getLevel().getServer().getPlayerList().remove(player); }
        h.succeed();
    }

    @GameTest(batch="wall_pair", template="service_npc_spawn_test_empty")
    public static void legacyDivergenceIsSafeBeforeEditAndRecoversFromTheVisibleLower(GameTestHelper h) {
        var block=BlockRegistry.PLASTER_WALL_AND_SUPPORT_BLANK.get();
        var base=h.absolutePos(new BlockPos(4,4,4));
        var lower=block.defaultBlockState().setValue(DoubleWallBlock.FACING,Direction.SOUTH);
        var upper=lower.setValue(DoubleWallBlock.HALF,DoubleBlockHalf.UPPER)
            .setValue(DoubleWallBlock.FACING,Direction.WEST).setValue(MirrorableWallBlock.MIRRORED,true);
        pair(h,base,lower,upper);
        h.assertTrue(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(
            lower.getCollisionShape(h.getLevel(),base),upper.getCollisionShape(h.getLevel(),base.above()),
            net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME),"legacy upper collider follows invisible stored facing");
        h.assertTrue(h.getLevel().getBlockState(base.above())==upper,"query mutated legacy save");
        var player=ManagedResourceTestPlayers.survival(h.getLevel(),"WallRecover");
        try {
            player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(ItemRegistry.INTERIOR_DECORATOR_TOOL.get()));
            player.getOffhandItem().useOn(new UseOnContext(player,InteractionHand.OFF_HAND,
                new BlockHitResult(Vec3.atCenterOf(base.above()),Direction.SOUTH,base.above(),false)));
            var actual=h.getLevel().getBlockState(base);
            h.assertTrue(actual.getValue(MirrorableWallBlock.MIRRORED),"edit used invisible upper state as authority");
            h.assertTrue(actual.getValue(DoubleWallBlock.FACING)==Direction.SOUTH,"recovery lost visible lower facing");
            assertPair(h,base);
            var codec=net.minecraft.world.level.block.state.BlockState.CODEC;
            for(var pos:new BlockPos[]{base,base.above()}) {
                var saved=h.getLevel().getBlockState(pos);
                h.assertTrue(codec.parse(net.minecraft.nbt.NbtOps.INSTANCE,
                    codec.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,saved).getOrThrow()).getOrThrow()==saved,"save round trip changed repaired pair");
            }
        } finally { h.getLevel().getServer().getPlayerList().remove(player); }
        h.runAfterDelay(10,()->{assertPair(h,base);h.succeed();});
    }

    @GameTest(batch="wall_pair", template="service_npc_spawn_test_empty")
    public static void brokenPairRejectsTheActualItemWithoutCreatingOrEditingACounterpart(GameTestHelper h) {
        var base=h.absolutePos(new BlockPos(4,4,4));
        var player=ManagedResourceTestPlayers.survival(h.getLevel(),"WallBroken");
        try {
            for(var hand:InteractionHand.values()) for(var half:DoubleBlockHalf.values()) {
                var original=BlockRegistry.PLASTER_WALL_AND_SUPPORT_BLANK.get().defaultBlockState().setValue(DoubleWallBlock.HALF,half);
                var other=BlockRegistry.PLASTER_WALL_BLANK.get().defaultBlockState()
                    .setValue(DoubleWallBlock.HALF,half==DoubleBlockHalf.LOWER?DoubleBlockHalf.UPPER:DoubleBlockHalf.LOWER);
                var clicked=half==DoubleBlockHalf.LOWER?base:base.above();
                var counterpart=half==DoubleBlockHalf.LOWER?base.above():base;
                h.getLevel().setBlock(clicked,original,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
                h.getLevel().setBlock(counterpart,other,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
                player.setItemInHand(hand,new ItemStack(ItemRegistry.INTERIOR_DECORATOR_TOOL.get()));
                var result=player.getItemInHand(hand).useOn(new UseOnContext(player,hand,
                    new BlockHitResult(Vec3.atCenterOf(clicked),Direction.SOUTH,clicked,false)));
                h.assertTrue(result==net.minecraft.world.InteractionResult.FAIL,"broken pair was accepted");
                h.assertTrue(h.getLevel().getBlockState(clicked)==original && h.getLevel().getBlockState(counterpart)==other,"broken pair mutated");
            }
        } finally { h.getLevel().getServer().getPlayerList().remove(player); }
        h.succeed();
    }

    @GameTest(batch="wall_pair", template="service_npc_spawn_test_empty", timeoutTicks=100)
    public static void registeredCornerVariantsSurviveEveryStructureTransformAndOrdinaryNotifications(GameTestHelper h) {
        var base=h.absolutePos(new BlockPos(4,4,4));
        for(var block:new MirrorableWallBlock[]{BlockRegistry.PLASTER_WALL_AND_SUPPORT_BLANK.get(),
            BlockRegistry.PLASTER_WALL_LARGE_WINDOW.get(),BlockRegistry.ORNATE_WALL_LARGE_WINDOW.get()})
            for(var facing:Direction.Plane.HORIZONTAL) for(boolean branch:new boolean[]{false,true})
                for(boolean mirrored:new boolean[]{false,true}) {
                    var seed=block.defaultBlockState().setValue(DoubleWallBlock.SHAPE,com.seggellion.britannia_mod.block.WallShape.CORNER)
                        .setValue(DoubleWallBlock.FACING,facing).setValue(DoubleWallBlock.BRANCH_RIGHT,branch)
                        .setValue(MirrorableWallBlock.MIRRORED,mirrored);
                    for(var mirror:net.minecraft.world.level.block.Mirror.values()) for(var rotation:net.minecraft.world.level.block.Rotation.values()) {
                        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<=1;y++)
                            h.getLevel().setBlock(base.offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
                        var transformed=seed.mirror(mirror).rotate(rotation);
                        var run=DoubleWallBlock.physicalRun(transformed);
                        pair(h,base,transformed,transformed.setValue(DoubleWallBlock.HALF,DoubleBlockHalf.UPPER));
                        for(var edge:new Direction[]{run.facing(),run.secondary()}) {
                            var position=base.relative((edge==run.facing()?run.secondary():run.facing()).getOpposite());
                            var straight=block.defaultBlockState().setValue(DoubleWallBlock.FACING,edge);
                            pair(h,position,straight,straight.setValue(DoubleWallBlock.HALF,DoubleBlockHalf.UPPER));
                        }
                        var before=transformed.getCollisionShape(h.getLevel(),base);
                        for(int round=0;round<4;round++) {
                            h.getLevel().setBlock(base.below(),(round%2==0?net.minecraft.world.level.block.Blocks.STONE:net.minecraft.world.level.block.Blocks.DIRT).defaultBlockState(),Block.UPDATE_ALL);
                            assertPair(h,base);
                            var actual=h.getLevel().getBlockState(base);
                            h.assertTrue(actual.getValue(DoubleWallBlock.BRANCH_RIGHT)==branch,"authored post variant changed after transform");
                            h.assertTrue(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(before,actual.getCollisionShape(h.getLevel(),base),
                                net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME),"physical corner moved after ordinary notification "+transformed+actual);
                        }
                    }
                }
        h.runAfterDelay(10,()->{assertPair(h,base);h.succeed();});
    }
    private static void pair(GameTestHelper h,BlockPos base,net.minecraft.world.level.block.state.BlockState lower,
                             net.minecraft.world.level.block.state.BlockState upper) {
        h.getLevel().setBlock(base,lower,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        h.getLevel().setBlock(base.above(),upper,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
    }
    private static void assertPair(GameTestHelper h,BlockPos base) {
        var lower=h.getLevel().getBlockState(base);
        h.assertTrue(lower.getBlock() instanceof DoubleWallBlock && lower.getValue(DoubleWallBlock.HALF)==DoubleBlockHalf.LOWER,"lower missing");
        h.assertTrue(h.getLevel().getBlockState(base.above())==lower.setValue(DoubleWallBlock.HALF,DoubleBlockHalf.UPPER),"not a synchronized pair");
    }
}
