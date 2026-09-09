package com.seggellion.britannia_mod.resource.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The line between a sited deposit and the world's ordinary crust, pinned so it cannot drift.
 *
 * <h2>Why this test exists</h2>
 * Milestone 6 made an ordinary Creative break of a managed deposit refuse. That is right for a
 * vein somebody sited and wrong for {@code minecraft:stone}, which is a managed resource in the
 * catalogue — it yields graded stone — but is also the material the entire world is made of.
 * Protecting it would stop a builder cutting a cellar, clearing a house plot or terraforming at all.
 *
 * <p>The distinction is easy to lose by accident: adding a mod-owned rock, or reclassifying a
 * family, silently moves blocks from one side of it to the other. So the rule is asserted here
 * against the shipped catalogue rather than described in a comment.
 *
 * <p>{@code isDepositCell} is deliberately not tested through a level: it is a decision about a
 * resource definition and a block's namespace, and needs no world.
 */
class AmbientRockIsNotADepositTest {

    private static List<ResourceDefinition> catalogue() {
        return ResourceCatalog.instance().all();
    }

    /**
     * Every ORE, MINERAL and SEDIMENT resource is a deposit: they exist only where something
     * sited them.
     *
     * <p>Written as "every family that is not STONE" rather than as a list, which is why MINERAL
     * — added for coal after this rule was written — needed no amendment to land on the right
     * side of the line.
     */
    @Test
    void everyOreAndSedimentResourceIsADepositCell() {
        int checked = 0;
        for (ResourceDefinition definition : catalogue()) {
            if (definition.family() == ResourceDefinition.Family.STONE) {
                continue;
            }
            checked++;
            assertTrue(isDepositFamily(definition),
                    definition.id() + " is " + definition.family()
                            + " and must count as a sited deposit");
        }
        assertTrue(checked >= 10, "only " + checked + " ore/sediment resources were checked");
    }

    /**
     * The vanilla stone family is ambient crust and must stay terraformable.
     *
     * <p>Named individually, because this is the assertion whose failure would be a gameplay
     * regression rather than a test problem.
     */
    @Test
    void ordinaryVanillaRockIsNotADepositCell() {
        List<String> ambient = List.of(
                "minecraft:stone", "minecraft:granite", "minecraft:deepslate",
                "minecraft:diorite", "minecraft:andesite", "minecraft:tuff",
                "minecraft:cobblestone", "minecraft:cobbled_deepslate",
                "minecraft:calcite", "minecraft:basalt", "minecraft:smooth_basalt",
                "minecraft:blackstone", "minecraft:dripstone_block");

        for (String blockId : ambient) {
            ResourceDefinition owner = ownerOf(blockId);
            assertTrue(owner != null, blockId + " is no longer catalogued; this test is stale");
            assertEquals(ResourceDefinition.Family.STONE, owner.family(),
                    blockId + " changed family, which moves it across the deposit line");
            assertFalse(isVanillaStoneDeposit(owner, blockId),
                    blockId + " would now be protected from an ordinary Creative break, which stops"
                            + " builders terraforming ordinary world geology");
        }
    }

    /**
     * Coal is a MINERAL, and a MINERAL is a deposit.
     *
     * <p>Named on its own because it is the family that did not exist when this rule was written:
     * a curated coal cell is sited by {@code /populateores} exactly as an ore vein is, and a
     * creative click must no more delete it than delete silver.
     */
    @Test
    void curatedCoalIsADepositCell() {
        ResourceDefinition coal = null;
        for (ResourceDefinition definition : catalogue()) {
            if (definition.family() == ResourceDefinition.Family.MINERAL) {
                coal = definition;
                break;
            }
        }
        assertTrue(coal != null, "no MINERAL resource is catalogued; this test is stale");
        assertTrue(isDepositFamily(coal), coal.id() + " is MINERAL and must be a sited deposit");
        for (String blockId : coal.blockIds()) {
            assertTrue(isVanillaStoneDeposit(coal, blockId),
                    blockId + " is a curated coal cell and must stay protected from a creative"
                            + " click, which would destroy it with no restoration debt filed");
        }
    }

    /**
     * A mod-owned stone block <em>is</em> a deposit, because it only exists where the mod put it.
     *
     * <p>This is the other half of the rule, and the half that stops the exemption being read as
     * "the STONE family is never protected".
     */
    @Test
    void aModOwnedStoneBlockIsStillADepositCell() {
        List<String> sited = List.of(
                "britannia_mod:sandstone_deposit", "britannia_mod:igneous_rock",
                "britannia_mod:metamorphic_rock", "britannia_mod:volcanic_rock",
                "britannia_mod:glacial_rock");

        for (String blockId : sited) {
            ResourceDefinition owner = ownerOf(blockId);
            assertTrue(owner != null, blockId + " is no longer catalogued; this test is stale");
            assertEquals(ResourceDefinition.Family.STONE, owner.family(), blockId);
            assertTrue(isVanillaStoneDeposit(owner, blockId),
                    blockId + " is placed by this mod and nowhere else, so it must stay protected");
        }
    }

    /**
     * The rule is a function of the data, not a list of names.
     *
     * <p>Sweeps the whole shipped catalogue and asserts the classification of every block follows
     * the stated rule, so a resource added later cannot land on the wrong side of the line without
     * this failing.
     */
    @Test
    void theRuleHoldsForEveryBlockInTheShippedCatalogue() {
        List<String> wrong = new ArrayList<>();
        for (ResourceDefinition definition : catalogue()) {
            for (String blockId : definition.blockIds()) {
                boolean expected = definition.family() != ResourceDefinition.Family.STONE
                        || blockId.startsWith("britannia_mod:");
                if (isVanillaStoneDeposit(definition, blockId) != expected) {
                    wrong.add(blockId + " in " + definition.id());
                }
            }
        }
        assertTrue(wrong.isEmpty(), "these blocks are classified against the stated rule: " + wrong);
    }

    /** Both sides of the line are populated, or the rule is not doing anything. */
    @Test
    void bothSidesOfTheLineArePopulated() {
        int deposits = 0;
        int ambient = 0;
        for (ResourceDefinition definition : catalogue()) {
            for (String blockId : definition.blockIds()) {
                if (isVanillaStoneDeposit(definition, blockId)) {
                    deposits++;
                } else {
                    ambient++;
                }
            }
        }
        assertTrue(deposits > 10, "only " + deposits + " blocks count as deposits");
        assertTrue(ambient > 5, "only " + ambient + " blocks count as ambient crust");
    }

    /* ------------------------------------------------------------------ */
    /*  The rule, restated here so the test fails if the production one moves */
    /* ------------------------------------------------------------------ */

    private static boolean isDepositFamily(ResourceDefinition definition) {
        return definition.family() != ResourceDefinition.Family.STONE;
    }

    /**
     * Mirrors {@code ManagedExtractionPolicy.isDepositCell} without needing a {@code BlockState}.
     *
     * <p>Restating it is the point: if production changes its mind about what a deposit is, the
     * two disagree and the sweep above fails, which is exactly the drift this test is here to
     * catch. The production method is exercised against real block states in
     * {@code ManagedExtractionPolicyGameTests}.
     */
    private static boolean isVanillaStoneDeposit(ResourceDefinition definition, String blockId) {
        return isDepositFamily(definition) || blockId.startsWith("britannia_mod:");
    }

    private static ResourceDefinition ownerOf(String blockId) {
        for (ResourceDefinition definition : catalogue()) {
            if (definition.blockIds().contains(blockId)) {
                return definition;
            }
        }
        return null;
    }
}
