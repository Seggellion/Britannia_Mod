package com.seggellion.britannia_mod.wildresource;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WildResourceDocumentationTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));

    @Test
    void handoffDocumentsOperationsBalanceAssetsAndKnownResults() throws IOException {
        String documentation = Files.readString(PROJECT.resolve("docs/wild-resources.md"));
        for (String required : new String[] {
                "MAX_CHUNKS_PER_TICK", "3–6 min", "4–8 min", "squared Euclidean",
                "minecraft:calcite", "WeaponRegistry.DAGGER", "Survival", "Adventure", "Creative",
                "75%", "20%", "5%", "Tags.Biomes.IS_SWAMP", "WildResourceHarvestEvent",
                "349 required GameTests", "Manual in-client checks", "no wild-resource force/debug command"
        }) {
            assertTrue(documentation.contains(required), required);
        }
    }
}
