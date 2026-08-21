package com.seggellion.britannia_mod.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * OreVein milestone 1: malformed curated rows must be refused before a shape algorithm sees them.
 *
 * <p>The shapes are imperative, mutate the world as they go, and throw on several values Rails is
 * free to send. A row that throws part-way through {@code /populateores} leaves blocks already
 * written and the rest of the rows unprocessed, so the containment has to happen ahead of the
 * call rather than inside it.
 */
class VeinPlacementValidationTest {

    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    private static Optional<String> reject(String ore, int radius) {
        return VeinPlacementValidation.reject(ore, radius, "XZ", 0, 0, 0, MIN_Y, MAX_Y);
    }

    /**
     * The known crash. {@code SnakeVein} computes {@code random.nextInt(radius - 9)}, which throws
     * for every radius of 9 or below — and it does so before the full-height override that would
     * have replaced the value, so the first tendril always reaches it.
     */
    @Test
    void snakeRefusesEveryRadiusThatWouldThrow() {
        for (int radius : List.of(Integer.MIN_VALUE + 1, -50, -1, 0, 1, 5, 8, 9)) {
            assertTrue(reject("gold", radius).isPresent(),
                    "gold radius " + radius + " reaches nextInt(radius - 9) and throws");
        }
        assertTrue(reject("gold", 10).isEmpty(), "radius 10 is the first that Snake can produce");
        assertTrue(reject("gold", 50).isEmpty(), "the shipped gold row must still place");
    }

    /** {@code GeodeVein} computes {@code random.nextInt(radius / 2)}, which throws below 2. */
    @Test
    void geodeRefusesEveryRadiusThatWouldThrow() {
        for (int radius : List.of(-7, -1, 0, 1)) {
            assertTrue(reject("agapite", radius).isPresent(),
                    "agapite radius " + radius + " reaches nextInt(radius / 2) and throws");
        }
        assertTrue(reject("agapite", 2).isEmpty(), "radius 2 is the first Geode can produce");
        assertTrue(reject("agapite", 55).isEmpty(), "the shipped agapite row must still place");
    }

    /**
     * The remaining shapes all reach {@code nextInt} on a radius-derived bound, or divide by the
     * radius, so zero and negative values are refused for every one of them.
     */
    @Test
    void noShapeAcceptsAZeroOrNegativeRadius() {
        for (String ore : VeinPlacementValidation.placeableOreTypes()) {
            for (int radius : List.of(-1000, -1, 0)) {
                assertTrue(reject(ore, radius).isPresent(),
                        ore + " must refuse radius " + radius);
            }
        }
    }

    /** An unbounded radius is unbounded work; Cluster is cubic and capped tighter than the rest. */
    @Test
    void radiiAreCappedAndClusterIsCappedTighter() {
        assertTrue(reject("copper", 33).isPresent(), "a cubic-cost shape must not run unbounded");
        assertTrue(reject("copper", 32).isEmpty());
        assertTrue(reject("iron", 129).isPresent());
        assertTrue(reject("iron", 128).isEmpty());
    }

    /**
     * Every row the mod actually ships must still place, or this containment would be a silent
     * content regression rather than a safety fix.
     */
    @Test
    void everyShippedVeinRowStillPasses() {
        record Row(String ore, int radius, String rotation, int x, int y, int z) {
        }
        List<Row> shipped = List.of(
                new Row("shadow_iron", 35, "XZ", 240, -50, 100),
                new Row("tin", 35, "XZ", 220, -50, 100),
                new Row("agapite", 55, "XZ", 200, -50, 100),
                new Row("copper", 15, "XZ", 180, -50, 100),
                new Row("iron", 90, "XZ", 160, -50, 100),
                new Row("gold", 50, "XZ", 140, -50, 100),
                new Row("silver", 50, "ZW", 120, -50, 100),
                new Row("verite", 20, "XZ", 40, -50, 100),
                new Row("valorite", 50, "XZ", 20, -50, 100));
        for (Row row : shipped) {
            assertEquals(Optional.empty(),
                    VeinPlacementValidation.reject(row.ore(), row.radius(), row.rotation(),
                            row.x(), row.y(), row.z(), MIN_Y, MAX_Y),
                    row.ore() + " is a shipped row and must still be placeable");
        }
    }

