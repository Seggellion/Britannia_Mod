package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The Wooden Board Floor family: the flooring an owner lays, and the structural interior floor
 * a house ships with. Both are these boards; only their identity differs.
 *
 * <h2>Why the family has its own class</h2>
 * It has fourteen boards, and {@link FloorBlock} declares fifteen variants because
 * {@code wooden_plank_floor} -- which shares that base -- genuinely has fifteen textures.
 * The board family was generated against the same count and was one short from the day it
 * landed: commit cb9483db added fifteen models and fourteen PNGs, and
 * {@code wooden_board_floor_14.png} has never existed as a blob anywhere in the history of
 * this repository. Variation 14 was therefore never a variant that could be drawn; it rendered
 * as the missing-texture checkerboard for anyone whose decorator tool cycled onto it.
 *
 * <p>So the range is narrowed here rather than in the base, which would have taken
 * {@code wooden_plank_floor} down with it.
 *
 * <h2>The three floor roles</h2>
 * A house distinguishes its exterior perimeter courses, the decorative flooring an owner lays,
 * and the structural interior floor it ships with -- the layer an owner cuts down through to
 * reach a basement. The first is the {@code *_foundation} perimeter family; the other two are
 * this class, registered twice under different names so the housing rules can tell them apart
 * without telling them apart by eye.
 */
public class WoodenBoardFloorBlock extends FloorBlock {

    /** One value per board texture that exists. Fourteen, not the base class fifteen. */
    public static final IntegerProperty VARIATION = IntegerProperty.create("variation", 0, 13);

    public WoodenBoardFloorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public IntegerProperty variationProperty() {
        return VARIATION;
    }
}
