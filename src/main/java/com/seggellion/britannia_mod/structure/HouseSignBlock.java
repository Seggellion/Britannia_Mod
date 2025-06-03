package com.seggellion.britannia_mod.structure;

import com.seggellion.britannia_mod.network.HouseManagementScreenPayload;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;

public class HouseSignBlock extends Block implements EntityBlock {
    private static final Logger LOGGER = LogUtils.getLogger();
        
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public static final EnumProperty<SignType> SIGN_TYPE = EnumProperty.create("sign_type", SignType.class);
    public static final EnumProperty<HolderType> HOLDER_TYPE = EnumProperty.create("holder_type", HolderType.class);

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        VoxelShape base = Block.box(0, 0, 0, 16, 16, 16);
        VoxelShape sign = switch (facing) {
            case NORTH -> Block.box(16, 0, 0, 32, 16, 16);
            case SOUTH -> Block.box(-16, 0, 0, 0, 16, 16);
            case WEST -> Block.box(0, 0, -16, 16, 16, 0);
            case EAST -> Block.box(0, 0, 16, 16, 16, 32);
            default -> Block.box(16, 0, 0, 32, 16, 16);
        };
        return Shapes.or(base, sign);
    }

    public enum HolderType implements StringRepresentable {
        WOOD_1("wood_1"),
        WOOD_2("wood_2"),
        METAL_1("metal_1"),
        METAL_2("metal_2"),
        METAL_3("metal_3"),
        METAL_4("metal_4");

        private final String name;

        HolderType(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum SignType implements StringRepresentable {
        DEFAULT("default", SignShape.RECTANGLE),
        TAILOR("tailor", SignShape.OVAL),
        LIBRARY("library", SignShape.RECTANGLE),
        BAKER("baker", SignShape.OVAL);

        private final String name;
        private final SignShape shape;

        SignType(String name, SignShape shape) {
            this.name = name;
            this.shape = shape;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }

        public SignShape getShape() {
            return shape;
        }

        public enum SignShape {
            RECTANGLE,
            OVAL
        }
    }

    public HouseSignBlock() {
        super(BlockBehaviour.Properties
            .of()
            .noOcclusion()
            .strength(3.0f, 3.0f)
        );

        this.registerDefaultState(this.defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(SIGN_TYPE, SignType.DEFAULT)
                .setValue(HOLDER_TYPE, HolderType.WOOD_1)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SIGN_TYPE, HOLDER_TYPE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HouseSignBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        LOGGER.info("Interacted with sign");

        ServerLevel serverLevel = (ServerLevel) level;
        ServerPlayer serverPlayer = (ServerPlayer) player;

        BlockEntity below = level.getBlockEntity(pos.below());
        if (!(below instanceof HouseLotBlockEntity lot)) {
            player.sendSystemMessage(Component.literal("Could not find the house controller."));
            return InteractionResult.FAIL;
        }

        LOGGER.info("EntityBlockFound");

        if (!lot.getOwner().equals(player.getName().getString())) {
            player.sendSystemMessage(Component.literal("You are not the owner of this house."));
            return InteractionResult.FAIL;
        }

        LOGGER.info("Is owner");

        HouseManagementScreenPayload.send(serverPlayer, pos);
        return InteractionResult.SUCCESS;
    }
}