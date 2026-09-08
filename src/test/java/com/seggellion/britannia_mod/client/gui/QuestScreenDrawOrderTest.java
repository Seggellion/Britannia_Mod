package com.seggellion.britannia_mod.client.gui;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The draw order the quest screens have to keep, asserted against the source.
 *
 * <h2>Why the source and not the screen</h2>
 * A {@code Screen} cannot be instantiated by either test harness this project has -- that is the
 * premise the whole M8 layout split rests on -- so the geometry moved out to records JUnit can
 * reach and what is left is state and events. Draw <i>order</i> is neither: it is the sequence of
 * calls inside a method, and there is no seam that makes it a value. So it is checked where it
 * lives.
 *
 * <h2>The rule</h2>
 * {@code Screen#render} is
 * <pre>{@code
 *   public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
 *       this.renderBackground(g, mouseX, mouseY, partialTick);
 *       for (Renderable renderable : this.renderables) { renderable.render(...); }
 *   }
 * }</pre>
 * -- background first, then widgets. So anything a screen paints and then calls
 * {@code super.render} after is painted <b>twice-over</b>: once by the screen, and then covered by
 * whatever {@code renderBackground} draws on the second pass. {@code QuestDecisionScreen}'s help
 * view did exactly that, and its {@code renderBackground} fills the whole screen with
 * {@code 0xCC000000} for any view but the dialogue, so the mixing guide was an 80%-opaque black
 * rectangle with a Back button on it -- the button being a widget, drawn afterwards.
 *
 * <p>The fix is the shape the dialogue path and {@code ServiceDialogueScreen} already use: put
 * everything that belongs under the widgets in {@code renderBackground}, call {@code super.render}
 * first, and draw on top of it.
 */
class QuestScreenDrawOrderTest {

    private static final Path DECISION = JavaSource.MOD.resolve("client/gui/QuestDecisionScreen.java");
    private static final Path JOURNAL = JavaSource.MOD.resolve("client/gui/QuestJournalScreen.java");

    // ---------------------------------------------------------------- the help view

    @Test
    void theHelpViewDrawsAfterTheWidgetsNotBeforeThem() {
        String body = method(DECISION, "private void renderHelp");
        int superRender = body.indexOf("super.render(");
        assertTrue(superRender >= 0, "renderHelp no longer goes through Screen#render at all");

        for (String draw : new String[]{"graphics.drawString(", "QuestScreenDraw.drawWrapped(",
                "QuestScreenDraw.drawItemIcon(", "renderGuideTooltip("}) {
            int at = body.indexOf(draw);
            assertTrue(at < 0 || at > superRender,
                    draw + " runs before super.render, so renderBackground paints over it");
        }
    }

    @Test
    void theHelpPanelIsPaintedByTheBackgroundNotByTheView() {
        // The panel has to be under the Back button, and the only place that is under a widget is
        // the background pass.
        assertFalse(method(DECISION, "private void renderHelp").contains("guideLayout.panel()"),
                "renderHelp still paints the panel after the widgets have been drawn");
        assertTrue(method(DECISION, "public void renderBackground(").contains("guideLayout.panel()"),
                "the help panel is not painted by the background pass");
    }

    @Test
    void theDialogueViewStillDrawsAfterTheWidgetsToo() {
        // The path that was already correct, kept correct.
        String body = method(DECISION, "public void render(");
        int superRender = body.indexOf("super.render(");
        int firstDraw = body.indexOf("QuestScreenDraw.drawPortrait(");
        assertTrue(superRender >= 0 && firstDraw > superRender,
                "the dialogue body is drawn before the widgets");
    }

    // ---------------------------------------------------------------- the journal

    @Test
    void theJournalPaintsItselfInTheBackgroundPassAndNothingAfterTheWidgets() {
        // Same arrangement as the help view had: panel, header, rows and the quit confirmation were
        // all drawn and then super.render was called on top of them. It happened to survive only
        // because this screen's renderBackground override was empty, which is a property of the
        // override rather than of the ordering, and it stops being true the moment anybody gives
        // the journal a real background.
        String render = method(JOURNAL, "public void render(");
        assertTrue(render.contains("super.render("), "the journal stopped drawing its widgets");
        for (String draw : new String[]{"graphics.blit(", "drawHeader(", "drawRows(",
                "drawConfirmationBackdrop("}) {
            assertFalse(render.contains(draw), draw + " is drawn after the widgets");
        }

        String background = method(JOURNAL, "public void renderBackground(");
        for (String draw : new String[]{"graphics.blit(", "drawHeader(", "drawRows(",
                "drawConfirmationBackdrop("}) {
            assertTrue(background.contains(draw), draw + " is not in the background pass");
        }
    }

