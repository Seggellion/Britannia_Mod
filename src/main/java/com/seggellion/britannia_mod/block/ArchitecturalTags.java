package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Block tags used by the architectural block families. */
public final class ArchitecturalTags {

    /**
     * Blocks a wall run treats as a continuation of itself when deciding whether it turns a corner
     * or branches.
     *
     * <p>Doorways are the reason this exists: a wall meeting a door has to return into the reveal,
     * and before this the wall could not see the door at all so it stayed flat. The tag is
     * data-driven so more blocks can be added without touching code - see
     * {@code data/britannia_mod/tags/block/wall_connectable.json}.
     */
    public static final TagKey<Block> WALL_CONNECTABLE = TagKey.create(
        Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "wall_connectable"));

    private ArchitecturalTags() {
    }
}
