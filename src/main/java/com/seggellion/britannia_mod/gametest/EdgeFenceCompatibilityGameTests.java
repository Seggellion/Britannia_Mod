package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.block.WoodenFenceBlock;
import com.seggellion.britannia_mod.block.WoodenFenceGeometry;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("britannia_edge_fence")
@PrefixGameTestTemplate(false)
public final class EdgeFenceCompatibilityGameTests {
    private static final String TEMPLATE="service_npc_spawn_test_empty";
    private static final Direction[] DIRS={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
    private static BlockState legacy(Direction facing,int mask) {
        BlockState state=BlockRegistry.WOODEN_FENCE.get().defaultBlockState().setValue(WoodenFenceBlock.FACING,facing);
        for(int i=0;i<4;i++) state=state.setValue(WoodenFenceBlock.property(DIRS[i]),(mask & 1<<i)!=0);
        return state;
    }
    private static VoxelShape edge(Direction direction,double height) {
        return switch(direction) {
            case NORTH -> Shapes.box(0,0,0,1,height,.25);
            case EAST -> Shapes.box(.75,0,0,1,height,1);
            case SOUTH -> Shapes.box(0,0,.75,1,height,1);
            case WEST -> Shapes.box(0,0,0,.25,height,1);
            default -> throw new AssertionError();
        };
    }
    /** Independent old Java oracle, intentionally retains its T/cross collision semantics. */
    private static VoxelShape oldShape(Direction facing,int mask,double height) {
        int count=Integer.bitCount(mask);
        if(count<2 || mask==5 || mask==10) return edge(facing,height);
        if(count==2) {
            VoxelShape result=Shapes.empty();
            for(int i=0;i<4;i++) if((mask & 1<<i)==0) result=Shapes.or(result,edge(DIRS[i],height));
            return result;
        }
        Direction primary=facing;
        if(count==3) for(int i=0;i<4;i++) if((mask & 1<<i)==0) primary=DIRS[i];
        return Shapes.or(edge(primary,height),edge(primary.getCounterClockWise(),height));
    }
    private static void equal(GameTestHelper h,VoxelShape a,VoxelShape b,String label) {
        h.assertTrue(!Shapes.joinIsNotEmpty(a,b,BooleanOp.NOT_SAME),label+" "+a.toAabbs()+" != "+b.toAabbs());
    }
    @GameTest(template=TEMPLATE)
    public static void allLegacyStatesDecodeAndMaterializeBeforeFlagCorrection(GameTestHelper h) {
        var fence=BlockRegistry.WOODEN_FENCE.get();
        h.assertTrue(fence.getStateDefinition().getPossibleStates().size()==2112,"unexpected state count");
        var pos=h.absolutePos(new BlockPos(5,4,5));
        for(var facing:DIRS) for(int mask=0;mask<16;mask++) {
            CompoundTag tag=new CompoundTag(),props=new CompoundTag();
            tag.putString("Name","britannia_mod:wooden_fence");
            props.putString("facing",facing.getName());
            for(int i=0;i<4;i++) props.putString(DIRS[i].getName(),Boolean.toString((mask&1<<i)!=0));
            tag.put("Properties",props);
            BlockState saved=BlockState.CODEC.parse(NbtOps.INSTANCE,tag).getOrThrow();
            h.assertTrue(saved==legacy(facing,mask),"actual missing-property codec default changed");
            h.assertTrue(saved.getValue(WoodenFenceBlock.LAYOUT_CODE)==32,"missing selector is not sentinel");
            equal(h,oldShape(facing,mask,1),saved.getShape(h.getLevel(),pos),"legacy outline "+saved);
            equal(h,oldShape(facing,mask,1.5),saved.getCollisionShape(h.getLevel(),pos),"legacy collision "+saved);
            h.assertTrue(saved.getValue(WoodenFenceBlock.LAYOUT_CODE)==32,"query wrote a legacy state");
            BlockState migrated=fence.deriveConnections(saved,h.getLevel(),pos);
            h.assertTrue(migrated.getValue(WoodenFenceBlock.FACING)==facing && migrated.getValue(WoodenFenceBlock.LAYOUT_CODE)==mask,"capture occurred after correction");
            equal(h,oldShape(facing,mask,1),migrated.getShape(h.getLevel(),pos),"materialized outline");
            equal(h,oldShape(facing,mask,1.5),migrated.getCollisionShape(h.getLevel(),pos),"materialized collision");
            Tag encoded=BlockState.CODEC.encodeStart(NbtOps.INSTANCE,migrated).getOrThrow();
            h.assertTrue(BlockState.CODEC.parse(NbtOps.INSTANCE,encoded).getOrThrow()==migrated,"serialized round trip");
            for(int round=0;round<20;round++) h.assertTrue(fence.deriveConnections(migrated,h.getLevel(),pos)==migrated,"not a fixed point");
        }
        h.succeed();
    }
    private static VoxelShape transform(VoxelShape shape,Mirror mirror,Rotation rotation) {
        VoxelShape result=Shapes.empty();
        for(AABB box:shape.toAabbs()) {
            double minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxZ=maxX;
            for(double x:new double[]{box.minX,box.maxX})for(double z:new double[]{box.minZ,box.maxZ}) {
                double px=mirror==Mirror.FRONT_BACK?1-x:x, pz=mirror==Mirror.LEFT_RIGHT?1-z:z;
                double tx=px,tz=pz;
                switch(rotation) {
                    case CLOCKWISE_90 -> { tx=1-pz; tz=px; }
                    case CLOCKWISE_180 -> { tx=1-px; tz=1-pz; }
                    case COUNTERCLOCKWISE_90 -> { tx=pz; tz=1-px; }
                }
                minX=Math.min(minX,tx);maxX=Math.max(maxX,tx);minZ=Math.min(minZ,tz);maxZ=Math.max(maxZ,tz);
            }
            result=Shapes.or(result,Shapes.box(minX,box.minY,minZ,maxX,box.maxY,maxZ));
        }
        return result;
    }
    @GameTest(template=TEMPLATE)
    public static void everyLayoutTransformMatchesWorldGeometryAndRoundTrips(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(4,4,4));
        for(var facing:DIRS)for(int code=0;code<33;code++) {
            BlockState state=legacy(facing,11).setValue(WoodenFenceBlock.LAYOUT_CODE,code);
            for(var mirror:Mirror.values()) for(var rotation:Rotation.values()) {
                BlockState transformed=state.mirror(mirror).rotate(rotation);
                equal(h,transform(state.getShape(h.getLevel(),pos),mirror,rotation),transformed.getShape(h.getLevel(),pos),"outline transform "+state+mirror+rotation);
                equal(h,transform(state.getCollisionShape(h.getLevel(),pos),mirror,rotation),transformed.getCollisionShape(h.getLevel(),pos),"collision transform");
                for(var d:DIRS) h.assertTrue(transformed.getValue(WoodenFenceBlock.property(rotation.rotate(mirror.mirror(d))))==state.getValue(WoodenFenceBlock.property(d)),"flags not transformed");
            }
            var concrete=WoodenFenceBlock.materialize(state);
            h.assertTrue(state.mirror(Mirror.LEFT_RIGHT).mirror(Mirror.LEFT_RIGHT)==concrete,"double mirror");
            h.assertTrue(state.mirror(Mirror.FRONT_BACK).mirror(Mirror.FRONT_BACK)==concrete,"double front/back mirror");
            BlockState spun=state;
            for(int i=0;i<4;i++)spun=spun.rotate(Rotation.CLOCKWISE_90);
            h.assertTrue(spun==concrete,"four turns");
            h.assertTrue(state.rotate(Rotation.NONE)==concrete && state.mirror(Mirror.NONE)==concrete,"semantic identity");
        }
        h.succeed();
    }
    @GameTest(template=TEMPLATE)
    public static void contactsAreSymmetricLocalAndIndependentOfFlags(GameTestHelper h) {
        var fence=BlockRegistry.WOODEN_FENCE.get();
        for(var aFace:DIRS)for(int aCode=0;aCode<32;aCode++)for(var bFace:DIRS)for(int bCode=0;bCode<32;bCode++) {
            var a=legacy(aFace,0).setValue(WoodenFenceBlock.LAYOUT_CODE,aCode);
            var b=legacy(bFace,15).setValue(WoodenFenceBlock.LAYOUT_CODE,bCode);
            var flaggedA=a.setValue(WoodenFenceBlock.NORTH,true).setValue(WoodenFenceBlock.EAST,true)
                .setValue(WoodenFenceBlock.SOUTH,true).setValue(WoodenFenceBlock.WEST,true);
            var unflaggedB=b.setValue(WoodenFenceBlock.NORTH,false).setValue(WoodenFenceBlock.EAST,false)
                .setValue(WoodenFenceBlock.SOUTH,false).setValue(WoodenFenceBlock.WEST,false);
            for(var direction:DIRS) {
                boolean contact=WoodenFenceGeometry.contacts(a,b,direction);
                h.assertTrue(contact==WoodenFenceGeometry.contacts(flaggedA,unflaggedB,direction),"contact depends on current flags");
                h.assertTrue(contact==WoodenFenceGeometry.contacts(b,a,direction.getOpposite()),"asymmetric contact");
                h.assertTrue(contact==WoodenFenceGeometry.contacts(a.rotate(Rotation.CLOCKWISE_90),b.rotate(Rotation.CLOCKWISE_90),direction.getClockWise()),"rotation changed contact");
            }
        }
        var north=legacy(Direction.NORTH,0).setValue(WoodenFenceBlock.LAYOUT_CODE,0);
        var south=north.setValue(WoodenFenceBlock.FACING,Direction.SOUTH);
        var west=north.setValue(WoodenFenceBlock.FACING,Direction.WEST);
        h.assertTrue(WoodenFenceGeometry.contacts(north,north,Direction.EAST),"straight continuation missing");
        h.assertTrue(!WoodenFenceGeometry.contacts(north,south,Direction.EAST),"gap treated as join");
        h.assertTrue(WoodenFenceGeometry.contacts(north,west,Direction.EAST),"perpendicular contact missing");
        h.assertTrue(!WoodenFenceGeometry.contacts(north,west,Direction.SOUTH),"union AABB false contact");
        h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void bothLegacyPatioTemplatesKeepPaletteAndPreviewGeometry(GameTestHelper h) throws java.io.IOException {
        var fence=BlockRegistry.WOODEN_FENCE.get();
        var pos=h.absolutePos(new BlockPos(4,4,4));
        int copies=0;
        for(String path:new String[]{"assets/britannia_mod/structures/large_patio.nbt","data/britannia_mod/structures/large_patio.nbt"}) {
            CompoundTag tag;
            try(var source=EdgeFenceCompatibilityGameTests.class.getClassLoader().getResourceAsStream(path)) {
                h.assertTrue(source!=null,"missing concrete patio fixture "+path);
                var input=new java.io.BufferedInputStream(source);
                input.mark(2);
                int magic=input.read()<<8 | input.read(); input.reset();
                tag=magic==0x1f8b?NbtIo.readCompressed(input,NbtAccounter.unlimitedHeap())
                    :NbtIo.read(new java.io.DataInputStream(input),NbtAccounter.unlimitedHeap());
            }
            var template=new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate();
            template.load(h.getLevel().holderLookup(net.minecraft.core.registries.Registries.BLOCK),tag);
            int wooden=0;
            for(Tag value:tag.getList("palette",Tag.TAG_COMPOUND)) {
                CompoundTag entry=(CompoundTag)value;
                if(!entry.getString("Name").equals("britannia_mod:wooden_fence")) continue;
                wooden++;
                BlockState state=NbtUtils.readBlockState(h.getLevel().holderLookup(net.minecraft.core.registries.Registries.BLOCK),entry);
                h.assertTrue(state.getValue(WoodenFenceBlock.LAYOUT_CODE)==32,"patio was rewritten instead of testing old state");
                int mask=WoodenFenceBlock.connectionMask(state);
                equal(h,oldShape(state.getValue(WoodenFenceBlock.FACING),mask,1),state.getShape(h.getLevel(),pos),"patio outline");
                for(var mirror:Mirror.values())for(var rotation:Rotation.values()) {
                    var transformed=state.mirror(mirror).rotate(rotation);
                    equal(h,transform(state.getShape(h.getLevel(),pos),mirror,rotation),transformed.getShape(h.getLevel(),pos),"patio preview/placement transform");
                }
            }
            h.assertTrue(wooden==5,"expected all five legacy patio palette states "+path);
            var entries=template.filterBlocks(BlockPos.ZERO,new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),fence);
            h.assertTrue(!entries.isEmpty(),"actual StructureTemplate lost wooden palette");
            copies++;
        }
        h.assertTrue(copies==2,"patio copies"); h.succeed();
    }

