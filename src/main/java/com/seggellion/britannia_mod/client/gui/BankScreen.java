package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.dialogue.DialogueLayout;
import com.seggellion.britannia_mod.dialogue.DialogueViewModel;
import com.seggellion.britannia_mod.network.payload.BankAccountOpenedS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Milestone 7 Slice B: the real, read-only Bank Screen, replacing Slice A's chat-message
 * placeholder for a genuine {@code OPENED} result. A plain client {@link Screen} opened
 * one-shot from an already-fetched, static {@link BankAccountOpenedS2CPayload} snapshot —
 * deliberately mirroring {@code QuestDecisionScreen}'s own real production mechanism
 * (traced directly: it is not an {@link net.minecraft.world.inventory.AbstractContainerMenu},
 * has no ongoing server-side validity check, and is opened the same way, from data already
 * in hand at construction time). See the Slice B completion report for why that is an
 * acceptable simplification here too: this screen is read-only (no deposit/withdrawal/check
 * logic exists yet), so there is no mutable, exploitable state for a continuous
 * {@code stillValid}-style check to protect. Session validity is instead enforced once, at
 * the moment the server decides whether to send this payload at all (the existing
 * disconnect/discard/out-of-range revalidation in {@code BankingProxyService.handle}'s
 * completion callback) — never after the screen is already open.
 *
 * <p>Reuses {@link DialogueLayout}/{@link DialogueViewModel}/{@link DialoguePresentation}
 * (this codebase's existing presentation-only content-rendering layer for
 * {@code ServiceDialogueScreen}) for the teller-name/portrait header; the account ledger
 * content below it is this screen's own, since none of those classes have any concept of
 * currency balances, weight, or an item vault.
 */
public final class BankScreen extends Screen {
    private static final int CONTENT_MARGIN = 20;
    private static final int LINE_GAP = 2;
    private static final int VAULT_HEIGHT = 60;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;

    private final BankAccountOpenedS2CPayload account;
    private final DialogueViewModel headerView;
    private DialogueLayout headerLayout;
    private Component headerBody;

    public BankScreen(BankAccountOpenedS2CPayload account) {
        super(Component.literal("Bank Account"));
        this.account = Objects.requireNonNull(account, "account");
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
        String[] labels = {"Deposit (not yet available)", "Withdraw (not yet available)", "Checks (not yet available)"};
        for (int index = 0; index < labels.length; index++) {
            Button button = Button.builder(Component.literal(labels[index]), ignored -> {})
                    .bounds(
                            CONTENT_MARGIN + index * (buttonWidth + BUTTON_GAP),
                            buttonY,
                            buttonWidth,
                            BUTTON_HEIGHT
                    )
                    .build();
            button.active = false;
            addRenderableWidget(button);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        DialoguePresentation.renderDialogue(graphics, font, headerView, headerLayout, headerBody, "Bank");
        renderLedger(graphics);
    }

    private void renderLedger(GuiGraphics graphics) {
        int x = CONTENT_MARGIN;
        int y = DialogueLayout.TOP_SECTION_HEIGHT + CONTENT_MARGIN;

        if (account.cityDisplayName() != null) {
            graphics.drawString(
                    font,
                    DialoguePresentation.text("City: " + account.cityDisplayName()),
                    x, y, DialoguePresentation.TEXT_COLOR, false
            );
            y += font.lineHeight + LINE_GAP;
        }

        String weightLine = formatWeight(account.currentWeight()) + " / " + account.weightLimit() + " stones";
        graphics.drawString(font, DialoguePresentation.text(weightLine), x, y, DialoguePresentation.TEXT_COLOR, false);
        y += font.lineHeight + LINE_GAP;

        String balanceLine = account.goldBalance() + "g " + account.silverBalance() + "s " + account.copperBalance() + "c";
        graphics.drawString(font, DialoguePresentation.text(balanceLine), x, y, DialoguePresentation.TEXT_COLOR, false);
        y += font.lineHeight + LINE_GAP + 6;

        int vaultWidth = width - (CONTENT_MARGIN * 2);
        graphics.fill(x, y, x + vaultWidth, y + VAULT_HEIGHT, 0x33000000);
        Component vaultLabel = DialoguePresentation.text("The vault is empty.");
        int labelX = x + (vaultWidth / 2) - (font.width(vaultLabel) / 2);
        int labelY = y + (VAULT_HEIGHT / 2) - (font.lineHeight / 2);
        graphics.drawString(font, vaultLabel, labelX, labelY, DialoguePresentation.TEXT_COLOR, false);
    }

    private static String formatWeight(double currentWeight) {
        if (currentWeight == Math.floor(currentWeight) && !Double.isInfinite(currentWeight)) {
            return String.valueOf((long) currentWeight);
        }
        return String.format(Locale.ROOT, "%.1f", currentWeight);
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
