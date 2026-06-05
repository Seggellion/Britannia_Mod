package com.seggellion.britannia_mod.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

public class CostermongerEntity extends AbstractEconomyMerchantEntity {
    public CostermongerEntity(EntityType<? extends CostermongerEntity> type, Level level) {
        super(type, level);
    }

    public static CostermongerEntity create(EntityType<CostermongerEntity> type, Level level) {
        return new CostermongerEntity(type, level);
    }

    @Override
    protected String getRoleTitle() {
        return "Costermonger";
    }

    public static AttributeSupplier.Builder createAttributes() {
        return CitizenEntity.baseAttributes();
    }
}
