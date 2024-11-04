// DaemonCastMagicGoal.java
package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.magic.MagicArrowSpell;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity;
import java.util.EnumSet;

public class DaemonCastMagicGoal extends Goal {
    private final DaemonEntity daemon;

    public DaemonCastMagicGoal(DaemonEntity daemon) {
        this.daemon = daemon;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.daemon.getTarget() != null && daemon.canCastMagicArrow();
    }

    @Override
    public void start() {
        LivingEntity target = this.daemon.getTarget();
        if (target != null) {
            this.daemon.castMagicArrow(target);
        }
    }
}
