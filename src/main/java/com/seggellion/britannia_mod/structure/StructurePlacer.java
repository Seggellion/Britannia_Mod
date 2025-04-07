
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

import net.minecraft.nbt.CompoundTag;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class StructurePlacer {
    private static final Logger LOGGER = LoggerFactory.getLogger(StructurePlacer.class);

    public static void placeStructure(ServerLevel level, BlockPos basePos, int rotationDeg, String structurePath, Player player) {
        ResourceLocation structureId = ResourceLocation.fromNamespaceAndPath("britannia_mod", structurePath.replace(".nbt", ""));

        // ✅ Load the structure using StructureManager
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
        BlockPos adjustedPos = basePos.offset(-size.getX() / 2, 0, -size.getZ() / 2);

        boolean placed = template.placeInWorld(level, adjustedPos, adjustedPos, settings, level.getRandom(), 3);
        LOGGER.info("Placed structure {} at {} with rotation {}. Size: {}", structurePath, adjustedPos, rotation, size);
    }
}