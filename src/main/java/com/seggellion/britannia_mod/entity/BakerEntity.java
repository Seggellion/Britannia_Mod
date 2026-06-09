package com.seggellion.britannia_mod.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

public class BakerEntity extends AbstractEconomyMerchantEntity {
    public BakerEntity(EntityType<? extends BakerEntity> type, Level level) {
        super(type, level);
    }

    public static BakerEntity create(EntityType<BakerEntity> type, Level level) {
        return new BakerEntity(type, level);
    }

    @Override
    protected String getRoleTitle() {
        return "Baker";
    }

    public static AttributeSupplier.Builder createAttributes() {
        return CitizenEntity.baseAttributes();
    }
}
