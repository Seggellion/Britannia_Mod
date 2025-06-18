package com.seggellion.britannia_mod.block;

import com.mojang.serialization.MapCodec;
import com.seggellion.britannia_mod.entity.LivingSeatEntity;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Direction;
import net.minecraft.world.ItemInteractionResult;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.List;

public class RotatingStoneWallBlock extends HorizontalDirectionalBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public static final MapCodec<RotatingStoneWallBlock> CODEC = simpleCodec(RotatingStoneWallBlock::new);

    public RotatingStoneWallBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        Direction dir = state.getValue(FACING);

        return switch (dir) {
            case NORTH -> Block.box(0, 0, 0, 16, 16, 8);
            case SOUTH -> Block.box(0, 0, 8, 16, 16, 16);
            case WEST  -> Block.box(0, 0, 0, 8, 16, 16);
            case EAST  -> Block.box(8, 0, 0, 16, 16, 16);
            default    -> Block.box(0, 0, 0, 16, 16, 16);
        };
    }
}
