package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.client.Keybinds;
import com.seggellion.britannia_mod.client.gui.QuestDialogueLayout;
import com.seggellion.britannia_mod.client.gui.QuestKeyPrompt;
import com.seggellion.britannia_mod.client.gui.QuestMixingGuideLayout;
import com.seggellion.britannia_mod.client.gui.QuestNodePresentation;
import com.seggellion.britannia_mod.client.gui.QuestChoiceButton;
import com.seggellion.britannia_mod.client.gui.QuestDialogueTransition;
import com.seggellion.britannia_mod.client.gui.QuestHandinPresentation;
import com.seggellion.britannia_mod.client.gui.QuestScreenDraw;
import com.seggellion.britannia_mod.client.gui.QuestScreenText;
import com.seggellion.britannia_mod.client.gui.QuestTriggerResultPresentation;
import com.seggellion.britannia_mod.client.gui.ScreenRect;
import com.seggellion.britannia_mod.entity.QuestGiverEntity;
import com.seggellion.britannia_mod.quest.network.QuestClient;
import com.seggellion.britannia_mod.quest.ClientQuestEntry;
import com.seggellion.britannia_mod.quest.ClientQuestTable;
import com.seggellion.britannia_mod.quest.QuestManager;
import com.seggellion.britannia_mod.dialogue.DialogueOptionViewModel;
import com.seggellion.britannia_mod.dialogue.DialogueViewModel;
import com.seggellion.britannia_mod.dialogue.QuestDialogueAdapter;
import com.seggellion.britannia_mod.quest.network.QuestModels.QuestResponse;
import com.seggellion.britannia_mod.quest.network.QuestModels.QuestChoice;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The quest dialogue.
 *
 * <h2>Milestone 8</h2>
 * The geometry moved to {@link QuestDialogueLayout} and the drawing to {@link QuestScreenDraw}, for
 * the reason Decision 0 gives: a {@code Screen} cannot be instantiated by either test harness this
 * project has, so nothing worth asserting can live in this file. What is left is state and events.
 *
 * <p>The screen used {@code DialogueLayout}, whose {@code maxTextWidth} is
 * {@code screenWidth - 343} and goes negative below 343 scaled units -- reachable on a 1280x1024
 * window at GUI scale 4, and on any window with Force Unicode Font on. The quest path now computes
 * its own numbers; {@code DialogueLayout} is untouched because the service dialogue still uses it.
 *
 * <p>New here: the quest number, a reward panel with the three labelled sections, a help view
 * carrying the mixing guide, scrolling for long body text and long choice lists, and keyboard
 * focus. The choice buttons are added in the layout's focus order, so vanilla's own Tab handling
 * walks them in the order {@code QuestDialogueLayoutTest} asserts.
 */
public class QuestDecisionScreen extends Screen {

    /** Which of the screen's two views is showing. Help is presentation only; it sends nothing. */
    private enum View { DIALOGUE, HELP }

    private final String npcName;
    private final String npcGender;
    private final QuestResponse questState;
    private final DialogueViewModel dialogueView;
    private final UUID npcUuid;

    private boolean choiceMade = false;
    /** A transition has been sent and no reply has come back yet (M8 item 8). */
    private boolean awaitingServer = false;
    /**
     * The strict item hand-in's answer, when the last one carried it (protocol section 1.5).
     *
     * <p>Not final and not part of {@code questState}, because these answers deliberately do not
     * move the node: the shortfall, the refund notice and the failure reason all belong to the
     * screen the player is already looking at. Updating in place is also what removes the flicker
     * -- a hand-in answer no longer swaps this screen for an identical one.
     */
    private QuestHandinPresentation handin = QuestHandinPresentation.ABSENT;

    /**
     * How many lines the status block will take, computed at {@link #init} so the scroll range can
     * account for it. The layout is calculated before the block is known, so the body's own
     * {@code scrollMax} does not include it -- without this the last line or two of what the quest
     * giver just said became unreachable while a shortfall was on screen.
     */
    private int statusLines;

    private Component bodyComponent;
    private QuestDialogueLayout layout;
    private QuestMixingGuideLayout guideLayout;
    private QuestNodePresentation node = QuestNodePresentation.NONE;
    private ClientQuestEntry journalEntry;
    private String localDirections = "";

    private View view = View.DIALOGUE;
    private int bodyScroll;
    private int choiceScroll;
    private int guideScroll;

    // 1. OLD Constructor (Used by QuestEventHandlers for Environmental Popups)
    public QuestDecisionScreen(QuestResponse questState, String npcName, String npcGender) {
        this(questState, npcName, npcGender, null);
    }

