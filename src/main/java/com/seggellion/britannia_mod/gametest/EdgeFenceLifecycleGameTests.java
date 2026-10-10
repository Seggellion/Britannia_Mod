package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.block.WoodenFenceBlock;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.*;

/** Explicit two-process disposable-world test. Never run against an operator world. */
@GameTestHolder("britannia_edge_fence_lifecycle")
@PrefixGameTestTemplate(false)
public final class EdgeFenceLifecycleGameTests {
    private static final String TEMPLATE="service_npc_spawn_test_empty";
    private static final Direction[] DIRS={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
    private static BlockState fixture(int i) {
        var state=BlockRegistry.WOODEN_FENCE.get().defaultBlockState().setValue(WoodenFenceBlock.FACING,DIRS[i/16]);
        for(int d=0;d<4;d++) state=state.setValue(WoodenFenceBlock.property(DIRS[d]),(i & 1<<d)!=0);
        return WoodenFenceBlock.materialize(state);
    }
    private static BlockPos fixturePos(int i) { return new BlockPos(120000+(i%8)*3,80,120064+(i/8)*3); }

    @GameTest(batch="edge_fence_lifecycle", template=TEMPLATE,timeoutTicks=300)
    public static void saveOrReadAllLegacyLayoutsAcrossRealProcessRestart(GameTestHelper h) {
        String phase=System.getProperty("britannia.edgeFenceLifecyclePhase","");
        h.assertTrue(phase.equals("write") || phase.equals("read"),"Supply -PedgeFenceLifecyclePhase=write then read using the same disposable world");
        for(int i=0;i<64;i++) {
            var pos=fixturePos(i);
            h.getLevel().getChunk(pos); // Explicit fixture setup/read, outside the implementation's neighbor queries.
            if(phase.equals("write")) h.getLevel().setBlock(pos,fixture(i),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
            else {
                var state=h.getLevel().getBlockState(pos);
                h.assertTrue(state.is(BlockRegistry.WOODEN_FENCE.get()),"restart fixture missing "+i);
                h.assertTrue(state.getValue(WoodenFenceBlock.FACING)==fixture(i).getValue(WoodenFenceBlock.FACING)
                    && state.getValue(WoodenFenceBlock.LAYOUT_CODE)==i%16,"real restart altered legacy layout "+i+" "+state);
                var fence=(WoodenFenceBlock)state.getBlock();
                var updated=fence.deriveConnections(state,h.getLevel(),pos);
                h.assertTrue(updated.getValue(WoodenFenceBlock.FACING)==state.getValue(WoodenFenceBlock.FACING)
                    && updated.getValue(WoodenFenceBlock.LAYOUT_CODE)==state.getValue(WoodenFenceBlock.LAYOUT_CODE),"reload reconciliation altered geometry");
            }
        }
        h.getLevel().getServer().saveEverything(false,true,true);
        System.out.println("EDGE_LIFECYCLE phase="+phase+" 64 actual Anvil states verified; orderly save and process shutdown follow");
        h.succeed();
    }

    @GameTest(batch="edge_fence_lifecycle", template=TEMPLATE,timeoutTicks=1400)
    public static void chunkBoundaryUnloadAndBothLoadOrdersKeepPinnedEdges(GameTestHelper h) { boundary(h,0); }
    private static void boundary(GameTestHelper h,int index) {
        if(index==4) { h.succeed();return; }
        boolean axisX=index<2, reverse=(index&1)!=0;
        BlockPos a=axisX?new BlockPos(121615,80,121600):new BlockPos(123200,80,123215);
        BlockPos b=axisX?a.east():a.south();
        Direction direction=axisX?Direction.EAST:Direction.SOUTH;
        Direction facing=axisX?Direction.SOUTH:Direction.EAST;
        ChunkPos ca=new ChunkPos(a),cb=new ChunkPos(b);
        var fence=BlockRegistry.WOODEN_FENCE.get();
        var fixed=fence.defaultBlockState().setValue(WoodenFenceBlock.LAYOUT_CODE,0).setValue(WoodenFenceBlock.FACING,facing);
        h.getLevel().getChunk(a);h.getLevel().getChunk(b);
        h.getLevel().setBlock(a,fixed,Block.UPDATE_ALL);h.getLevel().setBlock(b,fixed,Block.UPDATE_ALL);
        h.assertTrue(h.getLevel().getBlockState(a).getValue(WoodenFenceBlock.property(direction)),"loaded boundary contact missing");
        h.getLevel().getServer().saveEverything(false,true,true);
        // No forced tickets/player are created for these remote fixture chunks. UNKNOWN read
        // tickets expire naturally; wait for real cache eviction rather than simulating events.
        h.startSequence().thenWaitUntil(()->{
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(ca.x,ca.z)==null
                    && h.getLevel().getChunkSource().getChunkNow(cb.x,cb.z)==null,"waiting for actual unload case="+index);
        }).thenExecute(()->{
            BlockPos first=reverse?b:a,second=reverse?a:b;
            Direction towardSecond=reverse?direction.getOpposite():direction;
            h.getLevel().getChunk(first);
            ChunkPos absent=new ChunkPos(second);
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(absent.x,absent.z)==null,"first load pulled neighboring full chunk");
            var saved=h.getLevel().getBlockState(first);
            var alone=fence.deriveConnections(saved,h.getLevel(),first);
            h.assertTrue(!alone.getValue(WoodenFenceBlock.property(towardSecond)),"unloaded neighbor still connected");
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(absent.x,absent.z)==null,"reconciliation implicitly loaded neighbor");
            h.assertTrue(alone.getValue(WoodenFenceBlock.FACING)==facing && alone.getValue(WoodenFenceBlock.LAYOUT_CODE)==0,"partial load moved layout");
            h.getLevel().setBlock(first,alone,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
            h.getLevel().getChunk(second);
            var joined=fence.deriveConnections(h.getLevel().getBlockState(first),h.getLevel(),first);
            h.assertTrue(joined.getValue(WoodenFenceBlock.property(towardSecond)),"reloaded boundary did not reconnect");
            h.assertTrue(joined.getValue(WoodenFenceBlock.FACING)==facing && joined.getValue(WoodenFenceBlock.LAYOUT_CODE)==0,"reloaded boundary moved layout");
            h.getLevel().setBlock(first,joined,Block.UPDATE_ALL);
            System.out.println("EDGE_LIFECYCLE real unload/reload "+(axisX?"X":"Z")+" local15/16 reverse="+reverse+" positions="+a+" / "+b);
        }).thenExecuteAfter(5,()->boundary(h,index+1));
    }
}
