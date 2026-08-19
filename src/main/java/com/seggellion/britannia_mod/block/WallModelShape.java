package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the collision for a two-block-tall wall out of the numbers in its model file.
 *
 * <p>The two-block wall families here draw the whole thing from the lower half - the upper half's
 * blockstate is {@code minecraft:block/air} - so their models run from {@code y 0} to {@code y 32}
 * while each block can only collide over {@code y 0..16}. Transcribing those models into collision
 * by hand means subtracting sixteen from half the numbers, and getting that wrong is exactly how a
 * window ends up with an opening taller than the one it draws.
 *
 * <p>So boxes go in unchanged, in the model's own coordinates - {@code x} and {@code z} in
 * {@code 0..16}, {@code y} in {@code 0..32} from the bottom of the lower block - and
 * {@link #half(DoubleBlockHalf)} slices out the piece that belongs to one block. A collision
 * definition then reads as a straight copy of the {@code elements} array beside it.
 */
final class WallModelShape {

    private static final double BLOCK = 16.0D;

    private final List<double[]> boxes = new ArrayList<>();

    private WallModelShape() {
    }

    static WallModelShape of() {
        return new WallModelShape();
    }

    /** One {@code elements} entry, copied straight from the model JSON. */
    WallModelShape box(double x0, double y0, double z0, double x1, double y1, double z1) {
        this.boxes.add(new double[] {x0, y0, z0, x1, y1, z1});
        return this;
    }

    /** Everything from another definition, so a junction can reuse a run it shares. */
    WallModelShape addAll(WallModelShape other) {
        this.boxes.addAll(other.boxes);
        return this;
    }

    /** The part of the modelled wall that falls inside the requested block. */
    VoxelShape half(DoubleBlockHalf half) {
        double base = half == DoubleBlockHalf.LOWER ? 0.0D : BLOCK;
        VoxelShape shape = Shapes.empty();
        for (double[] box : this.boxes) {
            double y0 = Math.max(box[1], base) - base;
            double y1 = Math.min(box[4], base + BLOCK) - base;
            if (y1 <= y0) {
                continue; // This element belongs entirely to the other half.
            }
            shape = Shapes.or(shape, Block.box(box[0], y0, box[2], box[3], y1, box[5]));
        }
        return shape.optimize();
    }
}
