package com.seggellion.britannia_mod.structure.testsupport;

import com.seggellion.britannia_mod.structure.item.ShrineItem;
import com.seggellion.britannia_mod.structure.item.MonolithItem;
import com.seggellion.britannia_mod.structure.item.ShrineItemState;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlock;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructurePartBlock;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlockEntity;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.GameData;

/** Plain-JUnit registration boundary for real block states and item stacks. */
public final class MilestoneTwoRegisteredTestContent {
    private static LargeStructureAnchorBlock anchor;
    private static LargeStructurePartBlock part;
    private static ShrineItem shrine;
    private static MonolithItem monolith;
    private static DataComponentType<ShrineItemState> component;
    private static DataComponentType<ShrineItemState> monolithComponent;
    private static BlockEntityType<LargeStructureAnchorBlockEntity> blockEntityType;

    private MilestoneTwoRegisteredTestContent() {
    }

    public static synchronized void ensureRegistered() {
        if (anchor != null) return;
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
        component = Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                id("m3_test_shrine_instance_state"),
                DataComponentRegistry.createShrineInstanceStateType());
        monolithComponent = Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                id("m6_test_monolith_instance_state"),
                DataComponentRegistry.createShrineInstanceStateType());
        anchor = Registry.register(
                BuiltInRegistries.BLOCK,
                id("m2_test_large_structure_anchor"),
                new LargeStructureAnchorBlock(
                        BlockBehaviour.Properties.of().noOcclusion().pushReaction(PushReaction.BLOCK)));
        part = Registry.register(
                BuiltInRegistries.BLOCK,
                id("m2_test_large_structure_part"),
                new LargeStructurePartBlock(
                        BlockBehaviour.Properties.of().noOcclusion().pushReaction(PushReaction.BLOCK)));
        @SuppressWarnings("unchecked")
        BlockEntityType<LargeStructureAnchorBlockEntity>[] holder =
                (BlockEntityType<LargeStructureAnchorBlockEntity>[]) new BlockEntityType<?>[1];
        blockEntityType = BlockEntityType.Builder.of(
                (pos, state) -> new LargeStructureAnchorBlockEntity(holder[0], pos, state), anchor).build(null);
        holder[0] = blockEntityType;
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("m3_test_large_structure"), blockEntityType);
        shrine = Registry.register(
                BuiltInRegistries.ITEM,
                id("m2_test_shrine"),
                new ShrineItem(new Item.Properties().stacksTo(1), () -> component));
        monolith = Registry.register(
                BuiltInRegistries.ITEM,
                id("m6_test_monolith"),
                new MonolithItem(new Item.Properties().stacksTo(1), () -> monolithComponent));
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

    public static MonolithItem monolith() {
        ensureRegistered();
        return monolith;
    }

    public static DataComponentType<ShrineItemState> monolithComponent() {
        ensureRegistered();
        return monolithComponent;
    }

    public static DataComponentType<ShrineItemState> component() {
        ensureRegistered();
        return component;
    }

    public static BlockEntityType<LargeStructureAnchorBlockEntity> blockEntityType() {
        ensureRegistered();
        return blockEntityType;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", path);
    }
}
