package com.seggellion.britannia_mod.bannerdyeing;

import com.seggellion.britannia_mod.bannerdyeing.registry.ProductionBannerCatalogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone7RegisteredTestContent;
import com.seggellion.britannia_mod.network.payload.dye.C2SConfirmDyeApplicationPayload;
import com.seggellion.britannia_mod.registry.BannerBlockRegistry;
import com.seggellion.britannia_mod.registry.BannerItemRegistry;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import com.seggellion.britannia_mod.registry.DyeItemRegistry;
import com.seggellion.britannia_mod.registry.FishRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.PaintingRegistry;
import com.seggellion.britannia_mod.registry.SignBlockRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import com.seggellion.britannia_mod.registry.WeaponRegistry;
import com.seggellion.britannia_mod.structure.HouseSignBlock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class Milestone14RRemovalAndPreservationTest {
    private static final Path ROOT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path MAIN = ROOT.resolve("src/main");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        Milestone7RegisteredTestContent.ensureRegistered();
    }

    @Test
    void rejectedRegistrationsAndCreativeEntriesAreAbsent() throws Exception {
        assertEquals(1, BannerItemRegistry.ITEMS.getEntries().size());
        assertEquals(8, DyeItemRegistry.ITEMS.getEntries().size());
        // Integrated Patch 18 registry: wine + bank cheque + shrine/monolith instance
        // state + dye tub + banner instance state. The branch-local expectation was 3.
        assertEquals(6, DataComponentRegistry.DATA_COMPONENT_TYPES.getEntries().size());
        assertEquals(2, BannerBlockRegistry.BLOCKS.getEntries().size());
        assertEquals(1, BannerBlockRegistry.BLOCK_ENTITIES.getEntries().size());
        int repositoryItems = ItemRegistry.ITEMS.getEntries().size()
                + FishRegistry.fishIds().size()
                + PaintingRegistry.PAINTING_SIZES.size()
                + WeaponRegistry.WEAPONS.getEntries().size()
                + ToolRegistry.TOOLS.getEntries().size()
                + (int) Arrays.stream(HouseSignBlock.SignType.values())
                        .filter(SignBlockRegistry::isStoreSign).count()
                + BannerItemRegistry.ITEMS.getEntries().size()
                + DyeItemRegistry.ITEMS.getEntries().size();
        // Integrated Patch 18 item universe (farming + shrines + banners + dye added
        // to the branch-local 550).
        // Villa integration adds its construction item set on top of the previous
        // integrated total of 693.
        // Wild Reagents adds the Black Pearl item; ash and Blood Moss reuse existing items.
        // Vendor/Trader economy adds the three profession hammers (Milestone 17) and 122
        // retail items (Milestone 21) on top of 735; the Black Pearl is one of those 122,
        // so those two branches overlap on it rather than adding a 736th id.
        // New-assets adds its decorative block, multiblock, crate, well, ladder and
        // market-stall items on top of that; its own branch-local expectation of 766
        // was never bumped for the four market stalls in its final commit.
        // This total spans eight registries, so it is taken from an actual run rather
        // than from per-branch arithmetic.
        // 903 as of 2026-08-16: the Bronze ingot, seven stalactite block items, and the
        // Hobanger store-sign item were added after the previous integrated total. Bump this
        // deliberately when the item roster genuinely changes -- that is the whole point
        // of counting.
        // 904 as of 2026-08-18: the Flagstone foundation block item. Its three brick-side and
        // two flagstone-top variants are model-level only, so they add no further ids.
        // 905 as of 2026-08-18: the standalone Flagstone building block item. Its two textures
        // are a blockstate variation property, not separate ids.
        // 906 as of 2026-08-20: the Wooden Board Floor Foundation block item -- the structural
        // interior floor a house ships with, as distinct from the perimeter foundation around it
        // and from the decorative flooring an owner lays. It reuses the Wooden Board Floor models
        // and textures wholesale, so it adds one id and no assets beyond its own blockstate.
        // 909 as of 2026-08-20: the villa, patio and keep house deeds. Deeds are registered by
        // walking HouseStyle, so three new styles are three new items and no new registration
        // code; only their item models and language entries are hand-written.
        // 912 as of 2026-08-20: silica sand, raw glass and plaster -- the three bulk building
        // materials the housing economy was missing. All three are plain items with no block
        // form: the finished window, plaster wall and brick course are separate construction
        // blocks and are deliberately not these materials wearing a different name. The two new
        // deposit blocks add no ids at all, because a resource block has no item form.
        // 913 as of 2026-08-22: the Brick Foundation Stairs block item. Its nine models are
        // the three vanilla stair shapes times the three existing brick-side textures, so
        // they are model-level only and add no further ids -- and the block is unbreakable,
        // so the item is the only way anybody ever holds one.
        // 917 as of 2026-08-23: four rounded sandstone-brick stair items, one for each supplied
        // custom_sandstone_brick_top texture variant.
        // 923 as of 2026-08-24: Patch 18 Milestone 1 adds the non-placeable dirt and dung
        // commodities plus empty, dirt-filled, fertile-mixture and water bowl identities. The
        // dung world block intentionally has no BlockItem, so these are exactly six item ids.
        // 925 as of 2026-08-25: the light and dark sandstone pavers each add one ordinary
        // BlockItem; their two visual variants remain block-state/model variants, not item ids.
        assertEquals(925, repositoryItems);

        assertFalse(Files.exists(MAIN.resolve(
                "java/com/seggellion/britannia_mod/registry/BannerRecipeRegistry.java")));
        String bannerItems = read("java/com/seggellion/britannia_mod/registry/BannerItemRegistry.java");
        String components = read("java/com/seggellion/britannia_mod/registry/DataComponentRegistry.java");
        String creative = read("java/com/seggellion/britannia_mod/registry/CreativeTabRegistry.java");
        String mod = read("java/com/seggellion/britannia_mod/BritanniaMod.java");
        for (String removed : List.of(
                "banner_pattern", "cotton_cloth", "linen_cloth", "brass_banner_mount",
                "BANNER_PATTERN_DEFINITION", "BannerRecipeRegistry")) {
            assertFalse((bannerItems + components + creative + mod).contains(removed), removed);
        }
    }

    @Test
    void rejectedClassesAndResourcesAreAbsent() throws Exception {
        assertEquals(0, countJson(MAIN.resolve("resources/data/britannia_mod/recipe/banner")));
        assertEquals(0, countJson(MAIN.resolve("resources/data/britannia_mod/tags/item/banner_fabric")));
        assertEquals(0, countJson(MAIN.resolve("resources/data/britannia_mod/tags/item/banner_mount")));
        for (String model : List.of(
                "banner_pattern.json", "cotton_cloth.json", "linen_cloth.json", "brass_banner_mount.json")) {
            assertFalse(Files.exists(MAIN.resolve("resources/assets/britannia_mod/models/item/" + model)), model);
        }
        assertFalse(Files.exists(MAIN.resolve(
                "java/com/seggellion/britannia_mod/banner/item/BannerPatternItem.java")));
        assertEquals(0, countJava(MAIN.resolve(
                "java/com/seggellion/britannia_mod/banner/crafting")));
    }

    @Test
    void scaffoldAndStatusHaveNoCraftingContract() throws Exception {
        String scaffold = Files.readString(ROOT.resolve(
                "tools/scaffold/com/seggellion/britannia_mod/tools/BannerScaffoldTool.java"));
        String status = Files.readString(ROOT.resolve("content/banner_catalogue_status.md"));
        for (String rejected : List.of(
                "recipe/banner", "tags/item/banner_fabric", "tags/item/banner_mount",
                "generatedRecipes", "fabricUnits", "Recipe definitions complete")) {
            assertFalse(scaffold.contains(rejected), rejected);
        }
        for (String rejected : List.of(
                "Recipe ID", "Recipe present", "Pattern present", "Fabric units",
                "Supported crafting materials", "Supported crafting mounts",
                "Recipe definitions complete")) {
            assertFalse(status.contains(rejected), rejected);
        }
        assertTrue(status.contains("Banner crafting implemented: no"));
        assertTrue(status.contains("Admin acquisition implemented: yes"));
        assertTrue(status.contains("Survival acquisition implemented: no"));
    }

    @Test
    void milestoneThirteenFeatureBoundaryRemainsActive() throws Exception {
        RegistrySnapshot production = DyeResolverFixtures.productionSnapshot();
        assertEquals(ProductionBannerCatalogue.TARGET_COUNT, production.banners().activeCount());
        assertEquals(4, production.fabricMaterials().activeCount());
        assertEquals(4, production.materialPalettes().activeCount());
        assertEquals(7, production.pigments().activeCount());
        assertEquals(2, production.mounts().activeCount());
        assertEquals(1, C2SConfirmDyeApplicationPayload.class.getRecordComponents().length);
        assertEquals(UUID.class, C2SConfirmDyeApplicationPayload.class.getRecordComponents()[0].getType());

        String clientSetup = read("java/com/seggellion/britannia_mod/ClientModSetup.java");
        assertEquals(1, occurrences(clientSetup,
                "BannerBlockRegistry.BANNER_BLOCK_ENTITY.get(), BannerBlockEntityRenderer::new"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(MAIN.resolve(relative));
    }

    private static long countJson(Path directory) throws Exception {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (var paths = Files.walk(directory)) {
            return paths.filter(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().endsWith(".json")).count();
        }
    }

    private static long countJava(Path directory) throws Exception {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (var paths = Files.walk(directory)) {
            return paths.filter(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().endsWith(".java")).count();
        }
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }
}
