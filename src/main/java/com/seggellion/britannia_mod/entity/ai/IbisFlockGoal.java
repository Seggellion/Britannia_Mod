package com.seggellion.britannia_mod.entity.ai;

import com.seggellion.britannia_mod.entity.IbisEntity;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.entity.ai.goal.Goal;

/** Keeps nearby ibis moving as a flock while the oldest local bird remains free to wander. */
public final class IbisFlockGoal extends Goal {
    private final IbisEntity ibis;
    private final double speed;
    private final double startDistanceSquared;
    private final double searchDistance;
    private final double stopDistanceSquared;
    private IbisEntity leader;
    private int repathTicks;

    public IbisFlockGoal(
            IbisEntity ibis,
            double speed,
            double startDistance,
            double searchDistance) {
        this.ibis = ibis;
        this.speed = speed;
        this.startDistanceSquared = startDistance * startDistance;
        this.searchDistance = searchDistance;
        this.stopDistanceSquared = 2.0D * 2.0D;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        leader = selectLeader(ibis, nearbyOlderIbis());
        return leader != null && ibis.distanceToSqr(leader) > startDistanceSquared;
    }

    @Override
    public boolean canContinueToUse() {
        return leader != null
                && leader.isAlive()
                && ibis.distanceToSqr(leader) > stopDistanceSquared
                && ibis.distanceToSqr(leader) < searchDistance * searchDistance * 2.25D;
    }

    @Override
    public void start() {
        repathTicks = 0;
    }

    @Override
    public void stop() {
        leader = null;
        ibis.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (leader != null && --repathTicks <= 0) {
            repathTicks = adjustedTickDelay(10);
            ibis.getNavigation().moveTo(leader, speed);
        }
    }

    private List<IbisEntity> nearbyOlderIbis() {
        return ibis.level().getEntitiesOfClass(
                IbisEntity.class,
                ibis.getBoundingBox().inflate(searchDistance),
                candidate -> candidate != ibis && candidate.isAlive() && candidate.getId() < ibis.getId());
    }

    static IbisEntity selectLeader(IbisEntity ibis, List<IbisEntity> candidates) {
        return candidates.stream()
                .min(Comparator.comparingDouble(ibis::distanceToSqr))
                .orElse(null);
    }
}
