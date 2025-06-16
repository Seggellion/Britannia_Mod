// CarpetDummyBlock.java
package com.seggellion.britannia_mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CarpetDummyBlock extends Block {

    public static final EnumProperty<CarpetPart> PART =
        EnumProperty.create("part", CarpetPart.class);

public static final IntegerProperty STYLE = IntegerProperty.create("style", 0, 4);


    private static final VoxelShape SLAB =
        Shapes.box(0, 0, 0, 1, 1f / 16f, 1);

    public CarpetDummyBlock() {
        super(BlockBehaviour.Properties
                .of()
                .mapColor(MapColor.WOOL)
                .sound(SoundType.WOOL)
                .noOcclusion()       // render even if neighbours touch
                .instabreak());
        registerDefaultState(stateDefinition.any().setValue(PART, CarpetPart.CENTER));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(PART, STYLE);
    }

    @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p,
                                         net.minecraft.world.phys.shapes.CollisionContext c) {
        return SLAB;
    }

    /* auto‑cleanup if the teleporter (centre slice) disappears */
    @Override
    public void neighborChanged(BlockState s, Level lvl, BlockPos pos,
                                Block blk, BlockPos fromPos, boolean moving) {
        if (lvl.isClientSide) return;
        BlockPos centre = pos.offset(1 - s.getValue(PART).gridX,
                                     0,
                                     1 - s.getValue(PART).gridZ);
        if (!(lvl.getBlockState(centre).getBlock() instanceof CarpetTeleporterBlock))
            lvl.destroyBlock(pos, false);
    }
}
