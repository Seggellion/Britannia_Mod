package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.CarpetTeleporterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
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
                .noOcclusion()
                .instabreak());
        registerDefaultState(stateDefinition.any().setValue(PART, CarpetPart.CENTER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, STYLE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               net.minecraft.world.phys.shapes.CollisionContext context) {
        return SLAB;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;

        BlockPos centre = centerFromPart(pos, state.getValue(PART));
        BlockState centerState = level.getBlockState(centre);
        if (!(centerState.getBlock() instanceof CarpetTeleporterBlock)
                || !centerState.getValue(CarpetTeleporterBlock.POWERED)) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(centre);
        if (blockEntity instanceof CarpetTeleporterBlockEntity teleporter) {
            teleporter.teleport(player);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block block, BlockPos fromPos, boolean moving) {
        if (level.isClientSide) return;

        BlockPos centre = centerFromPart(pos, state.getValue(PART));
        if (!(level.getBlockState(centre).getBlock() instanceof CarpetTeleporterBlock)) {
            level.destroyBlock(pos, false);
        }
    }

    private static BlockPos centerFromPart(BlockPos pos, CarpetPart part) {
        return pos.offset(1 - part.gridX, 0, 1 - part.gridZ);
    }
}
