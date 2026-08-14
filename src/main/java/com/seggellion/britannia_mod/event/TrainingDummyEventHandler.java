package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.block.TrainingDummyBlock;
import com.seggellion.britannia_mod.training.TrainingDummyService;
import com.seggellion.britannia_mod.training.TrainingWeaponClassifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Intercepts supported main-hand strikes before block mining can damage the weapon. */
public final class TrainingDummyEventHandler {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        // Creative must retain completely vanilla block-breaking semantics.
        if (isCreative(event.getEntity())) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!(state.getBlock() instanceof TrainingDummyBlock dummy)) {
            return;
        }

        ItemStack mainHand = event.getEntity().getItemInHand(InteractionHand.MAIN_HAND);
        if (event.getEntity().isShiftKeyDown()
                || TrainingWeaponClassifier.classify(mainHand).isEmpty()) {
            return;
        }

        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCanceled(true);
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)
                || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }

        attemptStrike(player, level, event.getPos());
    }

    /** Shared server-side strike boundary for vanilla clicks and the Adventure-mode C2S fallback. */
    public static boolean attemptStrike(ServerPlayer player, ServerLevel level, net.minecraft.core.BlockPos pos) {
        if (player == null || isCreative(player)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TrainingDummyBlock dummy)) {
            return false;
        }
        TrainingDummyService.AttemptResult result =
                TrainingDummyService.attempt(player, player.getMainHandItem());
        if (!result.accepted() || !dummy.triggerHit(level, pos, state)) {
            return false;
        }
        level.playSound(
                null, pos, ModSounds.TRAINING_DUMMY_HIT.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    /**
     * The server-side game mode is the authority for the creative bypass, matching
     * {@code StructureProtectionHandler}'s and {@code MiningBreakGate}'s convention. Deliberately
     * NOT {@link Player#isCreative()} on the server: that is an overridable derived view —
     * GameTestHelper's mock players hard-code it to {@code true} whatever their real game mode —
     * while {@code gameMode.getGameModeForPlayer()} is the same state the vanilla break pipeline
     * itself consults. The abilities-derived view is only used on the logical client, where
     * {@code LeftClickBlock} also fires and no server game mode exists.
     */
    private static boolean isCreative(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return serverPlayer.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
        }
        return player.isCreative();
    }
}
