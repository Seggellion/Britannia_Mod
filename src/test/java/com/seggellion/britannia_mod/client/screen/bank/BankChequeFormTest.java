package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.client.screen.bank.BankBalanceCopy.Denomination;
import com.seggellion.britannia_mod.network.payload.BankAccountOpenedS2CPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 7: the Create Cheque form's validation, which is the only part of that screen that
 * holds a decision worth testing.
 *
 * <p>Playbook Milestone 7 names six required client validations -- empty, malformed, zero,
 * negative, overflow, no denomination selected -- and all six are here. The bounds cases are here
 * too, because the owner's value-denominated bounds decision makes them the ones a player is
 * actually most likely to hit.
 */
class BankChequeFormTest {

    /** A balance large enough that affordability never masks the case under test. */
    private static final int RICH = Integer.MAX_VALUE;

    @BeforeEach
    @AfterEach
    void resetSession() {
        ClientBankingSession.resetForTesting();
    }

    private static BankChequeForm.Validation validate(String amount, Denomination denomination, int balance) {
        return BankChequeForm.validate(amount, denomination, balance);
    }

    // ---------- The six required validations ----------

    @Test
    void rejectsNoDenominationSelected() {
        assertEquals(BankStatusPresenter.NO_DENOMINATION_SELECTED, validate("500", null, RICH).error());
    }

    @Test
    void rejectsAnEmptyAmount() {
        assertEquals(BankStatusPresenter.EMPTY_AMOUNT, validate("", Denomination.GOLD, RICH).error());
        assertEquals(BankStatusPresenter.EMPTY_AMOUNT, validate("   ", Denomination.GOLD, RICH).error());
    }

    @Test
    void rejectsAMalformedAmount() {
        for (String malformed : new String[]{"abc", "5.5", "1,000", "5g", "--5", "+500", "5 0 0"}) {
            assertEquals(BankStatusPresenter.INVALID_AMOUNT, validate(malformed, Denomination.GOLD, RICH).error(),
                    "should have rejected " + malformed);
        }
    }

    @Test
    void rejectsDigitsFromOtherWritingSystems() {
        // Long.parseLong would accept these -- it routes through Character.digit, so Arabic-Indic
        // "٥٠٠" parses as 500. The validator checks for ASCII digits first so that what the field
        // accepts is a fixed answer rather than whichever digit systems the JDK happens to know.
        assertEquals(BankStatusPresenter.INVALID_AMOUNT, validate("٥٠٠", Denomination.GOLD, RICH).error());
        assertEquals(BankStatusPresenter.INVALID_AMOUNT, validate("５００", Denomination.GOLD, RICH).error());
    }

    @Test
    void treatsAnAbsurdlyLongRunOfDigitsAsTooLargeRatherThanMalformed() {
        // Past long range entirely, so parseLong throws where the ASCII check passed. It is still
        // a number the player typed, and "too large" is the honest thing to tell them.
        assertEquals(BankStatusPresenter.AMOUNT_TOO_LARGE,
                validate("99999999999999999999999", Denomination.GOLD, RICH).error());
    }

    @Test
    void rejectsZero() {
        assertEquals(BankStatusPresenter.INVALID_AMOUNT, validate("0", Denomination.GOLD, RICH).error());
    }

    @Test
    void rejectsNegatives() {
        assertEquals(BankStatusPresenter.INVALID_AMOUNT, validate("-1", Denomination.GOLD, RICH).error());
        assertEquals(BankStatusPresenter.INVALID_AMOUNT, validate("-500", Denomination.GOLD, RICH).error());
    }

    @Test
    void rejectsOverflowWithoutWrappingIntoAPlausibleSmallNumber() {
        // Parsed as long precisely so this reports as "too large" rather than "not a number", and
        // so the copper conversion cannot wrap: 300000 gold is 3e9 copper, past int range.
        assertEquals(BankStatusPresenter.AMOUNT_TOO_LARGE, validate("300000", Denomination.GOLD, RICH).error());
        assertEquals(BankStatusPresenter.AMOUNT_TOO_LARGE, validate("9999999999", Denomination.COPPER, RICH).error());
    }

