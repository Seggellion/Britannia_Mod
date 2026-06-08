package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.teleport.BritanniaTeleportService;
import com.seggellion.britannia_mod.teleport.TeleportDestination;
import com.seggellion.britannia_mod.teleport.TeleportResult;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class DungeonMoongateBlockEntity extends BlockEntity {

    public DungeonMoongateBlockEntity(BlockPos pos, BlockState state) {
        super(BlockRegistry.DUNGEON_MOONGATE_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    public void setPairedMoongatePos(BlockPos pos) {
        CompoundTag tag = this.getPersistentData();
        if (pos != null) {
            tag.putInt("PairedX", pos.getX());
            tag.putInt("PairedY", pos.getY());
            tag.putInt("PairedZ", pos.getZ());
        } else {
            tag.remove("PairedX");
            tag.remove("PairedY");
            tag.remove("PairedZ");
        }
        this.setChanged(); // Mark the block entity as changed
    }

    public BlockPos getPairedMoongatePos() {
        CompoundTag tag = this.getPersistentData();
        if (tag.contains("PairedX")) {
            int x = tag.getInt("PairedX");
            int y = tag.getInt("PairedY");
            int z = tag.getInt("PairedZ");
            return new BlockPos(x, y, z);
        }
        return null;
    }

    public TeleportResult teleportPlayer(ServerPlayer player) {
        BlockPos pairedPos = getPairedMoongatePos();
        if (pairedPos != null && level != null) {
            // Shared server-side teleport path; paired NBT keys stay PairedX/PairedY/PairedZ for compatibility.
            return BritanniaTeleportService.teleport(
                    player,
                    TeleportDestination.inCurrentLevel(
                            player,
                            pairedPos.getX() + 0.5,
                            pairedPos.getY() + 1,
                            pairedPos.getZ() + 0.5,
                            "dungeon_moongate"
                    ),
                    100
            );
        }

        player.sendSystemMessage(Component.literal("This moongate is not linked to another moongate."));
        return TeleportResult.failure("unlinked", "dungeon moongate has no paired position");
    }

    public void unlinkPairedMoongate() {
        BlockPos pairedPos = getPairedMoongatePos();
        if (pairedPos != null && level != null) {
            BlockEntity pairedEntity = level.getBlockEntity(pairedPos);
            if (pairedEntity instanceof DungeonMoongateBlockEntity moongateEntity) {
                moongateEntity.setPairedMoongatePos(null);
                moongateEntity.setChanged();
            }
            // Remove this moongate's paired position
            setPairedMoongatePos(null);
            setChanged();
        }
    }
}
