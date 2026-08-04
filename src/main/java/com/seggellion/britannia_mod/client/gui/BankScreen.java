package com.seggellion.britannia_mod.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.seggellion.britannia_mod.bank.currency.CurrencyItemRegistry;
import com.seggellion.britannia_mod.client.screen.bank.ClientBankingSession;
import com.seggellion.britannia_mod.bank.item.BankItemEligibility;
import com.seggellion.britannia_mod.component.BankChequeData;
import com.seggellion.britannia_mod.dialogue.DialogueLayout;
import com.seggellion.britannia_mod.dialogue.DialogueViewModel;
import com.seggellion.britannia_mod.economy.CoinConversion;
import com.seggellion.britannia_mod.network.ClientNetworkHandler;
import com.seggellion.britannia_mod.network.payload.BankAccountOpenedS2CPayload;
import com.seggellion.britannia_mod.network.payload.BankCurrencyWithdrawalRequestC2SPayload;
import com.seggellion.britannia_mod.network.payload.BankDepositRequestC2SPayload;
import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload;
import com.seggellion.britannia_mod.network.payload.BankWithdrawalRequestC2SPayload;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.service.banking.BankItemSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Milestone 7 Slice B's real, read-only Bank Screen, extended by Milestone 9 Slice 3a into a
 * genuinely interactive one: a deposit-source picker over the player's own inventory and a
 * withdrawal picker over the account's real {@code bank_items} list, both adapting {@code
 * NpcCatalogScreen}'s confirmed, real precedent (manual row rendering plus a
 * double-click-within-400ms selection gesture) rather than a never-before-used real {@code
 * AbstractContainerMenu}/{@code Slot} mechanism -- see this program's own Slice 3a recon: no
 * such slot-based menu exists anywhere else in this codebase, including the trader system,
 * which uses this exact same plain-{@code Screen}-plus-custom-widget approach.
 *
 * <p>Still a plain client {@link Screen}, not an {@link
 * net.minecraft.world.inventory.AbstractContainerMenu} -- see the original Slice B class doc
 * reasoning, now extended: the one new piece of mutable, exploitable state this screen
 * introduces (which slot/bank item is selected, and the pending deposit/withdrawal itself) is
 * never trusted by the server as anything more than a selection reference ({@code
 * BankDepositRequestC2SPayload}/{@code BankWithdrawalRequestC2SPayload} carry only a slot index
 * or a bank item's public id) -- every real fact (fingerprint, weight, ownership, availability)
 * is independently re-derived and re-validated server-side by {@code
 * BankingDepositProxyService}/{@code BankingWithdrawalProxyService}, exactly mirroring {@code
 * SellItemsC2SPayload}'s own established trust model.
 *
 * <p>Client-side eligibility feedback (graying out an ineligible inventory slot via {@link
 * BankItemEligibility}) is a UX nicety only, not a security boundary -- a modified client
 * sending a slot index for an ineligible item is rejected the exact same way by {@code
 * BankingDepositProxyService}'s own, already-tested local-rejection path regardless of what
 * this screen displayed.
 */
public final class BankScreen extends Screen {
    private static final int CONTENT_MARGIN = 20;
    private static final int LINE_GAP = 2;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;
    private static final int PANEL_GAP = 10;
    private static final int ROW_H = 18;
    private static final int MIN_PANEL_HEIGHT = 50;
    private static final long DOUBLE_CLICK_MS = 400;
    private static final int MAIN_INVENTORY_SLOTS = 36;
    /**
     * Milestone 10 Slice 2: reserved at the TOP of the withdraw panel's own rect for the
     * currency amount box and the three denomination buttons -- the bank_items list itself
     * starts below this, not at the panel's own top edge. The partial-stack-deposit scope
     * decision does not mirror here: deposit reads its amount from a live inventory slot (no
     * input needed), but withdrawal pulls from a balance with no slot to read a count from, so
     * an explicit amount entry is a genuinely new requirement, not a copy of deposit's UI.
     */
    private static final int CURRENCY_CONTROLS_HEIGHT = 40;
    private static final int CURRENCY_BOX_HEIGHT = 16;
    private static final int CURRENCY_BUTTON_HEIGHT = 16;
    private static final int CURRENCY_ROW_GAP = 4;

    private final BankAccountOpenedS2CPayload account;
    private final List<BankItemSummary> bankItems;
    private final DialogueViewModel headerView;
    private DialogueLayout headerLayout;
    private Component headerBody;

    private Button depositButton;
    private Button withdrawButton;

    private EditBox currencyAmountBox;
    private Button withdrawGoldButton;
    private Button withdrawSilverButton;
    private Button withdrawCopperButton;

    private int panelsTop;
    private int panelHeight;
    private int depositPanelX;
    private int withdrawPanelX;
    private int panelWidth;

    private float depositScrollOffset;
    private float withdrawScrollOffset;

    @Nullable
    private Integer selectedDepositSlot;
    @Nullable
    private UUID selectedWithdrawalItem;

    private int lastClickedDepositSlot = -1;
    private long lastDepositClickTimeMs;
    @Nullable
    private UUID lastClickedWithdrawalItem;
    private long lastWithdrawalClickTimeMs;

    private boolean depositPending;
    private boolean withdrawalPending;

    @Nullable
    private Component statusMessage;

    private static final Component CLEAN_REJECTION_MESSAGE = Component.literal(
            "The teller checks the ledger and shakes their head: \"I'm afraid I can't complete that right now.\""
    );
    private static final Component RECONCILIATION_REQUIRED_MESSAGE = Component.literal(
            "Something has gone wrong with this transaction that requires staff attention. "
                    + "Please contact a server admin -- do not attempt this again until it is resolved."
    );
    private static final Component CHEQUE_NOT_FOUND_MESSAGE = Component.literal(
            "The teller inspects the cheque and frowns: \"I don't recognize this instrument at all.\""
    );
    private static final Component CHEQUE_ALREADY_REDEEMED_MESSAGE = Component.literal(
            "The teller checks the ledger: \"This cheque has already been redeemed.\""
    );
    private static final Component CHEQUE_CANCELLED_MESSAGE = Component.literal(
            "The teller checks the ledger: \"This cheque was cancelled and can no longer be redeemed.\""
    );
    private static final Component CHEQUE_VOIDED_MESSAGE = Component.literal(
            "The teller checks the ledger: \"This cheque was voided and can no longer be redeemed.\""
    );

    public BankScreen(BankAccountOpenedS2CPayload account) {
        super(Component.literal("Bank Account"));
        this.account = Objects.requireNonNull(account, "account");
        this.bankItems = account.bankItems();
        this.headerView = new DialogueViewModel(
                account.tellerName(), account.tellerGender(), "", "", "Here is your account.", "bank", false, List.of()
        );
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();

        headerBody = DialoguePresentation.text(headerView.body());
        DialogueLayout initialLayout = DialogueLayout.calculate(width, 1, 0, font.lineHeight, false);
        int textLineCount = font.split(headerBody, initialLayout.maxTextWidth()).size();
        headerLayout = DialogueLayout.calculate(width, textLineCount, 0, font.lineHeight, false);

        int buttonWidth = (width - (CONTENT_MARGIN * 2) - (BUTTON_GAP * 2)) / 3;
        int buttonY = height - CONTENT_MARGIN - BUTTON_HEIGHT;

        depositButton = Button.builder(Component.literal("Deposit"), ignored -> onDepositPressed())
                .bounds(CONTENT_MARGIN, buttonY, buttonWidth, BUTTON_HEIGHT)
                .build();
        withdrawButton = Button.builder(Component.literal("Withdraw"), ignored -> onWithdrawPressed())
                .bounds(CONTENT_MARGIN + buttonWidth + BUTTON_GAP, buttonY, buttonWidth, BUTTON_HEIGHT)
                .build();
        // Milestone 11 NeoForge Slice 1: real as of this slice (previously a permanently
        // disabled placeholder) -- opens the teller check-creation screen.
        Button checksButton = Button.builder(Component.literal("Checks"), ignored -> onChecksPressed())
                .bounds(CONTENT_MARGIN + (buttonWidth + BUTTON_GAP) * 2, buttonY, buttonWidth, BUTTON_HEIGHT)
                .build();

        addRenderableWidget(depositButton);
        addRenderableWidget(withdrawButton);
        addRenderableWidget(checksButton);

        int ledgerLinesBottom = DialogueLayout.TOP_SECTION_HEIGHT + CONTENT_MARGIN
                + font.lineHeight + LINE_GAP    // weight line
                + font.lineHeight + LINE_GAP    // balance line
                + (account.cityDisplayName() != null ? font.lineHeight + LINE_GAP : 0)
                + 6;
        int statusLineHeight = font.lineHeight + 4;
        panelsTop = ledgerLinesBottom;
        int panelsBottom = buttonY - BUTTON_GAP - statusLineHeight;
        panelHeight = Math.max(MIN_PANEL_HEIGHT, panelsBottom - panelsTop);
        panelWidth = (width - (CONTENT_MARGIN * 2) - PANEL_GAP) / 2;
        depositPanelX = CONTENT_MARGIN;
        withdrawPanelX = CONTENT_MARGIN + panelWidth + PANEL_GAP;

        currencyAmountBox = new EditBox(font, withdrawPanelX, panelsTop, panelWidth, CURRENCY_BOX_HEIGHT, Component.literal("Amount"));
        currencyAmountBox.setMaxLength(10);
        currencyAmountBox.setValue("");
        currencyAmountBox.setResponder(ignored -> refreshButtonStates());
        addRenderableWidget(currencyAmountBox);

        int currencyButtonY = panelsTop + CURRENCY_BOX_HEIGHT + CURRENCY_ROW_GAP;
        int currencyButtonWidth = (panelWidth - CURRENCY_ROW_GAP * 2) / 3;
        withdrawGoldButton = Button.builder(Component.literal("Gold"), ignored -> onWithdrawCurrencyPressed(CurrencyItemRegistry.GOLD_KEY))
                .bounds(withdrawPanelX, currencyButtonY, currencyButtonWidth, CURRENCY_BUTTON_HEIGHT)
                .build();
        withdrawSilverButton = Button.builder(Component.literal("Silver"), ignored -> onWithdrawCurrencyPressed(CurrencyItemRegistry.SILVER_KEY))
                .bounds(withdrawPanelX + currencyButtonWidth + CURRENCY_ROW_GAP, currencyButtonY, currencyButtonWidth, CURRENCY_BUTTON_HEIGHT)
                .build();
        withdrawCopperButton = Button.builder(Component.literal("Copper"), ignored -> onWithdrawCurrencyPressed(CurrencyItemRegistry.COPPER_KEY))
                .bounds(withdrawPanelX + (currencyButtonWidth + CURRENCY_ROW_GAP) * 2, currencyButtonY, currencyButtonWidth, CURRENCY_BUTTON_HEIGHT)
                .build();
        addRenderableWidget(withdrawGoldButton);
        addRenderableWidget(withdrawSilverButton);
        addRenderableWidget(withdrawCopperButton);

        refreshButtonStates();
    }

    private void refreshButtonStates() {
        boolean anyPending = depositPending || withdrawalPending;
        depositButton.active = !anyPending && selectedDepositSlot != null && isSelectedDepositSlotEligible();
        withdrawButton.active = !anyPending && selectedWithdrawalItem != null;
        depositButton.setMessage(Component.literal(depositPending ? "Depositing..." : "Deposit"));
        withdrawButton.setMessage(Component.literal(withdrawalPending ? "Withdrawing..." : "Withdraw"));

        boolean validAmount = parsePositiveAmount(currencyAmountBox.getValue()) > 0;
        withdrawGoldButton.active = !anyPending && validAmount;
        withdrawSilverButton.active = !anyPending && validAmount;
        withdrawCopperButton.active = !anyPending && validAmount;
    }

    /** {@code 0} for anything not a genuine positive integer -- never throws on malformed input. */
    private static int parsePositiveAmount(String value) {
        try {
            int amount = Integer.parseInt(value.trim());
            return Math.max(amount, 0);
        } catch (NumberFormatException malformed) {
            return 0;
        }
    }

    private boolean isSelectedDepositSlotEligible() {
        if (selectedDepositSlot == null) return false;
        ItemStack stack = clientInventory().getItem(selectedDepositSlot);
        return isDepositable(stack);
    }

    /**
     * Milestone 11 Slice 2: a slot is depositable if it passes item eligibility, is a bare coin
     * stack, OR is a bank cheque -- ADR-016's own "double-click-in-inventory" redemption gesture
     * reuses this exact picker/selection mechanism rather than inventing a separate one.
     * {@link CurrencyItemRegistry#isCurrencyStack}/{@link ItemRegistry#BANK_CHEQUE} are checked
     * first, deliberately mirroring the server-side routing order in {@code
     * BankingTransferPacketService#handleDeposit}: the same stack this displays as depositable
     * is exactly the stack that router would send down the currency/redemption protocol. Still
     * a UX nicety only, not a security boundary -- the server independently re-derives the
     * routing and every validation from the live slot regardless of what this screen displayed
     * (the same trust model as before, unchanged).
     *
     * <p>A container holding coins is NOT a coin stack (top-level item identity only) and
     * remains ineligible/greyed, matching the server's own routing polarity exactly.
     */
    private static boolean isDepositable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (CurrencyItemRegistry.isCurrencyStack(stack)) return true;
        if (stack.getItem() == ItemRegistry.BANK_CHEQUE.get()) return true;
        try {
            BankItemEligibility.checkEligible(stack);
            return true;
        } catch (BankItemEligibility.IneligibleItemException ineligible) {
            return false;
        }
    }

    private Inventory clientInventory() {
        Player player = Objects.requireNonNull(Minecraft.getInstance().player, "player");
        return player.getInventory();
    }

    // ---------- Checks action ----------

    private void onChecksPressed() {
        if (depositPending || withdrawalPending) return;
        // Milestone 7: the cheque screen reads the shared session rather than a captured payload,
        // so it takes no argument now. This whole screen has been unreachable since the Milestone
        // 4 cutover and is deleted at Milestone 19; the call is kept compiling, not kept working.
        Minecraft.getInstance().setScreen(new com.seggellion.britannia_mod.client.screen.BankChequeIssuanceScreen());
    }

    // ---------- Deposit action ----------

    private void onDepositPressed() {
        if (depositPending || withdrawalPending || selectedDepositSlot == null) return;
        if (!isSelectedDepositSlotEligible()) return;
        depositPending = true;
        statusMessage = null;
        refreshButtonStates();
        ClientNetworkHandler.sendToServer(new BankDepositRequestC2SPayload(account.entityId(), selectedDepositSlot));
    }

    private void onWithdrawPressed() {
        if (depositPending || withdrawalPending || selectedWithdrawalItem == null) return;
        withdrawalPending = true;
        statusMessage = null;
        refreshButtonStates();
        ClientNetworkHandler.sendToServer(new BankWithdrawalRequestC2SPayload(account.entityId(), selectedWithdrawalItem));
    }

    /**
     * Milestone 10 Slice 2: the currency counterpart to {@link #onWithdrawPressed}. Shares the
     * same {@code withdrawalPending} flag and the same {@code acceptTransferResult} handling --
     * both kinds of withdrawal report through the identical {@code Operation.WITHDRAWAL} result
     * channel, so no separate pending/result state is needed here.
     */
    private void onWithdrawCurrencyPressed(String currencyKey) {
        if (depositPending || withdrawalPending) return;
        int amount = parsePositiveAmount(currencyAmountBox.getValue());
        if (amount <= 0) return;
        withdrawalPending = true;
        statusMessage = null;
        refreshButtonStates();
        ClientNetworkHandler.sendToServer(new BankCurrencyWithdrawalRequestC2SPayload(account.entityId(), currencyKey, amount));
    }

    /** Invoked by {@code ClientNetworkHandler} on a clean-rejection or reconciliation-required result. */
    public void acceptTransferResult(BankTransferResultS2CPayload payload) {
        switch (payload.operation()) {
            case DEPOSIT, CHEQUE_REDEMPTION -> depositPending = false;
            case WITHDRAWAL -> withdrawalPending = false;
            // BankScreen itself never triggers a cheque issuance (BankChequeIssuanceScreen's
            // own acceptTransferResult handles that operation) -- unreachable here in practice,
            // kept only so this switch stays exhaustive as the shared Operation enum grows.
            case CHEQUE_ISSUANCE -> { }
        }
        statusMessage = switch (payload.kind()) {
            case CLEAN_REJECTION -> CLEAN_REJECTION_MESSAGE;
            case RECONCILIATION_REQUIRED -> RECONCILIATION_REQUIRED_MESSAGE;
            // Milestone 11 Slice 1: never actually sent for a DEPOSIT/WITHDRAWAL result (only
            // cheque issuance reaches PendingDelivery) -- kept only for switch exhaustiveness.
            case PENDING_DELIVERY -> CLEAN_REJECTION_MESSAGE;
            // Milestone 11 Slice 2 (Operation.CHEQUE_REDEMPTION only): each rendered as its own
            // clear, diegetic message per Codex Prompt 11's own requirement, never folded into
            // the generic CLEAN_REJECTION line -- these are the outcomes this endpoint actually
            // returns in ordinary play (an already-redeemed or invalid cheque), not rare edge
            // cases. Never modifies any value locally; the physical cheque item is already gone
            // by the time any of these arrive (see BankingChequeRedemptionProxyService's own
            // "Item disposition on a Rails rejection" docs).
            case CHEQUE_NOT_FOUND -> CHEQUE_NOT_FOUND_MESSAGE;
            case CHEQUE_ALREADY_REDEEMED -> CHEQUE_ALREADY_REDEEMED_MESSAGE;
            case CHEQUE_CANCELLED -> CHEQUE_CANCELLED_MESSAGE;
            case CHEQUE_VOIDED -> CHEQUE_VOIDED_MESSAGE;
            // Milestone 6b: unreachable here. This screen has been unreachable itself since the
            // Milestone 4 cutover, and Deposit All Coins only ever existed on BankBalanceScreen,
            // which renders these properly through BankStatusPresenter. Present solely to keep
            // this switch exhaustive until Milestone 19 deletes the class.
            case NOTHING_TO_DEPOSIT, BALANCE_CAPACITY_EXCEEDED -> CLEAN_REJECTION_MESSAGE;
        };
        refreshButtonStates();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        DialoguePresentation.renderDialogue(graphics, font, headerView, headerLayout, headerBody, "Bank");
        renderLedger(graphics);
        renderDepositPanel(graphics, mouseX, mouseY);
        renderWithdrawPanel(graphics, mouseX, mouseY);
        renderStatusMessage(graphics);
    }

    private void renderLedger(GuiGraphics graphics) {
        int x = CONTENT_MARGIN;
        int y = DialogueLayout.TOP_SECTION_HEIGHT + CONTENT_MARGIN;

        if (account.cityDisplayName() != null) {
            graphics.drawString(
                    font, DialoguePresentation.text("City: " + account.cityDisplayName()),
                    x, y, DialoguePresentation.TEXT_COLOR, false
            );
            y += font.lineHeight + LINE_GAP;
        }

        String weightLine = formatWeight(account.currentWeight()) + " / " + account.weightLimit() + " stones";
        graphics.drawString(font, DialoguePresentation.text(weightLine), x, y, DialoguePresentation.TEXT_COLOR, false);
        y += font.lineHeight + LINE_GAP;

        String balanceLine = account.goldBalance() + "g " + account.silverBalance() + "s " + account.copperBalance() + "c";
        graphics.drawString(font, DialoguePresentation.text(balanceLine), x, y, DialoguePresentation.TEXT_COLOR, false);
    }

    private void renderStatusMessage(GuiGraphics graphics) {
        if (statusMessage == null) return;
        int y = panelsTop + panelHeight + 4;
        graphics.drawString(font, statusMessage, CONTENT_MARGIN, y, 0xFFAA4444, false);
    }

    // ---------- Deposit panel: the player's own inventory ----------

    private void renderDepositPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, DialoguePresentation.text("Deposit from inventory:"), depositPanelX, panelsTop - font.lineHeight - 2,
                DialoguePresentation.TEXT_COLOR, false);
        graphics.fill(depositPanelX, panelsTop, depositPanelX + panelWidth, panelsTop + panelHeight, 0x33000000);

        List<Integer> slots = depositCandidateSlots();
        int maxScroll = Math.max(0, slots.size() * ROW_H - panelHeight);
        depositScrollOffset = Mth.clamp(depositScrollOffset, 0, maxScroll);

        enableScissor(depositPanelX, panelsTop, depositPanelX + panelWidth, panelsTop + panelHeight);
        int rowY = panelsTop - (int) depositScrollOffset;
        for (int slotIndex : slots) {
            if (rowY + ROW_H > panelsTop && rowY < panelsTop + panelHeight) {
                ItemStack stack = clientInventory().getItem(slotIndex);
                boolean eligible = isDepositable(stack);
                boolean selected = selectedDepositSlot != null && selectedDepositSlot == slotIndex;
                int color = !eligible ? 0xFF808080 : (selected ? 0xFF55FF55 : DialoguePresentation.TEXT_COLOR);

                if (selected) {
                    graphics.fill(depositPanelX, rowY, depositPanelX + panelWidth, rowY + ROW_H, 0x33FFFFFF);
                }
                graphics.renderItem(stack, depositPanelX + 2, rowY + 1);
                String name = stack.getHoverName().getString();
                if (!eligible) name = name + " (ineligible)";
                if (font.width(name) > panelWidth - 22) {
                    name = font.substrByWidth(Component.literal(name), panelWidth - 22).getString() + "...";
                }
                graphics.drawString(font, name, depositPanelX + 20, rowY + 5, color, false);
            }
            rowY += ROW_H;
        }
        RenderSystem.disableScissor();
    }

    private List<Integer> depositCandidateSlots() {
        List<Integer> slots = new ArrayList<>();
        Inventory inventory = clientInventory();
        int limit = Math.min(MAIN_INVENTORY_SLOTS, inventory.getContainerSize());
        for (int i = 0; i < limit; i++) {
            if (!inventory.getItem(i).isEmpty()) slots.add(i);
        }
        return slots;
    }

    // ---------- Withdraw panel: the account's real bank_items list ----------

    private int withdrawListTop() {
        return panelsTop + CURRENCY_CONTROLS_HEIGHT;
    }

    private int withdrawListHeight() {
        return Math.max(0, panelHeight - CURRENCY_CONTROLS_HEIGHT);
    }

    private void renderWithdrawPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, DialoguePresentation.text("Withdraw from vault:"), withdrawPanelX, panelsTop - font.lineHeight - 2,
                DialoguePresentation.TEXT_COLOR, false);
        // The currency controls (amount box, denomination buttons) render themselves via
        // addRenderableWidget -- this fill deliberately starts BELOW them (at withdrawListTop(),
        // not panelsTop) so it never draws over top of already-rendered widgets.
        int listTop = withdrawListTop();
        int listHeight = withdrawListHeight();
        graphics.fill(withdrawPanelX, listTop, withdrawPanelX + panelWidth, listTop + listHeight, 0x33000000);

        if (bankItems.isEmpty()) {
            Component empty = DialoguePresentation.text("The vault is empty.");
            graphics.drawString(font, empty, withdrawPanelX + 4, listTop + 4, DialoguePresentation.TEXT_COLOR, false);
            return;
        }

        int maxScroll = Math.max(0, bankItems.size() * ROW_H - listHeight);
        withdrawScrollOffset = Mth.clamp(withdrawScrollOffset, 0, maxScroll);

        enableScissor(withdrawPanelX, listTop, withdrawPanelX + panelWidth, listTop + listHeight);
        int rowY = listTop - (int) withdrawScrollOffset;
        for (BankItemSummary item : bankItems) {
            if (rowY + ROW_H > listTop && rowY < listTop + listHeight) {
                boolean selected = item.publicId().equals(selectedWithdrawalItem);
                int color = selected ? 0xFF55FF55 : DialoguePresentation.TEXT_COLOR;
                if (selected) {
                    graphics.fill(withdrawPanelX, rowY, withdrawPanelX + panelWidth, rowY + ROW_H, 0x33FFFFFF);
                }
                // Milestone 18: what the item actually is, in place of the "Stored item" literal
                // every row used to share. The weight suffix and the width truncation below are
                // untouched -- BankItemSummary#describe supplies the name and the stack count,
                // and falls back to that same literal for a row Rails has no name for.
                String label = item.describe() + " -- " + formatWeight(item.weight()) + " stones";
                if (font.width(label) > panelWidth - 6) {
                    label = font.substrByWidth(Component.literal(label), panelWidth - 6).getString() + "...";
                }
                graphics.drawString(font, label, withdrawPanelX + 4, rowY + 5, color, false);
            }
            rowY += ROW_H;
        }
        RenderSystem.disableScissor();
    }

    /**
     * Robust scissor helper adapted directly from {@code NpcCatalogScreen}'s own real, already
     * proven-necessary fix: {@link GuiGraphics#enableScissor} was found to bleed/misbehave when
     * accounting for GUI scale, so this computes raw window coordinates and applies the scissor
     * directly via {@link RenderSystem}, exactly the same way.
     */
    private void enableScissor(int x, int y, int x2, int y2) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        int windowHeight = Minecraft.getInstance().getWindow().getHeight();

        int w = x2 - x;
        int h = y2 - y;

        int sX = (int) (x * scale);
        int sY = (int) (windowHeight - (y + h) * scale);
        int sW = (int) (w * scale);
        int sH = (int) (h * scale);

        RenderSystem.enableScissor(sX, sY, sW, sH);
    }

    // ---------- Input ----------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !depositPending && !withdrawalPending) {
            if (withinPanel(mouseX, mouseY, depositPanelX)) {
                handleDepositPanelClick(mouseX, mouseY);
                return true;
            }
            // Deliberately the list sub-region only (withdrawListTop()-based), not the full
            // panel rect -- the currency amount box and denomination buttons live in the same
            // panel's top CURRENCY_CONTROLS_HEIGHT, and must fall through to super.mouseClicked
            // below so those real widgets receive their own click, rather than being swallowed
            // here as a (currently empty) list-row click.
            if (withinWithdrawList(mouseX, mouseY)) {
                handleWithdrawPanelClick(mouseX, mouseY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean withinPanel(double mouseX, double mouseY, int panelX) {
        return mouseX >= panelX && mouseX < panelX + panelWidth
                && mouseY >= panelsTop && mouseY < panelsTop + panelHeight;
    }

    private boolean withinWithdrawList(double mouseX, double mouseY) {
        int listTop = withdrawListTop();
        return mouseX >= withdrawPanelX && mouseX < withdrawPanelX + panelWidth
                && mouseY >= listTop && mouseY < listTop + withdrawListHeight();
    }

    private void handleDepositPanelClick(double mouseX, double mouseY) {
        List<Integer> slots = depositCandidateSlots();
        double absoluteY = mouseY - panelsTop + depositScrollOffset;
        int index = (int) (absoluteY / ROW_H);
        if (index < 0 || index >= slots.size()) return;
        int slotIndex = slots.get(index);

        long now = System.currentTimeMillis();
        if (lastClickedDepositSlot == slotIndex && now - lastDepositClickTimeMs < DOUBLE_CLICK_MS) {
            selectedDepositSlot = slotIndex;
            statusMessage = chequeSelectionConfirmationMessage(clientInventory().getItem(slotIndex));
            refreshButtonStates();
            lastClickedDepositSlot = -1;
        } else {
            lastClickedDepositSlot = slotIndex;
            lastDepositClickTimeMs = now;
        }
    }

    /**
     * Milestone 11 Slice 2: the "confirmation prompt" text Codex Prompt 11 calls for when a
     * bank cheque is selected via double-click -- the ONLY thing {@link
     * BankChequeData#displayAmount()} is ever read for on this side. This value is never sent
     * anywhere: it is read purely to render this status line, and clicking "Deposit" afterward
     * sends only the slot index, exactly like every other deposit-panel selection (the server
     * independently re-derives the real, authoritative amount from Rails -- see {@code
     * BankingChequeRedemptionProxyService}'s own docs). {@code null} for any non-cheque
     * selection, leaving the existing plain-selection behavior (no status message) unchanged.
     */
    @Nullable
    private static Component chequeSelectionConfirmationMessage(ItemStack selected) {
        if (selected.isEmpty() || selected.getItem() != ItemRegistry.BANK_CHEQUE.get()) return null;
        BankChequeData data = selected.get(DataComponentRegistry.BANK_CHEQUE_DATA.get());
        if (data == null) return null;

        CoinConversion.CoinCounts coins = CoinConversion.toCoins((int) data.displayAmount());
        StringBuilder amount = new StringBuilder();
        if (coins.gold() > 0) amount.append(coins.gold()).append(" gold ");
        if (coins.silver() > 0) amount.append(coins.silver()).append(" silver ");
        if (coins.copper() > 0 || amount.isEmpty()) amount.append(coins.copper()).append(" copper");
        return Component.literal("Selected a cheque worth " + amount.toString().trim() + ". Click Deposit to redeem it.");
    }

    private void handleWithdrawPanelClick(double mouseX, double mouseY) {
        if (bankItems.isEmpty()) return;
        double absoluteY = mouseY - withdrawListTop() + withdrawScrollOffset;
        int index = (int) (absoluteY / ROW_H);
        if (index < 0 || index >= bankItems.size()) return;
        UUID publicId = bankItems.get(index).publicId();

        long now = System.currentTimeMillis();
        if (publicId.equals(lastClickedWithdrawalItem) && now - lastWithdrawalClickTimeMs < DOUBLE_CLICK_MS) {
            selectedWithdrawalItem = publicId;
            statusMessage = null;
            refreshButtonStates();
            lastClickedWithdrawalItem = null;
        } else {
            lastClickedWithdrawalItem = publicId;
            lastWithdrawalClickTimeMs = now;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float scrollAmount = (float) (scrollY * ROW_H / 2.0);
        if (withinPanel(mouseX, mouseY, depositPanelX)) {
            depositScrollOffset -= scrollAmount;
            return true;
        }
        if (withinPanel(mouseX, mouseY, withdrawPanelX)) {
            withdrawScrollOffset -= scrollAmount;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static String formatWeight(double weight) {
        if (weight == Math.floor(weight) && !Double.isInfinite(weight)) {
            return String.valueOf((long) weight);
        }
        return String.format(Locale.ROOT, "%.1f", weight);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        DialoguePresentation.renderPaperBackground(graphics, width, height);
    }

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

    /**
     * Bank interface rebuild, Milestone 2: closing the screen ends the banking interaction, so the
     * shared session goes with it. Only reached when banking genuinely closes (Escape, or vanilla's
     * own close path) -- navigating to the cheque screen uses {@code setScreen} directly, which
     * calls {@code removed()} rather than this, so a navigation does not drop the session.
     *
     * <p>Any request already in flight is deliberately not cancelled (design §5.3): its result
     * arrives to no session and is dropped rather than fabricated into an outcome. The next
     * {@code bank.open} shows whatever actually happened.
     */
    @Override
    public void onClose() {
        ClientBankingSession.close();
        Minecraft.getInstance().setScreen(null);
    }
}
