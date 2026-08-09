package com.seggellion.britannia_mod.bannerdyeing;

import static com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone8TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshotTestFactory;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone6RegisteredTestContent;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone7RegisteredTestContent;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import com.seggellion.britannia_mod.dye.palette.MaterialPaletteEntry;
import com.seggellion.britannia_mod.dye.preview.DyePreviewFailure;
import com.seggellion.britannia_mod.dye.preview.DyePreviewPlan;
import com.seggellion.britannia_mod.dye.preview.DyePreviewValidationService;
import com.seggellion.britannia_mod.dye.service.DyeResolver;
import com.seggellion.britannia_mod.dye.state.DyeTubState;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DyePreviewValidationTest {
    private static RegistrySnapshot snapshot;
    private static DyePreviewValidationService service;

    @BeforeAll
    static void setup() throws Exception {
        snapshot = snapshot();
        service = new DyePreviewValidationService(new DyeResolver());
    }

    @Test
    void validLoadedTubAndConfiguredBannerProducesDisplayOnlyPlanWithoutMutation() {
        ItemStack tub = loadedTub(MADDER, Optional.of(3));
        ItemStack banner = naturalBanner(snapshot);
        ItemStack tubBefore = tub.copy();
        ItemStack bannerBefore = banner.copy();
        DyePreviewPlan plan = plan(tub, banner, snapshot, true);
        assertTrue(plan.successful());
        assertEquals(MADDER, plan.pigmentId().orElseThrow());
        assertEquals("banner.britannia_mod.ward_of_serpents", plan.displayData().orElseThrow().bannerNameKey());
        assertNotEquals(plan.displayData().orElseThrow().currentSrgb(), plan.displayData().orElseThrow().newSrgb());
        assertTrue(ItemStack.matches(tubBefore, tub));
        assertTrue(ItemStack.matches(bannerBefore, banner));
        assertEquals(3, plan.tubState().orElseThrow().remainingUses().orElseThrow());
    }

    @Test
    void emptyDepletedRawWrongItemAndUnavailableRegistryAreTyped() {
        ItemStack empty = new ItemStack(Milestone6RegisteredTestContent.tub());
        assertFailure(DyePreviewFailure.TUB_EMPTY, empty, naturalBanner(snapshot), snapshot, true);
        assertFailure(DyePreviewFailure.TUB_DEPLETED, loadedTub(MADDER, Optional.of(0)), naturalBanner(snapshot), snapshot, true);
        assertFailure(DyePreviewFailure.BANNER_UNCONFIGURED, loadedTub(MADDER, Optional.empty()),
                new ItemStack(Milestone7RegisteredTestContent.banner()), snapshot, true);
        assertFailure(DyePreviewFailure.INVALID_HAND_CONTRACT, loadedTub(MADDER, Optional.empty()),
                new ItemStack(Items.STICK), snapshot, true);
        assertFailure(DyePreviewFailure.REGISTRY_UNAVAILABLE, loadedTub(MADDER, Optional.empty()),
                naturalBanner(snapshot), RegistrySnapshot.empty(), false);
    }

    @Test
    void missingAndDisabledRegistryReferencesAreDistinguished() {
        ItemStack tub = loadedTub(MADDER, Optional.empty());
        ItemStack banner = naturalBanner(snapshot);
        assertFailure(DyePreviewFailure.PIGMENT_MISSING, tub, banner,
                RegistrySnapshotTestFactory.withoutPigment(snapshot, MADDER), true);
        assertFailure(DyePreviewFailure.PIGMENT_DISABLED, tub, banner,
                RegistrySnapshotTestFactory.withDisabledPigment(snapshot, MADDER), true);
        assertFailure(DyePreviewFailure.DEFINITION_MISSING, tub, banner,
                RegistrySnapshotTestFactory.withoutBanner(snapshot, WARD), true);
        assertFailure(DyePreviewFailure.DEFINITION_DISABLED, tub, banner,
                RegistrySnapshotTestFactory.withDisabledBanner(snapshot, WARD), true);
        assertFailure(DyePreviewFailure.MATERIAL_MISSING, tub, banner,
                RegistrySnapshotTestFactory.withoutMaterial(snapshot, COTTON), true);
        assertFailure(DyePreviewFailure.MATERIAL_DISABLED, tub, banner,
                RegistrySnapshotTestFactory.withDisabledMaterial(snapshot, COTTON), true);
        assertFailure(DyePreviewFailure.PALETTE_MISSING, tub, banner,
                RegistrySnapshotTestFactory.withoutPalette(snapshot,
                        snapshot.fabricMaterials().require(COTTON).paletteId()), true);
        assertFailure(DyePreviewFailure.MOUNT_MISSING, tub, banner,
                RegistrySnapshotTestFactory.withoutMount(snapshot,
                        bannerItem().stateAccess().read(banner).orElseThrow().mountId()), true);
        assertFailure(DyePreviewFailure.MOUNT_DISABLED, tub, banner,
                RegistrySnapshotTestFactory.withDisabledMount(snapshot,
                        bannerItem().stateAccess().read(banner).orElseThrow().mountId()), true);
    }

    @Test
    void missingCurrentColourIsNotAutoRepairedForPreview() {
        ItemStack banner = naturalBanner(snapshot);
        BannerInstanceState state = bannerItem().stateAccess().read(banner).orElseThrow();
        MaterialPalette original = snapshot.materialPalettes().require(
                snapshot.fabricMaterials().require(COTTON).paletteId());
        MaterialPalette missing = new MaterialPalette(original.schemaVersion(), original.id(), original.materialId(),
                original.entries().get(1).id(), original.entries().stream()
                        .filter(entry -> !entry.id().equals(state.resolvedColourId())).toList(), Map.of());
        RegistrySnapshot changed = RegistrySnapshotTestFactory.replacePalette(snapshot, missing);
        ItemStack before = banner.copy();
        assertFailure(DyePreviewFailure.BANNER_INVALID, loadedTub(MADDER, Optional.empty()), banner, changed, true);
        assertTrue(ItemStack.matches(before, banner));
    }

    @Test
    void noCompatibleColourIsTyped() {
        MaterialPalette original = snapshot.materialPalettes().require(
                snapshot.fabricMaterials().require(COTTON).paletteId());
        List<MaterialPaletteEntry> blocked = original.entries().stream().map(entry -> new MaterialPaletteEntry(
                entry.id(), entry.displayNameKey(), entry.displaySrgb(), entry.matchOklab(), entry.priority(),
                entry.tags(), List.of("never_matches"), List.of())).toList();
        MaterialPalette replacement = new MaterialPalette(original.schemaVersion(), original.id(), original.materialId(),
                original.naturalColourId(), blocked, Map.of());
        assertFailure(DyePreviewFailure.NO_COMPATIBLE_COLOUR, loadedTub(MADDER, Optional.empty()), naturalBanner(snapshot),
                RegistrySnapshotTestFactory.replacePalette(snapshot, replacement), true);
    }

    @Test
    void resolverOwnershipFailureIsTypedWithoutMutation() {
        MaterialPalette original = snapshot.materialPalettes().require(
                snapshot.fabricMaterials().require(COTTON).paletteId());
        MaterialPalette wrongOwner = new MaterialPalette(original.schemaVersion(), original.id(), SILK,
                original.naturalColourId(), original.entries(), original.pigmentOverrides());
        assertFailure(DyePreviewFailure.RESOLVER_FAILURE, loadedTub(MADDER, Optional.empty()), naturalBanner(snapshot),
                RegistrySnapshotTestFactory.replacePalette(snapshot, wrongOwner), true);
    }

    @Test
    void unrelatedComponentsAreIncludedInTheNonMutatingPreviewBoundary() {
        ItemStack banner = naturalBanner(snapshot);
        banner.set(DataComponents.CUSTOM_NAME, Component.literal("My Standard"));
        ItemStack before = banner.copy();
        assertTrue(plan(loadedTub(MADDER, Optional.empty()), banner, snapshot, true).successful());
        assertTrue(ItemStack.matches(before, banner));
    }

    private static DyePreviewPlan plan(ItemStack tub, ItemStack banner, RegistrySnapshot current, boolean available) {
        return service.plan(tub, Milestone6RegisteredTestContent.tub(), banner, bannerItem(), current, available,
                Milestone6RegisteredTestContent.component());
    }

    private static void assertFailure(
            DyePreviewFailure failure, ItemStack tub, ItemStack banner, RegistrySnapshot current, boolean available) {
        ItemStack beforeTub = tub.copy();
        ItemStack beforeBanner = banner.copy();
        assertEquals(failure, plan(tub, banner, current, available).failure());
        assertTrue(ItemStack.matches(beforeTub, tub));
        assertTrue(ItemStack.matches(beforeBanner, banner));
    }
}
