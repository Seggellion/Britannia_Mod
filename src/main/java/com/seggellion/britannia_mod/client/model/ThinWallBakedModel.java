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
        if (state == null || !(state.getBlock() instanceof ThinWall))
            return original.getQuads(state, side, rand, data, rt);

        Direction facing  = state.getValue(ThinWall.FACING);
        Direction gapSide = facing.getOpposite();
        boolean filled    = state.getValue(ThinWall.FILLED);

        List<BakedQuad> quads = new ArrayList<>(original.getQuads(state, side, rand, data, rt));

        if (side == null || !filled)
            return quads;

        BlockAndTintGetter level = data.get(LEVEL_PROP);
        BlockPos pos = data.get(POS_PROP);
        if (level == null || pos == null)
            return quads;

        BlockPos neighborPos = pos.relative(gapSide);
        BlockState neighbor = level.getBlockState(neighborPos);
        if (neighbor.isAir())
            return quads;



//  ── inside getQuads(..) ─────────────────────────────────────────────
if (neighbor.getBlock() instanceof StairBlock) {
    BakedModel stairFill = ThinWallModels.stairFill();
    if (stairFill == null) {
        LOGGER.warn("❌ ThinWallModels.stairFill() returned null");
        return quads;
    }

    // pull the whole mesh once; we’ll filter by side after rotation
    List<BakedQuad> rawQuads =
            stairFill.getQuads(null, null, rand, ModelData.EMPTY, rt);
    if (rawQuads.isEmpty()) return quads;

    // How far must we rotate the mesh so it points into the gap?
    int quarterTurns = switch (gapSide) {
        case EAST -> 0;   
        case SOUTH  -> 1;  
        case WEST -> 2;   
        case NORTH  -> 3; 
        default    -> 0;
    };

    for (BakedQuad base : rawQuads) {
        // keep only the face the engine asked for (or everything if side==null)
            if (side != null && rotateY(base.getDirection(), quarterTurns) != side)
            continue;

        int[] verts = base.getVertices().clone();
        int step = verts.length / 4;

        // rotate every vertex around the Y–axis through the block centre
        for (int v = 0; v < 4; v++) {
            int xi = v * step;
            int zi = v * step + 2;

            float x = Float.intBitsToFloat(verts[xi]) - 0.5f;
            float z = Float.intBitsToFloat(verts[zi]) - 0.5f;

            // quarter-turns clockwise
            for (int i = 0; i < quarterTurns; i++) {
                float tmp = x;
                x =  -z;
                z =   tmp;
            }

            verts[xi] = Float.floatToRawIntBits(x + 0.5f);
            verts[zi] = Float.floatToRawIntBits(z + 0.5f);
        }

        // rotate the face direction flag the same way for correct culling
        Direction newDir = rotateY(base.getDirection(), quarterTurns);

        quads.add(new BakedQuad(
            verts,
            base.getTintIndex(),
            newDir,
            base.getSprite(),          // untouched – uses the JSON-defined texture
            base.isShade()
        ));
    }
    return quads;
}





        // Default behavior: shrink neighbor face
        BakedModel neighM = Minecraft.getInstance().getBlockRenderer().getBlockModel(neighbor);
        facesFor(side, gapSide).forEach(dir ->
            copyShiftedQuad(neighM, neighbor, dir, gapSide, quads, rand, rt, side, state.getBlock())
        );

        return quads;
    }


/** Rotate dir clockwise around Y-axis n × 90° (n = 0–3). */
/** Rotate dir clockwise around Y-axis n × 90° (n = 0–3). */
private static Direction rotateY(Direction dir, int quarterTurns) {
    if (dir.getAxis() == Direction.Axis.Y)          // UP / DOWN → leave unchanged
        return dir;

    Direction d = dir;
    for (int i = 0; i < quarterTurns; i++)
        d = d.getClockWise();                      // safe: d is horizontal
    return d;
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
