package com.seggellion.britannia_mod.client.gui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M8 item 6: the visual mixing guide, at every acceptance matrix row. */
class QuestMixingGuideLayoutTest {

    private static final int FONT_LINE_HEIGHT = 9;

    private static List<Boolean> offHandFlags() {
        List<Boolean> flags = new ArrayList<>();
        for (QuestNodePresentation.GuideStep step : RowanQuestContent.mixingGuide()) {
            flags.add(step.usesOffHand());
        }
        return flags;
    }

    private static List<Integer> returnedCounts() {
        List<Integer> counts = new ArrayList<>();
        for (QuestNodePresentation.GuideStep step : RowanQuestContent.mixingGuide()) {
            counts.add(step.returned().size());
        }
        return counts;
    }

    /**
     * How many lines the real intro body wraps to at this size.
     *
     * <p>This helper used to hand {@code calculate} a hardcoded 3. The screen does not: it measures
     * {@code help.body()} against {@code probeBodyWrapWidth} and passes whatever comes back, which
     * at the worst case is 4. Three lines fit a guide row by seven units and four do not, so the
     * hardcoded number was the difference between the case the tests exercised and the case a
     * player got -- a panel with a title, an introduction and no guide at all.
     */
    private static int bodyLineCount(int width, int height) {
        return McFontMetrics.wrap(RowanQuestContent.MIXING_BODY,
                QuestMixingGuideLayout.probeBodyWrapWidth(width, height, FONT_LINE_HEIGHT)).size();
    }

    private static QuestMixingGuideLayout at(GuiScaleRule.Row row, int scroll) {
        return at(row.scaledWidth(), row.scaledHeight(), scroll);
    }

    private static QuestMixingGuideLayout at(int width, int height, int scroll) {
        return QuestMixingGuideLayout.calculate(width, height, FONT_LINE_HEIGHT,
                bodyLineCount(width, height), offHandFlags(), returnedCounts(), scroll);
    }

    // ---------------------------------------------------------------- invariants

    private static void assertNothingOutsideItsBounds(QuestMixingGuideLayout l, String where) {
        // A guide that draws no rows at all is worse than a guide with a shorter introduction, and
        // it used to happen silently: at 160x120 the real four-line intro left thirty units for a
        // thirty-two-unit row, so the panel showed no recipe while scrolls() claimed otherwise.
        // The bar is what the panel could do with no introduction competing for the space.
        if (l.totalRowCount() > 0) {
            QuestMixingGuideLayout withoutIntro = QuestMixingGuideLayout.calculate(
                    l.screen().width(), l.screen().height(), FONT_LINE_HEIGHT, 0,
                    offHandFlags(), returnedCounts(), l.firstVisibleRow());
            if (!withoutIntro.rows().isEmpty()) {
                assertFalse(l.rows().isEmpty(),
                        where + ": the introduction squeezed every guide row off the panel");
            }
        }

        assertTrue(l.panel().isInside(l.screen()), where + ": panel off screen");
        assertTrue(l.title().isInside(l.panel()), where + ": title outside the panel");
        assertTrue(l.body().isInside(l.panel()), where + ": body outside the panel");
        assertTrue(l.list().isInside(l.panel()), where + ": guide outside the panel");
        assertTrue(l.backButton().isInside(l.panel()), where + ": Back outside the panel");
        assertTrue(l.bodyWrapWidth() > 0, where + ": body wrap width must be positive");
        assertFalse(l.list().overlaps(l.backButton()), where + ": guide runs under Back");
        assertFalse(l.body().overlaps(l.list()), where + ": body runs into the guide");

        for (QuestMixingGuideLayout.Row row : l.rows()) {
            assertTrue(row.bounds().isInside(l.list()), where + ": row outside the viewport");
            assertTrue(row.gestureWrapWidth() > 0, where + ": gesture wrap width must be positive");
            assertTrue(row.mainHandIcon().isInside(row.bounds()), where + ": main hand outside its row");
            assertTrue(row.offHandIcon().isInside(row.bounds()), where + ": off hand outside its row");
            assertTrue(row.resultIcon().isInside(row.bounds()), where + ": result outside its row");
            assertTrue(row.arrow().isInside(row.bounds()), where + ": arrow outside its row");
            assertTrue(row.gesture().isInside(row.bounds()), where + ": gesture outside its row");
            for (ScreenRect returned : row.returnedIcons()) {
                assertTrue(returned.isInside(row.bounds()), where + ": returned item outside its row");
            }

            assertFalse(row.mainHandIcon().overlaps(row.offHandIcon()), where + ": hands overlap");
            assertFalse(row.gesture().overlaps(row.mainHandIcon()), where + ": gesture over the main hand");
            assertFalse(row.gesture().overlaps(row.offHandIcon()), where + ": gesture over the off hand");
            assertFalse(row.gesture().overlaps(row.resultIcon()), where + ": gesture over the result");
            assertFalse(row.gesture().overlaps(row.arrow()), where + ": gesture over the arrow");
            for (ScreenRect returned : row.returnedIcons()) {
                assertFalse(row.gesture().overlaps(returned), where + ": gesture over a returned item");
                assertFalse(row.resultIcon().overlaps(returned), where + ": result over a returned item");
            }
        }
        for (int i = 1; i < l.rows().size(); i++) {
            assertFalse(l.rows().get(i - 1).bounds().overlaps(l.rows().get(i).bounds()),
                    where + ": guide rows overlap");
        }
    }

