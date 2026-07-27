package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.economy.CoinConversion;
import com.seggellion.britannia_mod.network.ClientNetworkHandler;
import com.seggellion.britannia_mod.network.payload.BankAccountOpenedS2CPayload;
import com.seggellion.britannia_mod.network.payload.BankChequeIssuanceRequestC2SPayload;
import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload;
import com.seggellion.britannia_mod.service.banking.BankingChequeIssuanceProxyService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/**
 * Milestone 11 NeoForge Slice 1: the teller check-creation screen -- Codex Prompt 11's own
 * "current gold balance, approved min/max, numeric validation, confirm, and cancel." Opened
 * from {@link BankScreen}'s own "Checks" button (real as of this slice; previously disabled).
 *
 * <p>The player enters an amount in <b>gold</b> (500-100,000, the ADR-018/019-approved range as
 * humans actually reason about it), but the wire request -- {@link
 * BankChequeIssuanceRequestC2SPayload#amount()} -- carries <b>copper</b>, the unit Rails'
 * {@code ChequePayloadValidator}/{@code BankCheque} actually validate and store against. The
 * conversion happens once, right before sending, via {@link CoinConversion#COPPER_PER_GOLD}
 * (already committed, {@code a8f603d}) -- nothing here ever sends a gold-denominated amount over
 * the wire.
 */
public final class BankChequeIssuanceScreen extends Screen {
    private static final int CONTENT_MARGIN = 20;
    private static final int LINE_GAP = 4;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_GAP = 10;
    private static final int BOX_WIDTH = 160;
    private static final int BOX_HEIGHT = 20;

    public static final int MIN_GOLD = BankingChequeIssuanceProxyService.MIN_AMOUNT_COPPER / CoinConversion.COPPER_PER_GOLD;
    public static final int MAX_GOLD = BankingChequeIssuanceProxyService.MAX_AMOUNT_COPPER / CoinConversion.COPPER_PER_GOLD;

    private final BankAccountOpenedS2CPayload account;

    private EditBox amountBox;
    private Button confirmButton;
    private Button cancelButton;

    private boolean issuancePending;
    private Component statusMessage;

    public BankChequeIssuanceScreen(BankAccountOpenedS2CPayload account) {
        super(Component.literal("Create Bank Cheque"));
        this.account = Objects.requireNonNull(account, "account");
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();

        int centerX = width / 2;
        int boxY = height / 2 - 10;

        amountBox = new EditBox(font, centerX - BOX_WIDTH / 2, boxY, BOX_WIDTH, BOX_HEIGHT, Component.literal("Amount (gold)"));
        amountBox.setMaxLength(7);
        amountBox.setValue("");
        amountBox.setResponder(ignored -> refreshButtonStates());
        addRenderableWidget(amountBox);

        int buttonY = boxY + BOX_HEIGHT + BUTTON_GAP;
        confirmButton = Button.builder(Component.literal("Confirm"), ignored -> onConfirmPressed())
                .bounds(centerX - BUTTON_WIDTH - BUTTON_GAP / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        cancelButton = Button.builder(Component.literal("Cancel"), ignored -> onCancelPressed())
                .bounds(centerX + BUTTON_GAP / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
        addRenderableWidget(confirmButton);
        addRenderableWidget(cancelButton);

        refreshButtonStates();
    }

    private void refreshButtonStates() {
        int amount = parseGoldAmount(amountBox.getValue());
        boolean validAmount = amount >= MIN_GOLD && amount <= MAX_GOLD;
        confirmButton.active = !issuancePending && validAmount;
        confirmButton.setMessage(Component.literal(issuancePending ? "Issuing..." : "Confirm"));
        cancelButton.active = !issuancePending;
    }

    /** {@code 0} for anything not a genuine positive integer -- never throws on malformed input. */
    private static int parseGoldAmount(String value) {
        try {
            int amount = Integer.parseInt(value.trim());
            return Math.max(amount, 0);
        } catch (NumberFormatException malformed) {
            return 0;
        }
    }

    private void onConfirmPressed() {
        if (issuancePending) return;
        int gold = parseGoldAmount(amountBox.getValue());
        if (gold < MIN_GOLD || gold > MAX_GOLD) return;
        if (gold > account.goldBalance()) {
            statusMessage = INSUFFICIENT_BALANCE_MESSAGE;
            return;
        }

        long amountCopper = (long) gold * CoinConversion.COPPER_PER_GOLD;
        if (amountCopper > Integer.MAX_VALUE) return; // structurally unreachable given MAX_GOLD, defensive only

        issuancePending = true;
        statusMessage = null;
        refreshButtonStates();
        ClientNetworkHandler.sendToServer(new BankChequeIssuanceRequestC2SPayload(account.entityId(), (int) amountCopper));
    }

    private void onCancelPressed() {
        if (issuancePending) return;
        Minecraft.getInstance().setScreen(new BankScreen(account));
    }

    /**
     * Invoked by {@code ClientNetworkHandler} on a clean-rejection, pending-delivery, or
     * reconciliation-required result for this screen's own in-flight issuance. A clean {@code
     * CONFIRMED} never reaches here -- it refreshes straight to a new {@link BankScreen} via a
     * fresh {@code BankAccountOpenedS2CPayload} instead, mirroring every other transfer flow.
     */
    public void acceptTransferResult(BankTransferResultS2CPayload payload) {
        if (payload.operation() != BankTransferResultS2CPayload.Operation.CHEQUE_ISSUANCE) return;
        issuancePending = false;
        statusMessage = switch (payload.kind()) {
            case CLEAN_REJECTION -> CLEAN_REJECTION_MESSAGE;
            case RECONCILIATION_REQUIRED -> RECONCILIATION_REQUIRED_MESSAGE;
            case PENDING_DELIVERY -> PENDING_DELIVERY_MESSAGE;
        };
        refreshButtonStates();
    }

    private static final Component CLEAN_REJECTION_MESSAGE = Component.literal(
            "The teller checks the ledger and shakes their head: \"I'm afraid I can't complete that right now.\""
    );
    private static final Component RECONCILIATION_REQUIRED_MESSAGE = Component.literal(
            "Something has gone wrong with this transaction that requires staff attention. "
                    + "Please contact a server admin -- do not attempt this again until it is resolved."
    );
    private static final Component PENDING_DELIVERY_MESSAGE = Component.literal(
            "Your cheque was issued, but could not be delivered just now. It will be delivered automatically."
    );
    private static final Component INSUFFICIENT_BALANCE_MESSAGE = Component.literal(
            "You do not have enough gold for a cheque of that amount."
    );

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = width / 2;
        int y = height / 2 - 60;

        graphics.drawCenteredString(font, "Create Bank Cheque", centerX, y, DialoguePresentation.TEXT_COLOR);
        y += font.lineHeight + LINE_GAP * 2;

        graphics.drawCenteredString(font, "Current balance: " + account.goldBalance() + " gold", centerX, y, DialoguePresentation.TEXT_COLOR);
        y += font.lineHeight + LINE_GAP;

        graphics.drawCenteredString(font, "Approved range: " + MIN_GOLD + " - " + MAX_GOLD + " gold", centerX, y, DialoguePresentation.TEXT_COLOR);

        if (statusMessage != null) {
            int statusY = height / 2 + 40;
            graphics.drawCenteredString(font, statusMessage, centerX, statusY, 0xFFAA4444);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        DialoguePresentation.renderPaperBackground(graphics, width, height);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onCancelPressed();
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
