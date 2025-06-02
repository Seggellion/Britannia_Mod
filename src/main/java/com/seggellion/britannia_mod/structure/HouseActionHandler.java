package com.seggellion.britannia_mod.structure;

import com.seggellion.britannia_mod.util.HouseDataAPI;
import com.seggellion.britannia_mod.structure.StructureRegionManager;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.item.AbstractHouseDeedItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.SectionPos;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;


import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.List;
import java.util.UUID;


public class HouseActionHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
public static void handleRedeed(ServerPlayer player) {
    ServerLevel level = player.serverLevel();
    BlockPos origin = player.blockPosition();
    UUID playerId = player.getUUID();

    List<StructureRecord> structures = StructureRegionManager.getStructuresInChunk(
        SectionPos.blockToSectionCoord(origin.getX()),
        SectionPos.blockToSectionCoord(origin.getZ())
    );

    for (StructureRecord record : structures) {
        if (!record.getOwnerUuid().equals(playerId)) continue;

        AABB box = record.getStructureBox(); 

        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(box.maxY);

        // ✅ Step 1: Wipe structure above ground only (leave basement untouched)
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.isLoaded(pos)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        LOGGER.info("maxY: {}",maxY);
        LOGGER.info("minY: {}",minY);

        // ✅ Step 2: Restore basement (STONE layers below structure, GRASS on top)
        int basementStartY = minY - 10;
        int basementEndY = minY - 2;
        int grassY = minY - 1;
              LOGGER.info("basementStartY: {}",basementStartY);
        LOGGER.info("basementEndY: {}",basementEndY);

        for (int y = basementStartY; y <= basementEndY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.isLoaded(pos)) {
                        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                    }
                }
            }
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, grassY, z);
                if (level.isLoaded(pos)) {
                    level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                }
            }
        }

        // ✅ Step 3: Unregister the structure
        StructureRegionManager.unregisterStructure(record);

        // ✅ Step 4: Notify Rails and give back deed
         HouseDataAPI.deleteHouseRecord(player, record);

ItemStack deedStack = ItemRegistry.ITEMS.getEntries().stream()
    .map(DeferredHolder::get)
    .filter(i -> i instanceof AbstractHouseDeedItem d
                && d.getHouseStyle().getSize().id().equals(record.getSizeId()))
    .findFirst()
    .map(ItemStack::new)
    .orElse(ItemStack.EMPTY);


        player.getInventory().placeItemBackInInventory(deedStack);
        player.sendSystemMessage(Component.literal("Your house has been re-deeded. The deed has been returned."));
        LOGGER.info("House successfully re-deeded for {}", player.getName().getString());

        return;
    }

    player.sendSystemMessage(Component.literal("No house structure found to re-deed."));
}

    
}
