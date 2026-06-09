package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.BritanniaSpawnBlockEntity;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import com.seggellion.britannia_mod.InvisibleInAdventureMode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import net.minecraft.server.level.ServerLevel;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// 1. Implement SimpleWaterloggedBlock
public class BritanniaSpawnBlock extends Block implements EntityBlock, InvisibleInAdventureMode, SimpleWaterloggedBlock {

    // 2. Add the WATERLOGGED property
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public BritanniaSpawnBlock() {
        super(BlockBehaviour.Properties.of()
            .strength(1.5F)
            .noOcclusion()); 
        
        // 3. Register the default state to be NOT waterlogged
        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, false));
    }

    // 4. Register the property in the StateDefinition
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public RenderShape getRenderShape(BlockState state) {
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && player.isCreative()) {
            return RenderShape.MODEL;  
        }
        return RenderShape.INVISIBLE;  
    }

    @Override 
    public VoxelShape getCollisionShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

@Override 
    public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        // If an entity is interacting with or looking at the block
        if (c instanceof net.minecraft.world.phys.shapes.EntityCollisionContext ec && ec.getEntity() instanceof Player player) {
            boolean isAdmin = player.isCreative() || (player instanceof ServerPlayer sp && sp.hasPermissions(2));
            
            // Only give it a physical outline if the player is an admin
            if (isAdmin) {
                return Block.box(0, 0, 0, 16, 16, 16);
            }
        }

        // For regular players, mobs, projectiles, AND the fluid rendering engine:
        // Return an empty shape so water renders as perfectly still and undisturbed.
        return Shapes.empty();
    }

    // 5. Update placement context to check for water
    @Nullable @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Player p = ctx.getPlayer();
        if (p == null) return null;
        boolean isAdmin = p.isCreative() || (p instanceof ServerPlayer sp && sp.hasPermissions(2));
        
        if (!isAdmin) return null;

        // Check if we are placing the block inside water
FluidState fluidstate = ctx.getLevel().getFluidState(ctx.getClickedPos());
        boolean isWater = fluidstate.is(net.minecraft.tags.FluidTags.WATER);
        
        return this.defaultBlockState().setValue(WATERLOGGED, isWater);
    }

    // 6. Tell the engine this block contains water if the property is true
    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    // 7. Schedule fluid ticks if water flows into/out of the block space
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        boolean isAdmin = player.isCreative() || (player instanceof ServerPlayer sp && sp.hasPermissions(2));
        if (!isAdmin) return InteractionResult.CONSUME; 
        
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BritanniaSpawnBlockEntity spawner && player instanceof ServerPlayer sp) {
            com.seggellion.britannia_mod.network.payload.BritanniaSpawnScreenS2CPayload.send(
                sp, pos, spawner.getEntityId(), spawner.getSpawnRadius(),
                spawner.getRandomMinTicks(), spawner.getRandomMaxTicks(), spawner.isNightOnly(), spawner.getMaxEntities(), spawner.getActiveEntityTypes((ServerLevel) level)
            );
        }
        return InteractionResult.CONSUME;
    }

    @Nullable @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BritanniaSpawnBlockEntity(pos, state);
    }

    @Nullable @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, p, st, t) -> {
            if (t instanceof BritanniaSpawnBlockEntity e) e.serverTick();
        };
    }
}