package com.seggellion.britannia_mod.loot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A loot table only loads from the directory its owner looks in.
 *
 * <p>A block's table is resolved as {@code <namespace>:blocks/<path>}, which is
 * {@code data/<namespace>/loot_table/blocks/<path>.json}, and an entity's as
 * {@code entities/<path>}. Put a block table under {@code entities/} and nothing complains: the
 * file parses, the id it would answer to is one nothing asks for, and the block simply drops
 * nothing. There is no log line and no crash.
 *
 * <p>Which is what had happened to the three Britannia chests. {@code chest_wooden},
 * {@code chest_metal} and {@code chest_metal_bronze} each shipped a perfectly good
 * {@code "type": "minecraft:block"} table under {@code loot_table/entities/}, so breaking one
 * returned the contents (those come from the block's own {@code onRemove}) and never the chest.
 *
 * <p>Checking the declared type against the directory catches it, and would have caught it the day
 * the files were added.
 */
class LootTableDirectoryTest {

    private static final Path LOOT_TABLES = Path.of(System.getProperty("britannia.projectDir", "."))
            .resolve("src/main/resources/data/britannia_mod/loot_table");

    @Test
    void everyLootTableSitsInTheDirectoryItsTypeIsLoadedFrom() throws IOException {
        List<String> misfiled = new ArrayList<>();

        try (Stream<Path> files = Files.walk(LOOT_TABLES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                JsonObject table = JsonParser.parseString(
                        Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!table.has("type")) continue;

                String type = table.get("type").getAsString();
                String directory = LOOT_TABLES.relativize(file).getName(0).toString();

                String expected = switch (type) {
                    case "minecraft:block" -> "blocks";
                    case "minecraft:entity" -> "entities";
                    case "minecraft:chest" -> "chests";
                    default -> directory;
                };
                if (!expected.equals(directory)) {
                    misfiled.add(LOOT_TABLES.relativize(file) + " declares " + type
                            + " but sits in " + directory + "/, where nothing will ever ask for it");
                }
            }
        }

        assertTrue(misfiled.isEmpty(), String.join("\n", misfiled));
    }

    /**
     * The three chests specifically, because they are the ones that were wrong and they are
     * containers — a container that returns nothing when broken loses whatever it was worth.
     */
    @Test
    void theBritanniaChestsHaveABlockLootTableWhereBlocksLookForOne() {
        for (String chest : List.of("chest_wooden", "chest_metal", "chest_metal_bronze")) {
            assertTrue(Files.isRegularFile(LOOT_TABLES.resolve("blocks").resolve(chest + ".json")),
                    chest + " has no block loot table, so breaking one returns no chest at all");
        }
    }
}
