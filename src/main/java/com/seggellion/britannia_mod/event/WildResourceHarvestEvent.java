package com.seggellion.britannia_mod.event;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;

import java.util.Objects;

/** Successful server-authoritative wild-resource harvest notification. */
public final class WildResourceHarvestEvent extends Event {
    private final ServerPlayer player;
    private final ResourceLocation resourceId;
    private final BlockPos position;
    private final ResourceKey<Level> dimension;
    private final ItemStack result;
    private final ToolCategory toolCategory;

    public WildResourceHarvestEvent(
            ServerPlayer player,
            ResourceLocation resourceId,
            BlockPos position,
            ResourceKey<Level> dimension,
            ItemStack result,
            ToolCategory toolCategory
    ) {
        this.player = Objects.requireNonNull(player, "Harvest player is required");
        this.resourceId = Objects.requireNonNull(resourceId, "Harvest resource id is required");
        this.position = Objects.requireNonNull(position, "Harvest position is required").immutable();
        this.dimension = Objects.requireNonNull(dimension, "Harvest dimension is required");
        this.result = Objects.requireNonNull(result, "Harvest result is required").copy();
        if (this.result.isEmpty()) {
            throw new IllegalArgumentException("Successful harvest result cannot be empty");
        }
        this.toolCategory = Objects.requireNonNull(toolCategory, "Harvest tool category is required");
    }

    public ServerPlayer player() {
        return player;
    }

    public ResourceLocation resourceId() {
        return resourceId;
    }

    public BlockPos position() {
        return position;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    /** Returns a defensive copy so listeners cannot mutate the awarded stack. */
    public ItemStack result() {
        return result.copy();
    }

    public ToolCategory toolCategory() {
        return toolCategory;
    }

    public enum ToolCategory {
        DAGGER,
        OTHER
    }
}
