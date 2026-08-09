package com.seggellion.britannia_mod.skill;

import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived server-owned identities for the existing screen workflow. */
public final class BlacksmithSessionManager {
    private record Session(String token, long expiresAt) {}
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private BlacksmithSessionManager() {}

    public static String open(ServerPlayer player) {
        String token = UUID.randomUUID().toString();
        SESSIONS.put(player.getUUID(), new Session(token, player.serverLevel().getGameTime() + 1200));
        return token;
    }

    public static boolean validate(ServerPlayer player, String token) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || token == null || !session.token.equals(token)
                || player.serverLevel().getGameTime() > session.expiresAt) {
            SESSIONS.remove(player.getUUID());
            return false;
        }
        return true;
    }
}
