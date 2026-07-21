package com.seggellion.britannia_mod.dye.preview;

import com.seggellion.britannia_mod.dye.service.MatchType;
import java.util.Objects;
import java.util.Optional;

/** Server-owned, display-only projection. It is never accepted back as mutation authority. */
public record DyePreviewDisplayData(
        String bannerNameKey,
        String materialNameKey,
        String mountNameKey,
        String currentColourNameKey,
        Optional<String> currentPigmentNameKey,
        String tubPigmentNameKey,
        String newColourNameKey,
        MatchType matchType,
        double perceptualDistance,
        int currentSrgb,
        int newSrgb,
        boolean placeholder,
        boolean provisionalDimensions) {
    public DyePreviewDisplayData {
        bannerNameKey = requireKey(bannerNameKey, "bannerNameKey");
        materialNameKey = requireKey(materialNameKey, "materialNameKey");
        mountNameKey = requireKey(mountNameKey, "mountNameKey");
        currentColourNameKey = requireKey(currentColourNameKey, "currentColourNameKey");
        currentPigmentNameKey = Objects.requireNonNull(currentPigmentNameKey, "currentPigmentNameKey");
        currentPigmentNameKey.ifPresent(key -> requireKey(key, "currentPigmentNameKey"));
        tubPigmentNameKey = requireKey(tubPigmentNameKey, "tubPigmentNameKey");
        newColourNameKey = requireKey(newColourNameKey, "newColourNameKey");
        Objects.requireNonNull(matchType, "matchType");
        if (!Double.isFinite(perceptualDistance) || perceptualDistance < 0.0) {
            throw new IllegalArgumentException("perceptualDistance must be finite and non-negative");
        }
        currentSrgb &= 0xFFFFFF;
        newSrgb &= 0xFFFFFF;
    }

    private static String requireKey(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
