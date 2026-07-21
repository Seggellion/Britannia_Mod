package com.seggellion.britannia_mod.dye.preview;

import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.service.DyeResult;
import com.seggellion.britannia_mod.dye.state.DyeTubState;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Runtime-only authority retained for one preview confirmation. */
public record DyePreviewSession(
        UUID playerId,
        UUID sessionId,
        long createdAtMillis,
        long expiresAtMillis,
        Item expectedMainItem,
        ItemStack expectedMainStack,
        Item expectedOffItem,
        ItemStack expectedOffStack,
        PigmentId pigmentId,
        BannerInstanceState bannerState,
        DyeTubState tubState,
        DyeResult resolvedResult,
        RegistrySnapshot registrySnapshot,
        DyePreviewDisplayData displayData) {
    public DyePreviewSession {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(sessionId, "sessionId");
        if (createdAtMillis < 0 || expiresAtMillis <= createdAtMillis) {
            throw new IllegalArgumentException("Invalid preview lifetime");
        }
        Objects.requireNonNull(expectedMainItem, "expectedMainItem");
        expectedMainStack = Objects.requireNonNull(expectedMainStack, "expectedMainStack").copy();
        Objects.requireNonNull(expectedOffItem, "expectedOffItem");
        expectedOffStack = Objects.requireNonNull(expectedOffStack, "expectedOffStack").copy();
        Objects.requireNonNull(pigmentId, "pigmentId");
        Objects.requireNonNull(bannerState, "bannerState");
        Objects.requireNonNull(tubState, "tubState");
        Objects.requireNonNull(resolvedResult, "resolvedResult");
        Objects.requireNonNull(registrySnapshot, "registrySnapshot");
        Objects.requireNonNull(displayData, "displayData");
    }

    public boolean expired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }

    @Override
    public ItemStack expectedMainStack() {
        return expectedMainStack.copy();
    }

    @Override
    public ItemStack expectedOffStack() {
        return expectedOffStack.copy();
    }
}
