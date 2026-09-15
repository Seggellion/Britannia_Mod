package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.block.entity.OrangeTreeRootBlockEntity;
import com.seggellion.britannia_mod.block.entity.HouseFarmPlotBlockEntity;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.farming.CropDefinition;
import com.seggellion.britannia_mod.farming.CropRegistry;
import com.seggellion.britannia_mod.farming.FarmingActionType;
import com.seggellion.britannia_mod.farming.FarmingSkill;
import com.seggellion.britannia_mod.farming.FruitTreeDefinition;
import com.seggellion.britannia_mod.farming.OrangeTreeUtils;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class OrangeFruitBlock extends Block {
    public static final BooleanProperty RIPE = BooleanProperty.create("ripe");
    private final String treeTypeId;

    public OrangeFruitBlock(Properties properties) {
        this("orange", properties);
    }

    public OrangeFruitBlock(String treeTypeId, Properties properties) {
        super(properties);
        this.treeTypeId = treeTypeId;
        registerDefaultState(stateDefinition.any().setValue(RIPE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RIPE);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(ItemRegistry.SCISSORS.get())) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
        String fruitName = fruitName();
        if (!state.getValue(RIPE)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.literal("The " + fruitName + " fruit is not ripe yet.").withStyle(ChatFormatting.YELLOW), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide) {
            OrangeTreeRootBlockEntity root = OrangeTreeUtils.findRoot(level, pos).orElse(null);
            if (root == null) {
                player.displayClientMessage(Component.literal("This " + fruitName + " fruit is no longer connected to a tree root.").withStyle(ChatFormatting.YELLOW), true);
                return ItemInteractionResult.SUCCESS;
            }
            if (root.getSoilBlockEntity(level).orElse(null) instanceof HouseFarmPlotBlockEntity housePlot
                    && level instanceof net.minecraft.server.level.ServerLevel serverLevel
                    && !HouseFarmPlotBlock.mayManagePlot(serverLevel, housePlot.getBlockPos(), player)) {
                player.displayClientMessage(Component.literal("You may only harvest a house farm plot you own.").withStyle(ChatFormatting.RED), true);
                return ItemInteractionResult.SUCCESS;
            }

            com.seggellion.britannia_mod.farming.FruitTreeHarvestService.pick(
                    (ServerLevel)level,pos,root,player,hand,stack,true);
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && state.getValue(RIPE)) {
            player.displayClientMessage(Component.literal("Use scissors to harvest the " + fruitName() + " fruit.").withStyle(ChatFormatting.YELLOW), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Compatibility entry point; player callers must complete the same authorized fruit transaction. */
    public static boolean dropFruitFromTree(Level level, BlockPos fruitPos, OrangeTreeRootBlockEntity root, @Nullable Player player, boolean harvestedWithScissors) {
        if (!(level instanceof ServerLevel server) || player == null) return false;
        InteractionHand hand = harvestedWithScissors && player.getOffhandItem().is(ItemRegistry.SCISSORS.get())
                && !player.getMainHandItem().is(ItemRegistry.SCISSORS.get()) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return com.seggellion.britannia_mod.farming.FruitTreeHarvestService.pick(server,fruitPos,root,player,hand,player.getItemInHand(hand),harvestedWithScissors);
    }

    public String treeTypeId() {
        return treeTypeId;
    }

    private String fruitName() {
        return treeTypeId == null || treeTypeId.isBlank() ? "fruit" : treeTypeId.replace('_', ' ');
    }
}
