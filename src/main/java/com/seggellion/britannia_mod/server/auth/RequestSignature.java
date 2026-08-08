package com.seggellion.britannia_mod.server.auth;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Milestone 14 Security Slice 2: computes an HMAC-SHA256 request signature matching the real,
 * current Rails contract exactly -- {@code Api::RequestSignatureVerifier}
 * (ultimacraft-website repo, {@code app/services/api/request_signature_verifier.rb}) and the
 * six dated decisions in that repo's {@code docs/server_authentication.md}. Deliberately in
 * this package, not {@code RailsRequestAuthenticator} itself, so it can read
 * {@link ServerCredentials#shardSecret()} directly (package-private -- the secret is never
 * exposed outside {@code server.auth} even to this new feature) while keeping
 * {@code RailsRequestAuthenticator}'s own scope narrowly "attach headers to a connection," not
 * "know how to compute a signature."
 *
 * <p>Canonical string, verified byte-for-byte against Rails' own real, unmodified
 * {@code Api::RequestSignatureVerifier#canonical_string} -- not re-derived from the Rails-side
 * doc's prose alone -- see this class's own test for the exact cross-repo verification method
 * and the fixed input/output pair it was checked against:
 * {@code method + "\n" + rawPathAndQuery + "\n" + timestamp + "\n" + nonce + "\n" +
 * sha256Hex(body)}, HMAC-SHA256'd with the shard's {@code client_secret} as the key, hex-encoded.
 *
 * <p>{@code rawPathAndQuery} must be exactly the percent-encoded path+query string as it will
 * appear on the wire (e.g. {@code HttpURLConnection#getURL()#getFile()}), not a re-decoded or
 * independently-reconstructed one: Rails' own {@code request.fullpath} reads the raw,
 * as-received {@code PATH_INFO}/{@code QUERY_STRING} without re-decoding them (confirmed
 * directly against this app's real Rack version, not assumed from Rack's general reputation for
 * CGI-style PATH_INFO decoding, which this version does not actually do) -- any client-side
 * decoding here would silently and permanently break signing for a path segment or query value
 * containing a character that needed percent-encoding in the first place.
 */
public final class RequestSignature {
    public static final String TIMESTAMP_HEADER = "X-Signature-Timestamp";
    public static final String NONCE_HEADER = "X-Signature-Nonce";
    public static final String SIGNATURE_HEADER = "X-Signature";

    private final String timestamp;
    private final String nonce;
    private final String signature;

    private RequestSignature(String timestamp, String nonce, String signature) {
        this.timestamp = timestamp;
        this.nonce = nonce;
        this.signature = signature;
    }

    /**
     * A fresh timestamp (current wall-clock second, matching Rails' own {@code Time.current.to_i}
     * comparison) and a fresh, randomly-generated nonce (a standard UUID string -- 36 characters
     * of {@code [0-9a-f-]}, comfortably inside Rails' own accepted 8-128 character,
     * {@code [A-Za-z0-9-]} nonce shape) are generated for every call; this method must never be
     * used to sign more than one real request with the same nonce, or Rails' own replay
     * protection will correctly reject the second one.
     */
    public static RequestSignature compute(ServerCredentials credentials, String method, String rawPathAndQuery, byte[] body) {
        Objects.requireNonNull(credentials, "credentials");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(rawPathAndQuery, "rawPathAndQuery");
        byte[] safeBody = body == null ? new byte[0] : body;

        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String canonical = canonicalString(method, rawPathAndQuery, timestamp, nonce, safeBody);
        String signature = hmacSha256Hex(credentials.shardSecret(), canonical);
        return new RequestSignature(timestamp, nonce, signature);
    }

    /** Test/verification-only entry point taking every input explicitly, including a fixed
     * timestamp and nonce, so a specific canonical string and signature can be reproduced
     * exactly -- both for hand/cross-repo verification and so a test can assert the real
     * canonical string shape, not just that some signature was produced. */
    static RequestSignature computeForTesting(String shardSecret, String method, String rawPathAndQuery,
            String timestamp, String nonce, byte[] body) {
        byte[] safeBody = body == null ? new byte[0] : body;
        String canonical = canonicalString(method, rawPathAndQuery, timestamp, nonce, safeBody);
        String signature = hmacSha256Hex(shardSecret, canonical);
        return new RequestSignature(timestamp, nonce, signature);
    }

    private static String canonicalString(String method, String rawPathAndQuery, String timestamp, String nonce, byte[] body) {
        return String.join("\n",
                method.toUpperCase(Locale.ROOT), rawPathAndQuery, timestamp, nonce, sha256Hex(body));
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String hmacSha256Hex(String key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("HmacSHA256 is unavailable", impossible);
        } catch (InvalidKeyException invalidKey) {
            throw new IllegalStateException("Shard secret is not a valid HMAC key", invalidKey);
        }
    }

    public void applyTo(HttpURLConnection connection) {
        connection.setRequestProperty(TIMESTAMP_HEADER, timestamp);
        connection.setRequestProperty(NONCE_HEADER, nonce);
        connection.setRequestProperty(SIGNATURE_HEADER, signature);
    }

    public String timestamp() {
        return timestamp;
    }

    public String nonce() {
        return nonce;
    }

    public String signature() {
        return signature;
    }
}
