
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
import com.seggellion.britannia_mod.structure.HouseStyle;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.io.DataInputStream;
import java.util.Optional;


import net.minecraft.nbt.CompoundTag;
import java.time.Instant;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;

public class StructurePlacer {

    private static final Logger LOGGER = LoggerFactory.getLogger(StructurePlacer.class);

public static boolean placeStructure(ServerLevel level,
                                  BlockPos playerPos,
                                  int rotationDeg,
                                  HouseStyle style,
                                  Player player) {

String nbtFile = style.getStructureFile();                 // e.g. "structures/wooden_house.nbt"
    String rawPath = nbtFile.replace(".nbt", "").replaceFirst("^structures/", "");
    ResourceLocation structureId = ResourceLocation.fromNamespaceAndPath("britannia_mod", rawPath);
    ResourceLocation nbtPath     = ResourceLocation.fromNamespaceAndPath("britannia_mod", "structures/" + rawPath + ".nbt");

    /* --------------------------------------------------
       1. Try the normal StructureManager path first
       -------------------------------------------------- */
    StructureTemplate template = level.getStructureManager().getOrCreate(structureId);

    /* --------------------------------------------------
       2. If that failed (size == 0), fall back to manual
       -------------------------------------------------- */
   if (template.getSize().equals(Vec3i.ZERO)) {

    // Ask the resource manager for the structure file
    Optional<Resource> resOpt =
        level.getServer().getResourceManager().getResource(nbtPath);

    if (resOpt.isEmpty()) {
        LOGGER.error("❌ ResourceManager could not find {}", nbtPath);
        return false;
    }

    // resOpt.get() is NOT AutoCloseable → keep it outside the header
    Resource res = resOpt.get();

    // Only close the streams you open
    try (InputStream in  = res.open();
         DataInputStream dis = new DataInputStream(in)) {

        CompoundTag tag = NbtIo.read(dis);
        HolderGetter<Block> blocks =
            level.registryAccess().lookupOrThrow(Registries.BLOCK);

        template = new StructureTemplate();
        template.load(blocks, tag);

        LOGGER.info("✅ Manually loaded {}, size {}", structureId, template.getSize());

    } catch (Exception e) {
        LOGGER.error("💥 Exception loading {} manually", structureId, e);
        return false;
    }
}


    /* --------------------------------------------------
       3. Place the (now‑valid) template
       -------------------------------------------------- */
    Rotation rotation = StructureUtils.getRotation(rotationDeg);// always north‑facing
    Vec3i    rawSize  = template.getSize();
    BlockPos unrotatedDoorOffset    = StructureUtils.getDoorOffset(rawSize);
    BlockPos rotatedDoorOffset = StructureTemplate.calculateRelativePosition(
        new StructurePlaceSettings().setRotation(rotation),
        unrotatedDoorOffset
    );

    Direction playerFacing = player.getDirection();

    BlockPos doorTarget  = playerPos.relative(playerFacing, 2);
    BlockPos adjustedPos = doorTarget.subtract(rotatedDoorOffset);


    StructurePlaceSettings settings = new StructurePlaceSettings()
                                          .setRotation(rotation)
                                          .setIgnoreEntities(true);


// Constraints

// Bounding box (1-block buffer around the structure)
StructureBoxes boxes = StructureUtils.makeStructureBoxes(adjustedPos, rawSize, rotation);

int minX = Mth.floor(boxes.structureBox().minX) - 1;
int maxX = Mth.floor(boxes.structureBox().maxX) + 1;
int minY = Mth.floor(boxes.structureBox().minY);
int maxY = Mth.floor(boxes.structureBox().maxY);
int minZ = Mth.floor(boxes.structureBox().minZ) - 1;
int maxZ = Mth.floor(boxes.structureBox().maxZ) + 1;

boolean valid = true;

outer:
for (int x = minX; x <= maxX; x++) {
    for (int y = minY; y <= maxY; y++) {
        for (int z = minZ; z <= maxZ; z++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);

            boolean isBottom = (y == minY);

            if (isBottom) {
                // Require only grass or sand below
                if (state.getBlock() != Blocks.GRASS_BLOCK && state.getBlock() != Blocks.SAND) {
                    valid = false;
                    break outer;
                }
            } else {
                // Above ground must be air
                if (!state.isAir()) {
                    valid = false;
                    break outer;
                }
            }
        }
    }
}

if (!valid) {
    player.displayClientMessage(Component.literal("❌ Invalid placing location. You need flat grass or sand."), true);
    return false;
}


// end of constraints

                                          

    if (!template.placeInWorld(level, adjustedPos, adjustedPos, settings,
                               level.getRandom(), 3)) {
        LOGGER.error("Failed to place {}", structureId);
        return false;
    }

    

BlockPos.betweenClosedStream(
    new BlockPos(
        Mth.floor(boxes.structureBox().minX),
        Mth.floor(boxes.structureBox().minY),
        Mth.floor(boxes.structureBox().minZ)
    ),
    new BlockPos(
        Mth.floor(boxes.structureBox().maxX) - 1,
        Mth.floor(boxes.structureBox().maxY) - 1,
        Mth.floor(boxes.structureBox().maxZ) - 1
    )
).forEach(pos -> {
    BlockState state = level.getBlockState(pos);
    if (state.getBlock() instanceof HouseSignBlock) {
        LOGGER.info("✅ Correcting sign at {} for rotation {}", pos, rotation);
        Direction correctFacing = switch (rotation) {
            case NONE -> Direction.WEST;
            case CLOCKWISE_90 -> Direction.NORTH;
            case CLOCKWISE_180 -> Direction.EAST;
            case COUNTERCLOCKWISE_90 -> Direction.SOUTH;
        };
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            level.setBlock(pos, state.setValue(HorizontalDirectionalBlock.FACING, correctFacing), 3);
        }
    }
});

    LOGGER.info("Structure {} placed at {} (size {})", structureId, adjustedPos, rawSize);
    UUID houseUuid = UUID.randomUUID();
    // You can keep your HouseLot logic unchanged
    // Lot block goes just inside the door (1 block behind)
    BlockPos baseLot = doorTarget.relative(playerFacing.getOpposite(), 1);
BlockPos lotOffset = StructureTemplate.calculateRelativePosition(
    new StructurePlaceSettings().setRotation(rotation),
    new BlockPos(2, 1, 0)
);


   BlockPos lotPos = adjustedPos.offset(lotOffset); 


    level.setBlock(lotPos,
        BlockRegistry.HOUSE_LOT_BLOCK.get().defaultBlockState(), 3);

    if (level.getBlockEntity(lotPos) instanceof HouseLotBlockEntity lotBE) {
        lotBE.setOwnerUsername(player.getName().getString());
        lotBE.setHouseStyle(style);
        lotBE.setHouseUuid(houseUuid);
        lotBE.setHouseType(style.getSize().id());
        lotBE.setRegionName("Trinsic"); // TODO
        lotBE.setForSale(false);
        lotBE.setPrice(0);
        lotBE.setPlacedAt(Instant.now());
        lotBE.setAccessList(new ArrayList<>());
        lotBE.setChanged();
    }

    StructureRegionManager.registerStructure(
        new StructureRecord(
            player.getUUID(),
            boxes.structureBox(),
            boxes.fullBox(),
            houseUuid,
            style.getSize().id()
        )
    );

    HouseDataAPI.sendHouseDataToRails(level, lotPos, player, style);
    return true;

}



}