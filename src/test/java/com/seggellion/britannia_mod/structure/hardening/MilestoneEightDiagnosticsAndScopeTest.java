package com.seggellion.britannia_mod.structure.hardening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.interaction.ShrineVariantCycleService;
import com.seggellion.britannia_mod.structure.placement.ShrinePlacementFailure;
import com.seggellion.britannia_mod.structure.render.ShrineRenderSelection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class MilestoneEightDiagnosticsAndScopeTest {
    @Test
    void missingDefinitionsAndResourcesAreTypedDisplayOnlyAndNonDestructive() {
        FamilyId missingFamily = new FamilyId("missing_family");
        VariantId missingVariant = new VariantId("missing_variant");
        ShrineRenderSelection family = ShrineRenderSelection.resolve(missingFamily, missingVariant);
        ShrineRenderSelection variant = ShrineRenderSelection.resolve(
                ShrineMonolithDefinitions.SHRINE, missingVariant);
        assertEquals(ShrineRenderSelection.Status.UNKNOWN_FAMILY, family.status());
        assertEquals(ShrineRenderSelection.Status.UNKNOWN_VARIANT, variant.status());
        assertEquals(missingFamily, family.familyId());
        assertEquals(missingVariant, family.variantId());
        assertEquals(missingVariant, variant.variantId());
        assertTrue(family.geometry().isEmpty() && family.texture().isEmpty());
        assertTrue(variant.texture().isEmpty());
    }

    @Test
    void diagnosticCachesAndIntegrityQueuesHaveExplicitFiniteCapsAndEviction() throws Exception {
        String geoModel = read("src/main/java/com/seggellion/britannia_mod/client/renderer/shrine/ShrineGeoModel.java");
        String anchor = read("src/main/java/com/seggellion/britannia_mod/structure/multiblock/LargeStructureAnchorBlockEntity.java");
        String integrity = read("src/main/java/com/seggellion/britannia_mod/structure/lifecycle/ShrineIntegrityService.java");
        String handler = read("src/main/java/com/seggellion/britannia_mod/structure/lifecycle/ShrineIntegrityHandler.java");
        assertBoundedMap(geoModel, "MAX_DIAGNOSTICS = 128", "DIAGNOSTICS");
        assertBoundedMap(anchor, "MAX_LOAD_DIAGNOSTICS = 1024", "LOAD_DIAGNOSTICS");
        assertBoundedMap(integrity, "MAX_DIAGNOSTICS = 1024", "DIAGNOSTICS");
        assertTrue(handler.contains("MAX_CHUNKS_PER_TICK = 64"));
        assertTrue(handler.contains("MAX_PENDING_CHUNKS_PER_LEVEL = 4096"));
        assertTrue(handler.contains("PENDING.remove(level)"));
        assertTrue(handler.contains("getChunkNow"));
        assertFalse(handler.contains("addRegionTicket"));
        assertFalse(handler.contains("setChunkForced"));
    }

    @Test
    void failuresRemainTypedWhileUnauthorizedAndInvalidCyclePathsAreIntentionallySilent() throws Exception {
        assertTrue(List.of(ShrinePlacementFailure.values()).contains(
                ShrinePlacementFailure.UNEXPECTED_ROLLBACK_FAILURE));
        assertTrue(List.of(ShrineVariantCycleService.Result.values()).contains(
                ShrineVariantCycleService.Result.INVALID_CYCLE_DEFINITION));
        assertTrue(List.of(ShrineVariantCycleService.Result.values()).contains(
                ShrineVariantCycleService.Result.ROLLBACK_FAILED));
        String placement = read("src/main/java/com/seggellion/britannia_mod/structure/placement/ShrinePlacementService.java");
        String lifecycle = read("src/main/java/com/seggellion/britannia_mod/structure/lifecycle/ShrineLifecycleService.java");
        String cycle = read("src/main/java/com/seggellion/britannia_mod/structure/interaction/ShrineVariantCycleService.java");
        String authorization = read("src/main/java/com/seggellion/britannia_mod/structure/interaction/DecoratorAuthorization.java");
        assertTrue(placement.contains("LOGGER.error(\"Shrine rollback failed safely"));
        assertTrue(lifecycle.contains("LOGGER.error(\"Failed to remove shrine structure"));
        assertFalse(cycle.contains("Logger"));
        assertFalse(authorization.contains("Logger"));
        assertFalse(cycle.contains("net.minecraft.client"));
        assertFalse(authorization.contains("net.minecraft.client"));
    }

    @Test
    void registrationRendererAndCommonClientBoundariesRemainSingular() throws Exception {
        String registry = read("src/main/java/com/seggellion/britannia_mod/registry/LargeStructureRegistry.java");
        String setup = read("src/main/java/com/seggellion/britannia_mod/ClientModSetup.java");
        assertEquals(1, occurrences(registry, "BLOCKS.register(\"large_structure_anchor\""));
        assertEquals(1, occurrences(registry, "BLOCKS.register(\"large_structure_part\""));
        assertEquals(1, occurrences(registry, "BLOCK_ENTITIES.register(\"large_structure\""));
        assertEquals(1, occurrences(setup,
                "registerBlockEntityRenderer(LargeStructureRegistry.LARGE_STRUCTURE.get(), ShrineRenderer::new)"));
        assertFalse(setup.contains("LARGE_STRUCTURE_PART.get(), ShrineRenderer"));
        for (Path root : List.of(
                Path.of(System.getProperty("britannia.projectDir", "."), "src/main/java/com/seggellion/britannia_mod/structure/definition"),
                Path.of(System.getProperty("britannia.projectDir", "."), "src/main/java/com/seggellion/britannia_mod/structure/placement"),
                Path.of(System.getProperty("britannia.projectDir", "."), "src/main/java/com/seggellion/britannia_mod/structure/lifecycle"),
                Path.of(System.getProperty("britannia.projectDir", "."), "src/main/java/com/seggellion/britannia_mod/structure/interaction"))) {
            try (var files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    assertFalse(Files.readString(file).contains("net.minecraft.client"), file.toString());
                }
            }
        }
    }

    private static void assertBoundedMap(String source, String cap, String map) {
        assertTrue(source.contains(cap));
        assertTrue(source.contains(map + ".putIfAbsent"));
        assertTrue(source.contains(map + ".size() >"));
        assertTrue(source.contains(map + ".remove("));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(System.getProperty("britannia.projectDir", "."), path));
    }

    private static int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }
}
