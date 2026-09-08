package com.seggellion.britannia_mod.client.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M8's required layout coverage for the quest dialogue, at every row of the derived acceptance
 * matrix.
 *
 * <p>GUI scale is not a parameter: Minecraft divides by it before a screen sees a coordinate, so a
 * scaled width already encodes it. {@link GuiScaleRule} says which scaled widths are reachable and
 * from what, so a failure here points at a setting somebody can reproduce rather than at a number.
 */
class QuestDialogueLayoutTest {

    private static final int FONT_LINE_HEIGHT = 9;

    private static QuestDialogueLayout at(GuiScaleRule.Row row, int choices, int bodyLines,
                                          int onAccept, int onComplete, int keep) {
        return QuestDialogueLayout.calculate(row.scaledWidth(), row.scaledHeight(),
                FONT_LINE_HEIGHT, true, true, true, choices, bodyLines, onAccept, onComplete, keep);
    }

    // ---------------------------------------------------------------- invariants

    private static void assertNothingOutsideItsBounds(QuestDialogueLayout l, String where) {
        assertTrue(l.parchment().isInside(l.screen()), where + ": parchment outside the screen");
        assertTrue(l.body().bounds().isInside(l.parchment()), where + ": body outside the parchment");
        assertTrue(l.choices().bounds().isInside(l.parchment()), where + ": choices outside the parchment");
        assertTrue(l.stageLine().isInside(l.parchment()), where + ": quest number outside the parchment");
        if (l.portraitVisible()) {
            assertTrue(l.portrait().bounds().isInside(l.parchment()), where + ": portrait outside the parchment");
        }
        for (int slot = 0; slot < l.choices().visibleCount(); slot++) {
            assertTrue(l.choices().item(slot).isInside(l.parchment()),
                    where + ": choice " + slot + " outside the parchment");
        }
        // The scroll affordance is real estate like anything else: it must be on the parchment and
        // it must not be on top of a button.
        ScreenRect hint = l.choices().scrollHint();
        assertTrue(hint.isInside(l.parchment()), where + ": scroll affordance outside the parchment");
        assertFalse(hint.overlaps(l.choices().bounds()),
                where + ": scroll affordance over the choice buttons");
        if (!l.choices().scrolls()) {
            assertTrue(hint.isEmpty(),
                    where + ": a column that does not scroll must not claim it does");
        }
        if (l.rewards().visible()) {
            assertTrue(l.rewards().panel().isInside(l.screen()), where + ": reward panel off screen");
            assertFalse(l.rewards().panel().overlaps(l.parchment()),
                    where + ": reward panel over the parchment");
            for (QuestDialogueLayout.RewardBlock.Section section : l.rewards().sections()) {
                assertTrue(section.bounds().isInside(l.rewards().panel()),
                        where + ": section outside the panel");
                assertTrue(section.label().isInside(section.bounds()),
                        where + ": heading outside its section");
                for (int i = 0; i < section.visibleIconCount(); i++) {
                    assertTrue(section.icon(i).isInside(section.icons()),
                            where + ": icon " + i + " outside its grid");
                }
            }
        }
    }

    private static void assertColumnsNeverCollide(QuestDialogueLayout l, String where) {
        assertTrue(l.body().wrapWidth() > 0,
                where + ": wrap width " + l.body().wrapWidth() + " must be positive");
        assertFalse(l.body().bounds().overlaps(l.choices().bounds()),
                where + ": body ran into the choice column");
        if (l.rewards().visible()) {
            assertFalse(l.body().bounds().overlaps(l.rewards().panel()),
                    where + ": body ran into the reward panel");
            assertFalse(l.choices().bounds().overlaps(l.rewards().panel()),
                    where + ": choices ran into the reward panel");
        }
        if (l.portraitVisible()) {
            assertFalse(l.portrait().bounds().overlaps(l.body().bounds()),
                    where + ": portrait ran into the body");
        }
    }

    private static void assertFocusRingIsUsable(QuestDialogueLayout l, String where) {
        List<QuestDialogueLayout.Focusable> ring = l.focusRing();
        assertFalse(ring.isEmpty(), where + ": nothing to focus");
        for (QuestDialogueLayout.Focusable stop : ring) {
            assertFalse(stop.bounds().isEmpty(), where + ": focus stop " + stop.kind() + " has no area");
            assertTrue(stop.bounds().isInside(l.screen()), where + ": focus stop off screen");
        }
        // Choices come first, in slot order, and every one of them is a drawn button.
        for (int slot = 0; slot < l.choices().visibleCount(); slot++) {
            QuestDialogueLayout.Focusable stop = ring.get(slot);
            assertEquals(QuestDialogueLayout.FocusKind.CHOICE, stop.kind(), where + ": stop " + slot);
            assertEquals(slot, stop.index(), where + ": choices out of order");
            assertEquals(l.choices().item(slot), stop.bounds(), where + ": stop " + slot + " is not the button");
        }
    }

