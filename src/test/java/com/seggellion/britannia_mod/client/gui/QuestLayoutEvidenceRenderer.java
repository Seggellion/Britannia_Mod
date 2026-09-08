package com.seggellion.britannia_mod.client.gui;

import com.seggellion.britannia_mod.quest.ClientQuestEntry;

import javax.imageio.ImageIO;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws the computed quest layouts to PNG files, so the milestone can be looked at rather than
 * only asserted.
 *
 * <h2>These are layout renderings, not screenshots</h2>
 * Nothing here launches Minecraft, loads a texture, or uses a {@code Font}. It takes the exact
 * rectangles {@link QuestDialogueLayout}, {@link QuestJournalLayout} and
 * {@link QuestMixingGuideLayout} return for a given screen size, and the real authored strings,
 * and draws labelled boxes and wrapped text at those numbers using {@link McFontMetrics} for the
 * measure. What the images prove is <b>where the layout puts things</b>: whether anything falls
 * outside the parchment, whether the columns collide, whether three choices fit, where the focus
 * ring lands. What they do not show is the parchment art, the portrait, the item sprites, or the
 * real glyphs -- those are drawn as labelled placeholders.
 *
 * <p>Deliberately a {@code main} rather than a {@code @Test}: writing two dozen PNGs is not
 * something the unit suite should do on every run. Run it against the compiled classes:
 * <pre>{@code
 * java -Djava.awt.headless=true \
 *      -Dbritannia.projectDir=<worktree> \
 *      -cp "build/classes/java/main;build/classes/java/test;<gson>" \
 *      com.seggellion.britannia_mod.client.gui.QuestLayoutEvidenceRenderer <output dir>
 * }</pre>
 * Only the layout records are loaded from the main classes, and none of them import a client type,
 * so no Minecraft jar is needed on the classpath.
 */
public final class QuestLayoutEvidenceRenderer {

    private QuestLayoutEvidenceRenderer() {
    }

    // Parchment palette, matching the colours QuestScreenDraw uses.
    private static final Color BACKDROP = new Color(0x2B2622);
    private static final Color SCREEN_FILL = new Color(0x1A1614);
    private static final Color PARCHMENT = new Color(0xE8DCC8);
    private static final Color PANEL = new Color(0xE8DCC8);
    private static final Color INK = new Color(0x111111);
    private static final Color HEADING = new Color(0x3D1B0F);
    private static final Color MUTED = new Color(0x5C321C);
    private static final Color DONE = new Color(0x2E7D32);
    private static final Color PENDING = new Color(0x7A4B2C);
    private static final Color CLAIM = new Color(0xB8860B);
    private static final Color BUTTON = new Color(0xC9B896);
    private static final Color BUTTON_EDGE = new Color(0x6B5A42);
    private static final Color ICON = new Color(0xA9BFD0);
    private static final Color ICON_EDGE = new Color(0x44606F);
    private static final Color FOCUS = new Color(0xB8860B);
    private static final Color PORTRAIT = new Color(0xB9A88C);
    private static final Color CAPTION_BG = new Color(0x14110F);
    private static final Color CAPTION_FG = new Color(0xD8CDBA);
    private static final Color OUTSIDE_WARNING = new Color(0xCC3333);
    /**
     * For anything the renderer adds that the screen does not draw <i>in place</i>. Distinct on
     * purpose: an annotation that looks like the screen's own text is a rendering that overstates
     * what a player sees, which is the one way these images can mislead.
     */
    private static final Color ANNOTATION = new Color(0x3E6E8E);

    private static final int CAPTION_HEIGHT = 72;
    private static final int PAD = 12;

