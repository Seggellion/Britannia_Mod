package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.client.screen.bank.BankActionButton;
import com.seggellion.britannia_mod.client.screen.bank.BankBalanceCopy;
import com.seggellion.britannia_mod.client.screen.bank.BankDialogueFrame;
import com.seggellion.britannia_mod.client.screen.bank.BankNavigation;
import com.seggellion.britannia_mod.client.screen.bank.BankDialogueLayout;
import com.seggellion.britannia_mod.client.screen.bank.BankStatusPresenter;
import com.seggellion.britannia_mod.client.screen.bank.BankingScreen;
import com.seggellion.britannia_mod.client.screen.bank.ClientBankingSession;
import com.seggellion.britannia_mod.network.ClientNetworkHandler;
import com.seggellion.britannia_mod.network.payload.BankDepositAllCoinsRequestC2SPayload;
import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload;
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
    @Nullable
    private BankActionButton depositAllButton;
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

        depositAllButton = BankActionButton.create(
                layout.buttonX(), layout.buttonY(DEPOSIT_ALL_INDEX), layout.buttonWidth(), layout.buttonHeight(),
                Component.translatable("screen.britannia_mod.bank.action.deposit_all_coins"),
                Component.translatable("screen.britannia_mod.bank.action.deposit_all_coins.pending"),
                ignored -> depositAllCoins()
        );
        addRenderableWidget(depositAllButton);
        refreshButtonStates(session);
    }

    /**
     * Milestone 6b: one press, one request, and nothing computed locally.
     *
     * <p>The packet carries the teller's entity id and no amounts -- the server sweeps the live
     * inventory itself. Nothing is removed from the player's inventory here, and no balance is
     * adjusted: this screen learns what happened only from the refresh push or the result.
     *
     * <p>The pending lock is claimed <b>before</b> the packet goes out, and a failed claim sends
     * nothing. That is what makes a double-click one deposit rather than two, and it is a
     * session-level lock rather than a screen-level flag so navigating away mid-request cannot
     * shake it off (design §10.9).
     */
    private void depositAllCoins() {
        ClientBankingSession session = ClientBankingSession.active();
        if (session == null) return;
        // Coins, by construction -- the whole point of the sweep.
        if (!session.beginPending(BankTransferResultS2CPayload.Operation.DEPOSIT, true)) return;

        ClientNetworkHandler.sendToServer(new BankDepositAllCoinsRequestC2SPayload(session.tellerEntityId()));
        refreshButtonStates(session);
    }

    /**
     * Mirrors the session's lock onto the button. Called from {@code render} as well as {@code
     * init}, because the lock is released by a packet arriving rather than by anything this
     * screen does -- a result or a refresh can land on any tick.
     */
    private void refreshButtonStates(ClientBankingSession session) {
        if (depositAllButton == null) return;
        boolean pending = session.isMutationPending();
        depositAllButton.setPending(pending);
        depositAllButton.active = !pending;
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
        BankNavigation.beginNavigation(ClientBankingSession.active());
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

        refreshButtonStates(session);
        BankDialogueFrame.renderStatus(graphics, font, layout, BankStatusPresenter.statusFor(session));
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
