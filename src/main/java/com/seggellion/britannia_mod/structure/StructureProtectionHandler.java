package com.seggellion.britannia_mod.structure;

import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.seggellion.britannia_mod.item.TwoHandedAxeItem;
import com.seggellion.britannia_mod.item.QualityToolItem; 
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

public class StructureProtectionHandler {

    public static void register() {
        NeoForge.EVENT_BUS.register(new StructureProtectionHandler());
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

// If creative, ignore all checks
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;

        ItemStack heldItem = player.getMainHandItem();
        boolean isAllowedTool = (heldItem.getItem() instanceof QualityToolItem) || 
                                (heldItem.getItem() instanceof TwoHandedAxeItem);

        if (isAllowedTool) {
            return; // Skip the "Inside House" check completely
        }

        if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) return;

        BlockPos target = event.getPos();
        int chunkX = target.getX() >> 4;
        int chunkZ = target.getZ() >> 4;

        List<StructureRecord> records = StructureRegionManager.getStructuresInChunk(chunkX, chunkZ);
        UUID playerId = player.getUUID();

 boolean isInsideOwnedRegion = records.stream()
            .anyMatch(record ->
                record.getOwnerUuid().equals(playerId)
                && record.getFullBox().contains(target.getCenter())
            );

        if (!isInsideOwnedRegion) {
            event.setCanceled(true);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("You can't break blocks outside your house."));
        }
    }
}
