package com.seggellion.britannia_mod.util;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

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
        
        return new BlockPos(size.getX() - 1, 0, size.getZ() / 2); // X+, Z center
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

}
