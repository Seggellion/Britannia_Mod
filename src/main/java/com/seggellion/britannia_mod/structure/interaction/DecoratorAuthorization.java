package com.seggellion.britannia_mod.structure.interaction;

import net.minecraft.server.level.ServerPlayer;

/** Server-only authorization policy for administrator decorator actions. */
public final class DecoratorAuthorization {
    public static final int REQUIRED_PERMISSION_LEVEL = 2;

    private DecoratorAuthorization() {
    }

    public static boolean isAuthorized(ServerPlayer player) {
        return evaluate(true, player.isCreative(), player.hasPermissions(REQUIRED_PERMISSION_LEVEL));
    }

    static boolean evaluate(boolean logicalServer, boolean serverCreative, boolean permissionLevelTwo) {
        return logicalServer && (serverCreative || permissionLevelTwo);
    }
}
