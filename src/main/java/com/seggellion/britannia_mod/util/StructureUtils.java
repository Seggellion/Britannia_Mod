package com.seggellion.britannia_mod.util;

import com.seggellion.britannia_mod.structure.StructureBoxes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;


public class StructureUtils {

    public static Rotation getRotationFromPlayerFacing(Direction direction) {
        return switch (direction) {
            case NORTH -> Rotation.NONE;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.CLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    // Given player facing direction, what rotation turns structure's NORTH to that?
public static Rotation getRotationToFace(Direction playerFacing) {
    return switch (playerFacing) {
        case NORTH -> Rotation.NONE;                // door already faces north
        case EAST -> Rotation.CLOCKWISE_90;         // rotate so door ends east
        case SOUTH -> Rotation.CLOCKWISE_180;       // rotate so door ends south
        case WEST -> Rotation.COUNTERCLOCKWISE_90;  // rotate so door ends west
        default -> Rotation.NONE;
    };
}


    public static BlockPos getRotatedDoorOffset(Rotation rotation, BlockPos doorOffset) {
        return StructureTemplate.calculateRelativePosition(
                new StructurePlaceSettings().setRotation(rotation),
                doorOffset
        );
    }

    public static BlockPos getAdjustedStructurePos(BlockPos playerPos, Rotation rotation, BlockPos doorOffset) {
        BlockPos rotated = getRotatedDoorOffset(rotation, doorOffset);
        return playerPos.subtract(rotated);
    }



    public static Rotation getRotation(int rotationDeg) {
        // rotationDeg in [0, 90, 180, 270]
        return Rotation.values()[(rotationDeg / 90) % 4];
    }

    public static BlockPos getDoorOffset(Vec3i size) {
        // If door is at z=0, centered on X:
        
return new BlockPos(size.getX() / 2, 0, 0); // X center, Z front (north)
    }

public static Vec3i getRotatedSize(Vec3i originalSize, Rotation rotation) {
    return switch (rotation) {
        case NONE, CLOCKWISE_180 -> new Vec3i(originalSize.getX(), originalSize.getY(), originalSize.getZ());
        case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> new Vec3i(originalSize.getZ(), originalSize.getY(), originalSize.getX());
    };
}





    public static BlockPos getAdjustedPosForDoor(BlockPos doorTarget, Rotation rotation, BlockPos doorOffset) {
        // Rotate the door offset
        BlockPos rotatedOffset = StructureTemplate.calculateRelativePosition(
            new StructurePlaceSettings().setRotation(rotation), doorOffset
        );
        // The final structure origin is doorTarget - rotatedDoorOffset
        return doorTarget.subtract(rotatedOffset);
    }

    public static StructureBoxes makeStructureBoxes(BlockPos origin, Vec3i size, Rotation rotation) {
    int w = size.getX();
    int h = size.getY();
    int d = size.getZ();

    BlockPos min;
    BlockPos max;

    switch (rotation) {
        case NONE -> {
            min = origin;
            max = origin.offset(w - 1, h - 1, d - 1);
        }
        case CLOCKWISE_90 -> {
            min = origin.offset(-(d - 1), 0, 0);
            max = origin.offset(0, h - 1, w - 1);
        }
        case CLOCKWISE_180 -> {
            min = origin.offset(-(w - 1), 0, -(d - 1));
            max = origin.offset(0, h - 1, 0);
        }
        case COUNTERCLOCKWISE_90 -> {
            min = origin.offset(0, 0, -(w - 1));
            max = origin.offset(d - 1, h - 1, 0);
        }
        default -> throw new IllegalStateException("Unexpected rotation: " + rotation);
    }

// Create normal bounding box
AABB structureBox = new AABB(
    min.getX(), min.getY(), min.getZ(),
    max.getX() + 1, max.getY() + 1, max.getZ() + 1
);


    // Now expand downwards for basement (-10 blocks down)
    AABB fullBox = new AABB(
        structureBox.minX, structureBox.minY - 10, structureBox.minZ,
        structureBox.maxX, structureBox.maxY, structureBox.maxZ
    );

    return new StructureBoxes(structureBox, fullBox);
}


}