    // ---------------------------------------------------------------- the matrix

    @Test
    void holdsEveryInvariantAtEveryAcceptanceMatrixRow() {
        for (GuiScaleRule.Row row : GuiScaleRule.acceptanceMatrix()) {
            // Three visible choices and a long localized body: the acceptance list's own case.
            QuestDialogueLayout l = at(row, 3, 24, 1, 1, 1);
            assertNothingOutsideItsBounds(l, row.toString());
            assertColumnsNeverCollide(l, row.toString());
            assertFocusRingIsUsable(l, row.toString());
        }
    }

    @Test
    void holdsEveryInvariantAcrossTheWholeReachableWidthRange() {
        // Brute force rather than samples: the invariants must hold at the exact widths where the
        // portrait drops out and where the arrangement stacks, which no hand-picked list finds.
        for (int width = 160; width <= 2000; width++) {
            for (int height : new int[]{120, 180, 192, 240, 256, 270, 360, 540, 1080}) {
                for (int choices : new int[]{0, 1, 3, 6}) {
                    QuestDialogueLayout l = QuestDialogueLayout.calculate(width, height,
                            FONT_LINE_HEIGHT, true, true, true, choices, 24, 2, 3, 1);
                    String where = width + "x" + height + " choices=" + choices;
                    assertNothingOutsideItsBounds(l, where);
                    assertColumnsNeverCollide(l, where);
                    assertFocusRingIsUsable(l, where);
                }
            }
        }
    }

    @Test
    void neverProducesTheNegativeWrapWidthTheLegacyLayoutDoes() {
        // DialogueLayout computes maxTextWidth as screenWidth - 343 and hands it to drawWordWrap.
        // Eight rows of the matrix are narrower than 343 units. This is the regression guard.
        for (GuiScaleRule.Row row : GuiScaleRule.acceptanceMatrix()) {
            QuestDialogueLayout l = at(row, 3, 24, 1, 1, 1);
            assertTrue(l.body().wrapWidth() > 0,
                    "wrap width " + l.body().wrapWidth() + " at " + row);
            if (row.legacyWrapWidth() <= 0) {
                assertNotEquals(row.legacyWrapWidth(), l.body().wrapWidth(),
                        "still reproducing the legacy value at " + row);
            }
        }
    }

    // ---------------------------------------------------------------- degradation

    @Test
    void keepsThePortraitOnARoomyScreen() {
        QuestDialogueLayout l = QuestDialogueLayout.calculate(960, 540, FONT_LINE_HEIGHT,
                true, true, true, 3, 4, 1, 1, 1);
        assertEquals(QuestDialogueLayout.Mode.FULL, l.mode());
        assertTrue(l.portraitVisible());
        assertTrue(l.body().bounds().x() >= l.portrait().bounds().right());
    }

    @Test
    void dropsThePortraitBeforeItSquashesTheText() {
        // 342x256 is a 1024x768 window at GUI scale 4. The legacy layout wrapped text to -1 here.
        QuestDialogueLayout l = QuestDialogueLayout.calculate(342, 256, FONT_LINE_HEIGHT,
                true, true, true, 3, 12, 1, 1, 1);
        assertEquals(QuestDialogueLayout.Mode.COMPACT, l.mode());
        assertFalse(l.portraitVisible());
        assertTrue(l.body().wrapWidth() >= QuestDialogueLayout.MIN_BODY_WIDTH);
    }

    @Test
    void stacksTheColumnsWhenSideBySideStopsWorking() {
        // 160x120 is the worst case: a 320x240 window with Force Unicode Font on.
        GuiScaleRule.Row worst = GuiScaleRule.worstCase();
        QuestDialogueLayout l = at(worst, 3, 24, 1, 1, 1);
        assertEquals(QuestDialogueLayout.Mode.STACKED, l.mode());
        assertTrue(l.body().bounds().bottom() <= l.choices().bounds().y(),
                "stacked body must sit above the choices");
        assertTrue(l.body().visibleLines() >= 1, "a stacked screen still says something");
        assertTrue(l.choices().visibleCount() >= 1);
    }

