package com.seggellion.britannia_mod.structure.testsupport;

import com.seggellion.britannia_mod.structure.item.ShrineItem;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlock;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructurePartBlock;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.GameData;

/** Plain-JUnit registration boundary for real block states and item stacks. */
public final class MilestoneTwoRegisteredTestContent {
    private static LargeStructureAnchorBlock anchor;
    private static LargeStructurePartBlock part;
    private static ShrineItem shrine;

    private MilestoneTwoRegisteredTestContent() {
    }

    public static synchronized void ensureRegistered() {
        if (anchor != null) return;
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
        anchor = Registry.register(
                BuiltInRegistries.BLOCK,
                id("m2_test_large_structure_anchor"),
                new LargeStructureAnchorBlock(
                        BlockBehaviour.Properties.of().pushReaction(PushReaction.BLOCK)));
        part = Registry.register(
                BuiltInRegistries.BLOCK,
                id("m2_test_large_structure_part"),
                new LargeStructurePartBlock(
                        BlockBehaviour.Properties.of().pushReaction(PushReaction.BLOCK)));
        shrine = Registry.register(
                BuiltInRegistries.ITEM,
                id("m2_test_shrine"),
                new ShrineItem(new Item.Properties().stacksTo(1)));
    }

    public static LargeStructureAnchorBlock anchor() {
        ensureRegistered();
        return anchor;
    }

    public static LargeStructurePartBlock part() {
        ensureRegistered();
        return part;
    }

    public static ShrineItem shrine() {
        ensureRegistered();
        return shrine;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", path);
    }
}
