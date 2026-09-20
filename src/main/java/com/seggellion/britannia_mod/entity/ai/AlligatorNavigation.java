package com.seggellion.britannia_mod.entity.ai;

import com.seggellion.britannia_mod.entity.AlligatorEntity;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.AmphibiousNodeEvaluator;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** One amphibious evaluator, with feet-based water arrival and exact final water intent. */
public final class AlligatorNavigation extends AmphibiousPathNavigation {
    @Nullable private Vec3 waterDestination;

    public AlligatorNavigation(AlligatorEntity mob, Level level) {
        super(mob, level);
        setMaxVisitedNodesMultiplier(2);
    }

    @Override
    protected PathFinder createPathFinder(int maxNodes) {
        nodeEvaluator = new AmphibiousNodeEvaluator(false) {
            @Override public void prepare(PathNavigationRegion region, Mob mob) {
                super.prepare(region, mob);
                // Amphibious defaults strongly prefer water over bank cells. Apply the local
                // policy after prepare; done still restores the original maluses.
                mob.setPathfindingMalus(PathType.WALKABLE, 0);
                mob.setPathfindingMalus(PathType.WATER_BORDER, 0);
            }
        };
        nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(nodeEvaluator, maxNodes);
    }

    @Override
    protected Path createPath(java.util.Set<BlockPos> positions, int padding, boolean above, int accuracy) {
        // A detour around a wide body can exceed the straight target distance. This bounds
        // route length at 24 for the unchanged 16-block target acquisition range.
        return super.createPath(positions, padding, above, accuracy,
                (float)mob.getAttributeValue(Attributes.FOLLOW_RANGE) * 1.5F);
    }

    public boolean moveToWaterDestination(Path path, Vec3 destination, double speed) {
        boolean accepted = moveTo(path, speed);
        if (accepted) waterDestination = destination;
        return accepted;
    }

    @Override
    public boolean moveTo(double x, double y, double z, double speed) {
        boolean waterRequest = mob.isInWater() || level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER);
        boolean accepted = super.moveTo(x, y, z, waterRequest ? 0 : 1, speed);
        if (accepted) waterDestination = new Vec3(x, y, z);
        return accepted;
    }

    @Override
    public boolean moveTo(@Nullable Path path, double speed) {
        waterDestination = null;
        return super.moveTo(path, speed);
    }

    private Vec3 nextWaterPoint() {
        Vec3 node = path.getNextEntityPos(mob);
        if (waterDestination != null && path.getNextNodeIndex() == path.getNodeCount() - 1
                && node.distanceToSqr(waterDestination) <= 4
                && level.noCollision(mob, mob.getBoundingBox().move(waterDestination.subtract(mob.position())))
                && canMoveDirectly(mob.position(), waterDestination)) return waterDestination;
        return node;
    }

    @Override
    protected void followThePath() {
        if (!mob.isInWater()) {
            super.followThePath();
            return;
        }
        Vec3 delta = nextWaterPoint().subtract(mob.position());
        if (Math.abs(delta.y) <= .15 && delta.horizontalDistance() <= .35) path.advance();
        doStuckDetection(mob.position());
    }

    @Override
    public void tick() {
        super.tick();
        if (mob.isInWater() && !isDone()) {
            Vec3 point = nextWaterPoint();
            mob.getMoveControl().setWantedPosition(point.x, point.y, point.z, speedModifier);
        }
    }

    @Override
    public void stop() {
        super.stop();
        waterDestination = null;
        if (mob.getMoveControl() instanceof AlligatorMoveControl control) control.clearWaterInput();
    }
}
