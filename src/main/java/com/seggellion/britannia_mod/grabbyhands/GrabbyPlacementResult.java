package com.seggellion.britannia_mod.grabbyhands;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/** What one placement attempt did. */
public record GrabbyPlacementResult(
        GrabbyPlacementOutcome outcome,
        @Nullable BlockPos placedAt,
        int worldObjectsPlaced
) {
    public GrabbyPlacementResult {
        Objects.requireNonNull(outcome, "outcome");
        if (worldObjectsPlaced < 0 || worldObjectsPlaced > 1) {
            throw new IllegalArgumentException("A placement transaction affects at most one object");
        }
        if (outcome.placedSomething() != (worldObjectsPlaced == 1)) {
            throw new IllegalArgumentException(
                    "Outcome " + outcome + " disagrees with a placed count of " + worldObjectsPlaced);
        }
        if (worldObjectsPlaced == 1 && placedAt == null) {
            throw new IllegalArgumentException("A placed object must report where it landed");
        }
        placedAt = placedAt == null ? null : placedAt.immutable();
    }

    static GrabbyPlacementResult refused(GrabbyPlacementOutcome outcome) {
        return new GrabbyPlacementResult(outcome, null, 0);
    }

    public Optional<BlockPos> position() {
        return Optional.ofNullable(placedAt);
    }

    public boolean succeeded() {
        return outcome == GrabbyPlacementOutcome.SUCCESS;
    }
}
