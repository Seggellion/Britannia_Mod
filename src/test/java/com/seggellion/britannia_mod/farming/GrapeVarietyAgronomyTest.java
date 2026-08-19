package com.seggellion.britannia_mod.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.item.GrapesItem;
import com.seggellion.britannia_mod.winery.GrapeColor;
import com.seggellion.britannia_mod.winery.GrapeVariety;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The shard publishes hundreds of grape varieties, each with its own soil chemistry, hydration,
 * altitude band and climate. Those numbers only mean something if the crop is actually judged
 * against them; otherwise every variety grows identically and the catalogue is decoration.
 *
 * <p>The real Pinot Noir record is used as the worked example so the mapping from the Rails payload
 * to what the plot enforces is checked end to end rather than against invented values.
 */
class GrapeVarietyAgronomyTest {

    /** The shipped Rails record for pinot_noir, field for field. */
    private static final GrapeVariety PINOT_NOIR = new GrapeVariety(
            "pinot_noir", "Pinot Noir", 3,
            0.26f, 0.12f, 0.16f, 0.5f,
            "Warm", 150, 578,
            0x6e3c63, 9, GrapeColor.PURPLE);

    /** A deliberately different record, so "the variety was applied" cannot pass by coincidence. */
    private static final GrapeVariety HIGHLAND_TABLE = new GrapeVariety(
            "highland_table", "Highland Table", 5,
            0.80f, 0.70f, 0.60f, 0.20f,
            "Tropical", 4, 96,
            0x88bb44, 2, GrapeColor.GREEN);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        List<GrapeVariety> catalogue = new ArrayList<>(List.of(PINOT_NOIR, HIGHLAND_TABLE));
        // Stand in for a full shard catalogue, to prove nothing here scales with variety count.
        for (int index = 0; index < 400; index++) {
            catalogue.add(new GrapeVariety(
                    "bulk_variety_" + index, "Bulk " + index, index % 6,
                    0.3f, 0.3f, 0.3f, 0.3f,
                    "Temperate", 60, 120,
                    0x101010, 1, GrapeColor.values()[index % GrapeColor.values().length]));
        }
        GrapeVarietyManager.loadFromBootstrap(catalogue);
    }

    @Test
    void aPlantedVarietyReplacesTheCropWideAgronomy() {
        CropDefinition effective = GrapeVarietyAgronomy.effectiveCrop(grapeCrop(), "pinot_noir");

        assertEquals(0.26f, effective.idealBoneMeal(), 1.0e-6, "nitrogen target");
        assertEquals(0.12f, effective.idealTurquoise(), 1.0e-6, "phosphorus target");
        assertEquals(0.16f, effective.idealSulphurousAsh(), 1.0e-6, "potassium target");
        assertEquals(0.5f, effective.idealRottenFlesh(), 1.0e-6, "organic matter target");
        // Varieties record hydration on the plot's 0..5 scale; crops carry it as a fraction.
        assertEquals(3.0f / 5.0f, effective.hydrationIdeal(), 1.0e-6);
        assertEquals(150, effective.minAltitude());
        assertEquals(578, effective.maxAltitude());
    }

    @Test
    void aVarietysAltitudeBandDecidesWhereItWillGrow() {
        CropDefinition pinot = GrapeVarietyAgronomy.effectiveCrop(grapeCrop(), "pinot_noir");
        assertFalse(pinot.canGrowAtAltitude(new BlockPos(0, 64, 0)), "Pinot Noir must refuse the lowlands");
        assertTrue(pinot.canGrowAtAltitude(new BlockPos(0, 200, 0)));
        assertFalse(pinot.canGrowAtAltitude(new BlockPos(0, 600, 0)));

        CropDefinition highland = GrapeVarietyAgronomy.effectiveCrop(grapeCrop(), "highland_table");
        assertTrue(highland.canGrowAtAltitude(new BlockPos(0, 64, 0)),
            "a different variety must reach a different verdict at the same altitude");
        assertFalse(highland.canGrowAtAltitude(new BlockPos(0, 200, 0)));
    }

    @Test
    void aVarietysClimateIsHonouredAndAnUnknownClimateStaysTemperate() {
        // "Warm" is not one of the climates the world models, so it resolves to Temperate rather
        // than to nothing, which would leave the variety unable to grow anywhere.
        CropDefinition pinot = GrapeVarietyAgronomy.effectiveCrop(grapeCrop(), "pinot_noir");
        assertTrue(pinot.canGrowInClimate(FarmingClimate.TEMPERATE));
        assertFalse(pinot.canGrowInClimate(FarmingClimate.TROPICAL));

        CropDefinition highland = GrapeVarietyAgronomy.effectiveCrop(grapeCrop(), "highland_table");
        assertTrue(highland.canGrowInClimate(FarmingClimate.TROPICAL));
        assertFalse(highland.canGrowInClimate(FarmingClimate.TEMPERATE));
    }

    @Test
    void everythingThatIsNotTheVarietysBusinessSurvivesUntouched() {
        CropDefinition grapes = grapeCrop();
        CropDefinition effective = GrapeVarietyAgronomy.effectiveCrop(grapes, "pinot_noir");

        assertNotSame(grapes, effective);
        assertEquals(grapes.id(), effective.id());
        assertEquals(grapes.harvestTool(), effective.harvestTool());
        assertEquals(grapes.tallCrop(), effective.tallCrop());
        assertEquals(grapes.maxHeight(), effective.maxHeight());
        assertEquals(grapes.baseGrowthTicks(), effective.baseGrowthTicks());
        assertEquals(grapes.minYield(), effective.minYield());
        assertEquals(grapes.maxYield(), effective.maxYield());
        assertEquals(grapes.lifecycle(), effective.lifecycle());
        assertEquals(grapes.growthHabit(), effective.growthHabit());
        assertEquals(grapes.clampedPostHarvestRegrowthAge(), effective.clampedPostHarvestRegrowthAge());
    }

    @Test
    void nonGrapeCropsAndUnknownVarietiesPassStraightThrough() {
        CropDefinition wheat = CropRegistry.byId("wheat").orElseThrow();
        assertSame(wheat, GrapeVarietyAgronomy.effectiveCrop(wheat, "pinot_noir"));

        CropDefinition grapes = grapeCrop();
        assertSame(grapes, GrapeVarietyAgronomy.effectiveCrop(grapes, ""));
        assertSame(grapes, GrapeVarietyAgronomy.effectiveCrop(grapes, (String) null));
        // An id the shard has stopped publishing must not invent requirements out of thin air.
        assertSame(grapes, GrapeVarietyAgronomy.effectiveCrop(grapes, "a_variety_that_no_longer_exists"));
    }

    @Test
    void theWholeCatalogueResolvesWhateverItsSize() {
        assertTrue(GrapeVarietyManager.getAllVarieties().size() > 400,
            "this test is meaningless without a catalogue of realistic size");
        for (GrapeVariety variety : GrapeVarietyManager.getAllVarieties()) {
            CropDefinition effective = GrapeVarietyAgronomy.effectiveCrop(grapeCrop(), variety.id());
            assertEquals(variety.minAltitude(), effective.minAltitude(), variety.id());
            assertEquals(variety.maxAltitude(), effective.maxAltitude(), variety.id());
            assertTrue(effective.hydrationIdeal() >= 0.0f && effective.hydrationIdeal() <= 1.0f,
                variety.id() + " produced an out-of-range hydration target");
            assertEquals(variety.colorType(),
                GrapeVisualResolver.resolve(effective, 7, variety.id()).color(),
                variety.id() + " lost its colour");
        }
    }

    /**
     * {@code withGrapeVariety} restates every component positionally, which is the one way this can
     * break silently: add a component and the copy keeps compiling while quietly shifting values
     * into the wrong fields.
     */
    @Test
    void addingACropComponentMustSendYouBackToTheVarietyCopy() {
        assertEquals(49, CropDefinition.class.getRecordComponents().length,
            "CropDefinition gained or lost a component - revisit CropDefinition.withGrapeVariety, "
                + "which lists every component by position, then update this count");
    }

    /**
     * With no shard data the game must still have grapes, and the fallback must be a variety the
     * rest of the item behaves consistently towards - including being edible fresh, which the
     * Concord check previously missed because the catalogue spells Concord as coloured slugs.
     */
    @Test
    void theBuiltInFallbackIsConcordAndBehavesLikeConcordEverywhere() {
        assertEquals("concord_green", GrapesItem.DEFAULT_VARIETY_ID);

        GrapeVariety fallback = GrapeVarietyManager.getVariety("a_variety_that_no_longer_exists");
        assertEquals("concord_green", fallback.id(), "an unknown variety must fall back to Concord");
        assertEquals(GrapeColor.GREEN, fallback.colorType());

        assertTrue(GrapesItem.isConcordVariety("concord_green"));
        assertTrue(GrapesItem.isConcordVariety("concord_red"));
        assertTrue(GrapesItem.isConcordVariety("concord"));
        assertFalse(GrapesItem.isConcordVariety("pinot_noir"));
        assertFalse(GrapesItem.isConcordVariety("concordia_blanc"),
            "a variety that merely begins with those letters is not a Concord");
    }

    /** Both Concords survive a shard payload that omits them, so green and red are always available. */
    @Test
    void bothConcordsRemainAvailableEvenWhenTheShardOmitsThem() {
        assertEquals(GrapeColor.GREEN, GrapeVarietyManager.getVariety("concord_green").colorType());
        assertEquals(GrapeColor.RED, GrapeVarietyManager.getVariety("concord_red").colorType());
        assertEquals(25, GrapeVarietyAgronomy.effectiveCrop(grapeCrop(), "concord_red").minAltitude());
        assertEquals(625, GrapeVarietyAgronomy.effectiveCrop(grapeCrop(), "concord_red").maxAltitude());
    }

    private static CropDefinition grapeCrop() {
        return CropRegistry.byId("grapes").orElseThrow();
    }
}