    @Test
    void hidesTheRewardPanelRatherThanDrawingItOverTheChoices() {
        // Too short for both. The preview goes; the conversation stays usable.
        QuestDialogueLayout l = QuestDialogueLayout.calculate(640, 100, FONT_LINE_HEIGHT,
                true, true, true, 3, 6, 2, 2, 2);
        assertFalse(l.rewards().visible());
        assertNothingOutsideItsBounds(l, "640x100");
        assertColumnsNeverCollide(l, "640x100");
    }

    // ---------------------------------------------------------------- scrolling

    @Test
    void scrollsLongBodyTextInsteadOfOverflowing() {
        QuestDialogueLayout l = QuestDialogueLayout.calculate(480, 270, FONT_LINE_HEIGHT,
                true, true, true, 3, 200, 1, 1, 1);
        assertTrue(l.body().scrolls());
        assertEquals(200 - l.body().visibleLines(), l.body().scrollMax());
        assertTrue(l.body().bounds().isInside(l.parchment()),
                "a 200-line body must still be clipped to the parchment");
    }

    @Test
    void scrollsALongChoiceListInsteadOfOverflowing() {
        QuestDialogueLayout l = QuestDialogueLayout.calculate(480, 270, FONT_LINE_HEIGHT,
                true, true, true, 20, 4, 0, 0, 0);
        assertTrue(l.choices().scrolls());
        assertEquals(20, l.choices().totalCount());
        assertEquals(20 - l.choices().visibleCount(), l.choices().scrollMax());
        for (int slot = 0; slot < l.choices().visibleCount(); slot++) {
            assertTrue(l.choices().item(slot).isInside(l.parchment()));
        }
    }

    @Test
    void aScrollingChoiceColumnSaysSoOnScreen() {
        // The defect: below about 192 units the second and third choices were drawn nowhere, marked
        // nowhere, and reachable only by rolling a mouse wheel over an unlabelled strip. An
        // affordance is what turns hidden content into content somebody looks for.
        QuestDialogueLayout l = QuestDialogueLayout.calculate(480, 270, FONT_LINE_HEIGHT,
                true, true, true, 20, 4, 0, 0, 0);
        assertTrue(l.choices().scrolls());
        ScreenRect hint = l.choices().scrollHint();
        assertFalse(hint.isEmpty(), "a scrolling choice column with nothing to say it scrolls");
        assertEquals(l.choices().bounds().x(), hint.x(), "the affordance belongs to that column");
        assertTrue(hint.y() >= l.choices().bounds().bottom(), "under the column, not over it");
        assertTrue(hint.isInside(l.parchment()));
    }

    @Test
    void theAffordanceIsThereAtEveryReachableSizeThatHidesAChoice() {
        // Reserved before the stack is sized rather than fitted around it afterwards: a centred
        // stack on a full-height parchment leaves about four units under the last button, less than
        // a line, so fitting it afterwards could only have put it on the dim below the banner or
        // dropped it. The only sizes allowed to have no affordance are the ones with no room for
        // the single mandatory button and a line -- and those are the ones the check names.
        for (GuiScaleRule.Row row : GuiScaleRule.acceptanceMatrix()) {
            QuestDialogueLayout l = at(row, 20, 4, 0, 0, 0);
            if (!l.choices().scrolls()) continue;
            ScreenRect hint = l.choices().scrollHint();
            boolean noRoomAtAll = l.choices().visibleCount() == 1
                    && l.choices().bounds().height() + FONT_LINE_HEIGHT + 1
                            > l.parchment().height() - (QuestDialogueLayout.PARCHMENT_INSET * 2);
            assertTrue(!hint.isEmpty() || noRoomAtAll,
                    "hidden choices with nothing on screen to say so at " + row);
            assertTrue(hint.bottom() <= l.parchment().bottom(),
                    "the affordance ran off the parchment at " + row);
        }
    }

    @Test
    void threeChoicesFitWithoutScrollingAtEveryMatrixRowTallEnoughForThem() {
        for (GuiScaleRule.Row row : GuiScaleRule.acceptanceMatrix()) {
            QuestDialogueLayout l = at(row, 3, 6, 0, 0, 0);
            if (row.scaledHeight() >= 192) {
                assertEquals(3, l.choices().visibleCount(),
                        "three choices should fit at " + row);
                assertFalse(l.choices().scrolls(), "no scrolling needed at " + row);
            } else {
                // Shorter than that and they scroll -- which is the requirement, not a failure.
                assertTrue(l.choices().visibleCount() >= 1, row.toString());
                assertEquals(3, l.choices().visibleCount() + l.choices().scrollMax(), row.toString());
            }
        }
    }

    // ---------------------------------------------------------------- reward panel

