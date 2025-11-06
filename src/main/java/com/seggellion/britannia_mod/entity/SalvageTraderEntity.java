package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.network.ClientboundOpenNpcScreenPayload;
import com.seggellion.britannia_mod.npc.NpcType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;

public class SalvageTraderEntity extends CitizenEntity {

    public SalvageTraderEntity(EntityType<? extends SalvageTraderEntity> type, Level level) {
        super(type, level);
    }

    public static SalvageTraderEntity create(EntityType<SalvageTraderEntity> type, Level level) {
        return new SalvageTraderEntity(type, level);
    }
    
    @Override
    protected String getRoleTitle() {
        return "Salvage Trader";
    }

    // ---------- Interaction ----------
    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !player.level().isClientSide) {
            if (player instanceof ServerPlayer sp) {
                ClientboundOpenNpcScreenPayload.send(
                    sp,
                    NpcType.TRADER,            // same handler type as traders
                    "Salvage Trader",           // label for UI
                    this.getCityName(),         // city context for Rails API
                    this.getId()                // entityId
                );
            }
            return InteractionResult.SUCCESS;
        }
        return super.interactAt(player, hit, hand);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return CitizenEntity.baseAttributes();
    }



// SalvageTraderEntity.java
@Override
protected void updateDisplayName() {
    this.setCustomName(Component.literal(this.getPersonalName() + " the " + this.getRoleTitle()));
    this.setCustomNameVisible(true);
}


}
