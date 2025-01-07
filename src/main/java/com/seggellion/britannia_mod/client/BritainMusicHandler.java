package com.seggellion.britannia_mod.client;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.registry.CityRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

import java.util.List;

public class BritainMusicHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean isMusicPlaying = false;

    // SoundEvent for the Britain city music
    public static final SoundEvent BRITAIN_MUSIC_EVENT = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "britain_city_music"));

    private static final String CITY_NAME = "Britain";

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        LOGGER.debug("BritainMusicHandler: Received PlayerTickEvent for player: {}", player.getName().getString());

        if (!player.level().isClientSide()) {
            LOGGER.debug("Skipping event handling for server-side logic.");
            return; // Ensure this logic only runs on the client
        }

        Minecraft minecraft = Minecraft.getInstance();
        Vec3 position = player.position();

        // Retrieve all areas for the city of Britain from the CityRegistry
        List<AABB> britainAreas = CityRegistry.getCityAreas(CITY_NAME);

        // Check if the player is within any of the Britain areas
        boolean isPlayerInBritain = britainAreas.stream().anyMatch(area -> area.contains(position));

        if (isPlayerInBritain) {
            LOGGER.debug("Player {} is inside the Britain city area.", player.getName().getString());
            if (!isMusicPlaying) {
                playCustomMusic(minecraft);
                isMusicPlaying = true;
            }
        } else {
            if (isMusicPlaying) {
                LOGGER.debug("Player {} exited the Britain city area, stopping music.", player.getName().getString());
                stopCustomMusic(minecraft);
                isMusicPlaying = false;
            }
        }
    }

    private static void playCustomMusic(Minecraft minecraft) {
        if (minecraft.getSoundManager() != null) {
            SimpleSoundInstance soundInstance = SimpleSoundInstance.forMusic(BRITAIN_MUSIC_EVENT);
            minecraft.getSoundManager().play(soundInstance);
            LOGGER.info("Playing custom Britain city music: {}", BRITAIN_MUSIC_EVENT.getLocation());
        } else {
            LOGGER.warn("SoundManager is null, unable to play custom music.");
        }
    }

    private static void stopCustomMusic(Minecraft minecraft) {
        if (minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().stop(BRITAIN_MUSIC_EVENT.getLocation(), SoundSource.MUSIC);
            LOGGER.info("Stopped custom Britain city music: {}", BRITAIN_MUSIC_EVENT.getLocation());
        } else {
            LOGGER.warn("SoundManager is null, unable to stop custom music.");
        }
    }
}