    // ---------- Value-denominated bounds ----------

    @Test
    void theMinimumIsFiveHundredCoinsWhicheverCoinItIs() {
        // The rule a player can hold in their head, and the whole point of the owner's revision:
        // not "5,000,000 copper of value" but "500 coins".
        for (Denomination denomination : Denomination.values()) {
            assertEquals(BankChequeForm.MIN_UNITS, BankChequeForm.minimumIn(denomination), denomination.name());
        }
        assertEquals(500, BankChequeForm.MIN_UNITS);
    }

    @Test
    void goldsFloorIsUnchangedByTheRevision() {
        // 500 gold was the floor under the old value-denominated rule too, because 500 gold is
        // exactly 5,000,000 copper. Only silver and copper move.
        assertTrue(validate("500", Denomination.GOLD, RICH).valid());
        assertEquals(5_000_000, validate("500", Denomination.GOLD, RICH).copperAmount());
        assertEquals(BankStatusPresenter.AMOUNT_BELOW_MINIMUM, validate("499", Denomination.GOLD, RICH).error());
    }

    @Test
    void silverAndCopperNowHaveReachableFloors() {
        // Under the previous rule these needed 50,000 silver and 5,000,000 copper.
        assertTrue(validate("500", Denomination.SILVER, RICH).valid());
        assertEquals(50_000, validate("500", Denomination.SILVER, RICH).copperAmount());

        assertTrue(validate("500", Denomination.COPPER, RICH).valid());
        assertEquals(500, validate("500", Denomination.COPPER, RICH).copperAmount());
    }

    @Test
    void acceptsExactlyTheMinimumInEveryDenomination() {
        for (Denomination denomination : Denomination.values()) {
            int minimum = BankChequeForm.minimumIn(denomination);
            BankChequeForm.Validation validation = validate(String.valueOf(minimum), denomination, RICH);
            assertTrue(validation.valid(), denomination + " should accept its exact minimum");
            assertEquals(minimum * BankChequeForm.copperPerUnit(denomination), validation.copperAmount(),
                    denomination + "'s minimum should convert to its own coin value");
        }
    }

    @Test
    void rejectsOneBelowTheMinimumInEveryDenomination() {
        for (Denomination denomination : Denomination.values()) {
            int justUnder = BankChequeForm.minimumIn(denomination) - 1;
            assertEquals(BankStatusPresenter.AMOUNT_BELOW_MINIMUM, validate(String.valueOf(justUnder), denomination, RICH).error(),
                    denomination + " should reject one below its minimum");
        }
    }

    @Test
    void anAmountBelowTheFloorIsRejectedAsTooSmallNotAsInvalid() {
        // "10" is a perfectly reasonable number and nowhere near 500. Telling the player it is
        // invalid would be wrong; telling them it is too small is actionable.
        for (Denomination denomination : Denomination.values()) {
            assertEquals(BankStatusPresenter.AMOUNT_BELOW_MINIMUM, validate("10", denomination, RICH).error(),
                    denomination.name());
        }
    }

    @Test
    void acceptsExactlyTheMaximumInEveryDenomination() {
        for (Denomination denomination : Denomination.values()) {
            int maximum = BankChequeForm.maximumIn(denomination);
            BankChequeForm.Validation validation = validate(String.valueOf(maximum), denomination, RICH);
            assertTrue(validation.valid(), denomination + " should accept its exact maximum");
            assertEquals(BankChequeForm.MAX_COPPER, validation.copperAmount());
        }
    }

    @Test
    void rejectsBelowTheFloorBeforeCheckingTheCeiling() {
        // Ordering guard: 1 copper is under the floor, not over the ceiling.
        assertEquals(BankStatusPresenter.AMOUNT_BELOW_MINIMUM, validate("1", Denomination.COPPER, RICH).error());
    }

