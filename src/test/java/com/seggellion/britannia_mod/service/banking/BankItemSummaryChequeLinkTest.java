package com.seggellion.britannia_mod.service.banking;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stored-cheque link's one rule: only an explicit {@code true} makes the cashing gesture
 * available. Everything else -- an ordinary item, a legacy row deposited before the link
 * existed, a cheque Rails knows is spent -- must read as "not cashable", because offering a
 * click that then fails is the dishonest affordance this epic removes everywhere.
 */
class BankItemSummaryChequeLinkTest {

    private static BankItemSummary withRedeemable(Boolean chequeRedeemable) {
        return new BankItemSummary(UUID.randomUUID(), 1.0, "Bank Cheque", 1,
                "britannia_mod:bank_cheque", chequeRedeemable);
    }

    @Test
    void onlyAnExplicitTrueIsCashable() {
        assertTrue(withRedeemable(true).isChequeRedeemable());
        assertFalse(withRedeemable(false).isChequeRedeemable());
        assertFalse(withRedeemable(null).isChequeRedeemable(), "silence must never light up the gesture");
    }

    @Test
    void anOrdinaryItemCarriesNoLinkAtAll() {
        BankItemSummary diamond = BankItemSummary.withoutChequeLink(
                UUID.randomUUID(), 1.0, "Diamond", 5, "minecraft:diamond");
        assertNull(diamond.chequeRedeemable(), "absence is how an ordinary item says it is not a cheque");
        assertFalse(diamond.isChequeRedeemable());
    }

    @Test
    void aLegacyStoredRowIsNotCashableFromTheVault() {
        // Rails cannot backfill the link: the payload is opaque to it, and only the mod could
        // decode it -- which requires withdrawing the item first. So a cheque stored before the
        // link shipped reports nothing, and the player's route is withdraw-then-cash-from-pack.
        BankItemSummary legacy = BankItemSummary.withoutIdentity(UUID.randomUUID(), 1.0);
        assertFalse(legacy.isChequeRedeemable());
    }

    @Test
    void theLinkDoesNotDisturbHowARowDescribesItself() {
        assertEquals("Bank Cheque", withRedeemable(true).describe());
        assertEquals("Bank Cheque", withRedeemable(null).describe());
    }
}
