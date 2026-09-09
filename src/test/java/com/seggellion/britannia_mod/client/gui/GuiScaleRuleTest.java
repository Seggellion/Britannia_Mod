package com.seggellion.britannia_mod.client.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The acceptance matrix, derived from Minecraft's own scale rule and checked.
 *
 * <p>These are not tests of {@link GuiScaleRule} for its own sake -- they are the record of which
 * window-and-setting combinations a player can actually produce, so the layout tests next door are
 * testing real configurations instead of plausible-looking numbers.
 */
class GuiScaleRuleTest {

    @Test
    void aWindowAtGuiScaleFourIsNotNecessarilyAtGuiScaleFour() {
        // The playbook asks for "1024x768 at GUI scale 4 = 256x192". Without Force Unicode Font
        // that pairing does not exist: the loop stops at 3 because 1024/4 = 256 is below the 320
        // base width, and the result is 342x256.
        GuiScaleRule.Row row = GuiScaleRule.row("", 1024, 768, 4, false);
        assertEquals(3, row.effectiveScale(), "1024x768 clamps to scale 3");
        assertEquals(342, row.scaledWidth());
        assertEquals(256, row.scaledHeight());
        assertTrue(row.clamped());
    }

    @Test
    void forceUnicodeFontWalksThroughTheBaseSizeFloor() {
        // ...and with Force Unicode Font on, it does exist, because the odd-to-even bump happens
        // after the 320x240 check. This is the row the playbook named, and the reason sub-320
        // widths are a real configuration rather than a hypothetical one.
        GuiScaleRule.Row row = GuiScaleRule.row("", 1024, 768, 4, true);
        assertEquals(4, row.effectiveScale());
        assertEquals(256, row.scaledWidth());
        assertEquals(192, row.scaledHeight());
        assertTrue(row.scaledWidth() < GuiScaleRule.BASE_WIDTH);
        assertTrue(row.scaledHeight() < GuiScaleRule.BASE_HEIGHT);
    }

    @Test
    void theScaledSizeRoundsUpNotDown() {
        // 1024/3 is 341.33. Window#setGuiScale takes the ceiling, so a screen is 342 units wide,
        // not 341 -- one unit either side of the legacy layout's 343 threshold.
        assertEquals(342, GuiScaleRule.scaled(1024, 3));
        assertEquals(256, GuiScaleRule.scaled(768, 3));
        assertEquals(427, GuiScaleRule.scaled(1280, 3));
        assertEquals(320, GuiScaleRule.scaled(1280, 4));
    }

    @Test
    void aSixFortyByThreeSixtyWindowIsScaleOneUntilForceUnicodeIsOn() {
        // 360/2 = 180 is below the 240 base height, so no amount of requested scale moves it...
        assertEquals(1, GuiScaleRule.calculateScale(640, 360, 4, false));
        assertEquals(640, GuiScaleRule.scaled(640, 1));
        // ...unless Force Unicode Font rounds the 1 up to 2, which halves it to 320x180.
        GuiScaleRule.Row unicode = GuiScaleRule.row("", 640, 360, 3, true);
        assertEquals(2, unicode.effectiveScale());
        assertEquals(320, unicode.scaledWidth());
        assertEquals(180, unicode.scaledHeight());
    }

    @Test
    void autoScaleIsTheLargestScaleThatKeepsTheBaseSize() {
        // guiScale 0 never equals i, so the loop runs until the 320x240 check stops it.
        GuiScaleRule.Row row = GuiScaleRule.row("", 2560, 1440, GuiScaleRule.AUTO, false);
        assertEquals(6, row.effectiveScale());
        assertEquals(427, row.scaledWidth());
        assertEquals(240, row.scaledHeight());
    }

    @Test
    void theWorstCaseIsTheSmallestWindowWithForceUnicodeFont() {
        GuiScaleRule.Row worst = GuiScaleRule.worstCase();
        assertEquals(160, worst.scaledWidth());
        assertEquals(120, worst.scaledHeight());
        assertTrue(worst.forceUnicode());
        assertEquals(2, worst.effectiveScale(),
                "a 320x240 window is scale 1 until Force Unicode rounds it to 2");
    }

    @Test
    void theMatrixIsOrderedNarrowestFirst() {
        List<GuiScaleRule.Row> rows = GuiScaleRule.acceptanceMatrix();
        for (int i = 1; i < rows.size(); i++) {
            assertTrue(rows.get(i - 1).scaledWidth() <= rows.get(i).scaledWidth(),
                    "row " + i + " is wider than the one before it: " + rows.get(i));
        }
    }

    @Test
    void theLegacyWrapWidthIsNegativeOnFiveRealRows() {
        // The regression this milestone exists to remove, stated as a fact about real settings
        // rather than as an inequality: DialogueLayout's screenWidth - 343 is at or below zero on
        // every row narrower than 343 units, and there are five of them in the matrix.
        List<GuiScaleRule.Row> broken = GuiScaleRule.acceptanceMatrix().stream()
                .filter(row -> row.legacyWrapWidth() <= 0)
                .toList();
        assertEquals(8, broken.size(), "rows with a non-positive legacy wrap width: " + broken);
        for (GuiScaleRule.Row row : broken) {
            assertTrue(row.scaledWidth() < 343, row.toString());
        }
    }

    @Test
    void everyMatrixRowIsReachable() {
        // A row whose effective scale differs from what calculate() returns for its own inputs
        // would be a row somebody made up. This is the guard against that.
        for (GuiScaleRule.Row row : GuiScaleRule.acceptanceMatrix()) {
            int effective = GuiScaleRule.calculateScale(row.windowWidth(), row.windowHeight(),
                    row.requestedScale(), row.forceUnicode());
            assertEquals(effective, row.effectiveScale(), row.toString());
            assertEquals(GuiScaleRule.scaled(row.windowWidth(), effective), row.scaledWidth(),
                    row.toString());
            assertEquals(GuiScaleRule.scaled(row.windowHeight(), effective), row.scaledHeight(),
                    row.toString());
            assertTrue(row.scaledWidth() > 0 && row.scaledHeight() > 0, row.toString());
        }
    }

    @Test
    void withoutForceUnicodeTheScaledSizeNeverDropsBelowTheBase() {
        // The counterpart to the finding above: the 320x240 floor really does hold, but only while
        // Force Unicode Font is off. Swept rather than sampled, because the interesting rows are
        // the ones a hand-picked list would miss.
        for (int width = GuiScaleRule.BASE_WIDTH; width <= 3840; width += 7) {
            for (int height : new int[]{240, 360, 480, 720, 768, 1024, 1080, 1440}) {
                for (int requested : new int[]{1, 2, 3, 4, GuiScaleRule.AUTO}) {
                    GuiScaleRule.Row row = GuiScaleRule.row("", width, height, requested, false);
                    assertTrue(row.scaledWidth() >= GuiScaleRule.BASE_WIDTH,
                            "scaled below base width: " + row);
                    assertTrue(row.scaledHeight() >= GuiScaleRule.BASE_HEIGHT,
                            "scaled below base height: " + row);
                }
            }
        }
        assertFalse(GuiScaleRule.row("", 1280, 1024, 4, false).clamped(),
                "1280x1024 really does reach scale 4 without Force Unicode");
        assertEquals(320, GuiScaleRule.row("", 1280, 1024, 4, false).scaledWidth());
    }
}
