package com.seggellion.britannia_mod.client.gui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M8 items 3 and 9: the journal is responsive, its rows hold everything item 3 asks for, the focus
 * ring is real, and Quit is confirmed.
 *
 * <p>The five stages of the Rowan questline are the standing fixture: quest 1 of 5 through 5 of 5,
 * with the step counts the authored content gives them.
 */
class QuestJournalLayoutTest {

    private static final int FONT_LINE_HEIGHT = 9;

    /** Ordered progress step counts for the five authored quests. */
    private static final List<Integer> FIVE_STAGES = List.of(2, 3, 4, 4, 5);
    /** Stage 5 is the one waiting to be claimed at Rowan. */
    private static final List<Boolean> CLAIM_PENDING = List.of(false, false, false, false, true);

    private static QuestJournalLayout at(GuiScaleRule.Row row, int scroll, int confirming) {
        return QuestJournalLayout.calculate(row.scaledWidth(), row.scaledHeight(), FONT_LINE_HEIGHT,
                FIVE_STAGES, CLAIM_PENDING, scroll, confirming);
    }

    // ---------------------------------------------------------------- invariants

    private static void assertNothingOutsideItsBounds(QuestJournalLayout l, String where) {
        assertTrue(l.panel().isInside(l.screen()), where + ": panel off screen");
        assertTrue(l.header().isInside(l.panel()), where + ": header outside the panel");
        assertTrue(l.list().isInside(l.panel()), where + ": list outside the panel");
        assertTrue(l.closeButton().isInside(l.panel()), where + ": Close outside the panel");
        assertFalse(l.list().overlaps(l.closeButton()), where + ": list runs under Close");

        for (QuestJournalLayout.Row row : l.rows()) {
            assertTrue(row.bounds().isInside(l.list()), where + ": row outside the list viewport");
            assertTrue(row.textWrapWidth() > 0, where + ": row wrap width must be positive");
            assertTrue(row.quitButton().isInside(row.bounds()), where + ": Quit outside its row");
            assertTrue(row.stageLabel().isInside(row.bounds()), where + ": quest number outside its row");
            assertTrue(row.title().isInside(row.bounds()), where + ": title outside its row");
            assertTrue(row.objective().isInside(row.bounds()), where + ": next action outside its row");
            assertTrue(row.claimBadge().isInside(row.bounds()), where + ": claim state outside its row");
            for (ScreenRect step : row.progressRows()) {
                assertTrue(step.isInside(row.bounds()), where + ": progress step outside its row");
                assertFalse(step.overlaps(row.quitButton()), where + ": progress step under Quit");
            }
            assertFalse(row.title().overlaps(row.quitButton()), where + ": title under Quit");
            assertFalse(row.objective().overlaps(row.quitButton()), where + ": next action under Quit");
            assertFalse(row.claimBadge().overlaps(row.quitButton()), where + ": claim state under Quit");
        }
        for (int i = 1; i < l.rows().size(); i++) {
            assertFalse(l.rows().get(i - 1).bounds().overlaps(l.rows().get(i).bounds()),
                    where + ": rows " + (i - 1) + " and " + i + " overlap");
        }
    }

    private static void assertFocusRingIsUsable(QuestJournalLayout l, String where) {
        assertFalse(l.focusRing().isEmpty(), where + ": nothing to focus");
        for (QuestJournalLayout.Focusable stop : l.focusRing()) {
            assertFalse(stop.bounds().isEmpty(), where + ": focus stop " + stop.kind() + " has no area");
            assertTrue(stop.bounds().isInside(l.screen()), where + ": focus stop off screen");
        }
    }

    @Test
    void holdsEveryInvariantAtEveryAcceptanceMatrixRow() {
        for (GuiScaleRule.Row row : GuiScaleRule.acceptanceMatrix()) {
            for (int scroll : new int[]{0, 1, 99}) {
                QuestJournalLayout l = at(row, scroll, -1);
                assertNothingOutsideItsBounds(l, row + " scroll=" + scroll);
                assertFocusRingIsUsable(l, row + " scroll=" + scroll);
            }
            QuestJournalLayout confirming = at(row, 0, 4);
            assertNothingOutsideItsBounds(confirming, row + " confirming");
            assertFocusRingIsUsable(confirming, row + " confirming");
        }
    }

