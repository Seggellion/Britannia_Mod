package com.seggellion.britannia_mod.city;

import java.util.Objects;
import java.util.UUID;

public record BootstrapCityDefinition(UUID publicId, String displayName) {
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
    }
}
