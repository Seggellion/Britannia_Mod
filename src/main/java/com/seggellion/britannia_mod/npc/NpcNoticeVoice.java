package com.seggellion.britannia_mod.npc;

import com.seggellion.britannia_mod.network.ClientboundOpenNpcScreenPayload.Notice;

import javax.annotation.Nullable;

/**
 * A trader's own words for a Rails refusal code.
 *
 * <p>Codes travel over the wire and the prose stays here, so the NPC voice lives with the rest of
 * the NPC behaviour rather than in the protocol. Extracted from {@code ClientNetworkHandler} at
 * Trader Commodity Authority M5 so that which codes SPEAK and which stay SILENT is a testable
 * property rather than a client-only branch nothing could reach in a unit test.
 *
 * <p>Returning {@code null} means silence, and silence is load-bearing in two different ways. An
 * UNRECOGNISED code is silent so a protocol identifier never leaks into the chat log and the
 * blanket "not interested" line still covers it. A code that is recognised and DELIBERATELY silent
 * says the trader is simply uninterested — which is only ever right when nothing is actually
 * wrong. Everything that is wrong must be audible; that distinction is the whole point of the
 * refusal codes existing.
 */
public final class NpcNoticeVoice {

    private NpcNoticeVoice() {
    }

    /** The line to say, or {@code null} to stay silent. */
    @Nullable
    public static String line(String role, String code, String subject) {
        String named = subject == null || subject.isBlank() ? "that" : subject.replace('_', ' ');
        return switch (code) {
            case Notice.TREASURY_INSUFFICIENT ->
                    role + " says: 'I cannot afford that right now.'";
            case Notice.TREASURY_DENOMINATION_UNAVAILABLE ->
                    role + " says: 'I have no " + (subject == null || subject.isBlank() ? "coin" : named)
                            + " left to pay you with.'";
            case Notice.MIXED_PAYOUT_DENOMINATIONS ->
                    role + " says: 'I cannot settle that in mixed coin. Sell it to me in smaller lots.'";
            case Notice.VALUE_BELOW_DENOMINATION_MINIMUM ->
                    role + " says: 'That is not worth a single coin.'";
            case Notice.COMMODITY_NOT_BUYABLE ->
                    role + " says: 'I am not buying " + named + " at present.'";
            case Notice.COMMODITY_STOCK_CAP_EXCEEDED ->
                    role + " says: 'I have all the " + named + " I can store.'";
            case Notice.TRADER_NOT_FOUND, Notice.TRADER_NOT_ASSIGNED ->
                    role + " says: 'I am not open for business.'";

            // A profession that never deals in this commodity is not a fault and gets no line of
            // its own. It reaches a player only when the mod's synced policy and Rails' disagree
            // -- a stale sync or a modified client -- and in both cases the honest reading is
            // still ordinary disinterest, which the blanket line already covers.
            case Notice.COMMODITY_NOT_TRADED_BY_TYPE -> null;

            // Audible, and audibly a FAULT. A trader whose policy is empty or missing must not
            // sound like one who has looked at your goods and declined them: that confusion is
            // what let a whole class of broken Trader read as working for months.
            case Notice.TRADER_POLICY_MISSING ->
                    role + " says: 'My trading writ is not in order. I can buy nothing until the"
                            + " guild sets it right.'";

            default -> null;
        };
    }
}
