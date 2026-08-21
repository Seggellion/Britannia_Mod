package com.seggellion.britannia_mod.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * What a Rails vein payload turns into, including when it is wrong.
 *
 * <p>The old fetcher answered every failure the same way: log it and return an empty list. An
 * operator running an import could not tell "Rails is down" from "this shard has no veins", and
 * both looked like a command that had quietly done nothing. Each outcome is now distinct, and the
 * parsing rules are worth driving directly because they need no server and no network.
 */
class OreVeinFetcherParseTest {

    private static final String ONE_ROW = """
            [{"ore_type":"silver","x":10,"y":-20,"z":30,"radius":7,"rotation":"XZ","region":"britain"}]""";

    @Test
    void aWellFormedPayloadParses() {
        OreVeinFetcher.FetchResult result = OreVeinFetcher.parse(ONE_ROW);

        assertTrue(result.ok());
        assertEquals(1, result.veins().size());
        OreVeinFetcher.OreVein vein = result.veins().get(0);
        assertEquals("silver", vein.oreType);
        assertEquals(10, vein.getPosition().getX());
        assertEquals(-20, vein.getPosition().getY());
        assertEquals(30, vein.getPosition().getZ());
        assertEquals(7, vein.radius);
        assertEquals("XZ", vein.rotation);
        assertEquals("britain", vein.region);
    }

    /** An empty shard is a success with nothing in it, and says so. */
    @Test
    void anEmptyPayloadIsSuccessRatherThanFailure() {
        OreVeinFetcher.FetchResult result = OreVeinFetcher.parse("[]");

        assertTrue(result.ok());
        assertTrue(result.veins().isEmpty());
        assertTrue(result.describe().contains("no curated veins"), result.describe());
    }

    /** One unusable row must not cost the operator the other rows. */
    @Test
    void oneBadRowIsSkippedAndCountedRatherThanLosingTheImport() {
        String mixed = """
                [{"ore_type":"silver","x":1,"y":2,"z":3,"radius":7,"rotation":"XZ","region":"a"},
                 {"ore_type":"tin","x":"not a number","y":2,"z":3,"radius":7,"rotation":"XZ","region":"a"},
                 {"ore_type":"gold","x":4,"y":5,"z":6,"radius":9,"rotation":"XZ","region":"b"}]""";

        OreVeinFetcher.FetchResult result = OreVeinFetcher.parse(mixed);

        assertTrue(result.ok());
        assertEquals(2, result.veins().size(), "the two readable rows must survive");
        assertTrue(result.detail().contains("1 unreadable row"), result.detail());
    }

    /** A row missing a field it needs is unreadable, not a zero-filled vein. */
    @Test
    void aRowMissingAFieldIsSkippedRatherThanDefaulted() {
        String missing = """
                [{"ore_type":"silver","x":1,"y":2,"z":3,"rotation":"XZ","region":"a"}]""";

        OreVeinFetcher.FetchResult result = OreVeinFetcher.parse(missing);

        assertTrue(result.ok());
        assertTrue(result.veins().isEmpty(), "a radius-less row must not become a radius-zero vein");
        assertTrue(result.detail().contains("1 unreadable row"), result.detail());
    }

    @Test
    void aPayloadThatIsNotAnArrayIsMalformed() {
        OreVeinFetcher.FetchResult result = OreVeinFetcher.parse("{\"veins\": []}");

        assertFalse(result.ok());
        assertEquals(OreVeinFetcher.FetchResult.Status.MALFORMED, result.status());
        assertTrue(result.describe().contains("could not be read"), result.describe());
        assertTrue(result.describe().contains("Nothing was placed"), result.describe());
    }

    @Test
    void anUnparseableBodyIsMalformedRatherThanThrowing() {
        OreVeinFetcher.FetchResult result = OreVeinFetcher.parse("<html>gateway timeout</html>");

        assertFalse(result.ok());
        assertEquals(OreVeinFetcher.FetchResult.Status.MALFORMED, result.status());
    }

    /** Every failure says what happened and that nothing was written. */
    @Test
    void everyFailureIsDistinguishableAndSaysNothingWasPlaced() {
        for (OreVeinFetcher.FetchResult.Status status : OreVeinFetcher.FetchResult.Status.values()) {
            if (status == OreVeinFetcher.FetchResult.Status.OK) {
                continue;
            }
            OreVeinFetcher.FetchResult result =
                    new OreVeinFetcher.FetchResult(status, java.util.List.of(), "detail");
            assertFalse(result.ok());
            assertTrue(result.describe().contains("Nothing was placed"),
                    status + " does not tell the operator nothing was placed: " + result.describe());
        }
    }
}
