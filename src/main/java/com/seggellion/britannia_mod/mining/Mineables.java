package com.seggellion.britannia_mod.mining;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The one Minecraft-facing door into the Mining catalogue.
 *
 * <p>Mining milestone 2. Loads and validates the catalogue fail-fast at mod construction (the
 * {@code CraftableRegistry.init()} convention) and answers "is this block governed by Mining, and
 * by which definition" for the milestone-3 break gate. Registered block ids cannot be checked at
 * construction time — block registration has not run yet — so {@link #validateBlockIdsResolve}
 * re-validates against the live registry at server start.
 *
 * <p>Nothing calls {@link #resolve} yet; wiring the gate is milestone 3 by design.
 */
public final class Mineables {

    private static final Logger LOGGER = LogUtils.getLogger();

    private Mineables() {}

    /** Parse + validate the shipped catalogue now so a data bug fails the load, not a break. */
    public static void init() {
        MineableCatalog catalog = MineableCatalog.instance();
        LOGGER.info("Loaded {} mineable definitions ({} active covering {} blocks)",
                catalog.all().size(), catalog.active().size(), catalog.activeBlockIds().size());
    }

    /** ACTIVE definition governing this block, or empty = Mining does not apply. */
    public static Optional<MineableDefinition> resolve(BlockState state) {
        if (state == null) return Optional.empty();
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return MineableCatalog.instance().resolveBlock(blockId);
    }

    /**
     * Fails server start if any catalogued block id (active or deferred) does not exist in the
     * block registry — the catalogue's equivalent of an unresolved-tag error.
     */
    public static void validateBlockIdsResolve() {
        List<String> missing = new ArrayList<>();
        for (MineableDefinition definition : MineableCatalog.instance().all()) {
            for (String blockId : definition.blockIds()) {
                if (!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(blockId))) {
                    missing.add(definition.id() + " -> " + blockId);
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Mineable catalogue references unregistered blocks: " + missing);
        }
        LOGGER.info("Mineable catalogue block ids all resolve against the block registry");
    }
}
