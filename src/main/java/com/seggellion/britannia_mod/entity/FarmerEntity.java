package com.seggellion.britannia_mod.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

public class FarmerEntity extends AbstractEconomyMerchantEntity {
    public FarmerEntity(EntityType<? extends FarmerEntity> type, Level level) {
        super(type, level);
    }

    public static FarmerEntity create(EntityType<FarmerEntity> type, Level level) {
        return new FarmerEntity(type, level);
    }

    @Override
    protected String getRoleTitle() {
        return "Farmer";
    }

    public static AttributeSupplier.Builder createAttributes() {
        return CitizenEntity.baseAttributes();
    }
}
