package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.client.screen.bank.BankActionButton;
import com.seggellion.britannia_mod.client.screen.bank.BankDialogueFrame;
import com.seggellion.britannia_mod.client.screen.bank.BankDialogueLayout;
import com.seggellion.britannia_mod.client.screen.bank.BankingScreen;
import com.seggellion.britannia_mod.client.screen.bank.ClientBankingSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

import java.util.Objects;

/**
 * Milestone 4: a clearly-marked stand-in for a destination whose own milestone has not landed.
 *
 * <p>Playbook Milestone 4 permits temporary placeholders "only when clearly marked and only until
 * their milestones are implemented", which is what this is. It says plainly that the feature is
 * not built yet rather than pretending to be an empty version of it -- a blank Bank Box would read
 * as an emptied vault, which is precisely the misreading the item-identity program was written to
 * fix.
 *
 * <p><b>Delete both factory methods as their milestones land:</b> {@link #balance()} at Milestone
 * 5, {@link #bankBox()} at Milestone 9. When both are gone, delete this class.
 *
 * <p>It deposits nothing, withdraws nothing and sends no packet. Back returns to the hub; Escape
 * closes banking.
 */
public final class BankPlaceholderScreen extends Screen implements BankingScreen {

    private static final int BUTTON_COUNT = 1;

    private final Component body;

    @Nullable
    private BankDialogueLayout layout;

    private BankPlaceholderScreen(Component title, Component body) {
        super(Objects.requireNonNull(title, "title"));
        this.body = Objects.requireNonNull(body, "body");
    }

    /** Remove at Milestone 9, when the real Bank Box screen exists. */
    public static BankPlaceholderScreen bankBox() {
        return new BankPlaceholderScreen(
                Component.translatable("screen.britannia_mod.bank.placeholder.bank_box.title"),
                Component.translatable("screen.britannia_mod.bank.placeholder.bank_box.body")
        );
    }

    /** Remove at Milestone 5, when the real Balance screen exists. */
    public static BankPlaceholderScreen balance() {
        return new BankPlaceholderScreen(
                Component.translatable("screen.britannia_mod.bank.placeholder.balance.title"),
                Component.translatable("screen.britannia_mod.bank.placeholder.balance.body")
        );
    }

    @Override
    protected void init() {
        super.init();

        ClientBankingSession session = ClientBankingSession.active();
        if (session == null) {
            onClose();
            return;
        }

        BankDialogueLayout provisional = BankDialogueLayout.calculate(
                width, height, font.lineHeight, BUTTON_COUNT, 1, 0
        );
        int lines = BankDialogueFrame.bodyLineCount(font, body, provisional.bodyMaxWidth());
        layout = BankDialogueLayout.calculate(width, height, font.lineHeight, BUTTON_COUNT, lines, 0);

        Component back = Component.translatable("screen.britannia_mod.bank.action.back");
        addRenderableWidget(BankActionButton.create(
                layout.buttonX(), layout.buttonY(0), layout.buttonWidth(), layout.buttonHeight(),
                back, back,
                ignored -> returnToMain()
        ));
    }

    private void returnToMain() {
        Minecraft.getInstance().setScreen(new BankMainScreen());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        ClientBankingSession session = ClientBankingSession.active();
        if (session == null || layout == null) return;

        BankDialogueFrame.renderHeader(
                graphics, font, layout, session.tellerName(), session.tellerGender(), body
        );
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        BankDialogueFrame.renderBackground(graphics, width, height);
    }

    /** Design §5.2: Escape closes banking, it is not a second Back. */
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
