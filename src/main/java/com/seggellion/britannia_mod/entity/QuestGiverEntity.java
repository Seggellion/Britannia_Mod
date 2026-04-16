package com.seggellion.britannia_mod.entity;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.network.chat.Component;
import com.seggellion.britannia_mod.quest.network.QuestClient;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

// FATAL CLIENT IMPORTS HAVE BEEN REMOVED

public class QuestGiverEntity extends CitizenEntity {

    public QuestGiverEntity(EntityType<? extends QuestGiverEntity> type, Level level) {
        super(type, level);
    }

private static final ResourceLocation FONT_UO_CLASSIC = ResourceLocation.fromNamespaceAndPath("britannia_mod", "uo_classic");

private static final Style UO_STYLE = Style.EMPTY
            .withFont(FONT_UO_CLASSIC)
            .withColor(0x2194A5);

    @Override
    protected String getRoleTitle() {
        return "Wanderer";
    }

    public static AttributeSupplier.Builder createAttributes() {
        return CitizenEntity.baseAttributes();
    }

    @Override
    public InteractionResult interactAt(Player player, net.minecraft.world.phys.Vec3 hit, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && player.level().isClientSide) {
            
            String rawName = this.getPersonalName(); 
            if (rawName == null || rawName.isEmpty()) rawName = "Traveler";
            
            String parsedDisplayName = rawName;
            String internalApiId = rawName;

            // DECODE the internal API ID if it was injected by the spawner
            if (rawName.contains(":")) {
                String[] parts = rawName.split(":", 2); // Split into exactly 2 pieces max
                parsedDisplayName = parts[0]; 
                internalApiId = parts[1];
            }

            final String displayNpcName = parsedDisplayName;

            System.out.println("Initiating conversation visually with: " + displayNpcName + ", fetching API ID: " + internalApiId);

            String gender = this.getGender(); 
            if (gender == null || gender.isEmpty()) {
                gender = "unknown";
            }
            final String finalGender = gender;
            
            // Ask Rails for the quest using the decoded internalApiId!
            QuestClient.interactWithNpc(internalApiId, response -> {
                if (response != null && response.error == null) {
                    // SAFELY ROUTED: Let the client handler open the screen
                    if (net.neoforged.fml.loading.FMLLoader.getDist().isClient()) {
                        com.seggellion.britannia_mod.network.ClientNetworkHandler.openQuestDecisionScreen(
                            response, displayNpcName, finalGender, this.getUUID()
                        );
                    }
                } else {
                    String errMsg = (response != null && response.error != null) ? response.error : "I am busy, traveler.";
                    player.displayClientMessage(Component.literal("§e" + displayNpcName + " says: '" + errMsg + "'"), false);
                }
            });
            
            return InteractionResult.SUCCESS;
        }
        
        return super.interactAt(player, hit, hand);
    }

    // --- OVERRIDES TO HIDE THE ENCODING FROM THE PLAYER ---
    @Override
    public Component getName() {
        String raw = this.getPersonalName();
        if (raw != null && raw.contains(":")) {
           return Component.literal(raw.split(":", 2)[0]).withStyle(UO_STYLE);
        }
        return super.getName().copy().withStyle(UO_STYLE);
    }

    @Override
    public Component getDisplayName() {
        String raw = this.getPersonalName();
        if (raw != null && raw.contains(":")) {
            return Component.literal(raw.split(":", 2)[0]).withStyle(UO_STYLE);
        }
        Component superName = super.getDisplayName();
        return superName != null ? superName.copy().withStyle(UO_STYLE) : Component.empty();
    }
}