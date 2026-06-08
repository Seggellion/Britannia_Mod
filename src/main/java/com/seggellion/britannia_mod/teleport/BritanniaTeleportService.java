package com.seggellion.britannia_mod.teleport;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BritanniaTeleportService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    private static final Map<String, Long> LAST_FAILURE_LOG = new HashMap<>();
    private static final int FAILURE_LOG_INTERVAL_TICKS = 100;

    private BritanniaTeleportService() {
    }

    public static TeleportResult teleport(ServerPlayer player, TeleportDestination destination, int cooldownTicks) {
        if (player == null || destination == null) {
            return TeleportResult.failure("missing_player_or_destination", "null input");
        }

        ServerLevel currentLevel = player.serverLevel();
        long now = currentLevel.getGameTime();
        Long cooldownUntil = COOLDOWNS.get(player.getUUID());
        if (cooldownUntil != null && cooldownUntil > now) {
            return TeleportResult.failure("cooldown", context(player, destination));
        }

        ServerLevel targetLevel = player.server.getLevel(destination.dimension());
        if (targetLevel == null) {
            TeleportResult result = TeleportResult.failure("missing_dimension", context(player, destination));
            logFailure(player, destination, result, now);
            return result;
        }

        BlockPos targetPos = BlockPos.containing(destination.x(), destination.y(), destination.z());
        if (targetLevel.isOutsideBuildHeight(targetPos)) {
            TeleportResult result = TeleportResult.failure("outside_build_height", context(player, destination));
            logFailure(player, destination, result, now);
            return result;
        }

        targetLevel.getChunk(targetPos);

        Vec3 safePosition = resolveSafePosition(targetLevel, destination);
        if (safePosition == null) {
            TeleportResult result = TeleportResult.failure("unsafe_destination", context(player, destination));
            logFailure(player, destination, result, now);
            return result;
        }

        player.stopRiding();
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.teleportTo(
                targetLevel,
                safePosition.x,
                safePosition.y,
                safePosition.z,
                destination.yaw() != null ? destination.yaw() : player.getYRot(),
                destination.pitch() != null ? destination.pitch() : player.getXRot()
        );
        player.hurtMarked = true;

        if (cooldownTicks > 0) {
            COOLDOWNS.put(player.getUUID(), now + cooldownTicks);
        }

        LOGGER.debug("Teleport success source={} player={} to=({}, {}, {}) dim={}",
                destination.source(),
                player.getName().getString(),
                safePosition.x,
                safePosition.y,
                safePosition.z,
                targetLevel.dimension().location());
        return TeleportResult.success(context(player, destination));
    }

    private static Vec3 resolveSafePosition(ServerLevel level, TeleportDestination destination) {
        if (isSafe(level, BlockPos.containing(destination.x(), destination.y(), destination.z()))) {
            return new Vec3(destination.x(), destination.y(), destination.z());
        }

        int baseY = BlockPos.containing(destination.x(), destination.y(), destination.z()).getY();
        for (int offset = 1; offset <= 4; offset++) {
            BlockPos above = BlockPos.containing(destination.x(), baseY + offset, destination.z());
            if (isSafe(level, above)) {
                return new Vec3(destination.x(), above.getY(), destination.z());
            }
        }

        for (int offset = 1; offset <= 4; offset++) {
            BlockPos below = BlockPos.containing(destination.x(), baseY - offset, destination.z());
            if (isSafe(level, below)) {
                return new Vec3(destination.x(), below.getY(), destination.z());
            }
        }

        return null;
    }

    private static boolean isSafe(ServerLevel level, BlockPos feet) {
        if (level.isOutsideBuildHeight(feet) || level.isOutsideBuildHeight(feet.above())) {
            return false;
        }

        if (level.getFluidState(feet).is(FluidTags.LAVA)
                || level.getFluidState(feet.above()).is(FluidTags.LAVA)
                || level.getFluidState(feet.below()).is(FluidTags.LAVA)) {
            return false;
        }

        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(feet.above());
        return feetState.getCollisionShape(level, feet).isEmpty()
                && headState.getCollisionShape(level, feet.above()).isEmpty();
    }

    private static void logFailure(
            ServerPlayer player,
            TeleportDestination destination,
            TeleportResult result,
            long now
    ) {
        String key = player.getUUID() + ":" + destination.source() + ":" + result.failureReason();
        long lastLogged = LAST_FAILURE_LOG.getOrDefault(key, Long.MIN_VALUE);
        if (lastLogged == Long.MIN_VALUE || now - lastLogged >= FAILURE_LOG_INTERVAL_TICKS) {
            LAST_FAILURE_LOG.put(key, now);
            LOGGER.warn("Teleport failed source={} player={} reason={} context={}",
                    destination.source(),
                    player.getName().getString(),
                    result.failureReason(),
                    result.debugContext());
        }
    }

    private static String context(ServerPlayer player, TeleportDestination destination) {
        return "player=" + player.getName().getString()
                + ", source=" + destination.source()
                + ", dim=" + destination.dimension().location()
                + ", x=" + destination.x()
                + ", y=" + destination.y()
                + ", z=" + destination.z();
    }
}
