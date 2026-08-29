package com.seggellion.britannia_mod.client.model;

import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.CrateStackBlock;
import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import com.seggellion.britannia_mod.crate.CrateStackSlice;
import com.seggellion.britannia_mod.crate.CrateVariant;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
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
 * Draws whatever part of a crate column falls inside one world cell.
 *
 * <h2>Why a baked model and not a block-entity renderer</h2>
 *
 * <p>A column is up to four cells tall and only its root has a block entity, so a renderer attached
 * to that block entity would have to draw crates standing three blocks above itself. That means an
 * enlarged render bounding box, hand-managed lighting for crates in cells the root cannot see, and
 * block geometry pushed through an entity render type — the combination that has already produced
 * shader trouble in this project.
 *
 * <p>Chunk baking avoids all three. {@code SectionCompiler} calls
 * {@link #getModelData(BlockAndTintGetter, BlockPos, BlockState, ModelData)} for <em>every</em> block
 * it meets, whether or not that block has a block entity, and hands it the region and the position.
 * A continuation cell can therefore look down to its own root, read the column, and bake its own
 * slice into its own chunk section — where it gets vanilla lighting, vanilla ambient occlusion,
 * vanilla culling, and the terrain pipeline every other block in the mod already uses.
 *
 * <h2>Reusing the authored crates</h2>
 *
 * <p>No crate geometry is recreated here. The quads come from the real baked {@code small_crate} and
 * {@code medium_crate} models, with their textures, UVs, rotations and cutout behaviour, and are only
 * translated vertically to where the column says the crate rests.
 */
public final class CrateStackBakedModel extends BakedModelWrapper<BakedModel> {

    /** The slice this cell draws, resolved from the root during chunk baking. */
    public static final ModelProperty<CrateStackSlice> SLICE = new ModelProperty<>();

    private static final int INTS_PER_VERTEX = 8;
    private static final int VERTICES_PER_QUAD = 4;

    /** Crates are cutout, and a column is nothing but crates. */
    private static final ChunkRenderTypeSet CUTOUT = ChunkRenderTypeSet.of(RenderType.cutout());

    public CrateStackBakedModel(BakedModel originalModel) {
        super(originalModel);
    }

    /**
     * Finds this cell's root and asks it what belongs here.
     *
     * <p>The lookup is a plain {@code getBlockEntity} on the render region, which spans a three by
     * three chunk area over the whole world height — so a cell three blocks above its root still
     * reaches it, and a column never straddles a chunk boundary because every cell shares one x and z.
     */
    @Override
    public ModelData getModelData(
            BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        if (!(state.getBlock() instanceof CrateStackBlock)) {
            return modelData;
        }
        int part = state.getValue(CrateStackBlock.PART);
        if (!(level.getBlockEntity(pos.below(part)) instanceof CrateStackBlockEntity stack)) {
            return modelData;
        }
        return modelData.derive().with(SLICE, stack.sliceFor(part)).build();
    }

    /**
     * The crates in this cell, each moved to the height the column packed it at.
     *
     * <p>Everything is returned in the unculled bucket. A crate model's quads are baked for its own
     * block, so a quad that sat against that block's floor would, once lifted, be culled against
     * whatever is under <em>this</em> cell — hiding crate faces that should be visible. Crates are
     * small non-cube models that vanilla would not cull anyway, so nothing is lost by declining the
     * optimisation and a real defect is avoided.
     */
    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            RandomSource random,
            ModelData modelData,
            @Nullable RenderType renderType) {

        CrateStackSlice slice = modelData.get(SLICE);
        if (side != null || slice == null || slice.isEmpty()) {
            return List.of();
        }
        List<BakedQuad> quads = new ArrayList<>();
        appendSlice(slice, 0.0D, random, renderType, quads);
        return quads;
    }

    /**
     * Draws one cell's worth of crates, lifted by however far the drawing block is from that cell.
     *
     * <p>Shared with the foundation model. A large crate draws the part of a column that hangs into
     * the cell above it, and does so from its own anchor one cell lower, so it passes a whole cell of
     * extra lift; a column cell draws its own slice and passes none.
     */
    public static void appendSlice(
            CrateStackSlice slice,
            double extraLiftBlocks,
            RandomSource random,
            @Nullable RenderType renderType,
            List<BakedQuad> into) {

        for (CrateStackSlice.Entry entry : slice.entries()) {
            BakedModel crateModel = modelFor(entry.variant(), entry.facing());
            if (crateModel == null) {
                continue;
            }
            collectShifted(crateModel, entry, random, renderType,
                    (float) (entry.offsetBlocks() + extraLiftBlocks), into);
        }
    }

    /** Every bucket of the crate's own model, lifted into this cell's space. */
    private static void collectShifted(
            BakedModel crateModel,
            CrateStackSlice.Entry entry,
            RandomSource random,
            @Nullable RenderType renderType,
            float offsetY,
            List<BakedQuad> into) {

        appendShiftedModel(crateModel, crateStateFor(entry.variant(), entry.facing()),
                offsetY, random, renderType, into);
    }

    /**
     * Every bucket of one model, moved along Y.
     *
     * <p>Shared with the foundation model, which uses it to draw a whole large crate standing on
     * another crate's lid rather than one crate out of a column.
     */
    public static void appendShiftedModel(
            BakedModel model,
            BlockState state,
            float offsetY,
            RandomSource random,
            @Nullable RenderType renderType,
            List<BakedQuad> into) {

        addShifted(model.getQuads(state, null, random, ModelData.EMPTY, renderType), offsetY, into);
        for (Direction face : Direction.values()) {
            addShifted(model.getQuads(state, face, random, ModelData.EMPTY, renderType),
                    offsetY, into);
        }
    }

    private static void addShifted(List<BakedQuad> source, float offsetY, List<BakedQuad> into) {
        for (BakedQuad quad : source) {
            into.add(shiftY(quad, offsetY));
        }
    }

    /**
     * A copy of a quad moved along Y.
     *
     * <p>Copies the vertex array rather than editing it: these quads belong to the shared baked crate
     * model, and every other crate in the world is drawn from the same objects.
     */
    private static BakedQuad shiftY(BakedQuad quad, float offsetY) {
        int[] vertices = quad.getVertices().clone();
        for (int vertex = 0; vertex < VERTICES_PER_QUAD; vertex++) {
            int y = vertex * INTS_PER_VERTEX + 1;
            vertices[y] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[y]) + offsetY);
        }
        return new BakedQuad(
                vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade());
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return CUTOUT;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }

    @Nullable
    private static BakedModel modelFor(CrateVariant variant, Direction facing) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }
        return minecraft.getBlockRenderer().getBlockModel(crateStateFor(variant, facing));
    }

    /** The state whose baked model carries this variant's art, turned the way the crate faces. */
    private static BlockState crateStateFor(CrateVariant variant, Direction facing) {
        CrateBlock block = switch (variant) {
            case SMALL -> BlockRegistry.SMALL_CRATE.get();
            case MEDIUM -> BlockRegistry.MEDIUM_CRATE.get();
        };
        return block.defaultBlockState().setValue(CrateBlock.FACING, facing);
    }
}
