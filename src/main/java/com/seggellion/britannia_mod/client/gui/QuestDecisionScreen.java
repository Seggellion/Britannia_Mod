package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.quest.network.QuestClient;
import com.seggellion.britannia_mod.quest.ClientQuestEntry;
import com.seggellion.britannia_mod.quest.ClientQuestTable;
import com.seggellion.britannia_mod.quest.QuestManager;
import com.seggellion.britannia_mod.dialogue.DialogueLayout;
import com.seggellion.britannia_mod.dialogue.DialogueOptionViewModel;
import com.seggellion.britannia_mod.dialogue.DialogueViewModel;
import com.seggellion.britannia_mod.dialogue.QuestDialogueAdapter;
import com.seggellion.britannia_mod.quest.network.QuestModels.QuestResponse;
import com.seggellion.britannia_mod.quest.network.QuestModels.QuestChoice;
import com.mojang.logging.LogUtils;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.UUID;

public class QuestDecisionScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final String npcName;
    private final String npcGender;
    private final QuestResponse questState;
    private final DialogueViewModel dialogueView;

    private boolean choiceMade = false;

    private Component bodyComponent;
    private DialogueLayout dialogueLayout;
    private final UUID npcUuid;

    // 1. OLD Constructor (Used by QuestEventHandlers for Environmental Popups)
public QuestDecisionScreen(QuestResponse questState, String npcName, String npcGender) {
        this(questState, npcName, npcGender, null); 
    }

    // 2. NEW Constructor (Portrait argument removed!)
