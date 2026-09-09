package com.seggellion.britannia_mod.client.gui;

import com.seggellion.britannia_mod.dialogue.DialogueLayout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rowan farming questline M11, deferred defect 1: the legacy {@link DialogueLayout}'s wrap width.
 *
 * <p>M8 fixed the quest path by giving it {@link QuestDialogueLayout} and recorded that the shared
 * class was still computing {@code screenWidth - 343}, which is non-positive at 342 units and below
 * -- eight of the eighteen rows of {@link GuiScaleRule#acceptanceMatrix()}. Its remaining consumers
 * are {@code ServiceDialogueScreen} and {@code GrabbyDestructionScreen}, and both <i>measure</i>
 * their body with that number and then <i>draw</i> with it.
 *
 * <p>Same shape of test as {@link QuestDialogueLayoutTest}: the whole reachable width range rather
 * than samples, because the widths that matter are the ones where the portrait drops out and where
 * the button column narrows, and no hand-picked list finds those.
 */
class ServiceDialogueLayoutTest {

    private static final int FONT_LINE_HEIGHT = 9;

    private static DialogueLayout at(int width, boolean hasPortrait) {
        return DialogueLayout.calculate(width, 4, 3, FONT_LINE_HEIGHT, true, hasPortrait);
    }

    // ---------------------------------------------------------------- the defect

    @Test
    void theWrapWidthIsPositiveAtEveryReachableConfiguration() {
        for (GuiScaleRule.Row row : GuiScaleRule.acceptanceMatrix()) {
            DialogueLayout l = at(row.scaledWidth(), true);
            assertTrue(l.maxTextWidth() > 0,
                    "wrap width " + l.maxTextWidth() + " at " + row);
        }
    }

    @Test
    void theWrapWidthIsPositiveAcrossTheWholeReachableWidthRange() {
        // From 160, the same floor QuestDialogueLayoutTest sweeps from: 160x120 is Minecraft's own
        // base window with Force Unicode Font on, and the arithmetic in Window#calculateScale
        // cannot produce anything narrower.
        for (int width = 160; width <= 2000; width++) {
            for (boolean portrait : new boolean[]{true, false}) {
                DialogueLayout l = at(width, portrait);
                String where = width + (portrait ? " with portrait" : " without portrait");
                assertTrue(l.maxTextWidth() > 0, where + ": wrap width " + l.maxTextWidth());
                assertTrue(l.buttonWidth() > 0, where + ": button column vanished");
                assertTrue(l.textX() + l.maxTextWidth() <= l.buttonStartX(),
                        where + ": the text column ran into the buttons");
            }
        }
    }

    @Test
    void theEightMatrixRowsTheLegacyArithmeticBrokeAreNoLongerBroken() {
        int broken = 0;
        for (GuiScaleRule.Row row : GuiScaleRule.acceptanceMatrix()) {
            if (row.legacyWrapWidth() > 0) continue;
            broken++;
            DialogueLayout l = at(row.scaledWidth(), true);
            assertTrue(l.maxTextWidth() > 0, "still non-positive at " + row);
            assertFalse(l.portraitVisible(),
                    "the portrait column is what has to give way at " + row);
        }
        assertEquals(8, broken,
                "the acceptance matrix no longer has the eight rows this defect was recorded for");
    }

    // ---------------------------------------------------------------- degradation order

    @Test
    void aRoomyScreenIsUnchangedFromBeforeTheFix() {
        // The pre-M11 numbers, which ServiceDialogueControllerTest also pins. Fixing the narrow
        // band must not move the wide one by a unit.
        DialogueLayout l = at(800, true);
        assertTrue(l.portraitVisible());
        assertEquals(163, l.textX());
        assertEquals(640, l.buttonStartX());
        assertEquals(DialogueLayout.BUTTON_WIDTH, l.buttonWidth());
        assertEquals(457, l.maxTextWidth());
    }

    @Test
    void thePortraitGoesBeforeTheTextColumnIsSquashed() {
        // 463 is the last width at which the portrait arrangement still leaves MIN_TEXT_WIDTH.
        assertTrue(at(463, true).portraitVisible());
        assertEquals(DialogueLayout.MIN_TEXT_WIDTH, at(463, true).maxTextWidth());

        DialogueLayout dropped = at(462, true);
        assertFalse(dropped.portraitVisible(), "the portrait is the first thing to go");
        assertEquals(DialogueLayout.PORTRAIT_X, dropped.textX());
        assertTrue(dropped.maxTextWidth() >= DialogueLayout.MIN_TEXT_WIDTH,
                "dropping the portrait has to buy back a usable column");
    }

    @Test
    void theButtonColumnNarrowsOnlyAfterThePortraitHasAlreadyGone() {
        // 342x256 is a 1024x768 window at GUI scale 4 -- where the legacy class wrapped to -1.
        DialogueLayout l = at(342, true);
        assertFalse(l.portraitVisible());
        assertEquals(342 - DialogueLayout.BUTTON_WIDTH - DialogueLayout.GUTTER, l.buttonStartX());
        assertEquals(DialogueLayout.BUTTON_WIDTH, l.buttonWidth(),
                "there was still room for a full-width button column here");
        assertEquals(342 - 210, l.maxTextWidth());

        // 256x192 is the worst reachable case: a 1024x768 window with Force Unicode Font on.
        DialogueLayout worst = at(256, true);
        assertFalse(worst.portraitVisible());
        assertTrue(worst.buttonWidth() < DialogueLayout.BUTTON_WIDTH,
                "the button column has to give way once the portrait is not enough");
        assertTrue(worst.buttonWidth() >= DialogueLayout.MIN_BUTTON_WIDTH,
                "a button too narrow to hold a word is not a button");
        assertTrue(worst.maxTextWidth() > 0);
    }

    @Test
    void aViewWithNoPortraitNeverReservesAColumnForOne() {
        // The Grabby confirmation. It has no quest giver, so the whole width is its sentence's.
        DialogueLayout l = at(800, false);
        assertFalse(l.portraitVisible());
        assertEquals(DialogueLayout.PORTRAIT_X, l.textX());
        assertEquals(590, l.maxTextWidth());
        assertTrue(l.maxTextWidth() > at(800, true).maxTextWidth(),
                "a view with no picture should not be narrower than one with a picture");
    }

    @Test
    void theVerticalGeometryIsUntouched() {
        // Only the horizontal arrangement was wrong; the three centred columns are unchanged, and a
        // change here would move every existing service screen for no reason.
        DialogueLayout l = at(800, true);
        // 20 with a profession label under the name, 21 without: the pre-M11 arithmetic exactly,
        // and QuestDialogueAdapterTest pins the 21.
        assertEquals(20, l.portraitY());
        assertEquals(21, DialogueLayout.calculate(800, 2, 2, FONT_LINE_HEIGHT, false, true).portraitY());
        assertEquals(Math.max(5, (DialogueLayout.TOP_SECTION_HEIGHT - (4 * FONT_LINE_HEIGHT)) / 2),
                l.textY());
        assertEquals(Math.max(5, (DialogueLayout.TOP_SECTION_HEIGHT - ((3 * 24) - 4)) / 2),
                l.buttonStartY());
    }
}
