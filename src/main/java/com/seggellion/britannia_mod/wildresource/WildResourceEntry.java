package com.seggellion.britannia_mod.wildresource;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

import java.util.Objects;

/**
 * Immutable policy and strategy bundle for one opportunistically spawned world resource.
 * The scheduler consumes this contract without knowing which resource it is processing.
 */
public record WildResourceEntry(
        ResourceLocation id,
        int spawnWeight,
        int maxNodesPerChunk,
        WildResourceTuning tuning,
        CandidateGenerator candidateGenerator,
        PlacementRule environmentRule,
        PlacementRule substrateRule,
        PlacementRule biomeRule,
        PlacementRule nearbyRule,
        PlacementStrategy placementStrategy,
        HarvestStrategy harvestStrategy,
        LootStrategy lootStrategy
) {
    public WildResourceEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tuning, "tuning");
        Objects.requireNonNull(candidateGenerator, "candidateGenerator");
        Objects.requireNonNull(environmentRule, "environmentRule");
        Objects.requireNonNull(substrateRule, "substrateRule");
        Objects.requireNonNull(biomeRule, "biomeRule");
        Objects.requireNonNull(nearbyRule, "nearbyRule");
        Objects.requireNonNull(placementStrategy, "placementStrategy");
        Objects.requireNonNull(harvestStrategy, "harvestStrategy");
        Objects.requireNonNull(lootStrategy, "lootStrategy");
        if (spawnWeight <= 0) {
            throw new IllegalArgumentException("spawnWeight must be positive");
        }
        if (maxNodesPerChunk <= 0) {
            throw new IllegalArgumentException("maxNodesPerChunk must be positive");
        }
    }

    public boolean isValidPlacement(ServerLevel level, BlockPos position) {
        return environmentRule.test(level, position)
                && substrateRule.test(level, position)
                && biomeRule.test(level, position)
                && nearbyRule.test(level, position);
    }

    @FunctionalInterface
    public interface CandidateGenerator {
        BlockPos sample(ServerLevel level, ChunkPos chunk, RandomSource random);
    }

    @FunctionalInterface
    public interface PlacementRule {
        PlacementRule ALLOW = (level, position) -> true;

        boolean test(ServerLevel level, BlockPos position);

        default PlacementRule and(PlacementRule other) {
            Objects.requireNonNull(other, "other");
            return (level, position) -> test(level, position) && other.test(level, position);
        }
    }

    @FunctionalInterface
    public interface PlacementStrategy {
        boolean place(ServerLevel level, BlockPos position);
    }

    @FunctionalInterface
    public interface HarvestStrategy {
        HarvestStrategy DISABLED = (level, position, player, tool) -> false;

        boolean harvest(ServerLevel level, BlockPos position, ServerPlayer player, ItemStack tool);
    }

    @FunctionalInterface
    public interface LootStrategy {
        LootStrategy NONE = (level, position, player) -> ItemStack.EMPTY;

        ItemStack create(ServerLevel level, BlockPos position, ServerPlayer player);
    }
}
