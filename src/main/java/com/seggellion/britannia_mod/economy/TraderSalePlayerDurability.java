package com.seggellion.britannia_mod.economy;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;
import java.nio.file.Files;

/** Checked, atomic player-data write for the inventory/settlement marker pair. */
public final class TraderSalePlayerDurability {
    private TraderSalePlayerDurability() {}

    public static boolean save(ServerPlayer player) {
        var directory = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        try {
            CompoundTag data = player.saveWithoutId(new CompoundTag());
            Files.createDirectories(directory);
            IOUtilities.writeNbtCompressed(data, directory.resolve(player.getStringUUID() + ".dat"));
        } catch (Exception failure) {
            LogUtils.getLogger().warn("Trader player-data write failed for {}: {}", player.getUUID(), failure.toString());
            return false;
        }
        // A listener failure cannot undo a completed inventory save or authorize a rollback.
        try {
            net.neoforged.neoforge.event.EventHooks.firePlayerSavingEvent(player, directory.toFile(), player.getStringUUID());
        } catch (RuntimeException failure) {
            LogUtils.getLogger().warn("Player saving listener failed after trader data was saved: {}", failure.toString());
        }
        return true;
    }
}
