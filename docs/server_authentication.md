# Server authentication

## Ownership and trust

Each Minecraft shard is a pre-provisioned server identity. An authorized operator creates or selects the Rails `Shard`, rotates that shard's `client_secret`, and provisions the public shard name and secret directly to the dedicated-server process. Players and Minecraft clients do not establish shard trust. `/verify <code>` links a player account only.

The credential belongs to exactly one shard. Rails resolves the requested shard name first and then compares the supplied `Shard-Secret` with that shard's stored secret using a constant-time comparison. A secret for one shard cannot authorize another shard. Raw credentials must not appear in packets, client state, world SavedData, commands, logs, exceptions, screenshots, or committed configuration.

## Dedicated-server configuration

The preferred production source is a complete process-environment pair:

```text
ULTIMACRAFT_SHARD_NAME
ULTIMACRAFT_SHARD_SECRET
```

The server checks that pair first. If either variable is present without the other, configuration is invalid and authentication fails closed. The server never combines values from different sources.

Local development may instead use the ignored file `run/server/config/britannia_mod-server.properties`, resolved by the dedicated-server runtime as `config/britannia_mod-server.properties`:

```properties
shard_name=Britannia
shard_secret=<operator-entered secret>
api_base_url=http://127.0.0.1:3000
allow_integrated_server=false
rails_update_listener_enabled=false
```

Do not commit or distribute this file. The parser rejects missing, duplicate, unknown, malformed, or oversized entries. On POSIX systems, restrict it to the server account (for example, mode `0600`). On Windows, restrict its ACL to the server account; the server emits a warning because POSIX permission enforcement is unavailable.

`api_base_url` is not a credential. It is the Rails service origin, not an API endpoint or endpoint prefix. The server appends the single `/api` namespace and the selected closed endpoint automatically. Recommended examples are:

```properties
# Production
api_base_url=https://rails.example.com

# Explicit loopback development
api_base_url=http://127.0.0.1:3000
```

A trailing slash is normalized. Existing values ending in `/api` or `/api/` remain accepted only for compatibility and are normalized back to the service origin, so they never produce `/api/api/`. Do not configure an endpoint path such as `/api/world_bootstrap`; unsupported base paths, queries, user-info, fragments, malformed hosts, and invalid ports fail closed. Production and other non-loopback origins must use HTTPS. Plain HTTP is accepted only for the explicit development hosts `localhost`, `127.0.0.1`, or `::1`.

Dedicated server is the supported production mode. Integrated-server credential loading fails closed by default because client and server share one process. Local loopback development requires the explicit file opt-in `allow_integrated_server=true`; that mode is unsuitable for production and never synchronizes credentials to the client.

## Authenticated request boundary

At server startup, immutable credentials are associated with that `MinecraftServer`. Authenticated Rails requests add only the public `Shard-Name` selector and server-only `Shard-Secret` header. State is cleared on server stop. The diagnostic status command is operator-only:

```text
/britannia_api status
```

It reports source, public shard name, an eight-lowercase-hex SHA-256 prefix, and the last bootstrap result. It never reports a raw value, recoverable suffix, full hash, bearer, or request header. Credential setter commands were removed; rotation requires an operator workflow and a server restart.

Bootstrap HTTP uses a bounded worker executor with a 32-request queue, 5-second connection timeout, 10-second read timeout, 15-second overall timeout, and a 4 MiB response limit. Parsing is pure worker work. City, Service NPC registry, journal, inventory, player state, cache, and packet updates occur only through the logical server thread. Login generations reject completions after logout, a newer login, or server stop. Failed authentication leaves registries unavailable and cannot replace a newer valid result.

The client receives only bounded, sanitized city/type menu state and authoritative quest, journal, catalog, transaction, and display results. Quest and active economy C2S payloads express closed, size-limited player intent. The logical server validates player/entity proximity, identifiers, current journal state, quantities, and product keys before calling Rails. Rails remains authoritative for quest transitions, rewards, catalog data, prices, balances, and transaction outcomes.

The application-wide `britannia_api_token` bearer is absent from NeoForge. Rails retains narrowly scoped, deprecated server-side compatibility only for unrelated callers that have not been migrated; it cannot authenticate the hardened bootstrap, verification, quest, NPC, city, product, or transaction paths.

`/verify <code>` executes HTTP on the logical server under the authenticated shard. The code remains expiring and single-use, is rate-limited by Rails, is filtered from request parameters, and is not retained after the request. It can still remain in the local Minecraft command-history UI; clear that history before sharing a client profile or screenshot.

