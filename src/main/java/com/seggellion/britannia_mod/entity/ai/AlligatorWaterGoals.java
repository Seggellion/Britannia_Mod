package com.seggellion.britannia_mod.entity.ai;

import com.seggellion.britannia_mod.entity.AlligatorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import java.util.EnumSet;

/** Bounded water intent and survival, local to Alligator. No extra persisted state. */
public final class AlligatorWaterGoals {
    public static final int MAX_SAMPLES = 10;
    public static final int RETRY_TICKS = 20;
    private AlligatorWaterGoals() {}

    @Nullable
    private static Vec3 surface(AlligatorEntity a, int x, int z) {
        BlockPos cursor = BlockPos.containing(x, a.getY(), z);
        if (!a.level().hasChunkAt(cursor) || !a.level().getFluidState(cursor).is(FluidTags.WATER)) return null;
        for (int up = 0; up < 16; up++) {
            BlockPos next = cursor.above();
            if (!a.level().hasChunkAt(next)) return null;
            if (!a.level().getFluidState(next).is(FluidTags.WATER)) {
                double fluidTop = cursor.getY() + a.level().getFluidState(cursor).getHeight(a.level(), cursor);
                Vec3 candidate = new Vec3(x + .5, fluidTop - a.getEyeHeight() + .15, z + .5);
                return clear(a, candidate, false) ? candidate : null;
            }
            cursor = next;
        }
        return null;
    }

