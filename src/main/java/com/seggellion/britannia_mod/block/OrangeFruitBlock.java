package com.seggellion.britannia_mod.block;

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
import net.minecraft.sounds.SoundEvents;
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

            if (!dropFruitFromTree(level, pos, root, player, true)) {
                return ItemInteractionResult.SUCCESS;
            }
            FruitTreeDefinition definition = root.definition();
            level.setBlock(pos, definition.leafBlock().get().defaultBlockState(), 3);
            root.onFruitHarvested(pos);

            FarmingBlockEntity soil = root.getSoilBlockEntity(level).orElse(null);
            if (soil != null && soil.consumeSuccessfulFertileHarvest() == 0
                    && level instanceof ServerLevel serverLevel) {
                root.cleanupTree(serverLevel, player, false);
                FarmingBlock.exhaustFertileSoil(level, soil.getBlockPos(), soil);
            }
            level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
            if (!player.getAbilities().instabuild) {
                stack.hurtAndBreak(1, player, Player.getSlotForHand(hand));
            }

            CropDefinition crop = CropRegistry.byId(root.getTreeTypeId()).orElse(null);
            if (crop != null && player instanceof ServerPlayer serverPlayer) {
                FarmingSkill.award(serverPlayer, FarmingActionType.HARVEST, crop.tier(), crop.farmingSkillModifier());
            }
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

    public static boolean dropFruitFromTree(Level level, BlockPos fruitPos, OrangeTreeRootBlockEntity root, @Nullable Player player, boolean harvestedWithScissors) {
        BlockState state = level.getBlockState(fruitPos);
        FruitTreeDefinition definition = root.definition();
        if (!state.is(definition.fruitBlock().get()) || !state.getValue(RIPE)) {
            return false;
        }
        int count = definition.randomFruitYield(level.getRandom());
        ItemStack harvest = root.createHarvestStack(player, count);
        if (player != null && harvestedWithScissors) {
            if (!player.getInventory().add(harvest)) {
                player.drop(harvest, false);
            }
        } else {
            popResource(level, fruitPos, harvest);
        }
        return true;
    }

    public String treeTypeId() {
        return treeTypeId;
    }

    private String fruitName() {
        return treeTypeId == null || treeTypeId.isBlank() ? "fruit" : treeTypeId.replace('_', ' ');
    }
}
