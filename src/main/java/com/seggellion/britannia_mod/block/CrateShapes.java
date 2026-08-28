package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The collision, selection and raycast geometry of the crate family.
 *
 * <h2>Why this is not inline in the registry</h2>
 *
 * <p>These numbers are a restatement of the authored models, and they had already drifted away from
 * them: the medium crate went on colliding at the 14-voxel height of {@code medium_crate.old} long
 * after the art it described was replaced, and the small crate stood three voxels taller and five
 * deeper than anything drawn. Stale geometry is not cosmetic here — the support check and the player's
 * raycast both read this shape, so it decides where a crate can be stacked and where it can be
 * clicked.
 *
 * <p>Naming them once gives {@code CrateArtCollisionTest} something to hold against the model JSON,
 * so the next time the art moves the shape either follows it or the build says so.
 */
public final class CrateShapes {

    /** Bounds of {@code block/new_assets/small_crate.json}. */
    public static final VoxelShape SMALL = Block.box(2.0D, 0.0D, 1.3D, 14.0D, 7.15D, 9.1D);

    /** Bounds of {@code block/new_assets/medium_crate.json}. */
    public static final VoxelShape MEDIUM = Block.box(1.265D, 0.0D, 0.61D, 15.515D, 11.6D, 14.86D);

    private CrateShapes() {
    }

    /**
     * The part of {@code block/new_assets/large_crate.json} that falls inside one of its eight cells.
     *
     * <p>The model measures 19 x 18 x 19 voxels and reaches only half a voxel past its root cell, so
     * the far cells collide with a sliver rather than the 12 and 6 voxels they claimed before. The
     * 2x2x2 cell range itself is left alone on purpose — existing large crates are saved with
     * {@code PART} 0..7 and narrowing the range would orphan those cells.
     */
    public static VoxelShape largeCell(int x, int y, int z) {
        double maxX = x == 0 ? 16.0D : 0.5D;
        double maxZ = z == 0 ? 16.0D : 0.5D;
        return Block.box(0.0D, 0.0D, 0.0D, maxX, y == 0 ? 16.0D : 3.0D, maxZ);
    }
}
