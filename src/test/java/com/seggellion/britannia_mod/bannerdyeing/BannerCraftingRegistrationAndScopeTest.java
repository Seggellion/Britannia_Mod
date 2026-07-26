package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BannerCraftingRegistrationAndScopeTest {
    private static final Path MAIN = Path.of("src/main/java/com/seggellion/britannia_mod");

    @Test
    void oneSharedBannerPatternComponentAndSerializerAreRegistered() throws Exception {
        String items = Files.readString(MAIN.resolve("registry/BannerItemRegistry.java"));
        String components = Files.readString(MAIN.resolve("registry/DataComponentRegistry.java"));
        String recipes = Files.readString(MAIN.resolve("registry/BannerRecipeRegistry.java"));
        assertEquals(1, occurrences(items, "\"banner\""));
        assertEquals(1, occurrences(items, "\"banner_pattern\""));
        assertEquals(1, occurrences(components, "\"banner_pattern_definition\""));
        assertEquals(1, occurrences(recipes, "\"banner_crafting\""));
        assertFalse(recipes.toLowerCase().contains("dye"));
        assertFalse(Files.readString(MAIN.resolve("banner/crafting/BannerCraftingRecipe.java"))
                .contains("RecipeType.CRAFTING"));
    }

    @Test
    void commonCraftingAndPatternSourcesHaveNoClientOrCustomPacketDependency() throws Exception {
        try (var files = Files.walk(MAIN.resolve("banner/crafting"))) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                String source = Files.readString(path);
                assertFalse(source.contains("net.minecraft.client"), path.toString());
                assertFalse(source.contains("com.mojang.blaze3d"), path.toString());
                assertFalse(source.contains("CustomPacketPayload"), path.toString());
                assertFalse(source.contains("DyeResolver"), path.toString());
            }
        }
        String pattern = Files.readString(MAIN.resolve("banner/item/BannerPatternItem.java"));
        assertFalse(pattern.contains("net.minecraft.client"));
        assertFalse(pattern.contains("CustomPacketPayload"));
    }

    @Test
    void creativeTabUsesCanonicalConfiguredVariantsAndNoRawPattern() throws Exception {
        String source = Files.readString(MAIN.resolve("registry/CreativeTabRegistry.java"));
        assertTrue(source.contains("ProductionBannerCatalogue.CANONICAL_PATHS"));
        assertTrue(source.contains("BANNER_PATTERN.get().configured"));
        assertFalse(source.contains("safeAccept(output, BannerItemRegistry.BANNER_PATTERN"));
    }

    @Test
    void noMilestone15OrExpandedRecipeArtifactsExist() throws Exception {
        assertFalse(Files.exists(MAIN.resolve("command/BannerCommand.java")));
        try (var recipes = Files.walk(Path.of("src/main/resources/data/britannia_mod/recipe/banner"))) {
            assertEquals(33, recipes.filter(path -> path.toString().endsWith(".json")).count());
        }
        assertFalse(Files.exists(Path.of("src/main/resources/data/britannia_mod/recipe/banner/cotton")));
        assertFalse(Files.exists(Path.of("src/main/resources/data/britannia_mod/recipe/banner/dye")));
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
