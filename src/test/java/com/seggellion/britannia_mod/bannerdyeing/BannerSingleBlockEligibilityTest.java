package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.data.BannerDimensions;
import com.seggellion.britannia_mod.banner.placement.BannerSingleBlockEligibility;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BannerSingleBlockEligibilityTest {
    private static final Set<String> EXPECTED = Set.of(
            "silver_and_gold_pennon", "end_01", "end_02", "pennon_of_silver", "iron_ward",
            "iron_ward_auxiliary", "road_guard", "pale_road_guard", "red_crosslets",
            "captains_red_crosslets", "scarlet_court", "verdant_court", "x_small_unnamed_01");
    private static RegistrySnapshot production;

    @BeforeAll
    static void setup() throws Exception {
        production = DyeResolverFixtures.productionSnapshot();
    }

    @Test
    void canonicalCatalogueHasExactlyThirteenEligibleAndTwentyDeferred() {
        List<BannerDefinition> definitions = production.banners().activeDefinitions();
        long eligible = definitions.stream().filter(BannerSingleBlockEligibility::isEligible).count();
        assertEquals(33, definitions.size());
        assertEquals(13, eligible);
        assertEquals(20, definitions.size() - eligible);
        assertEquals(EXPECTED, definitions.stream().filter(BannerSingleBlockEligibility::isEligible)
                .map(definition -> definition.id().value().getPath()).collect(Collectors.toSet()));
    }

    @Test
    void everyExactOneByOneDefinitionIsEligibleAndEveryOtherDefinitionIsDeferred() {
        for (BannerDefinition definition : production.banners().activeDefinitions()) {
            boolean oneByOne = definition.dimensions().widthBlocks() == 1
                    && definition.dimensions().heightBlocks() == 1;
            assertEquals(oneByOne, BannerSingleBlockEligibility.isEligible(definition), definition.id().toString());
        }
    }

    @Test
    void widthOrHeightGreaterThanOneIsDeferred() {
        BannerDefinition base = production.banners().activeDefinitions().stream()
                .filter(BannerSingleBlockEligibility::isEligible).findFirst().orElseThrow();
        assertEquals(BannerSingleBlockEligibility.Result.MULTI_BLOCK_DEFERRED,
                BannerSingleBlockEligibility.evaluate(copy(base, "renamed", new BannerDimensions(2, 1, true),
                        base.supportedOrientations(), base.contentStatus())));
        assertEquals(BannerSingleBlockEligibility.Result.MULTI_BLOCK_DEFERRED,
                BannerSingleBlockEligibility.evaluate(copy(base, "renamed", new BannerDimensions(1, 2, true),
                        base.supportedOrientations(), base.contentStatus())));
    }

    @Test
    void unsupportedWallParallelOrientationWinsOverDimensions() {
        BannerDefinition base = production.banners().activeDefinitions().stream()
                .filter(BannerSingleBlockEligibility::isEligible).findFirst().orElseThrow();
        BannerDefinition perpendicular = copy(base, base.catalogueGroup(), base.dimensions(),
                List.of(BannerOrientation.WALL_PERPENDICULAR), base.contentStatus());
        assertEquals(BannerSingleBlockEligibility.Result.WALL_PARALLEL_UNSUPPORTED,
                BannerSingleBlockEligibility.evaluate(perpendicular));
    }

    @Test
    void groupNameDoesNotAffectEligibilityButDimensionsDo() {
        BannerDefinition base = production.banners().activeDefinitions().stream()
                .filter(BannerSingleBlockEligibility::isEligible).findFirst().orElseThrow();
        assertTrue(BannerSingleBlockEligibility.isEligible(copy(
                base, "large", base.dimensions(), base.supportedOrientations(), base.contentStatus())));
        assertFalse(BannerSingleBlockEligibility.isEligible(copy(
                base, "small", new BannerDimensions(3, 2, true), base.supportedOrientations(), base.contentStatus())));
    }

    @Test
    void provisionalAndPlaceholderContentRemainsEligible() {
        BannerDefinition base = production.banners().activeDefinitions().stream()
                .filter(BannerSingleBlockEligibility::isEligible).findFirst().orElseThrow();
        BannerDefinition placeholder = copy(base, base.catalogueGroup(),
                new BannerDimensions(1, 1, true), base.supportedOrientations(), BannerContentStatus.PLACEHOLDER);
        assertTrue(BannerSingleBlockEligibility.isEligible(placeholder));
    }

    private static BannerDefinition copy(
            BannerDefinition source, String group, BannerDimensions dimensions,
            List<BannerOrientation> orientations, BannerContentStatus status) {
        return new BannerDefinition(source.schemaVersion(), source.id(), source.displayNameKey(), status,
                source.sourceReference(), group, dimensions, orientations, source.supportedMounts(),
                source.defaultMount(), source.defaultMaterial(), source.assets(), source.placementProfile());
    }
}