    public static void main(String[] args) throws IOException {
        Path outDir = Path.of(args.length > 0 ? args[0]
                : "C:/Users/dusti/AppData/Local/Temp/claude/rowan-m8/evidence");
        Files.createDirectories(outDir);

        List<String> written = new ArrayList<>();

        // ---- one image per acceptance-matrix row, in the offer state ----
        int index = 0;
        for (GuiScaleRule.Row row : GuiScaleRule.acceptanceMatrix()) {
            index++;
            String name = String.format("matrix-%02d-%dx%d-%s.png", index,
                    row.scaledWidth(), row.scaledHeight(), slug(row.label()));
            written.add(write(outDir, name, renderDialogue(row, DialogueState.OFFER)));
        }

        // ---- one image per dialogue state, at a comfortable size and at the worst case ----
        GuiScaleRule.Row comfortable = GuiScaleRule.row("1920x1080 scale 2", 1920, 1080, 2, false);
        GuiScaleRule.Row worst = GuiScaleRule.worstCase();
        for (DialogueState state : DialogueState.values()) {
            if (state == DialogueState.HELP) continue;
            written.add(write(outDir, "state-" + state.name().toLowerCase(Locale.ROOT) + ".png",
                    renderDialogue(comfortable, state)));
            written.add(write(outDir,
                    "state-" + state.name().toLowerCase(Locale.ROOT) + "-worst-case.png",
                    renderDialogue(worst, state)));
        }

        // ---- the help view: the mixing guide ----
        written.add(write(outDir, "state-help-mixing-guide.png", renderMixingGuide(comfortable)));
        written.add(write(outDir, "state-help-mixing-guide-worst-case.png", renderMixingGuide(worst)));
        written.add(write(outDir, "state-help-mixing-guide-1024x768-scale4.png",
                renderMixingGuide(GuiScaleRule.row("1024x768 scale 4", 1024, 768, 4, false))));

        // ---- the journal, five stages ----
        written.add(write(outDir, "journal-five-stages.png", renderJournal(comfortable, -1, 0)));
        written.add(write(outDir, "journal-five-stages-worst-case.png", renderJournal(worst, -1, 0)));
        written.add(write(outDir, "journal-five-stages-1280x1024-scale4.png",
                renderJournal(GuiScaleRule.row("1280x1024 scale 4", 1280, 1024, 4, false), -1, 0)));
        written.add(write(outDir, "journal-five-stages-scrolled-to-end.png",
                renderJournal(GuiScaleRule.row("1280x1024 scale 4", 1280, 1024, 4, false), -1, 999)));
        written.add(write(outDir, "journal-quit-confirmation.png", renderJournal(comfortable, 4, 0)));

        System.out.println("wrote " + written.size() + " layout renderings to " + outDir);
        for (String name : written) {
            System.out.println("  " + name);
        }
    }

    // ================================================================ dialogue

    /** The dialogue states the milestone asks for evidence of. */
    private enum DialogueState { OFFER, WORKING, DONE, HELP }

