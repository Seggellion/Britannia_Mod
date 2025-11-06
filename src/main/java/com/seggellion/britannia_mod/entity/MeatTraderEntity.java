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

public class MeatTraderEntity extends CitizenEntity {

    public MeatTraderEntity(EntityType<? extends MeatTraderEntity> type, Level level) {
        super(type, level);
    }

    public static MeatTraderEntity create(EntityType<MeatTraderEntity> type, Level level) {
        return new MeatTraderEntity(type, level);
    }
    
    @Override
    protected String getRoleTitle() {
        return "Meat Trader";
    }

    // ---------- Interaction ----------
    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !player.level().isClientSide) {
            if (player instanceof ServerPlayer sp) {
                ClientboundOpenNpcScreenPayload.send(
                    sp,
                    NpcType.TRADER,            // same handler type as traders
                    "Meat Trader",           // label for UI
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



// MeatTraderEntity.java
@Override
protected void updateDisplayName() {
    this.setCustomName(Component.literal(this.getPersonalName() + " the " + this.getRoleTitle()));
    this.setCustomNameVisible(true);
}


}
