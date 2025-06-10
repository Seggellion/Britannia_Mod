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

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** Thin wrapper that copies the neighbor’s visible face when FILLED = true. */
public class ThinWallBakedModel implements BakedModel {

    /* custom keys – no external helpers needed */
    private static final ModelProperty<BlockAndTintGetter> LEVEL_PROP = new ModelProperty<>();
    private static final ModelProperty<BlockPos>           POS_PROP   = new ModelProperty<>();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final BakedModel original;

    public ThinWallBakedModel(BakedModel original) { this.original = original; }

    /* --------------------------------------------------------------------- */
    /*  Supply level + position so they are present in ModelData             */
    /* --------------------------------------------------------------------- */
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

    if (state == null || !(state.getBlock() instanceof ThinWall))
        return original.getQuads(state, side, rand, data, rt);

    Direction facing  = state.getValue(ThinWall.FACING);
    Direction gapSide = facing.getOpposite();
    boolean   filled  = state.getValue(ThinWall.FILLED);

 

    List<BakedQuad> quads =
            new ArrayList<>(original.getQuads(state, side, rand, data, rt));


    if (side == null) {
      
        return quads;
    }

    if (side == null || !filled) {

        return quads;
    }

    BlockAndTintGetter level = data.get(LEVEL_PROP);
    BlockPos           pos   = data.get(POS_PROP);
    if (level == null || pos == null) {
 
        return quads;
    }

  


    BlockPos neighborPos = pos.relative(gapSide);
    BlockState neighbor = level.getBlockState(pos.relative(gapSide));
    BakedModel neighM    = Minecraft.getInstance()
                                    .getBlockRenderer()
                                    .getBlockModel(neighbor);


      if (neighbor.isAir()) {
    
            return quads;
    }

        // --- Neighbor Model Check ---
        BakedModel neighborModel = Minecraft.getInstance()
                .getBlockRenderer().getBlockModel(neighbor);
     
        List<Direction> requiredFaces = facesFor(side, gapSide);
  



    int before = quads.size();

    facesFor(side, gapSide).forEach(dir ->
        copyShiftedQuad(neighM, neighbor, dir, gapSide, quads, rand, rt, side, state.getBlock())); // Pass 'side'

    return quads;
}

/* ----------------------------------------------------------------- */
/*  Helper: shrink neighbour face to *two‑thirds* block (0.666)      */
/*           and move it into the gap                                */
/* ----------------------------------------------------------------- */
private static void copyShiftedQuad(BakedModel neighM, BlockState neigh,
                                    Direction srcDir, Direction gapSide,
                                    List<BakedQuad> out,
                                    RandomSource rand, RenderType rt,
                                    Direction renderSide, Block wallBlock) {

    List<BakedQuad> src = neighM.getQuads(neigh, srcDir, rand, ModelData.EMPTY, rt);
    if (src.isEmpty()) return;

 //   final float GAP = 10.66F / 16F;            // 0.666 block
String blockId = BuiltInRegistries.BLOCK.getKey(neigh.getBlock()).getPath();

String wallId = BuiltInRegistries.BLOCK.getKey(wallBlock).getPath();

float GAP;
if (wallId.startsWith("stone_wall_")) {
    GAP = 0.5f;  // reduced fill
} else if (wallId.startsWith("brick_wall_")) {
    GAP = 10.66F / 16F;  // standard fill
} else {
    GAP = 0.666f;    // fallback fill
}



    boolean axisX  = gapSide.getAxis() == Direction.Axis.X;
    boolean toward = gapSide.getAxisDirection() == Direction.AxisDirection.POSITIVE;

    /* WEST or NORTH gap ⇒ offset = 0,  range = 0‑0.666
       EAST or SOUTH gap ⇒ offset = 0.333, range = 0.333‑1.0  */
    float offset = toward ? (1.0F - GAP) : 0.0F;   // 0.0 or 0.333

    for (BakedQuad base : src) {
        int[] verts = base.getVertices().clone();
        int   step  = verts.length / 4;       // 8 ints / vertex

        for (int v = 0; v < 4; ++v) {
            int xi = v * step;        // x float
            int zi = v * step + 2;    // z float

            float x = Float.intBitsToFloat(verts[xi]);
            float z = Float.intBitsToFloat(verts[zi]);

            if (axisX)       x = offset + x * GAP;  // scale along X
            else             z = offset + z * GAP;  // scale along Z

            verts[xi] = Float.floatToRawIntBits(x);
            verts[zi] = Float.floatToRawIntBits(z);
        }

        out.add(new BakedQuad(
                verts,
                base.getTintIndex(),
                renderSide,            // same side being rendered
                base.getSprite(),
                base.isShade()));
    }
}


/** For the wall’s render *call* on <side>, return neighbor faces we must copy. */
private static List<Direction> facesFor(Direction side, Direction gapSide) {
    Direction.Axis gapAxis = gapSide.getAxis();

    return switch (side) {
        /* the vertical gap face itself */
        case EAST, WEST, NORTH, SOUTH -> {
            if (side == gapSide)                      // front slice
                yield List.of(gapSide.getOpposite());
            // perpendicular faces
            if (gapAxis == Direction.Axis.X && (side == Direction.NORTH || side == Direction.SOUTH))
                yield List.of(side);
            if (gapAxis == Direction.Axis.Z && (side == Direction.EAST  || side == Direction.WEST))
                yield List.of(side);
            yield List.of();
        }
        /* top and bottom always needed */
        case UP, DOWN -> List.of(side);
        default       -> List.of();
    };
}


    /* ------------- delegate the remaining interface methods -------------- */

    @Override public boolean useAmbientOcclusion()       { return original.useAmbientOcclusion(); }
    @Override public boolean isGui3d()                    { return original.isGui3d(); }
    @Override public boolean usesBlockLight()             { return original.usesBlockLight(); }
    @Override public boolean isCustomRenderer()           { return original.isCustomRenderer(); }
    @Override public ItemOverrides getOverrides()         { return original.getOverrides(); }

    /* NeoForge 1.21 particle‑icon method (parameter‑less) */
    @Override public TextureAtlasSprite getParticleIcon() { return original.getParticleIcon(); }
}
