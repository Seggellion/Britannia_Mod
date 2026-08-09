package com.seggellion.britannia_mod.banner.placement;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Runtime-only, server-owned placement preference. Nothing here is persistent item or player data. */
public final class BannerOrientationPreferenceService {
    public record CycleResult(BannerOrientation orientation, boolean changed, boolean onlySupportedMode) {
    }

    private static final Map<UUID, BannerOrientation> PREFERENCES = new HashMap<>();

    private BannerOrientationPreferenceService() {
    }

    public static synchronized BannerOrientation currentNormalized(
            UUID playerId, List<BannerOrientation> supportedOrientations) {
        Objects.requireNonNull(playerId, "playerId");
        List<BannerOrientation> supported = normalizedSupported(supportedOrientations);
        BannerOrientation current = PREFERENCES.get(playerId);
        if (current == null || !supported.contains(current)) {
            current = supported.getFirst();
            PREFERENCES.put(playerId, current);
        }
        return current;
    }

    public static synchronized CycleResult cycle(
            UUID playerId, List<BannerOrientation> supportedOrientations) {
        List<BannerOrientation> supported = normalizedSupported(supportedOrientations);
        BannerOrientation current = currentNormalized(playerId, supported);
        if (supported.size() == 1) {
            return new CycleResult(current, false, true);
        }
        BannerOrientation next = supported.get((supported.indexOf(current) + 1) % supported.size());
        PREFERENCES.put(playerId, next);
        return new CycleResult(next, next != current, false);
    }

    public static synchronized void clear(UUID playerId) {
        PREFERENCES.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    public static synchronized void clearAll() {
        PREFERENCES.clear();
    }

    static synchronized int size() {
        return PREFERENCES.size();
    }

    private static List<BannerOrientation> normalizedSupported(List<BannerOrientation> orientations) {
        Objects.requireNonNull(orientations, "orientations");
        List<BannerOrientation> supported = BannerOrientation.orderedSupported(orientations);
        if (supported.isEmpty()) {
            throw new IllegalArgumentException("At least one placement orientation must be supported");
        }
        return supported;
    }
}
