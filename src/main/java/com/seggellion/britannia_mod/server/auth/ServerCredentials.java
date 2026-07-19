package com.seggellion.britannia_mod.server.auth;

import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ServerCredentials {
    public enum Source { ENVIRONMENT, SERVER_FILE }
    public enum MinecraftServerKeyStatus { AVAILABLE, MISSING, INVALID }

    private final String shardName;
    private final String shardSecret;
    private final URI serviceOrigin;
    private final RailsApiUrlResolver apiUrls;
    private final Source source;
    private final boolean integratedServerAllowed;
    private final boolean railsUpdateListenerEnabled;
    private final String fingerprint;
    private final UUID minecraftServerKey;
    private final MinecraftServerKeyStatus minecraftServerKeyStatus;

    ServerCredentials(String shardName, String shardSecret, URI serviceOrigin, Source source,
                      boolean integratedServerAllowed, boolean railsUpdateListenerEnabled,
                      String configuredMinecraftServerKey) {
        this.shardName = Objects.requireNonNull(shardName, "shardName");
        this.shardSecret = Objects.requireNonNull(shardSecret, "shardSecret");
        this.serviceOrigin = Objects.requireNonNull(serviceOrigin, "serviceOrigin");
        this.apiUrls = RailsApiUrlResolver.fromServiceOrigin(serviceOrigin);
        this.source = Objects.requireNonNull(source, "source");
        this.integratedServerAllowed = integratedServerAllowed;
        this.railsUpdateListenerEnabled = railsUpdateListenerEnabled;
        this.fingerprint = fingerprint(shardSecret);
        UUID parsedKey = null;
        MinecraftServerKeyStatus parsedStatus = MinecraftServerKeyStatus.MISSING;
        if (configuredMinecraftServerKey != null && !configuredMinecraftServerKey.isBlank()) {
            try {
                parsedKey = UUID.fromString(configuredMinecraftServerKey.trim());
                parsedStatus = MinecraftServerKeyStatus.AVAILABLE;
            } catch (IllegalArgumentException invalid) {
                parsedStatus = MinecraftServerKeyStatus.INVALID;
            }
        }
        this.minecraftServerKey = parsedKey;
        this.minecraftServerKeyStatus = parsedStatus;
    }

    /**
     * Test-only construction, analogous to {@code ServerCredentialsTestFactory} (which
     * lives in {@code src/test} and is unreachable from GameTests living in
     * {@code src/main}'s {@code gametest} package by this codebase's own convention).
     * Public for the same cross-package-test-access reason
     * {@link com.seggellion.britannia_mod.service.banking.BankingOpenClient}'s own test
     * constructor and functional interfaces were widened.
     */
    public static ServerCredentials forGameTesting(URI serviceOrigin, @javax.annotation.Nullable UUID minecraftServerKey) {
        return new ServerCredentials(
                "Test Shard", "test-only-secret-sentinel", serviceOrigin, Source.SERVER_FILE, false, false,
                minecraftServerKey == null ? null : minecraftServerKey.toString()
        );
    }

    public String shardName() { return shardName; }
    String shardSecret() { return shardSecret; }
    public URI serviceOrigin() { return serviceOrigin; }
    public RailsApiUrlResolver apiUrls() { return apiUrls; }
    public Source source() { return source; }
    public boolean integratedServerAllowed() { return integratedServerAllowed; }
    public boolean railsUpdateListenerEnabled() { return railsUpdateListenerEnabled; }
    public String fingerprint() { return fingerprint; }
    public Optional<UUID> minecraftServerKey() { return Optional.ofNullable(minecraftServerKey); }
    public MinecraftServerKeyStatus minecraftServerKeyStatus() { return minecraftServerKeyStatus; }
    public String minecraftServerKeyUnavailableCode() {
        return minecraftServerKeyStatus == MinecraftServerKeyStatus.INVALID
            ? "minecraft_server_key_invalid" : "minecraft_server_key_missing";
    }

    @Override
    public String toString() {
        return "ServerCredentials{source=" + source + ", shardName='" + shardName
            + "', fingerprint='" + fingerprint + "'}";
    }

    private static String fingerprint(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
