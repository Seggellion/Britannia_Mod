package com.seggellion.britannia_mod.client.screen.bank;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankBalanceAbbreviationTest {

    @Test
    void belowAThousandIsThePlainNumber() {
        assertEquals("0", BankBalanceAbbreviation.abbreviate(0));
        assertEquals("999", BankBalanceAbbreviation.abbreviate(999));
    }

    @Test
    void theOwnersOwnExamples() {
        assertEquals("1k", BankBalanceAbbreviation.abbreviate(1_000));
        assertEquals("1.25k", BankBalanceAbbreviation.abbreviate(1_250));
    }

    @Test
    void trailingZerosAreTrimmedNotPrinted() {
        assertEquals("2k", BankBalanceAbbreviation.abbreviate(2_000));
        assertEquals("1.5k", BankBalanceAbbreviation.abbreviate(1_500));
        assertEquals("1.05k", BankBalanceAbbreviation.abbreviate(1_050));
    }

    @Test
    void moneyIsTruncatedNeverRoundedUp() {
        // 1,999 must not read "2k" -- a display must never overstate what the player has.
        assertEquals("1.99k", BankBalanceAbbreviation.abbreviate(1_999));
        assertEquals("999.99k", BankBalanceAbbreviation.abbreviate(999_999));
    }

    @Test
    void theTiersAboveThousandsExistBecauseTheColumnDoes() {
        assertEquals("1m", BankBalanceAbbreviation.abbreviate(1_000_000));
        assertEquals("1.25m", BankBalanceAbbreviation.abbreviate(1_250_000));
        assertEquals("5m", BankBalanceAbbreviation.abbreviate(5_000_000));
        assertEquals("2.14b", BankBalanceAbbreviation.abbreviate(Integer.MAX_VALUE));
    }

    @Test
    void everyPossibleBalanceStaysShortEnoughForTheButton() {
        // The actual requirement: the text fits the frame. Seven characters ("999.99k") is the
        // widest any tier can produce.
        for (int balance : new int[]{0, 9, 999, 1_000, 999_999, 1_000_000, 999_999_999, Integer.MAX_VALUE}) {
            assertTrue(BankBalanceAbbreviation.abbreviate(balance).length() <= 7,
                    balance + " -> " + BankBalanceAbbreviation.abbreviate(balance));
        }
    }
}
