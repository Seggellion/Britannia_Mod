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
     *
     * <p>OreVein milestone 11 adds {@code britannia_mod:coal_ore}. That is a deliberate widening,
     * which is why it is written here rather than discovered: coal was withdrawn in milestone 1
     * because it placed unmanaged {@code minecraft:coal_ore}, and it returns as a managed block of
     * its own. Note which id is present — the vanilla block is deliberately still absent, so legacy
     * coal in old chunks stays ordinary decorative terrain and is not retro-claimed by the economy.
     */
    private static final Set<String> DISCOVERED_MANAGED_BLOCKS = Set.of(
            "britannia_mod:coal_ore",
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
            // Milestone 6, deliberate: Dripstone activated at its approved 35.0 tier as a pure
            // data change. High-Purity Silver was retired from the managed set by owner decision
            // (one Silver metal/ore), so the discovered set is 29 blocks minus it, plus Dripstone.
            "minecraft:dripstone_block",
            // Housing supply, added deliberately: the villa needs 121 sandstone and nothing in
            // the game could produce the commodity. It is an AUTHORED block, not the vanilla
            // material -- `minecraft:sandstone` stays out, because a block-type rule over a
            // material that generates in every desert would hand world generation the villa's
            // price. See ManagedSandstoneDepositTest.
            "britannia_mod:sandstone_deposit",
            // Skill-progression remediation, added deliberately: the sediment beds joined the
            // catalogue when the owner decided every managed geological resource requires Mining.
            // Their extraction stays with ManagedDepositExtraction (shovel, configured yield);
            // only the Mining requirement lives here, read through MiningBreakGate.
            "britannia_mod:clay_deposit", "britannia_mod:silica_sand_deposit");

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

    /**
     * Owner decisions, 2026-08-14: there is exactly one Silver metal/ore, and Obsidian is not a
     * Mining resource because it is not an Ultima Online material. Both are gone from the
     * catalogue entirely, so neither can be gated, mined, awarded, or restored.
     */
    @Test
    void retiredResourcesAreAbsentFromTheCatalogue() {
        assertTrue(catalog.byId("high_purity_silver").isEmpty(),
                "only one Silver metal/ore may exist");
        assertTrue(catalog.byId("obsidian").isEmpty(),
                "Obsidian is not an Ultima Online material and is not a Mining resource");
        for (String blockId : List.of("britannia_mod:high_purity_silver_ore", "minecraft:obsidian")) {
            assertTrue(catalog.resolveBlock(blockId).isEmpty(), blockId + " must not resolve");
            assertTrue(catalog.definitionForBlock(blockId).isEmpty(), blockId + " must not be catalogued");
        }
        assertEquals(1L, catalog.all().stream()
                        .filter(definition -> definition.id().contains("silver")).count(),
                "exactly one Silver definition");
    }

    /** Dripstone proves an approved rock can be activated as a data edit, with no Java change. */
    @Test
    void dripstoneIsActiveAtItsApprovedTier() {
        MineableDefinition dripstone = catalog.byId("dripstone").orElseThrow();
        assertEquals(MineableDefinition.Status.ACTIVE, dripstone.status());
        assertEquals(35.0f, dripstone.requiredMining(), 0.0f);
        assertEquals(MineableDefinition.Category.STONE, dripstone.category());
        assertTrue(catalog.resolveBlock("minecraft:dripstone_block").isPresent());
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

    /**
     * Milestone 6 replaced {@code BlockBreakUtils}' hard-coded name chains with the catalogue's
     * {@code drop} field. These are the exact strings that chain produced; they are the identity
     * the refining chain and the economy mapping both key on, so a typo here would silently break
     * smelting or make a resource unsellable.
     */
    @Test
    void dropNamesMatchTheHistoricalBlockBreakUtilsChain() {
        Map<String, String> expected = new HashMap<>();
        expected.put("minecraft:stone", "Cobblestone");
        expected.put("minecraft:cobblestone", "Cobblestone");
        expected.put("minecraft:diorite", "Diorite");
        expected.put("minecraft:andesite", "Andesite");
        expected.put("minecraft:calcite", "Limestone");
        expected.put("minecraft:granite", "Granite");
        expected.put("minecraft:tuff", "Tuff");
        expected.put("minecraft:basalt", "Basalt");
        expected.put("minecraft:smooth_basalt", "Basalt");
        expected.put("minecraft:blackstone", "Blackrock");
        expected.put("minecraft:deepslate", "Deepslate");
        expected.put("minecraft:cobbled_deepslate", "Cobbled Deepslate");
        expected.put("britannia_mod:igneous_rock", "Igneous Rock");
        expected.put("britannia_mod:metamorphic_rock", "Metamorphic Rock");
        expected.put("britannia_mod:volcanic_rock", "Volcanic Rock");
        expected.put("britannia_mod:glacial_rock", "Glacial Rock");
        expected.put("minecraft:iron_ore", "Iron ore");
        expected.put("minecraft:deepslate_iron_ore", "Iron ore");
        expected.put("minecraft:gold_ore", "Gold ore");
        expected.put("minecraft:deepslate_gold_ore", "Gold ore");
        expected.put("britannia_mod:gold_ore", "Gold ore");
        expected.put("britannia_mod:copper_ore", "Copper ore");
        expected.put("britannia_mod:tin_ore", "Tin ore");
        expected.put("britannia_mod:silver_ore", "Silver ore");
        expected.put("britannia_mod:shadow_iron_ore", "Shadow Iron ore");
        expected.put("britannia_mod:agapite_ore", "Agapite ore");
        expected.put("britannia_mod:verite_ore", "Verite ore");
        expected.put("britannia_mod:valorite_ore", "Valorite ore");

        expected.forEach((blockId, dropName) -> assertEquals(dropName,
                catalog.resolveBlock(blockId).orElseThrow(
                        () -> new AssertionError(blockId + " no longer resolves")).dropName(),
                "drop name changed for " + blockId));
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
                "message.britannia_mod.mining.unresolved",
                "message.britannia_mod.mining.city_protected")) {
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
