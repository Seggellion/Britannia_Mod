package com.seggellion.britannia_mod.entity;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import com.seggellion.britannia_mod.network.ClientboundOpenNpcScreenPayload;
import com.seggellion.britannia_mod.npc.NpcType;

public class ArchitectEntity extends CitizenEntity {
    protected ArchitectEntity(EntityType<? extends ArchitectEntity> type, Level level) {
        super(type, level);
    }

    public static ArchitectEntity create(EntityType<ArchitectEntity> type, Level level) {
        return new ArchitectEntity(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return CitizenEntity.baseAttributes();
    }

    // ---------- Interaction ----------
    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !player.level().isClientSide) {
            if (player instanceof ServerPlayer sp) {
                   ClientboundOpenNpcScreenPayload.send(
                sp,
                NpcType.MERCHANT,
                "Architect",      // role
                this.city(),      // <-- inherited from CitizenEntity
                this.getId()      // entityId
            );
            }
            return InteractionResult.SUCCESS;
        }
        return super.interactAt(player, hit, hand);
    }

    public void setPersonalName(String personalName) {
     //   super.setPersonalName(personalName, "The Architect");
    }
}
