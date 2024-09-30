package com.seggellion.britannia_mod.world;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class WorldLoader {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void copyAndLoadWorld() {
        Path destination = new File(Minecraft.getInstance().gameDirectory, "saves/custom_world").toPath();
        if (Files.exists(destination)) return;

        try (InputStream worldStream = WorldLoader.class.getClassLoader().getResourceAsStream("worlds/custom_world.zip")) {
            if (worldStream == null) {
                LOGGER.error("Could not find custom world resource. Check the resource path.");
                return;
            }

            Path tempZip = Files.createTempFile("custom_world", ".zip");
            Files.copy(worldStream, tempZip, StandardCopyOption.REPLACE_EXISTING);
            unzip(tempZip, destination);
            Files.delete(tempZip);
            LOGGER.info("Custom world copied successfully.");
        } catch (IOException e) {
            LOGGER.error("Error while copying the world save", e);
        }
    }

    private static void unzip(Path zipFilePath, Path destinationDir) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipFilePath))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path entryPath = destinationDir.resolve(entry.getName());
                if (entry.isDirectory()) Files.createDirectories(entryPath);
                else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zipInputStream, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zipInputStream.closeEntry();
            }
        }
    }
}
