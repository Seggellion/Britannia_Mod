package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.block.CommunityHoedFarmBlock;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.entity.CommunityFarmBlockEntity;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.quest.action.QuestActionEvents;
import com.seggellion.britannia_mod.structure.HouseBuildRights;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** One live eligibility/commit boundary for both block and item fertilizer use. */
public final class FertilizedSoilService {
    private FertilizedSoilService() {}

    public static boolean eligible(Level level, BlockPos pos, Player player) {
        if (player == null || player.isSpectator() || !level.mayInteract(player, pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CommunityHoedFarmBlock
                && state.getValue(com.seggellion.britannia_mod.block.CommunityFarmBlock.PREPARED)
                && level.getBlockEntity(pos) instanceof CommunityFarmBlockEntity prepared) {
            BlockState above = level.getBlockState(pos.above());
            return (above.isAir() || above.getBlock() instanceof com.seggellion.britannia_mod.block.TrellisBlock)
                    && (prepared.getPreparedExpiresAt() == 0 || prepared.getPreparedExpiresAt() > level.getGameTime());
        }
        return state.is(Blocks.FARMLAND) && level.getBlockState(pos.above()).isAir()
                && HouseBuildRights.ownsHouseAt(level, pos, player.getUUID());
    }

    public static ItemInteractionResult apply(Level level, BlockPos pos, Player player, ItemStack stack) {
        if (!stack.is(ItemRegistry.FERTILIZED_DIRT.get()) || stack.isEmpty()) return ItemInteractionResult.FAIL;
        // House records are server-owned. Predict only the target family, never grant client authority.
        if (level.isClientSide) {
            BlockState state = level.getBlockState(pos);
            return state.is(Blocks.FARMLAND) || state.getBlock() instanceof CommunityHoedFarmBlock
                    ? ItemInteractionResult.SUCCESS : ItemInteractionResult.FAIL;
        }
        if (player == null || player.isSpectator() || !level.mayInteract(player, pos)
                || (player.getMainHandItem() != stack && player.getOffhandItem() != stack)) {
            return ItemInteractionResult.FAIL;
        }
        BlockState prior = level.getBlockState(pos);
        CommunityFarmBlockEntity prepared = level.getBlockEntity(pos) instanceof CommunityFarmBlockEntity be ? be : null;
        if (prior.getBlock() instanceof CommunityHoedFarmBlock && prepared != null
                && prepared.preparationExpired(level)) {
            prepared.clearPreparedExpiry();
            level.setBlock(pos, BlockRegistry.COMMUNITY_FARM_BLOCK.get().defaultBlockState(), 3);
            player.displayClientMessage(Component.translatable(
                    com.seggellion.britannia_mod.client.gui.QuestScreenText.PLOT_EXPIRED)
                    .withStyle(ChatFormatting.YELLOW), true);
            return ItemInteractionResult.SUCCESS;
        }
        if (!eligible(level, pos, player)) return ItemInteractionResult.FAIL;
        long budget = prepared == null ? 0 : prepared.remainingPreparationTicks(level.getGameTime());
        var previousData = prepared == null ? null : prepared.saveWithoutMetadata(level.registryAccess());
        BlockState fertile = BlockRegistry.FARMING_BLOCK.get().defaultBlockState().setValue(FarmingBlock.HYDRATION, 1);
        if (!level.setBlock(pos, fertile, 3)) return ItemInteractionResult.FAIL;
        if (!(level.getBlockEntity(pos) instanceof FarmingBlockEntity soil)) {
            level.setBlock(pos, prior, 3);
            if (previousData != null && level.getBlockEntity(pos) instanceof CommunityFarmBlockEntity restored) {
                restored.loadWithComponents(previousData, level.registryAccess());
            }
            return ItemInteractionResult.FAIL;
        }
        soil.beginFertilizerApplication(prior, budget, player.getUUID(), level.getGameTime());
        soil.setHydration(1);
        if (prepared != null) {
            FarmingBlock.scheduleCommunitySeedWindow(level, pos, soil.getSeedableUntilGameTime());
            player.displayClientMessage(Component.translatable(
                            "message.britannia_mod.quest.plot.fertilized_countdown",
                            FarmingBlockEntity.COMMUNITY_SEED_WINDOW_TICKS / 20L)
                    .withStyle(ChatFormatting.GREEN), true);
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        level.playSound(null, pos, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
        if (player instanceof ServerPlayer serverPlayer) FarmingSkill.award(serverPlayer, FarmingActionType.TOOL, 1, 1.0f);
        if (prepared != null) QuestActionEvents.plotFertilize(player, level, pos);
        return ItemInteractionResult.CONSUME;
    }
}
