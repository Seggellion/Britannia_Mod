package com.seggellion.britannia_mod.effects;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Utility class for handling common spell effects, such as freezing a player during casting.
 */
public class SpellEffectHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * Freezes the player during spell casting for a specified duration.
     *
     * @param player The player to freeze. This should not be null.
     * @param castTime The number of ticks to freeze the player for.
     * @param onComplete A runnable to execute after the cast time is complete.
     */
    public static void freezePlayerDuringCast(ServerPlayer player, int castTime, Runnable onComplete) {
    
        if (player == null) {
            return; // Prevent NullPointerException by immediately returning if the player is null
        }
LOGGER.info("freezePlayerDuringCast");
        player.sendSystemMessage(Component.literal("Freezing..."));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, castTime, 255, false, false)); // Extreme slowness
        player.setNoGravity(true);  // Disable gravity
        player.setInvulnerable(true);  // Make player invulnerable

        player.getServer().execute(() -> {
            for (int i = 0; i < castTime; i++) {
                if (player == null || player.getServer() == null) {
                    return; // Ensure player and server are still valid
                }

                player.setDeltaMovement(0, 0, 0); // Ensure no movement
                try {
                    Thread.sleep(50);  // Ensure player remains frozen
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt(); // Re-interrupt the thread if interrupted
                }
            }
            if (player != null) {
                player.setNoGravity(false);  // Re-enable gravity
                player.setInvulnerable(false);  // Make player vulnerable again
                player.onUpdateAbilities();  // Refresh player abilities
            }

            // Execute onComplete action after cast time
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }
}
