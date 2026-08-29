package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The world representation of a compact crate column.
 *
 * <h2>Not a block players own</h2>
 *
 * <p>This has no item, no recipe, no creative-tab entry and no place in any structure. Players keep
 * holding {@code small_crate} and {@code medium_crate}; this block is only what those become in the
 * world once two of them share a position. Nothing places it by hand — a column comes into existence
 * by promotion from a legacy crate, and this milestone exercises that only through its service and
 * its tests.
 *
 * <h2>What is deliberately missing</h2>
 *
 * <p>Rendering, collision, selection, continuation cells and breaking all belong to the next
 * milestone, which is why this class carries no shapes and reports {@link RenderShape#INVISIBLE}. The
 * point of doing the state first is that those systems attach to a data layer that is already
 * impossible to corrupt.
 */
public class CrateStackBlock extends Block implements EntityBlock {

    public CrateStackBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrateStackBlockEntity(pos, state);
    }

    /**
     * Nothing is drawn yet.
     *
     * <p>A column's appearance is composed from its crates' models, which the renderer milestone
     * owns. Invisible is the honest state until then, and it keeps a half-built column from showing
     * a missing-model cube.
     */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}