    /**
     * Coal is withdrawn from this route. It placed {@code minecraft:coal_ore}, which the Mining
     * catalogue does not govern, so it broke with vanilla drops and scheduled no restoration —
     * a managed generation path producing unmanaged economic material.
     */
    @Test
    void coalIsNoLongerPlaceableThroughTheLegacyRoute() {
        assertTrue(VeinPlacementValidation.shapeFor("coal").isEmpty(),
                "coal must not resolve to a shape");
        assertFalse(VeinPlacementValidation.placeableOreTypes().contains("coal"),
                "coal must not be offered as placeable");
        assertTrue(reject("coal", 50).isPresent(), "the shipped coal row must now be skipped");
    }

    /** The nine types that never had a shape branch are gone rather than silently placing nothing. */
    @Test
    void typesWithNoShapeAreRejectedRatherThanPlacingNothing() {
        for (String ore : List.of("diamond", "deepslate_diamond", "redstone", "deepslate_redstone",
                "vanilla_copper", "emerald", "deepslate_emerald", "lapis", "deepslate_lapis")) {
            assertTrue(reject(ore, 20).isPresent(), ore + " has no shape and must be refused");
        }
    }

    @Test
    void unknownAndMalformedTypesAreRefused() {
        assertTrue(reject(null, 20).isPresent());
        assertTrue(reject("", 20).isPresent());
        assertTrue(reject("not_an_ore", 20).isPresent());
    }

    /**
     * {@code VerticalLayeredVein}'s rotation switch has no default branch, so an unrecognised
     * rotation leaves every offset at zero and stacks the whole deposit into one cell.
     */
    @Test
    void unrecognisedRotationsAreRefusedAndAbsentOnesDefault() {
        assertTrue(VeinPlacementValidation
                .reject("silver", 50, "sideways", 0, 0, 0, MIN_Y, MAX_Y).isPresent());
        assertTrue(VeinPlacementValidation
                .reject("silver", 50, "XQ", 0, 0, 0, MIN_Y, MAX_Y).isPresent());

        assertEquals(Optional.of("XZ"), VeinPlacementValidation.normaliseRotation(null));
        assertEquals(Optional.of("XZ"), VeinPlacementValidation.normaliseRotation("  "));
        assertEquals(Optional.of("ZW"), VeinPlacementValidation.normaliseRotation("zw"),
                "case and padding are tolerated; unknown values are not");
        assertEquals(Optional.of("YZ"), VeinPlacementValidation.normaliseRotation(" yz "));
        assertEquals(Optional.empty(), VeinPlacementValidation.normaliseRotation("nonsense"));
    }

    @Test
    void positionsOutsideTheWorldAreRefused() {
        assertTrue(VeinPlacementValidation
                .reject("iron", 20, "XZ", 0, MIN_Y - 1, 0, MIN_Y, MAX_Y).isPresent());
        assertTrue(VeinPlacementValidation
                .reject("iron", 20, "XZ", 0, MAX_Y + 1, 0, MIN_Y, MAX_Y).isPresent());
        assertTrue(VeinPlacementValidation
                .reject("iron", 20, "XZ", VeinPlacementValidation.MAX_HORIZONTAL + 1, 0, 0,
                        MIN_Y, MAX_Y).isPresent());
        assertTrue(VeinPlacementValidation
                .reject("iron", 20, "XZ", 0, 0, -VeinPlacementValidation.MAX_HORIZONTAL - 1,
                        MIN_Y, MAX_Y).isPresent());
        assertTrue(VeinPlacementValidation
                .reject("iron", 20, "XZ", 0, MIN_Y, 0, MIN_Y, MAX_Y).isEmpty(),
                "the build-height boundary itself is inclusive");
    }

    /** A rejection has to say why, because the operator sees it as a skipped row. */
    @Test
    void everyRejectionCarriesAReason() {
        for (Optional<String> rejection : List.of(
                reject("coal", 50), reject("gold", 3), reject("agapite", 1), reject("copper", 0))) {
            assertTrue(rejection.isPresent());
            assertFalse(rejection.get().isBlank(), "a skipped row must explain itself");
        }
    }
}
