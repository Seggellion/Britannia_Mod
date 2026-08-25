package com.seggellion.britannia_mod.skill.crafting;

import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.mining.MineableCatalog;
import com.seggellion.britannia_mod.mining.MineableDefinition;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.neoforge.registries.GameData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Blacksmithy material ladder: how much skill it takes to <em>work</em> each metal.
 *
 * <h2>Why this test exists</h2>
 * A Blacksmithy 0.0 smith could forge Valorite. Not because a check regressed, but because the
 * question was never asked: crafting read the recipe's requirement and then accepted whatever
 * ingot was in the offhand. The recipe half was well covered by
 * {@code BlacksmithySkillGateGameTests} and stayed green throughout, which is exactly why nobody
 * noticed the material half did not exist.
 *
 * <p>These assertions pin the ladder, prove it is total over the metal roster, and prove it fails
 * closed. The completeness test is the important one for the future: it is what stops a newly
 * added metal from silently defaulting to "anyone may work it", which is the shape the original
 * defect had.
 */
class MetalProgressionTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path CATALOG_JSON =
            PROJECT.resolve("src/main/resources/data/britannia_mod/mining/mineables.json");

    private static MineableCatalog catalog;

    /**
     * {@link UOMetalToolMaterial} builds a {@code SimpleTier} per constant, so merely naming a
     * metal needs live registries. The repository-standard bootstrap, same as the block tests.
     */
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
    }

    @BeforeAll
    static void loadShippedCatalog() throws Exception {
        catalog = MineableCatalog.parse(Files.newBufferedReader(CATALOG_JSON));
    }

    /** The ladder itself, recovered from the Mining catalogue and UO's own smithing table. */
    private static Map<UOMetalToolMaterial, Float> expectedLadder() {
        Map<UOMetalToolMaterial, Float> expected = new EnumMap<>(UOMetalToolMaterial.class);
        expected.put(UOMetalToolMaterial.IRON, 0.0f);
        expected.put(UOMetalToolMaterial.SILVER, 55.0f);
        expected.put(UOMetalToolMaterial.TIN, 65.0f);
        expected.put(UOMetalToolMaterial.SHADOW_IRON, 70.0f);
        expected.put(UOMetalToolMaterial.COPPER, 75.0f);
        expected.put(UOMetalToolMaterial.BRONZE, 80.0f);
        expected.put(UOMetalToolMaterial.GOLD, 85.0f);
        expected.put(UOMetalToolMaterial.AGAPITE, 90.0f);
        expected.put(UOMetalToolMaterial.VERITE, 95.0f);
        expected.put(UOMetalToolMaterial.VALORITE, 99.0f);
        return expected;
    }

    @Test
    void approvedMetalLadderIsEncodedExactly() {
        expectedLadder().forEach((metal, requirement) ->
                assertEquals(requirement, MetalProgression.requiredBlacksmithy(metal), 0.0f,
                        metal.name()));
    }

    /**
     * Every metal in the roster has a requirement. Without this, adding a metal to
     * {@link UOMetalToolMaterial} and forgetting the progression entry would reintroduce the exact
     * defect this ladder was written to close.
     */
    @Test
    void everyMetalInTheRosterHasARequirement() {
        for (UOMetalToolMaterial metal : UOMetalToolMaterial.values()) {
            assertNotNull(MetalProgression.all().get(metal),
                    "No Blacksmithy requirement defined for metal " + metal.name());
            assertTrue(MetalProgression.requiredBlacksmithy(metal) < MetalProgression.UNKNOWN_METAL_REQUIREMENT,
                    metal.name() + " must have a reachable requirement");
        }
        assertEquals(UOMetalToolMaterial.values().length, MetalProgression.all().size(),
                "the ladder must cover the metal roster exactly");
    }

    /**
     * The smithing requirement for every metal this project actually mines equals its Mining
     * requirement, because both ladders descend from the same UO table. Bronze is deliberately
     * absent from the Mining catalogue (it is alloyed, never mined) and is asserted separately.
     */
    @Test
    void smithingLadderStaysLevelWithTheMiningLadder() {
        Map<UOMetalToolMaterial, String> minedAs = new EnumMap<>(UOMetalToolMaterial.class);
        minedAs.put(UOMetalToolMaterial.IRON, "iron");
        minedAs.put(UOMetalToolMaterial.SILVER, "silver");
        minedAs.put(UOMetalToolMaterial.TIN, "tin");
        minedAs.put(UOMetalToolMaterial.SHADOW_IRON, "shadow_iron");
        minedAs.put(UOMetalToolMaterial.COPPER, "copper");
        minedAs.put(UOMetalToolMaterial.GOLD, "gold");
        minedAs.put(UOMetalToolMaterial.AGAPITE, "agapite");
        minedAs.put(UOMetalToolMaterial.VERITE, "verite");
        minedAs.put(UOMetalToolMaterial.VALORITE, "valorite");

        minedAs.forEach((metal, mineableId) -> {
            MineableDefinition definition = catalog.byId(mineableId).orElseThrow(
                    () -> new AssertionError("Missing mineable definition " + mineableId));
            assertEquals(definition.requiredMining(), MetalProgression.requiredBlacksmithy(metal), 0.0f,
                    metal.name() + ": smithing and mining requirements must stay level");
        });
    }

    /** The one requirement with no Mining analogue, called out so a change to it is deliberate. */
    @Test
    void bronzeIsTheDocumentedExceptionAndSitsBetweenCopperAndGold() {
        assertTrue(catalog.byId("bronze").isEmpty(), "bronze must remain un-mineable (alloyed only)");
        float bronze = MetalProgression.requiredBlacksmithy(UOMetalToolMaterial.BRONZE);
        assertEquals(80.0f, bronze, 0.0f);
        assertTrue(bronze > MetalProgression.requiredBlacksmithy(UOMetalToolMaterial.COPPER)
                        && bronze < MetalProgression.requiredBlacksmithy(UOMetalToolMaterial.GOLD),
                "bronze must sit between copper and gold, where its tier and price already place it");
    }

    /** The headline case: a novice may work iron and may not work valorite. */
    @Test
    void noviceMayWorkIronAndMayNotWorkValorite() {
        assertTrue(MetalProgression.canWork(0.0f, UOMetalToolMaterial.IRON));
        assertFalse(MetalProgression.canWork(0.0f, UOMetalToolMaterial.VALORITE));
    }

    /** Inclusive at the threshold, matching the Mining gate's {@code current >= required} rule. */
    @Test
    void theThresholdIsInclusiveForEveryMetal() {
        expectedLadder().forEach((metal, requirement) -> {
            assertTrue(MetalProgression.canWork(requirement, metal),
                    metal.name() + " must be workable exactly at its requirement");
            assertTrue(MetalProgression.canWork(requirement + 0.1f, metal),
                    metal.name() + " must be workable above its requirement");
            if (requirement > 0.0f) {
                assertFalse(MetalProgression.canWork(requirement - 0.1f, metal),
                        metal.name() + " must be refused just below its requirement");
            }
        });
    }

    /** An unrecognised metal is refused, never handed to everyone. */
    @Test
    void anUnknownMetalFailsClosed() {
        assertEquals(MetalProgression.UNKNOWN_METAL_REQUIREMENT,
                MetalProgression.requiredBlacksmithy(null), 0.0f);
        assertFalse(MetalProgression.canWork(100.0f, null),
                "an unresolved metal must be refused even to a grandmaster");
    }

    /** The ladder rises with the metal tier; no two neighbours are out of order. */
    @Test
    void theLadderIsStrictlyOrdered() {
        UOMetalToolMaterial[] ascending = {
                UOMetalToolMaterial.IRON, UOMetalToolMaterial.SILVER, UOMetalToolMaterial.TIN,
                UOMetalToolMaterial.SHADOW_IRON, UOMetalToolMaterial.COPPER, UOMetalToolMaterial.BRONZE,
                UOMetalToolMaterial.GOLD, UOMetalToolMaterial.AGAPITE, UOMetalToolMaterial.VERITE,
                UOMetalToolMaterial.VALORITE};
        for (int i = 1; i < ascending.length; i++) {
            assertTrue(MetalProgression.requiredBlacksmithy(ascending[i])
                            > MetalProgression.requiredBlacksmithy(ascending[i - 1]),
                    ascending[i].name() + " must require more than " + ascending[i - 1].name());
        }
    }
}
