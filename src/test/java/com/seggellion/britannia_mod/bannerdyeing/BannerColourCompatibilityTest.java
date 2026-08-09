package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderDataSnapshot;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshotTestFactory;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import com.seggellion.britannia_mod.dye.service.DyeResolver;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BannerColourCompatibilityTest {
    @Test
    void paletteMappingEvolutionAffectsFreshDyesButNeverReinterpretsStoredColourIds() throws Exception {
        RegistrySnapshot before = DyeResolverFixtures.productionSnapshot();
        FabricMaterialId cotton = FabricMaterialId.parse("britannia_mod:cotton");
        PigmentId madder = PigmentId.parse("britannia_mod:madder_red");
        MaterialPalette palette = before.materialPalettes().require(
                before.fabricMaterials().require(cotton).paletteId());
        ResolvedColourId originalTarget = palette.pigmentOverrides().get(madder);
        ResolvedColourId changedTarget = palette.entries().stream()
                .map(entry -> entry.id())
                .filter(id -> !id.equals(originalTarget))
                .findFirst().orElseThrow();
        LinkedHashMap<PigmentId, ResolvedColourId> changedOverrides =
                new LinkedHashMap<>(palette.pigmentOverrides());
        changedOverrides.put(madder, changedTarget);
        RegistrySnapshot after = RegistrySnapshotTestFactory.replacePalette(before, new MaterialPalette(
                palette.schemaVersion(), palette.id(), palette.materialId(), palette.naturalColourId(),
                palette.entries(), changedOverrides));

        BannerInstanceState saved = new BannerInstanceState(
                1,
                BannerDefinitionId.parse("britannia_mod:small_curtain"),
                cotton,
                originalTarget,
                Optional.of(madder),
                MountId.parse("britannia_mod:iron"));
        assertEquals(originalTarget, saved.resolvedColourId());
        int oldDisplay = BannerRenderDataSnapshot.fromRegistry(before).materials().get(cotton)
                .displaySrgbByColour().get(saved.resolvedColourId());
        int evolvedDisplay = BannerRenderDataSnapshot.fromRegistry(after).materials().get(cotton)
                .displaySrgbByColour().get(saved.resolvedColourId());
        assertEquals(oldDisplay, evolvedDisplay);

        ResolvedColourId freshBefore = new DyeResolver().resolve(madder, cotton, before)
                .result().orElseThrow().resolvedColourId();
        ResolvedColourId freshAfter = new DyeResolver().resolve(madder, cotton, after)
                .result().orElseThrow().resolvedColourId();
        assertEquals(originalTarget, freshBefore);
        assertEquals(changedTarget, freshAfter);
        assertNotEquals(saved.resolvedColourId(), freshAfter);
    }
}
