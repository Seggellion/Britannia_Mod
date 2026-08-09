package com.seggellion.britannia_mod.bannerdyeing.admin;

import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

public record BannerAdminResult(
        Optional<ItemStack> stack,
        Optional<BannerInstanceState> state,
        BannerAdminFailure failure,
        String diagnosticId) {
    public BannerAdminResult {
        stack = Objects.requireNonNull(stack, "stack");
        state = Objects.requireNonNull(state, "state");
        Objects.requireNonNull(failure, "failure");
        diagnosticId = Objects.requireNonNull(diagnosticId, "diagnosticId");
        if (stack.isPresent() != (failure == BannerAdminFailure.NONE) || stack.isPresent() != state.isPresent()) {
            throw new IllegalArgumentException("Successful banner result requires both stack and state");
        }
    }

    public static BannerAdminResult success(ItemStack stack, BannerInstanceState state) {
        return new BannerAdminResult(Optional.of(stack), Optional.of(state), BannerAdminFailure.NONE, "");
    }

    public static BannerAdminResult failure(BannerAdminFailure failure, String diagnosticId) {
        return new BannerAdminResult(Optional.empty(), Optional.empty(), failure, diagnosticId);
    }

    public boolean successful() {
        return failure == BannerAdminFailure.NONE;
    }
}