    private static BufferedImage renderDialogue(GuiScaleRule.Row row, DialogueState state) {
        ClientQuestEntry.JournalDetail detail =
                ClientQuestEntry.JournalDetail.fromJournalEntry(RowanQuestContent.stage5JournalEntry());
        QuestNodePresentation node =
                QuestNodePresentation.fromNodeMetadata(RowanQuestContent.stage5NodeMetadata());

        String body = switch (state) {
            case OFFER -> RowanQuestContent.OFFER_BODY;
            case WORKING -> RowanQuestContent.WORKING_BODY;
            case DONE -> RowanQuestContent.DONE_BODY;
            case HELP -> "";
        };
        List<String> choices = switch (state) {
            case OFFER -> List.of("I will learn what you can teach.",
                    "Not today, farmer.", "How do I farm a public plot?");
            case WORKING -> List.of("(harvested)", "How do I farm a public plot?");
            case DONE -> List.of("I will take my pay.", "Farewell.");
            case HELP -> List.of();
        };

        // The reward preview the state shows. On completion, Rowan's coin, and the fertilized dirt
        // is kept either way -- both straight off the frozen fixture.
        //
        // The on-accept pair is NOT from the fixture: journal_entry_stage5.json's
        // rewards_preview.on_accept is an empty list, because by stage 5 the seed and the bowls
        // have long since been handed over. It is a stand-in, so the offer image shows a
        // three-section panel at all, and every caption that carries it says so.
        List<ClientQuestEntry.RewardItem> onAccept = state == DialogueState.OFFER
                ? List.of(new ClientQuestEntry.RewardItem("britannia_mod:carrot_seeds", 1),
                          new ClientQuestEntry.RewardItem("britannia_mod:empty_bowl", 2))
                : List.of();
        boolean syntheticOnAccept = !onAccept.isEmpty() && detail.rewardsOnAccept().isEmpty();
        List<ClientQuestEntry.RewardItem> onComplete = detail.rewardsOnComplete();
        List<ClientQuestEntry.RewardItem> keep = detail.keepItems();

        int width = row.scaledWidth();
        int height = row.scaledHeight();
        int wrapProbe = QuestDialogueLayout.probeWrapWidth(width, height, McFontMetrics.LINE_HEIGHT,
                true, choices.size());
        List<String> bodyLines = McFontMetrics.wrap(body, wrapProbe);

        QuestDialogueLayout l = QuestDialogueLayout.calculate(width, height,
                McFontMetrics.LINE_HEIGHT, true, true, true, choices.size(), bodyLines.size(),
                onAccept.size(), onComplete.size(), keep.size());
        // Re-wrap at the final width, which is what the screen draws with.
        bodyLines = McFontMetrics.wrap(body, l.body().wrapWidth());

        Canvas canvas = new Canvas(width, height, caption(row, l, state, syntheticOnAccept));
        Graphics2D g = canvas.graphics();

        // parchment
        fill(g, l.parchment(), PARCHMENT);
        outline(g, l.parchment(), new Color(0x8B6A45));

        // portrait column
        if (l.portraitVisible()) {
            fill(g, l.portrait().bounds(), PORTRAIT);
            outline(g, l.portrait().bounds(), BUTTON_EDGE);
            centered(g, "portrait", l.portrait().bounds(), MUTED);
            drawCentered(g, "Rowan", l.portrait().nameCenterX(), l.portrait().nameY(), INK);
            drawCentered(g, "Farmer", l.portrait().nameCenterX(), l.portrait().professionY(), MUTED);
        }

        // quest number
        if (!l.stageLine().isEmpty()) {
            String stage = detail.stage().known()
                    ? "Quest " + detail.stage().index() + " of " + detail.stage().count()
                    : "Quest";
            drawText(g, McFontMetrics.fit(stage, l.stageLine().width()),
                    l.stageLine().x(), l.stageLine().y(), HEADING);
        }

        // Body, clipped to the visible line budget. When it scrolls the last line of the box is
        // the hint's, exactly as QuestDecisionScreen draws it.
        int textLines = l.body().scrolls()
                ? Math.max(1, l.body().visibleLines() - 1)
                : l.body().visibleLines();
        drawLines(g, bodyLines, l.body().bounds(), textLines, INK);
        if (l.body().scrolls()) {
            drawText(g, "[v] Scroll for more", l.body().bounds().x(),
                    l.body().bounds().bottom() - McFontMetrics.LINE_HEIGHT, MUTED);
        }

        // choices
        for (int slot = 0; slot < l.choices().visibleCount() && slot < choices.size(); slot++) {
            ScreenRect at = l.choices().item(slot);
            fill(g, at, BUTTON);
            outline(g, at, BUTTON_EDGE);
            centered(g, McFontMetrics.fit(choices.get(slot), at.width() - 4), at, INK);
        }
        if (l.choices().scrolls()) {
            drawLine(g, l.choices().scrollHint(), "[v] Scroll for more",
                    l.choices().scrollHint().width(), MUTED);
        }

        // reward panel
        drawRewardPanel(g, l, onAccept, onComplete, keep);

        // focus ring on the first stop
        if (!l.focusRing().isEmpty()) {
            focusRing(g, l.focusRing().get(0).bounds());
        }

        canvas.markAnythingOutside(l.parchment(), l.body().bounds(), l.choices().bounds(),
                l.choices().scrollHint(), l.stageLine());
        if (l.rewards().visible()) {
            canvas.markAnythingOutside(l.rewards().panel());
        }
        return canvas.finish();
    }

