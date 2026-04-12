package com.seggellion.britannia_mod.entity;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import com.seggellion.britannia_mod.quest.network.QuestClient;
import com.seggellion.britannia_mod.client.screen.QuestDecisionScreen;


public class QuestGiverEntity extends CitizenEntity {

    public QuestGiverEntity(EntityType<? extends QuestGiverEntity> type, Level level) {
        super(type, level);
    }

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
            
            ResourceLocation portrait = getPortraitForNpc(displayNpcName);

            String gender = this.getGender(); 
            if (gender == null || gender.isEmpty()) {
                gender = "unknown";
            }
            final String finalGender = gender;
            // Ask Rails for the quest using the decoded internalApiId!
            QuestClient.interactWithNpc(internalApiId, response -> {
                if (response != null && response.error == null) {
                    // NEW: Pass this.getUUID() as the 4th argument!
                    Minecraft.getInstance().setScreen(new QuestDecisionScreen(response, displayNpcName, finalGender, this.getUUID()));
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
            return Component.literal(raw.split(":", 2)[0]); 
        }
        return super.getName();
    }

    @Override
    public Component getDisplayName() {
        String raw = this.getPersonalName();
        if (raw != null && raw.contains(":")) {
            return Component.literal(raw.split(":", 2)[0]); 
        }
        return super.getDisplayName();
    }

    // NEW: Fallback Portrait Logic
    private ResourceLocation getPortraitForNpc(String name) {
        String safeName = name.toLowerCase().replaceAll("[^a-z0-9.\\-]", "_");
        
        ResourceLocation specificPortrait = ResourceLocation.fromNamespaceAndPath(
            "britannia_mod", "textures/screens/portraits/" + safeName + ".png"
        );

        if (!Minecraft.getInstance().getResourceManager().getResource(specificPortrait).isPresent()) {
            return ResourceLocation.fromNamespaceAndPath(
                "britannia_mod", "textures/screens/portraits/generic_peasant.png"
            );
        }
        return specificPortrait;
    }
}