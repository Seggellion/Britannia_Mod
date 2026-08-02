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

    /**
     * Milestone 14 Security Slice 2: additive on top of the existing header attachment above --
     * every request signed this way still carries the same Shard-Name/Shard-Secret headers a
     * client that never calls this overload would send, matching the Rails-side contract's own
     * additive design (unsigned requests keep working; a signed one gets real replay
     * protection). {@code body} must be the exact bytes that will actually be written to this
     * connection (or an empty array for a bodyless request) -- signing anything else would
     * produce a signature Rails' own body-hash check can never match.
     *
     * <p>This is the one place a body-aware signing overload needed to exist -- confirmed
     * during the Milestone 14 security recon that every real outbound-request call site in this
     * mod already funnels through one of this class's two {@code apply} overloads, so this new
     * one is inherited by any call site that adopts it, not a parallel mechanism.
     */
    public static boolean apply(HttpURLConnection connection, ServerCredentials credentials, byte[] body) {
        if (!apply(connection, credentials)) return false;

        RequestSignature.compute(credentials, connection.getRequestMethod(), connection.getURL().getFile(), body)
                .applyTo(connection);
        return true;
    }
}
