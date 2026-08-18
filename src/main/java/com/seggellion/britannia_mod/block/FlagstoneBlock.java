package com.seggellion.britannia_mod.block;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A full block of flagstone. Placing one picks a flagstone texture at random so a laid area
 * looks naturally mixed, and the interior decorator tool cycles it afterwards, the same way
 * {@code custom_sandstone_brick} behaves.
 *
 * <p>The variant count comes from the flagstone texture set alone. It deliberately does not
 * share a property with any other material, so adding a third flagstone texture is a change
 * here and in this block's blockstate, and cannot disturb another block's variants.
 */
public class FlagstoneBlock extends FloorBlock {

    /** One value per flagstone texture: {@code flagstone_01} and {@code flagstone_02}. */
    public static final IntegerProperty VARIATION = IntegerProperty.create("variation", 0, 1);

    public FlagstoneBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public IntegerProperty variationProperty() {
        return VARIATION;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        RandomSource random = context.getLevel().getRandom();
        return super.getStateForPlacement(context)
            .setValue(VARIATION, random.nextInt(VARIATION.getPossibleValues().size()));
    }
}
