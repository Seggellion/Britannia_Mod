package com.seggellion.britannia_mod.client.model;

import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.crate.CrateFoundation;
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
 * <h2>Drawn by the cell that contains it</h2>
 *
 * <p>The overhang is emitted by the cell it physically sits in, not by the anchor a cell below, and
 * that is not a tidiness point — it is the whole reason this class exists in its current shape.
 * Chunk geometry is lit against the block it is emitted from: {@code ModelBlockRenderer} records each
 * quad's bounds relative to that block and hands them to ambient occlusion as blend weights, together
 * with their {@code 1 - f} complements. A quad drawn a whole cell above its own block has a Y of
 * around 1.9, so its complement is about -0.9, and a weighted sum of light values with negative
 * weights collapses towards zero. That is what a black crate is.
 *
 * <p>The large crate normally draws its whole structure from its anchor and leaves its other seven
 * cells invisible. The one cell carrying an overhang is opened up in {@code getRenderShape} purely so
 * it can draw that overhang - it never draws the large crate's own art, which the anchor still owns.
 */
public final class CrateFoundationBakedModel extends BakedModelWrapper<BakedModel> {

    /** The part of a column resting on this crate that falls inside the cell above it. */
    public static final ModelProperty<CrateStackSlice> OVERHANG = new ModelProperty<>();

    /** Crates are cutout, whatever the crate underneath them draws with. */
    private static final ChunkRenderTypeSet CUTOUT =
            ChunkRenderTypeSet.of(RenderType.cutout());

    public CrateFoundationBakedModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public ModelData getModelData(
            BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        if (!carriesOverhang(state)) {
            return modelData;
        }
        return CrateFoundation.columnOn(level, pos, state)
                .map(founded -> modelData.derive().with(OVERHANG, founded.overhang()).build())
                .orElse(modelData);
    }

    /** Whether this state is the one cell of a large crate a column can hang into. */
    private static boolean carriesOverhang(@Nullable BlockState state) {
        return state != null
                && state.getBlock() instanceof CrateBlock crate
                && CrateFoundation.carriesOverhang(crate, state);
    }

    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            RandomSource random,
            ModelData modelData,
            @Nullable RenderType renderType) {

        // Every cell but the one carrying an overhang draws exactly what it always drew.
        if (!carriesOverhang(state)) {
            return super.getQuads(state, side, random, modelData, renderType);
        }
        CrateStackSlice overhang = modelData.get(OVERHANG);
        // Only the unculled bucket, for the same reason the column's own model uses it: these quads
        // were baked for a different block and would be culled against the wrong neighbours.
        if (side != null || overhang == null || overhang.isEmpty()) {
            return List.of();
        }
        List<BakedQuad> quads = new ArrayList<>();
        CrateStackBakedModel.appendSlice(overhang, 0.0D, random, renderType, quads);
        return quads;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return carriesOverhang(state) ? CUTOUT : super.getRenderTypes(state, random, data);
    }
}
