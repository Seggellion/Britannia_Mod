package com.seggellion.britannia_mod.client.model;

import com.seggellion.britannia_mod.crate.CrateFoundation;
import com.seggellion.britannia_mod.crate.CrateStackLayout;
import com.seggellion.britannia_mod.crate.CrateStackSlice;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * A large crate, plus whatever of a column resting on its lid hangs into its space.
 *
 * <h2>Why the foundation draws it</h2>
 *
 * <p>A column standing on a large crate begins at the lid, thirteen voxels below the first cell the
 * column is allowed to own. Its lowest crates are therefore physically inside the cell the large crate
 * occupies, and a chunk section is built per block: whoever owns the cell has to emit the geometry in
 * it, or the crate is drawn into a section it does not sit in and vanishes whenever that section is
 * culled and its neighbour is not.
 *
 * <p>This is the same arrangement continuation cells already use, pointing the other way. A cell above
 * a column looks down to the root and draws the part of the column that reaches up into it; a large
 * crate looks up and draws the part that reaches down. Neither owns the crates it draws, and in both
 * cases the geometry lands in the section that actually contains it.
 *
 * <p>Only the large crate's anchor is drawn at all — {@code getRenderShape} makes the other seven
 * cells invisible and the authored model spans the whole structure from there — so the overhang is
 * lifted by one whole cell to land in the cell above the anchor, which is where it belongs.
 */
public final class CrateFoundationBakedModel extends BakedModelWrapper<BakedModel> {

    /** The part of a column resting on this crate that falls inside the cell above it. */
    public static final ModelProperty<CrateStackSlice> OVERHANG = new ModelProperty<>();

    /** How far the overhang is lifted: it lives one cell above the block that draws it. */
    private static final double LIFT_BLOCKS =
            CrateStackLayout.CELL_HUNDREDTHS
                    / (double) (CrateStackLayout.HUNDREDTHS_PER_VOXEL * 16);

    /** Crates are cutout, whatever the crate underneath them draws with. */
    private static final ChunkRenderTypeSet CUTOUT =
            ChunkRenderTypeSet.of(RenderType.cutout());

    public CrateFoundationBakedModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public ModelData getModelData(
            BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        return CrateFoundation.columnOn(level, pos, state)
                .map(founded -> modelData.derive().with(OVERHANG, founded.overhang()).build())
                .orElse(modelData);
    }

    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            RandomSource random,
            ModelData modelData,
            @Nullable RenderType renderType) {

        List<BakedQuad> own = super.getQuads(state, side, random, modelData, renderType);
        CrateStackSlice overhang = modelData.get(OVERHANG);
        // Only the unculled bucket carries the overhang, for the same reason the column's own model
        // uses it: these quads were baked for a different block and would be culled against the wrong
        // neighbours.
        if (side != null || overhang == null || overhang.isEmpty()) {
            return own;
        }
        List<BakedQuad> quads = new ArrayList<>(own);
        CrateStackBakedModel.appendSlice(overhang, LIFT_BLOCKS, random, renderType, quads);
        return quads;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        ChunkRenderTypeSet own = super.getRenderTypes(state, random, data);
        CrateStackSlice overhang = data.get(OVERHANG);
        return overhang == null || overhang.isEmpty()
                ? own
                : ChunkRenderTypeSet.union(own, CUTOUT);
    }
}
