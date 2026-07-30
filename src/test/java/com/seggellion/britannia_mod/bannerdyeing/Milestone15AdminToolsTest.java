package com.seggellion.britannia_mod.bannerdyeing;

import com.seggellion.britannia_mod.bannerdyeing.registry.ProductionBannerCatalogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.item.BannerItemFactory;
import com.seggellion.britannia_mod.bannerdyeing.admin.AdminItemDelivery;
import com.seggellion.britannia_mod.bannerdyeing.admin.AdminItemRecipient;
import com.seggellion.britannia_mod.bannerdyeing.admin.BannerAdminFailure;
import com.seggellion.britannia_mod.bannerdyeing.admin.BannerAdminService;
import com.seggellion.britannia_mod.bannerdyeing.admin.BannerAdminSuggestions;
import com.seggellion.britannia_mod.bannerdyeing.admin.BannerCatalogueAdminService;
import com.seggellion.britannia_mod.bannerdyeing.admin.CatalogueValidationStatus;
import com.seggellion.britannia_mod.bannerdyeing.admin.DyeDebugFailure;
import com.seggellion.britannia_mod.bannerdyeing.admin.DyeResolutionDebugService;
import com.seggellion.britannia_mod.bannerdyeing.admin.DyeTubAdminFailure;
import com.seggellion.britannia_mod.bannerdyeing.admin.DyeTubAdminService;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshotTestFactory;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone6RegisteredTestContent;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone7RegisteredTestContent;
import com.seggellion.britannia_mod.commands.BannerDyeAdminCommands;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import com.seggellion.britannia_mod.dye.item.DyeTubStateAccess;
import com.seggellion.britannia_mod.dye.service.DyeResolver;
import com.seggellion.britannia_mod.dye.service.MatchType;
import com.seggellion.britannia_mod.dye.source.PigmentSourceFailure;
import com.seggellion.britannia_mod.dye.source.RegistryPigmentSourceService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Milestone15AdminToolsTest {
    private static final BannerDefinitionId WARD =
            BannerDefinitionId.parse("britannia_mod:ward_of_serpents");
    private static final BannerDefinitionId ROAD_GUARD =
            BannerDefinitionId.parse("britannia_mod:road_guard");
    private static final FabricMaterialId COTTON = FabricMaterialId.parse("britannia_mod:cotton");
    private static final PigmentId MADDER = PigmentId.parse("britannia_mod:madder_red");
    private static final MountId BRASS = MountId.parse("britannia_mod:brass");
    private static RegistrySnapshot production;
    private static BannerItem bannerItem;
    private static BannerAdminService bannerAdmin;
    private static RegistryPigmentSourceService pigmentSource;
    private static DyeTubAdminService tubAdmin;

    @BeforeAll
    static void setup() throws Exception {
        Milestone7RegisteredTestContent.ensureRegistered();
        production = DyeResolverFixtures.productionSnapshot();
        bannerItem = Milestone7RegisteredTestContent.banner();
        bannerAdmin = new BannerAdminService(
                new BannerItemFactory(bannerItem, bannerItem.stateAccess()), bannerItem.stateAccess());
        pigmentSource = new RegistryPigmentSourceService();
        tubAdmin = new DyeTubAdminService(Milestone6RegisteredTestContent.tub(), pigmentSource);
    }

    @Test
    void commandTreeRegistersOneAdministrativeRootAndAllRequiredBranches() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        BannerDyeAdminCommands.register(dispatcher);
        assertEquals(1, dispatcher.getRoot().getChildren().stream()
                .filter(node -> node.getName().equals("britannia")).count());
        var root = dispatcher.getRoot().getChild("britannia");
        var banner = root.getChild("banner");
        var dye = root.getChild("dye");
        assertTrue(banner.getChild("give") != null);
        assertTrue(banner.getChild("validate") != null);
        assertTrue(banner.getChild("placeholders") != null);
        assertTrue(dye.getChild("tub").getChild("give") != null);
        assertTrue(dye.getChild("resolve") != null);
        assertTrue(root.getChild("crafting") == null);
    }

    @Test
    void permissionLevelTwoAppliesAtRootToPlayersConsoleAndCommandBlocks() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        BannerDyeAdminCommands.register(dispatcher);
        var requirement = dispatcher.getRoot().getChild("britannia").getRequirement();
        assertFalse(requirement.test(source(0)));
        assertFalse(requirement.test(source(1)));
        assertTrue(requirement.test(source(2)));
        assertTrue(requirement.test(source(4)));
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> dispatcher.execute("britannia banner validate", source(0)));
    }

    @Test
    void suggestionsAreRegistryBackedContextualDeterministicAndItemBacked() {
        BannerAdminSuggestions suggestions = new BannerAdminSuggestions(pigmentSource);
        assertEquals(ProductionBannerCatalogue.TARGET_COUNT,
                suggestions.definitions(production, true).size());
        assertEquals(4, suggestions.materials(production, true).size());
        assertEquals(7, suggestions.pigments(production, true).size());
        List<String> cotton = suggestions.colours(Optional.of(COTTON), production, true);
        assertEquals(8, cotton.size());
        assertTrue(cotton.stream().allMatch(id -> id.contains("cotton_")));
        assertFalse(cotton.stream().anyMatch(id -> id.contains("silk_")));
        assertEquals(List.of("britannia_mod:brass", "britannia_mod:iron"),
                suggestions.mounts(Optional.of(WARD), production, true));
        assertEquals(suggestions.definitions(production, true).stream().sorted().toList(),
                suggestions.definitions(production, true));
        assertTrue(suggestions.definitions(production, false).isEmpty());
        assertTrue(suggestions.colours(Optional.empty(), production, true).isEmpty());
    }

    @Test
    void suggestionsReflectSnapshotReplacementAndOmitDisabledContent() {
        BannerAdminSuggestions suggestions = new BannerAdminSuggestions(pigmentSource);
        RegistrySnapshot disabled = RegistrySnapshotTestFactory.withDisabledBanner(production, WARD);
        assertEquals(ProductionBannerCatalogue.TARGET_COUNT - 1,
                suggestions.definitions(disabled, true).size());
        assertFalse(suggestions.definitions(disabled, true).contains(WARD.toString()));
        RegistrySnapshot disabledPigment = RegistrySnapshotTestFactory.withDisabledPigment(production, MADDER);
        assertEquals(6, suggestions.pigments(disabledPigment, true).size());
        assertFalse(suggestions.pigments(disabledPigment, true).contains(MADDER.toString()));
    }

    @Test
    void definitionOnlyBannerUsesCottonNaturalDefaultMountAndNoPigment() {
        var result = bannerAdmin.create(WARD, Optional.empty(), Optional.empty(), Optional.empty(), production, true);
        assertTrue(result.successful());
        var state = result.state().orElseThrow();
        assertEquals(COTTON, state.materialId());
        assertEquals(production.fabricMaterials().require(COTTON).naturalColourId(), state.resolvedColourId());
        assertEquals(production.banners().require(WARD).defaultMount(), state.mountId());
        assertTrue(state.sourcePigmentId().isEmpty());
        assertEquals(BannerDyeingConstants.CURRENT_SCHEMA_VERSION, state.schemaVersion());
        assertEquals(bannerItem, result.stack().orElseThrow().getItem());
    }

    @ParameterizedTest
    @ValueSource(strings = {"cotton", "wool", "linen", "silk"})
    void materialOnlyBannerUsesSelectedNaturalColour(String path) {
        FabricMaterialId material = FabricMaterialId.parse("britannia_mod:" + path);
        var result = bannerAdmin.create(
                WARD, Optional.of(material), Optional.empty(), Optional.empty(), production, true);
        assertTrue(result.successful());
        assertEquals(production.fabricMaterials().require(material).naturalColourId(),
                result.state().orElseThrow().resolvedColourId());
        assertTrue(result.state().orElseThrow().sourcePigmentId().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"cotton_red", "wool_oxblood", "linen_madder", "silk_ruby"})
    void explicitColourIsAcceptedOnlyWithItsMaterialAndNeverRecordsPigment(String path) {
        String materialPath = path.substring(0, path.indexOf('_'));
        FabricMaterialId material = FabricMaterialId.parse("britannia_mod:" + materialPath);
        ResolvedColourId colour = ResolvedColourId.parse("britannia_mod:" + path);
        var result = bannerAdmin.create(WARD, Optional.of(material), Optional.of(colour),
                Optional.of(BRASS), production, true);
        assertTrue(result.successful());
        assertEquals(colour, result.state().orElseThrow().resolvedColourId());
        assertTrue(result.state().orElseThrow().sourcePigmentId().isEmpty());
    }

    @Test
    void bannerFailuresAreTypedAndCreateNoPartialStack() {
        assertEquals(BannerAdminFailure.REGISTRY_UNAVAILABLE,
                bannerAdmin.create(WARD, Optional.empty(), Optional.empty(), Optional.empty(),
                        RegistrySnapshot.empty(), false).failure());
        var wrongPalette = bannerAdmin.create(WARD, Optional.of(COTTON),
                Optional.of(ResolvedColourId.parse("britannia_mod:silk_ruby")),
                Optional.empty(), production, true);
        assertEquals(BannerAdminFailure.COLOUR_NOT_IN_MATERIAL, wrongPalette.failure());
        assertTrue(wrongPalette.stack().isEmpty());
        assertEquals(BannerAdminFailure.DEFINITION_DISABLED,
                bannerAdmin.create(WARD, Optional.empty(), Optional.empty(), Optional.empty(),
                        RegistrySnapshotTestFactory.withDisabledBanner(production, WARD), true).failure());
    }

    @Test
    void explicitSupportedMountsArePreservedAndUnsupportedMountIsRejected() {
        for (String path : List.of("brass", "iron")) {
            MountId mount = MountId.parse("britannia_mod:" + path);
            assertEquals(mount, bannerAdmin.create(WARD, Optional.of(COTTON), Optional.empty(),
                    Optional.of(mount), production, true).state().orElseThrow().mountId());
        }
        assertEquals(BannerAdminFailure.MOUNT_MISSING, bannerAdmin.create(
                WARD, Optional.of(COTTON), Optional.empty(),
                Optional.of(MountId.parse("britannia_mod:missing")), production, true).failure());
        BannerDefinition definition = production.banners().require(WARD);
        BannerDefinition brassOnly = new BannerDefinition(
                definition.schemaVersion(), definition.id(), definition.displayNameKey(), definition.contentStatus(),
                definition.sourceReference(), definition.catalogueGroup(), definition.dimensions(),
                definition.supportedOrientations(), List.of(BRASS), BRASS, definition.defaultMaterial(),
                definition.assets(), definition.placementProfile());
        RegistrySnapshot restricted = RegistrySnapshotTestFactory.replaceBanner(production, brassOnly);
        assertEquals(BannerAdminFailure.UNSUPPORTED_MOUNT, bannerAdmin.create(
                WARD, Optional.of(COTTON), Optional.empty(),
                Optional.of(MountId.parse("britannia_mod:iron")), restricted, true).failure());
    }

    @Test
    void pigmentSourceListsSevenImmutableEntriesAndCreatesExactRegisteredItems() {
        var list = pigmentSource.listAvailablePigments(production, true);
        assertTrue(list.successful());
        assertEquals(7, list.entries().size());
        assertThrows(UnsupportedOperationException.class, () -> list.entries().clear());
        for (var entry : list.entries()) {
            var result = pigmentSource.createPigmentStack(entry.pigmentId(), 1, production, true);
            assertTrue(result.successful());
            assertEquals(entry.registeredItemId(),
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(
                            result.stack().orElseThrow().getItem()));
        }
    }

    @Test
    void pigmentSourceValidatesCountsMappingAvailabilityAndFreshness() {
        assertEquals(PigmentSourceFailure.INVALID_COUNT,
                pigmentSource.createPigmentStack(MADDER, 0, production, true).failure());
        assertEquals(PigmentSourceFailure.INVALID_COUNT,
                pigmentSource.createPigmentStack(MADDER, -1, production, true).failure());
        assertEquals(PigmentSourceFailure.COUNT_EXCEEDS_STACK_LIMIT,
                pigmentSource.createPigmentStack(MADDER, 65, production, true).failure());
        assertEquals(PigmentSourceFailure.REGISTRY_UNAVAILABLE,
                pigmentSource.createPigmentStack(MADDER, 1, production, false).failure());
        assertEquals(PigmentSourceFailure.ITEM_MAPPING_MISSING,
                new RegistryPigmentSourceService(id -> Optional.empty())
                        .createPigmentStack(MADDER, 1, production, true).failure());
        ItemStack first = pigmentSource.createPigmentStack(MADDER, 1, production, true).stack().orElseThrow();
        ItemStack second = pigmentSource.createPigmentStack(MADDER, 1, production, true).stack().orElseThrow();
        assertNotSame(first, second);
        assertEquals(first.getItem(), second.getItem());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "madder_red", "woad_blue", "verdigris", "weld_gold", "soot_black", "chalk_white", "ice_blue"
    })
    void loadedAdminTubUsesStablePigmentAndUnlimitedUses(String path) {
        PigmentId pigment = PigmentId.parse("britannia_mod:" + path);
        var result = tubAdmin.create(Optional.of(pigment), production, true);
        assertTrue(result.successful());
        ItemStack tub = result.stack().orElseThrow();
        assertEquals(Milestone6RegisteredTestContent.tub(), tub.getItem());
        assertEquals(1, tub.getMaxStackSize());
        assertEquals(Optional.of(pigment), DyeTubStateAccess.read(tub).pigmentId());
        assertTrue(DyeTubStateAccess.read(tub).remainingUses().isEmpty());
    }

    @Test
    void emptyTubIsCanonicalAndTubFailuresCreateNoSubstitute() {
        var empty = tubAdmin.create(Optional.empty(), production, true);
        assertEquals(com.seggellion.britannia_mod.dye.state.DyeTubState.empty(),
                DyeTubStateAccess.read(empty.stack().orElseThrow()));
        var missing = tubAdmin.create(Optional.of(PigmentId.parse("britannia_mod:missing")), production, true);
        assertEquals(DyeTubAdminFailure.PIGMENT_MISSING, missing.failure());
        assertTrue(missing.stack().isEmpty());
        assertEquals(DyeTubAdminFailure.PIGMENT_DISABLED,
                tubAdmin.create(Optional.of(MADDER),
                        RegistrySnapshotTestFactory.withDisabledPigment(production, MADDER), true).failure());
        assertEquals(DyeTubAdminFailure.REGISTRY_UNAVAILABLE,
                tubAdmin.create(Optional.empty(), RegistrySnapshot.empty(), false).failure());
    }

    @Test
    void deliveryInsertsOrDropsOnlyTheRemainderAndKeepsTargetsIndependent() {
        FakeRecipient inserted = new FakeRecipient("inserted", true, false);
        FakeRecipient dropped = new FakeRecipient("dropped", false, true);
        FakeRecipient failed = new FakeRecipient("failed", false, false);
        var result = AdminItemDelivery.deliverFresh(List.of(inserted, dropped, failed),
                () -> new ItemStack(Milestone6RegisteredTestContent.tub()));
        assertEquals(2, result.successCount());
        assertEquals(List.of("failed"), result.failedTargets());
        assertEquals(3, Stream.of(inserted, dropped, failed).map(target -> target.seen.getFirst())
                .distinct().count());
        assertEquals(0, inserted.dropCalls);
        assertEquals(1, dropped.dropCalls);
        assertEquals(1, failed.dropCalls);
    }

    @Test
    void catalogueValidationAndPlaceholderProjectionAreDataDrivenAndBounded() {
        BannerCatalogueAdminService service = new BannerCatalogueAdminService();
        var validation = service.validate(production, true, List.of());
        assertEquals(CatalogueValidationStatus.VALID, validation.status());
        assertEquals(ProductionBannerCatalogue.TARGET_COUNT, validation.activeBanners());
        assertEquals(0, validation.disabledBanners());
        assertEquals(4, validation.materials());
        assertEquals(4, validation.palettes());
        assertEquals(7, validation.pigments());
        assertEquals(2, validation.mounts());
        assertEquals(12, validation.placeholders());
        assertEquals(6, validation.provisionalNames());
        assertEquals(6, validation.provisionalDimensions());
        var first = service.placeholders(production, 1);
        assertEquals(12, first.totalCount());
        assertEquals(8, first.entries().size());
        assertEquals(2, first.pageCount());
        assertEquals(first.entries().stream()
                .map(entry -> entry.definitionId().toString()).sorted().toList(),
                first.entries().stream().map(entry -> entry.definitionId().toString()).toList());
        assertEquals(2, service.placeholders(production, 999).page());
        assertEquals(1, service.placeholders(production, -10).page());
        assertEquals(0, service.placeholders(RegistrySnapshot.empty(), 1).totalCount());
    }

    @Test
    void invalidCatalogueAndUnavailableRegistryFailWithoutMutation() {
        BannerCatalogueAdminService service = new BannerCatalogueAdminService();
        RegistrySnapshot invalid = RegistrySnapshotTestFactory.withoutPalette(
                production, production.fabricMaterials().require(COTTON).paletteId());
        assertEquals(CatalogueValidationStatus.INVALID, service.validate(invalid, true, List.of()).status());
        assertEquals(CatalogueValidationStatus.REGISTRY_UNAVAILABLE,
                service.validate(RegistrySnapshot.empty(), false, List.of()).status());
        assertEquals(ProductionBannerCatalogue.TARGET_COUNT, production.banners().activeCount());
    }

    @Test
    void debugResolutionExactlyMatchesGameplayForExplicitAndNearestResults() {
        DyeResolver resolver = new DyeResolver();
        DyeResolutionDebugService debug = new DyeResolutionDebugService(resolver);
        var explicit = debug.resolve(MADDER, COTTON, production, true);
        assertTrue(explicit.successful());
        assertEquals(MatchType.EXPLICIT_MAPPING,
                explicit.resolution().orElseThrow().outcome().result().orElseThrow().matchType());
        assertEquals(resolver.resolve(MADDER, COTTON, production).result(),
                explicit.resolution().orElseThrow().outcome().result());
        PigmentId woad = PigmentId.parse("britannia_mod:woad_blue");
        var nearest = debug.resolve(woad, COTTON, production, true);
        assertEquals(MatchType.NEAREST_COLOUR,
                nearest.resolution().orElseThrow().outcome().result().orElseThrow().matchType());
        assertEquals(resolver.explain(woad, COTTON, production), nearest.resolution().orElseThrow());
        assertFalse(nearest.resolution().orElseThrow().explanation().compatibleCandidates().isEmpty());
    }

    @Test
    void debugFailuresDistinguishAvailabilityDisabledAndMissingInputs() {
        DyeResolutionDebugService debug = new DyeResolutionDebugService(new DyeResolver());
        assertEquals(DyeDebugFailure.REGISTRY_UNAVAILABLE,
                debug.resolve(MADDER, COTTON, RegistrySnapshot.empty(), false).failure());
        assertEquals(DyeDebugFailure.PIGMENT_DISABLED,
                debug.resolve(MADDER, COTTON,
                        RegistrySnapshotTestFactory.withDisabledPigment(production, MADDER), true).failure());
        assertEquals(DyeDebugFailure.MATERIAL_DISABLED,
                debug.resolve(MADDER, COTTON,
                        RegistrySnapshotTestFactory.withDisabledMaterial(production, COTTON), true).failure());
        assertEquals(DyeDebugFailure.PALETTE_MISSING,
                debug.resolve(MADDER, COTTON, RegistrySnapshotTestFactory.withoutPalette(
                        production, production.fabricMaterials().require(COTTON).paletteId()), true).failure());
    }

    @Test
    void commonAdminAndPigmentSourceCodeHasNoClientPacketNpcShopOrCraftingDependency() throws Exception {
        List<Path> roots = List.of(
                Path.of("src/main/java/com/seggellion/britannia_mod/bannerdyeing/admin"),
                Path.of("src/main/java/com/seggellion/britannia_mod/dye/source"));
        for (Path root : roots) {
            try (var files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    assertFalse(source.contains("net.minecraft.client"));
                    assertFalse(source.contains("Minecraft.getInstance"));
                    assertFalse(source.contains("CustomPacketPayload"));
                    assertFalse(source.contains("Merchant"));
                    assertFalse(source.contains("price"));
                    assertFalse(source.contains("currency"));
                    assertFalse(source.contains("crafting"));
                }
            }
        }
    }

    private static CommandSourceStack source(int permission) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, permission,
                "test", Component.literal("test"), null, null);
    }

    private static final class FakeRecipient implements AdminItemRecipient {
        private final String name;
        private final boolean insert;
        private final boolean drop;
        private final List<ItemStack> seen = new ArrayList<>();
        private int dropCalls;

        private FakeRecipient(String name, boolean insert, boolean drop) {
            this.name = name;
            this.insert = insert;
            this.drop = drop;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean insert(ItemStack stack) {
            seen.add(stack);
            if (insert) {
                stack.setCount(0);
            }
            return insert;
        }

        @Override
        public boolean dropRemainder(ItemStack stack) {
            dropCalls++;
            if (drop) {
                stack.setCount(0);
            }
            return drop;
        }
    }
}
