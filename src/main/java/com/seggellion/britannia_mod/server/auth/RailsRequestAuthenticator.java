package com.seggellion.britannia_mod.server.auth;

import net.minecraft.server.MinecraftServer;
import java.net.HttpURLConnection;

public final class RailsRequestAuthenticator {
    public static final String SHARD_NAME_HEADER = "Shard-Name";
    public static final String SHARD_SECRET_HEADER = "Shard-Secret";
    private RailsRequestAuthenticator() {}

    public static boolean apply(HttpURLConnection connection, MinecraftServer server) {
        return ServerAuthRegistry.credentials(server).map(credentials -> apply(connection, credentials)).orElse(false);
    }

    public static boolean apply(HttpURLConnection connection, ServerCredentials credentials) {
        if (connection == null || credentials == null) return false;
        connection.setRequestProperty(SHARD_NAME_HEADER, credentials.shardName());
        connection.setRequestProperty(SHARD_SECRET_HEADER, credentials.shardSecret());
        return true;
    }
}
