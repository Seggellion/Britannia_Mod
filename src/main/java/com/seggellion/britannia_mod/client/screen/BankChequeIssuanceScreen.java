package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.client.screen.bank.BankActionButton;
import com.seggellion.britannia_mod.client.screen.bank.BankBalanceCopy;
import com.seggellion.britannia_mod.client.screen.bank.BankBalanceCopy.Denomination;
import com.seggellion.britannia_mod.client.screen.bank.BankChequeForm;
import com.seggellion.britannia_mod.client.screen.bank.BankDialogueFrame;
import com.seggellion.britannia_mod.client.screen.bank.BankDialogueLayout;
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

import java.util.EnumMap;
import java.util.Map;

/**
 * Milestone 7: the Create Cheque screen, rebuilt on the shared dialogue frame.
 *
 * <p>Portrait and name on the left, prompt and form in the middle, Back and Confirm on the right
 * (design §12.2). Three denomination buttons rather than a dropdown, matching currency
 * withdrawal's established control language (§12.3).
 *
 * <h2>What changed beyond the frame</h2>
 * <ul>
 *   <li><b>Three denominations.</b> The old screen was gold-only, in a box literally labelled
 *       "Amount (gold)". The owner confirmed all three on 2026-08-03 (design §12.3.1); the Rails
 *       half is Milestone 8a and the request is Milestone 8b.</li>
 *   <li><b>Escape closes banking.</b> It used to act as Back, which design §5.2 forbids
 *       explicitly. Back is now the only route to the hub.</li>
 *   <li><b>It reads the session.</b> Balances come from {@link ClientBankingSession} on every
 *       frame instead of from a payload captured in the constructor, which is what lets it
 *       implement {@link BankingScreen} and stay mounted across a refresh -- it could not before,
 *       because staying mounted would have shown numbers that were already stale.</li>
 *   <li><b>Validation is real and immediate.</b> See {@link BankChequeForm}.</li>
 * </ul>
 *
 * <h2>This milestone sends nothing</h2>
 * Confirm is enabled and disabled correctly and does not issue a cheque. Playbook Milestone 7
 * requires no issuance packet, and Milestone 8b cannot send one until 8a has taught Rails to
 * accept a denomination. Wiring it now would emit a field Rails rejects as {@code
 * UNEXPECTED_FIELD}, which is exactly the ordering §1.1 rule 2 exists to prevent.
 */
public final class BankChequeIssuanceScreen extends Screen implements BankingScreen {

    private static final int BUTTON_COUNT = 2;
    private static final int BACK_INDEX = 0;
    private static final int CONFIRM_INDEX = 1;

    /** Amount row, then denomination row. Drives the layout's reserved centre height. */
    private static final int FORM_ROWS = 2;

    private static final int AMOUNT_BOX_WIDTH = 120;
    private static final int DENOMINATION_BUTTON_GAP = 4;

    @Nullable
    private BankDialogueLayout layout;
    @Nullable
    private EditBox amountBox;
    @Nullable
    private BankActionButton confirmButton;

    private final Map<Denomination, Button> denominationButtons = new EnumMap<>(Denomination.class);

    /** Gold preselected: it is the denomination every cheque used before this milestone. */
    private Denomination selected = Denomination.GOLD;

    @Nullable
    private BankStatusPresenter.Status validationStatus;

    public BankChequeIssuanceScreen() {
        super(Component.translatable("screen.britannia_mod.bank.cheque.title"));
    }

    @Override
    protected void init() {
        super.init();

        ClientBankingSession session = ClientBankingSession.active();
        if (session == null) {
            onClose();
            return;
        }

        Component prompt = Component.translatable("screen.britannia_mod.bank.cheque.prompt");
        BankDialogueLayout provisional = BankDialogueLayout.calculate(
                width, height, font.lineHeight, BUTTON_COUNT, 1, FORM_ROWS
        );
        int lines = BankDialogueFrame.bodyLineCount(font, prompt, provisional.bodyMaxWidth());
        layout = BankDialogueLayout.calculate(width, height, font.lineHeight, BUTTON_COUNT, lines, FORM_ROWS);

        buildAmountBox();
        buildDenominationButtons();
        buildActionButtons();
        revalidate(session);
    }

    private void buildAmountBox() {
        String carried = amountBox == null ? "" : amountBox.getValue();
        amountBox = new EditBox(
                font, layout.formX(), layout.formY(), AMOUNT_BOX_WIDTH, BankDialogueLayout.FORM_ROW_HEIGHT,
                Component.translatable("screen.britannia_mod.bank.cheque.amount_label")
        );
        // Long enough for the largest copper cheque (1,000,000,000) and nothing beyond it.
        amountBox.setMaxLength(10);
        amountBox.setValue(carried);
        amountBox.setResponder(ignored -> revalidate(ClientBankingSession.active()));
        addRenderableWidget(amountBox);
        setInitialFocus(amountBox);
    }

