package com.seggellion.britannia_mod.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Only committed server planting calls this; the species key is shared with the plot HUD. */
public final class FarmingPlantingFeedback {
    private FarmingPlantingFeedback() {}

    public static Component speciesName(String species) {
        return Component.translatable("crop.britannia_mod." + species);
    }

    public static void planted(ServerLevel level, BlockPos pos, ServerPlayer player,
                               String species, int tier, float modifier, boolean free) {
        level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1f, 1f);
        player.displayClientMessage(Component.translatable("message.britannia_mod.seed_planted", speciesName(species)), true);
        if (!free) FarmingSkill.award(player, FarmingActionType.PLANT, tier, modifier);
    }
}