    // 2. NEW Constructor (Portrait argument removed!)
    public QuestDecisionScreen(QuestResponse questState, String npcName, String npcGender, java.util.UUID npcUuid) {
        super(Component.translatable(QuestScreenText.TITLE_FALLBACK));
        this.questState = questState;
        this.npcName = npcName;
        this.npcGender = npcGender; // Store it
        this.npcUuid = npcUuid;
        this.dialogueView = QuestDialogueAdapter.from(questState, npcName, npcGender);
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        this.node = questState != null && questState.currentNode != null
                ? QuestNodePresentation.fromNodeMetadata(questState.currentNode.metadata)
                : QuestNodePresentation.NONE;
        this.journalEntry = findJournalEntry();
        this.localDirections = readLocalDirections();

        if (view == View.HELP) {
            initHelp();
        } else {
            initDialogue();
        }
    }

    // ------------------------------------------------------------ dialogue view

    private void initDialogue() {
        this.bodyComponent = QuestScreenDraw.literal(dialogueView.body());
        List<QuestChoice> choices = visibleChoices();
        boolean hasPortrait = !dialogueView.npcName().isEmpty();
        boolean hasProfession = !professionLabel().isBlank();

        // Two passes, as before: the wrap width depends only on the width, and the line count
        // depends on the wrap width, so one probe and one calculate is exact.
        int wrapWidth = QuestDialogueLayout.probeWrapWidth(
                this.width, this.height, QuestScreenDraw.lineHeight(this.font), hasPortrait, choices.size());
        int bodyLines = this.font.split(this.bodyComponent, Math.max(1, wrapWidth)).size();

        this.layout = QuestDialogueLayout.calculate(
                this.width, this.height, QuestScreenDraw.lineHeight(this.font), hasPortrait, hasProfession,
                stage().known(), choices.size(), bodyLines,
                rewardsOnAccept().size(), rewardsOnComplete().size(), keepItems().size());

        this.statusLines = statusLines().size();
        this.bodyScroll = clamp(this.bodyScroll, 0, layout.body().scrollMax() + this.statusLines);
        this.choiceScroll = clamp(this.choiceScroll, 0, layout.choices().scrollMax());

        QuestDialogueLayout.ChoiceBlock block = layout.choices();
        if (choices.isEmpty()) {
            ScreenRect at = block.item(0);
            addRenderableWidget(new QuestChoiceButton(at.x(), at.y(), at.width(), at.height(),
                    QuestScreenDraw.text(QuestScreenText.FAREWELL), btn -> this.onClose()));
            return;
        }

        boolean isInfoNode = "info".equals(dialogueView.nodeType());
        for (int slot = 0; slot < block.visibleCount(); slot++) {
            int index = choiceScroll + slot;
            if (index >= choices.size()) break;
            QuestChoice choice = choices.get(index);
            ScreenRect at = block.item(slot);

            Component label = isInfoNode && choices.size() == 1
                    ? QuestScreenDraw.text(QuestScreenText.NEXT)
                    : QuestScreenDraw.literal(choice.text);

            Button button = new QuestChoiceButton(at.x(), at.y(), at.width(), at.height(),
                    label, ignored -> onChoicePressed(choice));
            // Disabled while a claim is in flight, which is the visible half of the debounce: the
            // vanilla grey comes free, and the guard in handleChoice is the half that actually
            // stops a second transaction being minted.
            button.active = !lockedAt(index) && !awaitingServer;
            addRenderableWidget(button);
            if (slot == 0) {
                // Deterministic keyboard entry point: the first drawn choice, every time.
                setInitialFocus(button);
            }
        }
    }

    /**
     * A choice button.
     *
     * <p>A presentation choice opens its view here and returns. Nothing is sent, so the node that
     * owns the live objective stays current -- reading the guide cannot cost the player a step.
     */
    private void onChoicePressed(QuestChoice choice) {
        String presentation = node.presentationFor(choice == null ? "" : choice.id);
        if (!presentation.isBlank()) {
            this.view = View.HELP;
            this.guideScroll = 0;
            rebuild();
            return;
        }

        if (choice == null || choice.id == null || choice.id.trim().isEmpty()
                || "close".equalsIgnoreCase(choice.id)) {
            this.choiceMade = true;
            clearPendingOfferStateIfUnaccepted();
            this.onClose();
        } else if (isRejectChoice(choice) && !hasAcceptedQuestState(questState)) {
            this.choiceMade = true;
            clearPendingOfferStateIfUnaccepted();
            this.onClose();
        } else {
            handleChoice(choice);
        }
    }

    // ------------------------------------------------------------ help view

