package com.seggellion.britannia_mod.structure.milestone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MonolithMilestoneSevenScopeTest {
    @Test
    void existingToolSharedTransactionAndSingleAnchorRendererAreReused() throws Exception {
        String registry = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java"));
        String item = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/item/InteriorDecoratorToolItem.java"));
        String setup = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/ClientModSetup.java"));
        assertEquals(1, occurrences(registry, "INTERIOR_DECORATOR_TOOL ="));
        assertEquals(1, occurrences(registry, "new InteriorDecoratorToolItem("));
        assertEquals(1, occurrences(item, "ShrineVariantCycleService.cycle("));
        assertEquals(1, occurrences(setup,
                "registerBlockEntityRenderer(LargeStructureRegistry.LARGE_STRUCTURE.get(), ShrineRenderer::new)"));
        assertFalse(setup.contains("LARGE_STRUCTURE_PART.get(), ShrineRenderer"));
    }

    @Test
    void cycleCommonCodeAcceptsNoClientSelectedIdentityAndCallsNoWorldMutationSubsystem() throws Exception {
        String cycle = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/structure/interaction/ShrineVariantCycleService.java"));
        assertFalse(cycle.contains("net.minecraft.client"));
        assertFalse(cycle.contains("Payload"));
        assertFalse(cycle.contains("desiredVariant"));
        assertFalse(cycle.contains("cycleIndex"));
        assertFalse(cycle.contains("ShrinePlacementPlanner"));
        assertFalse(cycle.contains("ShrinePlacementExecutor"));
        assertFalse(cycle.contains("ShrineIntegrityService"));
        assertFalse(cycle.contains("removeFrom("));
        assertFalse(cycle.contains("setBlock("));
        assertFalse(cycle.contains("shrink("));
        assertFalse(cycle.contains("hurtAndBreak("));
        assertTrue(cycle.contains("StructureVariantCycler.next(family, previous.variantId())"));
    }

    @Test
    void noThirdVariantReverseCycleCrossFamilyConversionOrMilestoneEightContentExists() throws Exception {
        String definitions = Files.readString(Path.of(
                "src/main/java/com/seggellion/britannia_mod/structure/definition/ShrineMonolithDefinitions.java"));
        assertEquals(1, occurrences(definitions, "new VariantId(\"diagnostic_missing_content\")"));
        assertEquals(1, occurrences(definitions, "new VariantId(\"diagnostic_alternate\")"));
        assertFalse(definitions.contains("diagnostic_third"));

        try (var paths = Files.walk(Path.of("src/main"))) {
            var names = paths.filter(Files::isRegularFile).map(Path::toString).toList();
            assertFalse(names.stream().anyMatch(path -> path.contains("MilestoneEight")));
            assertFalse(names.stream().anyMatch(path -> path.contains("ReverseMonolith")
                    || path.contains("MonolithRotation") || path.contains("MonolithResize")));
            assertFalse(names.stream().anyMatch(path -> path.contains("recipes")
                    && path.toLowerCase().contains("monolith")));
        }
    }

    private static int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }
}
