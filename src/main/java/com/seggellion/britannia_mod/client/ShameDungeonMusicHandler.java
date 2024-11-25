package com.seggellion.britannia_mod.client;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.BritanniaMod;
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

public class ShameDungeonMusicHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Define dungeon area coordinates
    private static final int DUNGEON_MIN_X = 2906;
    private static final int DUNGEON_MAX_X = 3153;
    private static final int DUNGEON_MIN_Z = 3496;
    private static final int DUNGEON_MAX_Z = 3893;
    private static final int DUNGEON_MIN_Y = -1;
    private static final int DUNGEON_MAX_Y = 108;

    private static boolean isMusicPlaying = false;

    // SoundEvent for the dungeon music
    public static final SoundEvent DUNGEON_MUSIC_EVENT = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "shame_dungeon_music"));

   @SubscribeEvent
public static void onPlayerTick(PlayerTickEvent.Post event) {
    Player player = event.getEntity();
    LOGGER.debug("ShameDungeonMusicHandler: Received PlayerTickEvent for player: {}", player.getName().getString());

    if (!player.level().isClientSide()) {
        LOGGER.debug("Skipping event handling for server-side logic.");
        return; // Ensure this logic only runs on the client
    }

    Minecraft minecraft = Minecraft.getInstance();
    Vec3 position = player.position();

    // Check if player is in the dungeon area
    AABB dungeonArea = new AABB(
        new Vec3(DUNGEON_MIN_X, DUNGEON_MIN_Y, DUNGEON_MIN_Z),
        new Vec3(DUNGEON_MAX_X, DUNGEON_MAX_Y, DUNGEON_MAX_Z)
    );

    if (dungeonArea.contains(position)) {
        LOGGER.debug("Player {} is inside the Shame Dungeon area.", player.getName().getString());
        if (!isMusicPlaying) {
            playCustomMusic(minecraft);
            isMusicPlaying = true;
        }
    } else {
        if (isMusicPlaying) {
            LOGGER.debug("Player {} exited the Shame Dungeon area, stopping music.", player.getName().getString());
            stopCustomMusic(minecraft);
            isMusicPlaying = false;
        }
    }
}


    private static void playCustomMusic(Minecraft minecraft) {
        if (minecraft.getSoundManager() != null) {
            SimpleSoundInstance soundInstance = SimpleSoundInstance.forMusic(DUNGEON_MUSIC_EVENT);
            minecraft.getSoundManager().play(soundInstance);
            LOGGER.info("Playing custom dungeon music: {}", DUNGEON_MUSIC_EVENT.getLocation());
        } else {
            LOGGER.warn("SoundManager is null, unable to play custom music.");
        }
    }

    private static void stopCustomMusic(Minecraft minecraft) {
        if (minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().stop(DUNGEON_MUSIC_EVENT.getLocation(), SoundSource.MUSIC);
            LOGGER.info("Stopped custom dungeon music: {}", DUNGEON_MUSIC_EVENT.getLocation());
        } else {
            LOGGER.warn("SoundManager is null, unable to stop custom music.");
        }
    }
}
