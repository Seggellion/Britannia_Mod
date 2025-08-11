package com.seggellion.britannia_mod.block;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seggellion.britannia_mod.block.nudgeable.INudgeable;
import com.seggellion.britannia_mod.block.nudgeable.block_entities.ChairBlockEntity;
import com.seggellion.britannia_mod.entity.LivingSeatEntity;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import com.seggellion.britannia_mod.item.InteriorDecoratorToolItem;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;

public class ChairBlock extends HorizontalDirectionalBlock implements EntityBlock, INudgeable {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private final double sittingHeight;

    public ChairBlock(double sittingHeight, Properties properties) {
        super(properties);
        this.sittingHeight = sittingHeight;
        this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        //pineapple removed for iris compatibility
        //return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChairBlockEntity(pos, state);
    }

    public static final MapCodec<ChairBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.DOUBLE.fieldOf("sitting_height").forGetter(block -> block.sittingHeight)
            ).apply(instance, height -> new ChairBlock(height, BlockBehaviour.Properties.of()))
    );

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
    return CODEC;
}

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            Vec3 offset = Vec3.ZERO;
            if (be instanceof ChairBlockEntity chairEntity) {
                offset = chairEntity.getOffset();
            }

            ItemStack offhand = player.getOffhandItem();
            if ((offhand.getItem() instanceof InteriorDecoratorToolItem)) {
                return InteractionResult.FAIL;
            }

            Vec3 seatPos = new Vec3(pos.getX() + 0.5 + offset.x, pos.getY() + sittingHeight + offset.y, pos.getZ() + 0.5 + offset.z);

            List<LivingSeatEntity> existing = level.getEntitiesOfClass(
                    LivingSeatEntity.class,
                    new AABB(pos).inflate(1.0),
                    entity -> true
            );

            LivingSeatEntity seat;
            if (existing.isEmpty()) {
                seat = EntityRegistry.SEAT_ENTITY.get().spawn((ServerLevel) level, pos, MobSpawnType.TRIGGERED);
                if (seat != null) {
                    seat.setPos(seatPos.x, seatPos.y, seatPos.z);
                }
            } else {
                seat = existing.get(0);
                seat.setPos(seatPos.x, seatPos.y, seatPos.z);
            }

            if (seat != null && !player.isPassenger()) {
                player.startRiding(seat);
            }
        }

        return InteractionResult.SUCCESS;
    }

@Override
protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
   
    if (!level.isClientSide()
        && stack.is(ItemRegistry.INTERIOR_DECORATOR_TOOL.get())
        && player.isCreative()
        && !player.isSpectator()) {

        LOGGER.info("✅ Rotating chair block at {}", pos);

        Direction current = state.getValue(FACING);
        Direction next = current.getClockWise();
        level.setBlock(pos, state.setValue(FACING, next), 3);

        return ItemInteractionResult.SUCCESS;
    }

    LOGGER.info("⏭️ Interaction passed to default.");
    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
}



}