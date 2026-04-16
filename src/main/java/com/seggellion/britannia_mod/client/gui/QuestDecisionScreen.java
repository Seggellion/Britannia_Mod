package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.quest.network.QuestClient;
import com.seggellion.britannia_mod.quest.network.QuestModels.QuestResponse;
import com.seggellion.britannia_mod.quest.network.QuestModels.QuestChoice;
import com.seggellion.britannia_mod.client.render.PortraitDownloader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

public class QuestDecisionScreen extends Screen {
    private static final ResourceLocation PAPER_BACKGROUND = ResourceLocation.fromNamespaceAndPath("britannia_mod", "textures/screens/dialogue_screen.png");
    private static final ResourceLocation FONT_UO_CLASSIC = ResourceLocation.fromNamespaceAndPath("britannia_mod", "uo_classic");
    private static final Style UO_STYLE = Style.EMPTY.withFont(FONT_UO_CLASSIC);

    private final String npcName;
    private final String npcGender;
    private final QuestResponse questState;

    private boolean choiceMade = false;

    // Layout variables calculated dynamically in init()
    private final int topSectionHeight = 134; 
    private int maxTextWidth;
    private Component bodyComponent;
    
    // Independent Y anchors for each column
    private int portraitY;
    private int textY;
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
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        String bodyText = questState.currentNode != null ? questState.currentNode.body : "No dialogue.";
        this.bodyComponent = Component.literal(bodyText).withStyle(UO_STYLE);

        int portraitX = 30;
        int visibleSize = 108;
        //int visibleSize = 108;
        int textX = portraitX + visibleSize + 25;
        int buttonWidth = 140;
        int buttonStartX = this.width - buttonWidth - 20;
        
        this.maxTextWidth = buttonStartX - textX - 20; 
        
        int col1Height = visibleSize + 5 + this.font.lineHeight;
        int textLineCount = this.font.split(this.bodyComponent, this.maxTextWidth).size();
        int col2Height = textLineCount * this.font.lineHeight; 
        
        boolean hasChoices = !questState.completed && questState.choices != null && !questState.choices.isEmpty();
        int col3Height = hasChoices ? (questState.choices.size() * 24) - 4 : 20;

        this.portraitY = Math.max(5, (this.topSectionHeight - col1Height) / 2) + 15;
        this.textY = Math.max(5, (this.topSectionHeight - col2Height) / 2);
        int buttonStartY = Math.max(5, (this.topSectionHeight - col3Height) / 2);

