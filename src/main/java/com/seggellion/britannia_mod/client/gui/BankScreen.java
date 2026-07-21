package com.seggellion.britannia_mod.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.seggellion.britannia_mod.bank.item.BankItemEligibility;
import com.seggellion.britannia_mod.dialogue.DialogueLayout;
import com.seggellion.britannia_mod.dialogue.DialogueViewModel;
import com.seggellion.britannia_mod.network.ClientNetworkHandler;
import com.seggellion.britannia_mod.network.payload.BankAccountOpenedS2CPayload;
import com.seggellion.britannia_mod.network.payload.BankDepositRequestC2SPayload;
import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload;
import com.seggellion.britannia_mod.network.payload.BankWithdrawalRequestC2SPayload;
import com.seggellion.britannia_mod.service.banking.BankItemSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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

    private final BankAccountOpenedS2CPayload account;
    private final List<BankItemSummary> bankItems;
    private final DialogueViewModel headerView;
    private DialogueLayout headerLayout;
    private Component headerBody;

    private Button depositButton;
    private Button withdrawButton;

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

    public BankScreen(BankAccountOpenedS2CPayload account) {
        super(Component.literal("Bank Account"));
        this.account = Objects.requireNonNull(account, "account");
        this.bankItems = account.bankItems();
        this.headerView = new DialogueViewModel(
                account.tellerName(), "unknown", "", "", "Here is your account.", "bank", false, List.of()
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
        Button checksButton = Button.builder(Component.literal("Checks (not yet available)"), ignored -> {})
                .bounds(CONTENT_MARGIN + (buttonWidth + BUTTON_GAP) * 2, buttonY, buttonWidth, BUTTON_HEIGHT)
                .build();
        checksButton.active = false;

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

        refreshButtonStates();
    }

    private void refreshButtonStates() {
        boolean anyPending = depositPending || withdrawalPending;
        depositButton.active = !anyPending && selectedDepositSlot != null && isSelectedDepositSlotEligible();
        withdrawButton.active = !anyPending && selectedWithdrawalItem != null;
        depositButton.setMessage(Component.literal(depositPending ? "Depositing..." : "Deposit"));
        withdrawButton.setMessage(Component.literal(withdrawalPending ? "Withdrawing..." : "Withdraw"));
    }

    private boolean isSelectedDepositSlotEligible() {
        if (selectedDepositSlot == null) return false;
        ItemStack stack = clientInventory().getItem(selectedDepositSlot);
        return isEligible(stack);
    }

    private static boolean isEligible(ItemStack stack) {
        if (stack.isEmpty()) return false;
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

    /** Invoked by {@code ClientNetworkHandler} on a clean-rejection or reconciliation-required result. */
    public void acceptTransferResult(BankTransferResultS2CPayload payload) {
        switch (payload.operation()) {
            case DEPOSIT -> depositPending = false;
            case WITHDRAWAL -> withdrawalPending = false;
        }
        statusMessage = switch (payload.kind()) {
            case CLEAN_REJECTION -> CLEAN_REJECTION_MESSAGE;
            case RECONCILIATION_REQUIRED -> RECONCILIATION_REQUIRED_MESSAGE;
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
                boolean eligible = isEligible(stack);
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

    private void renderWithdrawPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, DialoguePresentation.text("Withdraw from vault:"), withdrawPanelX, panelsTop - font.lineHeight - 2,
                DialoguePresentation.TEXT_COLOR, false);
        graphics.fill(withdrawPanelX, panelsTop, withdrawPanelX + panelWidth, panelsTop + panelHeight, 0x33000000);

        if (bankItems.isEmpty()) {
            Component empty = DialoguePresentation.text("The vault is empty.");
            graphics.drawString(font, empty, withdrawPanelX + 4, panelsTop + 4, DialoguePresentation.TEXT_COLOR, false);
            return;
        }

        int maxScroll = Math.max(0, bankItems.size() * ROW_H - panelHeight);
        withdrawScrollOffset = Mth.clamp(withdrawScrollOffset, 0, maxScroll);

        enableScissor(withdrawPanelX, panelsTop, withdrawPanelX + panelWidth, panelsTop + panelHeight);
        int rowY = panelsTop - (int) withdrawScrollOffset;
        for (BankItemSummary item : bankItems) {
            if (rowY + ROW_H > panelsTop && rowY < panelsTop + panelHeight) {
                boolean selected = item.publicId().equals(selectedWithdrawalItem);
                int color = selected ? 0xFF55FF55 : DialoguePresentation.TEXT_COLOR;
                if (selected) {
                    graphics.fill(withdrawPanelX, rowY, withdrawPanelX + panelWidth, rowY + ROW_H, 0x33FFFFFF);
                }
                String label = "Stored item -- " + formatWeight(item.weight()) + " stones";
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
            if (withinPanel(mouseX, mouseY, withdrawPanelX)) {
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

    private void handleDepositPanelClick(double mouseX, double mouseY) {
        List<Integer> slots = depositCandidateSlots();
        double absoluteY = mouseY - panelsTop + depositScrollOffset;
        int index = (int) (absoluteY / ROW_H);
        if (index < 0 || index >= slots.size()) return;
        int slotIndex = slots.get(index);

        long now = System.currentTimeMillis();
        if (lastClickedDepositSlot == slotIndex && now - lastDepositClickTimeMs < DOUBLE_CLICK_MS) {
            selectedDepositSlot = slotIndex;
            statusMessage = null;
            refreshButtonStates();
            lastClickedDepositSlot = -1;
        } else {
            lastClickedDepositSlot = slotIndex;
            lastDepositClickTimeMs = now;
        }
    }

    private void handleWithdrawPanelClick(double mouseX, double mouseY) {
        if (bankItems.isEmpty()) return;
        double absoluteY = mouseY - panelsTop + withdrawScrollOffset;
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

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}
