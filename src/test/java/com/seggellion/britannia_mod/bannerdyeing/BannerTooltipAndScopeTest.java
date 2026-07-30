package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.item.BannerItemFactory;
import com.seggellion.britannia_mod.banner.item.BannerItemStateAccess;
import com.seggellion.britannia_mod.banner.item.BannerStateValidation;
import com.seggellion.britannia_mod.banner.item.BannerTooltip;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshotTestFactory;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone7RegisteredTestContent;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import com.seggellion.britannia_mod.registry.BannerItemRegistry;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BannerTooltipAndScopeTest {
    private static final BannerDefinitionId WARD = BannerDefinitionId.parse("britannia_mod:joined_wards");
    private static final FabricMaterialId SILK = FabricMaterialId.parse("britannia_mod:silk");
    private static final PigmentId MADDER = PigmentId.parse("britannia_mod:madder_red");
    private static final MountId BRASS = MountId.parse("britannia_mod:brass");
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
    void validNaturalTooltipUsesRegistryNamesAndOmitsDyedWith() {
        List<String> keys = keys(lines(natural(), production));
        assertTrue(keys.contains("tooltip.britannia_mod.banner.material"));
        assertTrue(keys.contains("tooltip.britannia_mod.banner.colour"));
        assertTrue(keys.contains("tooltip.britannia_mod.banner.natural"));
        assertTrue(keys.contains("tooltip.britannia_mod.banner.mount"));
        assertTrue(keys.contains("tooltip.britannia_mod.banner.size"));
        assertTrue(keys.contains("tooltip.britannia_mod.banner.placement"));
        assertFalse(keys.contains("tooltip.britannia_mod.banner.dyed_with"));
    }

    @Test
    void dyedPlaceholderTooltipShowsPigmentPlaceholderProvisionalAndBothOrientations() {
        List<Component> lines = lines(dyed(), production);
        List<String> keys = keys(lines);
        assertTrue(keys.contains("tooltip.britannia_mod.banner.dyed_with"));
        assertTrue(keys.contains("tooltip.britannia_mod.banner.placeholder_warning"));
        assertTrue(keys.contains("tooltip.britannia_mod.banner.provisional_dimensions"));
        Component placementArg = (Component) contents(lines.stream()
                .filter(line -> key(line).equals("tooltip.britannia_mod.banner.placement"))
                .findFirst().orElseThrow()).getArgs()[0];
        assertEquals("tooltip.britannia_mod.banner.orientation.both", key(placementArg));
    }

    @Test
    void oneOrientationFixtureDisplaysOnlyParallel() {
        BannerDefinition definition = production.banners().require(WARD);
        BannerDefinition parallelOnly = new BannerDefinition(
                definition.schemaVersion(), definition.id(), definition.displayNameKey(), definition.contentStatus(),
                definition.sourceReference(), definition.catalogueGroup(), definition.dimensions(),
                List.of(BannerOrientation.WALL_PARALLEL), definition.supportedMounts(), definition.defaultMount(),
                definition.defaultMaterial(), definition.assets(), definition.placementProfile());
        RegistrySnapshot snapshot = RegistrySnapshotTestFactory.replaceBanner(production, parallelOnly);
        Component placement = lines(natural(), snapshot).stream()
                .filter(line -> key(line).equals("tooltip.britannia_mod.banner.placement"))
                .findFirst().orElseThrow();
        assertEquals("tooltip.britannia_mod.banner.orientation.parallel",
                key((Component) contents(placement).getArgs()[0]));
    }

    @Test
    void unconfiguredTooltipIsSafeAndDoesNotMutate() {
        ItemStack raw = new ItemStack(item);
        List<Component> lines = BannerTooltip.lines(access.validate(raw, production, true), production, true);
        assertEquals(List.of("tooltip.britannia_mod.banner.unconfigured",
                "tooltip.britannia_mod.banner.missing_state"), keys(lines));
        assertTrue(raw.getComponentsPatch().isEmpty());
    }

    @Test
    void missingReferencesDisplayStableIdsAndNeverMutate() {
        ItemStack stack = dyed();
        var before = stack.getComponentsPatch();
        assertMissing(stack, RegistrySnapshotTestFactory.withoutBanner(production, WARD), "definition", WARD.toString());
        assertMissing(stack, RegistrySnapshotTestFactory.withoutMaterial(production, SILK), "material", SILK.toString());
        assertMissing(stack, RegistrySnapshotTestFactory.withoutPigment(production, MADDER), "pigment", MADDER.toString());
        assertMissing(stack, RegistrySnapshotTestFactory.withoutMount(production, BRASS), "mount", BRASS.toString());
        assertEquals(before, stack.getComponentsPatch());
    }

    @Test
    void missingPigmentKeepsUsableColourAndMissingColourShowsRepairDiagnostic() {
        ItemStack stack = dyed();
        RegistrySnapshot noPigment = RegistrySnapshotTestFactory.withoutPigment(production, MADDER);
        List<String> pigmentKeys = keys(lines(stack, noPigment));
        assertTrue(pigmentKeys.contains("tooltip.britannia_mod.banner.colour"));
        assertTrue(pigmentKeys.contains("tooltip.britannia_mod.banner.missing_pigment"));

        MaterialPalette silk = production.materialPalettes().require(
                production.fabricMaterials().require(SILK).paletteId());
        MaterialPalette withoutRuby = new MaterialPalette(
                silk.schemaVersion(), silk.id(), silk.materialId(), silk.naturalColourId(),
                silk.entries().stream()
                        .filter(entry -> !entry.id().equals(ResolvedColourId.parse("britannia_mod:silk_ruby")))
                        .toList(), Map.of());
        RegistrySnapshot noColour = RegistrySnapshotTestFactory.replacePalette(production, withoutRuby);
        List<String> colourKeys = keys(lines(stack, noColour));
        assertTrue(colourKeys.contains("tooltip.britannia_mod.banner.missing_colour"));
        assertTrue(colourKeys.contains("tooltip.britannia_mod.banner.repairable_colour"));
    }

    @Test
    void configuredDisplayNameUsesDefinitionButPlayerCustomNameWins() {
        ItemStack stack = natural();
        assertEquals("banner.britannia_mod.joined_wards", key(item.configuredName(stack, production)));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Custom Standard"));
        assertEquals("Custom Standard", stack.getHoverName().getString());
    }

    @Test
    void oneSharedItemAndOneTypedComponentUseRequiredIdsAndCodecs() {
        assertEquals("britannia_mod:banner", BannerItemRegistry.BANNER.getId().toString());
        assertEquals("britannia_mod:banner_instance_state",
                DataComponentRegistry.BANNER_INSTANCE_STATE.getId().toString());
        assertTrue(DataComponentRegistry.createBannerInstanceStateType().codec() != null);
        assertTrue(DataComponentRegistry.createBannerInstanceStateType().streamCodec() != null);
        assertEquals(1, BannerItemRegistry.ITEMS.getEntries().size());
    }

    @Test
    void localizationModelAndScopeRestrictionsArePresent() throws Exception {
        JsonObject lang = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/britannia_mod/lang/en_us.json"))).getAsJsonObject();
        for (String key : List.of(
                "item.britannia_mod.banner", "tooltip.britannia_mod.banner.unconfigured",
                "tooltip.britannia_mod.banner.missing_state", "tooltip.britannia_mod.banner.material",
                "tooltip.britannia_mod.banner.colour", "tooltip.britannia_mod.banner.dyed_with",
                "tooltip.britannia_mod.banner.natural", "tooltip.britannia_mod.banner.mount",
                "tooltip.britannia_mod.banner.size", "tooltip.britannia_mod.banner.placement",
                "tooltip.britannia_mod.banner.placeholder_warning",
                "tooltip.britannia_mod.banner.provisional_dimensions",
                "tooltip.britannia_mod.banner.missing_definition",
                "tooltip.britannia_mod.banner.missing_material",
                "tooltip.britannia_mod.banner.missing_colour",
                "tooltip.britannia_mod.banner.missing_pigment",
                "tooltip.britannia_mod.banner.missing_mount")) {
            assertTrue(lang.has(key), key);
        }
        assertTrue(Files.isRegularFile(Path.of(
                "src/main/resources/assets/britannia_mod/models/item/banner.json")));
        String commonSources = Files.walk(Path.of("src/main/java/com/seggellion/britannia_mod/banner"))
                .filter(path -> path.toString().endsWith(".java"))
                .map(path -> {
                    try { return Files.readString(path); } catch (Exception exception) { throw new RuntimeException(exception); }
                }).reduce("", String::concat);
        assertFalse(commonSources.contains("net.minecraft.client"));
        assertFalse(commonSources.contains("Screen"));
        assertFalse(commonSources.contains("CustomPacketPayload"));
    }

    private static void assertMissing(ItemStack stack, RegistrySnapshot snapshot, String domain, String id) {
        List<Component> lines = lines(stack, snapshot);
        Component missing = lines.stream()
                .filter(line -> key(line).equals("tooltip.britannia_mod.banner.missing_" + domain))
                .findFirst().orElseThrow();
        assertEquals(id, contents(missing).getArgs()[0]);
    }

    private static List<Component> lines(ItemStack stack, RegistrySnapshot snapshot) {
        BannerStateValidation validation = access.validate(stack, snapshot, true);
        return BannerTooltip.lines(validation, snapshot, true);
    }

    private static ItemStack natural() {
        return factory.naturalCottonAdminBanner(WARD, production, true).stack().orElseThrow();
    }

    private static ItemStack dyed() {
        return factory.fullySpecifiedBanner(WARD, SILK,
                ResolvedColourId.parse("britannia_mod:silk_ruby"), Optional.of(MADDER), BRASS,
                production, true).stack().orElseThrow();
    }

    private static List<String> keys(List<Component> lines) {
        return lines.stream().map(BannerTooltipAndScopeTest::key).toList();
    }

    private static String key(Component component) {
        return contents(component).getKey();
    }

    private static TranslatableContents contents(Component component) {
        return (TranslatableContents) component.getContents();
    }
}