    @Test
    void theQuitConfirmationIsStillUnderItsOwnTwoButtons() {
        // The confirmation dims everything and then its buttons are drawn on top. In the background
        // pass that is automatic; the ordering within the pass still has to put the dim last, after
        // the rows it is dimming.
        String background = method(JOURNAL, "public void renderBackground(");
        assertTrue(background.indexOf("drawRows(") < background.indexOf("drawConfirmationBackdrop("),
                "the confirmation dim no longer covers the rows behind it");
    }

    // ---------------------------------------------------------------- keyboard reach

    @Test
    void thePageKeysReachTheHiddenChoices() {
        // Vanilla's Tab walks the widgets that were added, and only the visible choices are added,
        // so below about 192 units the second and third choices had no keyboard path at all -- while
        // the body, which a mouse can scroll by hovering it, owned both page keys.
        String body = method(DECISION, "public boolean keyPressed");
        int choiceBranch = body.indexOf("layout.choices().scrolls()");
        int bodyBranch = body.indexOf("layout.body().scrolls()");
        assertTrue(choiceBranch >= 0, "the page keys never reach the choice column");
        assertTrue(bodyBranch >= 0, "the body lost its page keys");
        assertTrue(choiceBranch < bodyBranch,
                "the body still takes the page keys ahead of the choices nobody else can reach");
        assertTrue(body.contains("choiceScroll"), "the choice branch does not move the choice scroll");
        assertTrue(body.contains("GLFW.GLFW_KEY_PAGE_UP") && body.contains("GLFW.GLFW_KEY_PAGE_DOWN"),
                "the page keys are gone");
    }

    @Test
    void theChoiceColumnsAffordanceIsActuallyDrawn() {
        String body = method(DECISION, "public void render(");
        assertTrue(body.contains("layout.choices().scrolls()")
                        && body.contains("drawChoiceScrollHint("),
                "the layout reserves room for a scroll affordance that nothing draws");
        assertTrue(method(DECISION, "private void drawChoiceScrollHint")
                        .contains("QuestScreenText.SCROLL_HINT"),
                "the choice affordance does not say what the body's says");
    }

    // ---------------------------------------------------------------- the reward panel

    @Test
    void theRewardPanelHasAGroundOfItsOwn() {
        // The three headings and the "+n more" line were drawn straight onto the 80%-opaque dim
        // under the parchment: QuestScreenDraw.HEADING_COLOR, a near-black brown, at about 1.6:1.
        // The evidence renderer painted a cream panel there that the screen did not, which is what
        // kept it from being noticed.
        String body = method(JavaSource.MOD.resolve("client/gui/QuestScreenDraw.java"),
                "public static void drawRewardPanel");
        int fill = body.indexOf("graphics.fill(");
        int firstHeading = body.indexOf("graphics.drawString(");
        assertTrue(fill >= 0, "the reward panel still has no background of its own");
        assertTrue(firstHeading > fill, "a heading is drawn before the ground it sits on");
        assertTrue(body.contains("REWARD_PANEL_COLOR"), "the ground is not a named colour");

        String renderer = JavaSource.read(JavaSource.PROJECT.resolve(
                "src/test/java/com/seggellion/britannia_mod/client/gui/"
                        + "QuestLayoutEvidenceRenderer.java"));
        assertTrue(renderer.contains("0xD8CCB4"),
                "the renderer's panel colour drifted from the screen's");
    }

    // ---------------------------------------------------------------- helper

    /**
     * The body of the first method whose declaration starts with {@code signaturePrefix}, comments
     * stripped, matched by braces.
     */
    private static String method(Path file, String signaturePrefix) {
        String source = JavaSource.withoutComments(file);
        int at = source.indexOf(signaturePrefix);
        assertTrue(at >= 0, file.getFileName() + " has no " + signaturePrefix);
        int open = source.indexOf('{', at);
        assertTrue(open >= 0, signaturePrefix + " has no body");

        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(open + 1, i);
            }
        }
        throw new IllegalStateException("unbalanced braces after " + signaturePrefix);
    }
}
