# Server Configuration Parity — Britannia (NeoForge) ↔ Atrevion (Fabric)

**One host environment template drives either mod. Only the values change per shard.**

This is the NeoForge half of the contract. The Fabric repository carries the full side-by-side
matrix at `docs/porting/SERVER_CONFIGURATION_PARITY.md`; the two documents describe the same
contract and must be changed together.

**Status: `FULL_OPERATIONAL_CONFIG_PARITY`**, achieved by two repairs on this branch — NF-002
(`58c98c04`, NeoForge now reads `ULTIMACRAFT_API_BASE_URL`) and NF-003 (`6ed719f0`, runtime shard
identity now comes from `ServerCredentials` everywhere, including durable spawn records).

---

## 1. Canonical environment variables

```bash
ULTIMACRAFT_SHARD_NAME=<shard>
ULTIMACRAFT_SHARD_SECRET=<shard-client-secret>                  # secret, server-only
ULTIMACRAFT_MINECRAFT_SERVER_KEY=<minecraft-server-public-id>   # secret, server-only
ULTIMACRAFT_API_BASE_URL=https://<rails-origin>
```

| Variable | Purpose | Required | Secret |
|---|---|---|---|
| `ULTIMACRAFT_SHARD_NAME` | Shard to authenticate as; matches Rails `shards.name` | Paired with the secret | No |
| `ULTIMACRAFT_SHARD_SECRET` | Shard credential (Rails column: `client_secret`) | Paired with the name | **Yes** |
| `ULTIMACRAFT_MINECRAFT_SERVER_KEY` | This server's identity — **is** `minecraft_servers.public_id` | Optional; banking, world sync and NPC spawning need it | **Yes** |
| `ULTIMACRAFT_API_BASE_URL` | Rails origin, scheme + host only | **Required in environment mode** | No |

**Name and secret are a pair.** Supplying one without the other is a hard failure, deliberately: it
stops a half-filled template silently falling through to a file holding different credentials.
The server key and the origin each override only themselves.

**Origin rules.** Scheme and host only — no path, query or fragment. A trailing `/api` or `/api/`
is tolerated and stripped, so older values still work. HTTPS is required for every host except
explicit loopback (`localhost`, `127.0.0.1`, `::1`), which may use `http` for local development.

---

## 2. Server properties file

```
config/britannia_mod-server.properties
```

Same filename, format and keys as Fabric. Resolved relative to the game directory.

```properties
shard_name=Britannia
shard_secret=<REDACTED>
minecraft_server_key=<uuid from the Rails admin>
api_base_url=https://<rails-origin>
allow_integrated_server=false
rails_update_listener_enabled=false
```

Exactly six keys are accepted. An unknown key, a duplicate key, a malformed line or a file over
64 KiB is a hard boot failure — silence would be worse, because a typo'd credential key would
otherwise look like a working configuration. The loader warns if the file is group- or
world-readable (POSIX), and warns that it cannot check on Windows.

`allow_integrated_server` and `rails_update_listener_enabled` are **file-only on both mods** and
default to `false`. An integrated server is refused a trusted session unless the flag is set *and*
the origin is loopback.

---

## 3. Precedence

```
environment  (ULTIMACRAFT_SHARD_NAME + ULTIMACRAFT_SHARD_SECRET both present)
    → the file is not read at all
otherwise: config/britannia_mod-server.properties
    → ULTIMACRAFT_MINECRAFT_SERVER_KEY overrides only the server key
    → ULTIMACRAFT_API_BASE_URL overrides only the origin
otherwise: no credentials — every authenticated call fails closed
```

Identical to Fabric. A blank environment value counts as absent, so an unfilled placeholder falls
through to the file rather than authenticating as the empty shard. Values are trimmed in both
mechanisms. **A change requires a restart**; nothing re-reads the environment or the file at
runtime.

---

## 4. What NF-002 was

Environment mode never read an origin variable. It passed `ModConfig.API_BASE_URL` instead:

```java
public static final String API_BASE_URL = "http://127.0.0.1:3000/api/";
```

That field is `static final`, assigned at its declaration, and `ModConfig.loadConfig()` reads only
`exampleValue` — so **no file, variable or command could change it**. A host configured entirely
by environment variables therefore talked to its own loopback on port 3000 and could not be pointed
at a real backend by any means. Fabric had already been built not to reproduce this.

