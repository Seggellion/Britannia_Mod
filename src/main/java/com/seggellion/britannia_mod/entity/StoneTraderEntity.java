package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.trader.TraderDefinition;
import com.seggellion.britannia_mod.trader.TraderTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

public class StoneTraderEntity extends AbstractTraderEntity {
    public StoneTraderEntity(EntityType<? extends StoneTraderEntity> type, Level level) {
        super(type, level);
    }

    public static StoneTraderEntity create(EntityType<StoneTraderEntity> type, Level level) {
        return new StoneTraderEntity(type, level);
    }

    @Override
    public TraderDefinition getTraderDefinition() {
        return TraderTypes.byId(TraderTypes.STONE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return CitizenEntity.baseAttributes();
    }
}
