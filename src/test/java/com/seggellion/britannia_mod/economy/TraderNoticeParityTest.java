package com.seggellion.britannia_mod.economy;

import com.seggellion.britannia_mod.network.ClientboundOpenNpcScreenPayload;
import com.seggellion.britannia_mod.network.ClientboundOpenNpcScreenPayload.Notice;
import com.seggellion.britannia_mod.npc.NpcNoticeVoice;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trader Commodity Authority Milestone 6, notice parity: silence must never cost audibility.
 *
 * <p>M5 made {@code commodity_not_traded_by_type} silent in two places — dropped per row in
 * {@code readRow}, and wordless in {@link NpcNoticeVoice}. Silence is right for ordinary
 * disinterest, but the notice list is CAPPED, and row notices are collected before payout notices.
 * So the question this file exists to answer is not "is disinterest silent" (M5 pinned that) but
 * "can a flood of it push the reasons a player can actually act on off the end of the wire".
 *
 * <p>That is the failure the suppression was introduced to prevent, and it is exactly the kind of
 * thing that would be discovered in production rather than in a test.
 */
class TraderNoticeParityTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Far more profession-refused rows than the wire can carry notices for, with the actionable
     * reasons behind them. Every actionable reason must still arrive.
     */
    @Test
    void aFloodOfProfessionDisinterestCannotCrowdOutTheActionableReasons() {
        int flood = ClientboundOpenNpcScreenPayload.MAX_NOTICES * 3;
        List<String> rows = new ArrayList<>();
        for (int index = 0; index < flood; index++) {
            rows.add(refusedRow(index, Notice.COMMODITY_NOT_TRADED_BY_TYPE, "oak"));
        }
        // The reasons that are worth a player's attention, behind the flood.
        rows.add(refusedRow(flood, Notice.COMMODITY_STOCK_CAP_EXCEEDED, "cod"));
        rows.add(refusedRow(flood + 1, Notice.COMMODITY_NOT_BUYABLE, "salmon"));

        var quote = EconomicBuybackCatalogService.parse(
                "{\"rows\":[" + String.join(",", rows) + "],"
                        + "\"payout\":{\"available\":false,\"denomination\":\"copper\","
                        + "\"reasons\":[\"treasury_insufficient\"]}}",
                sources(flood + 2));

        List<String> codes = quote.notices().stream().map(Notice::code).toList();
        assertFalse(codes.contains(Notice.COMMODITY_NOT_TRADED_BY_TYPE),
                "ordinary disinterest must not reach the wire at all");
        assertTrue(codes.contains(Notice.COMMODITY_STOCK_CAP_EXCEEDED),
                "a full warehouse is actionable and was crowded out");
        assertTrue(codes.contains(Notice.COMMODITY_NOT_BUYABLE),
                "a city buy-policy refusal is actionable and was crowded out");
        assertTrue(codes.contains(Notice.TREASURY_INSUFFICIENT),
                "the payout reason is added last and is the first thing a cap would drop");
        assertTrue(quote.notices().size() <= ClientboundOpenNpcScreenPayload.MAX_NOTICES,
                "the wire cap must still hold");
    }

    /**
     * The counterfactual that gives the test above its force: WITHOUT the suppression the same
     * quote overruns the cap on disinterest alone, and every actionable reason is lost. Modelled
     * on the codes rather than by reverting the code, so it documents the mechanism rather than
     * merely asserting today's behaviour is fine.
     */
    @Test
    void withoutTheSuppressionTheSameQuoteWouldHaveLostEveryActionableReason() {
        int flood = ClientboundOpenNpcScreenPayload.MAX_NOTICES * 3;
        List<String> unsuppressed = new ArrayList<>();
        for (int index = 0; index < flood; index++) unsuppressed.add(Notice.COMMODITY_NOT_TRADED_BY_TYPE);
        unsuppressed.add(Notice.COMMODITY_STOCK_CAP_EXCEEDED);
        unsuppressed.add(Notice.TREASURY_INSUFFICIENT);

        List<String> asSent = unsuppressed.stream()
                .limit(ClientboundOpenNpcScreenPayload.MAX_NOTICES).collect(Collectors.toList());

        assertFalse(asSent.contains(Notice.COMMODITY_STOCK_CAP_EXCEEDED));
        assertFalse(asSent.contains(Notice.TREASURY_INSUFFICIENT));
    }

    /** The two M5 codes, on their opposite sides of the silence line, at the wording layer. */
    @Test
    void disinterestStaysWordlessWhileAConfigurationFaultSpeaks() {
        assertNull(NpcNoticeVoice.line("Fish Trader", Notice.COMMODITY_NOT_TRADED_BY_TYPE, "oak"));

        String fault = NpcNoticeVoice.line("Fish Trader", Notice.TRADER_POLICY_MISSING, "");
        assertNotNull(fault);
        assertFalse(fault.isBlank());

        // Every actionable code keeps a voice; a silent one here would be invisible twice over.
        for (String code : List.of(Notice.COMMODITY_NOT_BUYABLE, Notice.COMMODITY_STOCK_CAP_EXCEEDED,
                Notice.TREASURY_INSUFFICIENT, Notice.TREASURY_DENOMINATION_UNAVAILABLE,
                Notice.MIXED_PAYOUT_DENOMINATIONS, Notice.VALUE_BELOW_DENOMINATION_MINIMUM,
                Notice.TRADER_NOT_FOUND, Notice.TRADER_NOT_ASSIGNED)) {
            assertNotNull(NpcNoticeVoice.line("Fish Trader", code, "cod"),
                    code + " lost its voice");
        }
    }

    /**
     * A policy fault short-circuits before any request is posted, so the player hears the fault
     * itself rather than the blanket "not interested" line that hid this class of bug for months.
     */
    @Test
    void aPolicyFaultIsReportedAsItsOwnNoticeRatherThanAsAnEmptyOffer() {
        var quote = EconomicBuybackCatalogService.Quote.noticeOnly(Notice.TRADER_POLICY_MISSING, "");

        assertEquals(1, quote.notices().size());
        assertEquals(Notice.TRADER_POLICY_MISSING, quote.notices().get(0).code());
        assertTrue(quote.products().isEmpty());
        assertNotNull(NpcNoticeVoice.line("Fish Trader", quote.notices().get(0).code(), ""));
    }

    private static String refusedRow(int index, String reason, String displayName) {
        return "{\"index\":" + index + ",\"commodity_key\":\"" + displayName + "\","
                + "\"display_name\":\"" + displayName + "\",\"available\":false,"
                + "\"reasons\":[\"" + reason + "\"]}";
    }

    private static List<ItemStack> sources(int count) {
        List<ItemStack> sources = new ArrayList<>(count);
        for (int index = 0; index < count; index++) sources.add(new ItemStack(Items.COD));
        return sources;
    }
}
