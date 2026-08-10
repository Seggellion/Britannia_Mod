package com.seggellion.britannia_mod.city;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One city from the world bootstrap.
 *
 * <h2>{@code supplies}</h2>
 * Guildmaster milestone 2. The city's current commodity levels, keyed by the same plain commodity
 * names Rails' {@code CityStaffing::EconomicEligibility::SUPPLY_COLUMNS} uses ({@code food},
 * {@code silver}, {@code alcohol}, …) — not the {@code *_supply} column names. The spawn screen
 * reads these to show an admin why a Guildmaster is or is not being staffed.
 *
 * <p>Empty for a Rails build that predates the milestone, and empty is deliberately distinguishable
 * from "everything is zero": {@link com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnEligibility}
 * reports UNKNOWN for a missing figure rather than treating absence as a failed requirement.
 *
 * <p>These are a display snapshot, nothing more. City supplies move continuously on the Rails side
 * and this copy is only as fresh as the last bootstrap; no decision on this side may be made from
 * it.
 */
public record BootstrapCityDefinition(UUID publicId, String displayName, Map<String, Double> supplies) {
    public static final int MAX_DISPLAY_NAME_BYTES = 128;

    public BootstrapCityDefinition {
        Objects.requireNonNull(publicId, "publicId");
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (displayName.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_DISPLAY_NAME_BYTES) {
            throw new IllegalArgumentException("displayName is too long");
        }
        supplies = Map.copyOf(Objects.requireNonNull(supplies, "supplies"));
    }

    /**
     * The pre-Guildmaster shape, with no supply figures. Kept as a real overload so the seven
     * existing construction sites -- none of which are about city economics -- stay untouched.
     */
    public BootstrapCityDefinition(UUID publicId, String displayName) {
        this(publicId, displayName, Map.of());
    }
}
