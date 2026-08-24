
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import com.seggellion.britannia_mod.item.HouseKeyItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;


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
    // Milestone 2: the rotation as it was actually applied. getRotation() folds any request
    // into one of four, so the incoming degrees are not necessarily what the house got;
    // persisting this instead means a rehydrated region matches the structure on the ground.
    final int canonicalRotationDeg = rotation.ordinal() * 90;
    Vec3i    rawSize  = template.getSize();
    // The entrance the style declares, not one derived from the template size. A house whose
    // front door is not at front-centre -- the villa, the patio, the keep -- would otherwise
    // land offset from where the player aimed by however far its real door is from the middle.
    BlockPos unrotatedDoorOffset    = style.getDoorOffset();
    BlockPos rotatedDoorOffset = StructureTemplate.calculateRelativePosition(
        new StructurePlaceSettings().setRotation(rotation),
        unrotatedDoorOffset
    );

    Direction playerFacing = player.getDirection();

    BlockPos doorTarget  = playerPos.relative(playerFacing, 1);
    BlockPos adjustedPos = doorTarget.above().subtract(rotatedDoorOffset);


    StructurePlaceSettings settings = new StructurePlaceSettings()
                                          .setRotation(rotation)
                                          .setIgnoreEntities(true);

// Constraints
//
// Clearance is derived from the footprint, not padded around it -- see HousePlacementClearance
// for what the old skirt actually demanded and why removing it does not weaken anything.
StructureBoxes boxes = StructureUtils.makeStructureBoxes(adjustedPos, rawSize, rotation);

HousePlacementClearance.Refusal refusal = findClearanceRefusal(level, boxes.structureBox());
if (refusal != null) {
    LOGGER.warn("❌ {} at {}", refusal.reason(), refusal.where());
    player.displayClientMessage(Component.literal("❌ " + refusal.reason()), true);
    return false;
}
// end of constraints

                                          

    if (!template.placeInWorld(level, adjustedPos, adjustedPos, settings,
                               level.getRandom(), 3)) {
        LOGGER.error("Failed to place {}", structureId);
        return false;
    }

    