    @Test
    void holdsEveryInvariantAtEveryAcceptanceMatrixRow() {
        for (GuiScaleRule.Row row : GuiScaleRule.acceptanceMatrix()) {
            for (int scroll : new int[]{0, 1, 99}) {
                assertNothingOutsideItsBounds(at(row, scroll), row + " scroll=" + scroll);
            }
        }
    }

    @Test
    void holdsEveryInvariantAcrossTheWholeReachableSizeRange() {
        for (int width = 160; width <= 1200; width += 3) {
            for (int height : new int[]{120, 150, 180, 192, 240, 256, 270, 360, 540, 1080}) {
                // The real body's own line count is in the list, not only round numbers: the
                // defect this catches lived at exactly the count the screen computes.
                for (int bodyLines : new int[]{0, 2, 9, bodyLineCount(width, height)}) {
                    QuestMixingGuideLayout l = QuestMixingGuideLayout.calculate(width, height,
                            FONT_LINE_HEIGHT, bodyLines, offHandFlags(), returnedCounts(), 0);
                    assertNothingOutsideItsBounds(l, width + "x" + height + " body=" + bodyLines);
                }
            }
        }
    }

    @Test
    void showsTheRecipeAtEverySizeThePanelCouldShowItAt() {
        // The one the hardcoded body line count hid. Every acceptance-matrix row, with the intro
        // the screen really measures, at every scroll offset the screen can reach.
        for (GuiScaleRule.Row row : GuiScaleRule.acceptanceMatrix()) {
            for (int scroll : new int[]{0, 1, 99}) {
                QuestMixingGuideLayout l = at(row, scroll);
                assertFalse(l.rows().isEmpty(),
                        "no guide row drawn at " + row + " scroll=" + scroll
                                + " with the real " + bodyLineCount(row.scaledWidth(),
                                        row.scaledHeight()) + "-line introduction");
            }
        }
    }

    @Test
    void shedsIntroductionLinesRatherThanTheGuideItself() {
        GuiScaleRule.Row worst = GuiScaleRule.worstCase();
        int measured = bodyLineCount(worst.scaledWidth(), worst.scaledHeight());
        QuestMixingGuideLayout l = at(worst, 0);

        assertTrue(l.bodyVisibleLines() < measured,
                "at " + worst + " the whole " + measured + "-line introduction cannot fit and a row");
        assertTrue(l.bodyVisibleLines() > 0, "the introduction is shortened, not dropped");
        assertFalse(l.rows().isEmpty(), "the recipe is what the space is for");
    }

    // ---------------------------------------------------------------- content

    @Test
    void showsBothHandsTheResultAndBothReturnedBowlsOnTheFinalMix() {
        QuestMixingGuideLayout l = QuestMixingGuideLayout.calculate(640, 360, FONT_LINE_HEIGHT,
                3, offHandFlags(), returnedCounts(), 0);
        assertEquals(4, l.totalRowCount());
        assertEquals(4, l.rows().size(), "all four steps should fit on a 640x360 screen");

        QuestMixingGuideLayout.Row finalMix = l.rows().get(3);
        assertFalse(finalMix.mainHandIcon().isEmpty(), "the final mix needs a main hand");
        assertFalse(finalMix.offHandIcon().isEmpty(), "the final mix needs an off hand");
        assertFalse(finalMix.resultIcon().isEmpty(), "the final mix needs a result");
        assertEquals(2, finalMix.returnedIcons().size(), "both bowls come back");
        assertEquals(0, finalMix.hiddenReturned());
    }

    @Test
    void drawsNoOffHandForTheOneStepThatUsesASingleHand() {
        QuestMixingGuideLayout l = QuestMixingGuideLayout.calculate(640, 360, FONT_LINE_HEIGHT,
                3, offHandFlags(), returnedCounts(), 0);
        // Step 3 fills a bowl at the well with an empty off hand.
        assertTrue(l.rows().get(2).offHandIcon().isEmpty());
        assertFalse(l.rows().get(0).offHandIcon().isEmpty());
    }

