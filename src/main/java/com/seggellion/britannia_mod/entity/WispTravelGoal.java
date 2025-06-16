package com.seggellion.britannia_mod.entity.ai.goal;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class WispTravelGoal<T extends Mob> extends Goal {
    private final T entity;
    private Vec3 targetPosition;

    public WispTravelGoal(T entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.entity.getNavigation().isInProgress()) {
            return false;
        }
        targetPosition = this.getRandomLocation();
        return targetPosition != null;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.entity.getNavigation().isDone();
    }

    @Override
    public void start() {
        this.entity.getNavigation().moveTo(targetPosition.x, targetPosition.y, targetPosition.z, 1.0D);
    }

    private Vec3 getRandomLocation() {
        Vec3 currentPosition = this.entity.position();
        double dx = currentPosition.x + (this.entity.getRandom().nextDouble() * 20 - 10);
        double dy = currentPosition.y + (this.entity.getRandom().nextDouble() * 6 - 3);
        double dz = currentPosition.z + (this.entity.getRandom().nextDouble() * 20 - 10);
        return new Vec3(dx, dy, dz);
    }
}
