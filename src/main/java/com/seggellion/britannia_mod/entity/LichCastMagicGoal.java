// LICHCastMagicGoal.java
package com.seggellion.britannia_mod.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class LichCastMagicGoal extends Goal {
    private final LichEntity lich;

    public LichCastMagicGoal(LichEntity lich) {
        this.lich = lich;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.lich.getTarget() != null && lich.canCastMagicArrow();
    }

    @Override
    public void start() {
        LivingEntity target = this.lich.getTarget();
        if (target != null) {
            this.lich.castMagicArrow(target);
        }
    }
}
