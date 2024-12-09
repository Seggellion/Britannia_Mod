// RestrictedStrollGoal.java
package com.seggellion.britannia_mod.entity.ai.goal;

import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;

public class RestrictedStrollGoal extends RandomStrollGoal {

    private final int maxDistance;

    public RestrictedStrollGoal(PathfinderMob mob, double speed, int maxDistance) {
        super(mob, speed);
        this.maxDistance = maxDistance;
    }

    @Override
    protected Vec3 getPosition() {
        Vec3 homePos = Vec3.atBottomCenterOf(this.mob.getRestrictCenter());
        RandomSource random = this.mob.getRandom();

        for (int i = 0; i < 10; ++i) {
            // Added the missing angleRange parameter (e.g., Math.PI / 2 for 90 degrees)
            Vec3 targetPos = DefaultRandomPos.getPosTowards(this.mob, 10, 7, homePos, Math.PI / 2);

            if (targetPos == null) {
                return null;
            }

            if (targetPos.distanceTo(homePos) <= maxDistance) {
                return targetPos;
            }
        }
        return null;
    }
}