    @Test
    void theGuideNeverMentionsTheBucket() {
        // The acceptance draft's step 22 is explicit: hands, ingredients, results and returned
        // bowls, with no mention of the bucket. Asserted against the authored content, and against
        // the layout, which has no bucket column to put one in.
        String everything = (RowanQuestContent.MIXING_TITLE + " " + RowanQuestContent.MIXING_BODY)
                .toLowerCase(Locale.ROOT);
        assertFalse(everything.contains("bucket"), "the guide's own text names a bucket");

        for (QuestNodePresentation.GuideStep step : RowanQuestContent.mixingGuide()) {
            String row = (step.mainHand() + " " + step.offHand() + " " + step.gesture() + " "
                    + step.result() + " " + String.join(" ", step.returned()))
                    .toLowerCase(Locale.ROOT);
            assertFalse(row.contains("bucket"), "guide step names a bucket: " + row);
        }
        for (String id : RowanQuestContent.mixingGuideItemIds()) {
            assertFalse(id.toLowerCase(Locale.ROOT).contains("bucket"),
                    "guide item is a bucket: " + id);
        }
    }

    // ---------------------------------------------------------------- arrangement

    @Test
    void keepsTheRowInlineWhileTheSentenceHasRoom() {
        QuestMixingGuideLayout l = QuestMixingGuideLayout.calculate(640, 360, FONT_LINE_HEIGHT,
                3, offHandFlags(), returnedCounts(), 0);
        assertEquals(QuestMixingGuideLayout.RowStyle.INLINE, l.rows().get(0).style());
        assertTrue(l.rows().get(0).gestureWrapWidth() >= QuestMixingGuideLayout.MIN_GESTURE_WIDTH);
    }

    @Test
    void wrapsTheSentenceUnderTheIconsWhenTheRowGetsNarrow() {
        GuiScaleRule.Row worst = GuiScaleRule.worstCase();
        QuestMixingGuideLayout l = at(worst, 0);
        assertFalse(l.rows().isEmpty(), "the guide must still show something at " + worst);
        assertEquals(QuestMixingGuideLayout.RowStyle.WRAPPED, l.rows().get(0).style());
        assertNothingOutsideItsBounds(l, worst.toString());
    }

    @Test
    void scrollsRatherThanOverflowingOnAShortScreen() {
        QuestMixingGuideLayout l = at(320, 180, 0);
        if (l.scrolls()) {
            assertTrue(l.rows().size() < l.totalRowCount());
            assertFalse(l.rows().isEmpty(), "scrolling means fewer rows, never none");
            QuestMixingGuideLayout end = at(320, 180, 999);
            assertEquals(l.scrollMax(), end.firstVisibleRow(), "an offset past the end is clamped");
            assertFalse(end.rows().isEmpty(), "the end of the list is still a row");
            assertNothingOutsideItsBounds(end, "320x180 scrolled to the end");
        }
        assertNothingOutsideItsBounds(l, "320x180");
    }

    @Test
    void theBodyProbeMeasuresAgainstThePanelNotTheScreen() {
        // Found by looking at a rendering: the help view measured its intro against the screen
        // width and then drew it wrapped to the panel width, so the line count was for a much
        // wider column and the end of the sentence was clipped away. The probe and the layout must
        // agree, and on any screen wider than the panel the probe must be the narrower of the two.
        for (GuiScaleRule.Row row : GuiScaleRule.acceptanceMatrix()) {
            int probed = QuestMixingGuideLayout.probeBodyWrapWidth(
                    row.scaledWidth(), row.scaledHeight(), FONT_LINE_HEIGHT);
            int actual = QuestMixingGuideLayout.calculate(row.scaledWidth(), row.scaledHeight(),
                    FONT_LINE_HEIGHT, 4, offHandFlags(), returnedCounts(), 0).bodyWrapWidth();
            assertEquals(actual, probed, "probe disagreed with the layout at " + row);
            assertTrue(probed > 0, "probe returned a non-positive width at " + row);
            assertTrue(probed <= row.scaledWidth(),
                    "probe measured a column wider than the screen at " + row);
        }
        assertTrue(QuestMixingGuideLayout.probeBodyWrapWidth(960, 540, FONT_LINE_HEIGHT) < 960,
                "on a wide screen the panel is the limit, not the screen");
    }

    @Test
    void anEmptyGuideStillProducesAUsablePanel() {
        QuestMixingGuideLayout l = QuestMixingGuideLayout.calculate(640, 360, FONT_LINE_HEIGHT,
                2, List.of(), List.of(), 0);
        assertEquals(0, l.totalRowCount());
        assertTrue(l.rows().isEmpty());
        assertFalse(l.backButton().isEmpty(), "Back must exist even with nothing to go back from");
        assertNothingOutsideItsBounds(l, "empty guide");
    }
}
