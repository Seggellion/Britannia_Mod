package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.bank.currency.CurrencyItemRegistry;
import com.seggellion.britannia_mod.client.screen.bank.BankActionButton;
import com.seggellion.britannia_mod.client.screen.bank.BankBoxLayout;
import com.seggellion.britannia_mod.client.screen.bank.BankDialogueFrame;
import com.seggellion.britannia_mod.client.screen.bank.BankGridGeometry;
import com.seggellion.britannia_mod.client.screen.bank.BankGridScroll;
import com.seggellion.britannia_mod.client.screen.bank.BankStatusPresenter;
import com.seggellion.britannia_mod.client.screen.bank.BankingScreen;
import com.seggellion.britannia_mod.client.screen.bank.ClientBankingSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

import java.util.Locale;

/**
 * Milestone 9: the Bank Box's structure, without moving anything.
 *
 * <p>Every region design §9.2 requires is present and positioned: title, weight and limit, the
 * stored-item grid, the player's main inventory, the hotbar, Back, Withdraw, a currency amount
 * field, three denomination buttons and a status area.
 *
 * <h2>What this milestone deliberately does not do</h2>
 * No deposit packet, no withdrawal packet, no drag, and <b>no fake bank items</b>. The grids draw
 * their cells and nothing in them. Real contents arrive at Milestone 10 (the {@code item_key}
 * render contract) and Milestone 11 (the grids themselves); dragging at 12 and 13; withdrawal at
 * 15 and 16.
 *
 * <p>The controls are built, laid out and disabled rather than omitted, so their placement, focus
 * order and wording can be reviewed now — and so the milestones that make them live change
 * behaviour rather than layout.
 *
 * <p>Weight is the one real value shown, because it is account state rather than item state and
 * design §9.2 lists it as its own region.
 *
 * <h2>Not a dialogue screen</h2>
 * No portrait, no parchment banner, no right-hand button column — design §9.1 makes the Bank Box
 * the one inventory-oriented screen in the epic, and it is laid out like an inventory screen.
 * {@link BankBoxLayout} owns every coordinate and is separately tested; this class draws at them.
 */
public final class BankBoxScreen extends Screen implements BankingScreen {

