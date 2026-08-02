package com.seggellion.britannia_mod.structure.definition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class MilestoneOneBoundaryTest {
    private static final Path PACKAGE = Path.of(
            "src/main/java/com/seggellion/britannia_mod/structure/definition");

    @Test
    void productionDefinitionPackageHasNoClientWorldRegistrationOrMutationDependencies() throws Exception {
        String source;
        try (var files = Files.walk(PACKAGE)) {
            source = files.filter(path -> path.toString().endsWith(".java"))
                    .map(MilestoneOneBoundaryTest::read)
                    .reduce("", String::concat);
        }
        for (String forbidden : List.of(
                "net.minecraft.client", "DeferredRegister", "BlockEntity", "setBlock(",
                "removeBlock(", "LevelAccessor", "ServerLevel", "ItemRegistry", "BlockRegistry")) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void milestonePackageContainsOnlyPureJavaSourceFiles() throws Exception {
        try (var files = Files.walk(PACKAGE)) {
            assertTrue(files.filter(Files::isRegularFile)
                    .allMatch(path -> path.toString().endsWith(".java")));
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }
}
