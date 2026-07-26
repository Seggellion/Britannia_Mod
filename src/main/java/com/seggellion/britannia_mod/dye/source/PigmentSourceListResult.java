package com.seggellion.britannia_mod.dye.source;

import java.util.List;
import java.util.Objects;

public record PigmentSourceListResult(List<PigmentSourceEntry> entries, PigmentSourceFailure failure) {
    public PigmentSourceListResult {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        Objects.requireNonNull(failure, "failure");
        if (!entries.isEmpty() && failure != PigmentSourceFailure.NONE) {
            throw new IllegalArgumentException("A failed list result cannot contain entries");
        }
    }

    public boolean successful() {
        return failure == PigmentSourceFailure.NONE;
    }
}
