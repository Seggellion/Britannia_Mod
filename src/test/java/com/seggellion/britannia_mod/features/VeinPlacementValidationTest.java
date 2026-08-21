package com.seggellion.britannia_mod.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.resource.shape.ShapeRotation;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Malformed curated rows are refused before they become a placement.
 *
 * <p>Written at milestone 1, when the six imperative shapes threw on values Rails was free to send
 * and something had to stop those values reaching them. Milestone 3 deleted the shapes, so these
 * are no longer crash boundaries -- they are the resource's own configured range, asked through
 * {@code PlacementPlanner} so that the command and the planner give the same answer. The cases are
 * kept, and their names still record which defect each one came from.
 */
class VeinPlacementValidationTest {

    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;

    private static Optional<String> reject(String ore, int radius) {
        return VeinPlacementValidation.reject(ore, radius, "XZ", 0, 0, 0, MIN_Y, MAX_Y);
    }

    /**
     * Gold's configured minimum is still ten.
     *
     * <p>It was ten at milestone 1 because {@code SnakeVein} computed {@code nextInt(radius - 9)}
     * and threw below it. The algorithm is gone and {@code SnakePlanner} needs only four, but gold's
     * data still says ten — data narrowing a shape's range, which it may do. This pins that the
     * row-level check reports the resource's configured minimum, not the shape's.
     */
    @Test
    void snakeRefusesEveryRadiusThatWouldThrow() {
        for (int radius : List.of(Integer.MIN_VALUE + 1, -50, -1, 0, 1, 5, 8, 9)) {
            assertTrue(reject("gold", radius).isPresent(),
                    "gold radius " + radius + " is below its configured minimum");
        }
        assertTrue(reject("gold", 10).isEmpty(), "radius 10 is the first that Snake can produce");
        assertTrue(reject("gold", 50).isEmpty(), "the shipped gold row must still place");
    }

    /**
     * The geode's radius finally means a radius.
     *
     * <p>{@code GeodeVein} scattered {@code radius * 2} lone points through a box, so agapite's
     * curated 55 was a count multiplier rather than a size. Read as an actual radius it would
     * describe a crust of about half a million cells, so agapite's configured range was narrowed to
     * a geode-sized one and a row still asking for 55 is refused by name. That refusal is the
     * containment working, not a regression -- but the curated Rails table needs the same
     * correction, which is an owner action recorded in the milestone report.
     */
    @Test
    void geodeRefusesRadiiThatAreNotGeodeSized() {
        for (int radius : List.of(-7, -1, 0, 1, 2)) {
            assertTrue(reject("agapite", radius).isPresent(),
                    "agapite radius " + radius + " is too small to be a nodule");
        }
        assertTrue(reject("agapite", 3).isEmpty(), "three is the smallest crust-and-hollow");
        assertTrue(reject("agapite", 8).isEmpty(), "the corrected agapite row must place");
        assertTrue(reject("agapite", 55).isPresent(),
                "the legacy 55 was never a radius and is now refused rather than exploded");
    }

    /** No shape means anything at a radius of zero or less, so every resource refuses one. */
    @Test
    void noShapeAcceptsAZeroOrNegativeRadius() {
        for (String ore : VeinPlacementValidation.placeableOreTypes()) {
            for (int radius : List.of(-1000, -1, 0)) {
                assertTrue(reject(ore, radius).isPresent(),
                        ore + " must refuse radius " + radius);
            }
        }
    }

    /**
     * The configured maxima bound planning work.
     *
     * <p>Cluster scans a cube, so its cell count grows with the cube of the radius and its cap is
     * the tightest; the two bedded shapes grow with the square. The caps were re-tuned at milestone
     * 3 so that no configuration can plan past {@code ShapePlan.MAX_CELLS}.
     */
    @Test
    void radiiAreCappedAndTheCubicShapeIsCappedTighter() {
        assertTrue(reject("copper", 23).isPresent(), "a cubic-cost shape must not run unbounded");
        assertTrue(reject("copper", 22).isEmpty());
        assertTrue(reject("silver", 97).isPresent());
        assertTrue(reject("silver", 96).isEmpty());
        assertTrue(reject("iron", 129).isPresent());
        assertTrue(reject("iron", 128).isEmpty());
    }

    /**
     * Every row the mod actually ships must still place, or this containment would be a silent
     * content regression rather than a safety fix.
     */
    @Test
    void everyShippedVeinRowStillPasses() {
        // agapite's row was corrected at milestone 3 from 55 to a geode-sized 8; see
        // geodeRefusesRadiiThatAreNotGeodeSized for why 55 was never a radius.
        record Row(String ore, int radius, String rotation, int x, int y, int z) {
        }
        List<Row> shipped = List.of(
                new Row("shadow_iron", 35, "XZ", 240, -50, 100),
                new Row("tin", 35, "XZ", 220, -50, 100),
                new Row("agapite", 8, "XZ", 200, -50, 100),
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
        assertTrue(VeinPlacementValidation.resourceFor("coal").isEmpty(),
                "coal must not resolve to a placeable resource");
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
     * An unrecognised rotation is refused rather than assumed.
     *
     * <p>{@code VerticalLayeredVein}'s rotation {@code switch} had no default, so a word it did not
     * know left every offset at zero and stacked the whole deposit into one cell. Rotation is an
     * enum now, so that failure has nowhere left to happen -- but a curated row can still carry a
     * word we do not know, and saying so beats guessing.
     */
    @Test
    void unrecognisedRotationsAreRefusedAndAbsentOnesDefault() {
        assertTrue(VeinPlacementValidation
                .reject("silver", 50, "sideways", 0, 0, 0, MIN_Y, MAX_Y).isPresent());
        assertTrue(VeinPlacementValidation
                .reject("silver", 50, "XQ", 0, 0, 0, MIN_Y, MAX_Y).isPresent());

        assertEquals(Optional.of(ShapeRotation.XZ), VeinPlacementValidation.normaliseRotation(null));
        assertEquals(Optional.of(ShapeRotation.XZ), VeinPlacementValidation.normaliseRotation("  "));
        assertEquals(Optional.of(ShapeRotation.ZW), VeinPlacementValidation.normaliseRotation("zw"),
                "case and padding are tolerated; unknown values are not");
        assertEquals(Optional.of(ShapeRotation.YZ), VeinPlacementValidation.normaliseRotation(" yz "));
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
