package com.seggellion.britannia_mod.banner.crafting;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public record CraftingIdentityResolution<T>(
        Optional<T> identity,
        CraftingIdentityFailure failure,
        List<ResourceLocation> evidence) {
    public CraftingIdentityResolution {
        identity = Objects.requireNonNull(identity, "identity");
        failure = Objects.requireNonNull(failure, "failure");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        if ((failure == CraftingIdentityFailure.NONE) != identity.isPresent()) {
            throw new IllegalArgumentException("Successful resolutions alone contain an identity");
        }
    }

    public static <T> CraftingIdentityResolution<T> success(T identity, List<ResourceLocation> evidence) {
        return new CraftingIdentityResolution<>(Optional.of(identity), CraftingIdentityFailure.NONE, evidence);
    }

    public static <T> CraftingIdentityResolution<T> failure(
            CraftingIdentityFailure failure, List<ResourceLocation> evidence) {
        return new CraftingIdentityResolution<>(Optional.empty(), failure, evidence);
    }

    public boolean successful() {
        return identity.isPresent();
    }
}
