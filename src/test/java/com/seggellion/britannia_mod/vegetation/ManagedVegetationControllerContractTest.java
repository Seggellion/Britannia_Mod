package com.seggellion.britannia_mod.vegetation;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedVegetationControllerContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir"));

    @Test
    void controllerIsRegisteredWithoutAPlayerObtainableBlockItem() throws IOException {
        String blocks = source("registry/BlockRegistry.java");
        String items = source("registry/ItemRegistry.java");
        assertTrue(blocks.contains("MANAGED_VEGETATION_CONTROLLER"));
        assertTrue(blocks.contains("noCollission()"));
        assertTrue(blocks.contains("noOcclusion()"));
        assertFalse(items.contains("MANAGED_VEGETATION_CONTROLLER"));
    }

    @Test
    void invisibleModelAndBlockstateAreValidJsonAssets() throws IOException {
        Path blockstate = PROJECT.resolve(
                "src/main/resources/assets/britannia_mod/blockstates/managed_vegetation_controller.json"
        );
        Path model = PROJECT.resolve(
                "src/main/resources/assets/britannia_mod/models/block/managed_vegetation_controller.json"
        );
        assertTrue(JsonParser.parseString(Files.readString(blockstate)).isJsonObject());
        assertTrue(JsonParser.parseString(Files.readString(model)).getAsJsonObject()
                .getAsJsonArray("elements").isEmpty());
    }

    @Test
    void permissionedCommandsExposeAddRemoveAndInspect() throws IOException {
        String commands = source("commands/ManagedVegetationCommands.java");
        assertTrue(commands.contains("hasPermission(2)"));
        assertTrue(commands.contains("literal(\"add\")"));
        assertTrue(commands.contains("literal(\"remove\")"));
        assertTrue(commands.contains("literal(\"inspect\")"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/" + relative
        ));
    }
}
