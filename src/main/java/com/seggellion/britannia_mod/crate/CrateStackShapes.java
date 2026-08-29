package com.seggellion.britannia_mod.crate;

import com.seggellion.britannia_mod.block.CrateShapes;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The collision and selection geometry of a column, built from the crates it actually holds.
 *
 * <h2>Following the art rather than the grid</h2>
 *
 * <p>A cell does not get a full cube. It gets the crates that reach into it, each moved to where it
 * is drawn and then cut to that cell's own sixteen voxels — so a player walks into a column exactly
 * where they can see one, and the space above the top crate stays open.
 *
 * <p>The translation is {@link CrateVariant#renderOffsetHundredths(int)}, the same call the renderer
 * makes. That is deliberate: a shape derived by any other arithmetic would eventually disagree with
 * the picture, and a crate you can see but cannot hit is worse than no crate.
 */
public final class CrateStackShapes {

    /** {@link CrateShapes} rotated to each facing, built once. */
    private static final Map<CrateVariant, Map<Direction, VoxelShape>> BY_FACING = buildRotations();

    private CrateStackShapes() {
    }

    /** One crate's own shape, turned to face the way it is drawn. */
    public static VoxelShape shapeFor(CrateVariant variant, Direction facing) {
        return BY_FACING.get(variant).get(facing.getAxis().isVertical() ? Direction.NORTH : facing);
    }

    /**
     * Everything of a column that lies inside one cell.
     *
     * <p>Each crate is translated to its drawn height and then clipped to {@code [0, 1]}, which is
     * what turns a crate spanning a boundary into a lower part in one cell and an upper part in the
     * next without either of them having to know it happened.
     */
    public static VoxelShape cellShape(CrateStackSlice slice) {
        if (slice.isEmpty()) {
            return Shapes.empty();
        }
        VoxelShape combined = Shapes.empty();
        for (CrateStackSlice.Entry entry : slice.entries()) {
            VoxelShape moved = clipToCell(
                    shapeFor(entry.variant(), entry.facing()), entry.offsetBlocks());
            if (!moved.isEmpty()) {
                combined = Shapes.or(combined, moved);
            }
        }
        return combined;
    }

    /**
     * Moves a shape up or down and keeps only what is still inside this cell.
     *
     * <p>Works box by box rather than by translating the whole shape, because a {@link VoxelShape}
     * moved outside the unit cube is exactly the fragile thing this project has been bitten by
     * before. Nothing here mutates the shape it was given.
     */
    /**
     * A shape moved along Y, keeping only what still lies inside one cell.
     *
     * <p>Public because a large crate standing on another is expressed the same way: each cell it
     * touches contributes the slice of it that is really inside that cell, and nothing else.
     */
    public static VoxelShape shiftIntoCell(VoxelShape shape, double offsetBlocks) {
        return clipToCell(shape, offsetBlocks);
    }

    private static VoxelShape clipToCell(VoxelShape shape, double offsetBlocks) {
        VoxelShape clipped = Shapes.empty();
        for (AABB box : shape.toAabbs()) {
            double minY = box.minY + offsetBlocks;
            double maxY = box.maxY + offsetBlocks;
            double insideMin = Math.max(0.0D, minY);
            double insideMax = Math.min(1.0D, maxY);
            if (insideMax - insideMin < 1.0E-7D) {
                continue;
            }
            clipped = Shapes.or(clipped, Shapes.box(
                    box.minX, insideMin, box.minZ, box.maxX, insideMax, box.maxZ));
        }
        return clipped;
    }

    private static Map<CrateVariant, Map<Direction, VoxelShape>> buildRotations() {
        Map<CrateVariant, Map<Direction, VoxelShape>> byVariant = new EnumMap<>(CrateVariant.class);
        for (CrateVariant variant : CrateVariant.values()) {
            VoxelShape north = baseShape(variant);
            Map<Direction, VoxelShape> rotations = new EnumMap<>(Direction.class);
            rotations.put(Direction.NORTH, north);
            rotations.put(Direction.EAST, rotateY(north, 1));
            rotations.put(Direction.SOUTH, rotateY(north, 2));
            rotations.put(Direction.WEST, rotateY(north, 3));
            byVariant.put(variant, Map.copyOf(rotations));
        }
        return Map.copyOf(byVariant);
    }

    /** The shape the standalone crate block already uses, so a column feels like the crates in it. */
    private static VoxelShape baseShape(CrateVariant variant) {
        return switch (variant) {
            case SMALL -> CrateShapes.SMALL;
            case MEDIUM -> CrateShapes.MEDIUM;
        };
    }

    /** The same quarter-turn {@code DecorativeMultiblockBlock} applies to its own cells. */
    private static VoxelShape rotateY(VoxelShape shape, int quarterTurnsClockwise) {
        VoxelShape rotated = shape;
        for (int turn = 0; turn < quarterTurnsClockwise; turn++) {
            VoxelShape next = Shapes.empty();
            for (AABB box : rotated.toAabbs()) {
                next = Shapes.or(next, Shapes.create(new AABB(
                        1.0D - box.maxZ, box.minY, box.minX,
                        1.0D - box.minZ, box.maxY, box.maxX)));
            }
            rotated = next;
        }
        return rotated;
    }
}
