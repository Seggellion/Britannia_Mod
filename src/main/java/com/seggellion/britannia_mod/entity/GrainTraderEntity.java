package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.trader.TraderDefinition;
import com.seggellion.britannia_mod.trader.TraderTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

public class GrainTraderEntity extends AbstractTraderEntity {
    public GrainTraderEntity(EntityType<? extends GrainTraderEntity> type, Level level) {
        super(type, level);
    }

    public static GrainTraderEntity create(EntityType<GrainTraderEntity> type, Level level) {
        return new GrainTraderEntity(type, level);
    }

    @Override
    public TraderDefinition getTraderDefinition() {
        return TraderTypes.byId(TraderTypes.GRAINS);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return CitizenEntity.baseAttributes();
    }
}
