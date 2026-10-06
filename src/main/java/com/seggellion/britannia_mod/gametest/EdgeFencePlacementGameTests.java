package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.block.WoodenFenceBlock;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real BlockItem entry point; constructed server hits, separately from client packet acceptance. */
@GameTestHolder("britannia_edge_fence")
@PrefixGameTestTemplate(false)
public final class EdgeFencePlacementGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    @GameTest(template=TEMPLATE, timeoutTicks=300) public static void northAnchor(GameTestHelper h) { scenario(h,Direction.NORTH,0); }
    @GameTest(template=TEMPLATE, timeoutTicks=300) public static void eastAnchor(GameTestHelper h) { scenario(h,Direction.EAST,0); }
    @GameTest(template=TEMPLATE, timeoutTicks=300) public static void southAnchor(GameTestHelper h) { scenario(h,Direction.SOUTH,0); }
    @GameTest(template=TEMPLATE, timeoutTicks=300) public static void westAnchor(GameTestHelper h) { scenario(h,Direction.WEST,0); }

    private static void scenario(GameTestHelper h, Direction facing, int index) {
        if(index==6) { h.succeed(); return; }
        var origin=h.absolutePos(new BlockPos(4,4,4));
        for(int x=-2;x<=3;x++)for(int z=-2;z<=3;z++)for(int y=-1;y<=1;y++)
            h.getLevel().setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
        var step=facing.getAxis()==Direction.Axis.Z?Direction.EAST:Direction.SOUTH;
        var first=index<3?origin:origin.relative(step);
        var second=index<3?origin.relative(step):origin;
        var extend=index<3?step:step.getOpposite();
        var p=ManagedResourceTestPlayers.survival(h.getLevel(),"EdgeAnchor");
        p.setGameMode(GameType.CREATIVE);
        p.setYRot(facing.getOpposite().toYRot()); p.setXRot(0);
        p.setPos(first.getX()+0.5,first.getY(),first.getZ()+3);
        support(h,p,first.relative(facing),facing.getOpposite());
        var old=h.getLevel().getBlockState(first);
        var outline=old.getShape(h.getLevel(),first);
        var collision=old.getCollisionShape(h.getLevel(),first);
        h.assertTrue(old.getValue(WoodenFenceBlock.FACING)==facing,"isolated anchor convention");
        h.assertTrue(old.getValue(WoodenFenceBlock.LAYOUT_CODE)==0,"plain item did not write a concrete single");
        h.runAfterDelay(3,()->{
            if(index%3==0) {
                var hit=Vec3.atLowerCornerOf(first).add(
                    extend==Direction.EAST?1:extend==Direction.WEST?0:facing==Direction.EAST?0.875:0.125,
                    0.5,extend==Direction.SOUTH?1:extend==Direction.NORTH?0:facing==Direction.SOUTH?0.875:0.125);
                place(h,p,new BlockHitResult(hit,extend,first,false));
            } else if(index%3==1) support(h,p,second.relative(facing),facing.getOpposite());
            else support(h,p,second.below(),Direction.UP);
            Runnable verify=()->{
                var state=h.getLevel().getBlockState(first);
                h.assertTrue(state.getValue(WoodenFenceBlock.FACING)==facing,"neighbor reversed "+facing+" case="+index+" state="+state);
                h.assertTrue(state.getValue(WoodenFenceBlock.LAYOUT_CODE)==old.getValue(WoodenFenceBlock.LAYOUT_CODE),"render layout selector changed");
                h.assertTrue(!Shapes.joinIsNotEmpty(outline,state.getShape(h.getLevel(),first),BooleanOp.NOT_SAME),"outline moved");
                h.assertTrue(!Shapes.joinIsNotEmpty(collision,state.getCollisionShape(h.getLevel(),first),BooleanOp.NOT_SAME),"collision moved");
                h.assertTrue(h.getLevel().getBlockState(second).getValue(WoodenFenceBlock.FACING)==facing,"new straight anchor differs");
            };
            verify.run();
            h.runAfterDelay(5,()->{
                verify.run(); h.getLevel().removeBlock(second,false);
                var state=h.getLevel().getBlockState(first);
                h.assertTrue(state.getValue(WoodenFenceBlock.FACING)==facing,"removal reversed anchor");
                h.assertTrue(!Shapes.joinIsNotEmpty(outline,state.getShape(h.getLevel(),first),BooleanOp.NOT_SAME),"removal moved outline");
                h.runAfterDelay(3,()->{ h.getLevel().getServer().getPlayerList().remove(p); scenario(h,facing,index+1); });
            });
        });
    }
    static void support(GameTestHelper h, ServerPlayer p, BlockPos pos, Direction face) {
        h.getLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
        place(h,p,new BlockHitResult(Vec3.atCenterOf(pos).add(face.getStepX()*0.5,face.getStepY()*0.5,face.getStepZ()*0.5),face,pos,false));
    }
    static void place(GameTestHelper h,ServerPlayer p,BlockHitResult hit) {
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(BlockRegistry.WOODEN_FENCE.get()));
        var ctx=new BlockPlaceContext(new UseOnContext(p,InteractionHand.MAIN_HAND,hit));
        var result=((BlockItem)p.getMainHandItem().getItem()).place(ctx);
        h.assertTrue(result.consumesAction(),"BlockItem placement failed "+hit+" "+result);
    }
    @GameTest(template=TEMPLATE)
    public static void savedCornerSurvivesFirstUpdateAndRemoval(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(4,3,4));
        var fence=BlockRegistry.WOODEN_FENCE.get();
        var legacy=fence.defaultBlockState().setValue(WoodenFenceBlock.FACING,Direction.NORTH)
            .setValue(WoodenFenceBlock.EAST,true).setValue(WoodenFenceBlock.SOUTH,true);
        var before=legacy.getShape(h.getLevel(),pos);
        h.getLevel().setBlock(pos,legacy,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        var updated=fence.deriveConnections(legacy,h.getLevel(),pos);
        h.assertTrue(!Shapes.joinIsNotEmpty(before,updated.getShape(h.getLevel(),pos),BooleanOp.NOT_SAME),"legacy corner disappeared when stale flags corrected");
        h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void sequentialFourCornerEnclosureAndReplacementWorkflow(GameTestHelper h) {
        var p=ManagedResourceTestPlayers.survival(h.getLevel(),"EdgeEnclosure");
        p.setGameMode(GameType.CREATIVE);
        var base=h.absolutePos(new BlockPos(7,4,7));
        try {
            for(var rotation:net.minecraft.world.level.block.Rotation.values()) {
                for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=-1;y<=1;y++)
                    h.getLevel().setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
                // A single chosen before the two arms stays single, requiring deliberate replacement.
                ground(h,p,base,rotation.rotate(Direction.NORTH));
                for(int i=1;i<=2;i++) {
                    ground(h,p,base.offset(new BlockPos(i,0,0).rotate(rotation)),rotation.rotate(Direction.NORTH));
                    ground(h,p,base.offset(new BlockPos(3,0,i).rotate(rotation)),rotation.rotate(Direction.EAST));
                    ground(h,p,base.offset(new BlockPos(i,0,3).rotate(rotation)),rotation.rotate(Direction.SOUTH));
                    ground(h,p,base.offset(new BlockPos(0,0,i).rotate(rotation)),rotation.rotate(Direction.WEST));
                }
                h.assertTrue(h.getLevel().getBlockState(base).getValue(WoodenFenceBlock.LAYOUT_CODE)==0,"old corner single silently became an L");
                h.getLevel().removeBlock(base,false);
                int[][] cells={{0,0,6},{3,0,12},{3,3,9},{0,3,3}};
                for(int[] c:cells) {
                    var pos=base.offset(new BlockPos(c[0],0,c[1]).rotate(rotation));
                    ground(h,p,pos,rotation.rotate(c[1]==0?Direction.NORTH:Direction.SOUTH));
                    var expected=BlockRegistry.WOODEN_FENCE.get().defaultBlockState().setValue(WoodenFenceBlock.LAYOUT_CODE,c[2]).rotate(rotation);
                    var actual=h.getLevel().getBlockState(pos);
                    h.assertTrue(actual.getValue(WoodenFenceBlock.LAYOUT_CODE)==expected.getValue(WoodenFenceBlock.LAYOUT_CODE),"corner selection "+rotation+" "+actual);
                    h.assertTrue(WoodenFenceBlock.connectionCount(actual)==2,"corner did not contact both arms");
                }
                // Every panel on the twelve-cell perimeter shares a real endpoint with both neighbors.
                for(int x=0;x<4;x++)for(int z=0;z<4;z++) if(x==0||x==3||z==0||z==3) {
                    var pos=base.offset(new BlockPos(x,0,z).rotate(rotation));
                    var state=h.getLevel().getBlockState(pos);
                    h.assertTrue(WoodenFenceBlock.connectionCount(state)==2,"broken enclosure at "+pos+" "+state);
                    var fence=(WoodenFenceBlock)state.getBlock();
                    for(int round=0;round<20;round++) h.assertTrue(fence.deriveConnections(state,h.getLevel(),pos)==state,"enclosure oscillates");
                }
                var old=h.getLevel().getBlockState(base);
                var shape=old.getShape(h.getLevel(),base);
                h.getLevel().removeBlock(base.relative(rotation.rotate(Direction.EAST)),false);
                h.getLevel().removeBlock(base.relative(rotation.rotate(Direction.SOUTH)),false);
                var after=h.getLevel().getBlockState(base);
                h.assertTrue(after.getValue(WoodenFenceBlock.LAYOUT_CODE)==old.getValue(WoodenFenceBlock.LAYOUT_CODE),"corner removal changed panels");
                h.assertTrue(!Shapes.joinIsNotEmpty(shape,after.getShape(h.getLevel(),base),BooleanOp.NOT_SAME),"corner removal moved physical layout");
            }
        } finally { h.getLevel().getServer().getPlayerList().remove(p); }
        h.succeed();
    }
    private static void ground(GameTestHelper h,ServerPlayer p,BlockPos target,Direction facing) {
        p.setYRot(facing.getOpposite().toYRot());
        p.setPos(target.getX()+.5,target.getY()+2,target.getZ()+3);
        support(h,p,target.below(),Direction.UP);
    }

    @GameTest(template=TEMPLATE)
    public static void supportIntentWinsConflictsAndReplacementUsesResolvedTarget(GameTestHelper h) {
        var p=ManagedResourceTestPlayers.survival(h.getLevel(),"EdgeIntent");
        var target=h.absolutePos(new BlockPos(5,4,5));
        p.setGameMode(GameType.CREATIVE); p.setYRot(Direction.NORTH.toYRot());
        p.setPos(target.getX()+.5,target.getY()+2,target.getZ()+3);
        try {
            var fence=BlockRegistry.WOODEN_FENCE.get();
            var north=fence.defaultBlockState().setValue(WoodenFenceBlock.LAYOUT_CODE,0);
            var south=north.setValue(WoodenFenceBlock.FACING,Direction.SOUTH);
            h.getLevel().setBlock(target.west(),north,Block.UPDATE_ALL);
            h.getLevel().setBlock(target.east(),north,Block.UPDATE_ALL);
            support(h,p,target.south(),Direction.NORTH);
            var actual=h.getLevel().getBlockState(target);
            h.assertTrue(actual.getValue(WoodenFenceBlock.FACING)==Direction.SOUTH,"support hint displaced explicit south anchor");
            h.assertTrue((com.seggellion.britannia_mod.block.WoodenFenceGeometry.occupiedEdges(actual)&4)!=0,"support elbow discarded anchor");
            h.getLevel().removeBlock(target,false);
            h.getLevel().removeBlock(target.south(),false);
            h.getLevel().setBlock(target,Blocks.TALL_GRASS.defaultBlockState(),Block.UPDATE_ALL);
            var hit=new BlockHitResult(Vec3.atCenterOf(target),Direction.UP,target,false);
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(fence));
            var context=new BlockPlaceContext(new UseOnContext(p,InteractionHand.MAIN_HAND,hit));
            var first=fence.getStateForPlacement(context);
            h.assertTrue(context.getClickedPos().equals(target),"replacement target not resolved");
            for(int round=0;round<20;round++) h.assertTrue(fence.getStateForPlacement(context)==first,"same-input conflict nondeterministic");
            place(h,p,hit);
            h.assertTrue(h.getLevel().getBlockState(target)==first,"placement disagrees with same resolved context");
            h.assertTrue(h.getLevel().getBlockState(target.west()).getValue(WoodenFenceBlock.FACING)==Direction.NORTH,"existing owner changed");
        } finally { h.getLevel().getServer().getPlayerList().remove(p); }
        h.succeed();
    }
}
