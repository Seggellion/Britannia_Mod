package com.seggellion.britannia_mod.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SlateRoofMilestoneThreeContractTest {
    private static final Path PROJECT = Path.of(
            System.getProperty("britannia.projectDir", "."));
    private static final Path ASSETS = PROJECT.resolve(
            "src/main/resources/assets/britannia_mod");

    @Test
    void canonicalBlockstateHasEveryDeterministicTypeAndVariationSelector() throws Exception {
        JsonObject variants = json(
                ASSETS.resolve("blockstates/slate_roof_flat.json"))
                .getAsJsonObject("variants");
        Map<Integer, String> expectedModels = Map.of(
                0, "slate_roof_1",
                1, "slate_roof_2",
                2, "slate_roof_3",
                3, "slate_roof_4",
                4, "slate_roof_5",
                5, "slate_roof_6");

        assertEquals(18, variants.size());
        for (int variation = 0; variation < 6; variation++) {
            String expectedModel = "britannia_mod:block/structure/roof/"
                    + expectedModels.get(variation);
            for (String type : new String[] {"bottom", "top", "double"}) {
                JsonElement definition = variants.get(
                        "type=" + type + ",variation=" + variation);
                assertTrue(definition != null && definition.isJsonObject(),
                        "missing deterministic selector for " + type
                                + " variation " + variation);
                assertEquals(1, definition.getAsJsonObject().size());
                assertEquals(expectedModel,
                        definition.getAsJsonObject().get("model").getAsString());
            }
        }

        assertFalse(Files.exists(ASSETS.resolve(
                "models/block/structure/slate_roof_flat_double.json")));
    }

    @Test
    void everyMappedSlateModelAndTextureExists() throws Exception {
        Map<String, String> modelTextures = Map.of(
                "slate_roof_1", "slate_roof_flat",
                "slate_roof_2", "slate_roof_1_flat",
                "slate_roof_3", "slate_roof_2_flat",
                "slate_roof_4", "slate_roof_flat",
                "slate_roof_5", "slate_roof_1_flat",
                "slate_roof_6", "slate_roof_2_flat");

        for (Map.Entry<String, String> entry : modelTextures.entrySet()) {
            Path modelPath = ASSETS.resolve(
                    "models/block/structure/roof/" + entry.getKey() + ".json");
            assertTrue(Files.isRegularFile(modelPath));
            assertEquals("britannia_mod:block/structure/roof/top_only_slab",
                    json(modelPath).get("parent").getAsString());
            assertEquals("britannia_mod:block/roof/" + entry.getValue(),
                    json(modelPath)
                            .getAsJsonObject("textures")
                            .get("roof")
                            .getAsString());
            assertTrue(Files.isRegularFile(ASSETS.resolve(
                    "textures/block/roof/" + entry.getValue() + ".png")));
        }

        JsonObject itemModel = json(ASSETS.resolve("models/item/slate_roof_flat.json"));
        assertEquals("britannia_mod:block/structure/roof/slate_roof_1",
                itemModel.get("parent").getAsString());
    }

    @Test
    void canonicalRegistrationAndSingleCreativeEntryPreserveLegacyIds() throws Exception {
        String blocks = javaSource("registry/BlockRegistry.java");
        String items = javaSource("registry/ItemRegistry.java");
        String blockEntities = javaSource("registry/BlockEntityRegistry.java");
        String creative = javaSource("registry/CreativeTabRegistry.java");

        assertTrue(blocks.contains(
                "DeferredHolder<Block, VariantTopOnlySlabBlock> SLATE_ROOF_FLAT"));
        assertTrue(blocks.contains(
                "new VariantTopOnlySlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB))"));
        assertTrue(blocks.contains(
                "DeferredHolder<Block, StairBlock> SLATE_ROOF = BLOCKS.register(\"slate_roof\""));
        assertTrue(blocks.contains(
                "DeferredHolder<Block, Block> SLATE_ROOF_BASE = BLOCKS.register(\"slate_roof_base\""));
        assertTrue(blocks.contains(
                "DeferredHolder<Block, TopOnlySlabBlock> SLATE_ROOF_1_FLAT"));
        assertTrue(blocks.contains(
                "DeferredHolder<Block, TopOnlySlabBlock> SLATE_ROOF_2_FLAT"));

        for (String id : new String[] {
                "slate_roof", "slate_roof_flat", "slate_roof_1_flat", "slate_roof_2_flat"
        }) {
            assertTrue(items.contains("ITEMS.register(\"" + id + "\""),
                    () -> "legacy item registration disappeared: " + id);
        }
        for (String holder : new String[] {
                "SLATE_ROOF_FLAT", "SLATE_ROOF_1_FLAT", "SLATE_ROOF_2_FLAT"
        }) {
            assertTrue(blockEntities.contains("BlockRegistry." + holder + ".get()"),
                    () -> "adaptive block-entity support disappeared: " + holder);
        }

        assertEquals(1, occurrences(creative, "ItemRegistry.SLATE_ROOF_FLAT_ITEM.get()"));
        assertFalse(creative.contains("ItemRegistry.SLATE_ROOF_ITEM.get()"));
        assertFalse(creative.contains("ItemRegistry.SLATE_ROOF_1_FLAT_ITEM.get()"));
        assertFalse(creative.contains("ItemRegistry.SLATE_ROOF_2_FLAT_ITEM.get()"));
    }

    @Test
    void canonicalPlayerFacingNameIsSlateRoof() throws Exception {
        JsonObject language = json(ASSETS.resolve("lang/en_us.json"));
        assertEquals("Slate Roof",
                language.get("block.britannia_mod.slate_roof_flat").getAsString());
    }

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static String javaSource(String relative) throws Exception {
        return Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/" + relative));
    }

    private static int occurrences(String text, String target) {
        return (text.length() - text.replace(target, "").length()) / target.length();
    }
}