        if (hasChoices) {
            boolean isInfoNode = questState.currentNode != null && "info".equals(questState.currentNode.nodeType);

            if (isInfoNode && questState.choices.size() == 1) {
                QuestChoice choice = questState.choices.get(0);
                Component btnText = Component.literal("Next ->").withStyle(UO_STYLE); 
                
                Button nextBtn = Button.builder(btnText, btn -> handleChoice(choice))
                    .bounds(buttonStartX, buttonStartY, buttonWidth, 20)
                    .build();
                
                nextBtn.active = !choice.isLocked;
                this.addRenderableWidget(nextBtn);
                
            } else {
                for (int i = 0; i < questState.choices.size(); i++) {
                    QuestChoice choice = questState.choices.get(i);
                    Component btnText = Component.literal(choice.text).withStyle(UO_STYLE);
                    
                    Button choiceBtn = Button.builder(btnText, btn -> {
                        // NEW: Intercept choices with no destination and just close the UI!
                        if (choice.id == null || choice.id.trim().isEmpty() || "close".equalsIgnoreCase(choice.id)) {
                            this.onClose();
                        } else {
                            handleChoice(choice);
                        }
                    })
                        .bounds(buttonStartX, buttonStartY + (i * 24), buttonWidth, 20)
                        .build();
                    
                    choiceBtn.active = !choice.isLocked;
                    this.addRenderableWidget(choiceBtn);
                }
            }
        } else {
            Component farewellText = Component.literal("Farewell.").withStyle(UO_STYLE);
            this.addRenderableWidget(Button.builder(farewellText, btn -> this.onClose())
                .bounds(buttonStartX, buttonStartY, buttonWidth, 20)
                .build());
        }
    }

    private void handleChoice(QuestChoice choice) {
        this.choiceMade = true;
        QuestClient.sendTransition(questState.quest_id, choice.id, null, newResponse -> {
            if (newResponse != null && newResponse.error == null) {
                
                if (newResponse.granted_items != null && !newResponse.granted_items.isEmpty()) {
                    net.minecraft.client.multiplayer.ClientPacketListener connection = Minecraft.getInstance().getConnection();
                    if (connection != null) {
                        connection.send(new com.seggellion.britannia_mod.network.payload.ClaimQuestRewardC2SPayload(newResponse.granted_items));
                    }
                }

                if (newResponse.client_actions != null) {
                    for (var action : newResponse.client_actions) {
                        if ("spawn_escort".equals(action.action)) {
                            net.minecraft.client.multiplayer.ClientPacketListener connection = Minecraft.getInstance().getConnection();
                            if (connection != null) {
                                java.util.UUID safeUuid = (this.npcUuid != null) ? this.npcUuid : new java.util.UUID(0, 0);
                                connection.send(new com.seggellion.britannia_mod.network.payload.SpawnEscortC2SPayload(action.entity_type, newResponse.quest_id, safeUuid));
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

@Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1. This automatically calls your custom renderBackground() below, THEN draws the buttons
        super.render(graphics, mouseX, mouseY, partialTick);

        // 2. Now render your text and portraits so they are crystal clear on top
        int portraitX = 30; 
        int textOpaqueColor = 0xFF111111;
        int textureSize = 108; 
       // int textureSize = 128; 
        int cropMargin = 10;
        int visibleSize = textureSize - (cropMargin * 2);

        if (npcName != null && !npcName.isEmpty()) {
            // dynamically fetch the portrait        
            ResourceLocation currentPortrait = PortraitDownloader.getPortrait(npcName, this.npcGender);
            
            graphics.blit(currentPortrait, portraitX, this.portraitY, cropMargin, cropMargin, visibleSize, visibleSize, textureSize, textureSize);

            Component nameComponent = Component.literal(npcName).withStyle(UO_STYLE);
            int nameWidth = this.font.width(nameComponent);
            
            // FIX: Replaced portraitSize with visibleSize
            int nameX = portraitX + (visibleSize / 2) - (nameWidth / 2);
            int nameY = this.portraitY + visibleSize + 3;
            graphics.drawString(this.font, nameComponent, nameX, nameY, textOpaqueColor, false);
            
            // FIX: Replaced portraitSize with visibleSize
            int textX = portraitX + visibleSize + 25;
            graphics.drawWordWrap(this.font, this.bodyComponent, textX, this.textY, this.maxTextWidth, textOpaqueColor);
        } else {
            String titleText = (questState.currentNode != null && questState.currentNode.title != null) 
                               ? questState.currentNode.title 
                               : "Quest Update";
                               
            Component nameComponent = Component.literal(titleText).withStyle(UO_STYLE);
            graphics.drawString(this.font, nameComponent, portraitX, this.portraitY, textOpaqueColor, false);
            
            // FIX: Replaced portraitSize with visibleSize
            graphics.drawWordWrap(this.font, this.bodyComponent, portraitX, this.textY + 15, this.maxTextWidth + visibleSize, textOpaqueColor);
        }
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
        graphics.fill(0, 0, this.width, this.height, 0xCC000000);
        // 2. Renders your paper texture ON TOP of the blur, but BEHIND the buttons
        graphics.blit(PAPER_BACKGROUND, 0, 0, 0, 0, this.width, this.topSectionHeight, this.width, this.topSectionHeight);
    }

@Override
    public void onClose() {
        if (!this.choiceMade && questState != null && !questState.completed) {
            QuestClient.abandonQuest(questState.quest_id);
        }
        
        Minecraft.getInstance().setScreen(null);
    }

}