## Local inbound listener

The legacy Rails update listener is disabled by default. `rails_update_listener_enabled=true` is permitted only in the ignored local file, binds explicitly to loopback, enforces bounded request bodies, compares the configured secret without logging it, and fails closed. It is not a production synchronization channel.

## Manual rotation and coordinated deployment

Stop the Minecraft server before rotating. In an authorized shell, collect replacements without putting them in shell history, export them only to the Rails-console process, and then start `bin/rails console` in `development`. Paste each entire `begin`/`ensure` block as one console input so IRB evaluates only the final `nil` and never echoes an assignment containing the credential:

```ruby
begin
  shard_secret = ENV.delete("ULTIMACRAFT_NEW_SHARD_SECRET") or raise "missing shard replacement"
  shard = Shard.find_by!("LOWER(name) = ?", "Britannia".downcase)
  shard.update!(client_secret: shard_secret)
  nil
ensure
  shard_secret&.replace("0" * shard_secret.bytesize)
end
```

If the deprecated global bearer is still active, rotate it separately in the same authorized session:

```ruby
begin
  legacy_bearer = ENV.delete("ULTIMACRAFT_NEW_LEGACY_BEARER") or raise "missing bearer replacement"
  Setting.set("britannia_api_token", legacy_bearer)
  nil
ensure
  legacy_bearer&.replace("0" * legacy_bearer.bytesize)
end
```

Do not paste replacements into command arguments, chat, tickets, source files, or this document. Exit the console, unset the temporary shell variables, provision the new shard secret into the dedicated server's environment or ignored file, and restart Rails and Minecraft together. Confirm the new credential only by source, eight-hex fingerprint, HTTP result, city count, and registry revision/count. Test rejection of the old value without printing it. Restart any unrelated legacy bearer caller after its separate configuration is updated.

Historical worlds may contain old `CityAPITokenData` bytes. They are intentionally ignored and never migrated. Treat the old sandbox, backups, logs, and screenshots as potentially exposed; rotate first and do not use them as proof of isolation.

## Clean-world verification

After rotation, create a new dedicated-server world; do not reuse `run/saves/sandbox`. Keep the Rails endpoint on loopback for local testing, provision credentials only to the dedicated server, and ensure the client process does not inherit them. Verify authenticated bootstrap using redacted status and counts, connect the client, and confirm the Service NPC menu receives UUID-backed cities and active/spawnable types without a credential payload.

Before resuming Milestone 4 Sections A-D, inspect the new world's NBT, server/client logs, crash reports, and packaged JAR resources for credential absence. Confirm the rotated old credential is rejected. Record only real manual evidence; automated checks do not mark a section passed.

## Explicit deferrals

Short-lived operator pairing, anonymous self-registration, authentication version negotiation, hashed/versioned server credentials, HMAC, timestamps, nonces, and replay protection are deliberately deferred to Milestone 5 or later. This prerequisite does not add banking or spawn-point registration.

## Milestone 4 verification closeout

The localhost hardened-authentication gate passed on 2026-07-16:

- Rails remained bound to loopback and returned HTTP 200 for the dedicated server's compact bootstrap request.
- The dedicated server loaded credentials from `SERVER_FILE`; the client was launched from a credential-sanitized environment.
- `/britannia_api status` exposed only source, shard, an eight-hex fingerprint, and last-bootstrap state. It did not expose a credential.
- Command completion exposed only the `status` subcommand. Removed credential setter commands were unavailable.
- UUID-backed Britain and the active, spawnable `bank_teller` definition reached the server-owned cache and configuration menu.
- The old credential was rejected without printing or recording it.
- Final source, runtime-name, and packaged-artifact scans found no credential values, verification-world data, logs, WorldEdit JAR, or dedicated-server runtime files in the reviewed commit scope.

The diagnostic fingerprint is correlation metadata only; it is not an authentication value and must remain truncated to eight hexadecimal characters. Runtime credential files, worlds, logs, screenshots, NBT evidence, EULA acceptance, `server.properties`, `ops.json`, and `usercache.json` remain ignored and outside the commit.

Milestone 4 manual Sections A-D passed. Section A used an accepted design variance: de-op automatically closed the open menu before a stale Save could be submitted. Normal client flow therefore prevented the unauthorized mutation; the direct server handler's `UNAUTHORIZED` response remains covered by automated tests.