    @Test
    void givesEachPresentRewardSectionItsOwnColumn() {
        QuestDialogueLayout l = QuestDialogueLayout.calculate(960, 540, FONT_LINE_HEIGHT,
                true, true, true, 3, 4, 2, 1, 3);
        assertTrue(l.rewards().visible());
        assertEquals(3, l.rewards().sections().size());
        assertEquals(QuestDialogueLayout.SectionKind.ON_ACCEPT, l.rewards().sections().get(0).kind());
        assertEquals(QuestDialogueLayout.SectionKind.ON_COMPLETE, l.rewards().sections().get(1).kind());
        assertEquals(QuestDialogueLayout.SectionKind.KEEP, l.rewards().sections().get(2).kind());
        for (int i = 1; i < l.rewards().sections().size(); i++) {
            assertFalse(l.rewards().sections().get(i - 1).bounds()
                            .overlaps(l.rewards().sections().get(i).bounds()),
                    "sections " + (i - 1) + " and " + i + " overlap");
        }
    }

    @Test
    void omitsASectionThatHasNothingInIt() {
        QuestDialogueLayout l = QuestDialogueLayout.calculate(960, 540, FONT_LINE_HEIGHT,
                true, true, true, 3, 4, 0, 2, 0);
        assertEquals(1, l.rewards().sections().size());
        assertEquals(QuestDialogueLayout.SectionKind.ON_COMPLETE, l.rewards().sections().get(0).kind());
    }

    @Test
    void countsOverflowingIconsRatherThanDrawingThemOutsideTheGrid() {
        // A section holding more than its grid can show must report the remainder, because the
        // heading's count is what keeps the panel honest about how much is really there.
        QuestDialogueLayout l = QuestDialogueLayout.calculate(320, 256, FONT_LINE_HEIGHT,
                true, true, true, 3, 4, 40, 1, 1);
        QuestDialogueLayout.RewardBlock.Section section = l.rewards().sections().get(0);
        assertEquals(40, section.iconCount());
        assertTrue(section.visibleIconCount() < section.iconCount());
        assertEquals(section.iconCount() - section.visibleIconCount(), section.overflowCount());
        for (int i = 0; i < section.visibleIconCount(); i++) {
            assertTrue(section.icon(i).isInside(section.icons()), "icon " + i + " escaped the grid");
        }
        assertTrue(section.icon(section.visibleIconCount()).isEmpty(),
                "an icon past the visible count has no rectangle at all");
    }

    // ---------------------------------------------------------------- misc

    @Test
    void alwaysOffersOneControlEvenWithNoChoices() {
        // The farewell button. A dialogue the keyboard cannot reach is a dialogue nobody can leave.
        QuestDialogueLayout l = QuestDialogueLayout.calculate(960, 540, FONT_LINE_HEIGHT,
                true, true, false, 0, 4, 0, 0, 0);
        assertEquals(1, l.choices().visibleCount());
        assertEquals(1, l.focusRing().size());
        assertEquals(QuestDialogueLayout.FocusKind.CHOICE, l.focusRing().get(0).kind());
    }

    @Test
    void probeWrapWidthAgreesWithTheLayoutItIsProbingFor() {
        // The screen measures the body with probeWrapWidth and then calculates with the result;
        // if the two disagreed the line count would be measured against the wrong width.
        for (GuiScaleRule.Row row : GuiScaleRule.acceptanceMatrix()) {
            int probed = QuestDialogueLayout.probeWrapWidth(row.scaledWidth(), row.scaledHeight(),
                    FONT_LINE_HEIGHT, true, 3);
            int actual = QuestDialogueLayout.calculate(row.scaledWidth(), row.scaledHeight(),
                    FONT_LINE_HEIGHT, true, false, true, 3, 1, 0, 0, 0).body().wrapWidth();
            assertEquals(actual, probed, "probe disagreed with the layout at " + row);
            assertTrue(probed > 0, "probe returned a non-positive width at " + row);
        }
    }

    @Test
    void marginGrowsWithTheScreenAndNeverEatsIt() {
        assertEquals(4, QuestDialogueLayout.margin(120));
        assertEquals(5, QuestDialogueLayout.margin(160));
        assertEquals(10, QuestDialogueLayout.margin(320));
        assertEquals(20, QuestDialogueLayout.margin(960));
        assertEquals(20, QuestDialogueLayout.margin(1920), "clamped, not proportional forever");
        for (int width = 100; width <= 4000; width++) {
            assertTrue(QuestDialogueLayout.margin(width) * 2 < width, "margin ate the screen at " + width);
        }
    }
}