    private static void drawRewardPanel(Graphics2D g, QuestDialogueLayout l,
                                        List<ClientQuestEntry.RewardItem> onAccept,
                                        List<ClientQuestEntry.RewardItem> onComplete,
                                        List<ClientQuestEntry.RewardItem> keep) {
        if (!l.rewards().visible()) return;
        // QuestScreenDraw.REWARD_PANEL_COLOR / REWARD_PANEL_EDGE_COLOR, without their alpha byte.
        // The renderer used to paint this ground when the screen painted none, which hid that the
        // three headings were sitting on the dim at about 1.6:1. The screen paints it now, so the
        // image is honest with the panel in it rather than honest with it removed.
        fill(g, l.rewards().panel(), new Color(0xD8CCB4));
        outline(g, l.rewards().panel(), new Color(0x8B6A45));

        for (QuestDialogueLayout.RewardBlock.Section section : l.rewards().sections()) {
            List<ClientQuestEntry.RewardItem> items = switch (section.kind()) {
                case ON_ACCEPT -> onAccept;
                case ON_COMPLETE -> onComplete;
                case KEEP -> keep;
            };
            String heading = switch (section.kind()) {
                case ON_ACCEPT -> "You receive now";
                case ON_COMPLETE -> "Reward when done";
                case KEEP -> "Keep for later";
            };
            drawText(g, McFontMetrics.fit(heading + " (" + items.size() + ")", section.label().width()),
                    section.label().x(), section.label().y(), HEADING);

            for (int i = 0; i < section.visibleIconCount() && i < items.size(); i++) {
                ScreenRect at = section.icon(i);
                fill(g, at, ICON);
                outline(g, at, ICON_EDGE);
                drawText(g, shortName(items.get(i).id()).substring(0, 2), at.x() + 2, at.y() + 4, INK);
            }
            // The item names the icons stand for, so a reader can check they are real names --
            // but the screen puts these in a TOOLTIP, on hover, and draws nothing here. Drawn in
            // the annotation colour and called out in the caption, because a reader who takes them
            // for screen text would believe the panel says more than it does.
            int y = section.icons().y() + QuestDialogueLayout.ICON_SIZE + 2;
            for (ClientQuestEntry.RewardItem item : items) {
                if (y + McFontMetrics.LINE_HEIGHT > section.bounds().bottom()) break;
                drawText(g, McFontMetrics.fit(shortName(item.id()) + " x" + item.count(),
                        section.bounds().width()), section.bounds().x(), y, ANNOTATION);
                y += McFontMetrics.LINE_HEIGHT;
            }
            if (section.overflowCount() > 0) {
                drawText(g, "+" + section.overflowCount() + " more",
                        section.bounds().x(), y, MUTED);
            }
        }
    }

    // ================================================================ mixing guide

    private static BufferedImage renderMixingGuide(GuiScaleRule.Row row) {
        List<QuestNodePresentation.GuideStep> guide = RowanQuestContent.mixingGuide();
        List<Boolean> offHand = new ArrayList<>();
        List<Integer> returned = new ArrayList<>();
        for (QuestNodePresentation.GuideStep step : guide) {
            offHand.add(step.usesOffHand());
            returned.add(step.returned().size());
        }

        int width = row.scaledWidth();
        int height = row.scaledHeight();
        int probe = QuestMixingGuideLayout.probeBodyWrapWidth(width, height, McFontMetrics.LINE_HEIGHT);
        List<String> bodyLines = McFontMetrics.wrap(RowanQuestContent.MIXING_BODY, probe);

        QuestMixingGuideLayout l = QuestMixingGuideLayout.calculate(width, height,
                McFontMetrics.LINE_HEIGHT, bodyLines.size(), offHand, returned, 0);
        bodyLines = McFontMetrics.wrap(RowanQuestContent.MIXING_BODY, l.bodyWrapWidth());

        Canvas canvas = new Canvas(width, height,
                row + "  |  mixing guide, " + l.rows().size() + " of " + l.totalRowCount()
                        + " steps drawn"
                        + (l.rows().isEmpty() ? "" : ", rows " + l.rows().get(0).style())
                        + "  |  no bucket row, by design");
        Graphics2D g = canvas.graphics();

        fill(g, l.panel(), PANEL);
        outline(g, l.panel(), new Color(0x8B6A45));

        drawLine(g, l.title(), RowanQuestContent.MIXING_TITLE, l.title().width(), HEADING);
        drawLines(g, bodyLines, l.body(), l.bodyVisibleLines(), INK);

        // The same condition QuestDecisionScreen.renderHelp uses: a guide with steps that produced
        // no rows says so rather than showing an empty panel.
        if (l.rows().isEmpty() && l.totalRowCount() > 0) {
            drawLine(g, l.list(), "Rowan has no guide for this step.", l.list().width(), MUTED);
        }

        for (QuestMixingGuideLayout.Row guideRow : l.rows()) {
            QuestNodePresentation.GuideStep step = guide.get(guideRow.stepIndex());
            iconBox(g, guideRow.mainHandIcon(), shortName(step.mainHand()));
            if (step.usesOffHand()) {
                iconBox(g, guideRow.offHandIcon(), shortName(step.offHand()));
            }
            if (!guideRow.arrow().isEmpty()) {
                g.setColor(MUTED);
                g.fillRect(guideRow.arrow().x(), guideRow.arrow().y(), guideRow.arrow().width(), 1);
                g.fillRect(guideRow.arrow().right() - 3, guideRow.arrow().y() - 2, 1, 5);
            }
            if (!guideRow.resultIcon().isEmpty()) {
                iconBox(g, guideRow.resultIcon(), shortName(step.result()));
            }
            for (int i = 0; i < guideRow.returnedIcons().size(); i++) {
                iconBox(g, guideRow.returnedIcons().get(i), shortName(step.returned().get(i)));
            }
            String sentence = (guideRow.stepIndex() + 1) + ". " + step.gesture();
            List<String> lines = McFontMetrics.wrap(sentence, guideRow.gestureWrapWidth());
            drawLines(g, lines, guideRow.gesture(),
                    Math.max(1, guideRow.gesture().height() / McFontMetrics.LINE_HEIGHT), INK);
            outline(g, guideRow.bounds(), new Color(0xCBBBA0));
        }

        ScreenRect back = l.backButton();
        fill(g, back, BUTTON);
        outline(g, back, BUTTON_EDGE);
        centered(g, "Back", back, INK);
        focusRing(g, back);

        canvas.markAnythingOutside(l.panel(), l.list(), l.body());
        return canvas.finish();
    }

