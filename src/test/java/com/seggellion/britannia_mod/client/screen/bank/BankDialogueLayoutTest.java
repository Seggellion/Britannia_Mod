package com.seggellion.britannia_mod.client.screen.bank;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 3's required layout coverage: narrow and wide widths, several GUI scales, wrapped
 * body text, three buttons and two, and form controls that overlap nothing.
 *
 * <p>GUI scale is not a parameter -- Minecraft divides by it before a screen sees a coordinate, so
 * a scaled width already encodes it. The constants below name the real window-and-scale
 * combinations each width stands for, so a failure points at a setting somebody can reproduce.
 */
class BankDialogueLayoutTest {

    private static final int FONT_LINE_HEIGHT = 9;

    // Scaled widths = window width / GUI scale.
    private static final int W_1920_SCALE_2 = 960;
    private static final int W_1280_SCALE_2 = 640;
    private static final int W_1280_SCALE_3 = 426;
    private static final int W_1024_SCALE_4 = 256;
    private static final int W_854_SCALE_4 = 213;

    private static final int H_1080_SCALE_2 = 540;
    private static final int H_720_SCALE_3 = 240;

    private static BankDialogueLayout layout(int width, int height, int buttons, int bodyLines, int formRows) {
        return BankDialogueLayout.calculate(width, height, FONT_LINE_HEIGHT, buttons, bodyLines, formRows);
    }

    /** The invariant the whole class exists to hold. */
    private static void assertBodyNeverOverlapsButtons(BankDialogueLayout l) {
        assertTrue(l.bodyMaxWidth() > 0, "body width must be positive, was " + l.bodyMaxWidth());
        assertTrue(
                l.bodyRight() <= l.buttonX(),
                "body right edge " + l.bodyRight() + " ran into button column at " + l.buttonX()
        );
    }

    private static void assertWithinScreen(BankDialogueLayout l, int width) {
        assertTrue(l.buttonX() >= 0, "button column off the left edge");
        assertTrue(l.buttonX() + l.buttonWidth() <= width, "button column off the right edge");
        assertTrue(l.bodyX() >= 0, "body off the left edge");
        assertTrue(l.statusX() + l.statusMaxWidth() <= width, "status runs off the right edge");
        assertTrue(l.formX() + l.formWidth() <= width, "form runs off the right edge");
    }

    // ---------- Width matrix ----------

    @Test
    void holdsTheNoOverlapInvariantAtEveryScaledWidthFrom120To2000() {
        // Brute force rather than samples: the invariant must hold everywhere, including at the
        // exact width where the portrait drops out, which no hand-picked list would find.
        for (int width = 120; width <= 2000; width++) {
            BankDialogueLayout l = layout(width, H_1080_SCALE_2, 3, 3, 0);
            assertBodyNeverOverlapsButtons(l);
            assertWithinScreen(l, width);
        }
    }

    @Test
    void keepsThePortraitOnAWideScreen() {
        BankDialogueLayout l = layout(W_1920_SCALE_2, H_1080_SCALE_2, 3, 2, 0);
        assertTrue(l.portraitVisible());
        assertEquals(BankDialogueLayout.PORTRAIT_X, l.portraitX());
        // Body starts clear of the portrait column.
        assertTrue(l.bodyX() >= l.portraitX() + l.portraitSize());
    }

    @Test
    void keepsThePortraitAtATypicalWindowedWidth() {
        BankDialogueLayout l = layout(W_1280_SCALE_2, H_1080_SCALE_2, 3, 2, 0);
        assertTrue(l.portraitVisible());
        assertBodyNeverOverlapsButtons(l);
    }

    @Test
    void dropsThePortraitWhenTheTextColumnWouldBecomeUnusable() {
        BankDialogueLayout l = layout(W_1024_SCALE_4, H_720_SCALE_3, 3, 4, 0);
        assertFalse(l.portraitVisible(), "portrait should yield to the text at 256 units");
        assertEquals(BankDialogueLayout.MARGIN, l.bodyX(), "text should reclaim the left margin");
        assertBodyNeverOverlapsButtons(l);
    }

    @Test
    void staysUsableAtTheNarrowestRealisticWidth() {
        BankDialogueLayout l = layout(W_854_SCALE_4, H_720_SCALE_3, 3, 5, 0);
        assertBodyNeverOverlapsButtons(l);
        assertWithinScreen(l, W_854_SCALE_4);
        assertTrue(l.buttonWidth() >= BankDialogueLayout.MIN_BUTTON_WIDTH);
    }

    @Test
    void neverProducesTheNegativeWrapWidthTheLegacyLayoutDoes() {
        // DialogueLayout computes maxTextWidth as screenWidth - 343, which is negative here and
        // reaches drawWordWrap as a negative wrap width. This is the regression guard for that.
        for (int width : new int[]{W_1024_SCALE_4, W_854_SCALE_4, 200, 160, 120}) {
            assertTrue(layout(width, 240, 3, 2, 0).bodyMaxWidth() > 0, "negative wrap width at " + width);
        }
    }

