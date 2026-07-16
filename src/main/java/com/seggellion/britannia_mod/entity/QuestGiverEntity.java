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
import com.seggellion.britannia_mod.entity.ai.EscortPlayerGoal;
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
    protected void registerGoals() {
        super.registerGoals(); // Inherits the float and stroll goals from CitizenEntity
        
        // Priority 0: Always follow the player if the escort tag exists!
        // speedModifier = 1.2, stopDistance = 3 blocks, teleportDistance = 20 blocks
        this.goalSelector.addGoal(0, new EscortPlayerGoal(this, 1.2D, 3.0F, 20.0F));
    }

@Override
    public InteractionResult interactAt(Player player, net.minecraft.world.phys.Vec3 hit, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            // Only run the UI logic on the client
            if (player.level().isClientSide) {
                String rawName = this.getPersonalName(); 
                if (rawName == null || rawName.isEmpty()) rawName = "Traveler";
                
                String parsedDisplayName = rawName;
                String internalApiId = rawName;

                if (rawName.contains(":")) {
                    String[] parts = rawName.split(":", 2); 
                    parsedDisplayName = parts[0]; 
                    internalApiId = parts[1];
                }

                final String displayNpcName = parsedDisplayName;
                String gender = this.getGender(); 
                if (gender == null || gender.isEmpty()) gender = "unknown";
                final String finalGender = gender;
                
                QuestClient.interactWithNpc(this.getId(), this.getUUID(), response -> {
                    if (response != null && response.error == null) {
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
            }
            
            // Return SUCCESS on both sides to prevent desyncs and block-clicks!
            return InteractionResult.sidedSuccess(player.level().isClientSide);
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