    private static void iconBox(Graphics2D g, ScreenRect at, String label) {
        if (at.isEmpty()) return;
        fill(g, at, ICON);
        outline(g, at, ICON_EDGE);
        drawText(g, label.isEmpty() ? "?" : label.substring(0, Math.min(2, label.length())),
                at.x() + 2, at.y() + 4, INK);
    }

    // ================================================================ journal

    private static BufferedImage renderJournal(GuiScaleRule.Row row, int confirmingIndex, int scroll) {
        List<JournalEntry> entries = fiveStages();
        List<Integer> stepCounts = new ArrayList<>();
        List<Boolean> claimPending = new ArrayList<>();
        for (JournalEntry entry : entries) {
            stepCounts.add(entry.steps().size());
            claimPending.add(entry.claimPending());
        }

        QuestJournalLayout l = QuestJournalLayout.calculate(row.scaledWidth(), row.scaledHeight(),
                McFontMetrics.LINE_HEIGHT, stepCounts, claimPending, scroll, confirmingIndex);

        Canvas canvas = new Canvas(row.scaledWidth(), row.scaledHeight(),
                row + "  |  journal, showing " + l.visibleRowCount() + " of " + l.totalRowCount()
                        + " quests, scrollMax " + l.scrollMax()
                        + (l.confirmation().active() ? "  |  Quit confirmation armed" : ""));
        Graphics2D g = canvas.graphics();

        fill(g, l.panel(), PANEL);
        outline(g, l.panel(), new Color(0x8B6A45));
        drawText(g, McFontMetrics.fit("Quest Journal - showing " + l.visibleRowCount()
                        + " of " + l.totalRowCount(), l.header().width()),
                l.header().x(), l.header().y(), HEADING);

        for (QuestJournalLayout.Row jrow : l.rows()) {
            JournalEntry entry = entries.get(jrow.entryIndex());
            fill(g, jrow.bounds(), new Color(0xDCCFB6));
            outline(g, jrow.bounds(), new Color(0xCBBBA0));

            // Every line goes through drawLine, which skips an empty rectangle -- the guard
            // QuestJournalScreen.drawLine has. Drawing at an EMPTY rect's x/y put text at (0, 0),
            // in the top-left corner of the screen, in exactly the worst case the images exist to
            // show: a row so squeezed that the real screen draws that line not at all.
            drawLine(g, jrow.stageLabel(), "Quest " + (jrow.entryIndex() + 1) + " of "
                    + entries.size(), jrow.textWrapWidth(), HEADING);
            drawLine(g, jrow.title(), entry.name(), jrow.textWrapWidth(), HEADING);
            drawLine(g, jrow.objective(), "Next: " + entry.objective(), jrow.textWrapWidth(), INK);

            for (int i = 0; i < jrow.progressRows().size(); i++) {
                ClientQuestEntry.ProgressStep step = entry.steps().get(i);
                ScreenRect at = jrow.progressRows().get(i);
                drawText(g, McFontMetrics.fit((step.done() ? "[x] " : "[ ] ") + step.label(),
                                at.width()), at.x(), at.y(), step.done() ? DONE : PENDING);
            }
            if (jrow.hiddenSteps() > 0 && !jrow.progressRows().isEmpty()) {
                ScreenRect at = jrow.progressRows().get(jrow.progressRows().size() - 1);
                drawText(g, "+" + jrow.hiddenSteps() + " more steps", at.x(), at.y(), MUTED);
            }
            drawLine(g, jrow.claimBadge(), "[!] Return to Rowan to claim your reward",
                    jrow.claimBadge().width(), CLAIM);

            fill(g, jrow.quitButton(), BUTTON);
            outline(g, jrow.quitButton(), BUTTON_EDGE);
            centered(g, "Quit", jrow.quitButton(), INK);

            // Every rectangle the row actually draws into, not just the panel's three: a line
            // escaping its row is the failure these images are for.
            canvas.markAnythingOutside(jrow.bounds(), jrow.stageLabel(), jrow.title(),
                    jrow.objective(), jrow.claimBadge(), jrow.quitButton());
        }

        fill(g, l.closeButton(), BUTTON);
        outline(g, l.closeButton(), BUTTON_EDGE);
        centered(g, "Close", l.closeButton(), INK);

        if (l.confirmation().active()) {
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(0, 0, row.scaledWidth(), row.scaledHeight());
            fill(g, l.confirmation().panel(), PANEL);
            outline(g, l.confirmation().panel(), new Color(0x8B0000));
            String name = entries.get(l.confirmation().entryIndex()).name();
            List<String> lines = McFontMetrics.wrap("Quit \"" + name + "\"? It will be removed from"
                    + " your journal, and any progress on it is lost. This cannot be undone.",
                    l.confirmation().messageWrapWidth());
            drawLines(g, lines, l.confirmation().message(),
                    Math.max(1, l.confirmation().message().height() / McFontMetrics.LINE_HEIGHT), INK);

            fill(g, l.confirmation().confirmButton(), BUTTON);
            outline(g, l.confirmation().confirmButton(), BUTTON_EDGE);
            centered(g, "Quit quest", l.confirmation().confirmButton(), INK);
            fill(g, l.confirmation().cancelButton(), BUTTON);
            outline(g, l.confirmation().cancelButton(), BUTTON_EDGE);
            centered(g, "Keep it", l.confirmation().cancelButton(), INK);
        }

        if (!l.focusRing().isEmpty()) {
            focusRing(g, l.focusRing().get(0).bounds());
        }
        canvas.markAnythingOutside(l.panel(), l.list(), l.closeButton());
        return canvas.finish();
    }

