package com.seggellion.britannia_mod.service.banking;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Milestone 18: reading item identity out of {@code banking/open}.
 *
 * <p>The governing property is that identity is optional in both directions. Rails omits a key
 * entirely when it has no value for it, so an item deposited before identity existed arrives with
 * none -- and will keep arriving that way forever, because a shard running a pre-Milestone-17
 * client keeps producing more of them. None of that may weaken the response as a whole: a bad or
 * missing name costs the name, never the player's account view.
 */
class BankingOpenResponseParserIdentityTest {

    @Test
    void readsAllThreeWhenRailsSendsThem() {
        List<BankItemSummary> items = parseItems("""
                {"public_id":"11111111-1111-4111-8111-111111111111","weight":1.5,
                 "display_name":"Gilded Arrow","item_key":"minecraft:arrow","count":64}
                """);

        assertEquals("Gilded Arrow", items.get(0).displayName());
        assertEquals(64, items.get(0).count());
        assertEquals("Gilded Arrow x64", items.get(0).describe());
        // Milestone 10: this test always sent item_key, and until now the parser dropped it --
        // Milestone 0's principal remaining-work finding, closed here.
        assertEquals("minecraft:arrow", items.get(0).itemKey());
    }

    @Test
    void anAbsentItemKeyIsNullNotAnError() {
        List<BankItemSummary> items = parseItems("""
                {"public_id":"11111111-1111-4111-8111-111111111111","weight":1.5,"display_name":"Old Deposit"}
                """);

        assertNull(items.get(0).itemKey());
        assertEquals("Old Deposit", items.get(0).displayName(), "the rest of the identity must be unaffected");
    }

    @Test
    void aWrongTypedItemKeyDegradesToAbsentWithoutFailingTheView() {
        List<BankItemSummary> items = parseItems("""
                {"public_id":"11111111-1111-4111-8111-111111111111","weight":1.5,"item_key":42},
                {"public_id":"22222222-2222-4222-8222-222222222222","weight":1.5,"item_key":{"nested":"object"}}
                """);

        assertNull(items.get(0).itemKey());
        assertNull(items.get(1).itemKey());
    }

    @Test
    void anOverLongItemKeyIsDroppedNotTruncated() {
        // Truncating a display name loses letters; truncating a registry id produces a DIFFERENT
        // id, which could resolve to the wrong item's icon. Over-long keys vanish instead.
        String longKey = "minecraft:" + "a".repeat(300);
        List<BankItemSummary> items = parseItems("""
                {"public_id":"11111111-1111-4111-8111-111111111111","weight":1.5,"item_key":"%s"}
                """.formatted(longKey));

        assertNull(items.get(0).itemKey());
    }

    @Test
    void aSyntacticallyDubiousKeyIsCarriedForTheResolverToJudge() {
        // The parser stays Minecraft-free, so it does not know ResourceLocation's rules. One
        // place decides what malformed means -- BankItemIcon, where the cell falls back to the
        // unknown icon. Carrying the string through is what keeps that definition singular.
        List<BankItemSummary> items = parseItems("""
                {"public_id":"11111111-1111-4111-8111-111111111111","weight":1.5,"item_key":"Not A Valid Key!"}
                """);

        assertEquals("Not A Valid Key!", items.get(0).itemKey());
    }

    @Test
    void readsAnItemWithNoIdentityAtAllAsHavingNone() {
        List<BankItemSummary> items = parseItems("""
                {"public_id":"11111111-1111-4111-8111-111111111111","weight":1.5}
                """);

        assertNull(items.get(0).displayName());
        assertNull(items.get(0).count());
        assertEquals("Stored item", items.get(0).describe());
    }

    @Test
    void readsPartialIdentityWithoutRequiringTheRest() {
        List<BankItemSummary> items = parseItems("""
                {"public_id":"11111111-1111-4111-8111-111111111111","weight":1.5,"display_name":"Solitary Ledger"},
                {"public_id":"22222222-2222-4222-8222-222222222222","weight":1.5,"count":12}
                """);

        assertEquals("Solitary Ledger", items.get(0).describe());
        assertNull(items.get(0).count());
        assertEquals("Stored item x12", items.get(1).describe());
        assertNull(items.get(1).displayName());
    }

    @Test
    void aCountOfOneIsCarriedButNotAnnounced() {
        List<BankItemSummary> items = parseItems("""
                {"public_id":"11111111-1111-4111-8111-111111111111","weight":1.5,
                 "display_name":"Marlo's Ledger","count":1}
                """);

        assertEquals(1, items.get(0).count());
        assertEquals("Marlo's Ledger", items.get(0).describe());
    }

    /**
     * A name is a courtesy. Losing the whole account view because one row's name arrived as the
     * wrong JSON type would be a far worse trade than falling back to the literal.
     */
    @Test
    void aMalformedIdentityValueDegradesToAbsentRatherThanFailingTheResponse() {
        List<BankItemSummary> items = parseItems("""
                {"public_id":"11111111-1111-4111-8111-111111111111","weight":1.5,
                 "display_name":{"nested":"object"},"count":"sixty-four"}
                """);

        assertEquals(1, items.size());
        assertNull(items.get(0).displayName());
        assertNull(items.get(0).count());
        assertEquals("Stored item", items.get(0).describe());
    }

    @Test
    void anImplausibleCountIsIgnoredRatherThanShown() {
        List<BankItemSummary> items = parseItems("""
                {"public_id":"11111111-1111-4111-8111-111111111111","weight":1.5,"count":0}
                """);

        assertNull(items.get(0).count());
        assertEquals("Stored item", items.get(0).describe());
    }

    @Test
    void anOverLongNameIsBoundedHereRatherThanTrusted() {
        List<BankItemSummary> items = parseItems("""
                {"public_id":"11111111-1111-4111-8111-111111111111","weight":1.5,"display_name":"%s"}
                """.formatted("n".repeat(4000)));

        assertEquals(255, items.get(0).displayName().length());
    }

    private static List<BankItemSummary> parseItems(String itemsJson) {
        String body = """
                {"protocol_version":1,"success":true,"outcome":"OPENED","retryable":false,
                 "account":{"public_id":"33333333-3333-4333-8333-333333333333","banking_mode":"global",
                            "weight_limit":250,"current_weight":0.0,
                            "gold_balance":0,"silver_balance":0,"copper_balance":0,"revision":1},
                 "bank_items":{"items":[%s],"next_cursor":null}}
                """.formatted(itemsJson);

        BankingOpenClientResult result = BankingOpenResponseParser.parse(200, body.getBytes(StandardCharsets.UTF_8));
        return assertInstanceOf(BankingOpenClientResult.Success.class, result).bankItems();
    }
}
