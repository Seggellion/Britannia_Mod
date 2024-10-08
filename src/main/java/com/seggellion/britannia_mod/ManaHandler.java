package com.seggellion.britannia_mod.magic;

import com.seggellion.britannia_mod.network.ManaSyncPayload;
import com.seggellion.britannia_mod.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class ManaHandler {
    private static final int MAX_MANA = 100;
    private static final int MANA_REGEN_RATE = 1;
    private static final int TICKS_PER_MANA = 200;
    private static final String MANA_TAG = "mana";
    private static final String TICK_COUNTER_TAG = "mana_tick_counter";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new ManaHandler());
    }

    // Track player's mana
    public static int getMana(Player player) {
        CompoundTag tag = player.getPersistentData();
        return tag.contains(MANA_TAG) ? tag.getInt(MANA_TAG) : MAX_MANA;
    }

    public static void setMana(Player player, int mana) {
        player.getPersistentData().putInt(MANA_TAG, Math.min(MAX_MANA, Math.max(0, mana)));
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.sendToPlayer(serverPlayer, new ManaSyncPayload(mana));  // Send updated mana to client
        }
    }

    // Add this new method to ManaHandler
    public static void reduceMana(Player player, int amount) {
        int currentMana = getMana(player);
        int newMana = Math.max(0, currentMana - amount);
        setMana(player, newMana);
        LOGGER.info("Reduced mana by {} points from player: {}. New mana: {}", amount, player.getName().getString(), newMana);
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide()) {
            CompoundTag tag = player.getPersistentData();
            int tickCounter = tag.getInt(TICK_COUNTER_TAG); 
            tickCounter++;

            if (tickCounter >= TICKS_PER_MANA) {
                int currentMana = getMana(player);
                if (currentMana < MAX_MANA) {
                    setMana(player, currentMana + MANA_REGEN_RATE);
                    LOGGER.info("Mana regenerated: {} for player {}", getMana(player), player.getName().getString());
                }
                tickCounter = 0;
            }
            tag.putInt(TICK_COUNTER_TAG, tickCounter);
        }
    }

    public static boolean useMana(Player player, int amount) {
        int currentMana = getMana(player);
        LOGGER.info("Attempting to use mana. Current Mana: {}, Amount needed: {}", currentMana, amount);
        if (currentMana >= amount) {
            setMana(player, currentMana - amount);
            LOGGER.info("Mana used. New Mana: {}", getMana(player));
            return true;
        } else {
            player.sendSystemMessage(Component.literal("Not enough mana!"));
            LOGGER.info("Not enough mana to cast the spell!");
            return false;
        }
    }
}
