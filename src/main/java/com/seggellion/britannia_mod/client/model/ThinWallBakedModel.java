package com.seggellion.britannia_mod.client.model;

import com.seggellion.britannia_mod.block.ThinWall;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;          // ← correct path
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.resources.model.BakedModel;                   // ← NeoForge interface
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.Blocks;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class ThinWallBakedModel implements BakedModel {

    private static final ModelProperty<BlockAndTintGetter> LEVEL_PROP = new ModelProperty<>();
    private static final ModelProperty<BlockPos> POS_PROP = new ModelProperty<>();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final BakedModel original;

    public ThinWallBakedModel(BakedModel original) {
        this.original = original;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos,
                                  BlockState state, ModelData data) {
        return data.derive()
                   .with(LEVEL_PROP, level)
                   .with(POS_PROP, pos)
                   .build();
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side,
                                    RandomSource rand) {
        return getQuads(state, side, rand, ModelData.EMPTY, null);
    }



@Override
public List<BakedQuad> getQuads(BlockState state, Direction side,
                                RandomSource rand, ModelData data,
                                RenderType rt) {

    /* 0 — fallback for non-ThinWall blocks */
    if (state == null || !(state.getBlock() instanceof ThinWall))
        return original.getQuads(state, side, rand, data, rt);

    /* 1 — flags ------------------------------------------------------- */
    Direction facing   = state.getValue(ThinWall.FACING);
    Direction gapSide  = facing.getOpposite();
    boolean   filled   = state.getValue(ThinWall.FILLED);
    boolean   isCorner = state.getValue(ThinWall.CORNER);

    boolean showCornerCube = isCorner && filled;   // ← NEW rule

    /* 2 — bail when nothing extra is needed -------------------------- */
    if (side == null || (!isCorner && !filled))
        return original.getQuads(state, side, rand, data, rt);

    /* 3 — wall’s own quads ------------------------------------------ */
    List<BakedQuad> quads =
        new ArrayList<>(original.getQuads(state, side, rand, data, rt));

    /* 4 — context for helpers --------------------------------------- */
    BlockAndTintGetter level = data.get(LEVEL_PROP);
    BlockPos pos             = data.get(POS_PROP);
    if (level == null || pos == null) return quads;

    BlockState neighbor = level.getBlockState(pos.relative(gapSide));

    /* 5 — stair gap filler ------------------------------------------ */
    if (neighbor.getBlock() instanceof StairBlock) {
        BakedModel stairFill = ThinWallModels.stairFill();
        if (stairFill != null)
            addStairFillQuads(quads, stairFill, gapSide, neighbor, side, rand, rt);
        return quads;
    }

    /* 6 — default shrink-copy if neighbour is solid ------------------ */
    if (!neighbor.isAir()) {
        BakedModel neighM =
            Minecraft.getInstance().getBlockRenderer().getBlockModel(neighbor);

        facesFor(side, gapSide).forEach(dir ->
            copyShiftedQuad(neighM, neighbor, dir, gapSide,
                            quads, rand, rt, side, state.getBlock()));
    }

    /* 7 — corner cube (only when CORNER && FILLED) ------------------- */
    if (showCornerCube) {
        BakedModel plug = ThinWallModels.cornerFill();
        if (plug != null) {
            int rot = quarterTurnsForCorner(facing);

            for (BakedQuad base :
                 plug.getQuads(null, null, rand, ModelData.EMPTY, rt)) {

                Direction d = rotateY(base.getDirection(), rot);
                if (side != null && d != side) continue;   // keep only requested face

                int[] vs = base.getVertices().clone();
                int step = vs.length / 4;

                for (int v = 0; v < 4; ++v) {
                    int xi = v * step, zi = v * step + 2;
                    float x = Float.intBitsToFloat(vs[xi]) - 0.5f;
                    float z = Float.intBitsToFloat(vs[zi]) - 0.5f;

                    for (int i = 0; i < rot; ++i) { float t = x; x = -z; z = t; }

                    vs[xi] = Float.floatToRawIntBits(x + 0.5f);
                    vs[zi] = Float.floatToRawIntBits(z + 0.5f);
                }
                quads.add(new BakedQuad(vs, base.getTintIndex(), d,
                                        base.getSprite(), base.isShade()));
            }
        }
    }

    return quads;
}







/* new helper */

private static void addStairFillQuads(List<BakedQuad> out,
                                      BakedModel stairFill,
                                      Direction gapSide,
                                      BlockState stairState,
                                      Direction side,
                                      RandomSource rand,
                                      RenderType rt) {

    int quarterTurnsGap = switch (gapSide) {
        case EAST  -> 0;
        case SOUTH -> 1;
        case WEST  -> 2;
        case NORTH -> 3;
        default    -> 0;
    };

    Direction stairFacing = stairState.getValue(StairBlock.FACING);
    int quarterTurnsTotal = (quarterTurnsGap + extraRot(gapSide, stairFacing)) & 3;
    boolean mirrorX = mirrorX(gapSide, stairFacing);
    boolean mirrorZ = mirrorZ(gapSide, stairFacing);

    for (BakedQuad base : stairFill.getQuads(null, null, rand, ModelData.EMPTY, rt)) {
        Direction d = rotateY(base.getDirection(), quarterTurnsTotal);
        if (mirrorX) d = mirrorDirX(d);
        if (mirrorZ) d = mirrorDirZ(d);
        if (side != null && d != side) continue;

        int[] vs = base.getVertices().clone();
        int step = vs.length / 4;

        for (int v = 0; v < 4; ++v) {
            int xi = v * step, zi = v * step + 2;
            float x = Float.intBitsToFloat(vs[xi]) - 0.5f;
            float z = Float.intBitsToFloat(vs[zi]) - 0.5f;

            for (int i = 0; i < quarterTurnsTotal; ++i) {
                float t = x; x = -z; z = t;
            }
            if (mirrorX) x = -x;
            if (mirrorZ) z = -z;

            vs[xi] = Float.floatToRawIntBits(x + 0.5f);
            vs[zi] = Float.floatToRawIntBits(z + 0.5f);
        }
        if (mirrorX || mirrorZ) fixWinding(vs, step);

        out.add(new BakedQuad(vs, base.getTintIndex(), d,
                              base.getSprite(), base.isShade()));
    }
}

/* new helper */

/** swap v0↔v1 and v2↔v3 so face winding stays correct after mirroring */
private static void fixWinding(int[] verts, int step) {
    for (int i = 0; i < step; i++) {
        int tmp = verts[i];                 // swap v0 ↔ v1
        verts[i]          = verts[step + i];
        verts[step + i]   = tmp;

        tmp = verts[2 * step + i];          // swap v2 ↔ v3
        verts[2 * step + i] = verts[3 * step + i];
        verts[3 * step + i] = tmp;
    }
}



/* ─────────  EDIT ME IF A PAIR MISALIGNS  ────────────
   row = gapSide (E=0 W=1 N=2 S=3)
   col = stairFacing (E W N S)
   ROT  = extra quarter-turns   (0 or 2)
   MX   = mirror about X axis   (true/false)
   MZ   = mirror about Z axis   (true/false)
   ----- fill “fixes” derived from your latest test list  ----- */
private static final int[][] ROT = {
        /* W */ {0,2,0,0},     // gap EAST
        /* E */ {2,0,2,0},     // gap WEST  (+2→0 for W+S, W+N overlap)
        /* S */ {0,0,1,1},     // gap NORTH
        /* N */ {0,2,3,3}      // gap SOUTH (+2→3 for S+E mis-mirror)
};
private static final boolean[][] MX = {
        /* W */ {false,false,false,false},
        /* E */ {false,false,true ,false },  // mirror X for W+N, W+S
        /* S */ {true,false,false,false},
        /* N */ {false,false,false,false}
};
private static final boolean[][] MZ = {
        /* W */ {false,false,false,true },  // mirror Z for E+S
        /* E */ {false,false,false,false},
        /* S */ {false,false,false,false},
        /* N */ {false,true ,false,false}   // mirror Z for S+W
};
/* ─────────────────────────────────────────────────── */

private static int index(Direction d){return switch(d){
    case EAST->0;case WEST->1;case NORTH->2;case SOUTH->3;default->0;};}

private static int extraRot(Direction gap,Direction stair){
    return ROT[index(gap)][index(stair)];           /* 0 or 2        */
}
private static boolean mirrorX(Direction gap,Direction stair){
    return MX[index(gap)][index(stair)];            /* left↔right    */
}
private static boolean mirrorZ(Direction gap,Direction stair){
    return MZ[index(gap)][index(stair)];            /* front↔back    */
}


/** rotate dir clockwise around Y, n × 90° (n∈0‥3) */
private static Direction rotateY(Direction d, int n) {
    if (d.getAxis() == Direction.Axis.Y) return d;
    for (int i = 0; i < n; i++) d = d.getClockWise();
    return d;
}

/** mirror EAST↔WEST, leave others */
private static Direction mirrorDirX(Direction d) {
    return switch (d) {
        case EAST -> Direction.WEST;
        case WEST -> Direction.EAST;
        default   -> d;
    };
}

/** mirror NORTH↔SOUTH, leave others */
private static Direction mirrorDirZ(Direction d) {
    return switch (d) {
        case NORTH -> Direction.SOUTH;
        case SOUTH -> Direction.NORTH;
        default    -> d;
    };
}



    private static List<Direction> facesFor(Direction side, Direction gapSide) {
        Direction.Axis gapAxis = gapSide.getAxis();
        return switch (side) {
            case EAST, WEST, NORTH, SOUTH -> {
                if (side == gapSide)
                    yield List.of(gapSide.getOpposite());
                if (gapAxis == Direction.Axis.X && (side == Direction.NORTH || side == Direction.SOUTH))
                    yield List.of(side);
                if (gapAxis == Direction.Axis.Z && (side == Direction.EAST || side == Direction.WEST))
                    yield List.of(side);
                yield List.of();
            }
            case UP, DOWN -> List.of(side);
            default -> List.of();
        };
    }

/** quarter-turns (CW) needed to swing the south-west cube into the gap */
private static int quarterTurnsForCorner(Direction facing) {
    return switch (facing) {
        case NORTH -> 0;   // gap faces SOUTH – cube already in place
        case EAST  -> 1;   // turn 90°
        case SOUTH -> 2;   // 180°
        case WEST  -> 3;   // 270°
        default    -> 0;
    };
}



    private static void copyShiftedQuad(BakedModel neighM, BlockState neigh, Direction srcDir,
                                        Direction gapSide, List<BakedQuad> out,
                                        RandomSource rand, RenderType rt,
                                        Direction renderSide, Block wallBlock) {
        List<BakedQuad> src = neighM.getQuads(neigh, srcDir, rand, ModelData.EMPTY, rt);
        if (src.isEmpty()) return;

        String wallId = BuiltInRegistries.BLOCK.getKey(wallBlock).getPath();
        float GAP = wallId.startsWith("stone_wall_") ? 0.5f :
                    wallId.startsWith("brick_wall_") ? (10.66F / 16F) : 0.666f;

        boolean axisX = gapSide.getAxis() == Direction.Axis.X;
        boolean toward = gapSide.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        float offset = toward ? (1.0F - GAP) : 0.0F;

        for (BakedQuad base : src) {
            int[] verts = base.getVertices().clone();
            int step = verts.length / 4;

            for (int v = 0; v < 4; ++v) {
                int xi = v * step;
                int zi = v * step + 2;

                float x = Float.intBitsToFloat(verts[xi]);
                float z = Float.intBitsToFloat(verts[zi]);

                if (axisX) x = offset + x * GAP;
                else       z = offset + z * GAP;

                verts[xi] = Float.floatToRawIntBits(x);
                verts[zi] = Float.floatToRawIntBits(z);
            }

            out.add(new BakedQuad(verts, base.getTintIndex(), renderSide,
                                  base.getSprite(), base.isShade()));
        }
    }

    @Override public boolean useAmbientOcclusion()       { return original.useAmbientOcclusion(); }
    @Override public boolean isGui3d()                    { return original.isGui3d(); }
    @Override public boolean usesBlockLight()             { return original.usesBlockLight(); }
    @Override public boolean isCustomRenderer()           { return original.isCustomRenderer(); }
    @Override public ItemOverrides getOverrides()         { return original.getOverrides(); }
    @Override public TextureAtlasSprite getParticleIcon() { return original.getParticleIcon(); }
}
