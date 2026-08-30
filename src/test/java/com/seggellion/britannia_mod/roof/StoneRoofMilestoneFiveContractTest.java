package com.seggellion.britannia_mod.roof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StoneRoofMilestoneFiveContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("britannia.projectDir", "."));
    private static final Path JAVA = PROJECT.resolve(
            "src/main/java/com/seggellion/britannia_mod");
    private static final Path ASSETS = PROJECT.resolve(
            "src/main/resources/assets/britannia_mod");

    @Test
    void canonicalMaterialsUseTheSharedVariantBlockAndKeepSlateCompatibilityIds()
            throws Exception {
        String blocks = source("registry/BlockRegistry.java");

        assertTrue(blocks.contains(
                "DeferredHolder<Block, VariantTopOnlySlabBlock> SANDSTONE_ROOF = "
                        + "BLOCKS.register(\"sandstone_roof\""));
        assertTrue(blocks.contains(
                "DeferredHolder<Block, VariantTopOnlySlabBlock> LIMESTONE_ROOF = "
                        + "BLOCKS.register(\"limestone_roof\""));
        assertEquals(3, occurrences(blocks,
                "new VariantTopOnlySlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB))"));
        assertTrue(blocks.contains(
                "DeferredHolder<Block, VariantTopOnlySlabBlock> SLATE_ROOF_FLAT"));
        assertTrue(blocks.contains(
                "DeferredHolder<Block, StairBlock> SLATE_ROOF = BLOCKS.register(\"slate_roof\""));
        assertTrue(blocks.contains(
                "DeferredHolder<Block, TopOnlySlabBlock> SLATE_ROOF_1_FLAT"));
        assertTrue(blocks.contains(
                "DeferredHolder<Block, TopOnlySlabBlock> SLATE_ROOF_2_FLAT"));
        assertFalse(Files.exists(JAVA.resolve("block/SandstoneRoofBlock.java")));
        assertFalse(Files.exists(JAVA.resolve("block/LimestoneRoofBlock.java")));
    }

    @Test
    void blockItemsAndAdaptiveBlockEntityCoverBothNewCanonicalIds() throws Exception {
        String items = source("registry/ItemRegistry.java");
        String blockEntities = source("registry/BlockEntityRegistry.java");

        assertTrue(items.contains(
                "SANDSTONE_ROOF_ITEM = ITEMS.register(\"sandstone_roof\""));
        assertTrue(items.contains(
                "new BlockItem(BlockRegistry.SANDSTONE_ROOF.get(), new Item.Properties())"));
        assertTrue(items.contains(
                "LIMESTONE_ROOF_ITEM = ITEMS.register(\"limestone_roof\""));
        assertTrue(items.contains(
                "new BlockItem(BlockRegistry.LIMESTONE_ROOF.get(), new Item.Properties())"));
        assertTrue(blockEntities.contains("BlockRegistry.SANDSTONE_ROOF.get()"));
        assertTrue(blockEntities.contains("BlockRegistry.LIMESTONE_ROOF.get()"));
    }

    @Test
    void creativeAndLocalizationExposeExactlyThreeCanonicalStoneSlateRoofs()
            throws Exception {
        String creative = source("registry/CreativeTabRegistry.java");
        JsonObject language = JsonParser.parseString(
                Files.readString(ASSETS.resolve("lang/en_us.json"))).getAsJsonObject();

        String slate = "ItemRegistry.SLATE_ROOF_FLAT_ITEM.get()";
        String sandstone = "ItemRegistry.SANDSTONE_ROOF_ITEM.get()";
        String limestone = "ItemRegistry.LIMESTONE_ROOF_ITEM.get()";
        assertEquals(1, occurrences(creative, slate));
        assertEquals(1, occurrences(creative, sandstone));
        assertEquals(1, occurrences(creative, limestone));
        assertTrue(creative.indexOf(slate) < creative.indexOf(sandstone));
        assertTrue(creative.indexOf(sandstone) < creative.indexOf(limestone));
        assertFalse(creative.contains("ItemRegistry.SLATE_ROOF_ITEM.get()"));
        assertFalse(creative.contains("ItemRegistry.SLATE_ROOF_1_FLAT_ITEM.get()"));
        assertFalse(creative.contains("ItemRegistry.SLATE_ROOF_2_FLAT_ITEM.get()"));

        assertEquals("Slate Roof",
                language.get("block.britannia_mod.slate_roof_flat").getAsString());
        assertEquals("Sandstone Roof",
                language.get("block.britannia_mod.sandstone_roof").getAsString());
        assertEquals("Limestone Roof",
                language.get("block.britannia_mod.limestone_roof").getAsString());
    }

    @Test
    void adaptiveModelWrappingIsTypeBasedAndKeepsInventoryExcluded() throws Exception {
        String adaptive = source("client/model/AdaptiveRoofClientModels.java");

        assertTrue(adaptive.contains("BuiltInRegistries.BLOCK::get"));
        assertTrue(adaptive.contains("instanceof TopOnlySlabBlock"));
        assertTrue(adaptive.contains("!\"inventory\".equals(location.variant())"));
        assertFalse(adaptive.contains("ADAPTIVE_ROOF_IDS"));
        assertFalse(adaptive.contains("sandstone_roof"));
        assertFalse(adaptive.contains("limestone_roof"));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(JAVA.resolve(relative));
    }

    private static int occurrences(String text, String target) {
        return (text.length() - text.replace(target, "").length()) / target.length();
    }
}
