// CarpetTeleporterBlockEntity.java
package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import com.seggellion.britannia_mod.teleport.BritanniaTeleportService;
import com.seggellion.britannia_mod.teleport.TeleportDestination;
import com.seggellion.britannia_mod.teleport.TeleportResult;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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
    if (dest == null || level == null) {
        sendUnlinkedMessage(player);
        return;
    }

    // Use the shared server-side teleport path; destination NBT stays destX/destY/destZ for compatibility.
    TeleportResult result = BritanniaTeleportService.teleport(
            player,
            TeleportDestination.inCurrentLevel(
                    player,
                    dest.getX() + 0.5,
                    dest.getY() + 1,
                    dest.getZ() + 0.5,
                    "carpet_teleporter"
            ),
            40
    );

    if (result.success()) {
        SoundEvent soundEvent = ModSounds.MOONGATE_TELEPORT.get();
        level.playSound(null, player.blockPosition(), soundEvent, SoundSource.BLOCKS, 1.0f, 1.0f);
    }
}

private void sendUnlinkedMessage(ServerPlayer player) {
    if (level == null) return;

    CompoundTag data = player.getPersistentData();
    long now = level.getGameTime();
    long lastMessage = data.getLong("britannia_mod:last_unlinked_carpet_message");
    if (now - lastMessage >= 40) {
        data.putLong("britannia_mod:last_unlinked_carpet_message", now);
     //   player.sendSystemMessage(Component.literal("This carpet teleporter is not linked."));
    }
}

}
