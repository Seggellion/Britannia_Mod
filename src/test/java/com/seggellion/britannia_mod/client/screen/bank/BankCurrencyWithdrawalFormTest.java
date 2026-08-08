package com.seggellion.britannia_mod.client.screen.bank;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 16: the playbook's list -- each denomination, zero, negative, malformed, overflow,
 * insufficient funds, exact funds -- as validation cases. Denomination itself is not a parameter
 * here: the form judges one (amount, balance) pair, and the screen asks it three times with three
 * balances, which is what lets the three buttons legitimately disagree.
 */
class BankCurrencyWithdrawalFormTest {

    private static BankCurrencyWithdrawalForm.Validation validate(String raw, int balance) {
        return BankCurrencyWithdrawalForm.validate(raw, balance);
    }

    @Test
    void aValidAmountWithinTheBalancePasses() {
        BankCurrencyWithdrawalForm.Validation validation = validate("800", 900);
        assertTrue(validation.valid());
        assertEquals(800, validation.amount());
    }

    @Test
    void exactFundsPass() {
        assertTrue(validate("900", 900).valid());
    }

    @Test
    void oneCoinOverTheBalanceIsInsufficient() {
        assertEquals(BankStatusPresenter.INSUFFICIENT_BALANCE, validate("901", 900).error());
    }

    @Test
    void theSameAmountCanPassOneBalanceAndFailAnother() {
        // The three-buttons-disagree property: 800 typed with 900 silver and 3 gold lights
        // Silver and not Gold.
        assertTrue(validate("800", 900).valid());
        assertEquals(BankStatusPresenter.INSUFFICIENT_BALANCE, validate("800", 3).error());
    }

    @Test
    void aSingleCoinIsALegitimateWithdrawal() {
        // No cheque-style floor: 3 copper for a market stall is a real request.
        assertTrue(validate("1", 92).valid());
        assertEquals(1, validate("1", 92).amount());
    }

    @Test
    void emptyAndWhitespaceAreEmpty() {
        assertEquals(BankStatusPresenter.EMPTY_AMOUNT, validate("", 900).error());
        assertEquals(BankStatusPresenter.EMPTY_AMOUNT, validate("   ", 900).error());
    }

    @Test
    void zeroIsInvalidNotInsufficient() {
        assertEquals(BankStatusPresenter.INVALID_AMOUNT, validate("0", 900).error());
    }

    @Test
    void negativesAndMalformedInputAreInvalid() {
        for (String bad : new String[]{"-5", "abc", "5.5", "1,000", "+5", "٥٠٠", "５００"}) {
            assertEquals(BankStatusPresenter.INVALID_AMOUNT, validate(bad, 900).error(),
                    "should have rejected " + bad);
        }
    }

    @Test
    void overflowReadsAsTooLargeNotMalformed() {
        assertEquals(BankStatusPresenter.AMOUNT_TOO_LARGE, validate("2147483648", Integer.MAX_VALUE).error());
        assertEquals(BankStatusPresenter.AMOUNT_TOO_LARGE, validate("99999999999999999999", Integer.MAX_VALUE).error());
    }

    @Test
    void shapeProblemsAreReportedBeforeAffordability() {
        // A player who typed "abc" should be told it is not a number, not that they are poor.
        assertEquals(BankStatusPresenter.INVALID_AMOUNT, validate("abc", 0).error());
    }

    @Test
    void anInvalidFormNeverCarriesASendableAmount() {
        assertFalse(validate("abc", 900).valid());
        assertEquals(0, validate("abc", 900).amount());
    }
}
