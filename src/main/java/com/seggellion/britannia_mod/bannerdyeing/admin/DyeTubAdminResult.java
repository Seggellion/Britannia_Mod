package com.seggellion.britannia_mod.bannerdyeing.admin;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

public record DyeTubAdminResult(Optional<ItemStack> stack, DyeTubAdminFailure failure, String diagnosticId) {
    public DyeTubAdminResult {
        stack = Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(failure, "failure");
        diagnosticId = Objects.requireNonNull(diagnosticId, "diagnosticId");
        if (stack.isPresent() != (failure == DyeTubAdminFailure.NONE)) {
            throw new IllegalArgumentException("Successful dye-tub result requires a stack");
        }
    }

    public static DyeTubAdminResult success(ItemStack stack) {
        return new DyeTubAdminResult(Optional.of(stack), DyeTubAdminFailure.NONE, "");
    }

    public static DyeTubAdminResult failure(DyeTubAdminFailure failure, String id) {
        return new DyeTubAdminResult(Optional.empty(), failure, id);
    }

    public boolean successful() {
        return failure == DyeTubAdminFailure.NONE;
    }
}
