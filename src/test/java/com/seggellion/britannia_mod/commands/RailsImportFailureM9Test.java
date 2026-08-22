package com.seggellion.britannia_mod.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.features.VeinPlacementValidation;
import com.seggellion.britannia_mod.util.OreVeinFetcher;

import org.junit.jupiter.api.Test;

import java.util.Optional;

/**
 * Milestone 9: the Rails importer under the conditions that actually happen.
 *
 * <h2>What is being tested, and what cannot be</h2>
 * The network call itself is not exercised — there is no Rails instance in a unit test, and
 * pretending otherwise would be worse than saying so. What is exercised is everything the importer
 * does with what it gets back, which is where the old implementation was weakest: it answered every
 * failure with an empty list, so an operator could not tell a broken shard from an empty one.
 *
 * <p>Row validation is driven through {@code VeinPlacementValidation}, the same code the command
 * calls before it plans anything, including the legacy agapite radius of 55 that the live Rails
 * table still carries.
 */
class RailsImportFailureM9Test {

    private static final String GOOD_ROW =
            "{\"ore_type\":\"silver\",\"x\":10,\"y\":-20,\"z\":30,\"radius\":7,"
                    + "\"rotation\":\"XZ\",\"region\":\"britain\"}";

    /* ------------------------------------------------------------------ */
    /*  Payload handling                                                   */
    /* ------------------------------------------------------------------ */

    @Test
    void aSuccessfulPayloadParsesEveryRow() {
        OreVeinFetcher.FetchResult result =
                OreVeinFetcher.parse("[" + GOOD_ROW + "," + GOOD_ROW + "," + GOOD_ROW + "]");
        assertTrue(result.ok());
        assertEquals(3, result.veins().size());
    }

    /**
     * The same payload twice produces the same rows, so a repeated import is a repeated no-op.
     *
     * <p>Idempotence at the ledger and world level is proven in
     * {@code OreVeinDiagnosticsGameTests.repeatingAnImportIsIdempotent}; this is the parsing half of
     * the same claim.
     */
    @Test
    void anIdenticalPayloadParsesIdentically() {
        OreVeinFetcher.FetchResult first = OreVeinFetcher.parse("[" + GOOD_ROW + "]");
        OreVeinFetcher.FetchResult second = OreVeinFetcher.parse("[" + GOOD_ROW + "]");

        assertEquals(first.veins().size(), second.veins().size());
        assertEquals(first.veins().get(0).getPosition(), second.veins().get(0).getPosition());
        assertEquals(first.veins().get(0).radius, second.veins().get(0).radius);
        assertEquals(first.veins().get(0).oreType, second.veins().get(0).oreType);
    }

    /** A duplicated row parses twice; the ledger is what deduplicates it, and does. */
    @Test
    void aDuplicatedRowIsParsedTwiceAndLeftForTheLedgerToRefuse() {
        OreVeinFetcher.FetchResult result = OreVeinFetcher.parse("[" + GOOD_ROW + "," + GOOD_ROW + "]");
        assertEquals(2, result.veins().size(),
                "the parser must not silently deduplicate; identity is the ledger's decision");
    }

    @Test
    void malformedCoordinatesLoseTheirRowAndNothingElse() {
        String malformed = "{\"ore_type\":\"silver\",\"x\":\"east a bit\",\"y\":-20,\"z\":30,"
                + "\"radius\":7,\"rotation\":\"XZ\",\"region\":\"britain\"}";
        OreVeinFetcher.FetchResult result =
                OreVeinFetcher.parse("[" + GOOD_ROW + "," + malformed + "," + GOOD_ROW + "]");

        assertTrue(result.ok());
        assertEquals(2, result.veins().size(), "the readable rows must survive");
        assertTrue(result.detail().contains("1 unreadable row"), result.detail());
    }

    /** A transport failure is reported as one, not as an empty shard. */
    @Test
    void everyTransportFailureIsDistinguishableFromAnEmptyShard() {
        OreVeinFetcher.FetchResult empty = OreVeinFetcher.parse("[]");
        assertTrue(empty.ok());
        assertTrue(empty.describe().contains("no curated veins"));

        for (OreVeinFetcher.FetchResult.Status status : OreVeinFetcher.FetchResult.Status.values()) {
            if (status == OreVeinFetcher.FetchResult.Status.OK) {
                continue;
            }
            OreVeinFetcher.FetchResult failure =
                    new OreVeinFetcher.FetchResult(status, java.util.List.of(), "timeout");
            assertFalse(failure.ok(), status + " reported itself as success");
            assertFalse(failure.describe().equals(empty.describe()),
                    status + " is indistinguishable from an empty shard");
            assertTrue(failure.describe().contains("Nothing was placed"),
                    status + " does not say nothing was placed");
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Row validation, before anything is planned                         */
    /* ------------------------------------------------------------------ */

    @Test
    void anUnknownResourceIsRejectedBeforePlanning() {
        assertTrue(VeinPlacementValidation.resourceFor("mithril").isEmpty(),
                "an unplaceable ore type resolved to a resource");
        // Coal used to be the second example here, because it had no definition. Milestone 11 gave
        // it one, so it now demonstrates the opposite: a row whose type the catalogue knows.
        assertTrue(VeinPlacementValidation.resourceFor("coal").isPresent(),
                "coal has a canonical resource definition since milestone 11");
        assertTrue(VeinPlacementValidation.resourceFor("silver").isPresent(),
                "silver should still be placeable");
    }

    /**
     * The legacy agapite radius the live Rails table still carries is refused.
     *
     * <p>This is the row the programme has been carrying as external debt since milestone 3: the
     * geode's valid range is 3–16 and the live row says 55. The importer refuses it with a message
     * naming the bound, rather than planning a geode fifty-five blocks across.
     */
    @Test
    void theLegacyAgapiteRadiusOfFiftyFiveIsRefusedWithAnActionableMessage() {
        Optional<String> rejection = VeinPlacementValidation.reject(
                "agapite", 55, "XZ", 100, -40, 100, -64, 320);

        assertTrue(rejection.isPresent(), "radius 55 was accepted for an agapite geode");
        String message = rejection.orElseThrow();
        assertTrue(message.contains("55"), "the refusal does not name the offending radius: " + message);

        // And the approved provisional radius is accepted.
        assertTrue(VeinPlacementValidation.reject("agapite", 8, "XZ", 100, -40, 100, -64, 320)
                .isEmpty(), "the approved provisional radius of 8 was refused");
    }

    @Test
    void aRadiusBelowAShapesMinimumIsRefused() {
        assertTrue(VeinPlacementValidation.reject("agapite", 1, "XZ", 0, -40, 0, -64, 320).isPresent(),
                "a radius below the geode minimum was accepted");
    }

    /** A row that would fall outside the world's build range is refused rather than clipped. */
    @Test
    void aRowOutsideTheBuildRangeIsRefused() {
        assertTrue(VeinPlacementValidation.reject("silver", 8, "XZ", 0, 5_000, 0, -64, 320).isPresent(),
                "a vein above the build ceiling was accepted");
        assertTrue(VeinPlacementValidation.reject("silver", 8, "XZ", 0, -5_000, 0, -64, 320).isPresent(),
                "a vein below the build floor was accepted");
    }
}
