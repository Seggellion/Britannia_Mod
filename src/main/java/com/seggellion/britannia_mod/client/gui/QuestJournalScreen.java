package com.seggellion.britannia_mod.client.gui;

import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.payload.ServerboundQuitQuestPayload;
import com.seggellion.britannia_mod.quest.ClientQuestEntry;
import com.seggellion.britannia_mod.quest.ClientQuestTable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The quest journal.
 *
 * <h2>Milestone 8</h2>
 * Item 3 wants the quest number, the next action, the ordered progress and the return-to-Rowan
 * state in every entry, and item 9 wants the panel responsive with keyboard focus and a Quit
 * confirmation. The geometry for all of that is in {@link QuestJournalLayout}, where JUnit can
 * assert it; this file holds state and events.
 *
 * <p>The panel was a fixed 370x260 centred on the screen, which is wider and taller than the screen
 * itself at 320x240 scaled units -- a 1280x1024 window at GUI scale 4, or any window with Force
 * Unicode Font on. It is now clamped, and rows are measured from their own content instead of
 * every row taking a constant 62 units whether it has five progress steps or none.
 *
 * <p>Quit no longer fires on the first press. It arms a confirmation, because quitting a quest is
 * not undoable from here and the button sat one row away from the mouse wheel.
 */
public class QuestJournalScreen extends Screen {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "britannia_mod", "textures/screens/quest-journal.png");

    private QuestJournalLayout layout;
    private int scrollOffset;
    private int confirmingIndex = -1;
    private List<ClientQuestEntry> quests = List.of();
    private String lastSignature = "";

    public QuestJournalScreen() {
        super(Component.translatable(QuestScreenText.JOURNAL_TITLE));
    }

    @Override
    protected void init() {
        super.init();
        refreshWidgets();
    }

    @Override
    public void tick() {
        String signature = signature(ClientQuestTable.snapshot());
        if (!signature.equals(lastSignature)) {
            refreshWidgets();
        }
    }

    private void refreshWidgets() {
        this.clearWidgets();
        this.quests = ClientQuestTable.snapshot();
        this.lastSignature = signature(this.quests);
        if (confirmingIndex >= this.quests.size()) {
            confirmingIndex = -1;
        }

        List<Integer> stepCounts = new ArrayList<>(quests.size());
        List<Boolean> claimPending = new ArrayList<>(quests.size());
        for (ClientQuestEntry quest : quests) {
            stepCounts.add(quest.detail().progress().size());
            claimPending.add(quest.detail().claimPending());
        }

        this.layout = QuestJournalLayout.calculate(this.width, this.height, this.font.lineHeight,
                stepCounts, claimPending, scrollOffset, confirmingIndex);
        this.scrollOffset = layout.firstVisibleRow();

        // Widgets are added in the layout's focus order, so vanilla's Tab handling walks them in
        // the order QuestJournalLayoutTest asserts. While the confirmation is up it is the only
        // thing focusable, which is what makes it a confirmation.
        if (layout.confirmation().active()) {
            addConfirmationWidgets();
            return;
        }

        for (QuestJournalLayout.Row row : layout.rows()) {
            ScreenRect at = row.quitButton();
            if (at.isEmpty()) continue;
            int index = row.entryIndex();
            addRenderableWidget(Button.builder(QuestScreenDraw.text(QuestScreenText.JOURNAL_QUIT),
                            b -> armQuit(index))
                    .bounds(at.x(), at.y(), at.width(), at.height())
                    .build());
        }

        ScreenRect close = layout.closeButton();
        Button closeButton = Button.builder(QuestScreenDraw.text(QuestScreenText.JOURNAL_CLOSE),
                        b -> onClose())
                .bounds(close.x(), close.y(), close.width(), close.height())
                .build();
        addRenderableWidget(closeButton);
        if (layout.rows().isEmpty()) {
            setInitialFocus(closeButton);
        }
    }

    private void addConfirmationWidgets() {
        QuestJournalLayout.Confirmation confirmation = layout.confirmation();
        ScreenRect cancel = confirmation.cancelButton();
        Button cancelButton = Button.builder(QuestScreenDraw.text(QuestScreenText.QUIT_CONFIRM_NO),
                        b -> {
                            confirmingIndex = -1;
                            refreshWidgets();
                        })
                .bounds(cancel.x(), cancel.y(), cancel.width(), cancel.height())
                .build();
        addRenderableWidget(cancelButton);

        ScreenRect confirm = confirmation.confirmButton();
        addRenderableWidget(Button.builder(QuestScreenDraw.text(QuestScreenText.QUIT_CONFIRM_YES),
                        b -> confirmQuit(confirmation.entryIndex()))
                .bounds(confirm.x(), confirm.y(), confirm.width(), confirm.height())
                .build());

        // Cancel takes focus, so a stray Enter keeps the quest rather than losing it.
        setInitialFocus(cancelButton);
    }

    private void armQuit(int entryIndex) {
        this.confirmingIndex = entryIndex;
        refreshWidgets();
    }

    private void confirmQuit(int entryIndex) {
        if (entryIndex >= 0 && entryIndex < quests.size()) {
            NetworkHandler.sendToServer(new ServerboundQuitQuestPayload(quests.get(entryIndex).questStateId()));
        }
        this.confirmingIndex = -1;
        refreshWidgets();
    }

    // ------------------------------------------------------------ rendering

    /**
     * {@code super.render} first, then anything that belongs on top of the widgets.
     *
     * <p>{@code Screen#render} begins with {@code this.renderBackground(...)} and then draws the
     * renderables, so a screen that paints its panel and <i>then</i> calls {@code super.render}
     * is relying on its own background override being harmless. This one's was -- it was empty --
     * but the arrangement is the same one that painted the help view of {@link QuestDecisionScreen}
     * black, and it only stays safe for as long as nobody gives this screen a real background. The
     * panel, the rows and the confirmation now live in {@link #renderBackground}, which is where
     * {@code Screen} already draws them from, in exactly the order they were drawn in before: panel,
     * header, rows, confirmation dim, then the buttons.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (layout == null) return;

        ScreenRect panel = layout.panel();
        graphics.blit(TEXTURE, panel.x(), panel.y(), 0, 0, panel.width(), panel.height(),
                panel.width(), panel.height());

        drawHeader(graphics);
        drawRows(graphics);
        drawConfirmationBackdrop(graphics);
    }

    private void drawHeader(GuiGraphics graphics) {
        ScreenRect header = layout.header();
        if (header.isEmpty()) return;
        Component title = layout.isEmpty()
                ? QuestScreenDraw.text(QuestScreenText.JOURNAL_TITLE)
                : QuestScreenDraw.text(QuestScreenText.JOURNAL_COUNT,
                        layout.visibleRowCount(), layout.totalRowCount());
        graphics.drawString(this.font, QuestScreenDraw.fit(this.font, title, header.width()),
                header.x(), header.y(), QuestScreenDraw.HEADING_COLOR, false);
    }

    private void drawRows(GuiGraphics graphics) {
        if (layout.isEmpty()) {
            Component empty = QuestScreenDraw.text(QuestScreenText.JOURNAL_EMPTY);
            ScreenRect list = layout.list();
            graphics.drawString(this.font, QuestScreenDraw.fit(this.font, empty, list.width()),
                    list.x() + Math.max(0, (list.width() - this.font.width(empty)) / 2),
                    list.y() + Math.max(0, (list.height() - this.font.lineHeight) / 2),
                    QuestScreenDraw.MUTED_COLOR, false);
            return;
        }

        for (QuestJournalLayout.Row row : layout.rows()) {
            if (row.entryIndex() >= quests.size()) continue;
            ClientQuestEntry quest = quests.get(row.entryIndex());
            ClientQuestEntry.JournalDetail detail = quest.detail();

            graphics.fill(row.bounds().x(), row.bounds().y(), row.bounds().right(),
                    row.bounds().bottom(), 0x33A06B32);

            drawLine(graphics, row.stageLabel(), stageLine(detail), QuestScreenDraw.HEADING_COLOR);
            drawLine(graphics, row.title(), QuestScreenDraw.literal(quest.name()),
                    QuestScreenDraw.HEADING_COLOR);
            drawLine(graphics, row.objective(), objectiveLine(quest), QuestScreenDraw.TEXT_COLOR);

            List<ClientQuestEntry.ProgressStep> steps = detail.progress();
            for (int i = 0; i < row.progressRows().size() && i < steps.size(); i++) {
                QuestScreenDraw.drawProgressStep(graphics, this.font, row.progressRows().get(i),
                        steps.get(i));
            }
            if (row.hiddenSteps() > 0 && !row.progressRows().isEmpty()) {
                ScreenRect last = row.progressRows().get(row.progressRows().size() - 1);
                drawLine(graphics, last,
                        QuestScreenDraw.text(QuestScreenText.JOURNAL_MORE_STEPS, row.hiddenSteps()),
                        QuestScreenDraw.MUTED_COLOR);
            }
            if (!row.claimBadge().isEmpty()) {
                drawLine(graphics, row.claimBadge(),
                        QuestScreenDraw.text(QuestScreenText.JOURNAL_RETURN_TO, giverName(quest)),
                        QuestScreenDraw.CLAIM_COLOR);
            }
        }
    }

    /**
     * The confirmation's dim and panel, drawn before the widgets so its two buttons -- the only
     * widgets on the screen while it is up -- land on top of it rather than under it.
     */
    private void drawConfirmationBackdrop(GuiGraphics graphics) {
        QuestJournalLayout.Confirmation confirmation = layout.confirmation();
        if (!confirmation.active()) return;

        ScreenRect box = confirmation.panel();
        graphics.fill(0, 0, this.width, this.height, 0x99000000);
        graphics.fill(box.x(), box.y(), box.right(), box.bottom(), 0xF2E8DCC8);
        graphics.renderOutline(box.x(), box.y(), box.width(), box.height(), 0xFF8B0000);

        String name = confirmation.entryIndex() >= 0 && confirmation.entryIndex() < quests.size()
                ? quests.get(confirmation.entryIndex()).name()
                : "";
        Component message = QuestScreenDraw.text(QuestScreenText.QUIT_CONFIRM_MESSAGE,
                QuestScreenDraw.literal(name));
        QuestScreenDraw.drawWrapped(graphics, this.font, message, confirmation.message(),
                confirmation.messageWrapWidth(), 0, QuestScreenDraw.TEXT_COLOR);
    }

    private void drawLine(GuiGraphics graphics, ScreenRect at, Component text, int color) {
        if (at.isEmpty()) return;
        graphics.drawString(this.font, QuestScreenDraw.fit(this.font, text, at.width()),
                at.x(), at.y(), color, false);
    }

    private Component stageLine(ClientQuestEntry.JournalDetail detail) {
        ClientQuestEntry.Stage stage = detail.stage();
        return stage.known()
                ? QuestScreenDraw.text(QuestScreenText.STAGE, stage.index(), stage.count())
                : QuestScreenDraw.text(QuestScreenText.STAGE_UNKNOWN);
    }

    /**
     * The next action.
     *
     * <p>Prefers the node's objective; falls back to the quest's own brief description, and then to
     * a translated "nothing pending" line. Never blank, so a row never looks like it lost its text.
     */
    private Component objectiveLine(ClientQuestEntry quest) {
        String objective = quest.detail().objective();
        if (!objective.isBlank()) {
            return QuestScreenDraw.text(QuestScreenText.JOURNAL_OBJECTIVE,
                    QuestScreenDraw.literal(objective));
        }
        if (!quest.briefDescription().isBlank()) {
            return QuestScreenDraw.text(QuestScreenText.JOURNAL_OBJECTIVE,
                    QuestScreenDraw.literal(quest.briefDescription()));
        }
        return QuestScreenDraw.text(QuestScreenText.JOURNAL_OBJECTIVE_NONE);
    }

    private Component giverName(ClientQuestEntry quest) {
        return quest.questGiverName().isBlank()
                ? QuestScreenDraw.text(QuestScreenText.JOURNAL_GIVER_UNKNOWN)
                : QuestScreenDraw.literal(quest.questGiverName());
    }

    // ------------------------------------------------------------ input

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (layout == null || layout.confirmation().active() || !isInsidePanel(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int next = scrollOffset - (int) Math.signum(scrollY);
        scrollOffset = Math.max(0, Math.min(layout.scrollMax(), next));
        refreshWidgets();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && layout != null
                && !layout.confirmation().active() && !isInsidePanel(mouseX, mouseY)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && layout != null && layout.confirmation().active()) {
            // Escape cancels the confirmation instead of closing the journal: the safe answer.
            confirmingIndex = -1;
            refreshWidgets();
            return true;
        }
        if (layout != null && !layout.confirmation().active() && layout.scrolls()) {
            if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
                scrollOffset = Math.min(layout.scrollMax(), scrollOffset + 1);
                refreshWidgets();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
                scrollOffset = Math.max(0, scrollOffset - 1);
                refreshWidgets();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean isInsidePanel(double mouseX, double mouseY) {
        ScreenRect panel = layout.panel();
        return mouseX >= panel.x() && mouseX <= panel.right()
                && mouseY >= panel.y() && mouseY <= panel.bottom();
    }

    private static String signature(List<ClientQuestEntry> entries) {
        return entries.stream()
                .map(entry -> entry.questStateId()
                        + ":" + entry.questGiverName()
                        + ":" + entry.name()
                        + ":" + entry.briefDescription()
                        + ":" + entry.acceptedAt()
                        + ":" + entry.status()
                        + ":" + entry.detail().objective()
                        + ":" + entry.detail().completedSteps()
                        + "/" + entry.detail().progress().size()
                        + ":" + entry.detail().claimPending())
                .collect(Collectors.joining("|"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
