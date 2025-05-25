// CarpetTeleporterBlockEntity.java
package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import com.seggellion.britannia_mod.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class CarpetTeleporterBlockEntity extends BlockEntity {

    public CarpetTeleporterBlockEntity(BlockPos pos, BlockState st) {
        super(BlockEntityRegistry.CARPET_TELEPORTER_BLOCK_ENTITY_TYPE.get(), pos, st);
    }

    /* ───────────────────────── destination storage ───────────────────────── */

public void setTarget(BlockPos pos) {
    CompoundTag tag = getPersistentData();
    if (pos != null) {
        tag.putInt("destX", pos.getX());
        tag.putInt("destY", pos.getY());
        tag.putInt("destZ", pos.getZ());
    } else {
        tag.remove("destX");
        tag.remove("destY");
        tag.remove("destZ");
    }
    setChanged();
}

public BlockPos getTarget() {
    CompoundTag tag = getPersistentData();
    if (tag.contains("destX")) {
        return new BlockPos(tag.getInt("destX"), tag.getInt("destY"), tag.getInt("destZ"));
    }
    return null;
}

public void teleport(ServerPlayer player) {
    BlockPos dest = getTarget();
    if (dest != null && level != null) {


    CompoundTag data = player.getPersistentData();
    long now = level.getGameTime();
    long lastTeleport = data.getLong("britannia_mod:last_carpet_teleport");

        level.getChunk(dest); // Force chunk load
        SoundEvent soundEvent = ModSounds.MOONGATE_TELEPORT.get();
        // 🔊 Play sound at source location
        level.playSound(
            null,                                // null = play for all nearby players
            player.blockPosition(),              // position of the player before teleport
            soundEvent,       // or your custom sound
            SoundSource.BLOCKS,                  // or SoundSource.PLAYERS
            1.0f,                                // volume
            1.0f                                 // pitch
        );
        player.teleportTo(dest.getX() + 0.5, dest.getY() + 1, dest.getZ() + 0.5);

    }
}

}