    /** The strongbox artwork behind the vault grid. */
    private static final net.minecraft.resources.ResourceLocation CHEST_TEXTURE =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    "britannia_mod", "textures/screens/bank_box_chest.png");

    /**
     * <b>These two must equal the PNG's real pixel dimensions.</b> {@code blit} takes the texture's
     * true size as its last two arguments and expresses every sample coordinate in those same
     * pixels -- declare 304x396 for a 608x792 file and it samples the top-left quarter. They are
     * the only two numbers to change if the artwork is re-exported at another size; the cuts below
     * are fractions and follow automatically.
     */
    private static final int CHEST_ART_W = 304;
    private static final int CHEST_ART_H = 396;

    /**
     * Where the art divides, as fractions of its height: everything above {@code LID} is the open
     * lid, everything below {@code BASE} is the foot and hasp, and the band between them is the
     * velvet interior that stretches to fit the grid.
     *
     * <p>Calibrated by eye against the supplied illustration. If the grid sits off the velvet
     * in-game, these two are the adjustment -- raising {@code LID_FRACTION} moves the grid down.
     */
    private static final float CHEST_LID_FRACTION = 0.42f;
    private static final float CHEST_BASE_FRACTION = 0.88f;

    private static final int CHEST_ART_LID_CUT = Math.round(CHEST_ART_H * CHEST_LID_FRACTION);
    private static final int CHEST_ART_BASE_CUT = Math.round(CHEST_ART_H * CHEST_BASE_FRACTION);

    /** Warm off-white for text on the lid and the dark panel -- the parchment palette's inverse. */
    private static final int LIGHT_TEXT_COLOR = 0xFFE9DCC3;
    private static final int PANEL_FILL = 0xC8140F0A;
    private static final int PANEL_BORDER = 0x33FFFFFF;

    @Nullable
    private BankBoxLayout layout;
    @Nullable
    private EditBox amountBox;

    private BankGridScroll scroll = BankGridScroll.TOP;

    public BankBoxScreen() {
        super(Component.translatable("screen.britannia_mod.bank.box.title"));
    }

    @Override
    protected void init() {
        super.init();

        ClientBankingSession session = ClientBankingSession.active();
        if (session == null) {
            onClose();
            return;
        }

        layout = BankBoxLayout.calculate(width, height, font.lineHeight, session.bankItems().size());
        scroll = scroll.reclamped(session.bankItems().size(), BankBoxLayout.COLUMNS, layout.bankGrid().rows());

        buildCurrencyControls();
        buildActions();
    }

    private void buildCurrencyControls() {
        String carried = amountBox == null ? "" : amountBox.getValue();
        amountBox = new EditBox(
                font, layout.amountBoxX(), layout.amountBoxY(), layout.amountBoxWidth(), BankBoxLayout.ROW_HEIGHT,
                Component.translatable("screen.britannia_mod.bank.box.amount_label")
        );
        amountBox.setMaxLength(10);
        amountBox.setValue(carried);
        // Milestone 16 makes currency withdrawal live. Editable now so the field's size and
        // position are reviewable, but nothing reads it yet.
        amountBox.setEditable(false);
        addRenderableWidget(amountBox);

        String[] keys = {CurrencyItemRegistry.GOLD_KEY, CurrencyItemRegistry.SILVER_KEY, CurrencyItemRegistry.COPPER_KEY};
        for (int index = 0; index < keys.length; index++) {
            Button button = Button.builder(
                            Component.translatable("screen.britannia_mod.bank.box.withdraw." + keys[index]),
                            ignored -> { }
                    )
                    .bounds(layout.denominationX(index), layout.denominationY(), layout.denominationWidth(), BankBoxLayout.ROW_HEIGHT)
                    .build();
            button.active = false;
            addRenderableWidget(button);
        }
    }

    private void buildActions() {
        Component back = Component.translatable("screen.britannia_mod.bank.action.back");
        addRenderableWidget(BankActionButton.create(
                layout.backX(), layout.backY(), layout.actionWidth(), BankBoxLayout.ROW_HEIGHT,
                back, back,
                ignored -> returnToMain()
        ));

        BankActionButton withdraw = BankActionButton.create(
                layout.withdrawX(), layout.withdrawY(), layout.actionWidth(), BankBoxLayout.ROW_HEIGHT,
                Component.translatable("screen.britannia_mod.bank.box.withdraw_item"),
                Component.translatable("screen.britannia_mod.bank.box.withdraw_item.pending"),
                ignored -> { }
        );
        // Milestone 15 makes this live, and it will stay disabled until something is selected --
        // there is no selection yet because there is nothing in the grid to select.
        withdraw.active = false;
        addRenderableWidget(withdraw);
    }

    private void returnToMain() {
        Minecraft.getInstance().setScreen(new BankMainScreen());
    }

    /**
     * The panel and chest go under the widgets, so they draw here rather than in {@code render}.
     * The dark panel grounds everything -- the previous build floated translucent cells over the
     * raw world, which is the readability problem the owner's screenshot showed.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        if (layout == null) return;

        graphics.fill(layout.panelLeft(), layout.panelTop(), layout.panelRight(), layout.panelBottom(), PANEL_FILL);
        graphics.renderOutline(
                layout.panelLeft(), layout.panelTop(), layout.panelWidth(), layout.panelHeight(), PANEL_BORDER
        );

        if (layout.chestVisible()) drawChest(graphics);
    }

    /**
     * The strongbox, in three vertical slices: the open lid near its natural proportion, the
     * velvet interior stretched to exactly the grid's height, the base fixed. One stretched image
     * would squash the whole illustration whenever the grid shows fewer rows; velvet tolerates a
     * vertical stretch, a lid and a lock do not. {@link BankBoxLayout} owns the three heights.
     */
    private void drawChest(GuiGraphics graphics) {
        int x = layout.chestLeft();
        int w = layout.chestWidth();
        int lidTop = layout.chestTop();
        int interiorTop = lidTop + layout.chestLidHeight();
        int baseTop = interiorTop + layout.chestInteriorHeight();

        graphics.blit(
                CHEST_TEXTURE, x, lidTop, w, layout.chestLidHeight(),
                0, 0, CHEST_ART_W, CHEST_ART_LID_CUT, CHEST_ART_W, CHEST_ART_H
        );
        graphics.blit(
                CHEST_TEXTURE, x, interiorTop, w, layout.chestInteriorHeight(),
                0, CHEST_ART_LID_CUT, CHEST_ART_W, CHEST_ART_BASE_CUT - CHEST_ART_LID_CUT, CHEST_ART_W, CHEST_ART_H
        );
        graphics.blit(
                CHEST_TEXTURE, x, baseTop, w, layout.chestBaseHeight(),
                0, CHEST_ART_BASE_CUT, CHEST_ART_W, CHEST_ART_H - CHEST_ART_BASE_CUT, CHEST_ART_W, CHEST_ART_H
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        ClientBankingSession session = ClientBankingSession.active();
        if (session == null || layout == null) return;

        // Title and weight sit on the open lid when the chest is drawn, centred over it; in the
        // plain fallback they keep their left-aligned positions. Light text either way -- both
        // grounds are dark now.
        Component title = DialoguePresentation.text(getTitle());
        Component weight = DialoguePresentation.text(weightLine(session));
        if (layout.chestVisible()) {
            int centerX = layout.chestLeft() + (layout.chestWidth() / 2);
            graphics.drawString(font, title, centerX - (font.width(title) / 2), layout.titleY(), LIGHT_TEXT_COLOR, false);
            graphics.drawString(font, weight, centerX - (font.width(weight) / 2), layout.weightY(), LIGHT_TEXT_COLOR, false);
        } else {
            graphics.drawString(font, title, layout.contentLeft(), layout.titleY(), LIGHT_TEXT_COLOR, false);
            graphics.drawString(font, weight, layout.contentLeft(), layout.weightY(), LIGHT_TEXT_COLOR, false);
        }
        graphics.drawString(
                font, DialoguePresentation.text(Component.translatable("screen.britannia_mod.bank.box.inventory_label")),
                layout.inventoryGrid().left(), layout.inventoryLabelY(), LIGHT_TEXT_COLOR, false
        );

        drawCells(graphics, layout.bankGrid());
        drawCells(graphics, layout.inventoryGrid());
        drawCells(graphics, layout.hotbar());

        BankDialogueFrame.renderStatusOnDark(
                graphics, font, layout.contentLeft(), layout.statusY(), layout.statusMaxWidth(),
                BankStatusPresenter.forResult(session.lastResult())
        );
    }

    /**
     * Empty cells only. Milestone 11 draws contents into these same rectangles; drawing the
     * structure now is what makes the layout reviewable before anything depends on it.
     */
    private void drawCells(GuiGraphics graphics, BankGridGeometry grid) {
        for (int index = 0; index < grid.capacity(); index++) {
            int left = grid.cellLeft(index);
            int top = grid.cellTop(index);
            graphics.fill(left, top, left + grid.cellPitch(), top + grid.cellPitch(), 0x33000000);
            graphics.renderOutline(left, top, grid.cellPitch(), grid.cellPitch(), 0x55FFFFFF);
        }
    }

    private Component weightLine(ClientBankingSession session) {
        return Component.translatable(
                "screen.britannia_mod.bank.box.weight",
                formatWeight(session.currentWeight()),
                String.valueOf(session.weightLimit())
        );
    }

    private static String formatWeight(double weight) {
        if (weight == Math.floor(weight) && !Double.isInfinite(weight)) return String.valueOf((long) weight);
        return String.format(Locale.ROOT, "%.1f", weight);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ClientBankingSession session = ClientBankingSession.active();
        if (session != null && layout != null && layout.bankGrid().contains(mouseX, mouseY)) {
            // Whole rows, and only over the bank grid -- scrolling must not disturb anything else
            // on a screen this crowded.
            scroll = scroll.scrolledBy(
                    scrollY > 0 ? -1 : 1,
                    session.bankItems().size(), BankBoxLayout.COLUMNS, layout.bankGrid().rows()
            );
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Design §5.2: Escape closes banking; Back is the only route to the hub. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ClientBankingSession.close();
        Minecraft.getInstance().setScreen(null);
    }
}
