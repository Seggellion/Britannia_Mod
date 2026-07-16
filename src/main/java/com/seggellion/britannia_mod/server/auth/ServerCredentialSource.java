package com.seggellion.britannia_mod.server.auth;

import com.seggellion.britannia_mod.config.ModConfig;
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

public final class ServerCredentialSource {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String SHARD_NAME_ENV = "ULTIMACRAFT_SHARD_NAME";
    public static final String SHARD_SECRET_ENV = "ULTIMACRAFT_SHARD_SECRET";
    public static final Path RELATIVE_SERVER_FILE = Path.of("config", "britannia_mod-server.properties");
    private static final long MAX_FILE_BYTES = 65_536L;
    private static final Set<String> ALLOWED_KEYS = Set.of(
        "shard_name", "shard_secret", "api_base_url", "allow_integrated_server", "rails_update_listener_enabled"
    );

    private ServerCredentialSource() {}

    public static Optional<ServerCredentials> load(Path gameDirectory) throws CredentialConfigurationException {
        return load(gameDirectory, System.getenv());
    }

    static Optional<ServerCredentials> load(Path gameDirectory, Map<String, String> environment)
        throws CredentialConfigurationException {
        String environmentName = normalized(environment.get(SHARD_NAME_ENV));
        String environmentSecret = normalized(environment.get(SHARD_SECRET_ENV));
        boolean namePresent = !environmentName.isEmpty();
        boolean secretPresent = !environmentSecret.isEmpty();
        if (namePresent != secretPresent) {
            throw new CredentialConfigurationException(
                "Environment credentials are incomplete; both canonical variables are required"
            );
        }
        if (namePresent) {
            return Optional.of(new ServerCredentials(
                validateName(environmentName), validateSecret(environmentSecret), validateApiBaseUrl(ModConfig.API_BASE_URL),
                ServerCredentials.Source.ENVIRONMENT, false, false
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
        return Optional.of(new ServerCredentials(
            validateName(required(values, "shard_name")),
            validateSecret(required(values, "shard_secret")),
            validateApiBaseUrl(required(values, "api_base_url")),
            ServerCredentials.Source.SERVER_FILE,
            parseBoolean(values, "allow_integrated_server", false),
            parseBoolean(values, "rails_update_listener_enabled", false)
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
