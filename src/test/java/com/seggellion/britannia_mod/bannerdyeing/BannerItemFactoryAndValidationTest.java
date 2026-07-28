package com.seggellion.britannia_mod.bannerdyeing;

import com.seggellion.britannia_mod.bannerdyeing.registry.ProductionBannerCatalogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.item.BannerColourUpdatePlan;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.item.BannerItemFactory;
import com.seggellion.britannia_mod.banner.item.BannerItemFactoryResult;
import com.seggellion.britannia_mod.banner.item.BannerItemStateAccess;
import com.seggellion.britannia_mod.banner.item.BannerRepairPlan;
import com.seggellion.britannia_mod.banner.item.BannerRepairReason;
import com.seggellion.britannia_mod.banner.item.BannerStateIssueKind;
import com.seggellion.britannia_mod.banner.item.BannerStateStatus;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshotTestFactory;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone7RegisteredTestContent;
import com.seggellion.britannia_mod.dye.api.DyeableItem;
import com.seggellion.britannia_mod.dye.api.DyeableStateFailure;
import com.seggellion.britannia_mod.dye.api.DyeableStateRead;
import com.seggellion.britannia_mod.dye.api.DyeableStateUpdate;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import com.seggellion.britannia_mod.dye.service.DyeResolver;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BannerItemFactoryAndValidationTest {
    private static final BannerDefinitionId WARD = BannerDefinitionId.parse("britannia_mod:ward_of_serpents");
    private static final FabricMaterialId COTTON = FabricMaterialId.parse("britannia_mod:cotton");
    private static final PigmentId MADDER = PigmentId.parse("britannia_mod:madder_red");
    private static final MountId BRASS = MountId.parse("britannia_mod:brass");
    private static final MountId IRON = MountId.parse("britannia_mod:iron");
    private static RegistrySnapshot production;
    private static BannerItem item;
    private static BannerItemStateAccess access;
    private static BannerItemFactory factory;

    @BeforeAll
    static void setup() throws Exception {
        Milestone7RegisteredTestContent.ensureRegistered();
        production = DyeResolverFixtures.productionSnapshot();
        item = Milestone7RegisteredTestContent.banner();
        access = item.stateAccess();
        factory = new BannerItemFactory(item, access);
    }

    @Test
    void rawSharedItemIsUnconfiguredAndGenericReadIsTypedFailure() {
        ItemStack raw = new ItemStack(item);
        assertTrue(access.read(raw).isEmpty());
        assertEquals(BannerStateStatus.UNCONFIGURED, access.validate(raw, production, true).status());
        DyeableStateRead read = item.readDyeableState(raw, production, true);
        assertEquals(DyeableStateFailure.UNCONFIGURED, read.failure());
        assertFalse(read.pigmentApplicationAllowed());
    }

    @Test
    void registryUnavailablePreservesStoredState() {
        ItemStack stack = natural(WARD);
        BannerInstanceState before = access.read(stack).orElseThrow();
        assertEquals(BannerStateStatus.REGISTRY_UNAVAILABLE,
                access.validate(stack, RegistrySnapshot.empty(), false).status());
        assertEquals(before, access.read(stack).orElseThrow());
    }

    @Test
    void naturalAdminFactoryUsesCottonNaturalDefaultMountAndNoPigment() {
        BannerInstanceState state = access.read(natural(WARD)).orElseThrow();
        assertEquals(COTTON, state.materialId());
        assertEquals(production.fabricMaterials().require(COTTON).naturalColourId(), state.resolvedColourId());
        assertEquals(production.banners().require(WARD).defaultMount(), state.mountId());
        assertTrue(state.sourcePigmentId().isEmpty());
        assertEquals(BannerStateStatus.VALID, access.validate(natural(WARD), production, true).status());
    }

    @ParameterizedTest
    @MethodSource("materials")
    void craftedFactoriesUseEachMaterialsAuthoredNaturalColour(String materialPath) {
        FabricMaterialId materialId = FabricMaterialId.parse("britannia_mod:" + materialPath);
        BannerItemFactoryResult result = factory.craftedMaterialBanner(
                WARD, materialId, Optional.empty(), production, true);
        assertTrue(result.successful());
        BannerInstanceState state = access.read(result.stack().orElseThrow()).orElseThrow();
        assertEquals(materialId, state.materialId());
        assertEquals(production.fabricMaterials().require(materialId).naturalColourId(), state.resolvedColourId());
        assertTrue(state.sourcePigmentId().isEmpty());
    }

    @Test
    void explicitBrassAndIronMountsAreAccepted() {
        assertEquals(BRASS, crafted(COTTON, BRASS).mountId());
        assertEquals(IRON, crafted(COTTON, IRON).mountId());
    }

    @Test
    void fullySpecifiedDyedBannerValidatesAllReferences() {
        BannerItemFactoryResult result = factory.fullySpecifiedBanner(
                WARD, FabricMaterialId.parse("britannia_mod:silk"),
                ResolvedColourId.parse("britannia_mod:silk_ruby"), Optional.of(MADDER), BRASS,
                production, true);
        assertTrue(result.successful());
        assertEquals(BannerStateStatus.VALID, access.validate(result.stack().orElseThrow(), production, true).status());
    }

    @Test
    void fullySpecifiedInvalidColourAndPigmentAreRejected() {
        BannerItemFactoryResult badColour = factory.fullySpecifiedBanner(WARD, COTTON,
                ResolvedColourId.parse("britannia_mod:not_a_cotton_colour"), Optional.empty(), BRASS,
                production, true);
        assertEquals(BannerStateIssueKind.RESOLVED_COLOUR_MISSING, badColour.failure().orElseThrow().kind());
        BannerItemFactoryResult badPigment = factory.fullySpecifiedBanner(WARD, COTTON,
                ResolvedColourId.parse("britannia_mod:cotton_red"),
                Optional.of(PigmentId.parse("britannia_mod:removed")), BRASS, production, true);
        assertEquals(BannerStateIssueKind.SOURCE_PIGMENT_MISSING, badPigment.failure().orElseThrow().kind());
    }

    @Test
    void missingDefinitionMaterialPaletteAndUnsupportedMountHaveTypedFailures() {
        assertEquals(BannerStateIssueKind.DEFINITION_MISSING,
                factory.naturalCottonAdminBanner(BannerDefinitionId.parse("britannia_mod:removed"), production, true)
                        .failure().orElseThrow().kind());
        assertEquals(BannerStateIssueKind.MATERIAL_MISSING,
                factory.craftedMaterialBanner(WARD, FabricMaterialId.parse("britannia_mod:removed"),
                        Optional.empty(), production, true).failure().orElseThrow().kind());
        RegistrySnapshot noPalette = RegistrySnapshotTestFactory.withoutPalette(
                production, production.fabricMaterials().require(COTTON).paletteId());
        assertEquals(BannerStateIssueKind.PALETTE_MISSING,
                factory.craftedMaterialBanner(WARD, COTTON, Optional.empty(), noPalette, true)
                        .failure().orElseThrow().kind());

        BannerDefinition definition = production.banners().require(WARD);
        BannerDefinition brassOnly = new BannerDefinition(
                definition.schemaVersion(), definition.id(), definition.displayNameKey(), definition.contentStatus(),
                definition.sourceReference(), definition.catalogueGroup(), definition.dimensions(),
                definition.supportedOrientations(), List.of(BRASS), BRASS, definition.defaultMaterial(),
                definition.assets(), definition.placementProfile());
        RegistrySnapshot restricted = RegistrySnapshotTestFactory.replaceBanner(production, brassOnly);
        assertEquals(BannerStateIssueKind.UNSUPPORTED_MOUNT,
                factory.craftedMaterialBanner(WARD, COTTON, Optional.of(IRON), restricted, true)
                        .failure().orElseThrow().kind());
    }

    @Test
    void allThirtyThreeDefinitionsUseSameSharedItemAndValidate() {
        assertEquals(ProductionBannerCatalogue.TARGET_COUNT, production.banners().activeCount());
        for (BannerDefinition definition : production.banners().activeDefinitions()) {
            BannerItemFactoryResult result = factory.naturalCottonAdminBanner(definition.id(), production, true);
            assertTrue(result.successful(), definition.id().toString());
            ItemStack stack = result.stack().orElseThrow();
            assertEquals(item, stack.getItem());
            assertEquals(definition.id(), access.read(stack).orElseThrow().bannerDefinitionId());
            assertEquals(BannerStateStatus.VALID, access.validate(stack, production, true).status());
        }
    }

    @Test
    void colourOnlyPlanDoesNotMutateAndApplyPreservesAllNonColourStateAndComponents() {
        ItemStack stack = natural(WARD);
        BannerInstanceState before = access.read(stack).orElseThrow();
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Player's Standard"));
        CompoundTag tag = new CompoundTag();
        tag.putString("unrelated", "preserved");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        BannerColourUpdatePlan plan = access.planColourUpdate(stack,
                ResolvedColourId.parse("britannia_mod:cotton_red"), Optional.of(MADDER), production, true);
        assertTrue(plan.successful());
        assertEquals(before, access.read(stack).orElseThrow());
        assertTrue(access.applyColourUpdate(stack, plan));
        BannerInstanceState after = access.read(stack).orElseThrow();
        assertEquals(before.schemaVersion(), after.schemaVersion());
        assertEquals(before.bannerDefinitionId(), after.bannerDefinitionId());
        assertEquals(before.materialId(), after.materialId());
        assertEquals(before.mountId(), after.mountId());
        assertEquals(ResolvedColourId.parse("britannia_mod:cotton_red"), after.resolvedColourId());
        assertEquals(Optional.of(MADDER), after.sourcePigmentId());
        assertEquals("Player's Standard", stack.getHoverName().getString());
        assertEquals("preserved", stack.get(DataComponents.CUSTOM_DATA).copyTag().getString("unrelated"));
    }

    @Test
    void genericContractPlansAndAppliesWithoutBannerSpecificClientDependencies() {
        DyeableItem dyeable = item;
        ItemStack stack = natural(WARD);
        DyeableStateUpdate update = dyeable.planColourUpdate(stack,
                ResolvedColourId.parse("britannia_mod:cotton_red"), Optional.of(MADDER), production, true);
        assertTrue(update.successful());
        assertTrue(dyeable.applyColourUpdate(stack, update));
        DyeableStateRead read = dyeable.readDyeableState(stack, production, true);
        assertEquals(COTTON, read.materialId().orElseThrow());
        assertEquals(ResolvedColourId.parse("britannia_mod:cotton_red"), read.resolvedColourId().orElseThrow());
        assertEquals(Optional.of(MADDER), read.sourcePigmentId());
    }

    @Test
    void staleColourAndRepairPlansCannotApply() {
        ItemStack stack = natural(WARD);
        BannerColourUpdatePlan first = access.planColourUpdate(stack,
                ResolvedColourId.parse("britannia_mod:cotton_red"), Optional.of(MADDER), production, true);
        BannerColourUpdatePlan second = access.planColourUpdate(stack,
                ResolvedColourId.parse("britannia_mod:cotton_blue"), Optional.empty(), production, true);
        assertTrue(access.applyColourUpdate(stack, first));
        assertFalse(access.applyColourUpdate(stack, second));
    }

    @Test
    void missingColourWithValidSourceReresolvesWithoutMutationUntilApply() {
        ItemStack stack = dyedCotton();
        BannerInstanceState before = access.read(stack).orElseThrow();
        RegistrySnapshot missingColour = withoutCottonRed(production);
        assertEquals(BannerStateStatus.REPAIRABLE, access.validate(stack, missingColour, true).status());
        BannerRepairPlan plan = access.planRepair(stack, missingColour, true);
        assertTrue(plan.successful());
        assertEquals(BannerRepairReason.SOURCE_PIGMENT_RERESOLVED, plan.reason().orElseThrow());
        assertEquals(before, access.read(stack).orElseThrow());
        assertTrue(access.applyRepair(stack, plan));
        assertEquals(before.bannerDefinitionId(), access.read(stack).orElseThrow().bannerDefinitionId());
        assertEquals(before.materialId(), access.read(stack).orElseThrow().materialId());
        assertEquals(before.mountId(), access.read(stack).orElseThrow().mountId());
        assertEquals(Optional.of(MADDER), access.read(stack).orElseThrow().sourcePigmentId());
        assertNotEquals(before.resolvedColourId(), access.read(stack).orElseThrow().resolvedColourId());
    }

    @Test
    void missingColourWithoutSourceUsesNaturalFallback() {
        ItemStack stack = stateStack(new BannerInstanceState(1, WARD, COTTON,
                ResolvedColourId.parse("britannia_mod:cotton_red"), Optional.empty(), BRASS));
        BannerRepairPlan plan = access.planRepair(stack, withoutCottonRed(production), true);
        assertEquals(BannerRepairReason.MATERIAL_NATURAL_FALLBACK, plan.reason().orElseThrow());
        assertFalse(plan.historicalPigmentRetained());
        assertEquals(ResolvedColourId.parse("britannia_mod:cotton_natural"),
                plan.replacementState().orElseThrow().resolvedColourId());
    }

    @Test
    void unusableHistoricalPigmentUsesNaturalFallbackAndRetainsProvenance() {
        ItemStack stack = dyedCotton();
        RegistrySnapshot unavailable = RegistrySnapshotTestFactory.withoutPigment(withoutCottonRed(production), MADDER);
        BannerRepairPlan plan = access.planRepair(stack, unavailable, true);
        assertEquals(BannerRepairReason.MATERIAL_NATURAL_FALLBACK, plan.reason().orElseThrow());
        assertTrue(plan.historicalPigmentRetained());
        assertEquals(Optional.of(MADDER), plan.replacementState().orElseThrow().sourcePigmentId());
    }

    @Test
    void missingDefinitionMaterialAndMountAreNeverAutoRepaired() {
        ItemStack stack = natural(WARD);
        assertFalse(access.planRepair(stack, RegistrySnapshotTestFactory.withoutBanner(production, WARD), true)
                .successful());
        assertFalse(access.planRepair(stack, RegistrySnapshotTestFactory.withoutMaterial(production, COTTON), true)
                .successful());
        assertFalse(access.planRepair(stack, RegistrySnapshotTestFactory.withoutMount(production, BRASS), true)
                .successful());
        assertEquals(WARD, access.read(stack).orElseThrow().bannerDefinitionId());
    }

    @ParameterizedTest
    @MethodSource("validationCases")
    void registryRemovalAndDisableCasesAreTyped(
            RegistrySnapshot snapshot, BannerStateIssueKind expected) {
        ItemStack stack = expected == BannerStateIssueKind.SOURCE_PIGMENT_MISSING
                || expected == BannerStateIssueKind.SOURCE_PIGMENT_DISABLED
                || expected == BannerStateIssueKind.RESOLVED_COLOUR_MISSING ? dyedCotton() : natural(WARD);
        assertTrue(access.validate(stack, snapshot, true).issues().stream().anyMatch(i -> i.kind() == expected));
    }

    static Stream<Arguments> validationCases() {
        return Stream.of(
                Arguments.of(RegistrySnapshotTestFactory.withoutBanner(production, WARD),
                        BannerStateIssueKind.DEFINITION_MISSING),
                Arguments.of(RegistrySnapshotTestFactory.withDisabledBanner(production, WARD),
                        BannerStateIssueKind.DEFINITION_DISABLED),
                Arguments.of(RegistrySnapshotTestFactory.withoutMaterial(production, COTTON),
                        BannerStateIssueKind.MATERIAL_MISSING),
                Arguments.of(RegistrySnapshotTestFactory.withDisabledMaterial(production, COTTON),
                        BannerStateIssueKind.MATERIAL_DISABLED),
                Arguments.of(RegistrySnapshotTestFactory.withoutPalette(production,
                                production.fabricMaterials().require(COTTON).paletteId()),
                        BannerStateIssueKind.PALETTE_MISSING),
                Arguments.of(withoutCottonRed(production), BannerStateIssueKind.RESOLVED_COLOUR_MISSING),
                Arguments.of(RegistrySnapshotTestFactory.withoutPigment(production, MADDER),
                        BannerStateIssueKind.SOURCE_PIGMENT_MISSING),
                Arguments.of(RegistrySnapshotTestFactory.withDisabledPigment(production, MADDER),
                        BannerStateIssueKind.SOURCE_PIGMENT_DISABLED),
                Arguments.of(RegistrySnapshotTestFactory.withoutMount(production, BRASS),
                        BannerStateIssueKind.MOUNT_MISSING),
                Arguments.of(RegistrySnapshotTestFactory.withDisabledMount(production, BRASS),
                        BannerStateIssueKind.MOUNT_DISABLED));
    }

    private static Stream<String> materials() {
        return Stream.of("cotton", "wool", "linen", "silk");
    }

    private static ItemStack natural(BannerDefinitionId id) {
        return factory.naturalCottonAdminBanner(id, production, true).stack().orElseThrow();
    }

    private static BannerInstanceState crafted(FabricMaterialId material, MountId mount) {
        return access.read(factory.craftedMaterialBanner(WARD, material, Optional.of(mount), production, true)
                .stack().orElseThrow()).orElseThrow();
    }

    private static ItemStack dyedCotton() {
        return factory.fullySpecifiedBanner(WARD, COTTON,
                ResolvedColourId.parse("britannia_mod:cotton_red"), Optional.of(MADDER), BRASS,
                production, true).stack().orElseThrow();
    }

    private static ItemStack stateStack(BannerInstanceState state) {
        ItemStack stack = new ItemStack(item);
        stack.set(Milestone7RegisteredTestContent.component(), state);
        return stack;
    }

    private static RegistrySnapshot withoutCottonRed(RegistrySnapshot source) {
        MaterialPalette palette = source.materialPalettes().require(
                source.fabricMaterials().require(COTTON).paletteId());
        MaterialPalette replacement = new MaterialPalette(
                palette.schemaVersion(), palette.id(), palette.materialId(), palette.naturalColourId(),
                palette.entries().stream()
                        .filter(entry -> !entry.id().equals(ResolvedColourId.parse("britannia_mod:cotton_red")))
                        .toList(),
                Map.of());
        return RegistrySnapshotTestFactory.replacePalette(source, replacement);
    }
}