**The repair:** `API_BASE_URL_ENV` is declared, required in environment mode, and overrides the
file value in file mode. `ServerCredentialSource` no longer references `ModConfig` at all, so the
origin now travels with the credentials that authenticate against it and the two cannot disagree.

**Why it survived review:** the environment-mode test asserted the source enum, the shard name and
the fingerprint — but never the resolved origin. A test that omits the one value a defect corrupts
passes forever. That assertion is now present.

---

## 5. What keeps this true

`CanonicalHostEnvironmentContractTest` pins the four variable names and the file path as **literal
strings**, so renaming a constant is a build failure rather than a silent break of every operator's
template. It drives the published template through environment-only, file-only and
environment-overrides-file, and asserts the missing-origin failure names the variable.

`ServerIdentityComesFromCredentialsTest` fails the build if any main-source class reads
`ModConfig.API_BASE_URL`, authenticates a request addressed from it, or reads `System.getenv`
outside the credential loader. It strips comments and string literals first, so it cannot be
silenced by deleting the javadoc that explains it, and it locates the source tree by walking up
from the runner's working directory — NeoGradle runs tests from `build/test-run/`, and a guard that
cannot find the source it scans would pass vacuously.

**Every Rails URL in main source is built by `RailsApiUrlResolver` from
`ServerCredentials.apiUrls()`** — 39 service classes, covering world bootstrap, skills, quests,
economy and buyback, all nine banking clients, Service-NPC registration and stale-post reporting,
world-state sync, housing persistence, ore veins, guild training, karma and city sync. The only
other URL construction in the repository is a client-side portrait fetch and a website link,
neither of which carries a credential.

---

## 6. NF-003 — fixed

`ModConfig.SHARD_NAME` is a compile-time constant (`"Britannia"`) that nothing assigns from
configuration. Nine call sites across four classes used to read it where the **configured** shard
was meant: the three durable spawn records in `ServiceNpcSpawnBlockEntity`, the sale and purchase
payloads in `ServerEconomyService` and `MerchantEconomyService`, an NPC sync key, and three sites
in `PopulateOresCommand`.

They agreed with the authenticated identity only because the constant happened to equal the real
shard name. All now read `ServerAuthRegistry.shardName(server)`.

**Why the durable half mattered most.** Spawn records are persisted, replayed after a restart and
retried by a delivery processor that rejects any record whose shard does not match the credentials.
A compiled value written there is not one bad request — it is a row on disk that can never be
delivered and is retried forever. The repair is therefore at the point the record is created, not
at the final serializer.

**Fail closed.** A post whose shard is unknown refuses to record durable work
(`CREDENTIALS_UNAVAILABLE`), and the admin command names the variables the operator must set. A
server without credentials could not deliver the work anyway, and a guessed shard is
indistinguishable from a real one once on disk.

**Zero readers, enforced.** `ServerIdentityComesFromCredentialsTest.nothingReadsTheCompiledShardName`
has no allowlist: the permitted count is zero.

**Proved against a non-default shard.** The defect was invisible because every test used the
compiled default. `ServiceNpcSpawnGameTests.durableSpawnWorkCarriesTheConfiguredShardAcrossAReload`
installs credentials naming a different shard and asserts the durable record carries it, does not
contain `"Britannia"`, and still carries it after a save/load round trip.

**Fabric now matches this exactly.** When this section was written Fabric still fell back to its
compiled default with a warning; its release-hardening pass removed that fallback, so both mods
refuse to record durable spawn work when the shard is unknown, and **both report zero executable
readers of a compiled shard name in main source**.

---

## 7. Security rules

- **The shard secret is server-only.** Its accessor is package-private in
  `com.seggellion.britannia_mod.server.auth`, so the type system prevents other packages from
  reading it. Never serialized to a client, never logged — an 8-character SHA-256 fingerprint is
  logged instead.
- **The Minecraft server key is server-only.** It travels as a request header and nothing else.
  When absent, clients that need it return **before opening a socket**.
- **Never commit a real configuration.** Use placeholders in version control.
- **Rotate with `Shard#rotate_client_secret!`** — it keeps a 24-hour dual-accept window — never a
  bare `update!`, which fails every in-flight request at the instant of the change.

---

## 8. Operator conclusion

Take the same `ULTIMACRAFT_*` file to a Britannia NeoForge host or an Atrevion Fabric host and
change only the shard values. Both read the same four variables, from the same sources, in the same
order, with the same validation, falling back to the same file name with the same six keys.