    @Test
    void holdsEveryInvariantAcrossTheWholeReachableSizeRange() {
        for (int width = 160; width <= 1200; width += 3) {
            for (int height : new int[]{120, 150, 180, 192, 240, 256, 270, 360, 540, 1080}) {
                for (int count : new int[]{0, 1, 5, 12}) {
                    List<Integer> steps = new ArrayList<>();
                    List<Boolean> claims = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        steps.add(i % 7);
                        claims.add(i % 3 == 0);
                    }
                    QuestJournalLayout l = QuestJournalLayout.calculate(width, height,
                            FONT_LINE_HEIGHT, steps, claims, 0, -1);
                    String where = width + "x" + height + " n=" + count;
                    assertNothingOutsideItsBounds(l, where);
                    assertFocusRingIsUsable(l, where);
                }
            }
        }
    }

    // ---------------------------------------------------------------- responsiveness

    @Test
    void clampsThePanelToASmallScreenInsteadOfDrawingOffTheEdges() {
        // The old journal was a fixed 370x260 panel. At 320x256 -- a 1280x1024 window at GUI
        // scale 4 -- that is wider and taller than the screen it is centred on.
        QuestJournalLayout l = QuestJournalLayout.calculate(320, 256, FONT_LINE_HEIGHT,
                FIVE_STAGES, CLAIM_PENDING, 0, -1);
        assertTrue(l.panel().width() <= 320);
        assertTrue(l.panel().height() <= 256);
        assertTrue(l.panel().isInside(l.screen()));
        assertTrue(l.panel().width() < QuestJournalLayout.PANEL_MAX_WIDTH, "panel should have shrunk");
    }

    @Test
    void usesTheFullPanelWhenThereIsRoomForIt() {
        QuestJournalLayout l = QuestJournalLayout.calculate(960, 540, FONT_LINE_HEIGHT,
                FIVE_STAGES, CLAIM_PENDING, 0, -1);
        assertEquals(QuestJournalLayout.PANEL_MAX_WIDTH, l.panel().width());
        assertEquals(QuestJournalLayout.PANEL_MAX_HEIGHT, l.panel().height());
    }

    @Test
    void movesQuitUnderTheTextWhenTheRowIsTooNarrowToHoldBoth() {
        QuestJournalLayout wide = QuestJournalLayout.calculate(960, 540, FONT_LINE_HEIGHT,
                FIVE_STAGES, CLAIM_PENDING, 0, -1);
        QuestJournalLayout.Row wideRow = wide.rows().get(0);
        assertEquals(wideRow.bounds().y() + QuestJournalLayout.ROW_PADDING, wideRow.quitButton().y(),
                "on a wide row Quit sits beside the text, at the top");

        QuestJournalLayout narrow = QuestJournalLayout.calculate(160, 240, FONT_LINE_HEIGHT,
                FIVE_STAGES, CLAIM_PENDING, 0, -1);
        QuestJournalLayout.Row narrowRow = narrow.rows().get(0);
        assertTrue(narrowRow.quitButton().y() > narrowRow.title().y(),
                "on a narrow row Quit drops below the text");
        assertFalse(narrowRow.title().overlaps(narrowRow.quitButton()));
    }

    // ---------------------------------------------------------------- item 3 content

    @Test
    void everyRowHasRoomForTheQuestNumberTheNextActionAndItsProgress() {
        QuestJournalLayout l = QuestJournalLayout.calculate(960, 540, FONT_LINE_HEIGHT,
                FIVE_STAGES, CLAIM_PENDING, 0, -1);
        assertFalse(l.rows().isEmpty());
        for (QuestJournalLayout.Row row : l.rows()) {
            assertFalse(row.stageLabel().isEmpty(), "no room for the quest number");
            assertFalse(row.title().isEmpty(), "no room for the quest name");
            assertFalse(row.objective().isEmpty(), "no room for the next action");
            int expected = Math.min(FIVE_STAGES.get(row.entryIndex()),
                    QuestJournalLayout.MAX_STEPS_PER_ROW);
            assertEquals(expected, row.progressRows().size(),
                    "row " + row.entryIndex() + " lost progress steps");
            assertEquals(0, row.hiddenSteps());
        }
    }

    @Test
    void showsTheReturnToRowanStateOnlyOnTheQuestWaitingToBeClaimed() {
        QuestJournalLayout l = QuestJournalLayout.calculate(960, 540, FONT_LINE_HEIGHT,
                FIVE_STAGES, CLAIM_PENDING, 0, -1);
        for (QuestJournalLayout.Row row : l.rows()) {
            boolean expected = CLAIM_PENDING.get(row.entryIndex());
            assertEquals(expected, !row.claimBadge().isEmpty(),
                    "claim badge on row " + row.entryIndex() + " should be " + expected);
        }
    }

    @Test
    void tallerRowsForQuestsWithMoreSteps() {
        QuestJournalLayout l = QuestJournalLayout.calculate(960, 540, FONT_LINE_HEIGHT,
                List.of(1, 5), List.of(false, false), 0, -1);
        assertEquals(2, l.rows().size());
        assertTrue(l.rows().get(1).bounds().height() > l.rows().get(0).bounds().height(),
                "a five-step quest should not take the same room as a one-step quest");
    }

    @Test
    void countsStepsItCouldNotDrawInsteadOfLosingThem() {
        QuestJournalLayout l = QuestJournalLayout.calculate(960, 540, FONT_LINE_HEIGHT,
                List.of(20), List.of(false), 0, -1);
        QuestJournalLayout.Row row = l.rows().get(0);
        assertEquals(QuestJournalLayout.MAX_STEPS_PER_ROW, row.progressRows().size());
        assertEquals(20 - QuestJournalLayout.MAX_STEPS_PER_ROW, row.hiddenSteps());
    }

    // ---------------------------------------------------------------- scrolling

    @Test
    void scrollsRatherThanOverflowingAndClampsTheOffset() {
        QuestJournalLayout l = QuestJournalLayout.calculate(320, 256, FONT_LINE_HEIGHT,
                FIVE_STAGES, CLAIM_PENDING, 0, -1);
        assertTrue(l.scrolls(), "five stages should not fit in a 320x256 journal");

        QuestJournalLayout past = QuestJournalLayout.calculate(320, 256, FONT_LINE_HEIGHT,
                FIVE_STAGES, CLAIM_PENDING, 999, -1);
        assertEquals(l.scrollMax(), past.firstVisibleRow(), "an offset past the end is clamped");
        assertFalse(past.rows().isEmpty(), "the last page still shows something");
        assertNothingOutsideItsBounds(past, "scrolled to the end");
    }

    @Test
    void theLastScrollPositionShowsTheLastQuest() {
        QuestJournalLayout l = QuestJournalLayout.calculate(320, 256, FONT_LINE_HEIGHT,
                FIVE_STAGES, CLAIM_PENDING, 999, -1);
        int lastShown = l.rows().get(l.rows().size() - 1).entryIndex();
        assertEquals(FIVE_STAGES.size() - 1, lastShown,
                "scrolling to the end must reach the final quest, not stop short of it");
    }

    // ---------------------------------------------------------------- empty and confirm

    @Test
    void anEmptyJournalStillHasSomethingToFocus() {
        QuestJournalLayout l = QuestJournalLayout.calculate(960, 540, FONT_LINE_HEIGHT,
                List.of(), List.of(), 0, -1);
        assertTrue(l.isEmpty());
        assertEquals(1, l.focusRing().size());
        assertEquals(QuestJournalLayout.FocusKind.CLOSE, l.focusRing().get(0).kind());
    }

    @Test
    void theQuitConfirmationIsTheOnlyThingFocusableWhileItIsUp() {
        // A confirmation a player can Tab out of, to quit a different quest behind it, is not a
        // confirmation. Cancel comes first so a stray Enter keeps the quest.
        QuestJournalLayout l = QuestJournalLayout.calculate(960, 540, FONT_LINE_HEIGHT,
                FIVE_STAGES, CLAIM_PENDING, 0, 2);
        assertTrue(l.confirmation().active());
        assertEquals(2, l.confirmation().entryIndex());
        assertEquals(2, l.focusRing().size());
        assertEquals(QuestJournalLayout.FocusKind.CONFIRM_NO, l.focusRing().get(0).kind());
        assertEquals(QuestJournalLayout.FocusKind.CONFIRM_YES, l.focusRing().get(1).kind());
    }

    @Test
    void theConfirmationFitsInsideThePanelAtEveryMatrixRow() {
        for (GuiScaleRule.Row row : GuiScaleRule.acceptanceMatrix()) {
            QuestJournalLayout l = at(row, 0, 0);
            QuestJournalLayout.Confirmation confirmation = l.confirmation();
            assertTrue(confirmation.panel().isInside(l.screen()), "confirmation off screen at " + row);
            assertTrue(confirmation.confirmButton().isInside(confirmation.panel()),
                    "Confirm outside its box at " + row);
            assertTrue(confirmation.cancelButton().isInside(confirmation.panel()),
                    "Cancel outside its box at " + row);
            assertFalse(confirmation.confirmButton().overlaps(confirmation.cancelButton()),
                    "the two answers overlap at " + row);
            assertTrue(confirmation.messageWrapWidth() > 0, "non-positive wrap width at " + row);
        }
    }

    @Test
    void noConfirmationMeansTheOrdinaryFocusRing() {
        QuestJournalLayout l = QuestJournalLayout.calculate(960, 540, FONT_LINE_HEIGHT,
                FIVE_STAGES, CLAIM_PENDING, 0, -1);
        assertFalse(l.confirmation().active());
        List<QuestJournalLayout.Focusable> ring = l.focusRing();
        // Every visible row's Quit, in row order, then Close.
        for (int i = 0; i < l.rows().size(); i++) {
            assertEquals(QuestJournalLayout.FocusKind.ROW_QUIT, ring.get(i).kind());
            assertEquals(l.rows().get(i).entryIndex(), ring.get(i).index());
            assertEquals(l.rows().get(i).quitButton(), ring.get(i).bounds());
        }
        assertEquals(QuestJournalLayout.FocusKind.CLOSE, ring.get(ring.size() - 1).kind());
    }
}
