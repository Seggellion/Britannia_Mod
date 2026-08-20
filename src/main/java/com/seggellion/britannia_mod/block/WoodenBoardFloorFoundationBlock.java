package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The structural interior floor of a house, wearing the Wooden Board Floor's own boards.
 *
 * <p>A house has three distinct floor-and-foundation roles, and until now only two of them had
 * blocks:
 *
 * <ul>
 *   <li><b>Exterior perimeter</b> -- {@code cobblestone_foundation}, {@code plaster_stone_
 *       foundation} and their siblings, the wall-base courses that run around the outside of
 *       the building. Structural, and not something an owner should be cutting through.</li>
 *   <li><b>Interior visible floor</b> -- flooring the owner lays and rearranges,
 *       {@link com.seggellion.britannia_mod.registry.BlockRegistry#WOODEN_BOARD_FLOOR} among
 *       them.</li>
 *   <li><b>Interior structural floor</b> -- the shipped bottom layer of the house that is also
 *       the floor you walk on. That is this block, and it did not exist.</li>
 * </ul>
 *
 * <p>The distinction is what makes a basement possible. The owner reaches theirs by cutting
 * down through their own interior floor, so that floor has to be tellable apart from the
 * perimeter which stays protected -- and tellable apart from decorative flooring, which is not
 * part of the shipped structure at all. Milestone 5 owns what each of the three may do; this
 * block exists so that milestone has something to point at.
 *
 * <p>Visually it is the Wooden Board Floor and nothing else: same textures, same models, same
 * facing and variation behaviour, same decorator-tool cycling. Only the identity differs.
 */
public class WoodenBoardFloorFoundationBlock extends FloorBlock {

    /**
     * One value per Wooden Board Floor texture that actually exists.
     *
     * <p>Deliberately 0-13 rather than the inherited 0-14. {@code wooden_board_floor} declares
     * fifteen variants and ships fourteen textures -- its {@code _14} model points at a
     * {@code wooden_board_floor_14.png} that is not in the repository, so that one variant
     * renders as the missing-texture checkerboard. That is a defect in the floor block and is
     * left to the defect milestone; there is no reason to copy it into a new block on the way
     * past.
     */
    public static final IntegerProperty VARIATION = IntegerProperty.create("variation", 0, 13);

    public WoodenBoardFloorFoundationBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public IntegerProperty variationProperty() {
        return VARIATION;
    }
}
