package com.seggellion.britannia_mod.server.auth;

import java.net.URI;
import java.util.UUID;

/** Test-only construction at the credential package boundary. */
public final class ServerCredentialsTestFactory {
    private ServerCredentialsTestFactory() {}

    public static ServerCredentials create(URI origin, UUID serverKey) {
        return new ServerCredentials(
            "Britannia", "test-only-secret-sentinel", origin,
            ServerCredentials.Source.SERVER_FILE, false, false,
            serverKey == null ? null : serverKey.toString()
        );
    }
}
