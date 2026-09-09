package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.dialogue.DialogueLayout;
import com.seggellion.britannia_mod.dialogue.DialogueOptionViewModel;
import com.seggellion.britannia_mod.dialogue.DialogueViewModel;
import com.seggellion.britannia_mod.network.ClientNetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * "Do you wish to destroy this?" — the R-2.11 accident guard.
 *
 * <p>Destruction is irreversible and, for a container, also spills everything inside. An axe swing is
 * cheap to make by accident, so the cost of a mistake and the cost of the gesture are badly matched
 * without this.
 *
 * <p>Built on the project's existing dialogue presentation so it looks like the quest and service
 * dialogues rather than a bolted-on window. The NPC portrait fields are left blank, which
 * {@code DialoguePresentation.renderDialogue} already handles by skipping the portrait — there is no
 * NPC here, only a question.
 *
 * <p>Closing without choosing is the same as answering no: nothing is sent, and the server's session
 * simply expires.
 */
public class GrabbyDestructionScreen extends Screen {
    private static final String OPTION_YES = "yes";
    private static final String OPTION_NO = "no";

    private final UUID sessionId;
    private final String objectName;
    private final int occupiedSlots;

    private DialogueViewModel dialogueView;
    private DialogueLayout dialogueLayout;
    private Component bodyComponent;
    private boolean answered;

    public GrabbyDestructionScreen(UUID sessionId, String objectName, int occupiedSlots) {
        super(Component.translatable("screen.britannia_mod.grabby.destroy.title"));
        this.sessionId = sessionId;
        this.objectName = objectName;
        this.occupiedSlots = occupiedSlots;
    }

    @Override
    protected void init() {
        super.init();
        clearWidgets();

        this.bodyComponent = DialoguePresentation.text(body().getString());
        this.dialogueView = new DialogueViewModel(
                "", "", "",
                Component.translatable("screen.britannia_mod.grabby.destroy.title").getString(),
                bodyComponent.getString(),
                "confirm",
                false,
                List.of(
                        new DialogueOptionViewModel(OPTION_YES,
                                Component.translatable("screen.britannia_mod.grabby.destroy.yes").getString(), false),
                        new DialogueOptionViewModel(OPTION_NO,
                                Component.translatable("screen.britannia_mod.grabby.destroy.no").getString(), false)));

        // No quest giver, so no portrait column: the confirmation gets the whole width for its
        // sentence rather than a column reserved for a picture this screen never draws.
        DialogueLayout initial = DialogueLayout.calculate(
                this.width, 1, dialogueView.options().size(), this.font.lineHeight, false, false);
        int lineCount = this.font.split(bodyComponent, initial.maxTextWidth()).size();
        this.dialogueLayout = DialogueLayout.calculate(
                this.width, lineCount, dialogueView.options().size(), this.font.lineHeight, false, false);

        for (int index = 0; index < dialogueView.options().size(); index++) {
            addRenderableWidget(DialoguePresentation.optionButton(
                    dialogueView.options().get(index), index, dialogueLayout, this::choose));
        }
    }

    private Component body() {
        return occupiedSlots > 0
                ? Component.translatable(
                        "screen.britannia_mod.grabby.destroy.body_with_contents", objectName, occupiedSlots)
                : Component.translatable("screen.britannia_mod.grabby.destroy.body", objectName);
    }

    private void choose(String optionId) {
        if (answered) {
            return;
        }
        answered = true;
        if (OPTION_YES.equals(optionId)) {
            ClientNetworkHandler.sendToServer(
                    new com.seggellion.britannia_mod.network.payload.grabby.C2SConfirmGrabbyDestructionPayload(sessionId));
        }
        // Answering no sends nothing at all. The server's session expires on its own, so a cancel
        // cannot be distinguished from walking away - which is exactly right.
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        DialoguePresentation.renderPaperBackground(graphics, this.width, this.height);
        super.render(graphics, mouseX, mouseY, partialTick);
        DialoguePresentation.renderDialogue(
                graphics, this.font, dialogueView, dialogueLayout, bodyComponent,
                Component.translatable("screen.britannia_mod.grabby.destroy.title").getString());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
