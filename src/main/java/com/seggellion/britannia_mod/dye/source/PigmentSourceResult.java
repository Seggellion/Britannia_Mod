package com.seggellion.britannia_mod.dye.source;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

public record PigmentSourceResult(Optional<ItemStack> stack, PigmentSourceFailure failure) {
    public PigmentSourceResult {
        stack = Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(failure, "failure");
        if (stack.isPresent() != (failure == PigmentSourceFailure.NONE)) {
            throw new IllegalArgumentException("Success requires a stack and failures require no stack");
        }
    }

    public static PigmentSourceResult success(ItemStack stack) {
        return new PigmentSourceResult(Optional.of(stack), PigmentSourceFailure.NONE);
    }

    public static PigmentSourceResult failure(PigmentSourceFailure failure) {
        return new PigmentSourceResult(Optional.empty(), failure);
    }

    public boolean successful() {
        return stack.isPresent();
    }
}
