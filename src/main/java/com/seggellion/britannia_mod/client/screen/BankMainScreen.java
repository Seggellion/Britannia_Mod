package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.client.screen.bank.BankActionButton;
import com.seggellion.britannia_mod.client.screen.bank.BankDialogueFrame;
import com.seggellion.britannia_mod.client.screen.bank.BankDialogueLayout;
import com.seggellion.britannia_mod.client.screen.bank.BankNavigation;
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
 * Milestone 4: the banking hub, and the screen a banker interaction now opens.
 *
 * <p>Portrait and name on the left, a short greeting in the middle, three destinations on the
 * right. Deliberately nothing else -- no balances, no weight ledger, no item lists, no amount
 * fields. The legacy {@code BankScreen} put all of that on one surface, which is the crowding this
 * epic exists to undo (design §1, §8.3).
 *
 * <p>It owns no account state. Every value it renders is read from {@link ClientBankingSession}
 * during {@code render}, so a refresh push that lands while this screen is open simply changes
 * what the next frame draws. That is the whole point of Milestone 2: this screen never has to be
 * rebuilt to show fresh data, and never has to be told that data changed.
 */
public final class BankMainScreen extends Screen implements BankingScreen {

    private static final int BUTTON_COUNT = 3;

    @Nullable
    private BankDialogueLayout layout;
    private Component greeting = Component.empty();

    public BankMainScreen() {
        super(Component.translatable("screen.britannia_mod.bank.main.title"));
    }

    @Override
    protected void init() {
        super.init();

        ClientBankingSession session = ClientBankingSession.active();
        if (session == null) {
            // Nothing to render against. Reachable only if the session was closed between this
            // screen being constructed and initialised; closing is safer than drawing blanks.
            onClose();
            return;
        }

        greeting = greetingFor(session);
        layout = layoutFor(greeting);

        addRenderableWidget(destinationButton(session, BankNavigation.Destination.BANK_BOX, 0));
        addRenderableWidget(destinationButton(session, BankNavigation.Destination.BALANCE, 1));
        addRenderableWidget(destinationButton(session, BankNavigation.Destination.CREATE_CHEQUE, 2));
    }

    private static Component greetingFor(ClientBankingSession session) {
        String city = session.cityDisplayName();
        String key = BankNavigation.greetingKey(city);
        return city == null || city.isBlank()
                ? Component.translatable(key, session.tellerName())
                : Component.translatable(key, session.tellerName(), city);
    }

    /**
     * Two passes, because the wrap width depends on the layout and the layout centres the text
     * against its own wrapped height. The provisional pass asks for one line purely to obtain a
     * usable {@code bodyMaxWidth}.
     */
    private BankDialogueLayout layoutFor(Component body) {
        BankDialogueLayout provisional = BankDialogueLayout.calculate(
                width, height, font.lineHeight, BUTTON_COUNT, 1, 0
        );
        int lines = BankDialogueFrame.bodyLineCount(font, body, provisional.bodyMaxWidth());
        return BankDialogueLayout.calculate(width, height, font.lineHeight, BUTTON_COUNT, lines, 0);
    }

    private BankActionButton destinationButton(
            ClientBankingSession session, BankNavigation.Destination destination, int index
    ) {
        Component label = Component.translatable(destination.labelKey());
        BankActionButton button = BankActionButton.create(
                layout.buttonX(), layout.buttonY(index), layout.buttonWidth(), layout.buttonHeight(),
                // Navigation never enters a pending state -- it sends nothing. The idle label is
                // passed twice deliberately rather than inventing a "navigating..." state.
                label, label,
                ignored -> open(destination)
        );
        button.active = BankNavigation.canOpen(destination, session);
        return button;
    }

    private void open(BankNavigation.Destination destination) {
        ClientBankingSession session = ClientBankingSession.active();
        if (session == null || !BankNavigation.canOpen(destination, session)) return;

        Minecraft minecraft = Minecraft.getInstance();
        switch (destination) {
            case BANK_BOX -> minecraft.setScreen(BankPlaceholderScreen.bankBox());
            case BALANCE -> minecraft.setScreen(new BankBalanceScreen());
            // The real screen, not a placeholder: cheque issuance already works end to end, and
            // routing it at a placeholder would take a working feature away from players for the
            // three milestones until Milestone 7 rebuilds its presentation.
            case CREATE_CHEQUE -> minecraft.setScreen(new BankChequeIssuanceScreen(session.snapshot()));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        ClientBankingSession session = ClientBankingSession.active();
        if (session == null || layout == null) return;

        BankDialogueFrame.renderHeader(
                graphics, font, layout, session.tellerName(), session.tellerGender(), greeting
        );
        BankDialogueFrame.renderStatus(
                graphics, font, layout, BankStatusPresenter.forResult(session.lastResult())
        );
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        BankDialogueFrame.renderBackground(graphics, width, height);
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

    /** Escape from the hub closes banking outright -- there is nowhere further back to go. */
    @Override
    public void onClose() {
        ClientBankingSession.close();
        Minecraft.getInstance().setScreen(null);
    }
}
