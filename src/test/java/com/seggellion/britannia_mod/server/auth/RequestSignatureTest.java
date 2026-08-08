package com.seggellion.britannia_mod.server.auth;

import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestSignatureTest {
    // Fixed cross-repo verification vector. This exact (secret, method, path, timestamp, nonce,
    // body) tuple and its resulting signature were independently confirmed valid against real,
    // unmodified Rails code -- Api::RequestSignatureVerifier.call, in a real `bin/rails runner`
    // session against the ultimacraft-website repo (branch feature/service-npc-m14-ops-hardening,
    // commit 9500ce9), not simulated or re-derived from this class's own logic. See this test's
    // own crossRepoVerifiedSignatureMatchesRealRailsValidation() for the exact expected value and
    // how it was produced; the assertion there is what actually locks this in, this comment is
    // just the pointer to the method that does.
    private static final String FIXED_SECRET = "cross-repo-verification-secret-do-not-reuse";
    private static final String FIXED_METHOD = "GET";
    private static final String FIXED_PATH = "/api/world_state_changes/Britannia?from_version=7";
    private static final String FIXED_TIMESTAMP = "1785642134";
    private static final String FIXED_NONCE = "11111111-1111-4111-8111-111111111111";

    @Test
    void canonicalStringMatchesRailsOwnDocumentedShapeExactly() {
        // Api::RequestSignatureVerifier#canonical_string (ultimacraft-website repo):
        //   [@method, @path, @timestamp, @nonce, Digest::SHA256.hexdigest(@body)].join("\n")
        // Reproduced here field-by-field, not just trusted, so a future accidental reordering
        // or delimiter change in RequestSignature's own implementation would break this test
        // even if #compute's overall output still happened to look plausible.
        RequestSignature signature = RequestSignature.computeForTesting(
                FIXED_SECRET, FIXED_METHOD, FIXED_PATH, FIXED_TIMESTAMP, FIXED_NONCE, new byte[0]
        );

        String expectedBodyDigest = sha256Hex(new byte[0]);
        String expectedCanonical = String.join("\n", FIXED_METHOD, FIXED_PATH, FIXED_TIMESTAMP, FIXED_NONCE, expectedBodyDigest);
        String expectedSignature = hmacSha256Hex(FIXED_SECRET, expectedCanonical);

        assertEquals(expectedSignature, signature.signature());
        assertEquals(FIXED_TIMESTAMP, signature.timestamp());
        assertEquals(FIXED_NONCE, signature.nonce());
    }

    /**
     * The real cross-repo proof, actually performed, not hypothetical. This exact signature
     * ({@code b39f436e8f5c99b5cfc80f2c525a20e0473f2a4796fe2b934ce32c6993d7427a}) was produced by
     * running exactly the inputs this test uses through {@link RequestSignature#computeForTesting}
     * (with {@code FIXED_TIMESTAMP} set to a real, then-current Unix timestamp,
     * {@code 1785642134}, so it would pass Rails' own clock-skew check -- the literal value is
     * otherwise inert on the Java side, which does no time-based comparison of its own), then
     * feeding the printed value into a real, unmodified Rails runner session against the
     * ultimacraft-website repo, {@code feature/service-npc-m14-ops-hardening} @ {@code 9500ce9}:
     * <pre>
     * shard = Shard.new(name: "Britannia", client_secret: "cross-repo-verification-secret-do-not-reuse")
     * result = Api::RequestSignatureVerifier.call(
     *   shard: shard, method: "GET", path: "/api/world_state_changes/Britannia?from_version=7",
     *   timestamp: "1785642134", nonce: "11111111-1111-4111-8111-111111111111",
     *   body: "", signature: "b39f436e8f5c99b5cfc80f2c525a20e0473f2a4796fe2b934ce32c6993d7427a"
     * )
     * result.status  # =&gt; :valid
     * result.valid?  # =&gt; true
     * </pre>
     * confirmed for real, at the moment of verification (Rails-side {@code Time.current.to_i}
     * was {@code 1785642123}, 11 seconds before the signed timestamp -- comfortably inside the
     * 90-second window). This is not this class re-checking its own arithmetic -- it is Rails'
     * own real HMAC computation and comparison, against Java's real output, agreeing. If this
     * test's literal expected value ever needs to change (e.g. a deliberate future change to the
     * canonical string format on either side), it must be re-verified against real Rails code
     * the same way, not just updated to whatever this class currently computes -- a match
     * against itself proves nothing about cross-repo correctness.
     */
    @Test
    void crossRepoVerifiedSignatureMatchesRealRailsValidation() {
        RequestSignature signature = RequestSignature.computeForTesting(
                FIXED_SECRET, FIXED_METHOD, FIXED_PATH, FIXED_TIMESTAMP, FIXED_NONCE, new byte[0]
        );

        assertEquals(
                "b39f436e8f5c99b5cfc80f2c525a20e0473f2a4796fe2b934ce32c6993d7427a",
                signature.signature(),
                "this literal is the value independently confirmed valid by real Rails code -- see this test's own javadoc for the exact session"
        );
    }

    @Test
    void differentNoncesForOtherwiseIdenticalRequestsProduceDifferentSignatures() {
        RequestSignature first = RequestSignature.computeForTesting(
                FIXED_SECRET, FIXED_METHOD, FIXED_PATH, FIXED_TIMESTAMP, "11111111-1111-4111-8111-111111111111", new byte[0]);
        RequestSignature second = RequestSignature.computeForTesting(
                FIXED_SECRET, FIXED_METHOD, FIXED_PATH, FIXED_TIMESTAMP, "22222222-2222-4222-8222-222222222222", new byte[0]);

        assertNotEquals(first.signature(), second.signature());
    }

    @Test
    void aChangedBodyProducesADifferentSignatureEvenWithIdenticalMethodPathTimestampAndNonce() {
        RequestSignature emptyBody = RequestSignature.computeForTesting(
                FIXED_SECRET, "POST", FIXED_PATH, FIXED_TIMESTAMP, FIXED_NONCE, new byte[0]);
        RequestSignature realBody = RequestSignature.computeForTesting(
                FIXED_SECRET, "POST", FIXED_PATH, FIXED_TIMESTAMP, FIXED_NONCE,
                "{\"from_version\":7}".getBytes(StandardCharsets.UTF_8));

        assertNotEquals(emptyBody.signature(), realBody.signature());
    }

    @Test
    void aDifferentShardSecretProducesADifferentSignatureForTheIdenticalRequest() {
        RequestSignature mine = RequestSignature.computeForTesting(
                FIXED_SECRET, FIXED_METHOD, FIXED_PATH, FIXED_TIMESTAMP, FIXED_NONCE, new byte[0]);
        RequestSignature theirs = RequestSignature.computeForTesting(
                "a-completely-different-secret", FIXED_METHOD, FIXED_PATH, FIXED_TIMESTAMP, FIXED_NONCE, new byte[0]);

        assertNotEquals(mine.signature(), theirs.signature());
    }

    @Test
    void signatureIsLowercaseHexOfTheExpectedHmacSha256Length() {
        RequestSignature signature = RequestSignature.computeForTesting(
                FIXED_SECRET, FIXED_METHOD, FIXED_PATH, FIXED_TIMESTAMP, FIXED_NONCE, new byte[0]);

        // HMAC-SHA256 is a 32-byte digest -> 64 lowercase hex characters, matching what
        // Api::RequestSignatureVerifier expects (it downcases the received value before
        // comparing, but this client should already send the canonical lowercase form).
        assertEquals(64, signature.signature().length());
        assertTrue(signature.signature().matches("[0-9a-f]+"), "expected lowercase hex, got: " + signature.signature());
    }

    @Test
    void computeGeneratesAFreshTimestampAndUuidNonceEveryCall() {
        ServerCredentials credentials = ServerCredentialsTestFactory.create(URI.create("http://127.0.0.1"), null);

        RequestSignature first = RequestSignature.compute(credentials, "GET", FIXED_PATH, new byte[0]);
        RequestSignature second = RequestSignature.compute(credentials, "GET", FIXED_PATH, new byte[0]);

        assertNotEquals(first.nonce(), second.nonce(), "every real signed request must use a fresh nonce");
        assertTrue(first.nonce().matches("[0-9a-f-]{36}"), "expected a standard UUID string nonce, got: " + first.nonce());
        assertTrue(Long.parseLong(first.timestamp()) > 0);
    }

    @Test
    void applyToSetsAllThreeHeadersOnTheConnection() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://127.0.0.1:1/api/skills/config").toURL().openConnection();
        RequestSignature signature = RequestSignature.computeForTesting(
                FIXED_SECRET, FIXED_METHOD, FIXED_PATH, FIXED_TIMESTAMP, FIXED_NONCE, new byte[0]);

        signature.applyTo(connection);

        assertEquals(FIXED_TIMESTAMP, connection.getRequestProperty(RequestSignature.TIMESTAMP_HEADER));
        assertEquals(FIXED_NONCE, connection.getRequestProperty(RequestSignature.NONCE_HEADER));
        assertEquals(signature.signature(), connection.getRequestProperty(RequestSignature.SIGNATURE_HEADER));
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String hmacSha256Hex(String key, String message) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
