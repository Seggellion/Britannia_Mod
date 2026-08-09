package com.seggellion.britannia_mod.structure.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.ResourceId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ShrineRimMaterialSelectionTest {
    private static final Path ASSETS = Path.of(System.getProperty("britannia.projectDir", "."), "src/main/resources/assets/britannia_mod");

    @Test
    void onlyChaosUsesLightGranite() {
        var shrine = ShrineMonolithDefinitions.catalogue()
                .family(ShrineMonolithDefinitions.SHRINE).orElseThrow();
        assertEquals(9, shrine.variants().size());
        long chaosMaterials = shrine.variants().stream()
                .map(variant -> ShrineRimMaterialSelection.textureFor(shrine.id(), variant.id()))
                .filter(ShrineRimMaterialSelection.CHAOS_GRANITE::equals)
                .count();
        long defaultMaterials = shrine.variants().stream()
                .map(variant -> ShrineRimMaterialSelection.textureFor(shrine.id(), variant.id()))
                .filter(ShrineRimMaterialSelection.DEFAULT_GRANITE::equals)
                .count();
        assertEquals(1, chaosMaterials);
        assertEquals(8, defaultMaterials);
        shrine.variants().forEach(variant -> {
            ResourceId expected = variant.id().value().equals("chaos")
                    ? ShrineRimMaterialSelection.CHAOS_GRANITE
                    : ShrineRimMaterialSelection.DEFAULT_GRANITE;
            assertEquals(expected, ShrineRimMaterialSelection.textureFor(shrine.id(), variant.id()),
                    variant.id().value());
        });
    }

    @Test
    void nonShrineAndUnknownIdentitiesFailClosedToDefaultGranite() {
        assertEquals(ShrineRimMaterialSelection.DEFAULT_GRANITE,
                ShrineRimMaterialSelection.textureFor(
                        ShrineMonolithDefinitions.MONOLITH, new VariantId("chaos")));
        assertEquals(ShrineRimMaterialSelection.DEFAULT_GRANITE,
                ShrineRimMaterialSelection.textureFor(
                        ShrineMonolithDefinitions.SHRINE, new VariantId("missing")));
        assertEquals(ShrineRimMaterialSelection.DEFAULT_GRANITE,
                ShrineRimMaterialSelection.textureFor(
                        new FamilyId("missing"), new VariantId("chaos")));
    }

    @Test
    void bothMaterialPathsAreDistinctAvailablePngResources() {
        assertNotEquals(ShrineRimMaterialSelection.DEFAULT_GRANITE,
                ShrineRimMaterialSelection.CHAOS_GRANITE);
        assertTrue(Files.isRegularFile(resolve(ShrineRimMaterialSelection.DEFAULT_GRANITE)));
        assertTrue(Files.isRegularFile(resolve(ShrineRimMaterialSelection.CHAOS_GRANITE)));
    }

    private static Path resolve(ResourceId resource) {
        assertEquals("britannia_mod", resource.namespace());
        return ASSETS.resolve(resource.path());
    }
}