    private record JournalEntry(String name, String objective,
                                List<ClientQuestEntry.ProgressStep> steps, boolean claimPending) {}

    /** The five quests, with stage 5's ordered progress taken from the frozen fixture. */
    private static List<JournalEntry> fiveStages() {
        ClientQuestEntry.JournalDetail stage5 =
                ClientQuestEntry.JournalDetail.fromJournalEntry(RowanQuestContent.stage5JournalEntry());
        return List.of(
                new JournalEntry("From Soil to Supper (1 of 5): Gather Dung",
                        "Gather dung from the pasture with your shovel.",
                        steps(new String[]{"Find the pasture", "Gather dung"}, 2), false),
                new JournalEntry("From Soil to Supper (2 of 5): Gather Dirt",
                        "Gather dirt, and take Rowan's bowls.",
                        steps(new String[]{"Take the bowls", "Gather dirt", "Return to Rowan"}, 3), false),
                new JournalEntry("From Soil to Supper (3 of 5): Fill a Bowl",
                        "Fill a bowl at the Water Well.",
                        steps(new String[]{"Find the well", "Fill a bowl", "Return to Rowan",
                                "Take the hoe"}, 2), false),
                new JournalEntry("From Soil to Supper (4 of 5): Mix Fertilized Dirt",
                        "Mix the bowls into fertilized dirt.",
                        steps(new String[]{"Bowl of Dirt", "Bowl of Fertile Dirt", "Bowl of Water",
                                "Final mix"}, 3), false),
                new JournalEntry("From Soil to Supper (5 of 5): Plant and Harvest",
                        stage5.objective(), stage5.progress(), true));
    }