    @GameTest(template=TEMPLATE)
    public static void decoratorRotatesWholeCornerAndSurvivesScheduledUpdates(GameTestHelper h) {
        var p=ManagedResourceTestPlayers.survival(h.getLevel(),"EdgeDecorator");
        var pos=h.absolutePos(new BlockPos(5,4,5));
        var fence=BlockRegistry.WOODEN_FENCE.get();
        var state=legacy(Direction.SOUTH,6);
        var expected=fence.deriveConnections(state.rotate(Rotation.CLOCKWISE_90),h.getLevel(),pos);
        h.getLevel().setBlock(pos,state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        try {
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(com.seggellion.britannia_mod.registry.ItemRegistry.INTERIOR_DECORATOR_TOOL.get()));
            var hit=new net.minecraft.world.phys.BlockHitResult(pos.getCenter(),Direction.UP,pos,false);
            var result=p.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(p,net.minecraft.world.InteractionHand.MAIN_HAND,hit));
            h.assertTrue(result.consumesAction(),"decorator did not handle fence");
            h.assertTrue(h.getLevel().getBlockState(pos)==expected,"decorator only rotated facing");
            h.getLevel().scheduleTick(pos,fence,1);
            com.seggellion.britannia_mod.block.WoodenFenceLoadHandler.inspect(h.getLevel(),new net.minecraft.world.level.ChunkPos(pos));
        } finally { h.getLevel().getServer().getPlayerList().remove(p); }
        h.runAfterDelay(10,()->{h.assertTrue(h.getLevel().getBlockState(pos)==expected,"tick/load undid intentional layout");h.succeed();});
    }

    @GameTest(template=TEMPLATE)
    public static void updateReadsAtMostFourLoadedOwnersAndNeverMutatesNeighbors(GameTestHelper h) {
        var fence=BlockRegistry.WOODEN_FENCE.get();
        var neighbor=legacy(Direction.SOUTH,6); // Querying this sentinel must remain read-only.
        var reads=new java.util.concurrent.atomic.AtomicInteger();
        var guards=new java.util.concurrent.atomic.AtomicInteger();
        boolean[] available={false};
        var level=(net.minecraft.world.level.LevelAccessor)java.lang.reflect.Proxy.newProxyInstance(
            EdgeFenceCompatibilityGameTests.class.getClassLoader(),new Class[]{net.minecraft.world.level.LevelAccessor.class},
            (proxy,method,args)->{
                if(method.getName().equals("hasChunk")) {guards.incrementAndGet();return available[0];}
                if(method.getName().equals("getBlockState")) {
                    h.assertTrue(available[0],"unguarded unloaded read"); reads.incrementAndGet(); return neighbor;
                }
                throw new AssertionError("unexpected world access/write: "+method.getName());
            });
        var source=legacy(Direction.EAST,15);
        var absent=fence.deriveConnections(source,level,BlockPos.ZERO);
        h.assertTrue(guards.get()==4 && reads.get()==0,"unloaded lookup generated chunks");
        h.assertTrue(absent.getValue(WoodenFenceBlock.FACING)==Direction.EAST && absent.getValue(WoodenFenceBlock.LAYOUT_CODE)==15,"capture after flag correction");
        available[0]=true;guards.set(0);
        fence.deriveConnections(absent,level,BlockPos.ZERO);
        h.assertTrue(guards.get()==4 && reads.get()==4,"query bound depends on run length");
        h.assertTrue(neighbor.getValue(WoodenFenceBlock.LAYOUT_CODE)==32,"neighbor query materialized another owner");
        h.succeed();
    }
}
