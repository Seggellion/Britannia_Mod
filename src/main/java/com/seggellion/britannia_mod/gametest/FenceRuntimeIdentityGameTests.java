package com.seggellion.britannia_mod.gametest;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.grabbyhands.diagnostics.GrabbyEnvironmentReport;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.*;
import java.nio.file.*;
import java.util.*;

/** Same-coordinate artifact comparison. Constructed hits are not mouse/render acceptance. */
@GameTestHolder("britannia_edge_fence")
@PrefixGameTestTemplate(false)
public final class FenceRuntimeIdentityGameTests {
    private static final Gson JSON = new Gson();
    private static final Direction[] DIRS={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
    @GameTest(batch="fence_runtime_identity",template="service_npc_spawn_test_empty",timeoutTicks=1600)
    public static void fixedCoordinateArtifactPlacementAndRestart(GameTestHelper h) {
        var fence=BlockRegistry.WOODEN_FENCE.get();
        var properties=new TreeMap<String,Object>();
        for(var p:fence.getStateDefinition().getProperties()) properties.put(p.getName(),p.getPossibleValues().toString());
        emit(Map.of("phase","schema","class",fence.getClass().getName(),"properties",properties,
            "default",state(fence.defaultBlockState()),"states",fence.getStateDefinition().getPossibleStates().size(),
            "origin",ModList.get().getModFileById("britannia_mod").getFile().getFilePath().toString(),
            "provenance",GrabbyEnvironmentReport.lines(h.getLevel().getServer())));
        if(System.getProperty("britannia.fenceRuntimePhase","write").equals("read")) { read(h);return; }
        var player=ManagedResourceTestPlayers.survival(h.getLevel(),"FenceRuntimeTrace");
        player.setGameMode(GameType.CREATIVE);
        scenario(h,player,0,new ArrayList<>(),new JsonArray());
    }
    private static BlockPos position(int i) { return new BlockPos(20000+(i%8)*8,80,22000+(i/8)*8); }
    private static void scenario(GameTestHelper h,ServerPlayer p,int i,List<String> failures,JsonArray fixtures) {
        if(i==48) { legacy(h,fixtures); finish(h,p,failures,fixtures); return; }
        Direction facing=DIRS[i/12], step=facing.getAxis()==Direction.Axis.Z?Direction.EAST:Direction.SOUTH;
        if(i%12>=6) step=step.getOpposite();
        int surface=i%6/2; boolean oblique=(i%2)==1;
        var a=position(i); var b=a.relative(step); Direction extend=step;
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=-1;y<=2;y++) h.getLevel().setBlock(a.offset(x,y,z),Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
        p.setYRot(facing.getOpposite().toYRot()+(oblique?20:0));p.setXRot(25);
        p.setPos(a.getX()+.5-facing.getStepX()*2.5,a.getY(),a.getZ()+.5-facing.getStepZ()*2.5);
        support(h,p,a.relative(facing),facing.getOpposite(),i,"place-A");
        var original=h.getLevel().getBlockState(a);
        String outline=original.getShape(h.getLevel(),a).toAabbs().toString(),collision=original.getCollisionShape(h.getLevel(),a).toAabbs().toString();
        snapshot(h,i,"A-isolated-immediate",a,b);
        h.runAfterDelay(3,()->{
            snapshot(h,i,"A-isolated-settled-before-B",a,b);
            if(surface==0) {
                Vec3 hit=Vec3.atLowerCornerOf(a).add(extend==Direction.EAST?1:extend==Direction.WEST?0:facing==Direction.EAST?.875:.125,
                    .95,extend==Direction.SOUTH?1:extend==Direction.NORTH?0:facing==Direction.SOUTH?.875:.125);
                place(h,p,new BlockHitResult(hit,extend,a,false),i,"endpoint-B");
            } else if(surface==1) support(h,p,b.relative(facing),facing.getOpposite(),i,"support-B");
            else support(h,p,b.below(),Direction.UP,i,"ground-B");
            snapshot(h,i,"after-B-immediate",a,b);check(h,i,"after-B-immediate",a,b,original,outline,collision,failures,true);
            h.runAfterDelay(1,()->{
                snapshot(h,i,"after-B-scheduled-tick",a,b);check(h,i,"after-B-scheduled-tick",a,b,original,outline,collision,failures,true);
                h.runAfterDelay(4,()->{
                    snapshot(h,i,"after-B-settled",a,b);check(h,i,"after-B-settled",a,b,original,outline,collision,failures,true);
                    h.getLevel().removeBlock(b,false);snapshot(h,i,"removed-B-immediate",a,b);
                    check(h,i,"removed-B-immediate",a,b,original,outline,collision,failures,false);
                    h.runAfterDelay(3,()->{
                        snapshot(h,i,"removed-B-settled",a,b);check(h,i,"removed-B-settled",a,b,original,outline,collision,failures,false);
                        fixture(fixtures,a,h.getLevel().getBlockState(a));scenario(h,p,i+1,failures,fixtures);
                    });
                });
            });
        });
    }
    private static void support(GameTestHelper h,ServerPlayer p,BlockPos owner,Direction face,int i,String label) {
        h.getLevel().setBlock(owner,Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
        place(h,p,new BlockHitResult(Vec3.atCenterOf(owner).add(face.getStepX()*.5,face.getStepY()*.5,face.getStepZ()*.5),face,owner,false),i,label);
    }
    private static void place(GameTestHelper h,ServerPlayer p,BlockHitResult hit,int i,String label) {
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(BlockRegistry.WOODEN_FENCE.get()));
        var context=new BlockPlaceContext(new UseOnContext(p,InteractionHand.MAIN_HAND,hit));
        var neighbors=new TreeMap<String,Object>();for(var d:DIRS) neighbors.put(d.getName(),state(h.getLevel().getBlockState(context.getClickedPos().relative(d))));
        emit(Map.of("phase",label,"case",i,"yaw",p.getYRot(),"pitch",p.getXRot(),"clicked",hit.getBlockPos().toShortString(),
            "face",hit.getDirection().getName(),"hit",hit.getLocation().toString(),"resolvedOwner",context.getClickedPos().toShortString(),
            "itemComponents",p.getMainHandItem().getComponents().toString(),"neighbors",neighbors));
        var result=((BlockItem)p.getMainHandItem().getItem()).place(context);
        h.assertTrue(result.consumesAction(),"placement failed case="+i+" "+label);
    }
    private static void check(GameTestHelper h,int i,String phase,BlockPos a,BlockPos b,BlockState old,String outline,String collision,List<String> failures,boolean hasB) {
        var now=h.getLevel().getBlockState(a);boolean moved=now.getValue(BlockStateProperties.HORIZONTAL_FACING)!=old.getValue(BlockStateProperties.HORIZONTAL_FACING)
            ||!outline.equals(now.getShape(h.getLevel(),a).toAabbs().toString())||!collision.equals(now.getCollisionShape(h.getLevel(),a).toAabbs().toString());
        Property<?> selector=now.getBlock().getStateDefinition().getProperty("layout_code");
        if(selector!=null) moved |= !state(now).getAsJsonObject("Properties").get("layout_code").equals(state(old).getAsJsonObject("Properties").get("layout_code"));
        if(hasB) moved |= h.getLevel().getBlockState(b).getValue(BlockStateProperties.HORIZONTAL_FACING)!=old.getValue(BlockStateProperties.HORIZONTAL_FACING);
        if(moved) failures.add("case="+i+" phase="+phase+" A="+a.toShortString()+" old="+old+" now="+now);
    }
    private static void snapshot(GameTestHelper h,int i,String phase,BlockPos a,BlockPos b) {
        var s=h.getLevel().getBlockState(a);
        emit(Map.of("case",i,"phase",phase,"ownerA",a.toShortString(),"ownerB",b.toShortString(),"A",state(s),"B",state(h.getLevel().getBlockState(b)),
            "outlineA",s.getShape(h.getLevel(),a).toAabbs().toString(),"collisionA",s.getCollisionShape(h.getLevel(),a).toAabbs().toString(),"clientState","unobserved; constructed server context"));
    }
    private static void legacy(GameTestHelper h,JsonArray fixtures) {
        if(BlockRegistry.WOODEN_FENCE.get().getStateDefinition().getProperty("layout_code")==null) return;
        for(int i=0;i<64;i++) {
            var pos=new BlockPos(20400+(i%16)*3,80,22400+(i/16)*3);var props=new JsonObject();props.addProperty("facing",DIRS[i/16].getName());
            for(int n=0;n<4;n++)props.addProperty(DIRS[n].getName(),Boolean.toString((i&(1<<n))!=0));
            var encoded=new JsonObject();encoded.addProperty("Name","britannia_mod:wooden_fence");encoded.add("Properties",props);
            var old=BlockState.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow();h.getLevel().setBlock(pos,old,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
            String shape=old.getShape(h.getLevel(),pos).toAabbs().toString(),collision=old.getCollisionShape(h.getLevel(),pos).toAabbs().toString();
            emit(Map.of("phase","legacy-before","case",i,"ownerA",pos.toShortString(),"A",state(old),"outlineA",shape,"collisionA",collision));
            h.getLevel().setBlock(pos.above(),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
            var now=h.getLevel().getBlockState(pos);
            h.assertTrue(now.getValue(BlockStateProperties.HORIZONTAL_FACING)==DIRS[i/16]&&shape.equals(now.getShape(h.getLevel(),pos).toAabbs().toString())&&collision.equals(now.getCollisionShape(h.getLevel(),pos).toAabbs().toString()),"legacy migration moved case="+i);
            emit(Map.of("phase","legacy-after","case",i,"ownerA",pos.toShortString(),"A",state(now),"outlineA",now.getShape(h.getLevel(),pos).toAabbs().toString(),"collisionA",now.getCollisionShape(h.getLevel(),pos).toAabbs().toString()));fixture(fixtures,pos,now);
        }
    }
    private static JsonObject state(BlockState s) { return BlockState.CODEC.encodeStart(JsonOps.INSTANCE,s).getOrThrow().getAsJsonObject(); }
    private static void fixture(JsonArray fixtures,BlockPos p,BlockState s) {
        var j=new JsonObject();j.addProperty("x",p.getX());j.addProperty("y",p.getY());j.addProperty("z",p.getZ());j.add("state",state(s));fixtures.add(j);
    }
    private static void finish(GameTestHelper h,ServerPlayer p,List<String> failures,JsonArray fixtures) {
        try { Files.writeString(Path.of("fence-runtime-fixtures.json"),JSON.toJson(fixtures)); } catch(Exception e) {throw new RuntimeException(e);}
        h.getLevel().getServer().getPlayerList().remove(p);h.getLevel().getServer().saveEverything(false,true,true);
        emit(Map.of("phase","summary-write","cases",48,"fixtures",fixtures.size(),"divergences",failures.size(),"firstDivergence",failures.isEmpty()?"none":failures.getFirst()));
        h.assertTrue(failures.isEmpty(),"fixed owner/layout changed; "+(failures.isEmpty()?"":failures.getFirst()));h.succeed();
    }
    private static void read(GameTestHelper h) {
        JsonArray fixtures;try {fixtures=JsonParser.parseString(Files.readString(Path.of("fence-runtime-fixtures.json"))).getAsJsonArray();}catch(Exception e){throw new RuntimeException(e);}
        for(var element:fixtures) {
            var j=element.getAsJsonObject();var p=new BlockPos(j.get("x").getAsInt(),j.get("y").getAsInt(),j.get("z").getAsInt());h.getLevel().getChunk(p);
            var expected=BlockState.CODEC.parse(JsonOps.INSTANCE,j.get("state")).getOrThrow();var actual=h.getLevel().getBlockState(p);
            h.assertTrue(state(expected).equals(state(actual)),"restart changed saved state at "+p+" "+actual);
            emit(Map.of("phase","restart-read","ownerA",p.toShortString(),"A",state(actual),"outlineA",actual.getShape(h.getLevel(),p).toAabbs().toString(),"collisionA",actual.getCollisionShape(h.getLevel(),p).toAabbs().toString()));
        }
        emit(Map.of("phase","summary-read","fixtures",fixtures.size(),"exactSavedStatesMatch",true));h.succeed();
    }
    private static void emit(Map<String,?> record) {System.out.println("FENCE_RUNTIME_TRACE "+JSON.toJson(record));}
}
