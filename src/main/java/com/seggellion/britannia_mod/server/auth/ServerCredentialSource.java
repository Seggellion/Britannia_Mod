package com.seggellion.britannia_mod.server.auth;

import com.seggellion.britannia_mod.server.http.RailsApiUrlResolver;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads server credentials from the environment or {@code config/britannia_mod-server.properties}.
 *
 * <p>All four canonical host variables are read here, and this is the only place any of them is
 * read. The origin travels with the credentials that authenticate against it, so the two can never
 * disagree — a request cannot be addressed to one host while carrying another host's secret.
 *
 * <p>{@link #API_BASE_URL_ENV} used to be absent (NF-002): environment mode substituted
 * {@code ModConfig.API_BASE_URL}, which is a {@code static final} constant pointing at
 * {@code http://127.0.0.1:3000/api/} that nothing ever assigns from configuration. A host
 * configured entirely by environment variables therefore talked to its own loopback and no
 * operator could change it. It is now required in environment mode and overrides the file value
 * in file mode, exactly as {@link #MINECRAFT_SERVER_KEY_ENV} already did for its own value.
 */
public final class ServerCredentialSource {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String SHARD_NAME_ENV = "ULTIMACRAFT_SHARD_NAME";
    public static final String SHARD_SECRET_ENV = "ULTIMACRAFT_SHARD_SECRET";
    public static final String MINECRAFT_SERVER_KEY_ENV = "ULTIMACRAFT_MINECRAFT_SERVER_KEY";
    public static final String API_BASE_URL_ENV = "ULTIMACRAFT_API_BASE_URL";
    public static final Path RELATIVE_SERVER_FILE = Path.of("config", "britannia_mod-server.properties");
    private static final long MAX_FILE_BYTES = 65_536L;
    private static final Set<String> ALLOWED_KEYS = Set.of(
        "shard_name", "shard_secret", "api_base_url", "minecraft_server_key",
        "allow_integrated_server", "rails_update_listener_enabled"
    );

    private ServerCredentialSource() {}

    public static Optional<ServerCredentials> load(Path gameDirectory) throws CredentialConfigurationException {
        return load(gameDirectory, System.getenv());
    }

    static Optional<ServerCredentials> load(Path gameDirectory, Map<String, String> environment)
        throws CredentialConfigurationException {
        String environmentName = normalized(environment.get(SHARD_NAME_ENV));
        String environmentSecret = normalized(environment.get(SHARD_SECRET_ENV));
        String environmentBaseUrl = normalized(environment.get(API_BASE_URL_ENV));
        boolean namePresent = !environmentName.isEmpty();
        boolean secretPresent = !environmentSecret.isEmpty();
        if (namePresent != secretPresent) {
            throw new CredentialConfigurationException(
                "Environment credentials are incomplete; both canonical variables are required"
            );
        }
        if (namePresent) {
            if (environmentBaseUrl.isEmpty()) {
                throw new CredentialConfigurationException(
                    "Environment credentials require " + API_BASE_URL_ENV + " to be set"
                );
            }
            return Optional.of(new ServerCredentials(
                validateName(environmentName), validateSecret(environmentSecret),
                validateApiBaseUrl(environmentBaseUrl),
                ServerCredentials.Source.ENVIRONMENT, false, false,
                environment.get(MINECRAFT_SERVER_KEY_ENV)
            ));
        }

        Path root = gameDirectory.toAbsolutePath().normalize();
        Path credentialFile = root.resolve(RELATIVE_SERVER_FILE).normalize();
        if (!credentialFile.startsWith(root)) {
            throw new CredentialConfigurationException("Server credential path escaped the game directory");
        }
        if (!Files.exists(credentialFile)) return Optional.empty();

        checkFileLocation(root, credentialFile);
        checkPermissions(credentialFile);
        Map<String, String> values = readProperties(credentialFile);
        String environmentServerKey = normalized(environment.get(MINECRAFT_SERVER_KEY_ENV));
        String configuredServerKey = environmentServerKey.isEmpty()
            ? values.get("minecraft_server_key") : environmentServerKey;
        String configuredBaseUrl = environmentBaseUrl.isEmpty()
            ? required(values, "api_base_url") : environmentBaseUrl;
        return Optional.of(new ServerCredentials(
            validateName(required(values, "shard_name")),
            validateSecret(required(values, "shard_secret")),
            validateApiBaseUrl(configuredBaseUrl),
            ServerCredentials.Source.SERVER_FILE,
            parseBoolean(values, "allow_integrated_server", false),
            parseBoolean(values, "rails_update_listener_enabled", false), configuredServerKey
        ));
    }

    private static Map<String, String> readProperties(Path file) throws CredentialConfigurationException {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) {
                throw new CredentialConfigurationException("Server credential file is missing, invalid, or too large");
            }
            List<String> lines = Files.readAllLines(file);
            Map<String, String> result = new HashMap<>();
            Set<String> seen = new HashSet<>();
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index).trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue;
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    throw new CredentialConfigurationException("Invalid server credential entry on line " + (index + 1));
                }
                String key = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(separator + 1).trim();
                if (!ALLOWED_KEYS.contains(key)) {
                    throw new CredentialConfigurationException("Unknown server credential key: " + key);
                }
                if (!seen.add(key)) {
                    throw new CredentialConfigurationException("Duplicate server credential key: " + key);
                }
                result.put(key, value);
            }
            return result;
        } catch (IOException error) {
            throw new CredentialConfigurationException("Unable to read the server credential file", error);
        }
    }

    private static void checkFileLocation(Path root, Path file) throws CredentialConfigurationException {
        try {
            Path realRoot = root.toRealPath();
            Path realFile = file.toRealPath();
            if (!realFile.startsWith(realRoot)) {
                throw new CredentialConfigurationException("Server credential file escaped the game directory");
            }
        } catch (IOException error) {
            throw new CredentialConfigurationException("Unable to resolve the server credential file", error);
        }
    }

    private static void checkPermissions(Path file) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
            boolean exposed = permissions.contains(PosixFilePermission.GROUP_READ)
                || permissions.contains(PosixFilePermission.GROUP_WRITE)
                || permissions.contains(PosixFilePermission.OTHERS_READ)
                || permissions.contains(PosixFilePermission.OTHERS_WRITE);
            if (exposed) {
                LOGGER.warn("Server credential file permissions allow group/other access; restrict the file to the server account");
            }
        } catch (UnsupportedOperationException unsupported) {
            LOGGER.warn("POSIX credential-file permissions are unavailable on this platform; verify the Windows ACL permits only the server account");
        } catch (IOException error) {
            LOGGER.warn("Unable to inspect server credential file permissions; verify access is restricted to the server account");
        }
    }

    private static String required(Map<String, String> values, String key) throws CredentialConfigurationException {
        String value = values.getOrDefault(key, "").trim();
        if (value.isEmpty()) throw new CredentialConfigurationException("Missing required server credential key: " + key);
        return value;
    }

    private static boolean parseBoolean(Map<String, String> values, String key, boolean defaultValue)
        throws CredentialConfigurationException {
        String value = values.get(key);
        if (value == null || value.isBlank()) return defaultValue;
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        throw new CredentialConfigurationException("Invalid boolean server credential key: " + key);
    }

    private static String validateName(String value) throws CredentialConfigurationException {
        String normalized = normalized(value);
        if (normalized.isEmpty() || normalized.length() > 100 || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new CredentialConfigurationException("Shard name is missing or invalid");
        }
        return normalized;
    }

    private static String validateSecret(String value) throws CredentialConfigurationException {
        String normalized = normalized(value);
        if (normalized.isEmpty() || normalized.length() > 512 || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new CredentialConfigurationException("Shard secret is missing or invalid");
        }
        return normalized;
    }

    private static URI validateApiBaseUrl(String value) throws CredentialConfigurationException {
        try {
            return RailsApiUrlResolver.normalizeServiceOrigin(value);
        } catch (IllegalArgumentException error) {
            throw new CredentialConfigurationException("API base URL is invalid", error);
        }
    }

    private static String normalized(String value) { return value == null ? "" : value.trim(); }
}
