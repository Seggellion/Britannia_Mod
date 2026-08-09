package com.seggellion.britannia_mod.farming;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** Central owner-approved Creative-or-operator-level-2 authorization foundation. */
public final class FlowerProtectionService {
    public static final int ADMIN_PERMISSION_LEVEL = 2;

    private FlowerProtectionService() {
    }

    public static boolean isAdministrator(@Nullable Player player) {
        return player != null && (player.isCreative()
                || player instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(ADMIN_PERMISSION_LEVEL));
    }

    public static int effectivePermissionLevel(@Nullable Player player) {
        return player instanceof ServerPlayer serverPlayer
                && serverPlayer.hasPermissions(ADMIN_PERMISSION_LEVEL)
                ? ADMIN_PERMISSION_LEVEL
                : 0;
    }

    public static boolean mayMutate(
            FlowerPersistentState flower,
            @Nullable Player actor,
            FlowerMutationReason reason
    ) {
        if (flower == null || reason == null) {
            return false;
        }
        return mayMutate(
                flower.protectedFlower(),
                actor != null && actor.isCreative(),
                effectivePermissionLevel(actor),
                reason
        );
    }

    public static boolean isAdministrator(boolean creativeMode, int permissionLevel) {
        return creativeMode || permissionLevel >= ADMIN_PERMISSION_LEVEL;
    }

    public static boolean mayMutate(
            boolean flowerProtected,
            boolean creativeMode,
            int permissionLevel,
            FlowerMutationReason reason
    ) {
        if (reason == null || reason == FlowerMutationReason.PISTON) {
            return false;
        }
        if (reason.isSystemAuthorized()) {
            return true;
        }
        if (!flowerProtected) {
            return true;
        }
        if (reason == FlowerMutationReason.EXPLOSION || reason == FlowerMutationReason.FLUID) {
            return false;
        }
        return isAdministrator(creativeMode, permissionLevel);
    }
}
