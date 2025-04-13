
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

public static void placeStructure(ServerLevel level, BlockPos targetPos, int rotationDeg, String structurePath, Player player) {
    ResourceLocation structureId = ResourceLocation.fromNamespaceAndPath("britannia_mod", structurePath.replace(".nbt", ""));
    LOGGER.info("Placing structure from {}", structureId);

    StructureTemplate template = level.getStructureManager().getOrCreate(structureId);
    if (template == null || template.getSize().getX() == 0) {
        LOGGER.error("Structure {} could not be found or is empty", structureId);
        return;
    }

    Rotation rotation = Rotation.values()[rotationDeg / 90];
    StructurePlaceSettings settings = new StructurePlaceSettings()
        .setRotation(rotation)
        .setIgnoreEntities(true);

    Vec3i size = template.getSize();

    // 👇 Define front door offset relative to structure origin inside .nbt
    // For example, maybe front door is 3 blocks forward and 4 blocks right in default orientation
    BlockPos doorOffset = new BlockPos(4, 0, 0); // ← Replace with actual value from structure
   // BlockPos rotatedDoorOffset = rotateOffset(doorOffsetInNbt, rotation);

    // 👇 Adjust the structure's origin so the door appears at the player position
    BlockPos adjustedPos = StructureUtils.getAdjustedStructurePos(targetPos, rotation, doorOffset);

    // ✅ Place structure with rotated settings
    boolean placed = template.placeInWorld(level, adjustedPos, adjustedPos, settings, level.getRandom(), 3);
    LOGGER.info("Structure placed at {}, rotated {}, size {}", adjustedPos, rotation, size);

    // ✅ Compute world position of front door
    BlockPos doorWorldPos = targetPos;

    // ✅ Place lot block next to front door (1 block behind)
Direction facing = rotation.rotate(Direction.SOUTH);
BlockPos lotPos = doorWorldPos.relative(facing.getOpposite(), 1);

    level.setBlock(lotPos, BlockRegistry.HOUSE_LOT_BLOCK.get().defaultBlockState(), 3);

    BlockEntity be = level.getBlockEntity(lotPos);
    if (be instanceof HouseLotBlockEntity lotBE) {
        lotBE.setOwnerUsername(player.getName().getString());
        lotBE.setHouseSize(HouseSize.SMALL);
        lotBE.setHouseUuid(UUID.randomUUID());
        lotBE.setHouseType("small");
        lotBE.setRegionName("Trinsic");
        lotBE.setForSale(false);
        lotBE.setPrice(0);
        lotBE.setPlacedAt(Instant.now());
        lotBE.setAccessList(new ArrayList<>());
        lotBE.setChanged();
    }

    // ✅ Register structure boundaries
    int minY = adjustedPos.getY() - 20;
    int maxY = adjustedPos.getY() + size.getY() + 30;

    AABB boundingBox = new AABB(
        adjustedPos.getX(), minY, adjustedPos.getZ(),
        adjustedPos.getX() + size.getX(), maxY, adjustedPos.getZ() + size.getZ()
    );
    StructureRegionManager.registerStructure(new StructureRecord(player.getUUID(), boundingBox));

    // ✅ Sync to Rails
    HouseDataAPI.sendHouseDataToRails(level, lotPos, player, HouseSize.SMALL);
}

private static BlockPos rotateOffset(BlockPos offset, Rotation rotation) {
    switch (rotation) {
        case NONE:
            return offset;
        case CLOCKWISE_90:
            return new BlockPos(-offset.getZ(), offset.getY(), offset.getX());
        case CLOCKWISE_180:
            return new BlockPos(-offset.getX(), offset.getY(), -offset.getZ());
        case COUNTERCLOCKWISE_90:
            return new BlockPos(offset.getZ(), offset.getY(), -offset.getX());
        default:
            return offset;
    }
}


}