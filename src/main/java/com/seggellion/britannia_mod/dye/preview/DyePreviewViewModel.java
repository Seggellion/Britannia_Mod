package com.seggellion.britannia_mod.dye.preview;

import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Testable client advisory state; the server remains authoritative for expiry and application. */
public final class DyePreviewViewModel {
    private final UUID sessionId;
    private final DyePreviewDisplayData displayData;
    private final long expiresAtMillis;
    private final LongSupplier clock;
    private boolean confirmationInFlight;
    private boolean serverInvalid;

    public DyePreviewViewModel(
            UUID sessionId, DyePreviewDisplayData displayData, long lifetimeMillis, LongSupplier clock) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.displayData = Objects.requireNonNull(displayData, "displayData");
        if (lifetimeMillis <= 0) {
            throw new IllegalArgumentException("lifetimeMillis must be positive");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
        this.expiresAtMillis = clock.getAsLong() + lifetimeMillis;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public DyePreviewDisplayData displayData() {
        return displayData;
    }

    public boolean expired() {
        return clock.getAsLong() >= expiresAtMillis;
    }

    public boolean applyEnabled() {
        return !confirmationInFlight && !serverInvalid && !expired();
    }

    public boolean beginConfirmation() {
        if (!applyEnabled()) {
            return false;
        }
        confirmationInFlight = true;
        return true;
    }

    public void markServerInvalid() {
        serverInvalid = true;
    }

    public boolean confirmationInFlight() {
        return confirmationInFlight;
    }

    public String matchLabelKey() {
        return switch (displayData.matchType()) {
            case EXPLICIT_MAPPING -> "screen.britannia_mod.dye_preview.exact_match";
            case NEAREST_COLOUR -> "screen.britannia_mod.dye_preview.closest_match";
            case NATURAL -> "screen.britannia_mod.dye_preview.exact_match";
        };
    }
}
