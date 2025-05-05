
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

    /**
     * Places a house structure in the world.
     *
     * @param level       server level
     * @param playerPos   block‑pos the player is standing on
     * @param rotationDeg current rotation from {@link HouseRotationData}
     * @param size        which house we are placing (SMALL, MEDIUM, …)
     * @param player      placing player
     */
    public static void placeStructure(ServerLevel level,
                                      BlockPos playerPos,
                                      int rotationDeg,
                                      HouseSize size,
                                      Player player) {

        /* ---------- 1. resolve structure file ---------- */
        String nbtFile = size.structureFile();                 // e.g. "medium_house.nbt"
        ResourceLocation structureId =
                ResourceLocation.fromNamespaceAndPath("britannia_mod",
                        nbtFile.replace(".nbt", ""));
        LOGGER.info("Placing structure {} (rotation {}° for {})",
                     structureId, rotationDeg, player.getName().getString());

        StructureTemplate template = level.getStructureManager().getOrCreate(structureId);
        if (template == null || template.getSize().equals(Vec3i.ZERO)) {
            LOGGER.error("Structure {} could not be found or is empty", structureId);
            return;
        }

        /* ---------- 2. rotation & offsets ---------- */
        Rotation rotation = StructureUtils.getRotation(rotationDeg);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setIgnoreEntities(true);

        Vec3i rawSize = template.getSize(); // pre‑rotation
        BlockPos localDoorOffset = StructureUtils.getDoorOffset(rawSize);
        BlockPos rotatedDoorOffset = StructureTemplate.calculateRelativePosition(
                new StructurePlaceSettings().setRotation(rotation),
                localDoorOffset);

        /* player → door is always 3 blocks */
        double radians = Math.toRadians((rotationDeg + 90) % 360);
        BlockPos doorTarget = new BlockPos(
                (int) (player.getX() + Math.cos(radians) * 3),
                playerPos.getY(),
                (int) (player.getZ() + Math.sin(radians) * 3));

        BlockPos extraOffset = StructureTemplate.calculateRelativePosition(
                new StructurePlaceSettings().setRotation(rotation),
                new BlockPos(-3, 0, -3));
        doorTarget = doorTarget.offset(extraOffset);

        BlockPos adjustedPos = StructureUtils
                .getAdjustedPosForDoor(doorTarget, Rotation.NONE, rotatedDoorOffset);

        /* ---------- 3. place structure ---------- */
        UUID houseUuid = UUID.randomUUID();
        boolean placed = template.placeInWorld(level, adjustedPos, adjustedPos,
                                               settings, level.getRandom(), 3);
        if (!placed) {
            LOGGER.error("Failed to place {}", structureId);
            return;
        }
        LOGGER.info("Structure placed at {} (rot {} size {})",
                    adjustedPos, rotation, rawSize);

        /* ---------- 4. create HouseLot block/entity ---------- */
        Direction facing   = rotation.rotate(Direction.EAST);           // door faces EAST in NBT
        BlockPos baseLot   = doorTarget.relative(facing.getOpposite(), 1);
        BlockPos lotOffset = StructureTemplate.calculateRelativePosition(
                new StructurePlaceSettings().setRotation(rotation),
                new BlockPos(1, 1, -2));
        BlockPos lotPos = baseLot.offset(lotOffset);

        level.setBlock(lotPos,
                BlockRegistry.HOUSE_LOT_BLOCK.get().defaultBlockState(), 3);

        if (level.getBlockEntity(lotPos) instanceof HouseLotBlockEntity lotBE) {
            lotBE.setOwnerUsername(player.getName().getString());
            lotBE.setHouseSize(size);                 // <-- enum, no more hard‑coding
            lotBE.setHouseUuid(houseUuid);
            lotBE.setHouseType(size.id());           // "small", "medium", …
            lotBE.setRegionName("Trinsic");          // TODO: dynamic region lookup
            lotBE.setForSale(false);
            lotBE.setPrice(0);
            lotBE.setPlacedAt(Instant.now());
            lotBE.setAccessList(new ArrayList<>());
            lotBE.setChanged();
        }

        /* ---------- 5. register region + basement ---------- */
  StructureBoxes boxes = StructureUtils.makeStructureBoxes(adjustedPos, rawSize, rotation);
StructureRegionManager.registerStructure(
    new StructureRecord(player.getUUID(),
        boxes.structureBox(),
        boxes.fullBox(),     
        houseUuid,
        size.id()));  // <- now it matches correctly


        /* ---------- 6. sync to Rails ---------- */
        HouseDataAPI.sendHouseDataToRails(level, lotPos, player, size);
    }
}