public QuestDecisionScreen(QuestResponse questState, String npcName, String npcGender, java.util.UUID npcUuid) {
        super(Component.literal("Quest Interface"));
        this.questState = questState;
        this.npcName = npcName;
        this.npcGender = npcGender; // Store it
        this.npcUuid = npcUuid;
        this.dialogueView = QuestDialogueAdapter.from(questState, npcName, npcGender);
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        this.bodyComponent = DialoguePresentation.text(dialogueView.body());
        boolean hasChoices = !dialogueView.completed() && !dialogueView.options().isEmpty();
        DialogueLayout initialLayout = DialogueLayout.calculate(
                this.width,
                1,
                hasChoices ? dialogueView.options().size() : 0,
                this.font.lineHeight,
                false
        );
        int textLineCount = this.font.split(this.bodyComponent, initialLayout.maxTextWidth()).size();
        this.dialogueLayout = DialogueLayout.calculate(
                this.width,
                textLineCount,
                hasChoices ? dialogueView.options().size() : 0,
                this.font.lineHeight,
                false
        );

        if (hasChoices) {
            boolean isInfoNode = "info".equals(dialogueView.nodeType());

            if (isInfoNode && questState.choices.size() == 1) {
                QuestChoice choice = questState.choices.get(0);
                DialogueOptionViewModel optionView = dialogueView.options().get(0);
                Component btnText = DialoguePresentation.text("Next ->");
                
                Button nextBtn = Button.builder(btnText, btn -> handleChoice(choice))
                    .bounds(dialogueLayout.buttonStartX(), dialogueLayout.buttonStartY(), dialogueLayout.buttonWidth(), 20)
                    .build();
                
                nextBtn.active = !optionView.locked();
                this.addRenderableWidget(nextBtn);
                
            } else {
                for (int i = 0; i < questState.choices.size(); i++) {
                    QuestChoice choice = questState.choices.get(i);
                    DialogueOptionViewModel optionView = dialogueView.options().get(i);
                    Button choiceBtn = DialoguePresentation.optionButton(optionView, i, dialogueLayout, ignored -> {
                        // NEW: Intercept choices with no destination and just close the UI!
                        if (choice.id == null || choice.id.trim().isEmpty() || "close".equalsIgnoreCase(choice.id)) {
                            this.choiceMade = true;
                            clearPendingOfferStateIfUnaccepted();
                            this.onClose();
                        } else if (isRejectChoice(choice) && !hasAcceptedQuestState(questState)) {
                            this.choiceMade = true;
                            clearPendingOfferStateIfUnaccepted();
                            this.onClose();
                        } else {
                            handleChoice(choice);
                        }
                    });
                    this.addRenderableWidget(choiceBtn);
                }
            }
        } else {
            Component farewellText = DialoguePresentation.text("Farewell.");
            this.addRenderableWidget(Button.builder(farewellText, btn -> this.onClose())
                .bounds(dialogueLayout.buttonStartX(), dialogueLayout.buttonStartY(), dialogueLayout.buttonWidth(), 20)
                .build());
        }
    }

    private void handleChoice(QuestChoice choice) {
        this.choiceMade = true;
        QuestClient.sendTransition(questState.quest_id, choice.id, questGiverContext(), newResponse -> {
            if (newResponse != null && newResponse.error == null) {
                String questStateId = resolveQuestStateId(newResponse);
                if (newResponse.granted_items != null && !newResponse.granted_items.isEmpty()) {
                    net.minecraft.client.multiplayer.ClientPacketListener connection = Minecraft.getInstance().getConnection();
                    if (connection != null && !questStateId.isBlank()) {
                        connection.send(com.seggellion.britannia_mod.network.payload.ClaimQuestRewardC2SPayload.fromResponse(newResponse));
                    } else if (questStateId.isBlank()) {
                        LOGGER.warn("Ignoring quest reward claim before Rails accepted quest_state_id quest_id={} choice_id={}",
                                newResponse.quest_id, choice.id);
                    }
                }

                if (newResponse.client_actions != null) {
                    for (var action : newResponse.client_actions) {
                        if ("spawn_escort".equals(action.action)) {
                            net.minecraft.client.multiplayer.ClientPacketListener connection = Minecraft.getInstance().getConnection();
                            if (connection != null) {
                                java.util.UUID safeUuid = (this.npcUuid != null) ? this.npcUuid : new java.util.UUID(0, 0);

                                String safeName = this.npcName != null ? this.npcName : "Unknown";
                                String safeGender = this.npcGender != null ? this.npcGender : "unknown";
                                if (questStateId.isBlank()) {
                                    LOGGER.warn("Ignoring escort spawn before Rails accepted quest_state_id quest_id={} npc_uuid={} choice_id={}",
                                            newResponse.quest_id, safeUuid, choice.id);
                                    continue;
                                }

                                connection.send(new com.seggellion.britannia_mod.network.payload.SpawnEscortC2SPayload(
                                    action.entity_type, 
                                    newResponse.quest_id, 
                                    questStateId,
                                    safeUuid, 
                                    safeName, 
                                    safeGender
                                ));

                            }
                        }
                    }
                }

                // Refresh the screen with 3 arguments
                Minecraft.getInstance().setScreen(new QuestDecisionScreen(newResponse, this.npcName,this.npcGender, this.npcUuid));
            } else {
                this.onClose();
            }
        });
    }

    private JsonObject questGiverContext() {
        JsonObject context = new JsonObject();
        String displayName = displayQuestGiverName(this.npcName);
        if (!displayName.isBlank()) {
            context.addProperty("quest_giver_name", displayName);
        }
        if (this.npcUuid != null) {
            context.addProperty("quest_giver_uuid", this.npcUuid.toString());
        }
        return context;
    }

    private static String displayQuestGiverName(String rawName) {
        if (rawName == null) return "";
        String cleaned = rawName.trim();
        if (cleaned.isBlank()) return "";
        if (!cleaned.contains(":")) return cleaned;
        return cleaned.split(":", 2)[0].trim();
    }

    private static String resolveQuestStateId(QuestResponse response) {
        if (response == null) return "";
        if (response.questStateId != null && !response.questStateId.isBlank()) {
            return response.questStateId.trim();
        }
        if (response.quest_id <= 0) return "";

        ClientQuestEntry entry = ClientQuestTable.findByQuestId(Long.toString(response.quest_id));
        return entry != null ? entry.questStateId() : "";
    }

    private static boolean hasAcceptedQuestState(QuestResponse response) {
        return response != null && response.questStateId != null && !response.questStateId.isBlank();
    }

    private static boolean isRejectChoice(QuestChoice choice) {
        if (choice == null) return false;
        String id = choice.id == null ? "" : choice.id.trim().toLowerCase(java.util.Locale.ROOT);
        String text = choice.text == null ? "" : choice.text.trim().toLowerCase(java.util.Locale.ROOT);
        return id.equals("reject")
                || id.equals("decline")
                || id.equals("refuse")
                || id.equals("no")
                || text.contains("reject")
                || text.contains("decline")
                || text.contains("refuse");
    }

    private void clearPendingOfferStateIfUnaccepted() {
        if (!hasAcceptedQuestState(this.questState)) {
            QuestManager.getInstance().clearState();
        }
    }

@Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1. This automatically calls your custom renderBackground() below, THEN draws the buttons
        super.render(graphics, mouseX, mouseY, partialTick);
        DialoguePresentation.renderDialogue(
                graphics,
                this.font,
                dialogueView,
                dialogueLayout,
                bodyComponent,
                "Quest Update"
        );
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1. Renders the default Minecraft dark/blurred background first
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        DialoguePresentation.renderPaperBackground(graphics, this.width, this.height);
    }

@Override
    public void onClose() {
        if (!this.choiceMade && questState != null && !questState.completed && hasAcceptedQuestState(questState)) {
            QuestClient.abandonQuest(questState.quest_id);
        }
        clearPendingOfferStateIfUnaccepted();
        
        Minecraft.getInstance().setScreen(null);
    }

}