    @Test
    void rejectsOneAboveTheMaximum() {
        for (Denomination denomination : Denomination.values()) {
            int justOver = BankChequeForm.maximumIn(denomination) + 1;
            assertEquals(BankStatusPresenter.AMOUNT_TOO_LARGE, validate(String.valueOf(justOver), denomination, RICH).error(),
                    denomination + " should reject one above its maximum");
        }
    }

    // ---------- Conversion ----------

    @Test
    void convertsTheTypedAmountOutOfItsDenominationIntoCopper() {
        assertEquals(5_000_000, validate("500", Denomination.GOLD, RICH).copperAmount());
        assertEquals(5_000_000, validate("50000", Denomination.SILVER, RICH).copperAmount());
        assertEquals(5_000_000, validate("5000000", Denomination.COPPER, RICH).copperAmount());
    }

    @Test
    void theCeilingStaysAValueLimitBecauseTheColumnRequiresIt() {
        // Unlike the floor, this is not a policy choice: the amount is an int32 copper column, so
        // a flat coin count would let a copper cheque overflow what it is stored in.
        assertEquals(100_000, BankChequeForm.maximumIn(Denomination.GOLD));
        assertEquals(10_000_000, BankChequeForm.maximumIn(Denomination.SILVER));
        assertEquals(1_000_000_000, BankChequeForm.maximumIn(Denomination.COPPER));
    }

    @Test
    void keepsTheTypedNumberAlongsideTheConvertedValue() {
        // Milestone 8b sends the copper value and the denomination; the entered number is what
        // the player should keep seeing in the box.
        BankChequeForm.Validation validation = validate("600", Denomination.GOLD, RICH);
        assertEquals(600, validation.enteredAmount());
        assertEquals(6_000_000, validation.copperAmount());
    }

    // ---------- Affordability ----------

    @Test
    void rejectsAnAmountTheSelectedDenominationCannotFund() {
        assertEquals(BankStatusPresenter.INSUFFICIENT_BALANCE, validate("500", Denomination.GOLD, 499).error());
    }

    @Test
    void acceptsSpendingTheBalanceExactly() {
        assertTrue(validate("500", Denomination.GOLD, 500).valid());
    }

    @Test
    void checksAffordabilityAgainstTheSelectedDenominationOnly() {
        // A rich copper balance does not make a gold cheque affordable: 8a debits the funding
        // denomination's own column.
        assertEquals(BankStatusPresenter.INSUFFICIENT_BALANCE, validate("500", Denomination.GOLD, 0).error());
    }

    @Test
    void reportsShapeProblemsBeforeAffordability() {
        // A player who typed "abc" should be told it is not a number, not that they are poor.
        assertEquals(BankStatusPresenter.INVALID_AMOUNT, validate("abc", Denomination.GOLD, 0).error());
        assertEquals(BankStatusPresenter.EMPTY_AMOUNT, validate("", Denomination.GOLD, 0).error());
    }

    @Test
    void reportsRangeProblemsBeforeAffordability() {
        assertEquals(BankStatusPresenter.AMOUNT_BELOW_MINIMUM, validate("1", Denomination.GOLD, 0).error());
    }

    // ---------- Reading the balance off the session ----------

    @Test
    void readsEachDenominationsBalanceFromTheSession() {
        ClientBankingSession session = ClientBankingSession.applyAccountOpened(new BankAccountOpenedS2CPayload(
                "Aldric", "male", 42, "Britain", 250, 0.0, 3, 47, 92, List.of()
        ));

        assertEquals(3, BankChequeForm.balanceOf(session, Denomination.GOLD));
        assertEquals(47, BankChequeForm.balanceOf(session, Denomination.SILVER));
        assertEquals(92, BankChequeForm.balanceOf(session, Denomination.COPPER));
    }

    @Test
    void whitespaceAroundAGoodNumberIsForgiven() {
        assertTrue(validate("  500  ", Denomination.GOLD, RICH).valid());
    }

    @Test
    void anInvalidFormNeverCarriesASpendableAmount() {
        BankChequeForm.Validation validation = validate("abc", Denomination.GOLD, RICH);
        assertFalse(validation.valid());
        assertEquals(0, validation.copperAmount());
        assertEquals(0, validation.enteredAmount());
    }
}
