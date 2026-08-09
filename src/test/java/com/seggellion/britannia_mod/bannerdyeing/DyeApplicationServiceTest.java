package com.seggellion.britannia_mod.bannerdyeing;

import static com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone8TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.item.BannerItemFactory;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshotTestFactory;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone6RegisteredTestContent;
import com.seggellion.britannia_mod.dye.api.DyeableItem;
import com.seggellion.britannia_mod.dye.api.DyeableStateRead;
import com.seggellion.britannia_mod.dye.api.DyeableStateUpdate;
import com.seggellion.britannia_mod.dye.api.DyeableStateFailure;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import com.seggellion.britannia_mod.dye.item.DyeTubStateAccess;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import com.seggellion.britannia_mod.dye.preview.DyeApplicationResultCode;
import com.seggellion.britannia_mod.dye.preview.DyeApplicationService;
import com.seggellion.britannia_mod.dye.preview.DyePreviewPlan;
import com.seggellion.britannia_mod.dye.preview.DyePreviewSession;
import com.seggellion.britannia_mod.dye.preview.DyePreviewValidationService;
import com.seggellion.britannia_mod.dye.service.DyeResolver;
import com.seggellion.britannia_mod.dye.service.DyeResult;
import com.seggellion.britannia_mod.dye.service.MatchType;
import com.seggellion.britannia_mod.dye.state.DyeTubState;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DyeApplicationServiceTest {
    private static RegistrySnapshot snapshot;
    private static DyeApplicationService service;

    @BeforeAll
    static void setup() throws Exception {
        snapshot = snapshot();
        service = new DyeApplicationService(new DyeResolver());
    }

    @Test
    void successfulExplicitApplicationPreservesIdentityCustomComponentsAndUnlimitedTub() {
        ItemStack tub = loadedTub(MADDER, Optional.empty());
        ItemStack banner = naturalBanner(snapshot);
        BannerInstanceState before = bannerItem().stateAccess().read(banner).orElseThrow();
        banner.set(DataComponents.CUSTOM_NAME, Component.literal("Player Standard"));
        CompoundTag custom = new CompoundTag();
        custom.putString("owner_note", "preserved");
        banner.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        ItemStack tubBefore = tub.copy();
        DyePreviewSession session = session(tub, banner, snapshot);

        assertEquals(DyeApplicationResultCode.SUCCESS, apply(session, tub, banner, bannerItem(), snapshot));
        BannerInstanceState after = bannerItem().stateAccess().read(banner).orElseThrow();
        assertEquals(before.bannerDefinitionId(), after.bannerDefinitionId());
        assertEquals(before.materialId(), after.materialId());
        assertEquals(before.mountId(), after.mountId());
        assertEquals(Optional.of(MADDER), after.sourcePigmentId());
        assertNotEquals(before.resolvedColourId(), after.resolvedColourId());
        assertEquals("Player Standard", banner.getHoverName().getString());
        assertEquals("preserved", banner.get(DataComponents.CUSTOM_DATA).copyTag().getString("owner_note"));
        assertTrue(ItemStack.matches(tubBefore, tub));
    }

    @Test
    void finiteUseDecrementsOnceAndOneBecomesDiagnosticallyLoadedZero() {
        ItemStack tub = loadedTub(MADDER, Optional.of(1));
        ItemStack banner = naturalBanner(snapshot);
        DyePreviewSession session = session(tub, banner, snapshot);
        assertEquals(DyeApplicationResultCode.SUCCESS, apply(session, tub, banner, bannerItem(), snapshot));
        DyeTubState state = DyeTubStateAccess.read(tub, Milestone6RegisteredTestContent.component());
        assertEquals(Optional.of(0), state.remainingUses());
        assertEquals(Optional.of(MADDER), state.pigmentId());
    }

    @Test
    void exactNoOpConsumesNothingAndSameColourDifferentPigmentUpdatesProvenance() {
        ItemStack noOpTub = loadedTub(MADDER, Optional.of(2));
        ItemStack already = dyed(snapshot, MADDER, ResolvedColourId.parse("britannia_mod:cotton_red"));
        DyePreviewSession noOp = session(noOpTub, already, snapshot);
        ItemStack before = noOpTub.copy();
        assertEquals(DyeApplicationResultCode.ALREADY_DYED,
                apply(noOp, noOpTub, already, bannerItem(), snapshot));
        assertTrue(ItemStack.matches(before, noOpTub));

        MaterialPalette original = snapshot.materialPalettes().require(
                snapshot.fabricMaterials().require(COTTON).paletteId());
        LinkedHashMap<PigmentId, ResolvedColourId> overrides = new LinkedHashMap<>(original.pigmentOverrides());
        overrides.put(WOAD, ResolvedColourId.parse("britannia_mod:cotton_red"));
        MaterialPalette sharedColour = new MaterialPalette(original.schemaVersion(), original.id(), original.materialId(),
                original.naturalColourId(), original.entries(), overrides);
        RegistrySnapshot changed = RegistrySnapshotTestFactory.replacePalette(snapshot, sharedColour);
        ItemStack provenanceTub = loadedTub(WOAD, Optional.of(2));
        ItemStack provenanceBanner = dyed(changed, MADDER, ResolvedColourId.parse("britannia_mod:cotton_red"));
        DyePreviewSession provenance = session(provenanceTub, provenanceBanner, changed);
        assertEquals(DyeApplicationResultCode.SUCCESS,
                apply(provenance, provenanceTub, provenanceBanner, bannerItem(), changed));
        assertEquals(Optional.of(WOAD), bannerItem().stateAccess().read(provenanceBanner).orElseThrow().sourcePigmentId());
        assertEquals(Optional.of(1), DyeTubStateAccess.read(
                provenanceTub, Milestone6RegisteredTestContent.component()).remainingUses());
    }

    @Test
    void mainOffCountsItemsComponentsCustomDataAndHandSwapAreRejectedAtomically() {
        assertStale(DyeApplicationResultCode.MAIN_HAND_CHANGED, (tub, banner) -> tub.setCount(2));
        assertStale(DyeApplicationResultCode.MAIN_HAND_CHANGED, (tub, banner) ->
                tub.set(DataComponents.CUSTOM_NAME, Component.literal("Changed tub")));
        assertStale(DyeApplicationResultCode.TUB_STATE_CHANGED, (tub, banner) ->
                DyeTubStateAccess.write(tub, Milestone6RegisteredTestContent.component(),
                        new DyeTubState(1, Optional.of(WOAD), Optional.of(2))));
        assertStale(DyeApplicationResultCode.OFF_HAND_CHANGED, (tub, banner) -> banner.setCount(2));
        assertStale(DyeApplicationResultCode.BANNER_STATE_CHANGED, (tub, banner) ->
                banner.set(DataComponents.CUSTOM_NAME, Component.literal("Changed banner")));

        ItemStack tub = loadedTub(MADDER, Optional.of(2));
        ItemStack banner = naturalBanner(snapshot);
        DyePreviewSession preview = session(tub, banner, snapshot);
        assertEquals(DyeApplicationResultCode.OFF_HAND_CHANGED,
                apply(preview, tub, new ItemStack(Items.STICK), bannerItem(), snapshot));
        assertEquals(DyeApplicationResultCode.MAIN_HAND_CHANGED,
                apply(preview, banner, tub, bannerItem(), snapshot));
        assertEquals(Optional.of(2), DyeTubStateAccess.read(tub,
                Milestone6RegisteredTestContent.component()).remainingUses());
    }

    @Test
    void depletedRegistryAndResolverChangesMutateNeitherStack() {
        assertStale(DyeApplicationResultCode.TUB_DEPLETED, (tub, banner) ->
                DyeTubStateAccess.write(tub, Milestone6RegisteredTestContent.component(),
                        new DyeTubState(1, Optional.of(MADDER), Optional.of(0))));

        ItemStack tub = loadedTub(MADDER, Optional.of(2));
        ItemStack banner = naturalBanner(snapshot);
        DyePreviewSession preview = session(tub, banner, snapshot);
        ItemStack tubBefore = tub.copy();
        ItemStack bannerBefore = banner.copy();
        RegistrySnapshot changed = RegistrySnapshotTestFactory.withoutPigment(snapshot, MADDER);
        assertEquals(DyeApplicationResultCode.REGISTRY_CHANGED,
                apply(preview, tub, banner, bannerItem(), changed));
        assertTrue(ItemStack.matches(tubBefore, tub));
        assertTrue(ItemStack.matches(bannerBefore, banner));

        DyePreviewSession forgedPreview = new DyePreviewSession(preview.playerId(), UUID.randomUUID(), 1, 30_001,
                preview.expectedMainItem(), tub, preview.expectedOffItem(), banner, preview.pigmentId(),
                preview.bannerState(), preview.tubState(),
                new DyeResult(ResolvedColourId.parse("britannia_mod:cotton_blue"), MatchType.NEAREST_COLOUR, 0.5),
                snapshot, preview.displayData());
        assertEquals(DyeApplicationResultCode.RESOLVER_RESULT_CHANGED,
                apply(forgedPreview, tub, banner, bannerItem(), snapshot));
        assertTrue(ItemStack.matches(tubBefore, tub));
        assertTrue(ItemStack.matches(bannerBefore, banner));
    }

    @Test
    void unexpectedApplyFailureRollsBannerBackAndNeverConsumesTub() {
        ItemStack tub = loadedTub(MADDER, Optional.of(2));
        ItemStack banner = naturalBanner(snapshot);
        DyePreviewSession preview = session(tub, banner, snapshot);
        ItemStack tubBefore = tub.copy();
        ItemStack bannerBefore = banner.copy();
        DyeableItem throwing = new DyeableItem() {
            @Override public DyeableStateRead readDyeableState(ItemStack stack, RegistrySnapshot s, boolean a) {
                return bannerItem().readDyeableState(stack, s, a);
            }
            @Override public DyeableStateUpdate planColourUpdate(ItemStack stack, ResolvedColourId colour,
                    Optional<PigmentId> pigment, RegistrySnapshot s, boolean a) {
                return bannerItem().planColourUpdate(stack, colour, pigment, s, a);
            }
            @Override public boolean applyColourUpdate(ItemStack stack, DyeableStateUpdate update) {
                bannerItem().applyColourUpdate(stack, update);
                throw new IllegalStateException("fixture apply failure");
            }
        };
        assertEquals(DyeApplicationResultCode.UNEXPECTED_APPLY_FAILURE,
                apply(preview, tub, banner, throwing, snapshot));
        assertTrue(ItemStack.matches(tubBefore, tub));
        assertTrue(ItemStack.matches(bannerBefore, banner));
    }

    @Test
    void invalidBannerUpdatePlanChangesNeitherStack() {
        ItemStack tub = loadedTub(MADDER, Optional.of(2));
        ItemStack banner = naturalBanner(snapshot);
        DyePreviewSession preview = session(tub, banner, snapshot);
        ItemStack tubBefore = tub.copy();
        ItemStack bannerBefore = banner.copy();
        DyeableItem rejecting = new DyeableItem() {
            @Override public DyeableStateRead readDyeableState(ItemStack stack, RegistrySnapshot s, boolean a) {
                return bannerItem().readDyeableState(stack, s, a);
            }
            @Override public DyeableStateUpdate planColourUpdate(ItemStack stack, ResolvedColourId colour,
                    Optional<PigmentId> pigment, RegistrySnapshot s, boolean a) {
                return new DyeableStateUpdate(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), DyeableStateFailure.INVALID_STATE, Optional.of("fixture"));
            }
            @Override public boolean applyColourUpdate(ItemStack stack, DyeableStateUpdate update) {
                fail("invalid plan must not be applied");
                return false;
            }
        };
        assertEquals(DyeApplicationResultCode.BANNER_UPDATE_INVALID,
                apply(preview, tub, banner, rejecting, snapshot));
        assertTrue(ItemStack.matches(tubBefore, tub));
        assertTrue(ItemStack.matches(bannerBefore, banner));
    }

    @Test
    void sequentialRedyesRequireFreshPreviewsAndUseCurrentServerResult() {
        ItemStack tub = loadedTub(MADDER, Optional.empty());
        ItemStack banner = naturalBanner(snapshot, SILK);
        DyePreviewSession first = session(tub, banner, snapshot);
        assertEquals(DyeApplicationResultCode.SUCCESS, apply(first, tub, banner, bannerItem(), snapshot));
        ResolvedColourId firstColour = bannerItem().stateAccess().read(banner).orElseThrow().resolvedColourId();
        DyeTubStateAccess.write(tub, Milestone6RegisteredTestContent.component(),
                new DyeTubState(1, Optional.of(WOAD), Optional.empty()));
        DyePreviewSession second = session(tub, banner, snapshot);
        assertEquals(MatchType.NEAREST_COLOUR, second.resolvedResult().matchType());
        assertEquals(DyeApplicationResultCode.SUCCESS, apply(second, tub, banner, bannerItem(), snapshot));
        BannerInstanceState finalState = bannerItem().stateAccess().read(banner).orElseThrow();
        assertNotEquals(firstColour, finalState.resolvedColourId());
        assertEquals(Optional.of(WOAD), finalState.sourcePigmentId());
        assertEquals(second.resolvedResult().resolvedColourId(), finalState.resolvedColourId());
    }

    private static DyePreviewSession session(ItemStack tub, ItemStack banner, RegistrySnapshot current) {
        DyePreviewPlan plan = new DyePreviewValidationService(new DyeResolver()).plan(
                tub, Milestone6RegisteredTestContent.tub(), banner, bannerItem(), current, true,
                Milestone6RegisteredTestContent.component());
        assertTrue(plan.successful(), () -> plan.failure().toString());
        return new DyePreviewSession(UUID.randomUUID(), UUID.randomUUID(), 1, 30_001,
                tub.getItem(), tub, banner.getItem(), banner, plan.pigmentId().orElseThrow(),
                plan.bannerState().orElseThrow(), plan.tubState().orElseThrow(), plan.result().orElseThrow(),
                current, plan.displayData().orElseThrow());
    }

    private static DyeApplicationResultCode apply(
            DyePreviewSession session, ItemStack tub, ItemStack banner,
            DyeableItem dyeable, RegistrySnapshot current) {
        return service.apply(session, tub, banner, Milestone6RegisteredTestContent.tub(), dyeable,
                current, true, Milestone6RegisteredTestContent.component());
    }

    private static ItemStack dyed(RegistrySnapshot current, PigmentId pigment, ResolvedColourId colour) {
        MountId mount = current.banners().require(WARD).defaultMount();
        return new BannerItemFactory(bannerItem(), bannerItem().stateAccess())
                .fullySpecifiedBanner(WARD, COTTON, colour, Optional.of(pigment), mount, current, true)
                .stack().orElseThrow();
    }

    private static void assertStale(DyeApplicationResultCode expected, Mutation mutation) {
        ItemStack tub = loadedTub(MADDER, Optional.of(2));
        ItemStack banner = naturalBanner(snapshot);
        DyePreviewSession preview = session(tub, banner, snapshot);
        mutation.apply(tub, banner);
        ItemStack changedTub = tub.copy();
        ItemStack changedBanner = banner.copy();
        assertEquals(expected, apply(preview, tub, banner, bannerItem(), snapshot));
        assertTrue(ItemStack.matches(changedTub, tub));
        assertTrue(ItemStack.matches(changedBanner, banner));
    }

    @FunctionalInterface
    private interface Mutation {
        void apply(ItemStack tub, ItemStack banner);
    }
}
