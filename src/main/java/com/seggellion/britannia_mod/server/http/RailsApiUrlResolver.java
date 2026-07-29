package com.seggellion.britannia_mod.server.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Resolves closed Rails API operations from a validated service origin.
 * Callers supply raw path and query values; this class performs the only encoding pass.
 */
public final class RailsApiUrlResolver {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    private final URI serviceOrigin;

    private RailsApiUrlResolver(URI serviceOrigin) {
        this.serviceOrigin = serviceOrigin;
    }

    public static RailsApiUrlResolver fromConfiguredBase(String configuredBase) {
        return new RailsApiUrlResolver(normalizeServiceOrigin(configuredBase));
    }

    public static RailsApiUrlResolver fromServiceOrigin(URI serviceOrigin) {
        Objects.requireNonNull(serviceOrigin, "serviceOrigin");
        return new RailsApiUrlResolver(normalizeServiceOrigin(serviceOrigin.toString()));
    }

    /**
     * Accepts a service root, with a trailing /api only as a compatibility input, and
     * returns an origin URI with no path, query, user-info, or fragment.
     */
    public static URI normalizeServiceOrigin(String configuredBase) {
        if (configuredBase == null || configuredBase.isBlank()) {
            throw new IllegalArgumentException("API base URL is missing");
        }

        try {
            URI parsed = new URI(configuredBase.trim());
            String scheme = parsed.getScheme() == null
                ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
            String host = parsed.getHost() == null
                ? "" : withoutIpv6Brackets(parsed.getHost()).toLowerCase(Locale.ROOT);

            if (parsed.isOpaque() || !(scheme.equals("http") || scheme.equals("https"))) {
                throw new IllegalArgumentException("API base URL must use HTTP or HTTPS");
            }
            if (host.isEmpty()) {
                throw new IllegalArgumentException("API base URL must include a valid host");
            }
            if (parsed.getRawUserInfo() != null) {
                throw new IllegalArgumentException("API base URL must not include user-info");
            }
            if (parsed.getRawQuery() != null) {
                throw new IllegalArgumentException("API base URL must not include a query");
            }
            if (parsed.getRawFragment() != null) {
                throw new IllegalArgumentException("API base URL must not include a fragment");
            }
            int port = parsed.getPort();
            if (port == 0 || port > 65_535) {
                throw new IllegalArgumentException("API base URL has an invalid port");
            }

            String rawPath = parsed.getRawPath();
            if (rawPath == null) rawPath = "";
            if (!(rawPath.isEmpty() || rawPath.equals("/")
                || rawPath.equals("/api") || rawPath.equals("/api/"))) {
                throw new IllegalArgumentException(
                    "API base URL must be a service origin; endpoint paths are not supported"
                );
            }
            if (scheme.equals("http") && !isExplicitLoopback(host)) {
                throw new IllegalArgumentException("Non-loopback API base URLs must use HTTPS");
            }

            return new URI(scheme, null, host, port, null, null, null);
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("API base URL is invalid", error);
        }
    }

    public URI serviceOrigin() {
        return serviceOrigin;
    }

    public URI resolve(Endpoint endpoint) {
        return resolve(endpoint, Map.of(), Map.of());
    }

    public URI resolvePath(Endpoint endpoint, Map<String, String> pathParameters) {
        return resolve(endpoint, pathParameters, Map.of());
    }

    public URI resolveQuery(Endpoint endpoint, Map<String, String> queryParameters) {
        return resolve(endpoint, Map.of(), queryParameters);
    }

    public URI resolve(Endpoint endpoint, Map<String, String> pathParameters,
                       Map<String, String> queryParameters) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(pathParameters, "pathParameters");
        Objects.requireNonNull(queryParameters, "queryParameters");

        if (!pathParameters.keySet().equals(endpoint.pathParameterNames())) {
            throw new IllegalArgumentException("Path parameters do not match endpoint " + endpoint.symbolicName());
        }
        if (!endpoint.allowedQueryParameters().containsAll(queryParameters.keySet())) {
            throw new IllegalArgumentException("Query parameter is not allowed for endpoint " + endpoint.symbolicName());
        }

        StringBuilder rawPath = new StringBuilder("/api");
        for (String segment : endpoint.templateSegments()) {
            rawPath.append('/');
            if (segment.startsWith(":")) {
                String name = segment.substring(1);
                String value = pathParameters.get(name);
                if (value == null || value.isEmpty()) {
                    throw new IllegalArgumentException("Path parameter is missing for endpoint " + endpoint.symbolicName());
                }
                rawPath.append(encodeComponent(value));
            } else {
                rawPath.append(segment);
            }
        }

