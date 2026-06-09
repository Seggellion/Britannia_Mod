package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.trader.TraderDefinition;
import com.seggellion.britannia_mod.trader.TraderTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

public class OreTraderEntity extends AbstractTraderEntity {
    public OreTraderEntity(EntityType<? extends OreTraderEntity> type, Level level) {
        super(type, level);
    }

    public static OreTraderEntity create(EntityType<OreTraderEntity> type, Level level) {
        return new OreTraderEntity(type, level);
    }

    @Override
    public TraderDefinition getTraderDefinition() {
        return TraderTypes.byId(TraderTypes.ORE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return CitizenEntity.baseAttributes();
    }
}
