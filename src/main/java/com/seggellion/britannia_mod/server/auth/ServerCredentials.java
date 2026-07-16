package com.seggellion.britannia_mod.server.auth;

import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class ServerCredentials {
    public enum Source { ENVIRONMENT, SERVER_FILE }

    private final String shardName;
    private final String shardSecret;
    private final URI serviceOrigin;
    private final RailsApiUrlResolver apiUrls;
    private final Source source;
    private final boolean integratedServerAllowed;
    private final boolean railsUpdateListenerEnabled;
    private final String fingerprint;

    ServerCredentials(String shardName, String shardSecret, URI serviceOrigin, Source source,
                      boolean integratedServerAllowed, boolean railsUpdateListenerEnabled) {
        this.shardName = Objects.requireNonNull(shardName, "shardName");
        this.shardSecret = Objects.requireNonNull(shardSecret, "shardSecret");
        this.serviceOrigin = Objects.requireNonNull(serviceOrigin, "serviceOrigin");
        this.apiUrls = RailsApiUrlResolver.fromServiceOrigin(serviceOrigin);
        this.source = Objects.requireNonNull(source, "source");
        this.integratedServerAllowed = integratedServerAllowed;
        this.railsUpdateListenerEnabled = railsUpdateListenerEnabled;
        this.fingerprint = fingerprint(shardSecret);
    }

    public String shardName() { return shardName; }
    String shardSecret() { return shardSecret; }
    public URI serviceOrigin() { return serviceOrigin; }
    public RailsApiUrlResolver apiUrls() { return apiUrls; }
    public Source source() { return source; }
    public boolean integratedServerAllowed() { return integratedServerAllowed; }
    public boolean railsUpdateListenerEnabled() { return railsUpdateListenerEnabled; }
    public String fingerprint() { return fingerprint; }

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