    private static boolean clear(AlligatorEntity a, Vec3 candidate, boolean fullySubmerged) {
        if (!a.isWithinRestriction(BlockPos.containing(candidate))) return false;
        var box = a.getBoundingBox().move(candidate.subtract(a.position())).deflate(.001);
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (!a.level().hasChunkAt(p) || (fullySubmerged && !a.level().getFluidState(p).is(FluidTags.WATER))) return false;
        }
        return a.level().noCollision(a, box);
    }

    private record Destination(Vec3 position, Path path, boolean dive) {}

    private static double speedScale(AlligatorEntity a) {
        return Math.min(1, a.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) / .3)
                * Math.min(1, a.getAttributeValue(net.neoforged.neoforge.common.NeoForgeMod.SWIM_SPEED));
    }

    @Nullable
    private static Destination select(AlligatorEntity a, boolean dive, boolean recovery) {
        for (int sample = 0; sample < MAX_SAMPLES; sample++) {
            int x = a.getBlockX() + (sample == 0 && recovery ? 0 : a.getRandom().nextInt(7) - 3);
            int z = a.getBlockZ() + (sample == 0 && recovery ? 0 : a.getRandom().nextInt(7) - 3);
            Vec3 top = surface(a, x, z);
            if (top == null) continue;
            Vec3 candidate = dive ? top.add(0, -(2 + a.getRandom().nextInt(2)), 0) : top;
            boolean actualDive = dive;
            if (!clear(a, candidate, dive)) {
                // A shallow pond must keep selecting supported surface routes. The last of
                // the ten samples may fall back, without starting another sampling pass.
                if (dive && sample == MAX_SAMPLES - 1) { candidate = top; actualDive = false; }
                else continue;
            }
            double horizontal = candidate.subtract(a.position()).horizontalDistance();
            if (actualDive && !AlligatorAirBudget.canDive(a.getAirSupply(), top.y - candidate.y, horizontal, speedScale(a))) continue;
            Path path = a.getNavigation().createPath(BlockPos.containing(candidate), 0);
            if (path != null && path.canReach()) {
                if (actualDive) {
                    Vec3 previous = a.position();
                    double vertical = 0;
                    double routeHorizontal = 0;
                    for (int node = 0; node < path.getNodeCount(); node++) {
                        Vec3 point = path.getEntityPosAtNode(a, node);
                        routeHorizontal += point.subtract(previous).horizontalDistance();
                        vertical += Math.abs(point.y - previous.y);
                        previous = point;
                    }
                    vertical += Math.abs(candidate.y - previous.y);
                    routeHorizontal += candidate.subtract(previous).horizontalDistance();
                    vertical = Math.max(vertical, top.y - candidate.y);
                    // Includes actual detours and conservatively budgets their reverse too.
                    if (!AlligatorAirBudget.canDive(a.getAirSupply(), vertical, routeHorizontal, speedScale(a))) continue;
                }
                return new Destination(candidate, path, actualDive);
            }
        }
        return null;
    }

    private static void follow(AlligatorEntity a, Destination destination) {
        ((AlligatorNavigation)a.getNavigation()).moveToWaterDestination(destination.path, destination.position, 1);
    }

    public static final class RecoverAir extends Goal {
        private final AlligatorEntity a;
        private int retryAt;
        private int routeStarted;
        @Nullable private Destination destination;

        public RecoverAir(AlligatorEntity a) { this.a = a; setFlags(EnumSet.of(Flag.MOVE)); }

        @Override public boolean canUse() {
            if (!a.isInWater() || !a.isUnderWater()) return false;
            Vec3 top = surface(a, a.getBlockX(), a.getBlockZ());
            int cost = top == null ? 240 : AlligatorAirBudget.returnTicks(top.y - a.getY(), 0, speedScale(a));
            return a.getAirSupply() <= Math.min(a.getMaxAirSupply() - 20, cost);
        }

        @Override public boolean canContinueToUse() {
            return a.isInWater() && (a.isUnderWater() || a.getAirSupply() < a.getMaxAirSupply());
        }

        @Override public void start() { a.getNavigation().stop(); retryAt = a.tickCount; destination = null; }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override public void tick() {
            if (!a.isUnderWater()) return;
            if (destination != null && a.tickCount - routeStarted >= 100) {
                a.getNavigation().stop();
                destination = null;
            }
            if ((destination == null || a.getNavigation().isDone()) && a.tickCount >= retryAt) {
                destination = select(a, false, true);
                retryAt = a.tickCount + RETRY_TICKS;
                routeStarted = a.tickCount;
                if (destination != null) follow(a, destination);
            }
        }

        @Override public void stop() { a.getNavigation().stop(); destination = null; }
    }

    public static final class IdleWater extends Goal {
        private final AlligatorEntity a;
        private int retryAt;
        private int routeNumber;
        private int startedAt;
        private int progressAt;
        private int arrivedAt;
        private boolean returnToSurface;
        private Vec3 lastProgress = Vec3.ZERO;
        @Nullable private Destination destination;

        public IdleWater(AlligatorEntity a) { this.a = a; setFlags(EnumSet.of(Flag.MOVE)); }

        @Nullable public Vec3 plannedDestination() { return destination == null ? null : destination.position; }
        public boolean plannedDive() { return destination != null && destination.dive; }

        @Override public boolean canUse() {
            if (!a.usesWaterMovement() || a.getTarget() != null || a.tickCount < retryAt) return false;
            retryAt = a.tickCount + RETRY_TICKS;
            boolean dive = !returnToSurface && (routeNumber + 1) % 4 == 0
                    && AlligatorAirBudget.canDive(a.getAirSupply(), 2, 0, speedScale(a));
            destination = select(a, dive, false);
            if (destination == null) return false;
            routeNumber++;
            returnToSurface = destination.dive;
            return true;
        }

        @Override public void start() {
            startedAt = progressAt = a.tickCount;
            arrivedAt = 0;
            lastProgress = a.position();
            follow(a, destination);
        }

        @Override public boolean canContinueToUse() {
            return a.usesWaterMovement() && a.getTarget() == null && a.tickCount - startedAt < 180
                    && a.tickCount - progressAt < 100 && (arrivedAt == 0 || a.tickCount - arrivedAt < 20);
        }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override public void tick() {
            if (a.position().distanceToSqr(lastProgress) >= .25) {
                progressAt = a.tickCount;
                lastProgress = a.position();
            }
            Vec3 delta = destination.position.subtract(a.position());
            if (arrivedAt == 0 && Math.abs(delta.y) < .3 && delta.horizontalDistance() < .5) arrivedAt = a.tickCount;
        }

        @Override public void stop() {
            a.getNavigation().stop();
            retryAt = a.tickCount + (returnToSurface ? RETRY_TICKS : 40);
            destination = null;
        }
    }
}