    private static List<ClientQuestEntry.ProgressStep> steps(String[] labels, int doneCount) {
        List<ClientQuestEntry.ProgressStep> steps = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            steps.add(new ClientQuestEntry.ProgressStep("step_" + i, labels[i], i < doneCount));
        }
        return steps;
    }

    // ================================================================ drawing

    /**
     * An image with the screen area at a legible zoom and a caption band under it.
     *
     * <p>Everything is drawn in scaled screen units; the zoom is a {@code Graphics2D} transform, so
     * the numbers in the drawing code are exactly the numbers the layout returned.
     */
    private static final class Canvas {
        private final int screenWidth;
        private final int screenHeight;
        private final int zoom;
        private final String caption;
        private final BufferedImage image;
        private final Graphics2D graphics;
        private final List<String> warnings = new ArrayList<>();

        Canvas(int screenWidth, int screenHeight, String caption) {
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
            this.caption = caption;
            this.zoom = Math.max(1, Math.min(4, 1280 / Math.max(1, screenWidth)));

            int imageWidth = (screenWidth * zoom) + (PAD * 2);
            int imageHeight = (screenHeight * zoom) + (PAD * 2) + CAPTION_HEIGHT;
            this.image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);

            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setColor(BACKDROP);
            g.fillRect(0, 0, imageWidth, imageHeight);
            g.setColor(SCREEN_FILL);
            g.fillRect(PAD, PAD, screenWidth * zoom, screenHeight * zoom);
            g.setColor(new Color(0x554A3E));
            g.drawRect(PAD, PAD, (screenWidth * zoom) - 1, (screenHeight * zoom) - 1);

            g.translate(PAD, PAD);
            g.scale(zoom, zoom);
            this.graphics = g;
        }

        Graphics2D graphics() {
            return graphics;
        }

        /** Records anything that escaped its container, so a bad layout is visible in the image. */
        void markAnythingOutside(ScreenRect... rects) {
            ScreenRect screen = new ScreenRect(0, 0, screenWidth, screenHeight);
            for (ScreenRect rect : rects) {
                if (!rect.isInside(screen)) {
                    warnings.add("OUTSIDE SCREEN: " + rect);
                    graphics.setColor(OUTSIDE_WARNING);
                    graphics.setStroke(new BasicStroke(1));
                    graphics.drawRect(rect.x(), rect.y(), rect.width() - 1, rect.height() - 1);
                }
            }
        }

        BufferedImage finish() {
            graphics.dispose();
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int top = (screenHeight * zoom) + (PAD * 2);
            g.setColor(CAPTION_BG);
            g.fillRect(0, top - PAD, image.getWidth(), CAPTION_HEIGHT + PAD);

            // The caption is wrapped to the image, because a caption clipped at the right edge
            // loses exactly the numbers it exists to report.
            g.setColor(CAPTION_FG);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            int y = top + 4;
            for (String line : wrapToWidth(g, caption, image.getWidth() - (PAD * 2))) {
                g.drawString(line, PAD, y);
                y += 13;
            }
            g.setColor(new Color(0x9A8E7C));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            for (String line : wrapToWidth(g, "Layout rendering, not a screenshot: computed "
                    + "rectangles and real authored strings, drawn with approximate vanilla font "
                    + "metrics. Zoom x" + zoom + ".", image.getWidth() - (PAD * 2))) {
                g.drawString(line, PAD, y);
                y += 11;
            }
            if (!warnings.isEmpty()) {
                g.setColor(OUTSIDE_WARNING);
                g.drawString(String.join("; ", warnings), PAD, y);
            }
            g.dispose();
            return image;
        }

        private List<String> wrapToWidth(Graphics2D g, String text, int maxWidth) {
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (g.getFontMetrics().stringWidth(candidate) > maxWidth && line.length() > 0) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            if (line.length() > 0) lines.add(line.toString());
            return lines;
        }
    }

    private static void fill(Graphics2D g, ScreenRect rect, Color color) {
        if (rect.isEmpty()) return;
        g.setColor(color);
        g.fillRect(rect.x(), rect.y(), rect.width(), rect.height());
    }

    private static void outline(Graphics2D g, ScreenRect rect, Color color) {
        if (rect.isEmpty()) return;
        g.setColor(color);
        g.setStroke(new BasicStroke(1));
        g.drawRect(rect.x(), rect.y(), rect.width() - 1, rect.height() - 1);
    }

    private static void focusRing(Graphics2D g, ScreenRect rect) {
        if (rect.isEmpty()) return;
        g.setColor(FOCUS);
        g.setStroke(new BasicStroke(1));
        g.drawRect(rect.x() - 1, rect.y() - 1, rect.width() + 1, rect.height() + 1);
    }

    /**
     * Draws text one glyph at a time at the vanilla advance widths, so a line lands where the
     * layout's wrap width said it would rather than where an AWT font happens to put it.
     */
    private static void drawText(Graphics2D g, String text, int x, int y, Color color) {
        if (text == null || text.isEmpty()) return;
        g.setColor(color);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, McFontMetrics.GLYPH_HEIGHT));
        int cursor = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != ' ') {
                g.drawString(String.valueOf(c), cursor, y + McFontMetrics.GLYPH_HEIGHT - 1);
            }
            cursor += McFontMetrics.charWidth(c);
        }
    }

    private static void drawCentered(Graphics2D g, String text, int centerX, int y, Color color) {
        drawText(g, text, centerX - (McFontMetrics.width(text) / 2), y, color);
    }

    private static void centered(Graphics2D g, String text, ScreenRect rect, Color color) {
        if (rect.isEmpty()) return;
        drawText(g, text, rect.x() + Math.max(0, (rect.width() - McFontMetrics.width(text)) / 2),
                rect.y() + Math.max(0, (rect.height() - McFontMetrics.LINE_HEIGHT) / 2), color);
    }

    /**
     * One clipped line, or nothing at all when the rectangle is empty.
     *
     * <p>{@code QuestJournalScreen.drawLine} returns on an empty rectangle, and so must this: a
     * layout that squeezed a line out returns {@link ScreenRect#EMPTY}, whose x and y are zero, so
     * drawing at them puts the text in the corner of the screen instead of nowhere.
     */
    private static void drawLine(Graphics2D g, ScreenRect at, String text, int wrapWidth,
                                 Color color) {
        if (at.isEmpty()) return;
        drawText(g, McFontMetrics.fit(text, wrapWidth), at.x(), at.y(), color);
    }

    private static void drawLines(Graphics2D g, List<String> lines, ScreenRect area,
                                  int visibleLines, Color color) {
        if (area.isEmpty()) return;
        for (int i = 0; i < visibleLines && i < lines.size(); i++) {
            drawText(g, lines.get(i), area.x(), area.y() + (i * McFontMetrics.LINE_HEIGHT), color);
        }
    }

    // ================================================================ misc

    /** "britannia_mod:bowl_of_water" -> "Bowl Of Water", so the images show names, not ids. */
    private static String shortName(String itemId) {
        if (itemId == null || itemId.isBlank()) return "";
        String path = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        StringBuilder out = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private static String caption(GuiScaleRule.Row row, QuestDialogueLayout l, DialogueState state,
                                  boolean syntheticOnAccept) {
        return row + "  |  " + state.name().toLowerCase(Locale.ROOT)
                + "  |  mode " + l.mode()
                + ", wrap " + l.body().wrapWidth() + " (legacy would be " + row.legacyWrapWidth() + ")"
                + ", body " + l.body().visibleLines() + "/" + l.body().totalLines() + " lines"
                + ", choices " + l.choices().visibleCount() + "/" + l.choices().totalCount()
                + (l.choices().scrolls()
                        ? (l.choices().scrollHint().isEmpty()
                                ? " (scrolls; no room for the affordance)"
                                : " (scrolls)")
                        : "")
                + ", rewards " + (l.rewards().visible() ? "shown" : "hidden")
                + (l.rewards().visible()
                        ? "  |  item names under the icons are TOOLTIP content, drawn inline in blue"
                                + " as an annotation; the screen shows them only on hover"
                        : "")
                + (syntheticOnAccept
                        ? "  |  the \"You receive now\" items are SYNTHETIC: the stage-5 fixture's"
                                + " rewards_preview.on_accept is empty"
                        : "");
    }

    private static String slug(String label) {
        return label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private static String write(Path dir, String name, BufferedImage image) throws IOException {
        File file = dir.resolve(name).toFile();
        ImageIO.write(image, "png", file);
        return name;
    }
}
