package com.seggellion.britannia_mod.client;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.ModSounds;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

/**
 * Plays the UltimaCraft theme wherever Minecraft would play its own menu music.
 *
 * <p>{@code Minecraft#getSituationalMusic} falls through to {@code Musics.MENU} whenever no
 * player exists, which covers the whole menu state -- title, options, world select, server
 * list -- and that constant is the only thing in the game built on {@code music.menu}. Keying
 * off the selected event therefore replaces every menu track and nothing else: biome,
 * dimension, creative, underwater, boss and credits music all select other events and fall
 * straight through this listener.
 *
 * <p>Reading the selection rather than the open screen is also what keeps the track steady
 * across the branded title screen's transitions -- opening Options changes the screen but not
 * the music, so {@code MusicManager} sees the same sound still playing and leaves it alone.
 */
@EventBusSubscriber(modid = BritanniaMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class MenuMusicBranding {

    /**
     * Zero delays start the theme on the first tick instead of vanilla's staggered wait and let
     * it repeat without a gap; replaceCurrentMusic lets it cut in over whatever was still
     * playing when the player left a world for the menu.
     */
    private static final Music MENU_MUSIC = new Music(ModSounds.MENU_MUSIC, 0, 0, true);

    private MenuMusicBranding() {
    }

    @SubscribeEvent
    public static void replaceMenuMusic(SelectMusicEvent event) {
        Music selected = event.getMusic();
        if (selected == null || selected.getEvent().value() != SoundEvents.MUSIC_MENU.value()) {
            return;
        }

        event.setMusic(MENU_MUSIC);
    }
}
