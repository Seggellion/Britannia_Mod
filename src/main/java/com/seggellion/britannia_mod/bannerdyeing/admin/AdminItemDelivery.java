package com.seggellion.britannia_mod.bannerdyeing.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;

/** Inserts normally and drops only a non-empty remainder, matching existing repository give behavior. */
public final class AdminItemDelivery {
    private AdminItemDelivery() {
    }

    public static BatchResult deliverFresh(
            List<? extends AdminItemRecipient> recipients, Supplier<ItemStack> freshStack) {
        Objects.requireNonNull(recipients, "recipients");
        Objects.requireNonNull(freshStack, "freshStack");
        List<String> delivered = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (AdminItemRecipient recipient : recipients) {
            ItemStack stack = Objects.requireNonNull(freshStack.get(), "freshStack result");
            recipient.insert(stack);
            boolean success = stack.isEmpty();
            if (!success && recipient.dropRemainder(stack)) {
                success = true;
            }
            (success ? delivered : failed).add(recipient.name());
        }
        return new BatchResult(delivered, failed);
    }

    public record BatchResult(List<String> deliveredTargets, List<String> failedTargets) {
        public BatchResult {
            deliveredTargets = List.copyOf(deliveredTargets);
            failedTargets = List.copyOf(failedTargets);
        }

        public int successCount() {
            return deliveredTargets.size();
        }
    }
}
