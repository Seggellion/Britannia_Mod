package com.seggellion.britannia_mod.server.auth;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class ServerAuthRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<MinecraftServer, State> STATES = new ConcurrentHashMap<>();
    private ServerAuthRegistry() {}

    public static void initialize(MinecraftServer server, Path gameDirectory, boolean dedicatedServer) {
        try {
            Optional<ServerCredentials> loaded = ServerCredentialSource.load(gameDirectory);
            if (loaded.isEmpty()) {
                STATES.put(server, State.unavailable("credentials_not_configured"));
                LOGGER.warn("UltimaCraft server authentication is unavailable: credentials are not configured");
                return;
            }
            ServerCredentials credentials = loaded.get();
            if (!dedicatedServer && (!credentials.integratedServerAllowed() || !isLoopback(credentials))) {
                STATES.put(server, State.unavailable("integrated_server_disabled"));
                LOGGER.warn("UltimaCraft server authentication is unavailable: integrated-server use is disabled");
                return;
            }
            STATES.put(server, State.available(credentials));
            LOGGER.info("UltimaCraft server authentication initialized: source={}, shard={}, fingerprint={}",
                credentials.source(), credentials.shardName(), credentials.fingerprint());
        } catch (CredentialConfigurationException error) {
            STATES.put(server, State.unavailable("credentials_invalid"));
            LOGGER.error("UltimaCraft server authentication configuration is invalid: {}", error.getMessage());
        }
    }

    public static Optional<ServerCredentials> credentials(MinecraftServer server) {
        State state = STATES.get(server);
        return state == null ? Optional.empty() : Optional.ofNullable(state.credentials);
    }
    public static String unavailableReason(MinecraftServer server) {
        State state = STATES.get(server);
        return state == null ? "not_initialized" : state.unavailableReason;
    }
    public static void recordBootstrapResult(MinecraftServer server, String result) {
        State state = STATES.get(server);
        if (state != null) state.lastBootstrapResult.set(sanitizeResult(result));
    }
    public static String lastBootstrapResult(MinecraftServer server) {
        State state = STATES.get(server);
        return state == null ? "not_run" : state.lastBootstrapResult.get();
    }
    public static void clear(MinecraftServer server) { STATES.remove(server); }

    public static boolean matchesConfiguredSecret(MinecraftServer server, String providedSecret) {
        if (providedSecret == null) return false;
        return credentials(server).map(credentials -> MessageDigest.isEqual(
            credentials.shardSecret().getBytes(StandardCharsets.UTF_8),
            providedSecret.getBytes(StandardCharsets.UTF_8)
        )).orElse(false);
    }

    private static boolean isLoopback(ServerCredentials credentials) {
        String host = credentials.serviceOrigin().getHost();
        return host != null && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")
            || host.equals("::1") || host.equals("[::1]"));
    }
    private static String sanitizeResult(String result) {
        if (result == null || result.isBlank()) return "unknown";
        String sanitized = result.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return sanitized.substring(0, Math.min(sanitized.length(), 80));
    }

    private static final class State {
        private final ServerCredentials credentials;
        private final String unavailableReason;
        private final AtomicReference<String> lastBootstrapResult = new AtomicReference<>("not_run");
        private State(ServerCredentials credentials, String unavailableReason) {
            this.credentials = credentials;
            this.unavailableReason = unavailableReason;
        }
        private static State available(ServerCredentials credentials) { return new State(credentials, "available"); }
        private static State unavailable(String reason) { return new State(null, reason); }
    }
}