// The authored house sign, in world coordinates. Collected during the same pass that corrects
// its facing, because the sign is also what tells us where the house's controller belongs --
// see lotPositionFor below.
java.util.List<BlockPos> houseSigns = new ArrayList<>();

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
        houseSigns.add(pos.immutable());
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
    // The controller block, directly beneath the house's own sign. That pairing is the contract
    // HouseSignBlock reads back, and lotPositionFor is where it is stated.

   BlockPos lotPos = lotPositionFor(style, rawSize, rotation, adjustedPos, houseSigns);


    level.setBlock(lotPos,
        BlockRegistry.HOUSE_LOT_BLOCK.get().defaultBlockState(), 3);
    UUID deedUuid = null;

    if (level.getBlockEntity(lotPos) instanceof HouseLotBlockEntity lotBE) {
        lotBE.setOwnerUsername(player.getName().getString());
        lotBE.setHouseStyle(style);
        lotBE.setHouseUuid(houseUuid);
        lotBE.setHouseType(style.getSize().id());
        lotBE.setRegionName("Blank"); // TODO
        lotBE.setForSale(false);
        lotBE.setPrice(0);
        lotBE.setPlacedAt(Instant.now());
        lotBE.setAccessList(new ArrayList<>());
        // Milestone 2: the local copy of the region inputs. Rails is what the shard
        // rehydrates from, but a house placed while Rails was down never got there, and the
        // lot block is the only thing left in the world that knows how it was turned.
        lotBE.setRotationDeg(canonicalRotationDeg);
        lotBE.setOwnerUuid(player.getUUID());
        lotBE.setChanged();

    // ✅ Apply deed_uuid if available

    ItemStack heldItem = player.getMainHandItem();
    if (!heldItem.isEmpty()) {
        CustomData customData = heldItem.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = (customData != null) ? customData.copyTag() : new CompoundTag();

        if (tag != null && tag.contains("deed_id")) {
            try {
                deedUuid = UUID.fromString(tag.getString("deed_id"));
                lotBE.setDeedUuid(deedUuid);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Invalid deed_uuid format in item: {}", tag.getString("deed_id"));
            }
        }
    }


    }

    StructureRecord record = new StructureRecord(
        player.getUUID(),
        boxes.structureBox(),
        boxes.fullBox(),
        houseUuid,
        style.getSize().id(),
        style.name(),
        deedUuid,
        canonicalRotationDeg,
        level.dimension()
    );
    StructureRegionManager.registerStructure(record);

    // Milestone 2: the same record goes to Rails, with the origin it was placed at, so the
    // region can be put back after a restart instead of being lost with the process.
    HouseDataAPI.sendHouseDataToRails(level, lotPos, player, style, record, adjustedPos);

    HouseKeyItem keyItem = (HouseKeyItem) ItemRegistry.HOUSE_KEY.get();
    ItemStack    key     = keyItem.createKey(houseUuid);
    if (!player.addItem(key.copy())) player.drop(key, false);

    return true;

}

    /**
     * Where this house's controller block belongs.
     *
     * <h2>The contract, stated once</h2>
     *
     * <p>A house's controller is the {@code HouseLotBlockEntity}: it carries the house UUID, the
     * owner, the privacy flag and the rotation, and {@code HouseUtil.findLot} resolves it for every
     * door and every management action. It is not authored into the structure -- no shipped NBT
     * contains one -- so placement has to decide where it goes.
     *
     * <p>{@code HouseSignBlock.useWithoutItem} reads it from {@code pos.below()}. That is the
     * contract, and it is the only one: the sign and the controller are a pair, and a sign with no
     * controller directly beneath it is the "Could not find the house controller." message.
     *
     * <p>It used to be computed instead, from {@code width / 2 + HouseStyle.getLotOffsetX()} with
     * y and z hard-coded to 1 and 0. That agreed with the authored sign for the six small houses
     * and the castle and disagreed for the other three, which is exactly the set of houses the
     * message was reported against:
     *
     * <pre>
     *   villa   sign (6,3,0)   computed lot (1,1,0)   wrong in x and y
     *   patio   sign (8,2,0)   computed lot (9,1,0)   wrong in x
     *   keep    sign (10,3,2)  computed lot (11,1,0)  wrong in x, y and z
     * </pre>
     *
     * <p>Two independent descriptions of one position will always drift, and the authored one is
     * the one a person can see. So the sign decides, and the table no longer has to be kept in step
     * with the exports.
     *
     * <p>The declared offset survives only as the answer for a structure with no sign at all. That
     * is not a fallback that guesses: it is the historical authored value for such a house, and
     * {@code HouseControllerContractTest} asserts that every registered style ships exactly one
     * sign, so no shipped house can reach it.
     */
    static BlockPos lotPositionFor(HouseStyle style,
                                   Vec3i rawSize,
                                   Rotation rotation,
                                   BlockPos origin,
                                   java.util.List<BlockPos> houseSigns) {
        if (houseSigns.size() == 1) {
            return houseSigns.get(0).below();
        }
        if (houseSigns.size() > 1) {
            LOGGER.error("{} placed {} house signs; a house has exactly one controller, so the "
                    + "first one found is being used. Fix the structure.", style, houseSigns.size());
            return houseSigns.get(0).below();
        }
        LOGGER.error("{} placed no house sign, so its controller position cannot be read from the "
                + "structure. Falling back to the declared lot offset; its sign, if one is added "
                + "later, will not resolve it.", style);
        BlockPos declared = new BlockPos(rawSize.getX() / 2 + style.getLotOffsetX(), 1, 0);
        return origin.offset(StructureTemplate.calculateRelativePosition(
                new StructurePlaceSettings().setRotation(rotation), declared));
    }

    /**
     * Why this footprint cannot take a house, or {@code null} if it can.
     *
     * <p>Three checks, each naming its own cell so the player is told what is in the way instead of
     * being told to find flatter ground and guess. In footprint order -- foundation, then the volume
     * the structure writes into, then the region registry -- because the first two are what a player
     * can see and fix.
     */
    private static HousePlacementClearance.Refusal findClearanceRefusal(ServerLevel level, AABB structureBox) {
        int[] columns = HousePlacementClearance.foundationColumns(structureBox);
        int groundY = HousePlacementClearance.foundationY(structureBox);
        for (int x = columns[0]; x <= columns[1]; x++) {
            for (int z = columns[2]; z <= columns[3]; z++) {
                BlockPos ground = new BlockPos(x, groundY, z);
                BlockState state = level.getBlockState(ground);
                if (state.getBlock() != Blocks.GRASS_BLOCK && state.getBlock() != Blocks.SAND) {
                    return new HousePlacementClearance.Refusal(
                            "A house needs flat grass or sand under all of it.", ground);
                }
            }
        }

        int[] volume = HousePlacementClearance.occupiedVolume(structureBox);
        for (int x = volume[0]; x <= volume[1]; x++) {
            for (int z = volume[4]; z <= volume[5]; z++) {
                for (int y = volume[2]; y <= volume[3]; y++) {
                    BlockPos cell = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(cell);
                    if (!state.isAir() && !state.canBeReplaced()) {
                        return new HousePlacementClearance.Refusal(
                                "Something is in the way where the house would stand.", cell);
                    }
                }
            }
        }

        StructureRecord overlap =
                HousePlacementClearance.overlappingHouse(level.dimension(), structureBox);
        if (overlap != null) {
            // Deliberately the only rule that consults the registry rather than the blocks: a
            // neighbouring house's claim extends ten blocks below its floor, where there is nothing
            // to bump into, and it exists whether or not its chunks are loaded.
            return new HousePlacementClearance.Refusal(
                    "That overlaps another house.",
                    BlockPos.containing(overlap.getStructureBox().getCenter()));
        }
        return null;
    }
}