    private void initHelp() {
        QuestNodePresentation.Help help = node.help();
        List<Boolean> offHand = new ArrayList<>();
        List<Integer> returned = new ArrayList<>();
        for (QuestNodePresentation.GuideStep step : help.guide()) {
            offHand.add(step.usesOffHand());
            returned.add(step.returned().size());
        }

        // Measured against the panel's content width, not the screen's: the guide body wraps
        // inside the panel, and measuring against the screen counts far too few lines.
        int probeWrap = QuestMixingGuideLayout.probeBodyWrapWidth(
                this.width, this.height, QuestScreenDraw.lineHeight(this.font));
        int bodyLines = this.font.split(QuestScreenDraw.literal(help.body()), probeWrap).size();
        this.guideLayout = QuestMixingGuideLayout.calculate(this.width, this.height,
                QuestScreenDraw.lineHeight(this.font), bodyLines, offHand, returned, this.guideScroll);
        this.guideScroll = clamp(this.guideScroll, 0, guideLayout.scrollMax());

        ScreenRect back = guideLayout.backButton();
        Button button = new QuestChoiceButton(back.x(), back.y(), back.width(), back.height(),
                QuestScreenDraw.text(QuestScreenText.HELP_BACK), btn -> {
                    this.view = View.DIALOGUE;
                    rebuild();
                });
        addRenderableWidget(button);
        setInitialFocus(button);
    }

    private void rebuild() {
        this.clearWidgets();
        init();
    }

    // ------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (view == View.HELP) {
            renderHelp(graphics, mouseX, mouseY, partialTick);
            return;
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        if (layout == null) return;

        QuestScreenDraw.drawPortrait(graphics, this.font, layout,
                dialogueView.npcName(), dialogueView.npcGender(), professionLabel());
        QuestScreenDraw.drawStageLine(graphics, this.font, layout, stage());

        // When the body scrolls, the last line of the box belongs to the hint rather than to the
        // text. Drawing both in it puts one on top of the other, which is what a bare
        // "draw the hint at the bottom" does on a short screen.
        // Every line drawn at the foot of the body box is reserved out of the text area first. The
        // status notice used to be drawn straight over the last wrapped line whenever the body did
        // not scroll, which read as the parchment being corrupt rather than as a message.
        ScreenRect bodyBounds = layout.body().bounds();
        int line = QuestScreenDraw.lineHeight(this.font);
        int hint = layout.body().scrolls() ? 1 : 0;
        // Fitted, not merely reserved. A hand-in may name up to eight requirements, and on a small
        // window the block asked for more rows than the box has: the old arithmetic floored the text
        // area at one line and then drew the status over it, and any line that landed above the box
        // was silently dropped -- which for a shortfall meant losing the "You are still short:"
        // heading and leaving an unlabelled list. The tail is dropped instead of the head, and the
        // body always keeps a line.
        int room = Math.max(0, (bodyBounds.height() / Math.max(1, line)) - 1 - hint);
        List<Component> status = statusLines();
        if (status.size() > room) status = status.subList(0, room);
        int reserved = (hint + status.size()) * line;
        ScreenRect textArea = reserved == 0 ? bodyBounds
                : new ScreenRect(bodyBounds.x(), bodyBounds.y(), bodyBounds.width(),
                        Math.max(line, bodyBounds.height() - reserved));
        QuestScreenDraw.drawWrapped(graphics, this.font, bodyComponent, textArea,
                layout.body().wrapWidth(), bodyScroll, QuestScreenDraw.TEXT_COLOR);

        if (layout.body().scrolls()) {
            drawScrollHint(graphics, bodyBounds);
        }

        // The choice column scrolls too, and used to do it with nothing on screen to say so: below
        // about 192 units the second and third choices were reachable only by a mouse wheel over an
        // unmarked strip, and not at all from the keyboard. Same words and same colour as the
        // body's, so one affordance means one thing on this screen.
        if (layout.choices().scrolls()) {
            drawChoiceScrollHint(graphics, layout.choices().scrollHint());
        }

        drawStatusLines(graphics, bodyBounds, status, line);

        drawDirections(graphics);

