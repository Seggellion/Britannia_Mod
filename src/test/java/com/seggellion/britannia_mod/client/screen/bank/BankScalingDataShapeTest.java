package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.service.banking.BankItemSummary;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 18, the half of the scaling matrix that is data rather than pixels: <b>large currency
 * values</b> and <b>long translated names</b>.
 *
 * <p>What a font does with the resulting string at GUI scale 4 is the owner's visual gate and
 * cannot be asserted here (Architecture Decision 0 -- no screen harness exists). What can be
 * asserted, and is what actually breaks, is the string itself: that a maximal balance formats
 * rather than overflows, that a 255-character name survives the classes that handle it, and that
 * the widths the layout reserves are large enough for the biggest legitimate content.
 */
class BankScalingDataShapeTest {

    // ---------- Large currency values ----------

    @Test
    void aMaximalBalanceFormatsReadablyRatherThanAsARunOfDigits() {
        // Rails' balance columns are int32, so this is the largest value that can ever arrive.
        assertEquals("2,147,483,647", BankBalanceCopy.formatAmount(Integer.MAX_VALUE));
        assertEquals("5,000,000", BankBalanceCopy.formatAmount(5_000_000));
        assertEquals("0", BankBalanceCopy.formatAmount(0));
    }

    @Test
    void theBalanceSentenceStillChoosesCorrectlyAtTheCeiling() {
        // The empty-account sentence must not be picked for a maximal account, and vice versa.
        assertTrue(BankBalanceCopy.bodyKey(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)
                .endsWith("body"));
        assertTrue(BankBalanceCopy.bodyKey(0, 0, 0).endsWith("body_empty"));
        // One coin anywhere is not an empty account.
        assertTrue(BankBalanceCopy.bodyKey(0, 0, 1).endsWith("body"));
    }

    @Test
    void pluralisationHoldsAtBothExtremes() {
        assertTrue(BankBalanceCopy.amountKey(BankBalanceCopy.Denomination.GOLD, 1).endsWith(".one"));
        assertTrue(BankBalanceCopy.amountKey(BankBalanceCopy.Denomination.GOLD, 0).endsWith(".many"));
        assertTrue(BankBalanceCopy.amountKey(BankBalanceCopy.Denomination.GOLD, Integer.MAX_VALUE).endsWith(".many"));
    }

    @Test
    void aMaximalAmountSurvivesTheWithdrawalFormWithoutOverflowing() {
        // Integer.MAX_VALUE typed against a matching balance is legitimate, not "too large".
        BankCurrencyWithdrawalForm.Validation validation =
                BankCurrencyWithdrawalForm.validate(String.valueOf(Integer.MAX_VALUE), Integer.MAX_VALUE);
        assertTrue(validation.valid());
        assertEquals(Integer.MAX_VALUE, validation.amount());

        // One past it is not, and must be named as too large rather than wrapping negative.
        assertEquals(BankStatusPresenter.AMOUNT_TOO_LARGE,
                BankCurrencyWithdrawalForm.validate("2147483648", Integer.MAX_VALUE).error());
    }

    // ---------- Long translated names ----------

    /** Rails' own bound on display_name, which is therefore the longest that can ever arrive. */
    private static final int RAILS_MAX_NAME = 255;

    @Test
    void aMaximalLengthNameSurvivesTheSummaryIntact() {
        String longName = "N".repeat(RAILS_MAX_NAME);
        BankItemSummary item = BankItemSummary.withoutChequeLink(
                UUID.randomUUID(), 1.0, longName, 1, "minecraft:diamond");
        assertEquals(longName, item.displayName());
        // A count of one adds nothing, so describe() must not append anything to an already
        // maximal string.
        assertEquals(longName, item.describe());
    }

    @Test
    void aMaximalNameWithACountStillDescribesWithoutTruncating() {
        String longName = "N".repeat(RAILS_MAX_NAME);
        BankItemSummary item = BankItemSummary.withoutChequeLink(UUID.randomUUID(), 1.0, longName, 64, null);
        // Truncation is the renderer's business, not the model's -- losing characters here would
        // lose them for the tooltip too, where there is room.
        assertEquals(longName + " x64", item.describe());
        assertTrue(item.describe().length() > RAILS_MAX_NAME);
    }

    @Test
    void aMultiByteNameIsCountedInCharactersNotBytes() {
        // A name of 255 four-byte characters is legitimate and must not be mistaken for oversized.
        String emoji = "🪙".repeat(RAILS_MAX_NAME);
        BankItemSummary item = BankItemSummary.withoutChequeLink(UUID.randomUUID(), 1.0, emoji, 1, null);
        assertEquals(emoji, item.displayName());
    }

    @Test
    void aBlankNameFallsBackRatherThanRenderingAnEmptyRow() {
        BankItemSummary blank = BankItemSummary.withoutChequeLink(UUID.randomUUID(), 1.0, "   ", 1, null);
        assertEquals(BankItemSummary.FALLBACK_NAME, blank.describe());
    }

    // ---------- The widths the layout reserves ----------

    @Test
    void theStatusLineGetsRealWidthAtEverySupportedSize() {
        // A long status message is one of the matrix's named cases. It wraps, so what matters is
        // that the width it wraps into is never absurd -- a few pixels would wrap every sentence
        // to one character per line.
        for (int width = 180; width <= 1200; width += 4) {
            for (int height = 200; height <= 700; height += 50) {
                BankBoxLayout layout = BankBoxLayout.calculate(width, height, 9, 40);
                assertTrue(layout.statusMaxWidth() >= 100,
                        "status width " + layout.statusMaxWidth() + " at " + width + "x" + height);
                assertTrue(layout.statusMaxWidth() <= layout.panelWidth(),
                        "status wider than its own panel at " + width + "x" + height);
            }
        }
    }

    @Test
    void everyDialogueScreenGivesItsBodyAndStatusRealWidthToo() {
        for (int width = 320; width <= 1200; width += 4) {
            for (int height = 200; height <= 700; height += 50) {
                BankDialogueLayout layout = BankDialogueLayout.calculate(width, height, 9, 3, 2, 0);
                assertTrue(layout.bodyMaxWidth() > 0, "body width at " + width + "x" + height);
                assertTrue(layout.statusMaxWidth() > 0, "status width at " + width + "x" + height);
                assertFalse(layout.buttonWidth() <= 0, "button width at " + width + "x" + height);
            }
        }
    }
}