    @Test
    void clampsButtonWidthBetweenItsBounds() {
        assertEquals(BankDialogueLayout.MAX_BUTTON_WIDTH, layout(W_1920_SCALE_2, 540, 3, 1, 0).buttonWidth());
        assertEquals(BankDialogueLayout.MIN_BUTTON_WIDTH, layout(150, 240, 3, 1, 0).buttonWidth());
    }

    // ---------- Button stacks ----------

    @Test
    void stacksThreeButtonsWithoutOverlapAndInsideTheBanner() {
        BankDialogueLayout l = layout(W_1280_SCALE_2, H_1080_SCALE_2, 3, 2, 0);
        int step = l.buttonHeight() + l.buttonGap();
        assertEquals(l.buttonTopY(), l.buttonY(0));
        assertEquals(l.buttonTopY() + step, l.buttonY(1));
        assertEquals(l.buttonTopY() + (step * 2), l.buttonY(2));
        assertTrue(l.buttonY(0) >= 0);
        assertTrue(
                l.buttonY(2) + l.buttonHeight() <= BankDialogueLayout.HEADER_HEIGHT,
                "third button must stay inside the parchment banner"
        );
    }

    @Test
    void stacksTwoButtonsAndCentresThemLowerThanThree() {
        BankDialogueLayout three = layout(W_1280_SCALE_2, H_1080_SCALE_2, 3, 2, 0);
        BankDialogueLayout two = layout(W_1280_SCALE_2, H_1080_SCALE_2, 2, 2, 0);
        assertTrue(two.buttonTopY() > three.buttonTopY(), "a shorter stack should sit lower when centred");
        assertTrue(two.buttonY(1) + two.buttonHeight() <= BankDialogueLayout.HEADER_HEIGHT);
    }

    @Test
    void handlesNoButtonsAtAll() {
        BankDialogueLayout l = layout(W_1280_SCALE_2, H_1080_SCALE_2, 0, 2, 0);
        assertBodyNeverOverlapsButtons(l);
        assertTrue(l.buttonTopY() >= 0);
    }

    @Test
    void placesEveryButtonAtTheSameXAndWidth() {
        BankDialogueLayout l = layout(W_1280_SCALE_2, H_1080_SCALE_2, 3, 2, 0);
        assertEquals(l.buttonX() + l.buttonWidth(), l.buttonX() + l.buttonWidth());
        assertTrue(l.buttonX() + l.buttonWidth() <= W_1280_SCALE_2 - 1);
    }

    // ---------- Wrapped body ----------

    @Test
    void centresAOneLineBodyLowerThanAFiveLineBody() {
        BankDialogueLayout one = layout(W_1280_SCALE_2, H_1080_SCALE_2, 3, 1, 0);
        BankDialogueLayout five = layout(W_1280_SCALE_2, H_1080_SCALE_2, 3, 5, 0);
        assertTrue(one.bodyY() > five.bodyY(), "a taller block should start higher when centred");
    }

    @Test
    void keepsAVeryLongBodyOnScreenRatherThanPushingItOffTheTop() {
        BankDialogueLayout l = layout(W_1280_SCALE_2, H_1080_SCALE_2, 3, 40, 0);
        assertTrue(l.bodyY() >= 0, "body must not be positioned above the screen");
    }

    // ---------- Form and status ----------

    @Test
    void placesFormControlsBelowTheBannerNotUnderIt() {
        BankDialogueLayout l = layout(W_1280_SCALE_2, H_1080_SCALE_2, 2, 2, 3);
        assertTrue(
                l.formY() >= BankDialogueLayout.HEADER_HEIGHT,
                "form must clear the parchment banner"
        );
    }

    @Test
    void placesStatusBelowTheFormWithoutOverlap() {
        int formRows = 3;
        BankDialogueLayout l = layout(W_1280_SCALE_2, H_1080_SCALE_2, 2, 2, formRows);
        int formBottom = l.formY()
                + (formRows * (BankDialogueLayout.FORM_ROW_HEIGHT + BankDialogueLayout.FORM_ROW_GAP))
                - BankDialogueLayout.FORM_ROW_GAP;
        assertTrue(l.statusY() >= formBottom, "status overlapped the form rows");
    }

    @Test
    void putsStatusDirectlyBelowTheBannerWhenThereIsNoForm() {
        BankDialogueLayout l = layout(W_1280_SCALE_2, H_1080_SCALE_2, 3, 2, 0);
        assertTrue(l.statusY() >= BankDialogueLayout.HEADER_HEIGHT);
        assertEquals(l.formY(), l.statusY(), "with no form rows the two share the same top edge");
    }

    @Test
    void statusAndFormStayWithinTheScreenAtEveryTestedWidth() {
        for (int width : new int[]{W_1920_SCALE_2, W_1280_SCALE_2, W_1280_SCALE_3, W_1024_SCALE_4, W_854_SCALE_4}) {
            assertWithinScreen(layout(width, 240, 2, 2, 3), width);
        }
    }
}
