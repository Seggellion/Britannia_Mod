package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.network.ClientboundOpenNpcScreenPayload;
import com.seggellion.britannia_mod.npc.NpcType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class AlcoholTraderEntity extends CitizenEntity {

    public AlcoholTraderEntity(EntityType<? extends AlcoholTraderEntity> type, Level level) {
        super(type, level);
    }

    public static AlcoholTraderEntity create(EntityType<AlcoholTraderEntity> type, Level level) {
        return new AlcoholTraderEntity(type, level);
    }

    @Override
    protected String getRoleTitle() {
        return "Alcohol Trader";
    }

    // ---------- Interaction ----------
    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !player.level().isClientSide) {
            if (player instanceof ServerPlayer sp) {
                // This triggers the GUI opening on the client side.
                // The "Alcohol Trader" string is crucial: it tells the GUI 
                // to hit the Rails endpoint for alcohol prices.
                ClientboundOpenNpcScreenPayload.send(
                    sp,
                    NpcType.TRADER,              // TRADER mode = Player Sells to NPC
                    "Alcohol Trader",            // Role ID for Rails API catalog lookup
                    this.getCityName(),          // City Context
                    this.getId()                 // Entity ID for transaction verification
                );
            }
            return InteractionResult.SUCCESS;
        }
        return super.interactAt(player, hit, hand);
    }

    // ---------- Attributes ----------
    public static AttributeSupplier.Builder createAttributes() {
        // Re-uses the base Citizen attributes
        return CitizenEntity.baseAttributes();
    }

    // ---------- Display Name ----------
    @Override
    protected void updateDisplayName() {
        // Renders as "John the Alcohol Trader"
        this.setCustomName(Component.literal(this.getPersonalName() + " the " + this.getRoleTitle()));
        this.setCustomNameVisible(true);
    }
}