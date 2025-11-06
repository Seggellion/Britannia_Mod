package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.core.BlockPos;


public class HouseLotBlock extends Block implements EntityBlock {

    public HouseLotBlock() {
        super(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0f, 6.0f)
            .noOcclusion());
    }
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HouseLotBlockEntity(pos, state);
    }
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

}
