package com.seggellion.britannia_mod.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Data-pack files must live where 1.21.1 actually reads them.
 *
 * <h2>The defect this exists for</h2>
 * The project removed vanilla diamond tools in 2024 by overriding
 * {@code data/minecraft/recipes/diamond_pickaxe.json} with an unbuildable recipe. Minecraft 1.21
 * then renamed every data-pack directory to the singular — {@code recipe}, {@code loot_table},
 * {@code advancement} — and the file was never moved. The loader stopped reading it, no error was
 * printed anywhere, and the diamond pickaxe quietly became craftable again.
 *
 * <p>Nothing about that failure was visible: the file was present, well-formed, and correct in
 * every respect except which folder it sat in. Milestone 10B found it only by noticing that the
 * repository's own recipes used a different directory name than the override did.
 *
 * <p>So the convention is asserted rather than trusted, in both directions: no pre-1.21 plural
 * directory may exist, and the diamond removals must be present in the singular one.
 */
@DisplayName("data pack files sit in directories 1.21.1 loads")
final class DataPackDirectoryConventionTest {

    /** Gradle does not run tests from the project root; the build supplies this. */
    private static final Path PROJECT = Paths.get(System.getProperty("britannia.projectDir", "."));

    private static final Path DATA = PROJECT.resolve(Paths.get("src", "main", "resources", "data"));

    /**
     * Directories 1.21 renamed. A file under any of these is read by nothing.
     *
     * <p>{@code tags} is deliberately absent: it kept its plural name in 1.21. Its <em>children</em>
     * were singularised ({@code tags/block}, {@code tags/item}), which is a separate rule and is
     * checked below.
     */
    private static final List<String> PRE_1_21_DIRECTORIES =
            List.of("recipes", "loot_tables", "advancements", "predicates", "item_modifiers",
                    "structures", "functions");

    /**
     * Directories that keep a plural name legitimately, because nothing in the vanilla loader reads
     * them.
     *
     * <p>{@code britannia_mod/structures} holds the ten house and castle templates, and the mod
     * fetches them itself by explicit path — {@code StructureCache}, {@code StructurePlacer} and
     * {@code ClientEventHandler} all build {@code britannia_mod:structures/<name>.nbt} and ask the
     * resource manager directly. It never goes through {@code StructureTemplateManager}, so the
     * 1.21 rename does not apply to it and renaming it would break every house.
     *
     * <p>An entry here has to be justified by a real reader in mod code. "It looked fine" is how
     * the diamond recipe survived two years.
     */
    private static final List<String> READ_BY_MOD_CODE_NOT_THE_LOADER =
            List.of("britannia_mod/structures");

    /** Every vanilla diamond equipment recipe the project intends to be uncraftable. */
    private static final List<String> DISABLED_DIAMOND_RECIPES = List.of(
            "diamond_sword", "diamond_pickaxe", "diamond_axe", "diamond_shovel", "diamond_hoe",
            "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots");

    private static List<Path> walk() throws IOException {
        try (Stream<Path> paths = Files.walk(DATA)) {
            return paths.toList();
        }
    }

    @Test
    @DisplayName("no data file hides in a directory 1.21 renamed away")
    void noPre121DirectoriesRemain() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path path : walk()) {
            if (!Files.isDirectory(path)) {
                continue;
            }
            String name = path.getFileName().toString();
            String relative = DATA.relativize(path).toString().replace('\\', '/');
            if (PRE_1_21_DIRECTORIES.contains(name)
                    && !READ_BY_MOD_CODE_NOT_THE_LOADER.contains(relative)) {
                offenders.add(relative);
            }
        }
        assertEquals(List.of(), offenders,
                "these directories are not read on 1.21.1 -- anything inside them is inert."
                        + " 1.21 singularised the data pack directories; use 'recipe', 'loot_table',"
                        + " 'advancement' and so on");
    }

    @Test
    @DisplayName("the diamond removals are present where the loader will find them")
    void diamondRemovalsArePresentAndLoadable() throws IOException {
        Path recipes = DATA.resolve("minecraft").resolve("recipe");
        assertTrue(Files.isDirectory(recipes),
                "data/minecraft/recipe must exist -- it is where a vanilla recipe override lives");

        for (String recipe : DISABLED_DIAMOND_RECIPES) {
            Path file = recipes.resolve(recipe + ".json");
            assertTrue(Files.isRegularFile(file), recipe + " must be overridden to disable it");
        }
    }

    @Test
    @DisplayName("each removal actually disables, rather than merely being present")
    void eachRemovalCarriesADisablingCondition() throws IOException {
        Path recipes = DATA.resolve("minecraft").resolve("recipe");
        for (String recipe : DISABLED_DIAMOND_RECIPES) {
            Path file = recipes.resolve(recipe + ".json");
            JsonObject json;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }
            assertTrue(json.has("neoforge:conditions"),
                    recipe + " must carry neoforge:conditions, or it overrides the vanilla recipe"
                            + " with a working one instead of removing it");
            String conditions = json.get("neoforge:conditions").toString();
            assertTrue(conditions.contains("neoforge:false"),
                    recipe + " must be conditioned on neoforge:false so it is never registered,"
                            + " found " + conditions);
        }
    }

    /**
     * The repository's own recipes prove the singular directory is the one that loads.
     *
     * <p>Without this, {@link #noPre121DirectoriesRemain} could be satisfied by a repository that
     * had no recipes at all. These three load today, so the convention they follow is the working
     * one.
     */
    @Test
    @DisplayName("the mod's own recipes confirm which directory name works")
    void theModsOwnRecipesUseTheSingularDirectory() throws IOException {
        Path ours = DATA.resolve("britannia_mod").resolve("recipe");
        assertTrue(Files.isDirectory(ours), "britannia_mod recipes live in the singular directory");
        try (Stream<Path> files = Files.list(ours)) {
            assertTrue(files.anyMatch(path -> path.toString().endsWith(".json")),
                    "at least one shipped recipe must prove the directory is real");
        }
        assertFalse(Files.exists(DATA.resolve("britannia_mod").resolve("recipes")),
                "the mod must not grow a plural recipes directory either");
    }
}
