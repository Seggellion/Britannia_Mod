package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.bank.currency.CurrencyItemRegistry;
import com.seggellion.britannia_mod.client.screen.bank.BankActionButton;
import com.seggellion.britannia_mod.client.screen.bank.BankBoxLayout;
import com.seggellion.britannia_mod.client.screen.bank.BankBoxSelection;
import com.seggellion.britannia_mod.client.screen.bank.BankDepositHint;
import com.seggellion.britannia_mod.client.screen.bank.BankDialogueFrame;
import com.seggellion.britannia_mod.client.screen.bank.BankItemIcon;
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

    /** Vanilla inventory: slots 0-8 are the hotbar, 9-35 the three main rows. */
    private static final int MAIN_INVENTORY_FIRST_SLOT = 9;

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
    /** Milestone 12. Rebuilt with the layout on every init, so a resize cancels by construction. */
    @Nullable
    private com.seggellion.britannia_mod.client.screen.bank.BankDragController drag;
    /** Milestone 17 gate corrective: double-click on a pack cheque cashes it. */
    private final com.seggellion.britannia_mod.client.screen.bank.BankChequeDoubleClick chequeDoubleClick =
            new com.seggellion.britannia_mod.client.screen.bank.BankChequeDoubleClick();
    /**
     * The vault's own tracker, separate from the pack's so the two grids cannot complete one
     * another's gesture -- a click in the pack followed by one in the vault is two first presses,
     * which is what a player means by it.
     */
    private final com.seggellion.britannia_mod.client.screen.bank.BankChequeDoubleClick vaultChequeDoubleClick =
            new com.seggellion.britannia_mod.client.screen.bank.BankChequeDoubleClick();
    /** Milestone 15: live once something is selected. */
    @Nullable
    private BankActionButton withdrawButton;
    /** Milestone 16: keyed by denomination so each judges its own affordability. */
    private final java.util.EnumMap<com.seggellion.britannia_mod.client.screen.bank.BankBalanceCopy.Denomination, Button> currencyButtons =
            new java.util.EnumMap<>(com.seggellion.britannia_mod.client.screen.bank.BankBalanceCopy.Denomination.class);

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
        drag = new com.seggellion.britannia_mod.client.screen.bank.BankDragController(layout.bankGrid());

        buildCurrencyControls();
        buildActions();
    }

    /**
     * The opaque token {@code BankDragController.tick} compares each frame: registry id and
     * count of whatever the source slot holds right now. Any change -- shrink, swap, empty --
     * makes the token differ and kills the gesture before it can deposit the wrong thing.
     */
    @Nullable
    private String sourceSnapshot(int slot) {
        net.minecraft.world.item.ItemStack stack = Minecraft.getInstance().player.getInventory().getItem(slot);
        if (stack.isEmpty()) return null;
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()) + " x" + stack.getCount();
    }

    /** The vanilla slot under the cursor across both player grids, or -1. */
    private int inventorySlotAt(double mouseX, double mouseY) {
        Integer mainCell = layout.inventoryGrid().cellIndexAt(mouseX, mouseY);
        if (mainCell != null) return MAIN_INVENTORY_FIRST_SLOT + mainCell;
        Integer hotbarCell = layout.hotbar().cellIndexAt(mouseX, mouseY);
        if (hotbarCell != null) return hotbarCell;
        return -1;
    }

    private void buildCurrencyControls() {
        String carried = amountBox == null ? "" : amountBox.getValue();
        amountBox = new EditBox(
                font, layout.amountBoxX(), layout.amountBoxY(), layout.amountBoxWidth(), BankBoxLayout.ROW_HEIGHT,
                Component.translatable("screen.britannia_mod.bank.box.amount_label")
        );
        amountBox.setMaxLength(10);
        amountBox.setValue(carried);
        addRenderableWidget(amountBox);

        // Milestone 16: live. One field, three send buttons -- pressing Gold withdraws the typed
        // amount of gold, the legacy screen's own idiom. Each button judges affordability against
        // its OWN denomination's balance, so the three can legitimately disagree: 800 typed with
        // 900 silver and 3 gold lights Silver and not Gold.
        currencyButtons.clear();
        com.seggellion.britannia_mod.client.screen.bank.BankBalanceCopy.Denomination[] denominations =
                com.seggellion.britannia_mod.client.screen.bank.BankBalanceCopy.Denomination.values();
        String[] keys = {CurrencyItemRegistry.GOLD_KEY, CurrencyItemRegistry.SILVER_KEY, CurrencyItemRegistry.COPPER_KEY};
        for (int index = 0; index < keys.length; index++) {
            final String wireKey = keys[index];
            final com.seggellion.britannia_mod.client.screen.bank.BankBalanceCopy.Denomination denomination =
                    denominations[index];
            Button button = Button.builder(
                            Component.translatable("screen.britannia_mod.bank.box.withdraw." + wireKey),
                            ignored -> sendCurrencyWithdrawal(wireKey, denomination)
                    )
                    .bounds(layout.denominationX(index), layout.denominationY(index), layout.denominationWidth(), BankBoxLayout.ROW_HEIGHT)
                    .build();
            button.active = false;
            currencyButtons.put(denomination, button);
            addRenderableWidget(button);
        }
    }

    /**
     * Milestone 16: the last dead controls come alive, in the shape every mutation before them
     * established -- revalidate at press rather than trusting the button's enabled state, claim
     * the session lock before the packet, send the existing payload unchanged, and let the
     * refresh push or the result payload say what happened. Currency withdrawal deliberately does
     * not touch the bank-grid selection (design §9.7): coins are a balance, not a stored item.
     */
    private void sendCurrencyWithdrawal(
            String wireKey, com.seggellion.britannia_mod.client.screen.bank.BankBalanceCopy.Denomination denomination
    ) {
        ClientBankingSession session = ClientBankingSession.active();
        if (session == null || amountBox == null) return;

        com.seggellion.britannia_mod.client.screen.bank.BankCurrencyWithdrawalForm.Validation validation =
                com.seggellion.britannia_mod.client.screen.bank.BankCurrencyWithdrawalForm.validate(
                        amountBox.getValue(),
                        com.seggellion.britannia_mod.client.screen.bank.BankCurrencyWithdrawalForm
                                .balanceOf(session, denomination));
        if (!validation.valid()) return;
        if (!session.beginPending(
                com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload.Operation.WITHDRAWAL)) {
            return;
        }
        com.seggellion.britannia_mod.network.ClientNetworkHandler.sendToServer(
                new com.seggellion.britannia_mod.network.payload.BankCurrencyWithdrawalRequestC2SPayload(
                        session.tellerEntityId(), wireKey, validation.amount()));
    }

    /** Per-denomination affordability plus the global pending lock, mirrored every frame. */
    private void refreshCurrencyButtons(ClientBankingSession session) {
        if (amountBox == null) return;
        boolean pending = session.isMutationPending();
        for (var entry : currencyButtons.entrySet()) {
            entry.getValue().active = !pending
                    && com.seggellion.britannia_mod.client.screen.bank.BankCurrencyWithdrawalForm.validate(
                            amountBox.getValue(),
                            com.seggellion.britannia_mod.client.screen.bank.BankCurrencyWithdrawalForm
                                    .balanceOf(session, entry.getKey())).valid();
        }
        amountBox.setEditable(!pending);
    }

    private void buildActions() {
        Component back = Component.translatable("screen.britannia_mod.bank.action.back");
        addRenderableWidget(BankActionButton.create(
                layout.backX(), layout.backY(), layout.actionWidth(), BankBoxLayout.ROW_HEIGHT,
                back, back,
                ignored -> returnToMain()
        ));

        withdrawButton = BankActionButton.create(
                layout.withdrawX(), layout.withdrawY(), layout.actionWidth(), BankBoxLayout.ROW_HEIGHT,
                Component.translatable("screen.britannia_mod.bank.box.withdraw_item"),
                Component.translatable("screen.britannia_mod.bank.box.withdraw_item.pending"),
                ignored -> sendWithdrawal()
        );
        withdrawButton.active = false;
        addRenderableWidget(withdrawButton);
    }

    /**
     * Milestone 15: one press, one request, referencing the selection's public id -- never a grid
     * position (design §9.6). The same shape as every mutation before it: the session lock is
     * claimed before the packet goes out, a failed claim sends nothing, and what happens next
     * arrives as a refresh push (the item gone from the vault, in the pack) or a result payload
     * -- including {@code INVENTORY_FULL}, this milestone's own new kind, when the pack has no
     * room. The selection itself needs no cleanup here: the session drops it when a refresh no
     * longer holds the item, which is also what makes a duplicate press structurally moot -- by
     * the time the lock releases on success, there is nothing selected to re-send.
     */
    private void sendWithdrawal() {
        ClientBankingSession session = ClientBankingSession.active();
        if (session == null || session.selectedStoredItem() == null) return;
        if (!session.beginPending(
                com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload.Operation.WITHDRAWAL)) {
            return;
        }
        com.seggellion.britannia_mod.network.ClientNetworkHandler.sendToServer(
                new com.seggellion.britannia_mod.network.payload.BankWithdrawalRequestC2SPayload(
                        session.tellerEntityId(), session.selectedStoredItem()));
        refreshWithdrawState(session);
        refreshCurrencyButtons(session);
    }

    /**
     * Mirrors the session onto the button: active only with a live selection and no pending
     * mutation. Runs from {@code render} because both inputs change from packets, not from
     * anything this screen does.
     */
    private void refreshWithdrawState(ClientBankingSession session) {
        if (withdrawButton == null) return;
        boolean pending = session.isMutationPending();
        withdrawButton.setPending(pending
                && session.pendingOperation()
                        == com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload.Operation.WITHDRAWAL);
        withdrawButton.active = !pending && session.selectedStoredItem() != null;
    }

    private void returnToMain() {
        com.seggellion.britannia_mod.client.screen.bank.BankNavigation.beginNavigation(ClientBankingSession.active());
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

        // Milestone 11: the cells have contents now. Contents draw over the cell chrome, hover
        // highlights over the contents (vanilla's own layering), tooltips last of all.
        drawBankContents(graphics, session, mouseX, mouseY);
        drawInventoryContents(graphics, layout.inventoryGrid(), MAIN_INVENTORY_FIRST_SLOT, mouseX, mouseY);
        drawInventoryContents(graphics, layout.hotbar(), 0, mouseX, mouseY);

        BankDialogueFrame.renderStatusOnDark(
                graphics, font, layout.contentLeft(), layout.statusY(), layout.statusMaxWidth(),
                BankStatusPresenter.statusFor(session)
        );

        refreshWithdrawState(session);
        refreshCurrencyButtons(session);

        // Milestone 12: the source-changed watchdog runs every frame, and the drag visuals draw
        // last so the ghost rides above everything. Tooltips are suppressed while dragging --
        // that is how "the tooltip does not obscure the carried item" is satisfied: it is not
        // dodged, it is absent.
        if (drag != null && drag.isGestureLive()) {
            drag.tick(sourceSnapshot(drag.sourceSlot()));
        }
        if (drag != null && drag.isDragging()) {
            renderDragVisuals(graphics, mouseX, mouseY);
        } else {
            renderHoverTooltip(graphics, session, mouseX, mouseY);
        }
    }

    /**
     * The gesture's four visual signals, none of them colour-only (design §16): the source cell
     * is dimmed and outlined (shape), the bank grid gains a bright border while it is the valid
     * target (shape), the ghost stack follows the cursor (motion), and an invalid hover marks the
     * ghost with a cross (glyph).
     */
    private void renderDragVisuals(GuiGraphics graphics, int mouseX, int mouseY) {
        int slot = drag.sourceSlot();
        BankGridGeometry sourceGrid = slot >= MAIN_INVENTORY_FIRST_SLOT ? layout.inventoryGrid() : layout.hotbar();
        int sourceCell = slot >= MAIN_INVENTORY_FIRST_SLOT ? slot - MAIN_INVENTORY_FIRST_SLOT : slot;
        graphics.fill(sourceGrid.cellLeft(sourceCell), sourceGrid.cellTop(sourceCell),
                sourceGrid.cellLeft(sourceCell) + sourceGrid.cellPitch(),
                sourceGrid.cellTop(sourceCell) + sourceGrid.cellPitch(), 0x99101010);
        graphics.renderOutline(sourceGrid.cellLeft(sourceCell), sourceGrid.cellTop(sourceCell),
                sourceGrid.cellPitch(), sourceGrid.cellPitch(), 0xFFE9DCC3);

        boolean overValid = drag.state()
                == com.seggellion.britannia_mod.client.screen.bank.BankDragController.State.DRAGGING_OVER_VALID;
        if (overValid) {
            BankGridGeometry bank = layout.bankGrid();
            graphics.renderOutline(bank.left() - 1, bank.top() - 1, bank.width() + 2, bank.height() + 2, 0xFFFFD24A);
            graphics.fill(bank.left(), bank.top(), bank.right(), bank.bottom(), 0x2AFFD24A);
        }

        net.minecraft.world.item.ItemStack carried =
                Minecraft.getInstance().player.getInventory().getItem(slot);
        if (!carried.isEmpty()) {
            // The whole live stack rides the cursor -- whole-stack deposits, design §10.2 --
            // slightly offset so the cursor tip stays visible over the drop region.
            int ghostX = mouseX - 8;
            int ghostY = mouseY - 8;
            graphics.renderItem(carried, ghostX, ghostY);
            graphics.renderItemDecorations(font, carried, ghostX, ghostY);
            if (!overValid) {
                graphics.drawString(font, "✕", ghostX + 14, ghostY - 2, 0xFFFF5555, true);
            }
        }
    }

    /**
     * The vault's contents: icon, count numeral, selection ring, hover highlight. Selection is
     * matched by public id against the session -- never by cell index, so it stays on the same
     * item when a refresh reorders the list underneath the view.
     */
    private void drawBankContents(GuiGraphics graphics, ClientBankingSession session, int mouseX, int mouseY) {
        BankGridGeometry grid = layout.bankGrid();
        java.util.List<com.seggellion.britannia_mod.service.banking.BankItemSummary> items = session.bankItems();
        Integer hoverCell = grid.cellIndexAt(mouseX, mouseY);

        for (int cell = 0; cell < grid.capacity(); cell++) {
            int itemIndex = scroll.itemIndexFor(cell, BankBoxLayout.COLUMNS, items.size());
            if (itemIndex < 0) continue;
            com.seggellion.britannia_mod.service.banking.BankItemSummary summary = items.get(itemIndex);

            boolean selected = summary.publicId().equals(session.selectedStoredItem());
            if (selected) {
                // A ring, not just a tint: shape carries the selected state for anyone who cannot
                // rely on colour (design §16), and it survives any icon drawn inside it.
                graphics.fill(grid.cellLeft(cell), grid.cellTop(cell),
                        grid.cellLeft(cell) + grid.cellPitch(), grid.cellTop(cell) + grid.cellPitch(), 0x5FFFD24A);
                graphics.renderOutline(grid.cellLeft(cell), grid.cellTop(cell),
                        grid.cellPitch(), grid.cellPitch(), 0xFFFFD24A);
            }

            net.minecraft.world.item.ItemStack icon = BankItemIcon.iconFor(summary);
            graphics.renderItem(icon, grid.iconLeft(cell), grid.iconTop(cell));
            graphics.renderItemDecorations(font, icon, grid.iconLeft(cell), grid.iconTop(cell));
        }

        if (hoverCell != null) {
            graphics.fill(grid.cellLeft(hoverCell), grid.cellTop(hoverCell),
                    grid.cellLeft(hoverCell) + grid.cellPitch(), grid.cellTop(hoverCell) + grid.cellPitch(), 0x50FFFFFF);
        }
    }

    /**
     * One player grid: the live stacks, with ineligible ones dimmed. {@code firstSlot} maps cell
     * 0 onto the vanilla inventory index -- 9 for the three main rows, 0 for the hotbar, the same
     * split every container screen uses.
     */
    private void drawInventoryContents(GuiGraphics graphics, BankGridGeometry grid, int firstSlot, int mouseX, int mouseY) {
        net.minecraft.world.entity.player.Inventory inventory = Minecraft.getInstance().player.getInventory();
        Integer hoverCell = grid.cellIndexAt(mouseX, mouseY);

        for (int cell = 0; cell < grid.capacity(); cell++) {
            net.minecraft.world.item.ItemStack stack = inventory.getItem(firstSlot + cell);
            if (stack.isEmpty()) continue;

            graphics.renderItem(stack, grid.iconLeft(cell), grid.iconTop(cell));
            graphics.renderItemDecorations(font, stack, grid.iconLeft(cell), grid.iconTop(cell));

            if (!BankDepositHint.isDepositable(stack)) {
                // Dimmed icon; the tooltip carries the words. Brightness plus text keeps the
                // state readable without leaning on hue alone (design §16).
                graphics.fill(grid.cellLeft(cell) + 1, grid.cellTop(cell) + 1,
                        grid.cellLeft(cell) + grid.cellPitch() - 1, grid.cellTop(cell) + grid.cellPitch() - 1, 0x88101010);
            }
        }

        if (hoverCell != null && !inventory.getItem(firstSlot + hoverCell).isEmpty()) {
            graphics.fill(grid.cellLeft(hoverCell), grid.cellTop(hoverCell),
                    grid.cellLeft(hoverCell) + grid.cellPitch(), grid.cellTop(hoverCell) + grid.cellPitch(), 0x50FFFFFF);
        }
    }

    /** Exactly one tooltip, for whichever cell the cursor is over. */
    private void renderHoverTooltip(GuiGraphics graphics, ClientBankingSession session, int mouseX, int mouseY) {
        Integer bankCell = layout.bankGrid().cellIndexAt(mouseX, mouseY);
        if (bankCell != null) {
            int itemIndex = scroll.itemIndexFor(bankCell, BankBoxLayout.COLUMNS, session.bankItems().size());
            if (itemIndex >= 0) {
                com.seggellion.britannia_mod.service.banking.BankItemSummary summary = session.bankItems().get(itemIndex);
                java.util.List<Component> lines = new java.util.ArrayList<>();
                lines.add(summary.displayName() != null
                        ? Component.literal(summary.displayName())
                        : Component.translatable("screen.britannia_mod.bank.box.unknown_item"));
                lines.add(Component.translatable(
                                "screen.britannia_mod.bank.box.tooltip_weight", formatWeight(summary.weight()))
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
                // Offered only when Rails says this row is cashable right now, so the hint and
                // the gesture can never disagree.
                if (summary.isChequeRedeemable()) {
                    lines.add(Component.translatable("screen.britannia_mod.bank.box.stored_cheque_hint")
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
                }
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
            return;
        }

        renderInventoryTooltip(graphics, layout.inventoryGrid(), MAIN_INVENTORY_FIRST_SLOT, mouseX, mouseY);
        renderInventoryTooltip(graphics, layout.hotbar(), 0, mouseX, mouseY);
    }

    private void renderInventoryTooltip(GuiGraphics graphics, BankGridGeometry grid, int firstSlot, int mouseX, int mouseY) {
        Integer cell = grid.cellIndexAt(mouseX, mouseY);
        if (cell == null) return;
        net.minecraft.world.item.ItemStack stack =
                Minecraft.getInstance().player.getInventory().getItem(firstSlot + cell);
        if (stack.isEmpty()) return;

        java.util.List<Component> lines = new java.util.ArrayList<>(getTooltipFromItem(Minecraft.getInstance(), stack));
        if (!BankDepositHint.isDepositable(stack)) {
            lines.add(Component.translatable("screen.britannia_mod.bank.box.cannot_bank")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        // Milestone 17 gate corrective: a cheque has two gestures now, so the tooltip names both.
        if (stack.getItem() == com.seggellion.britannia_mod.registry.ItemRegistry.BANK_CHEQUE.get()) {
            lines.add(Component.translatable("screen.britannia_mod.bank.box.cheque_hint")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    /**
     * Milestone 11: single-click selection, no timer anywhere. {@code BankBoxSelection} owns the
     * semantics and is tested; this only routes the event. Inventory cells deliberately do
     * nothing yet -- clicking a stack does not deposit until the drag engine lands (Milestone 13).
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ClientBankingSession session = ClientBankingSession.active();
        if (button == 0 && session != null && layout != null) {
            // Cashing a cheque that is already in the vault: the same double-click, on the other
            // grid. Checked before selection so the completing press cashes rather than merely
            // re-selecting; the FIRST press falls through and selects as usual, which is what
            // makes the gesture discoverable rather than a hidden shortcut.
            if (registerVaultChequeClick(session, mouseX, mouseY)) return true;

            BankBoxSelection.Result result =
                    BankBoxSelection.handleClick(session, layout.bankGrid(), scroll, mouseX, mouseY);
            if (result != BankBoxSelection.Result.OUTSIDE) return true;

            // Milestone 12: a press on a player stack arms a potential drag. Released under the
            // threshold it is a click, and inventory clicks still do nothing -- consistent with
            // Milestone 11.
            //
            // Milestone 17 gate corrective, the one exception: a DOUBLE-click on a pack cheque
            // cashes it. Checked before the drag arms, so the completing press sends the
            // redemption instead of starting a second gesture. A single press on a cheque still
            // arms the drag as usual -- dragging it to the vault STORES it now (owner's ADR-016
            // override); cashing is only ever this deliberate second click.
            int slot = inventorySlotAt(mouseX, mouseY);
            if (slot >= 0 && drag != null) {
                net.minecraft.world.item.ItemStack stack =
                        Minecraft.getInstance().player.getInventory().getItem(slot);
                boolean cheque = !stack.isEmpty()
                        && stack.getItem() == com.seggellion.britannia_mod.registry.ItemRegistry.BANK_CHEQUE.get();
                if (cheque) {
                    if (chequeDoubleClick.register(slot, System.currentTimeMillis())) {
                        sendChequeRedemption(slot);
                        return true;
                    }
                } else {
                    chequeDoubleClick.reset();
                }
                if (!stack.isEmpty() && drag.onPress(
                        slot, BankDepositHint.isDepositable(stack), sourceSnapshot(slot),
                        mouseX, mouseY, session.isMutationPending())) {
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Milestone 13: the armed handoff becomes the deposit -- or is safely dropped.
     *
     * <p>Order matters and each step earns its place (design §10.6):
     * <ol>
     *   <li><b>Release-time re-check.</b> The per-frame watchdog covered the drag; this covers
     *       the final frame. The live stack must still be exactly what was pressed, and still
     *       depositable. Any mismatch drops the handoff and sends nothing -- the player keeps
     *       their items and nothing pretends otherwise.</li>
     *   <li><b>The session lock is claimed before the packet goes out</b>, and a failed claim
     *       sends nothing -- the same one-press-one-request rule every other mutation uses,
     *       and the second half of duplicate protection (the machine's single-handoff rule is
     *       the first).</li>
     *   <li><b>The packet is the same {@code BankDepositRequestC2SPayload} the legacy screen
     *       sends</b>: teller entity id and the live slot index, nothing else. Milestone 0 §3.3
     *       established the consequence -- the server routes item, coin and cheque deposits from
     *       the live slot itself, so the drag inherits all three routes and every validation
     *       unchanged. No local removal, no local balance change; what happens next arrives as a
     *       refresh push or a result payload, exactly like every other operation.</li>
     * </ol>
     */
    private void sendDragDeposit() {
        ClientBankingSession session = ClientBankingSession.active();
        int slot = drag.sourceSlot();
        if (session == null || slot < 0) {
            drag.completeHandoff();
            return;
        }

        net.minecraft.world.item.ItemStack live = Minecraft.getInstance().player.getInventory().getItem(slot);
        boolean stillSameStack = drag.handoffSourceUnchanged(sourceSnapshot(slot));
        if (live.isEmpty() || !stillSameStack || !BankDepositHint.isDepositable(live)) {
            drag.completeHandoff();
            return;
        }
        if (!session.beginPending(
                com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload.Operation.DEPOSIT)) {
            drag.completeHandoff();
            return;
        }

        com.seggellion.britannia_mod.network.ClientNetworkHandler.sendToServer(
                new com.seggellion.britannia_mod.network.payload.BankDepositRequestC2SPayload(
                        session.tellerEntityId(), slot));
        drag.completeHandoff();
    }

    /**
     * Feeds a bank-grid press to the vault tracker, and cashes when it completes a double-click.
     *
     * @return whether the event was consumed by a cashing gesture
     */
    private boolean registerVaultChequeClick(ClientBankingSession session, double mouseX, double mouseY) {
        Integer cell = layout.bankGrid().cellIndexAt(mouseX, mouseY);
        if (cell == null || session.isMutationPending()) {
            return false;
        }
        int itemIndex = scroll.itemIndexFor(cell, BankBoxLayout.COLUMNS, session.bankItems().size());
        if (itemIndex < 0) {
            vaultChequeDoubleClick.reset();
            return false;
        }
        com.seggellion.britannia_mod.service.banking.BankItemSummary summary = session.bankItems().get(itemIndex);
        // Only an item Rails says is cashable right now offers the gesture. A spent, cancelled,
        // voided or legacy (unlinked) cheque is an ordinary stored item here -- offering a click
        // that would then fail is the dishonest affordance this epic removed everywhere else.
        if (!summary.isChequeRedeemable()) {
            vaultChequeDoubleClick.reset();
            return false;
        }
        if (!vaultChequeDoubleClick.register(cell, System.currentTimeMillis())) {
            return false;
        }
        sendStoredChequeRedemption(summary.publicId());
        return true;
    }

    /**
     * Cashing a stored cheque: the vault row's own public id, never a grid position (design
     * §9.6), because a refresh can reorder the vault between the two clicks and this action
     * destroys value. Lock first, packet second, and the refresh push tells the player what
     * happened -- the row leaves the grid and the balance rises in one snapshot.
     */
    private void sendStoredChequeRedemption(java.util.UUID bankItemPublicId) {
        ClientBankingSession session = ClientBankingSession.active();
        if (session == null) return;
        if (!session.beginPending(
                com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload.Operation.CHEQUE_REDEMPTION)) {
            return;
        }
        com.seggellion.britannia_mod.network.ClientNetworkHandler.sendToServer(
                new com.seggellion.britannia_mod.network.payload.BankStoredChequeRedemptionRequestC2SPayload(
                        session.tellerEntityId(), bankItemPublicId));
    }

    /**
     * Milestone 17 gate corrective: the explicit cashing request. Same shape as every other
     * mutation -- revalidate the live slot, claim the session lock before the packet, send a
     * selection reference only, and let the refresh push or the result payload say what
     * happened. The server re-reads the slot and rejects locally if it is not actually a
     * cheque, so this pre-check is UX, never the boundary.
     */
    private void sendChequeRedemption(int slot) {
        ClientBankingSession session = ClientBankingSession.active();
        if (session == null) return;

        net.minecraft.world.item.ItemStack live = Minecraft.getInstance().player.getInventory().getItem(slot);
        if (live.isEmpty()
                || live.getItem() != com.seggellion.britannia_mod.registry.ItemRegistry.BANK_CHEQUE.get()) {
            return;
        }
        if (!session.beginPending(
                com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload.Operation.CHEQUE_REDEMPTION)) {
            return;
        }

        com.seggellion.britannia_mod.network.ClientNetworkHandler.sendToServer(
                new com.seggellion.britannia_mod.network.payload.BankChequeRedemptionRequestC2SPayload(
                        session.tellerEntityId(), slot));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && drag != null && drag.isGestureLive()) {
            drag.onMove(mouseX, mouseY);
            // Once the gesture is genuinely a drag (past the 4px threshold, not mouse jitter),
            // it stops being a potential first click of a double-click.
            if (drag.isDragging()) {
                chequeDoubleClick.reset();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && drag != null) {
            com.seggellion.britannia_mod.client.screen.bank.BankDragController.ReleaseOutcome outcome =
                    drag.onRelease(mouseX, mouseY);
            switch (outcome) {
                case DROPPED_ON_BANK -> {
                    sendDragDeposit();
                    return true;
                }
                case CANCELLED, CLICK -> {
                    return true;
                }
                case NONE -> {
                    // fall through to super
                }
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
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

    /**
     * Escape belongs to the drag while one is live -- cancelling the gesture, not the screen
     * (playbook Milestone 12's cancellation list). Otherwise design §5.2: Escape closes banking,
     * and Back is the only route to the hub.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (drag != null && drag.cancel()) return true;
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
