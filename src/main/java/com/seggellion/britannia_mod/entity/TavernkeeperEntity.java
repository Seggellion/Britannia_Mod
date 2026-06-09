package com.seggellion.britannia_mod.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

public class TavernkeeperEntity extends AbstractEconomyMerchantEntity {
    public TavernkeeperEntity(EntityType<? extends TavernkeeperEntity> type, Level level) {
        super(type, level);
    }

    public static TavernkeeperEntity create(EntityType<TavernkeeperEntity> type, Level level) {
        return new TavernkeeperEntity(type, level);
    }

    @Override
    protected String getRoleTitle() {
        return "Tavernkeeper";
    }

    public static AttributeSupplier.Builder createAttributes() {
        return CitizenEntity.baseAttributes();
    }
}
