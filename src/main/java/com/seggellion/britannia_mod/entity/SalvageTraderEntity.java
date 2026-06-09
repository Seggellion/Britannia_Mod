package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.trader.TraderDefinition;
import com.seggellion.britannia_mod.trader.TraderTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

public class SalvageTraderEntity extends AbstractTraderEntity {
    public SalvageTraderEntity(EntityType<? extends SalvageTraderEntity> type, Level level) {
        super(type, level);
    }

    public static SalvageTraderEntity create(EntityType<SalvageTraderEntity> type, Level level) {
        return new SalvageTraderEntity(type, level);
    }

    @Override
    public TraderDefinition getTraderDefinition() {
        return TraderTypes.byId(TraderTypes.SALVAGE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return CitizenEntity.baseAttributes();
    }
}
