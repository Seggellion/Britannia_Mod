package com.seggellion.britannia_mod.banner.item;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

public record BannerItemFactoryResult(Optional<ItemStack> stack, Optional<BannerStateIssue> failure) {
    public BannerItemFactoryResult {
        stack = Objects.requireNonNull(stack, "stack");
        failure = Objects.requireNonNull(failure, "failure");
        if (stack.isPresent() == failure.isPresent()) {
            throw new IllegalArgumentException("Exactly one of stack or failure must be present");
        }
    }

    public static BannerItemFactoryResult success(ItemStack stack) {
        return new BannerItemFactoryResult(Optional.of(stack), Optional.empty());
    }

    public static BannerItemFactoryResult failure(BannerStateIssue issue) {
        return new BannerItemFactoryResult(Optional.empty(), Optional.of(issue));
    }

    public boolean successful() {
        return stack.isPresent();
    }
}
