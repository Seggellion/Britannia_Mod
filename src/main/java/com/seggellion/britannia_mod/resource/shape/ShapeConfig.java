package com.seggellion.britannia_mod.resource.shape;

import java.util.Objects;

/**
 * Everything a planner needs, and nothing a planner may not have.
 *
 * <p>There is no level, no position, no block and no world state here on purpose. A planner answers
 * "which cells belong to this deposit", and the answer must not depend on what is currently
 * standing in them — the legacy {@code LayeredVein} and {@code VerticalLayeredVein} both consulted
 * {@code isAir()} while computing geometry, which is why silver only ever materialised in caves.
 * Whether a cell is an acceptable host is {@code MaterializationService}'s question, asked later.
 *
 * @param radius   the deposit's configured size; what it means is the planner's to define
 * @param rotation orientation, consulted only by planners that declare they are directional
 * @param seed     the deterministic seed; identical seeds must give identical geometry
 */
public record ShapeConfig(int radius, ShapeRotation rotation, long seed) {

    public ShapeConfig {
        Objects.requireNonNull(rotation, "shape rotation is required");
    }

    public ShapeConfig withSeed(long newSeed) {
        return new ShapeConfig(radius, rotation, newSeed);
    }
}