        StringJoiner query = new StringJoiner("&");
        queryParameters.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                if (entry.getValue() == null) {
                    throw new IllegalArgumentException(
                        "Query parameter is null for endpoint " + endpoint.symbolicName()
                    );
                }
                query.add(encodeComponent(entry.getKey()) + "=" + encodeComponent(entry.getValue()));
            });

        String rawUri = serviceOrigin.toASCIIString() + rawPath;
        if (query.length() > 0) rawUri += "?" + query;
        try {
            return new URI(rawUri);
        } catch (URISyntaxException impossible) {
            throw new IllegalStateException("Resolved Rails endpoint is invalid", impossible);
        }
    }

    private static boolean isExplicitLoopback(String host) {
        return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1");
    }

    private static String withoutIpv6Brackets(String host) {
        return host.startsWith("[") && host.endsWith("]")
            ? host.substring(1, host.length() - 1) : host;
    }

    private static String encodeComponent(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length);
        for (byte raw : bytes) {
            int valueByte = raw & 0xff;
            if ((valueByte >= 'a' && valueByte <= 'z')
                || (valueByte >= 'A' && valueByte <= 'Z')
                || (valueByte >= '0' && valueByte <= '9')
                || valueByte == '-' || valueByte == '.' || valueByte == '_' || valueByte == '~') {
                encoded.append((char) valueByte);
            } else {
                encoded.append('%')
                    .append(HEX[valueByte >>> 4])
                    .append(HEX[valueByte & 0x0f]);
            }
        }
        return encoded.toString();
    }

    public enum Endpoint {
        WORLD_BOOTSTRAP("world_bootstrap/:shard", "player_uuid", "minecraft_uuid", "minecraft_username", "profile"),
        QUEST_START("quests/:quest_id/start"),
        QUEST_INTERACT("quests/interact"),
        QUEST_TRANSITION("quests/:quest_id/choose"),
        QUEST_TRIGGER("quests/:quest_id/trigger_node"),
        QUEST_ABANDON("quests/:quest_id/abandon"),
        QUEST_QUIT("quests/:quest_state_id/quit"),
        QUEST_JOURNAL("status/:player_uuid"),
        QUEST_CLEAR_ALL("quests/clear_all"),
        QUEST_RECORD_KILL("quests/record_kill"),
        MINECRAFT_VERIFY("minecraft_verifications/verify"),
        CATALOG("catalog", "city", "role", "npc_type", "shard"),
        TRADER_CATALOG("trader_catalog"),
        CITY_COMMODITIES("city_commodities", "city"),
        MERCHANT_PURCHASE("merchant_transactions"),
        TRADER_SALE("trader_transactions"),
        TRANSACTION("transactions"),
        HOUSE_CREATE("houses"),
        HOUSE_DELETE("houses/delete"),
        HOUSE_RENAME("houses/rename"),
        SKILL_CONFIG("skills/config"),
        PLAYER_SKILLS("player_skills", "uuid", "username", "shard"),
        SKILL_GAIN("skills/gain"),
        SKILL_SET("skills/set"),
        BLESSED_ITEMS("blessed_items", "minecraft_uuid"),
        ORE_VEINS("ore_veins", "shard"),
        SHARD_USER_ADJUST_STATS("shard_users/:user_id/adjust_stats"),
        SERVICE_NPC_SPAWN_OPERATIONS("service_npc_spawn_operations"),
        WORLD_STATE_CHANGES("world_state_changes/:shard", "from_version"),
        BANKING_OPEN("banking/open"),
        BANKING_DEPOSIT_PREPARE("banking/deposit/prepare"),
        BANKING_WITHDRAWAL_PREPARE("banking/withdrawal/prepare"),
        BANKING_CURRENCY_DEPOSIT_PREPARE("banking/currency/deposit/prepare"),
        BANKING_CURRENCY_WITHDRAWAL_PREPARE("banking/currency/withdrawal/prepare"),
        BANKING_CHEQUE_ISSUANCE_PREPARE("banking/cheque/issue/prepare"),
        BANKING_CHEQUE_REDEEM("banking/cheque/redeem"),
        BANKING_CONFIRM("banking/confirm"),
        BANKING_CANCEL("banking/cancel"),
        CITY_FOOD_AND_WOOD_SUPPLY("cities/:city/food_and_wood_supply"),
        CITY_FOOD_SUPPLY("cities/:city/food_supply"),
        CITY_TRADE_DATA("cities/:city/trade_data"),
        NPC_CREATE("npcs"),
        NPC_UPSERT("npcs/upsert"),
        NPC_SYNC("npcs/sync"),
        NPC_HEARTBEAT("npcs/:npc_id/heartbeat"),
        NPC_INACTIVE("npcs/:npc_id/inactive"),
        NPC_DESPAWN("npcs/:npc_id/despawn"),
        NPC_DEATH("npcs/:npc_id/death"),
        NPC_STATUS("npcs/:npc_id/status"),
        NPC_DELETE("npcs/:npc_id");

        private final List<String> templateSegments;
        private final Set<String> pathParameterNames;
        private final Set<String> allowedQueryParameters;

        Endpoint(String template, String... allowedQueryParameters) {
            this.templateSegments = List.copyOf(Arrays.asList(template.split("/", -1)));
            Set<String> pathNames = new HashSet<>();
            for (String segment : templateSegments) {
                if (segment.isEmpty()) throw new IllegalArgumentException("Endpoint template contains an empty segment");
                if (segment.startsWith(":")) {
                    String name = segment.substring(1);
                    if (name.isEmpty() || !pathNames.add(name)) {
                        throw new IllegalArgumentException("Endpoint template has an invalid path parameter");
                    }
                } else if (!segment.matches("[A-Za-z0-9._~-]+")) {
                    throw new IllegalArgumentException("Endpoint template has an invalid literal segment");
                }
            }
            this.pathParameterNames = Collections.unmodifiableSet(pathNames);
            this.allowedQueryParameters = Set.copyOf(Arrays.asList(allowedQueryParameters));
        }

        public String symbolicName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public String symbolicPath() {
            return "/api/" + String.join("/", templateSegments);
        }

        private List<String> templateSegments() {
            return templateSegments;
        }

        private Set<String> pathParameterNames() {
            return pathParameterNames;
        }

        private Set<String> allowedQueryParameters() {
            return allowedQueryParameters;
        }
    }
}
