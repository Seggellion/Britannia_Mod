package com.seggellion.britannia_mod.farming;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The data half of the grape-vine axe fix.
 *
 * <h2>Why the directory is asserted, not just the contents</h2>
 * {@code data/britannia_mod/tags/} in this repository carries both the live Minecraft 1.21 singular
 * directories ({@code tags/block}, {@code tags/item}) and dead pre-1.21 plural ones
 * ({@code tags/blocks}, {@code tags/items}) left over from an older version. A tag authored into the
 * plural directory is silently ignored at runtime — it loads no contents, throws nothing, and looks
 * entirely correct in a diff. That failure mode is indistinguishable from the original defect, so
 * the location is pinned here rather than left to reviewer attention.
 *
 * <p>Behaviour lives in {@code GrapeVineAxeGameTests}; block tags are datapack-loaded, so a plain
 * unit test cannot resolve {@code state.is(tag)} and must not pretend to.
 */
class GrapeVineAxeTagContractTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path DATA = PROJECT.resolve("src/main/resources/data/britannia_mod/tags");

    private static final String GRAPE_ARBOR = "britannia_mod:grape_arbor_block";
    private static final String GRAPE_VINE = "britannia_mod:grape_vine_block";

    @Test
    void theSeverableTagLivesInTheLiveSingularBlockDirectory() {
        assertTrue(Files.isRegularFile(DATA.resolve("block/axe_severable_plants.json")),
                "the severable-plant tag must exist at tags/block/axe_severable_plants.json");
        assertFalse(Files.exists(DATA.resolve("blocks/axe_severable_plants.json")),
                "tags/blocks/ is the dead pre-1.21 path and is never loaded");
    }

    @Test
    void theSeverableTagHoldsBothGrapeBlocks() throws IOException {
        List<String> values = tagValues("block/axe_severable_plants.json");

        assertTrue(values.contains(GRAPE_ARBOR), "the live arbor block must be severable");
        assertTrue(values.contains(GRAPE_VINE), "the retired standalone vine must be severable too");
        assertEquals(2, values.size(), "the category is grape vines only: " + values);
    }

    /**
     * The whole point of a separate category. A grape block inside any wood tag would be routed by
     * {@code WoodChopEventHandler} as timber and would mint a {@code WeightedWoodItem}.
     */
    @Test
    void noGrapeBlockIsEnrolledInAnyWoodOrFruitTreeTag() throws IOException {
        for (String tag : List.of(
                "block/logs.json",
                "block/fruit_tree_blocks.json",
                "block/fruit_tree_logs.json",
                "block/fruit_tree_trunks.json",
                "block/fruit_tree_branches.json",
                "block/fruit_tree_leaves.json",
                "block/fruit_tree_fruits.json")) {
            List<String> values = tagValues(tag);
            assertFalse(values.contains(GRAPE_ARBOR), GRAPE_ARBOR + " must not be in " + tag);
            assertFalse(values.contains(GRAPE_VINE), GRAPE_VINE + " must not be in " + tag);
        }
    }

    /**
     * The tag is the only authority the axe rules consult for this category, so the predicate must
     * read it rather than name the blocks, and it must stay out of the wood predicates.
     */
    @Test
    void axeHarvestRulesReadTheTagAndKeepSeverablePlantsOutOfTheWoodPredicates() throws IOException {
        String source = Files.readString(
                PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/util/AxeHarvestRules.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("isAllowedAxeSeverablePlant"),
                "the severable-plant predicate must exist by name");
        assertTrue(source.contains("ModTags.Blocks.AXE_SEVERABLE_PLANTS"),
                "the predicate must resolve the tag rather than list blocks");

        String logBody = methodBody(source, "isAllowedLogBlock");
        String leafBody = methodBody(source, "isAllowedLeafBlock");
        assertFalse(logBody.contains("AXE_SEVERABLE_PLANTS"),
                "severable plants must not be classified as logs");
        assertFalse(leafBody.contains("AXE_SEVERABLE_PLANTS"),
                "severable plants must not be classified as leaves");
        assertTrue(methodBody(source, "isAllowedAxeHarvestBlock").contains("isAllowedAxeSeverablePlant"),
                "severable plants must be reachable through the axe's harvest predicate");
    }

    private static List<String> tagValues(String relativePath) throws IOException {
        Path file = DATA.resolve(relativePath);
        assertTrue(Files.isRegularFile(file), "missing tag file " + file);
        JsonObject root = JsonParser.parseString(
                Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray array = root.getAsJsonArray("values");
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            values.add(element.getAsString());
        }
        return values;
    }

    /** The text of one method, from its signature to the next method declaration. */
    private static String methodBody(String source, String methodName) {
        int start = source.indexOf("boolean " + methodName + "(");
        assertTrue(start >= 0, "method " + methodName + " is missing");
        int next = source.indexOf("    public static boolean ", start + 1);
        return next < 0 ? source.substring(start) : source.substring(start, next);
    }
}
