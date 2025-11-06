package com.seggellion.britannia_mod.entity;


import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

import com.seggellion.britannia_mod.network.ClientboundOpenNpcScreenPayload;
import com.seggellion.britannia_mod.npc.NpcType;


public class FishTraderEntity extends CitizenEntity {
    public FishTraderEntity(EntityType<? extends FishTraderEntity> type, Level level) {
        super(type, level);
    }

    public static FishTraderEntity create(EntityType<FishTraderEntity> type, Level level) {
        return new FishTraderEntity(type, level);
    }

    // ---------- Interaction ----------
@Override
public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
    if (hand == InteractionHand.MAIN_HAND && !player.level().isClientSide) {
        if (player instanceof ServerPlayer sp) {
            ClientboundOpenNpcScreenPayload.send(
                sp,
                NpcType.TRADER,
                "Fish Trader",          // role label for UI
                this.getCityName(),     // or a hardcoded city, e.g. "Britain"
                this.getId()            // entityId
            );
        }
        return InteractionResult.SUCCESS;
    }
    return super.interactAt(player, hit, hand);
}

    public static AttributeSupplier.Builder createAttributes() {
        return CitizenEntity.baseAttributes();
    }

    @Override
    protected String getRoleTitle() {
        return "Fish Trader";
    }

    @Override
    protected void updateDisplayName() {
        this.setCustomName(Component.literal(this.getPersonalName() + " the " + this.getRoleTitle()));
        this.setCustomNameVisible(true);
    }

}
