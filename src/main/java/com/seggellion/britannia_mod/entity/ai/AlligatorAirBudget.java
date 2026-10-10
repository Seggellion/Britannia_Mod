package com.seggellion.britannia_mod.entity.ai;

/** Conservative planning budget; actual air bookkeeping and drowning remain in LivingEntity. */
public final class AlligatorAirBudget {
    public static final double MIN_VERTICAL_PROGRESS = .06;
    public static final double MIN_HORIZONTAL_PROGRESS = .08;
    public static final int RESERVE_TICKS = 60;
    private AlligatorAirBudget() {}

    public static int returnTicks(double depth, double horizontalDistance) {
        return returnTicks(depth, horizontalDistance, 1);
    }

    public static int returnTicks(double depth, double horizontalDistance, double speedScale) {
        if (!Double.isFinite(depth) || !Double.isFinite(horizontalDistance) || !Double.isFinite(speedScale) || speedScale <= 0)
            return Integer.MAX_VALUE;
        speedScale = Math.min(1, speedScale);
        double cost = Math.ceil(Math.max(0, depth) / (MIN_VERTICAL_PROGRESS * speedScale)
                + Math.max(0, horizontalDistance) / (MIN_HORIZONTAL_PROGRESS * speedScale)) + RESERVE_TICKS;
        return cost >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)cost;
    }

    public static boolean canDive(int air, double depth, double horizontalDistance) {
        return canDive(air, depth, horizontalDistance, 1);
    }

    public static boolean canDive(int air, double depth, double horizontalDistance, double speedScale) {
        long trip = (long)returnTicks(depth, horizontalDistance, speedScale) + returnTicks(depth, horizontalDistance, speedScale) - RESERVE_TICKS;
        return trip < air;
    }
}
