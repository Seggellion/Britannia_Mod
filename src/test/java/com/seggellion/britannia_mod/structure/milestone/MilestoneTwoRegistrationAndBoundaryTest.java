package com.seggellion.britannia_mod.structure.milestone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MilestoneTwoRegistrationAndBoundaryTest {
    private static final Path MAIN = Path.of("src/main/java/com/seggellion/britannia_mod");
    private static final Path RESOURCES = Path.of("src/main/resources/assets/britannia_mod");

    @Test
    void exactlyOneAnchorPartEntityAndShrineItemAreRegisteredWithoutBlockItems() throws Exception {
        String registry = Files.readString(MAIN.resolve("registry/LargeStructureRegistry.java"));
        assertEquals(1, count(registry, "BLOCKS\\.register\\(\"large_structure_anchor\""));
        assertEquals(1, count(registry, "BLOCKS\\.register\\(\"large_structure_part\""));
        assertEquals(1, count(registry, "BLOCK_ENTITIES\\.register\\(\"large_structure\""));
        assertEquals(1, count(registry, "ITEMS\\.register\\(\\s*\"shrine\""));
        assertTrue(registry.contains("LargeStructureAnchorBlockEntity::new, LARGE_STRUCTURE_ANCHOR.get()"));
        assertFalse(registry.contains("BlockItem"));
        assertEquals(1, count(registry, "pushReaction\\(PushReaction\\.BLOCK\\)"));
        assertTrue(Files.readString(MAIN.resolve("BritanniaMod.java"))
                .contains("LargeStructureRegistry.register(modEventBus)"));
    }

    @Test
    void diagnosticResourcesExistWithoutAnchorOrPartItemModelsOrLootTables() throws Exception {
        assertTrue(Files.isRegularFile(RESOURCES.resolve("blockstates/large_structure_anchor.json")));
        assertTrue(Files.isRegularFile(RESOURCES.resolve("blockstates/large_structure_part.json")));
        assertTrue(Files.isRegularFile(RESOURCES.resolve("models/item/shrine.json")));
        assertFalse(Files.exists(RESOURCES.resolve("models/item/large_structure_anchor.json")));
        assertFalse(Files.exists(RESOURCES.resolve("models/item/large_structure_part.json")));
        assertFalse(Files.exists(Path.of(
                "src/main/resources/data/britannia_mod/loot_tables/blocks/large_structure_anchor.json")));
        assertFalse(Files.exists(Path.of(
                "src/main/resources/data/britannia_mod/loot_tables/blocks/large_structure_part.json")));
        String lang = Files.readString(RESOURCES.resolve("lang/en_us.json"));
        assertTrue(lang.contains("item.britannia_mod.shrine"));
        assertTrue(lang.contains("message.britannia_mod.shrine.placement.failed_safely"));
    }

    @Test
    void placementIsServerAuthoritativeLoadedChunkOnlyProtectedAndDropSuppressing() throws Exception {
        String service = Files.readString(MAIN.resolve(
                "structure/placement/ShrinePlacementService.java"));
        assertTrue(service.contains("instanceof ServerLevel"));
        assertTrue(service.contains("getChunkSource().hasChunk"));
        assertFalse(service.contains("getChunk("));
        assertTrue(service.contains("mayInteract"));
        assertTrue(service.contains("mayUseItemAt"));
        assertTrue(service.contains("UPDATE_SUPPRESS_DROPS"));
        assertTrue(service.contains("player.hasInfiniteMaterials()"));
        assertTrue(service.contains("LargeStructurePartBlock.anchorPosition"));
    }

    @Test
    void noMilestoneThreeOrLaterBehaviorWasAdded() throws Exception {
        Set<Path> folders = Set.of(
                MAIN.resolve("structure/multiblock"),
                MAIN.resolve("structure/placement"),
                MAIN.resolve("structure/item"));
        for (Path folder : folders) {
            try (var paths = Files.walk(folder)) {
                for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(path);
                    assertFalse(source.contains("net.minecraft.client"), path.toString());
                    assertFalse(source.contains("BlockEntityRenderer"), path.toString());
                    assertFalse(source.contains("InteriorDecorator"), path.toString());
                    assertFalse(source.contains("saveAdditional"), path.toString());
                    assertFalse(source.contains("loadAdditional"), path.toString());
                    assertFalse(source.contains("recipe"), path.toString());
                }
            }
        }
        String registry = Files.readString(MAIN.resolve("registry/LargeStructureRegistry.java"));
        assertFalse(registry.contains("MONOLITH"));
        assertFalse(registry.contains("\"monolith\""));
        assertFalse(Files.exists(MAIN.resolve("structure/renderer")));
    }

    private static long count(String input, String regex) {
        return Pattern.compile(regex, Pattern.MULTILINE).matcher(input).results().count();
    }
}
