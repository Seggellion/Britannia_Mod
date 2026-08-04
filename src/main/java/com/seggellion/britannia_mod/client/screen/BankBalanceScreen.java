package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.client.screen.bank.BankActionButton;
import com.seggellion.britannia_mod.client.screen.bank.BankBalanceCopy;
import com.seggellion.britannia_mod.client.screen.bank.BankDialogueFrame;
import com.seggellion.britannia_mod.client.screen.bank.BankDialogueLayout;
import com.seggellion.britannia_mod.client.screen.bank.BankStatusPresenter;
import com.seggellion.britannia_mod.client.screen.bank.BankingScreen;
import com.seggellion.britannia_mod.client.screen.bank.ClientBankingSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/**
 * Milestone 5: the read-only Balance Screen.
 *
 * <p>Portrait and name on the left, the account's three balances spoken by the teller in the
 * middle, Back and Deposit All Coins on the right (design §11.2).
 *
 * <p>It reads {@link ClientBankingSession} on every frame and holds nothing of its own, so a
 * refresh push that lands while it is open changes the numbers in place. That is what Playbook
 * Milestone 5's "text updates when refreshed session state changes" asks for, and it needs no code
 * here beyond not caching anything -- Milestone 4's cutover already stopped refreshes from
 * rebuilding the screen.
 *
 * <p><b>Deposit All Coins is present but inert</b>, which Playbook Milestone 5 explicitly permits
 * until Milestone 6 builds the transaction behind it. It renders disabled and the status line says
 * why, because a greyed button on its own tells a player "not now" without telling them whether
 * that is permanent.
 */
public final class BankBalanceScreen extends Screen implements BankingScreen {

    private static final int BUTTON_COUNT = 2;
    private static final int BACK_INDEX = 0;
    private static final int DEPOSIT_ALL_INDEX = 1;

    @Nullable
    private BankDialogueLayout layout;
    private Component body = Component.empty();

    public BankBalanceScreen() {
        super(Component.translatable("screen.britannia_mod.bank.balance.title"));
    }

    @Override
    protected void init() {
        super.init();

        ClientBankingSession session = ClientBankingSession.active();
        if (session == null) {
            onClose();
            return;
        }

        body = balanceBody(session);
        layout = layoutFor(body);

        Component back = Component.translatable("screen.britannia_mod.bank.action.back");
        addRenderableWidget(BankActionButton.create(
                layout.buttonX(), layout.buttonY(BACK_INDEX), layout.buttonWidth(), layout.buttonHeight(),
                back, back,
                ignored -> returnToMain()
        ));

        Component depositAll = Component.translatable("screen.britannia_mod.bank.action.deposit_all_coins");
        BankActionButton depositAllButton = BankActionButton.create(
                layout.buttonX(), layout.buttonY(DEPOSIT_ALL_INDEX), layout.buttonWidth(), layout.buttonHeight(),
                depositAll, Component.translatable("screen.britannia_mod.bank.action.deposit_all_coins.pending"),
                // Milestone 6 replaces this with the real request. Left as a no-op rather than
                // omitted so the layout, focus order and wording are all owner-reviewable now.
                ignored -> { }
        );
        depositAllButton.active = false;
        addRenderableWidget(depositAllButton);
    }

    /** Rebuilt on every render so the numbers follow the session rather than the constructor. */
    private static Component balanceBody(ClientBankingSession session) {
        int gold = session.goldBalance();
        int silver = session.silverBalance();
        int copper = session.copperBalance();

        String bodyKey = BankBalanceCopy.bodyKey(gold, silver, copper);
        if (bodyKey.endsWith("body_empty")) {
            return Component.translatable(bodyKey);
        }
        return Component.translatable(
                bodyKey,
                amount(BankBalanceCopy.Denomination.GOLD, gold),
                amount(BankBalanceCopy.Denomination.SILVER, silver),
                amount(BankBalanceCopy.Denomination.COPPER, copper)
        );
    }

    private static Component amount(BankBalanceCopy.Denomination denomination, int value) {
        return Component.translatable(
                BankBalanceCopy.amountKey(denomination, value),
                BankBalanceCopy.formatAmount(value)
        );
    }

    private BankDialogueLayout layoutFor(Component text) {
        BankDialogueLayout provisional = BankDialogueLayout.calculate(
                width, height, font.lineHeight, BUTTON_COUNT, 1, 0
        );
        int lines = BankDialogueFrame.bodyLineCount(font, text, provisional.bodyMaxWidth());
        return BankDialogueLayout.calculate(width, height, font.lineHeight, BUTTON_COUNT, lines, 0);
    }

    private void returnToMain() {
        Minecraft.getInstance().setScreen(new BankMainScreen());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        ClientBankingSession session = ClientBankingSession.active();
        if (session == null || layout == null) return;

        // A refresh can change the balances -- and with them the wrapped height of the sentence --
        // between init and now. Recompute when the text actually changes rather than every frame,
        // so font.split is not run sixty times a second to get the same answer.
        //
        // Only the body's vertical centring depends on the line count; button positions come from
        // the screen width and the button count alone, so they stay where init put them.
        Component current = balanceBody(session);
        if (!current.equals(body)) {
            body = current;
            layout = layoutFor(current);
        }

        BankDialogueFrame.renderHeader(
                graphics, font, layout, session.tellerName(), session.tellerGender(), body
        );

        BankStatusPresenter.Status status = BankStatusPresenter.forResult(session.lastResult());
        // A real outcome always wins; the Deposit All Coins notice fills the gap when there is none.
        BankDialogueFrame.renderStatus(
                graphics, font, layout, status != null ? status : BankBalanceCopy.DEPOSIT_ALL_UNAVAILABLE
        );
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        BankDialogueFrame.renderBackground(graphics, width, height);
    }

    /** Design §5.2: Escape closes banking outright. Back is the only route to the hub. */
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
