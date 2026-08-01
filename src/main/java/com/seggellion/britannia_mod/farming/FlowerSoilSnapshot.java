package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import net.minecraft.nbt.CompoundTag;

import java.util.Objects;
import java.util.Optional;

/**
 * FarmingBlock soil values captured before later block conversion. The origin
 * and restoration record preserve the private/community distinction.
 */
public record FlowerSoilSnapshot(
        int hydration,
        int fertilizerLevel,
        float nitrogen,
        float phosphorus,
        float potassium,
        float organicMatter,
        FlowerSoilOrigin origin,
        Optional<FlowerCommunityRestoration> communityRestoration,
        long communitySeedableUntilGameTime
) {
    public FlowerSoilSnapshot {
        if (hydration < 0 || hydration > FarmingBlockEntity.MAX_HYDRATION) {
            throw new IllegalArgumentException("Flower soil hydration must use the FarmingBlock scale 0..5: " + hydration);
        }
        if (fertilizerLevel < 0 || fertilizerLevel > 2) {
            throw new IllegalArgumentException("Flower soil fertilizer level must use the FarmingBlock scale 0..2: " + fertilizerLevel);
        }
        requireNormalized("nitrogen", nitrogen);
        requireNormalized("phosphorus", phosphorus);
        requireNormalized("potassium", potassium);
        requireNormalized("organic matter", organicMatter);
        Objects.requireNonNull(origin, "Flower soil origin is required");
        communityRestoration = Objects.requireNonNull(communityRestoration, "Community restoration optional is required");
        if (origin == FlowerSoilOrigin.COMMUNITY_PLOT && communityRestoration.isEmpty()) {
            throw new IllegalArgumentException("Community-origin flower soil requires restoration metadata");
        }
        if (origin == FlowerSoilOrigin.PRIVATE_FARMING_BLOCK && communityRestoration.isPresent()) {
            throw new IllegalArgumentException("Private FarmingBlock soil cannot contain community restoration metadata");
        }
        if (communitySeedableUntilGameTime < 0L) {
            throw new IllegalArgumentException("Community seed-window time cannot be negative: " + communitySeedableUntilGameTime);
        }
        if (origin == FlowerSoilOrigin.PRIVATE_FARMING_BLOCK && communitySeedableUntilGameTime != 0L) {
            throw new IllegalArgumentException("Private FarmingBlock soil cannot contain a community seed-window time");
        }
    }

    public static FlowerSoilSnapshot privateSoil(
            int hydration, float nitrogen, float phosphorus, float potassium, float organicMatter
    ) {
        return new FlowerSoilSnapshot(
                hydration, 0, nitrogen, phosphorus, potassium, organicMatter,
                FlowerSoilOrigin.PRIVATE_FARMING_BLOCK, Optional.empty(), 0L
        );
    }

    public static FlowerSoilSnapshot privateSoil(
            int hydration, int fertilizerLevel,
            float nitrogen, float phosphorus, float potassium, float organicMatter
    ) {
        return new FlowerSoilSnapshot(
                hydration, fertilizerLevel, nitrogen, phosphorus, potassium, organicMatter,
                FlowerSoilOrigin.PRIVATE_FARMING_BLOCK, Optional.empty(), 0L
        );
    }

    public static FlowerSoilSnapshot communitySoil(
            int hydration, float nitrogen, float phosphorus, float potassium, float organicMatter
    ) {
        return new FlowerSoilSnapshot(
                hydration, 0, nitrogen, phosphorus, potassium, organicMatter,
                FlowerSoilOrigin.COMMUNITY_PLOT,
                Optional.of(FlowerCommunityRestoration.currentRepositoryState()),
                0L
        );
    }

    public static FlowerSoilSnapshot communitySoil(
            int hydration, int fertilizerLevel,
            float nitrogen, float phosphorus, float potassium, float organicMatter,
            long communitySeedableUntilGameTime
    ) {
        return new FlowerSoilSnapshot(
                hydration, fertilizerLevel, nitrogen, phosphorus, potassium, organicMatter,
                FlowerSoilOrigin.COMMUNITY_PLOT,
                Optional.of(FlowerCommunityRestoration.currentRepositoryState()),
                communitySeedableUntilGameTime
        );
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Hydration", hydration);
        tag.putInt("FertilizerLevel", fertilizerLevel);
        tag.putFloat("Nitrogen", nitrogen);
        tag.putFloat("Phosphorus", phosphorus);
        tag.putFloat("Potassium", potassium);
        tag.putFloat("OrganicMatter", organicMatter);
        tag.putString("Origin", origin.name());
        communityRestoration.ifPresent(restoration -> tag.put("CommunityRestoration", restoration.toTag()));
        tag.putLong("CommunitySeedableUntilGameTime", communitySeedableUntilGameTime);
        return tag;
    }

    public FlowerSoilSnapshot withHydration(int hydration) {
        return new FlowerSoilSnapshot(
                hydration,
                fertilizerLevel,
                nitrogen,
                phosphorus,
                potassium,
                organicMatter,
                origin,
                communityRestoration,
                communitySeedableUntilGameTime
        );
    }

    public static FlowerSoilSnapshot fromTag(CompoundTag tag) {
        FlowerSoilOrigin origin;
        try {
            origin = FlowerSoilOrigin.valueOf(tag.getString("Origin"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown flower soil origin: " + tag.getString("Origin"), exception);
        }
        Optional<FlowerCommunityRestoration> restoration = tag.contains("CommunityRestoration")
                ? Optional.of(FlowerCommunityRestoration.fromTag(tag.getCompound("CommunityRestoration")))
                : Optional.empty();
        return new FlowerSoilSnapshot(
                tag.getInt("Hydration"),
                tag.getInt("FertilizerLevel"),
                tag.getFloat("Nitrogen"),
                tag.getFloat("Phosphorus"),
                tag.getFloat("Potassium"),
                tag.getFloat("OrganicMatter"),
                origin,
                restoration,
                tag.getLong("CommunitySeedableUntilGameTime")
        );
    }

    private static void requireNormalized(String fieldName, float value) {
        if (!Float.isFinite(value) || value < 0.0f || value > 1.0f) {
            throw new IllegalArgumentException("Flower soil " + fieldName + " must use the normalized 0..1 farming scale: " + value);
        }
    }
}
