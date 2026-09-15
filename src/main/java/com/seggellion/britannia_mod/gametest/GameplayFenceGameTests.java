package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.*;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class GameplayFenceGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final Direction[] DIRECTIONS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    @GameTest(template = TEMPLATE)
    public static void everyNeighborhoodConvergesAcrossFacingsAndPlacementOrders(GameTestHelper h) {
        var center = h.absolutePos(new BlockPos(5,3,5));
        for (int mask=0; mask<16; mask++) {
            var positions = new ArrayList<BlockPos>(); positions.add(center);
            for (int bit=0; bit<4; bit++) if ((mask & (1<<bit)) != 0) positions.add(center.relative(DIRECTIONS[bit]));
            Map<BlockPos, BlockState> expected = null;
            var orders = new ArrayList<List<BlockPos>>(); permutations(positions, 0, orders);
            for (var facing : DIRECTIONS) for (var order : orders) {
                clear(h, center);
                for (var pos : order) place(h, pos, facing);
                var observed = snapshot(h, positions);
                if (mask == 0) h.assertTrue(observed.get(center).getValue(WoodenFenceBlock.FACING) == facing, "isolated orientation lost");
                else if (expected == null) expected = observed;
                else h.assertTrue(expected.equals(observed), "history-dependent mask="+mask+" facing="+facing+" order="+order);
                for (var entry : observed.entrySet()) {
                    var state = entry.getValue();
                    var fence = (WoodenFenceBlock) state.getBlock();
                    h.assertTrue(state == fence.deriveConnections(state, h.getLevel(), entry.getKey()), "state did not reach a fixed point");
                }
            }
        }
        clear(h,center); h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void directLAndTemporaryTAreEquivalentInAllRotationsAndMirrors(GameTestHelper h) {
        var center = h.absolutePos(new BlockPos(5,3,5));
        for (var rotation : Rotation.values()) for (var mirror : Mirror.values()) {
            clear(h,center);
            Direction a = rotation.rotate(mirror.mirror(Direction.SOUTH));
            Direction b = rotation.rotate(mirror.mirror(Direction.WEST));
            var positions = List.of(center,center.relative(a),center.relative(a,2),center.relative(b),center.relative(b,2));
            for (var pos : positions) place(h,pos,Direction.SOUTH);
            var direct = snapshot(h,positions);
            place(h,center.relative(a.getOpposite()),Direction.WEST);
            h.getLevel().removeBlock(center.relative(a.getOpposite()),false);
            h.assertTrue(direct.equals(snapshot(h,positions)), "L -> T -> L changed state "+rotation+" "+mirror);
            // The straight arm must meet the corner's occupied outside edge.
            var arm = h.getLevel().getBlockState(center.relative(a)).getValue(WoodenFenceBlock.FACING);
            h.assertTrue(arm == b.getOpposite(), "arm is on the wrong side of the L corner");
        }
        clear(h,center); h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void everyOccupiedStripHasVanillaHeightWithoutFillingTheInterior(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(3,3,3));
        for (int mask=0; mask<16; mask++) for (var facing : DIRECTIONS) {
            BlockState state = BlockRegistry.WOODEN_FENCE.get().defaultBlockState().setValue(WoodenFenceBlock.FACING,facing)
                    .setValue(WoodenFenceBlock.NORTH,(mask&1)!=0).setValue(WoodenFenceBlock.EAST,(mask&2)!=0)
                    .setValue(WoodenFenceBlock.SOUTH,(mask&4)!=0).setValue(WoodenFenceBlock.WEST,(mask&8)!=0);
            var outline = state.getShape(h.getLevel(),pos);
            var collision = state.getCollisionShape(h.getLevel(),pos);
            h.assertTrue(outline.max(Direction.Axis.Y)==1.0 && collision.max(Direction.Axis.Y)==1.5, "wrong bounds "+mask+facing);
            for (var box : collision.toAabbs()) {
                h.assertTrue(box.maxY == 1.5 && box.minY == 0.0, "one occupied arm has shorter collision");
                h.assertTrue(box.getXsize() <= 0.25 || box.getZsize() <= 0.25, "collision fills the interior");
            }
            for (var box : outline.toAabbs()) {
                var probe = net.minecraft.world.phys.shapes.Shapes.box(box.minX,1.25,box.minZ,box.maxX,1.49,box.maxZ);
                h.assertTrue(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(probe,collision,net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST), "gap above an occupied arm");
            }
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void shovelMadePathPersistsUnderCustomFenceWithVanillaControls(GameTestHelper h) {
        var player = ManagedResourceTestPlayers.survival(h.getLevel(), "M8Path");
        var pos = h.absolutePos(new BlockPos(3,2,3));
        var control = pos.east(3);
        try {
            h.getLevel().setBlock(pos,Blocks.GRASS_BLOCK.defaultBlockState(),Block.UPDATE_ALL);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SHOVEL));
            var hit = new BlockHitResult(pos.getCenter(),Direction.UP,pos,false);
            player.getMainHandItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit));
            h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.DIRT_PATH), "vanilla shovel did not create path");
            place(h,pos.above(),Direction.NORTH);
            h.getLevel().setBlock(control,Blocks.DIRT_PATH.defaultBlockState(),Block.UPDATE_ALL);
            h.getLevel().setBlock(control.above(),Blocks.OAK_FENCE.defaultBlockState(),Block.UPDATE_ALL);
            h.assertTrue(h.getLevel().getBlockState(pos).canSurvive(h.getLevel(),pos), "custom fence destroys path");
            h.assertTrue(!h.getLevel().getBlockState(control).canSurvive(h.getLevel(),control), "vanilla oak rule was changed");
            // Even an already queued conversion is canceled only under this custom fence.
            h.getLevel().scheduleTick(pos,Blocks.DIRT_PATH,1);
            WoodenFenceLoadHandler.inspect(h.getLevel(),new ChunkPos(pos));
        } finally { h.getLevel().getServer().getPlayerList().remove(player); }
        h.runAfterDelay(5, () -> {
            h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.DIRT_PATH), "scheduled/reload update converted custom path");
            h.assertTrue(h.getLevel().getBlockState(control).is(Blocks.DIRT), "vanilla control no longer converts");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void loadedRunRepairsSavedFacingWithoutUpdateLoops(GameTestHelper h) {
        var start = h.absolutePos(new BlockPos(1,4,5));
        var positions = new ArrayList<BlockPos>();
        for (int i=0;i<12;i++) { var pos=start.east(i); positions.add(pos); place(h,pos,Direction.SOUTH); }
        place(h,start.south(),Direction.WEST);
        var expected = snapshot(h,positions);
        for (var pos:positions) h.getLevel().setBlock(pos,h.getLevel().getBlockState(pos).setValue(WoodenFenceBlock.FACING,Direction.SOUTH),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        for (var chunk:positions.stream().map(ChunkPos::new).distinct().toList()) WoodenFenceLoadHandler.inspect(h.getLevel(),chunk);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(expected.equals(snapshot(h,positions)), "saved run did not reconcile"))
                .thenExecuteAfter(10, () -> h.assertTrue(expected.equals(snapshot(h,positions)), "settled run oscillated"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void ordinaryPlayerCollisionBlocksWalkingAndNormalJumpEnvelope(GameTestHelper h) {
        var player = ManagedResourceTestPlayers.survival(h.getLevel(), "M8Collision");
        var pos = h.absolutePos(new BlockPos(4,3,4));
        try {
            for(int x=-1;x<=1;x++)for(int y=0;y<=4;y++)for(int z=-2;z<=3;z++)
                h.getLevel().setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
            for (var mode:List.of(GameType.SURVIVAL,GameType.ADVENTURE)) for (var block:List.of(BlockRegistry.WOODEN_FENCE.get(),Blocks.OAK_FENCE)) {
                h.getLevel().setBlock(pos,block.defaultBlockState(),Block.UPDATE_ALL);
                player.setGameMode(mode);
                for (double feetAboveFloor:new double[]{0,1.249}) {
                    player.setPos(pos.getX()+0.5,pos.getY()+feetAboveFloor,pos.getZ()-0.8);
                    player.setOnGround(feetAboveFloor == 0);
                    player.move(MoverType.SELF,new Vec3(0,0,2));
                    h.assertTrue(player.getZ() < pos.getZ()+0.5, "walk/jump envelope crossed fence "+mode+" "+block);
                }
                player.setPos(pos.getX()+0.5,pos.getY()+1.51,pos.getZ()-0.8);
                player.setOnGround(false);
                player.move(MoverType.SELF,new Vec3(0,0,2));
                h.assertTrue(player.getZ() > pos.getZ()+1, "collision above height "+mode+" "+block+" actual="+player.position()+" fence="+pos);
            }
        } finally { h.getLevel().getServer().getPlayerList().remove(player); }
        h.succeed();
    }

    private static void place(GameTestHelper h, BlockPos pos, Direction facing) {
        var fence=BlockRegistry.WOODEN_FENCE.get();
        h.getLevel().setBlock(pos,fence.deriveConnections(fence.defaultBlockState().setValue(WoodenFenceBlock.FACING,facing),h.getLevel(),pos),Block.UPDATE_ALL);
    }
    private static void clear(GameTestHelper h,BlockPos center) {
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++) h.getLevel().setBlock(center.offset(x,0,z),Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
    }
    private static Map<BlockPos,BlockState> snapshot(GameTestHelper h,List<BlockPos> positions) {
        var map=new LinkedHashMap<BlockPos,BlockState>();for(var pos:positions)map.put(pos,h.getLevel().getBlockState(pos));return map;
    }
    private static void permutations(List<BlockPos> list,int start,List<List<BlockPos>> output) {
        if(start==list.size()){output.add(List.copyOf(list));return;}
        for(int i=start;i<list.size();i++){Collections.swap(list,start,i);permutations(list,start+1,output);Collections.swap(list,start,i);}
    }
}
