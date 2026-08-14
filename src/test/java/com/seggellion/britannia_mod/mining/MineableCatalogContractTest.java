package com.seggellion.britannia_mod.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Milestone 2 contracts for the shipped Mining progression catalogue: the approved metal ladder is
 * encoded exactly, every block the live break flow manages resolves to exactly one ACTIVE
 * definition, and nothing else resolves at all.
 */
class MineableCatalogContractTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path CATALOG_JSON =
            PROJECT.resolve("src/main/resources/data/britannia_mod/mining/mineables.json");
    private static final Path LANG_JSON =
            PROJECT.resolve("src/main/resources/assets/britannia_mod/lang/en_us.json");

    /**
     * The complete managed set discovered in milestone 1 (PickaxeMiningRules): the catalogue's
     * ACTIVE coverage must equal it exactly — no silent widening, no dropped block.
     */
    private static final Set<String> DISCOVERED_MANAGED_BLOCKS = Set.of(
            "minecraft:stone", "minecraft:cobblestone", "minecraft:andesite", "minecraft:diorite",
            "minecraft:granite", "minecraft:tuff", "minecraft:basalt", "minecraft:smooth_basalt",
            "minecraft:blackstone", "minecraft:deepslate", "minecraft:cobbled_deepslate",
            "minecraft:calcite",
            "britannia_mod:igneous_rock", "britannia_mod:metamorphic_rock",
            "britannia_mod:volcanic_rock", "britannia_mod:glacial_rock",
            "minecraft:iron_ore", "minecraft:deepslate_iron_ore",
            "minecraft:gold_ore", "minecraft:deepslate_gold_ore",
            "britannia_mod:copper_ore", "britannia_mod:tin_ore", "britannia_mod:silver_ore",
            "britannia_mod:gold_ore", "britannia_mod:shadow_iron_ore", "britannia_mod:agapite_ore",
            "britannia_mod:verite_ore", "britannia_mod:valorite_ore",
            "britannia_mod:high_purity_silver_ore");

    private static MineableCatalog catalog;

    @BeforeAll
    static void loadShippedCatalog() throws Exception {
        catalog = MineableCatalog.parse(Files.newBufferedReader(CATALOG_JSON));
    }

    @Test
    void approvedMetalLadderIsEncodedExactly() {
        Map<String, Float> expected = new HashMap<>();
        expected.put("iron", 0.0f);
        expected.put("silver", 55.0f);
        expected.put("tin", 65.0f);
        expected.put("shadow_iron", 70.0f);
        expected.put("copper", 75.0f);
        expected.put("gold", 85.0f);
        expected.put("agapite", 90.0f);
        expected.put("verite", 95.0f);
        expected.put("valorite", 99.0f);
        expected.forEach((id, requirement) -> {
            MineableDefinition definition = catalog.byId(id).orElseThrow(
                    () -> new AssertionError("Missing approved metal definition " + id));
            assertEquals(requirement, definition.requiredMining(), 0.0f, id);
            assertEquals(MineableDefinition.Category.ORE, definition.category(), id);
            assertEquals(MineableDefinition.Status.ACTIVE, definition.status(), id);
        });
    }

    @Test
    void forbiddenMaterialsAreAbsent() {
        assertTrue(catalog.byId("dull_copper").isEmpty(), "Dull Copper must not exist (Tin holds 65.0)");
        assertTrue(catalog.byId("bronze").isEmpty(),
                "Bronze must not be mineable (refined-only identity per discovery)");
    }

    @Test
    void rockBaselineIsEncoded() {
        Map<String, Float> expected = new HashMap<>();
        expected.put("stone", 0.0f);
        expected.put("cobblestone", 0.0f);
        expected.put("calcite", 5.0f);
        expected.put("diorite", 10.0f);
        expected.put("andesite", 15.0f);
        expected.put("granite", 20.0f);
        expected.put("tuff", 25.0f);
        expected.put("deepslate", 30.0f);
        expected.put("cobbled_deepslate", 30.0f);
        expected.put("basalt", 40.0f);
        expected.put("blackstone", 45.0f);
        expected.forEach((id, requirement) -> {
            MineableDefinition definition = catalog.byId(id).orElseThrow(
                    () -> new AssertionError("Missing rock definition " + id));
            assertEquals(requirement, definition.requiredMining(), 0.0f, id);
            assertEquals(MineableDefinition.Category.STONE, definition.category(), id);
        });
    }

    @Test
    void deferredBaselineEntriesDoNotResolveForGameplay() {
        for (String id : List.of("dripstone", "obsidian")) {
            MineableDefinition definition = catalog.byId(id).orElseThrow();
            assertEquals(MineableDefinition.Status.DEFERRED, definition.status(), id);
            for (String blockId : definition.blockIds()) {
                assertTrue(catalog.resolveBlock(blockId).isEmpty(),
                        blockId + " is deferred and must not resolve as ACTIVE");
                assertTrue(catalog.definitionForBlock(blockId).isPresent(),
                        blockId + " must still be catalogued");
            }
        }
        assertEquals(35.0f, catalog.byId("dripstone").orElseThrow().requiredMining(), 0.0f);
        assertEquals(60.0f, catalog.byId("obsidian").orElseThrow().requiredMining(), 0.0f);
    }

    @Test
    void activeCoverageEqualsTheDiscoveredManagedSetExactly() {
        assertEquals(DISCOVERED_MANAGED_BLOCKS, catalog.activeBlockIds().keySet());
    }

    @Test
    void everyManagedBlockResolvesExactlyOnce() {
        Map<String, String> owners = new HashMap<>();
        for (MineableDefinition definition : catalog.all()) {
            for (String blockId : definition.blockIds()) {
                String previous = owners.put(blockId, definition.id());
                assertTrue(previous == null,
                        blockId + " claimed by both " + previous + " and " + definition.id());
            }
        }
        for (String blockId : DISCOVERED_MANAGED_BLOCKS) {
            MineableDefinition resolved = catalog.resolveBlock(blockId).orElseThrow(
                    () -> new AssertionError(blockId + " does not resolve"));
            assertEquals(owners.get(blockId), resolved.id());
        }
    }

    @Test
    void nonMineableBlocksResolveNotApplicable() {
        for (String blockId : List.of("minecraft:dirt", "minecraft:oak_log", "minecraft:coal_ore",
                "minecraft:copper_ore", "britannia_mod:kettle", "minecraft:air")) {
            assertTrue(catalog.resolveBlock(blockId).isEmpty(), blockId + " must not resolve");
            assertTrue(catalog.definitionForBlock(blockId).isEmpty(), blockId + " must not be catalogued");
        }
    }

    @Test
    void activeDefinitionsPreserveCurrentRestorationScope() {
        for (MineableDefinition definition : catalog.active()) {
            assertTrue(definition.restorable(), definition.id() + " must be restorable");
        }
    }

    @Test
    void challengeInputsAreSeededWithinRange() {
        for (MineableDefinition definition : catalog.all()) {
            assertTrue(definition.challenge() >= 0.0f && definition.challenge() <= 100.0f,
                    definition.id());
            assertFalse(definition.displayName().isBlank(), definition.id());
            assertFalse(definition.dropName().isBlank(), definition.id());
        }
    }

    @Test
    void miningRequirementLocalizationKeysExist() throws Exception {
        String lang = Files.readString(LANG_JSON);
        for (String key : List.of(
                "message.britannia_mod.mining.insufficient",
                "message.britannia_mod.mining.skill_unavailable",
                "message.britannia_mod.mining.automation_blocked",
                "message.britannia_mod.mining.unresolved")) {
            assertTrue(lang.contains("\"" + key + "\""), "en_us.json is missing " + key);
        }
    }

    @Test
    void shippedCatalogAlsoLoadsFromTheClasspath() {
        MineableCatalog fromClasspath = MineableCatalog.instance();
        assertNotNull(fromClasspath);
        assertEquals(catalog.all().size(), fromClasspath.all().size());
        assertEquals(catalog.activeBlockIds().keySet(), fromClasspath.activeBlockIds().keySet());
    }
}
