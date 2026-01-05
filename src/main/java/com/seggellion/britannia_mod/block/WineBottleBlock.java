package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.block.entity.WineBottleBlockEntity;
import com.seggellion.britannia_mod.item.WineBottleBlockItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.util.LabelColor;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public class WineBottleBlock extends HorizontalDirectionalBlock implements EntityBlock {
    // A small bottle shape (approx 4x4 pixels wide, 10 pixels high) centered
    private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 10, 10);
    public static final MapCodec<WineBottleBlock> CODEC = simpleCodec(WineBottleBlock::new);
public static final EnumProperty<LabelColor> LABEL = EnumProperty.create("label", LabelColor.class);

    public WineBottleBlock(Properties properties) {
        super(properties);
       this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(LABEL, LabelColor.NONE));;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WineBottleBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LABEL);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    
    // Prevent placing on air/invalid surfaces
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    // --- DATA TRANSFER: Item -> Block ---
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);


// Read color from Item NBT
        String colorStr = "none";
        if (stack.has(DataComponentRegistry.WINE_DATA)) {
            colorStr = WineBottleBlockItem.getWineData(stack).labelColor();
        }

        // Match string to Enum
        LabelColor colorEnum = LabelColor.NONE;
        for (LabelColor c : LabelColor.values()) {
            if (c.getSerializedName().equalsIgnoreCase(colorStr)) {
                colorEnum = c;
                break;
            }
        }

        // Update BlockState
        level.setBlock(pos, state.setValue(LABEL, colorEnum), 3);


        if (level.getBlockEntity(pos) instanceof WineBottleBlockEntity be) {
            // Use the helper method from your Item class to read the data
            be.setWineData(WineBottleBlockItem.getWineData(stack));
        }
    }

    // --- DATA TRANSFER: Block -> Item (Middle Click / Pick Block) ---
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        // FIX: Use 'this.asItem()' to dynamically get the correct colored item
        ItemStack stack = new ItemStack(this.asItem()); 
        
        if (level.getBlockEntity(pos) instanceof WineBottleBlockEntity be) {
            WineBottleBlockItem.setWineData(
                stack,
                be.getWineData().wineryName(),
                be.getWineData().grapeType(),
                be.getWineData().year(),
                be.getWineData().quality(),
                be.getWineData().region(),
                be.getWineData().labelColor()
            );
        }
        return stack;
    }
    
    // Note: For Survival Drops (breaking the block), you usually use a Loot Table JSON 
    // with the "minecraft:copy_components" function.
}