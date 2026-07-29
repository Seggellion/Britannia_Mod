package com.seggellion.britannia_mod.bannerdyeing;

import com.seggellion.britannia_mod.bannerdyeing.registry.ProductionBannerCatalogue;

import static org.junit.jupiter.api.Assertions.*;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderDataSnapshot;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshotTestFactory;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone7RegisteredTestContent;
import com.seggellion.britannia_mod.client.banner.*;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.util.LinkedHashMap;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BannerRenderStateAndKeyTest {
    private static RegistrySnapshot registry;
    private static BannerRenderDataSnapshot renderData;
    private static ClientBannerRenderPublication publication;

    @BeforeAll
    static void loadProduction() throws Exception {
        Milestone7RegisteredTestContent.ensureRegistered();
        registry = DyeResolverFixtures.productionSnapshot();
        renderData = BannerRenderDataSnapshot.fromRegistry(registry);
        publication = new ClientBannerRenderPublication(renderData, 7, true);
    }

    @Test
    void productionProjectionContainsAllDisplayMetadata() {
        assertAll(
                () -> assertEquals(ProductionBannerCatalogue.TARGET_COUNT, renderData.banners().size()),
                () -> assertEquals(4, renderData.materials().size()),
                () -> assertEquals(2, renderData.mounts().size()),
                () -> assertTrue(renderData.materials().values().stream()
                        .allMatch(material -> material.displaySrgbByColour().containsKey(material.naturalColourId()))));
    }

    @Test
    void allDefinitionsAndSixArtworkGeometryFamiliesExtractWithoutFallback() {
        var geometries = new java.util.LinkedHashSet<>();
        renderData.banners().values().forEach(definition -> {
            geometries.add(definition.assets().geometry());
            BannerItemRenderState state = extract(stack(definition.id(), cotton(), natural(cotton()), brass()));
            assertFalse(state.fallback(), definition.id().toString());
            assertEquals(definition.id(), state.definitionId().orElseThrow());
        });
        assertTrue(BannerAssetAvailability.GEOMETRY_MODELS.containsAll(geometries));
        assertEquals(10, geometries.size());
        assertTrue(BannerAssetAvailability.GEOMETRY_MODELS.contains(
                net.minecraft.resources.ResourceLocation.parse("britannia_mod:banner/mount/wall_parallel")));
        assertTrue(BannerAssetAvailability.GEOMETRY_MODELS.contains(
                net.minecraft.resources.ResourceLocation.parse("britannia_mod:banner/mount/wall_perpendicular")));
    }

    @Test
    void naturalAndDyedCottonUseCanonicalPaletteRgb() {
        var material = renderData.materials().get(cotton());
        ResolvedColourId dyed = material.displaySrgbByColour().keySet().stream()
                .filter(id -> !id.equals(material.naturalColourId())).findFirst().orElseThrow();
        BannerItemRenderState natural = extract(stack(firstBanner(), cotton(), material.naturalColourId(), brass()));
        BannerItemRenderState coloured = extract(stack(firstBanner(), cotton(), dyed, brass()));
        assertFalse(natural.recolourActive());
        assertTrue(coloured.recolourActive());
        assertEquals(material.displaySrgbByColour().get(dyed).intValue(), coloured.displaySrgb());
        assertNotEquals(natural.key(2), coloured.key(2));
    }

    @Test
    void naturalCottonWoolLinenAndSilkUseTheSameUntintedBaseContract() {
        net.minecraft.resources.ResourceLocation expectedBase = null;
        for (String path : java.util.List.of("cotton", "wool", "linen", "silk")) {
            FabricMaterialId material = FabricMaterialId.parse("britannia_mod:" + path);
            BannerItemRenderState state = extract(stack(firstBanner(), material, natural(material), brass()));
            assertFalse(state.fallback(), path);
            assertFalse(state.recolourActive(), path);
            assertEquals(material, state.materialId().orElseThrow());
            assertEquals(java.util.List.of(BannerRenderLayer.Type.BASE_TEXTURE, BannerRenderLayer.Type.MOUNT),
                    BannerLayerPlan.from(state).layers().stream().map(BannerRenderLayer::type).toList(), path);
            if (expectedBase == null) {
                expectedBase = state.baseTexture().orElseThrow();
            } else {
                assertEquals(expectedBase, state.baseTexture().orElseThrow(), path);
            }
        }
    }

    @Test
    void brassAndIronSelectIndependentUntintedMountAssets() {
        BannerItemRenderState brassState = extract(stack(firstBanner(), cotton(), natural(cotton()), brass()));
        BannerItemRenderState ironState = extract(stack(firstBanner(), cotton(), natural(cotton()), iron()));
        assertNotEquals(brassState.mountGeometry(), ironState.mountGeometry());
        assertNotEquals(brassState.mountTexture(), ironState.mountTexture());
        assertNotEquals(brassState.key(1), ironState.key(1));
        assertEquals(BannerRenderLayer.NO_TINT,
                BannerLayerPlan.from(brassState).layers().getLast().tintIndex());
        assertEquals(BannerRenderLayer.NO_TINT,
                BannerLayerPlan.from(ironState).layers().getLast().tintIndex());
    }

    @Test
    void layerOrderAndTintBoundaryAreExplicit() {
        BannerItemRenderState naturalState =
                extract(stack(firstBanner(), cotton(), natural(cotton()), brass()));
        BannerLayerPlan naturalPlan = BannerLayerPlan.from(naturalState);
        assertEquals(java.util.List.of(BannerRenderLayer.Type.BASE_TEXTURE, BannerRenderLayer.Type.MOUNT),
                naturalPlan.layers().stream().map(BannerRenderLayer::type).toList());
        assertEquals(java.util.List.of(-1, -1),
                naturalPlan.layers().stream().map(BannerRenderLayer::tintIndex).toList());

        var material = renderData.materials().get(cotton());
        ResolvedColourId dyed = material.displaySrgbByColour().keySet().stream()
                .filter(id -> !id.equals(material.naturalColourId())).findFirst().orElseThrow();
        BannerItemRenderState dyedState = extract(stack(firstBanner(), cotton(), dyed, brass()));
        BannerLayerPlan dyedPlan = BannerLayerPlan.from(dyedState);
        assertEquals(java.util.List.of(
                        BannerRenderLayer.Type.BASE_TEXTURE, BannerRenderLayer.Type.DYE_MASK,
                        BannerRenderLayer.Type.MOUNT),
                dyedPlan.layers().stream().map(BannerRenderLayer::type).toList());
        assertEquals(java.util.List.of(-1, 1, -1),
                dyedPlan.layers().stream().map(BannerRenderLayer::tintIndex).toList());
        assertEquals(0xFF000000 | dyedState.displaySrgb(), dyedPlan.displayArgb());
    }

    @Test
    void identicalAppearanceHasStableKeyWhileEveryVisualInputInvalidatesIt() {
        BannerItemRenderState base = extract(stack(firstBanner(), cotton(), natural(cotton()), brass()));
        BannerRenderKey key = base.key(3);
        assertEquals(key, extract(stack(firstBanner(), cotton(), natural(cotton()), brass())).key(3));
        assertEquals(key.hashCode(), extract(stack(firstBanner(), cotton(), natural(cotton()), brass())).key(3).hashCode());
        assertNotEquals(key, base.key(4));
        assertNotEquals(key, extract(stack(secondBanner(), cotton(), natural(cotton()), brass())).key(3));
        FabricMaterialId wool = FabricMaterialId.parse("britannia_mod:wool");
        assertNotEquals(key, extract(stack(firstBanner(), wool, natural(wool), brass())).key(3));
        assertNotEquals(key, extract(stack(firstBanner(), cotton(), natural(cotton()), iron())).key(3));
    }

    @Test
    void pigmentPresenceActivatesRecolourButPigmentIdentityAndCustomNameDoNotFragmentVisualKey() {
        ItemStack plain = stack(firstBanner(), cotton(), natural(cotton()), brass());
        BannerInstanceState original = plain.get(Milestone7RegisteredTestContent.component());
        ItemStack decorated = plain.copy();
        decorated.set(Milestone7RegisteredTestContent.component(), new BannerInstanceState(
                original.schemaVersion(), original.bannerDefinitionId(), original.materialId(),
                original.resolvedColourId(), Optional.of(PigmentId.parse("britannia_mod:woad_blue")),
                original.mountId()));
        decorated.set(DataComponents.CUSTOM_NAME, Component.literal("Named banner"));
        ItemStack otherPigment = plain.copy();
        otherPigment.set(Milestone7RegisteredTestContent.component(), new BannerInstanceState(
                original.schemaVersion(), original.bannerDefinitionId(), original.materialId(),
                original.resolvedColourId(), Optional.of(PigmentId.parse("britannia_mod:madder_red")),
                original.mountId()));
        assertFalse(extract(plain).recolourActive());
        assertTrue(extract(decorated).recolourActive());
        assertNotEquals(extract(plain).key(8), extract(decorated).key(8));
        assertEquals(extract(decorated).key(8), extract(otherPigment).key(8));
        assertEquals(original, plain.get(Milestone7RegisteredTestContent.component()));
    }

    @Test
    void derivedRuleCoversAdministrativeAndNaturalColourPigmentEdges() {
        ItemStack natural = stack(firstBanner(), cotton(), natural(cotton()), brass());
        BannerInstanceState naturalState = natural.get(Milestone7RegisteredTestContent.component());
        ItemStack naturalPigment = natural.copy();
        naturalPigment.set(Milestone7RegisteredTestContent.component(), new BannerInstanceState(
                naturalState.schemaVersion(), naturalState.bannerDefinitionId(), naturalState.materialId(),
                naturalState.resolvedColourId(), Optional.of(PigmentId.parse("britannia_mod:woad_blue")),
                naturalState.mountId()));

        ResolvedColourId nonNatural = renderData.materials().get(cotton()).displaySrgbByColour().keySet().stream()
                .filter(id -> !id.equals(natural(cotton()))).findFirst().orElseThrow();
        ItemStack administrative = stack(firstBanner(), cotton(), nonNatural, brass());

        assertFalse(extract(natural).recolourActive(), "admin/default natural colour without pigment uses base");
        assertTrue(extract(administrative).recolourActive(), "admin non-natural colour activates mask");
        assertTrue(extract(naturalPigment).recolourActive(), "natural-colour pigment still activates mask");
        assertEquals(java.util.List.of(
                        BannerRenderLayer.Type.BASE_TEXTURE, BannerRenderLayer.Type.DYE_MASK,
                        BannerRenderLayer.Type.MOUNT),
                BannerLayerPlan.from(extract(naturalPigment)).layers().stream()
                        .map(BannerRenderLayer::type).toList());
    }

    @Test
    void missingStateAndRegistryDataUseTypedFallbackWithoutRepair() {
        ItemStack unconfigured = new ItemStack(Milestone7RegisteredTestContent.banner());
        assertFallback(unconfigured, publication, BannerAssetAvailability.allExpected(),
                BannerRenderFailure.MISSING_COMPONENT, "banner_instance_state");
        ItemStack wrong = new ItemStack(Items.STICK);
        assertEquals(BannerRenderFailure.INVALID_ITEM, extract(wrong).failure());

        BannerInstanceState original = stack(firstBanner(), cotton(), natural(cotton()), brass())
                .get(Milestone7RegisteredTestContent.component());
        assertFallback(stack(BannerDefinitionId.parse("server:unknown"), cotton(), natural(cotton()), brass()),
                publication, BannerAssetAvailability.allExpected(), BannerRenderFailure.MISSING_DEFINITION,
                "server:unknown");
        assertFallback(stack(firstBanner(), FabricMaterialId.parse("server:unknown"), natural(cotton()), brass()),
                publication, BannerAssetAvailability.allExpected(), BannerRenderFailure.MISSING_MATERIAL,
                "server:unknown");
        assertFallback(stack(firstBanner(), cotton(), ResolvedColourId.parse("server:unknown"), brass()),
                publication, BannerAssetAvailability.allExpected(), BannerRenderFailure.MISSING_COLOUR,
                "server:unknown");
        assertFallback(stack(firstBanner(), cotton(), natural(cotton()), MountId.parse("server:unknown")),
                publication, BannerAssetAvailability.allExpected(), BannerRenderFailure.MISSING_MOUNT,
                "server:unknown");
        ItemStack valid = stack(original.bannerDefinitionId(), original.materialId(), original.resolvedColourId(), original.mountId());
        assertFallback(valid, new ClientBannerRenderPublication(BannerRenderDataSnapshot.empty(), 9, false),
                BannerAssetAvailability.allExpected(), BannerRenderFailure.REGISTRY_UNAVAILABLE, "client_render_data");
        assertEquals(original, valid.get(Milestone7RegisteredTestContent.component()));
    }

    @Test
    void everyMissingAssetKindFallsBackAndPreservesStableId() {
        ItemStack valid = stack(firstBanner(), cotton(), natural(cotton()), brass());
        var allModels = BannerAssetAvailability.EXPECTED_MODELS;
        var allTextures = BannerAssetAvailability.EXPECTED_TEXTURES;
        BannerItemRenderState good = extract(valid);
        for (var missing : java.util.List.of(
                new MissingAsset(good.geometry().orElseThrow(), true, BannerRenderFailure.MISSING_GEOMETRY),
                new MissingAsset(good.baseTexture().orElseThrow(), false, BannerRenderFailure.MISSING_BASE_TEXTURE),
                new MissingAsset(good.dyeMask().orElseThrow(), false, BannerRenderFailure.MISSING_DYE_MASK),
                new MissingAsset(good.mountGeometry().orElseThrow(), true, BannerRenderFailure.MISSING_MOUNT_GEOMETRY),
                new MissingAsset(good.mountTexture().orElseThrow(), false, BannerRenderFailure.MISSING_MOUNT_TEXTURE))) {
            var models = new java.util.LinkedHashSet<>(allModels);
            var textures = new java.util.LinkedHashSet<>(allTextures);
            (missing.model ? models : textures).remove(missing.id);
            BannerItemRenderState state = BannerRenderStateExtractor.extract(valid,
                    Milestone7RegisteredTestContent.banner(), Milestone7RegisteredTestContent.component(),
                    publication, new BannerAssetAvailability(models, textures));
            assertEquals(missing.failure, state.failure());
            assertEquals(missing.id.toString(), state.diagnosticId());
            assertTrue(BannerLayerPlan.from(state).layers().isEmpty());
        }
    }

    @Test
    void disabledDefinitionIsAbsentFromDisplayProjectionAndRendersFallback() {
        BannerDefinitionId disabledId = firstBanner();
        BannerRenderDataSnapshot disabled = BannerRenderDataSnapshot.fromRegistry(
                RegistrySnapshotTestFactory.withDisabledBanner(registry, disabledId));
        assertFalse(disabled.banners().containsKey(disabledId));
        ItemStack stack = stack(disabledId, cotton(), natural(cotton()), brass());
        assertFallback(stack, new ClientBannerRenderPublication(disabled, 11, true),
                BannerAssetAvailability.allExpected(), BannerRenderFailure.MISSING_DEFINITION,
                disabledId.toString());
    }

    private static BannerItemRenderState extract(ItemStack stack) {
        return BannerRenderStateExtractor.extract(stack, Milestone7RegisteredTestContent.banner(),
                Milestone7RegisteredTestContent.component(), publication, BannerAssetAvailability.allExpected());
    }

    private static void assertFallback(ItemStack stack, ClientBannerRenderPublication data,
            BannerAssetAvailability assets, BannerRenderFailure failure, String diagnostic) {
        BannerInstanceState before = stack.get(Milestone7RegisteredTestContent.component());
        BannerItemRenderState state = BannerRenderStateExtractor.extract(stack,
                Milestone7RegisteredTestContent.banner(), Milestone7RegisteredTestContent.component(), data, assets);
        assertEquals(failure, state.failure());
        assertEquals(diagnostic, state.diagnosticId());
        assertEquals(before, stack.get(Milestone7RegisteredTestContent.component()));
    }

    private static ItemStack stack(BannerDefinitionId banner, FabricMaterialId material,
            ResolvedColourId colour, MountId mount) {
        ItemStack stack = new ItemStack(Milestone7RegisteredTestContent.banner());
        stack.set(Milestone7RegisteredTestContent.component(),
                new BannerInstanceState(1, banner, material, colour, Optional.empty(), mount));
        return stack;
    }

    private static BannerDefinitionId firstBanner() { return renderData.banners().keySet().stream().findFirst().orElseThrow(); }
    private static BannerDefinitionId secondBanner() { return renderData.banners().keySet().stream().skip(1).findFirst().orElseThrow(); }
    private static FabricMaterialId cotton() { return FabricMaterialId.parse("britannia_mod:cotton"); }
    private static ResolvedColourId natural(FabricMaterialId material) { return renderData.materials().get(material).naturalColourId(); }
    private static MountId brass() { return MountId.parse("britannia_mod:brass"); }
    private static MountId iron() { return MountId.parse("britannia_mod:iron"); }
    private record MissingAsset(net.minecraft.resources.ResourceLocation id, boolean model, BannerRenderFailure failure) {}
}
