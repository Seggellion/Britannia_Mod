
package com.seggellion.britannia_mod.structure;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.NbtIo;
import net.minecraft.core.registries.BuiltInRegistries;
import java.io.InputStream;
import net.minecraft.core.registries.Registries;
import java.io.IOException;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.core.Direction;
import com.seggellion.britannia_mod.util.HouseDataAPI;
import com.seggellion.britannia_mod.util.StructureUtils;
import com.seggellion.britannia_mod.structure.HouseSize;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;

import net.minecraft.world.level.block.entity.BlockEntity;

import net.minecraft.world.phys.AABB;


import net.minecraft.nbt.CompoundTag;
import java.time.Instant;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;


    public class StructurePlacer {
        private static final Logger LOGGER = LoggerFactory.getLogger(StructurePlacer.class);

    public static void placeStructure(ServerLevel level, BlockPos playerPos, int rotationDeg, String structurePath, Player player) {
        ResourceLocation structureId = ResourceLocation.fromNamespaceAndPath("britannia_mod", structurePath.replace(".nbt", ""));
        LOGGER.info("Placing structure from {}", structureId);

        StructureTemplate template = level.getStructureManager().getOrCreate(structureId);
        if (template == null || template.getSize().equals(Vec3i.ZERO)) {
            LOGGER.error("Structure {} could not be found or is empty", structureId);
            return;
        }

        Rotation rotation = StructureUtils.getRotation(rotationDeg);
        StructurePlaceSettings settings = new StructurePlaceSettings()
            .setRotation(rotation)
            .setIgnoreEntities(true);

Vec3i rawSize = template.getSize(); // this is BEFORE rotation


// Apply rotation to get the true size


    
      BlockPos localDoorOffset = StructureUtils.getDoorOffset(rawSize); // no rotation yet
BlockPos rotatedDoorOffset = StructureTemplate.calculateRelativePosition(
    new StructurePlaceSettings().setRotation(rotation),
    localDoorOffset
);


        // Calculate doorTarget = 3 blocks outward from player in rotation direction
        double radians = Math.toRadians((rotationDeg + 90) % 360);
        double offsetX = Math.cos(radians) * 3;
        double offsetZ = Math.sin(radians) * 3;
        BlockPos doorTarget = new BlockPos(
            (int) (player.getX() + offsetX),
            playerPos.getY(),
            (int) (player.getZ() + offsetZ)
        );

        // Apply directional adjustment (East +1, South +2), respecting current rotation
        BlockPos extraOffset = StructureTemplate.calculateRelativePosition(
            new StructurePlaceSettings().setRotation(rotation),
            new BlockPos(-3, 0, -3)
        );
        doorTarget = doorTarget.offset(extraOffset);


   BlockPos adjustedPos = StructureUtils.getAdjustedPosForDoor(doorTarget, Rotation.NONE, rotatedDoorOffset);

        UUID houseUuid = UUID.randomUUID(); // Generate it only once

        boolean placed = template.placeInWorld(level, adjustedPos, adjustedPos, settings, level.getRandom(), 3);
        LOGGER.info("Structure placed at {}, rotated {}, rotatedSize {}", adjustedPos, rotation);

        // Place HouseLot block 1 block behind door
        Direction facing = rotation.rotate(Direction.EAST); // Door is EAST in NBT
        BlockPos baseLotPos = doorTarget.relative(facing.getOpposite(), 1);

        // Apply offset: +1 East, -1 North (in unrotated structure space)
        BlockPos rotatedOffset = StructureTemplate.calculateRelativePosition(
            new StructurePlaceSettings().setRotation(rotation),
            new BlockPos(1, 1, -2)
        );

        BlockPos lotPos = baseLotPos.offset(rotatedOffset);

        level.setBlock(lotPos, BlockRegistry.HOUSE_LOT_BLOCK.get().defaultBlockState(), 3);

        BlockEntity be = level.getBlockEntity(lotPos);
        if (be instanceof HouseLotBlockEntity lotBE) {
            lotBE.setOwnerUsername(player.getName().getString());
            lotBE.setHouseSize(HouseSize.SMALL);
            lotBE.setHouseUuid(houseUuid);
            lotBE.setHouseType("small");
            lotBE.setRegionName("Trinsic");
            lotBE.setForSale(false);
            lotBE.setPrice(0);
            lotBE.setPlacedAt(Instant.now());
            lotBE.setAccessList(new ArrayList<>());
            lotBE.setChanged();
        }

int w = rawSize.getX();
int h = rawSize.getY();
int d = rawSize.getZ();

BlockPos min;
BlockPos max;

// this has to get refined -- but the 180 is good to go
switch (rotation) {
    case NONE -> {
        min = adjustedPos;
        max = adjustedPos.offset(w - 1, h - 1, d - 1);
    }
    case CLOCKWISE_90 -> {
        min = adjustedPos.offset(-(d - 1), 0, 0);
        max = adjustedPos.offset(0, h - 1, w - 1);
    }
    case CLOCKWISE_180 -> {
        min = adjustedPos.offset(-(w - 1), 0, -(d - 1));
        max = adjustedPos.offset(1, h - 1, 1);
    }
    case COUNTERCLOCKWISE_90 -> {
        min = adjustedPos.offset(0, 0, -(w - 1));
        max = adjustedPos.offset(d - 1, h - 1, 0);
    }
    default -> throw new IllegalStateException("Unexpected rotation: " + rotation);
}


AABB structureBox = new AABB(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
int basementMinY = min.getY() - 5;
AABB fullBox = new AABB(min.getX(), basementMinY, min.getZ(), max.getX(), max.getY(), max.getZ());



LOGGER.info("AdjustedPos: {}", adjustedPos);
LOGGER.info("basementMinY: {}", basementMinY);
LOGGER.info("StructureBox: {}", structureBox);
LOGGER.info("FullBox: {}", fullBox);



StructureRegionManager.registerStructure(new StructureRecord(player.getUUID(), structureBox, fullBox, houseUuid));

        HouseDataAPI.sendHouseDataToRails(level, lotPos, player, HouseSize.SMALL);
    }


    }