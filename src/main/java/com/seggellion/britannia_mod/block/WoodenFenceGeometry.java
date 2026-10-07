package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;

/** Frozen legacy decoding, individual terminal strips, and new-only placement choices. */
public final class WoodenFenceGeometry {
    private WoodenFenceGeometry() {}
    public static final List<Direction> DIRECTIONS = List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST);
    private static final int[] ELBOWS = {6,12,9,3};
    private static final VoxelShape[] OUTLINES = new VoxelShape[16];
    private static final VoxelShape[] COLLISIONS = new VoxelShape[16];
    private static final AABB[] STRIPS = {
        new AABB(0,14.5/16,0,1,17.5/16,3.0/16),
        new AABB(13.0/16,14.5/16,0,1,17.5/16,1),
        new AABB(0,14.5/16,13.0/16,1,17.5/16,1),
        new AABB(0,14.5/16,0,3.0/16,17.5/16,1)
    };
    static {
        VoxelShape[] edges = {Block.box(0,0,0,16,16,4),Block.box(12,0,0,16,16,16),
                Block.box(0,0,12,16,16,16),Block.box(0,0,0,4,16,16)};
        for(int mask=0;mask<16;mask++) {
            VoxelShape shape=Shapes.empty(), collision=Shapes.empty();
            for(int i=0;i<4;i++) if((mask & 1<<i)!=0) {
                shape=Shapes.or(shape,edges[i]);
                AABB b=edges[i].bounds();
                collision=Shapes.or(collision,Shapes.box(b.minX,0,b.minZ,b.maxX,1.5,b.maxZ));
            }
            OUTLINES[mask]=shape; COLLISIONS[mask]=collision;
        }
    }
    public static int bit(Direction d) { return 1 << DIRECTIONS.indexOf(d); }
    public static int transformMask(int mask, UnaryOperator<Direction> transform) {
        int result=0;
        for(Direction d:DIRECTIONS) if((mask & bit(d))!=0) result |= bit(transform.apply(d));
        return result;
    }
    private static int reflect(int mask) { return transformMask(mask,Mirror.LEFT_RIGHT::mirror); }
    private static boolean oppositePair(int mask) { return mask==5 || mask==10; }
    /** Exactly the old Java decoder, independent of the new connection booleans. */
    public static int legacyOutlineEdges(Direction facing, int mask) {
        int count=Integer.bitCount(mask);
        if(count<2 || count==2 && oppositePair(mask)) return bit(facing);
        if(count==2) return (~mask)&15;
        Direction primary=count==3?DIRECTIONS.get(Integer.numberOfTrailingZeros((~mask)&15)):facing;
        return bit(primary) | bit(primary.getCounterClockWise());
    }
    /** Common full outer rails; the T center branch is never advertised as an outer arm. */
    private static int legacyContactEdges(Direction facing, int mask) {
        int count=Integer.bitCount(mask), rendered;
        if(count==1) {
            Direction connection=DIRECTIONS.get(Integer.numberOfTrailingZeros(mask));
            Direction edge=connection.getCounterClockWise();
            rendered=bit(facing==edge?edge:edge.getOpposite());
        } else if(count==3) rendered=(~mask)&15;
        else rendered=legacyOutlineEdges(facing,mask);
        return rendered & legacyOutlineEdges(facing,mask);
    }
    private static int decode(BlockState state, boolean contact) {
        int code=WoodenFenceBlock.effectiveLayout(state);
        Direction facing=state.getValue(WoodenFenceBlock.FACING);
        int mask=code&15;
        if((code&16)!=0) { facing=Mirror.LEFT_RIGHT.mirror(facing); mask=reflect(mask); }
        int edges=contact?legacyContactEdges(facing,mask):legacyOutlineEdges(facing,mask);
        return (code&16)!=0?reflect(edges):edges;
    }
    public static int occupiedEdges(BlockState state) { return decode(state,false); }
    public static int contactEdges(BlockState state) { return decode(state,true); }
    public static VoxelShape outline(BlockState state) { return OUTLINES[occupiedEdges(state)]; }
    public static VoxelShape collision(BlockState state) { return COLLISIONS[occupiedEdges(state)]; }
    public static BlockState loadedNeighbor(LevelAccessor level, BlockPos pos) {
        return level.hasChunk(pos.getX()>>4,pos.getZ()>>4)?level.getBlockState(pos):null;
    }
    /** Positive overlap on a shared plane, comparing individual strips, never a union AABB. */
    public static boolean contacts(BlockState a, BlockState b, Direction offset) {
        return contactDistance(a,b,offset,null) < Double.POSITIVE_INFINITY;
    }
    private static double contactDistance(BlockState a,BlockState b,Direction offset,Vec3 hit) {
        int ma=contactEdges(a), mb=contactEdges(b);
        double best=Double.POSITIVE_INFINITY;
        for(int i=0;i<4;i++) if((ma & 1<<i)!=0) for(int j=0;j<4;j++) if((mb & 1<<j)!=0) {
            if(DIRECTIONS.get(i).getAxis()==offset.getAxis() && DIRECTIONS.get(j).getAxis()==offset.getAxis()) continue;
            AABB x=STRIPS[i], y=STRIPS[j].move(offset.getStepX(),0,offset.getStepZ());
            boolean axisX=offset.getAxis()==Direction.Axis.X;
            double plane=offset.getAxisDirection()==Direction.AxisDirection.POSITIVE?1:0;
            double aMin=axisX?x.minX:x.minZ, aMax=axisX?x.maxX:x.maxZ;
            double bMin=axisX?y.minX:y.minZ, bMax=axisX?y.maxX:y.maxZ;
            if(aMin>plane || aMax<plane || bMin>plane || bMax<plane) continue;
            double low=Math.max(axisX?x.minZ:x.minX,axisX?y.minZ:y.minX);
            double high=Math.min(axisX?x.maxZ:x.maxX,axisX?y.maxZ:y.maxX);
            if(high<=low || Math.min(x.maxY,y.maxY)<=Math.max(x.minY,y.minY)) continue;
            best=Math.min(best,hit==null?0:intervalDistance(axisX?hit.z:hit.x,low,high));
        }
        return best;
    }
    private static double intervalDistance(double value,double min,double max) {
        return Math.max(Math.max(min-value,value-max),0);
    }
    private static double hitDistance(BlockState state,Vec3 hit) {
        double result=Double.POSITIVE_INFINITY;
        int edges=occupiedEdges(state);
        for(int i=0;i<4;i++) if((edges & 1<<i)!=0) {
            AABB b=STRIPS[i];
            double x=intervalDistance(hit.x,b.minX,b.maxX), z=intervalDistance(hit.z,b.minZ,b.maxZ);
            result=Math.min(result,x*x+z*z);
        }
        return result;
    }
    private record Candidate(BlockState state,int contacts,int panels,double distance,int intent,int order) {}
    public static BlockState placement(BlockState defaults,BlockPlaceContext context) {
        Direction intended=context.getHorizontalDirection().getOpposite();
        BlockPos target=context.getClickedPos();
        Direction face=context.getClickedFace();
        // BlockPlaceContext has already resolved replacement versus adjacent target.
        BlockPos clicked=context.replacingClickedOnBlock()?target:target.relative(face.getOpposite());
        BlockState clickedState=loadedNeighbor(context.getLevel(),clicked);
        boolean endpoint=!clicked.equals(target) && face.getAxis().isHorizontal()
                && clickedState!=null && clickedState.getBlock() instanceof WoodenFenceBlock;
        boolean anchor=!clicked.equals(target) && face.getAxis().isHorizontal() && !endpoint;
        Vec3 hit=context.getClickLocation().subtract(Vec3.atLowerCornerOf(target));
        BlockState isolated=defaults.setValue(WoodenFenceBlock.FACING,intended).setValue(WoodenFenceBlock.LAYOUT_CODE,0);
        BlockState[] neighbors=new BlockState[4];
        for(int i=0;i<4;i++) neighbors[i]=loadedNeighbor(context.getLevel(),target.relative(DIRECTIONS.get(i)));
        List<Candidate> candidates=new ArrayList<>();
        for(int layoutIndex=0;layoutIndex<8;layoutIndex++) {
            int code=layoutIndex<4?0:ELBOWS[layoutIndex-4];
            int edges=layoutIndex<4?1<<layoutIndex:(~code)&15;
            if(anchor && (edges & bit(intended))==0) continue;
            Direction facing=(edges & bit(intended))!=0?intended:DIRECTIONS.get(Integer.numberOfTrailingZeros(edges));
            BlockState state=defaults.setValue(WoodenFenceBlock.FACING,facing).setValue(WoodenFenceBlock.LAYOUT_CODE,code);
            int count=0;
            for(int i=0;i<4;i++) if(neighbors[i]!=null && neighbors[i].getBlock() instanceof WoodenFenceBlock
                    && contacts(state,neighbors[i],DIRECTIONS.get(i))) count++;
            double distance=hitDistance(state,hit);
            if(endpoint) {
                distance=contactDistance(state,clickedState,face.getOpposite(),hit);
                if(distance>1.0e-6) continue;
            }
            candidates.add(new Candidate(state,count,Integer.bitCount(edges),distance,facing==intended?0:1,layoutIndex));
        }
        if(candidates.stream().noneMatch(c->c.contacts>0)) return isolated;
        Comparator<Candidate> ordering=Comparator.comparingInt(Candidate::panels)
                .thenComparingDouble(Candidate::distance).thenComparingInt(Candidate::intent).thenComparingInt(Candidate::order);
        if(!endpoint) ordering=Comparator.comparingInt(Candidate::contacts).reversed().thenComparing(ordering);
        return candidates.stream().min(ordering).orElseThrow().state;
    }
}
