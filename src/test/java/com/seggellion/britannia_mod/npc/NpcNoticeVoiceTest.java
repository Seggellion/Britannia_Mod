package com.seggellion.britannia_mod.npc;

import com.seggellion.britannia_mod.network.ClientboundOpenNpcScreenPayload.Notice;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which refusals a trader says out loud — Trader Commodity Authority, M5.
 *
 * <p>The two codes added at M5 sit deliberately on opposite sides of that line, and the split is
 * the whole reason the codes exist. A profession that never deals in a commodity is ordinary
 * disinterest and stays SILENT. A profession whose policy is empty or missing is BROKEN and says
 * so. Collapsing the second into the first is the original bug: a whole class of Trader read as
 * merely uninterested for months while it was in fact misconfigured.
 */
class NpcNoticeVoiceTest {

    private static final String ROLE = "Fish Trader";

    @Test
    void aCommodityThisProfessionNeverBuysIsSaidNotAtAll() {
        assertNull(NpcNoticeVoice.line(ROLE, Notice.COMMODITY_NOT_TRADED_BY_TYPE, "oak"),
                "ordinary disinterest earns no line of its own");
        assertNull(NpcNoticeVoice.line(ROLE, Notice.COMMODITY_NOT_TRADED_BY_TYPE, ""));
    }

    @Test
    void aMissingPolicyIsSpokenAndSoundsLikeAFaultRatherThanDisinterest() {
        String line = NpcNoticeVoice.line(ROLE, Notice.TRADER_POLICY_MISSING, "");

        assertNotNull(line, "a broken trader must not be silent");
        assertTrue(line.startsWith(ROLE + " says: '"), "spoken in the trader's own voice");
        assertTrue(line.contains("not in order"),
                "the wording must name something being wrong, not a preference");

        // The failure this code exists to prevent: sounding like the lines that mean "I looked at
        // your goods and I do not want them."
        assertFalse(line.contains("not interested"));
        assertFalse(line.contains("not buying"));
        assertEquals(line, NpcNoticeVoice.line(ROLE, Notice.TRADER_POLICY_MISSING, "cod"),
                "the fault is the trader's, so no commodity is named as its cause");
    }

    @Test
    void theExistingCodesKeepTheirWordingAndTheirSilences() {
        assertEquals(ROLE + " says: 'I cannot afford that right now.'",
                NpcNoticeVoice.line(ROLE, Notice.TREASURY_INSUFFICIENT, ""));
        assertEquals(ROLE + " says: 'I am not buying lava fish at present.'",
                NpcNoticeVoice.line(ROLE, Notice.COMMODITY_NOT_BUYABLE, "lava_fish"));
        assertEquals(ROLE + " says: 'I have all the cod I can store.'",
                NpcNoticeVoice.line(ROLE, Notice.COMMODITY_STOCK_CAP_EXCEEDED, "cod"));
        assertEquals(ROLE + " says: 'I have no silver left to pay you with.'",
                NpcNoticeVoice.line(ROLE, Notice.TREASURY_DENOMINATION_UNAVAILABLE, "silver"));
        assertEquals(ROLE + " says: 'I have no coin left to pay you with.'",
                NpcNoticeVoice.line(ROLE, Notice.TREASURY_DENOMINATION_UNAVAILABLE, ""));
        assertEquals(ROLE + " says: 'I am not open for business.'",
                NpcNoticeVoice.line(ROLE, Notice.TRADER_NOT_ASSIGNED, ""));

        // "The city does not trade that" has always been silent, for the same reason the new
        // disinterest code is.
        assertNull(NpcNoticeVoice.line(ROLE, Notice.COMMODITY_NOT_FOUND, "cod"));
    }

    /** An unrecognised code stays silent so a protocol identifier never lands in the chat log. */
    @Test
    void anUnknownCodeIsNeverEchoedAtThePlayer() {
        assertNull(NpcNoticeVoice.line(ROLE, "some_future_refusal_code", "cod"));
    }
}
