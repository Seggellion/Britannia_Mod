// LICHCastMagicGoal.java
package com.seggellion.britannia_mod.entity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

public class LichCastMagicGoal extends Goal {
    private static final int CASTING_DELAY_TICKS = 20;

    private final LichEntity lich;
    private int castingDelay;

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
        this.castingDelay = CASTING_DELAY_TICKS;
        this.freezeLich();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.lich.getTarget();
        return this.castingDelay > 0
                && target != null
                && target.isAlive()
                && this.lich.canCastMagicArrow();
    }

    @Override
    public void stop() {
        this.castingDelay = 0;
        this.lich.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.lich.getTarget();
        this.freezeLich();

        if (target != null) {
            this.lich.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }

        if (--this.castingDelay <= 0 && target != null && target.isAlive()) {
            this.lich.castMagicArrow(target);
        }
    }

    private void freezeLich() {
        this.lich.getNavigation().stop();

        Vec3 movement = this.lich.getDeltaMovement();
        this.lich.setDeltaMovement(0.0D, movement.y, 0.0D);
    }
}
