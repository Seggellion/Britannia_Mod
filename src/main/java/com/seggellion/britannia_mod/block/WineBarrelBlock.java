package com.seggellion.britannia_mod.block;

import com.mojang.serialization.MapCodec;
import com.seggellion.britannia_mod.block.entity.WineBarrelBlockEntity;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.BlockGetter;

public class WineBarrelBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<WineBarrelBlock> CODEC = simpleCodec(WineBarrelBlock::new);

    public WineBarrelBlock(Properties properties) {
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

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WineBarrelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != BlockEntityRegistry.WINE_BARREL_BE.get()) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<WineBarrelBlockEntity>) WineBarrelBlockEntity::tick;
    }

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 2, 16, 2),
            Block.box(14, 0, 0, 16, 16, 2),
            Block.box(0, 0, 14, 2, 16, 16),
            Block.box(14, 0, 14, 16, 16, 16),
            Block.box(0, 4, 0, 16, 16, 16)
    );

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof WineBarrelBlockEntity barrel)) return ItemInteractionResult.FAIL;

        // 1. Pour Juice
        boolean isRedJuice = stack.getItem() == ItemRegistry.PITCHER_RED_GRAPE_JUICE.get();
        boolean isWhiteJuice = stack.getItem() == ItemRegistry.PITCHER_WHITE_GRAPE_JUICE.get();

        if (isRedJuice || isWhiteJuice) {
            
            // Check NBT data on the pitcher
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
            String variety = tag.contains("GrapeVariety") ? tag.getString("GrapeVariety") : "Wild";
            String region = tag.contains("GrapeRegion") ? tag.getString("GrapeRegion") : "Britannia";
            int quality = tag.contains("Quality") ? tag.getInt("Quality") : 50;

            if (!level.isClientSide) {
                // Try to add the juice
                int result = barrel.tryAddJuice(variety, quality, region);

                if (result == -1) {
                    // Barrel Busy
                    player.displayClientMessage(Component.literal("The barrel is sealed and busy fermenting!").withStyle(ChatFormatting.RED), true);
                } 
                else if (result == -2) {
                    // Mixing Mismatch
                    player.displayClientMessage(
                        Component.literal("Do not mix wine! Barrel contains: " + barrel.getVariety() + " (" + barrel.getRegion() + ")").withStyle(ChatFormatting.DARK_RED), true
                    );
                } 
                else if (result == -3) {
                    // Should be covered by ready check, but just in case
                     player.displayClientMessage(Component.literal("The barrel is full.").withStyle(ChatFormatting.RED), true);
                }
                else {
                    // Success (0 or 1)
                    stack.shrink(1);
                    ItemStack emptyPitcher = new ItemStack(ItemRegistry.PITCHER_EMPTY.get());
                    if (!player.getInventory().add(emptyPitcher)) {
                        player.drop(emptyPitcher, false);
                    }
                    level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);

                    if (result == 1) {
                        player.displayClientMessage(Component.literal("Barrel full! Fermentation started.").withStyle(ChatFormatting.GREEN), true);
                        level.playSound(null, pos, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
                    } else {
                        // Filling progress
                        int current = barrel.getPitchersStored();
                        int max = WineBarrelBlockEntity.MAX_PITCHERS_TO_FILL;
                        player.displayClientMessage(Component.literal("Added " + variety + " (" + current + "/" + max + ")"), true);
                    }
                }
            }
            return ItemInteractionResult.SUCCESS;
        }

        // 2. Check "Bottling" (Open Screen)
        boolean isBlueBottle = stack.getItem() == ItemRegistry.WINE_BOTTLE_BLUE.get(); 
        boolean isBrownBottle = stack.getItem() == ItemRegistry.WINE_BOTTLE_BROWN.get(); 
        boolean isGreenBottle = stack.getItem() == ItemRegistry.WINE_BOTTLE_GREEN.get(); 
        boolean isClearBottle = stack.getItem() == ItemRegistry.WINE_BOTTLE_CLEAR.get();

        if (isBrownBottle || isGreenBottle || isBlueBottle || isClearBottle) {
            if (barrel.isReady()) {
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            } else {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.literal("The wine is not ready yet!").withStyle(ChatFormatting.RED), true);
                }
                return ItemInteractionResult.SUCCESS;
            }
        }

        // 3. Check "Bubbler" Status (Empty Hand)
        if (stack.isEmpty()) {
            if (!level.isClientSide) {
                String status = barrel.getBubblerStatus();
                player.displayClientMessage(Component.literal(status).withStyle(ChatFormatting.AQUA), true);
            }
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}