package com.seggellion.britannia_mod.structure;

import com.seggellion.britannia_mod.util.HouseDataAPI;
import com.seggellion.britannia_mod.structure.StructureRegionManager;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.item.AbstractHouseDeedItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Block;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;


import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class HouseActionHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    
    
    public static void handleRedeed(ServerPlayer player) {
    ServerLevel level = player.serverLevel();
    BlockPos origin = player.blockPosition();
    UUID playerId = player.getUUID();
        LOGGER.info("Initializing handleRedeed");

    List<StructureRecord> structures = StructureRegionManager.getStructuresInChunk(
        SectionPos.blockToSectionCoord(origin.getX()),
        SectionPos.blockToSectionCoord(origin.getZ())
    );
        LOGGER.info("structures: {}", structures);

    for (StructureRecord record : structures) {
        if (!record.getOwnerUuid().equals(playerId)) continue;
     LOGGER.info("Record loaded");
        AABB box = record.getStructureBox();

        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(box.maxY);

        // ✅ Step 4: Return deed — check if blessed
        ItemStack deedStack = ItemRegistry.ITEMS.getEntries().stream()
            .map(DeferredHolder::get)
            .filter(i -> i instanceof AbstractHouseDeedItem d
                && d.getHouseStyle().name().equals(record.getStyleId()))
            .findFirst()
            .map(ItemStack::new)
            .orElse(ItemStack.EMPTY);


 // 🔁 Preserve blessing and deed ID
        BlockPos lotPos = new BlockPos(origin.getX(), minY, origin.getZ()); // fallback if unknown
        List<BlockPos> positions = new ArrayList<>();
        BlockPos.betweenClosedStream(minX, minY, minZ, maxX, maxY, maxZ).forEach(pos -> positions.add(pos.immutable()));

        for (BlockPos pos : positions) {

            if (level.getBlockEntity(pos) instanceof HouseLotBlockEntity lot) {
                lotPos = pos;
     LOGGER.info("Just before DeedUUID");

                UUID deedUuid = lot.getDeedUuid();
                if (deedUuid != null) {
                    
                    CompoundTag tag = new CompoundTag();
                    tag.putBoolean("blessed", true);
                    tag.putString("owner", player.getUUID().toString());
                    tag.putString("deed_id", deedUuid.toString());

                    deedStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                    String prettyName = Arrays.stream(record.getStyleId().toLowerCase().split("_"))
                        .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1))
                        .collect(Collectors.joining(" "));

                    deedStack.set(DataComponents.CUSTOM_NAME, Component.literal(prettyName + "Deed (Blessed)"));
                }
                break;
            }
        }

        // ✅ Step 1: Wipe structure (above ground only)
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.isLoaded(pos)) {
                        level.removeBlock(pos, false); // false = do not drop items
                    }
                }
            }
        }

        // ✅ Step 2: Refill basement
        int basementStartY = minY - 10;
        int basementEndY = minY - 2;
        int grassY = minY - 1;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos groundProbe = new BlockPos(x, basementEndY, z);
                Block groundBlock = level.getBlockState(groundProbe).getBlock();

                Block backfill = Blocks.STONE;
                if (groundBlock == Blocks.SAND || groundBlock == Blocks.RED_SAND) {
                    backfill = Blocks.SAND;
                } else if (groundBlock == Blocks.DIRT || groundBlock == Blocks.GRASS_BLOCK || groundBlock == Blocks.COARSE_DIRT) {
                    backfill = Blocks.DIRT;
                }

                for (int y = basementStartY; y <= basementEndY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.isLoaded(pos)) {
                        level.setBlock(pos, backfill.defaultBlockState(), 3);
                    }
                }

                BlockPos topPos = new BlockPos(x, grassY, z);
                if (level.isLoaded(topPos)) {
                    level.setBlock(topPos, backfill == Blocks.DIRT ? Blocks.GRASS_BLOCK.defaultBlockState() : backfill.defaultBlockState(), 3);
                }
            }
        }

        // ✅ Step 3: Remove from structure manager and Rails
        StructureRegionManager.unregisterStructure(record);
        HouseDataAPI.deleteHouseRecord(player, record);


       

        // ✅ Give deed back to player
        player.getInventory().placeItemBackInInventory(deedStack);
        player.sendSystemMessage(Component.literal("Your house has been re-deeded. The deed has been returned."));
        LOGGER.info("House successfully re-deeded for {}", player.getName().getString());
        return;
    }

    player.sendSystemMessage(Component.literal("No house structure found to re-deed."));
}


    
}
