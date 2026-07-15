package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.dialogue.DialogueLayout;
import com.seggellion.britannia_mod.dialogue.DialogueOptionViewModel;
import com.seggellion.britannia_mod.dialogue.DialogueViewModel;
import com.seggellion.britannia_mod.service.DialogueSelectionResult;
import com.seggellion.britannia_mod.service.ServiceDialogueController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/**
 * Presentation-only Service NPC screen. No production entity, packet, command, or HTTP path opens it yet.
 */
public final class ServiceDialogueScreen extends Screen {
    private final ServiceDialogueController controller;
    private DialogueViewModel view;
    private DialogueLayout layout;
    private Component bodyComponent;
    private String statusMessage;

    public ServiceDialogueScreen(ServiceDialogueController controller) {
        super(Component.literal("Service NPC Dialogue"));
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        view = controller.viewModel();
        String body = statusMessage == null ? view.body() : statusMessage;
        bodyComponent = DialoguePresentation.text(body);

        DialogueLayout initialLayout = DialogueLayout.calculate(
                width,
                1,
                view.options().size(),
                font.lineHeight,
                !view.professionLabel().isBlank()
        );
        int textLineCount = font.split(bodyComponent, initialLayout.maxTextWidth()).size();
        layout = DialogueLayout.calculate(
                width,
                textLineCount,
                view.options().size(),
                font.lineHeight,
                !view.professionLabel().isBlank()
        );

        for (int index = 0; index < view.options().size(); index++) {
            DialogueOptionViewModel option = view.options().get(index);
            addRenderableWidget(DialoguePresentation.optionButton(option, index, layout, this::select));
        }
    }

    private void select(String optionId) {
        DialogueSelectionResult result = controller.select(optionId);
        switch (result.outcome()) {
            case CLOSED -> onClose();
            case NAVIGATED -> {
                statusMessage = null;
                init();
            }
            case SERVICE_RESULT -> {
                statusMessage = result.serviceResult().message();
                init();
            }
            case INVALID_OPTION -> {
                // The immutable current-node definition did not contain the selected identifier.
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        DialoguePresentation.renderDialogue(graphics, font, view, layout, bodyComponent, "Service");
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
