package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.WeightedWoodBlockEntity;
import com.seggellion.britannia_mod.item.WeightedWoodItem;
import com.seggellion.britannia_mod.item.WeightedWoodType;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

public class WeightedWoodBlock extends RotatedPillarBlock implements EntityBlock {
    public static final EnumProperty<WeightedWoodType> WOOD_TYPE = EnumProperty.create("wood_type", WeightedWoodType.class);

    public WeightedWoodBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(WOOD_TYPE, WeightedWoodType.OAK));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        WeightedWoodBlockEntity blockEntity = new WeightedWoodBlockEntity(pos, state);
        blockEntity.setWoodData(state.getValue(WOOD_TYPE).id(), state.getValue(WOOD_TYPE).averageWeight());
        return blockEntity;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WOOD_TYPE);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !movedByPiston && !level.isClientSide) {
            ItemStack drop = createDropStack(state, level.getBlockEntity(pos));
            level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, drop));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public static ItemStack createDropStack(BlockState state, @Nullable BlockEntity blockEntity) {
        WeightedWoodType type = state.hasProperty(WOOD_TYPE) ? state.getValue(WOOD_TYPE) : WeightedWoodType.OAK;
        double weight = type.averageWeight();
        if (blockEntity instanceof WeightedWoodBlockEntity weightedWood) {
            type = weightedWood.getWoodTypeDefinition();
            weight = weightedWood.getWoodWeight();
        }

        ItemStack stack = new ItemStack(ItemRegistry.WEIGHTED_WOOD_ITEM.get());
        if (stack.getItem() instanceof WeightedWoodItem weightedWoodItem) {
            weightedWoodItem.setWoodType(stack, type.id());
            weightedWoodItem.setWeight(stack, weight);
        }
        return stack;
    }
}