        QuestScreenDraw.drawRewardPanel(graphics, this.font, layout,
                rewardsOnAccept(), rewardsOnComplete(), keepItems());
        QuestScreenDraw.drawRewardTooltip(graphics, this.font, layout,
                rewardsOnAccept(), rewardsOnComplete(), keepItems(), mouseX, mouseY);
    }

    /**
     * The help view.
     *
     * <p><b>{@code super.render} first, then draw on top</b> -- the same shape the dialogue path
     * uses, and the reason is {@code Screen#render}: it begins with
     * {@code this.renderBackground(...)} and only then draws the widgets. Painting the panel and its
     * contents and <i>then</i> calling {@code super.render} re-entered this class's
     * {@link #renderBackground} override, which fills the whole screen with {@code 0xCC000000} for
     * any view but the dialogue -- an 80%-opaque quad over everything just drawn. Only the Back
     * button survived, because a widget is drawn after the background. The panel fill and its
     * outline therefore live in the background override now, where they belong, and everything
     * below is drawn after the widgets rather than before them.
     */
    private void renderHelp(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (guideLayout == null) return;

        QuestNodePresentation.Help help = node.help();
        Component title = help.title().isBlank()
                ? QuestScreenDraw.text(QuestScreenText.HELP_TITLE_FALLBACK)
                : QuestScreenDraw.literal(help.title());
        QuestScreenDraw.drawLine(graphics, this.font, QuestScreenDraw.fit(this.font, title, guideLayout.title().width()),
                guideLayout.title().x(), guideLayout.title().y(), QuestScreenDraw.HEADING_COLOR);

        if (!guideLayout.body().isEmpty()) {
            QuestScreenDraw.drawWrapped(graphics, this.font, QuestScreenDraw.literal(help.body()),
                    guideLayout.body(), guideLayout.bodyWrapWidth(), 0, QuestScreenDraw.TEXT_COLOR);
        }

        List<QuestNodePresentation.GuideStep> guide = help.guide();
        // Two ways to end up with nothing to show, and both must say so. The node may carry no
        // guide at all, or the panel may be too short to place a single row -- which used to be
        // silent, because the message was tied to the content being empty rather than to the
        // layout having produced no rows.
        if (guide.isEmpty()
                || (guideLayout.rows().isEmpty() && guideLayout.totalRowCount() > 0)) {
            QuestScreenDraw.drawLine(graphics, this.font, QuestScreenDraw.text(QuestScreenText.GUIDE_EMPTY),
                    guideLayout.list().x(), guideLayout.list().y(), QuestScreenDraw.MUTED_COLOR);
        }
        for (QuestMixingGuideLayout.Row row : guideLayout.rows()) {
            if (row.stepIndex() >= guide.size()) continue;
            QuestNodePresentation.GuideStep step = guide.get(row.stepIndex());
            QuestScreenDraw.drawItemIcon(graphics, this.font, row.mainHandIcon(), step.mainHand(), 1);
            if (step.usesOffHand()) {
                QuestScreenDraw.drawItemIcon(graphics, this.font, row.offHandIcon(), step.offHand(), 1);
            }
            // M11 F13. The "produces" marker was a one-pixel rule and the returned items had no
            // marker at all, so the only thing telling the two icon groups apart on screen was
            // which side of the row they were on -- with the difference stated in a tooltip. Both
            // are now translated symbols, drawn in their own reserved rectangles.
            if (!row.arrow().isEmpty()) {
                drawMarker(graphics, row.arrow(), QuestScreenText.GUIDE_PRODUCES_MARKER);
            }
            if (!row.resultIcon().isEmpty() && !step.result().isBlank()) {
                QuestScreenDraw.drawItemIcon(graphics, this.font, row.resultIcon(), step.result(), 1);
            }
            if (!row.returnedMarker().isEmpty()) {
                drawMarker(graphics, row.returnedMarker(), QuestScreenText.GUIDE_RETURNED_MARKER);
            }
            for (int i = 0; i < row.returnedIcons().size() && i < step.returned().size(); i++) {
                QuestScreenDraw.drawItemIcon(graphics, this.font, row.returnedIcons().get(i),
                        step.returned().get(i), 1);
            }
            QuestScreenDraw.drawWrapped(graphics, this.font,
                    QuestScreenDraw.text(QuestScreenText.GUIDE_STEP, row.stepIndex() + 1,
                            QuestScreenDraw.literal(step.gesture())),
                    row.gesture(), row.gestureWrapWidth(), 0, QuestScreenDraw.TEXT_COLOR);
        }

        renderGuideTooltip(graphics, mouseX, mouseY);
    }

    /** Item tooltips in the guide, so every icon can be identified by its real name. */
    private void renderGuideTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        List<QuestNodePresentation.GuideStep> guide = node.help().guide();
        for (QuestMixingGuideLayout.Row row : guideLayout.rows()) {
            if (row.stepIndex() >= guide.size()) continue;
            QuestNodePresentation.GuideStep step = guide.get(row.stepIndex());
            if (hovered(row.mainHandIcon(), mouseX, mouseY)) {
                tooltip(graphics, QuestScreenText.GUIDE_MAIN_HAND, step.mainHand(), mouseX, mouseY);
                return;
            }
            if (step.usesOffHand() && hovered(row.offHandIcon(), mouseX, mouseY)) {
                tooltip(graphics, QuestScreenText.GUIDE_OFF_HAND, step.offHand(), mouseX, mouseY);
                return;
            }
            if (hovered(row.resultIcon(), mouseX, mouseY) && !step.result().isBlank()) {
                tooltip(graphics, QuestScreenText.GUIDE_RESULT, step.result(), mouseX, mouseY);
                return;
            }
            for (int i = 0; i < row.returnedIcons().size() && i < step.returned().size(); i++) {
                if (hovered(row.returnedIcons().get(i), mouseX, mouseY)) {
                    tooltip(graphics, QuestScreenText.GUIDE_RETURNED, step.returned().get(i), mouseX, mouseY);
                    return;
                }
            }
            // The marker answers for the group it labels, so hovering the symbol says what it means.
            if (hovered(row.returnedMarker(), mouseX, mouseY) && !step.returned().isEmpty()) {
                tooltip(graphics, QuestScreenText.GUIDE_RETURNED, step.returned().get(0), mouseX, mouseY);
                return;
            }
            if (hovered(row.arrow(), mouseX, mouseY) && !step.result().isBlank()) {
                tooltip(graphics, QuestScreenText.GUIDE_RESULT, step.result(), mouseX, mouseY);
                return;
            }
        }
    }

    /** One guide marker, centred in the rectangle the layout reserved for it. */
    private void drawMarker(GuiGraphics graphics, ScreenRect at, String key) {
        Component marker = QuestScreenDraw.text(key);
        // Screen units on both sides. font.width is in font units, TEXT_SCALE smaller, so the
        // marker used to sit about a tenth of its own width left of centre.
        int x = at.x() + Math.max(0, (at.width() - QuestScreenDraw.width(this.font, marker)) / 2);
        QuestScreenDraw.drawLine(graphics, this.font, QuestScreenDraw.fit(this.font, marker, at.width()),
                x, at.y(), QuestScreenDraw.MUTED_COLOR);
    }

    private void tooltip(GuiGraphics graphics, String roleKey, String itemId, int mouseX, int mouseY) {
        graphics.renderTooltip(this.font, List.of(
                        QuestScreenDraw.itemName(itemId).getVisualOrderText(),
                        QuestScreenDraw.text(roleKey).getVisualOrderText()),
                mouseX, mouseY);
    }

    private static boolean hovered(ScreenRect at, int mouseX, int mouseY) {
        return !at.isEmpty() && mouseX >= at.x() && mouseX < at.right()
                && mouseY >= at.y() && mouseY < at.bottom();
    }

    /**
     * The per-spawner directions hint (M6), and the journal key the player actually has bound.
     *
     * <p>The key name comes from the live {@code KeyMapping}, never from a letter in this file, so
     * rebinding the control rebinds the instruction.
     */
    private void drawDirections(GuiGraphics graphics) {
        QuestDialogueLayout.RewardBlock rewards = layout.rewards();
        int y = rewards.visible() ? rewards.panel().bottom() + 2 : layout.parchment().bottom() + 4;
        if (y + QuestScreenDraw.lineHeight(this.font) > this.height) return;

        int x = QuestDialogueLayout.margin(this.width);
        int maxWidth = Math.max(1, this.width - (x * 2));
        if (!localDirections.isBlank()) {
            QuestScreenDraw.drawLine(graphics, this.font, QuestScreenDraw.fit(this.font,
                            QuestScreenDraw.text(QuestScreenText.DIRECTIONS,
                                    QuestScreenDraw.literal(localDirections)), maxWidth),
                    x, y, QuestScreenDraw.MUTED_COLOR);
            y += QuestScreenDraw.lineHeight(this.font) + 1;
        }
        if (y + QuestScreenDraw.lineHeight(this.font) <= this.height) {
            String boundKey = Keybinds.OPEN_SKILL_SCREEN.getKey().getName();
            // The key's own display name, not Component.translatable(its identifier). The screen
            // was printing "key.keyboard.o" at the player: a raw identifier is exactly the defect
            // the acceptance list calls out, and getDisplayName is the accessor vanilla itself uses
            // for bindings, so it also covers mouse buttons and unnamed scancodes.
            Component keyLabel = QuestKeyPrompt.isUnbound(boundKey)
                    ? Component.translatable(QuestKeyPrompt.OPEN_JOURNAL_BINDING)
                    : Keybinds.OPEN_SKILL_SCREEN.getKey().getDisplayName();
            QuestScreenDraw.drawLine(graphics, this.font, QuestScreenDraw.fit(this.font,
                            QuestScreenDraw.text(QuestKeyPrompt.openJournalMessageKey(boundKey), keyLabel),
                            maxWidth),
                    x, y, QuestScreenDraw.MUTED_COLOR);
        }
    }

    /**
     * The lines that belong at the foot of the parchment, in the order they are drawn.
     *
     * <p>These are five of the six presentation states a strict item hand-in has (protocol section
     * 1.5): checking, short by an exact amount, waiting on the server, handed over, and could not
     * be completed. The sixth -- ready to turn in -- is the dialogue with no line at all, which is
     * what the player sees before they click.
     *
     * <p>Bounded by construction: the missing list is at most eight entries and the heading is one,
     * so this never grows past what the layout reserved room to shrink by.
     */
    private List<Component> statusLines() {
        List<Component> lines = new ArrayList<>();
        if (awaitingServer) {
            // Two different waits, and telling them apart is the difference between "the game is
            // busy" and "the game is deciding whether you have the goods".
            // Only a re-attempt after a known shortfall is certainly another inventory check. On a
            // first click this screen cannot know whether the node carries a hand-in at all, and
            // the generic line is the honest one for every other quest in the game.
            lines.add(QuestScreenDraw.text(
                    handin.state() == QuestHandinPresentation.State.ITEMS_MISSING
                            ? QuestScreenText.HANDIN_CHECKING : QuestScreenText.PENDING_CONFIRMATION));
            return lines;
        }
        switch (handin.state()) {
            case ITEMS_MISSING -> {
                lines.add(handin.message().isBlank()
                        ? QuestScreenDraw.text(QuestScreenText.HANDIN_MISSING)
                        : QuestScreenDraw.literal(handin.message()));
                for (QuestHandinPresentation.Line missing : handin.missing()) {
                    lines.add(QuestScreenDraw.text(QuestScreenText.HANDIN_MISSING_LINE,
                            QuestScreenDraw.itemName(missing.itemId()), missing.count()));
                }
            }
            case CONSUMED -> {
                if (!handin.removed().isEmpty()) {
                    lines.add(QuestScreenDraw.text(QuestScreenText.HANDIN_TAKEN, describe(handin.removed())));
                }
            }
            case REFUNDED -> lines.add(QuestScreenDraw.text(QuestScreenText.HANDIN_REFUNDED));
            case UNAVAILABLE -> lines.add(QuestScreenDraw.text(QuestScreenText.HANDIN_UNAVAILABLE,
                    handin.reason().isBlank() ? "unknown" : handin.reason()));
            case NONE -> { }
        }
        return lines;
    }

    /** Draws the status block upward from the foot of the body box, above any scroll hint. */
    private void drawStatusLines(GuiGraphics graphics, ScreenRect body, List<Component> lines, int lineHeight) {
        if (lines.isEmpty()) return;
        int bottom = body.bottom() - (layout.body().scrolls() ? lineHeight : 0);
        int top = bottom - (lines.size() * lineHeight);
        for (int index = 0; index < lines.size(); index++) {
            int y = top + (index * lineHeight);
            if (y < body.y()) continue;
            QuestScreenDraw.drawLine(graphics, this.font,
                    QuestScreenDraw.fit(this.font, lines.get(index), body.width()),
                    body.x(), y, QuestScreenDraw.CLAIM_COLOR);
        }
    }

    /** "1 Dung, 1 Dirt" -- what the quest giver actually took, in the player's own language. */
    private Component describe(List<QuestHandinPresentation.Line> items) {
        net.minecraft.network.chat.MutableComponent joined = Component.empty();
        for (int index = 0; index < items.size(); index++) {
            if (index > 0) joined.append(QuestScreenDraw.literal(", "));
            joined.append(QuestScreenDraw.text(QuestScreenText.HANDIN_MISSING_LINE,
                    QuestScreenDraw.itemName(items.get(index).itemId()), items.get(index).count()));
        }
        return joined;
    }

    private void drawScrollHint(GuiGraphics graphics, ScreenRect body) {
        int y = body.bottom() - QuestScreenDraw.lineHeight(this.font);
        if (y < body.y()) return;
        Component hint = QuestScreenDraw.text(QuestScreenText.SCROLL_HINT);
        QuestScreenDraw.drawLine(graphics, this.font, QuestScreenDraw.fit(this.font, hint, body.width()),
                body.x(), y, QuestScreenDraw.MUTED_COLOR);
    }

    /** The choice column's affordance, in the line the layout reserved under the last button. */
    private void drawChoiceScrollHint(GuiGraphics graphics, ScreenRect at) {
        if (at.isEmpty()) return;
        Component hint = QuestScreenDraw.text(QuestScreenText.SCROLL_HINT);
        QuestScreenDraw.drawLine(graphics, this.font, QuestScreenDraw.fit(this.font, hint, at.width()),
                at.x(), at.y(), QuestScreenDraw.MUTED_COLOR);
    }

    /**
     * Everything that belongs <i>under</i> the widgets.
     *
     * <p>{@code Screen#render} calls this before it draws the renderables, so the parchment, the
     * dim and the help panel all go here rather than being painted by {@code render} and then
     * covered by a second pass through this method.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        if (view == View.DIALOGUE && layout != null) {
            QuestScreenDraw.drawParchment(graphics, layout);
            return;
        }
        graphics.fill(0, 0, this.width, this.height, 0xCC000000);
        if (view == View.HELP && guideLayout != null) {
            ScreenRect panel = guideLayout.panel();
            graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), 0xF2E8DCC8);
            graphics.renderOutline(panel.x(), panel.y(), panel.width(), panel.height(), 0xFF8B6A45);
        }
    }

    // ------------------------------------------------------------ input

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = (int) -Math.signum(scrollY);
        if (delta == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

        if (view == View.HELP && guideLayout != null && guideLayout.scrolls()) {
            guideScroll = clamp(guideScroll + delta, 0, guideLayout.scrollMax());
            rebuild();
            return true;
        }
        if (view == View.DIALOGUE && layout != null) {
            if (layout.choices().scrolls() && hovered(layout.choices().bounds(), (int) mouseX, (int) mouseY)) {
                choiceScroll = clamp(choiceScroll + delta, 0, layout.choices().scrollMax());
                rebuild();
                return true;
            }
            if (layout.body().scrolls()) {
                bodyScroll = clamp(bodyScroll + delta, 0, layout.body().scrollMax());
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // Escape backs out of help rather than out of the conversation: the player opened a
            // panel, and closing a panel should not abandon a quest.
            if (view == View.HELP) {
                view = View.DIALOGUE;
                rebuild();
                return true;
            }
            this.onClose();
            return true;
        }
        if (view == View.DIALOGUE && layout != null) {
            // Hidden choices come first. Vanilla's Tab walks the widgets that were added, and only
            // the visible choices are added, so below about 192 units the second and third choices
            // had no keyboard path at all -- while the body, which can be read by scrolling with a
            // mouse over it, owned both page keys. The journal routes its page keys to the thing
            // that scrolls; this now does the same.
            if (layout.choices().scrolls()) {
                if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
                    choiceScroll = clamp(choiceScroll + 1, 0, layout.choices().scrollMax());
                    rebuild();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
                    choiceScroll = clamp(choiceScroll - 1, 0, layout.choices().scrollMax());
                    rebuild();
                    return true;
                }
            } else if (layout.body().scrolls()) {
                if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
                    bodyScroll = clamp(bodyScroll + 1, 0, layout.body().scrollMax());
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
                    bodyScroll = clamp(bodyScroll - 1, 0, layout.body().scrollMax());
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------ quest plumbing

    private void handleChoice(QuestChoice choice) {
        // The debounce. Vanilla answers a second click on a disabled button by doing nothing, but
        // the buttons are only disabled on the next rebuild, and a fast double-click lands before
        // it: without this guard the second click mints a second requestUuid, a second pending
        // callback and -- for a turn-in -- a second claim on the same transaction. The server
        // stops the second removal at its ledger, but a screen that lets you ask twice is a screen
        // that looks like it did nothing the first time.
        if (awaitingServer) return;

        this.choiceMade = true;
        this.awaitingServer = true;
        // Rebuilt immediately so the buttons grey out with the notice rather than a frame later.
        rebuild();
        long askedFrom = QuestDialogueTransition.nodeId(questState);
        QuestClient.sendTransition(questState.quest_id, choice.id, questGiverContext(), newResponse -> {
            this.awaitingServer = false;
            // The player may have walked away while the round trip was in flight, and a hand-in's is
            // longer than most: a claim, a removal and a confirmation. Acting now would pop a quest
            // dialogue back over whatever they are doing, or close whatever they have since opened.
            // The server has already applied everything authoritative; this callback only paints.
            if (Minecraft.getInstance().screen != this) return;
            if (newResponse == null || newResponse.error != null) {
                this.onClose();
                return;
            }

            // A strict item hand-in answers from the node it was asked at, because Rails does not
            // advance until this server confirms a removal. Those answers are shown in place: the
            // shortfall, the refund notice and the failure reason all belong to the parchment the
            // player is already reading, and swapping the screen for an identical one is what the
            // old flicker was.
            QuestHandinPresentation answer = QuestHandinPresentation.from(newResponse);
            if (answer.keepsDialogueOpen()) {
                this.handin = answer;
                // choiceMade deliberately stays true. It does not gate clicking -- it gates the
                // abandon-on-close guard in onClose -- so resetting it meant a player who was told
                // "you are still short: Dung x1" and pressed Escape to go and find one had the quest
                // stage silently abandoned out of their journal. Worse on an `unavailable` answer,
                // where the items are already gone.
                rebuild();
                return;
            }
            // A choice that lands back on the node it was offered from is a dismissal, not a step:
            // "I will return with the dung." Rails answers it correctly and returns the same node,
            // because the objective is still live and there is nowhere else to go. Re-opening an
            // identical screen makes the button look dead -- the player clicks, the pending notice
            // flickers, and nothing changes -- so close instead and let them walk away.
            //
            // Deliberately keyed on the node actually returned rather than on the choice's id: the
            // wire carries no destination, and the five questline choices that do this are the only
            // self-loops in the whole content set. A presentation choice never reaches here at all;
            // onChoicePressed opens its view without sending anything.
            if (QuestDialogueTransition.isDismissal(askedFrom, newResponse)) {
                this.onClose();
                return;
            }
            QuestDecisionScreen next = new QuestDecisionScreen(newResponse, this.npcName,
                    this.npcGender, this.npcUuid);
            // The completion notice travels with the node it completed, so "Handed over: 1 Dung"
            // is read on the screen that shows what Rowan said next rather than on the one the
            // player has already left.
            next.handin = answer;
            Minecraft.getInstance().setScreen(next);
        });
    }


    private JsonObject questGiverContext() {
        JsonObject context = new JsonObject();
        String displayName = QuestTriggerResultPresentation.displayName(this.npcName);
        if (!displayName.isBlank()) {
            context.addProperty("quest_giver_name", displayName);
        }
        if (this.npcUuid != null) {
            context.addProperty("quest_giver_uuid", this.npcUuid.toString());
        }
        return context;
    }

    private List<QuestChoice> visibleChoices() {
        if (dialogueView.completed() || questState == null || questState.choices == null) return List.of();
        List<QuestChoice> choices = new ArrayList<>();
        for (QuestChoice choice : questState.choices) {
            if (choice != null) choices.add(choice);
        }
        return choices;
    }

    private boolean lockedAt(int index) {
        List<DialogueOptionViewModel> options = dialogueView.options();
        return index >= 0 && index < options.size() && options.get(index).locked();
    }

    private String professionLabel() {
        String fromView = dialogueView.professionLabel();
        if (fromView != null && !fromView.isBlank()) return fromView;
        return journalEntry == null ? "" : journalEntry.detail().questGiverProfession();
    }

    private ClientQuestEntry.Stage stage() {
        if (node.stage().known()) {
            QuestNodePresentation.Stage from = node.stage();
            return new ClientQuestEntry.Stage(from.questlineKey(), from.index(), from.count(), from.label());
        }
        return journalEntry == null ? ClientQuestEntry.Stage.NONE : journalEntry.detail().stage();
    }

    private List<ClientQuestEntry.RewardItem> rewardsOnAccept() {
        return preview(node.onAccept(),
                journalEntry == null ? List.of() : journalEntry.detail().rewardsOnAccept());
    }

    private List<ClientQuestEntry.RewardItem> rewardsOnComplete() {
        return preview(node.onComplete(),
                journalEntry == null ? List.of() : journalEntry.detail().rewardsOnComplete());
    }

    private List<ClientQuestEntry.RewardItem> keepItems() {
        return preview(node.keepItems(),
                journalEntry == null ? List.of() : journalEntry.detail().keepItems());
    }

    /** The node's own preview wins; the journal entry is the fallback for a node that carries none. */
    private static List<ClientQuestEntry.RewardItem> preview(
            List<QuestNodePresentation.ItemPreview> fromNode,
            List<ClientQuestEntry.RewardItem> fromJournal) {
        if (fromNode == null || fromNode.isEmpty()) return fromJournal;
        List<ClientQuestEntry.RewardItem> items = new ArrayList<>(fromNode.size());
        for (QuestNodePresentation.ItemPreview item : fromNode) {
            items.add(new ClientQuestEntry.RewardItem(item.id(), item.count()));
        }
        return items;
    }

    private ClientQuestEntry findJournalEntry() {
        if (questState == null) return null;
        if (questState.questStateId != null && !questState.questStateId.isBlank()) {
            String wanted = questState.questStateId.trim();
            for (ClientQuestEntry entry : ClientQuestTable.snapshot()) {
                if (entry.questStateId().equals(wanted)) return entry;
            }
        }
        if (questState.quest_id <= 0) return null;
        return ClientQuestTable.findByQuestId(Long.toString(questState.quest_id));
    }

    /** Rowan's per-spawner directions hint (M6), read off the quest giver the player is talking to. */
    private String readLocalDirections() {
        Minecraft mc = Minecraft.getInstance();
        if (npcUuid == null || mc.level == null) return "";
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (npcUuid.equals(entity.getUUID()) && entity instanceof QuestGiverEntity giver) {
                return giver.getLocalDirections();
            }
        }
        return "";
    }

    private static boolean hasAcceptedQuestState(QuestResponse response) {
        return response != null && response.questStateId != null && !response.questStateId.isBlank();
    }

    private static boolean isRejectChoice(QuestChoice choice) {
        if (choice == null) return false;
        String id = choice.id == null ? "" : choice.id.trim().toLowerCase(java.util.Locale.ROOT);
        String text = choice.text == null ? "" : choice.text.trim().toLowerCase(java.util.Locale.ROOT);
        return id.equals("reject")
                || id.equals("decline")
                || id.equals("refuse")
                || id.equals("no")
                || text.contains("reject")
                || text.contains("decline")
                || text.contains("refuse");
    }

    private void clearPendingOfferStateIfUnaccepted() {
        if (!hasAcceptedQuestState(this.questState)) {
            QuestManager.getInstance().clearState();
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void onClose() {
        if (!this.choiceMade && questState != null && !questState.completed && hasAcceptedQuestState(questState)) {
            QuestClient.abandonQuest(questState.quest_id);
        }
        clearPendingOfferStateIfUnaccepted();

        Minecraft.getInstance().setScreen(null);
    }
}