    private void buildDenominationButtons() {
        denominationButtons.clear();
        int y = layout.formY() + BankDialogueLayout.FORM_ROW_HEIGHT + BankDialogueLayout.FORM_ROW_GAP;
        int width = (AMOUNT_BOX_WIDTH - (DENOMINATION_BUTTON_GAP * 2)) / 3;

        int index = 0;
        for (Denomination denomination : Denomination.values()) {
            int x = layout.formX() + (index * (width + DENOMINATION_BUTTON_GAP));
            Button button = Button.builder(
                            Component.translatable("screen.britannia_mod.bank.cheque.denomination." + denomination.keySegment()),
                            ignored -> selectDenomination(denomination)
                    )
                    .bounds(x, y, width, BankDialogueLayout.FORM_ROW_HEIGHT)
                    .build();
            denominationButtons.put(denomination, button);
            addRenderableWidget(button);
            index++;
        }
    }

    private void buildActionButtons() {
        Component back = Component.translatable("screen.britannia_mod.bank.action.back");
        addRenderableWidget(BankActionButton.create(
                layout.buttonX(), layout.buttonY(BACK_INDEX), layout.buttonWidth(), layout.buttonHeight(),
                back, back,
                ignored -> returnToMain()
        ));

        confirmButton = BankActionButton.create(
                layout.buttonX(), layout.buttonY(CONFIRM_INDEX), layout.buttonWidth(), layout.buttonHeight(),
                Component.translatable("screen.britannia_mod.bank.cheque.confirm"),
                Component.translatable("screen.britannia_mod.bank.cheque.confirm.pending"),
                // Milestone 8b sends the request. Deliberately inert here: Rails cannot accept a
                // denomination until 8a ships, and emitting one early is the exact failure mode
                // Playbook §1.1 rule 2 exists to prevent.
                ignored -> { }
        );
        addRenderableWidget(confirmButton);
    }

    /**
     * Changing denomination re-reads the balance and re-runs validation against it, which is
     * Playbook Milestone 7's "changing denomination updates contextual balance". The typed amount
     * is kept: a player switching from gold to silver usually means the same number, and clearing
     * it would punish them for exploring.
     */
    private void selectDenomination(Denomination denomination) {
        if (selected == denomination) return;
        selected = denomination;
        revalidate(ClientBankingSession.active());
    }

    private void revalidate(@Nullable ClientBankingSession session) {
        if (session == null || amountBox == null || confirmButton == null) return;

        BankChequeForm.Validation validation = BankChequeForm.validate(
                amountBox.getValue(), selected, BankChequeForm.balanceOf(session, selected)
        );
        validationStatus = validation.error();

        boolean pending = session.isMutationPending();
        confirmButton.setPending(pending);
        confirmButton.active = validation.valid() && !pending;

        for (Map.Entry<Denomination, Button> entry : denominationButtons.entrySet()) {
            // The selected one reads as unavailable-because-current rather than as a live choice,
            // which is how the three buttons express a single selection without a radio widget.
            entry.getValue().active = entry.getKey() != selected && !pending;
        }
        amountBox.setEditable(!pending);
    }

    private void returnToMain() {
        Minecraft.getInstance().setScreen(new BankMainScreen());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        ClientBankingSession session = ClientBankingSession.active();
        if (session == null || layout == null) return;

        // Re-run every frame: a refresh can change the balance under a typed amount that was
        // affordable a moment ago, and the pending lock is released by a packet, not by this
        // screen.
        revalidate(session);

        BankDialogueFrame.renderHeader(
                graphics, font, layout, session.tellerName(), session.tellerGender(),
                Component.translatable("screen.britannia_mod.bank.cheque.prompt")
        );

        renderSelectedBalance(graphics, session);

        // A live validation problem is the more useful thing to show while typing; a server
        // outcome takes the slot once there is nothing to fix.
        BankStatusPresenter.Status status = validationStatus != null
                ? validationStatus
                : BankStatusPresenter.forResult(session.lastResult());
        BankDialogueFrame.renderStatus(graphics, font, layout, status);
    }

    /**
     * The contextual balance for the selected denomination, plus that denomination's real minimum.
     * The minimum is shown because the bounds are value-denominated: "500 gold" and "5,000,000
     * copper" are the same floor, and a player has no way to infer the second from the first.
     */
    private void renderSelectedBalance(GuiGraphics graphics, ClientBankingSession session) {
        int balance = BankChequeForm.balanceOf(session, selected);
        Component line = Component.translatable(
                "screen.britannia_mod.bank.cheque.context",
                Component.translatable(
                        BankBalanceCopy.amountKey(selected, balance), BankBalanceCopy.formatAmount(balance)),
                BankBalanceCopy.formatAmount(BankChequeForm.minimumIn(selected))
        );
        int y = layout.formY() + ((BankDialogueLayout.FORM_ROW_HEIGHT + BankDialogueLayout.FORM_ROW_GAP) * FORM_ROWS);
        graphics.drawWordWrap(
                font, com.seggellion.britannia_mod.client.screen.DialoguePresentation.text(line),
                layout.formX(), y, layout.formWidth(),
                com.seggellion.britannia_mod.client.screen.DialoguePresentation.TEXT_COLOR
        );
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        BankDialogueFrame.renderBackground(graphics, width, height);
    }

    /**
     * Design §5.2: Escape closes banking. It used to call Cancel and return to the previous
     * screen, which made Escape a second Back -- the behaviour that section forbids by name.
     */
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
