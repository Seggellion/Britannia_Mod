package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.BritanniaChestBlockEntity;
import com.seggellion.britannia_mod.block.entity.WineBottleBlockEntity;
import com.seggellion.britannia_mod.component.WineData;
import com.seggellion.britannia_mod.grabbyhands.GrabbyInstanceState;
import com.seggellion.britannia_mod.grabbyhands.GrabbyProvenance;
import com.seggellion.britannia_mod.grabbyhands.GrabbyProvenanceAccess;
import com.seggellion.britannia_mod.grabbyhands.blockentity.GrabbyPlacedItemBlockEntity;
import com.seggellion.britannia_mod.item.WineBottleBlockItem;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.GrabbyRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Milestone 12: everything Grabby Hands persists, checked against a real level.
 *
 * <p>Until this milestone every persistence claim was made against an in-memory tag. That proves the
 * <em>format</em> round-trips, and says nothing about whether the block entity Minecraft actually
 * creates writes and reads it. These tests place real blocks, take the real block entities, and drive
 * their real {@code saveWithoutMetadata}/{@code loadWithComponents} pair — which is what a chunk
 * unload and reload does.
 *
 * <p>A running server is required because none of these block entities exist outside a mod-loading
 * lifecycle, and because the wine bottle's state transfer runs inside {@code setPlacedBy}.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class GrabbyPersistenceGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final UUID PLACER = UUID.fromString("0a0a0a0a-1b1b-2c2c-3d3d-4e4e4e4e4e4e");
    private static final WineData VINTAGE = new WineData(
            "Britannia Vintners", "Merlot", 1487, 82, "Trinsic", "green");

    private GrabbyPersistenceGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Saves and reloads a block entity the way a chunk unload and reload does. */
    private static void reload(ServerLevel level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        check(entity != null, "no block entity at " + pos);
        HolderLookup.Provider registries = level.registryAccess();
        CompoundTag saved = entity.saveWithoutMetadata(registries);
        entity.loadWithComponents(saved, registries);
    }

    // ------------------------------------------------------------------
    // Provenance
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void provenanceSurvivesAReload(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        level.setBlockAndUpdate(pos, BlockRegistry.WOODEN_CHAIR.get().defaultBlockState());

        GrabbyProvenanceAccess.write(level.getBlockEntity(pos),
                GrabbyInstanceState.playerPlaced(PLACER, 1234L));
        reload(level, pos);

        GrabbyInstanceState restored = GrabbyProvenanceAccess.read(level, pos);
        check(restored.provenance() == GrabbyProvenance.PLAYER, "provenance was lost");
        check(restored.placerUuid().orElseThrow().equals(PLACER), "the placer was lost");
        check(restored.placedAtGameTime() == 1234L, "the placement time was lost");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anUnmarkedBlockStillReadsAsProtectedAfterAReload(GameTestHelper helper) {
        // The no-migration guarantee, checked against a real block entity rather than a bare tag.
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        level.setBlockAndUpdate(pos, BlockRegistry.WOODEN_CHAIR.get().defaultBlockState());

        reload(level, pos);

        check(!GrabbyProvenanceAccess.read(level, pos).grabbyManaged(),
                "a chair nobody placed must stay immovable");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void facingSurvivesAReload(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        BlockState east = BlockRegistry.WOODEN_CHAIR.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.EAST);
        level.setBlockAndUpdate(pos, east);

        reload(level, pos);

        check(level.getBlockState(pos).getValue(BlockStateProperties.HORIZONTAL_FACING)
                        == net.minecraft.core.Direction.EAST,
                "the chair changed which way it faces");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Wine
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void aWineBottleKeepsAllSixFieldsAndItsOriginStackAcrossAReload(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        level.setBlockAndUpdate(pos, BlockRegistry.WINE_BOTTLE_GREEN_BLOCK.get().defaultBlockState());

        ItemStack source = new ItemStack(ItemRegistry.WINE_BOTTLE_GREEN.get());
        WineBottleBlockItem.setWineData(source, VINTAGE.wineryName(), VINTAGE.grapeType(),
                VINTAGE.year(), VINTAGE.quality(), VINTAGE.region(), VINTAGE.labelColor());
        source.set(DataComponents.CUSTOM_NAME, Component.literal("Cellared"));

        WineBottleBlockEntity bottle = (WineBottleBlockEntity) level.getBlockEntity(pos);
        check(bottle != null, "no wine bottle block entity");
        bottle.setWineData(VINTAGE);
        bottle.setOriginStack(source);
        GrabbyProvenanceAccess.write(bottle, GrabbyInstanceState.playerPlaced(PLACER, 9L));

        reload(level, pos);

        WineBottleBlockEntity reloaded = (WineBottleBlockEntity) level.getBlockEntity(pos);
        check(VINTAGE.equals(reloaded.getWineData()), "the wine fields changed across a reload");

        ItemStack portable = reloaded.portableStack(ItemRegistry.WINE_BOTTLE_GREEN.get());
        check(ItemStack.matches(source, portable),
                "the bottle no longer restores the exact item it was placed from");
        check("Cellared".equals(portable.get(DataComponents.CUSTOM_NAME).getString()),
                "the custom name was lost across a reload");
        check(GrabbyProvenanceAccess.read(level, pos).grabbyManaged(), "provenance was lost");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aWineBottlePlacedThroughItsOwnPathRecordsWhatItWasPlacedFrom(GameTestHelper helper) {
        // setPlacedBy is where the item-to-block transfer actually happens, and it has never run
        // outside a live level before this milestone.
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();

        ItemStack source = new ItemStack(ItemRegistry.WINE_BOTTLE_GREEN.get());
        WineBottleBlockItem.setWineData(source, VINTAGE.wineryName(), VINTAGE.grapeType(),
                VINTAGE.year(), VINTAGE.quality(), VINTAGE.region(), VINTAGE.labelColor());

        BlockState state = BlockRegistry.WINE_BOTTLE_GREEN_BLOCK.get().defaultBlockState();
        level.setBlockAndUpdate(pos, state);
        BlockRegistry.WINE_BOTTLE_GREEN_BLOCK.get().setPlacedBy(level, pos, state, null, source);

        WineBottleBlockEntity bottle = (WineBottleBlockEntity) level.getBlockEntity(pos);
        check(bottle != null, "no wine bottle block entity after setPlacedBy");
        check(VINTAGE.equals(bottle.getWineData()), "setPlacedBy did not transfer the wine fields");
        check(!bottle.getOriginStack().isEmpty(), "setPlacedBy did not record the source stack");

        ItemStack recovered = BlockRegistry.WINE_BOTTLE_GREEN_BLOCK.get()
                .getCloneItemStack(level, pos, level.getBlockState(pos));
        check(VINTAGE.equals(WineBottleBlockItem.getWineData(recovered)),
                "the bottle does not come back with its own wine fields");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Containers
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void aFilledContainerKeepsItsContentsAcrossAReload(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        level.setBlockAndUpdate(pos, BlockRegistry.CHEST_WOODEN.get().defaultBlockState());

        BritanniaChestBlockEntity chest = (BritanniaChestBlockEntity) level.getBlockEntity(pos);
        check(chest != null, "no chest block entity");
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Britannian Steel"));
        chest.setItem(0, new ItemStack(Items.GOLD_INGOT, 5));
        chest.setItem(4, named);
        GrabbyProvenanceAccess.write(chest, GrabbyInstanceState.playerPlaced(PLACER, 3L));

        reload(level, pos);

        BritanniaChestBlockEntity reloaded = (BritanniaChestBlockEntity) level.getBlockEntity(pos);
        check(reloaded.getItem(0).getItem() == Items.GOLD_INGOT, "slot 0 lost its stack");
        check(reloaded.getItem(0).getCount() == 5, "slot 0 lost its count");
        check("Britannian Steel".equals(
                        reloaded.getItem(4).get(DataComponents.CUSTOM_NAME).getString()),
                "an item inside the chest lost its custom name");
        check(reloaded.getItem(1).isEmpty(), "an empty slot did not stay empty");
        check(GrabbyProvenanceAccess.read(level, pos).grabbyManaged(), "provenance was lost");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aContainerCarriesItsWholeStateIntoAPortableItem(GameTestHelper helper) {
        // Contents plus lock fields. Only the format was checked before; this runs the real writer.
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        level.setBlockAndUpdate(pos, BlockRegistry.BRITANNIA_LOCKABLE_CHEST.get().defaultBlockState());

        BritanniaChestBlockEntity chest = (BritanniaChestBlockEntity) level.getBlockEntity(pos);
        check(chest != null, "no lockable chest block entity");
        chest.setLocked(true);
        chest.setItem(2, new ItemStack(Items.EMERALD, 7));

        ItemStack portable = new ItemStack(ItemRegistry.BRITANNIA_LOCKABLE_CHEST_ITEM.get());
        chest.writePortableState(portable, level.registryAccess());

        CompoundTag carried = portable.getOrDefault(DataComponents.BLOCK_ENTITY_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        check(carried.getBoolean("Locked"), "a stolen chest must arrive still locked");
        check(carried.hasUUID("LockId"), "the lock identity was not carried");
        check(carried.getBoolean("ChestKeySeeded"),
                "losing this marker turns every place-and-pickup cycle into a key printer");
        check(carried.contains("Items"), "the contents were not carried");
        check(!carried.contains(GrabbyInstanceState.TAG_KEY),
                "provenance must be stamped fresh on placement, not carried forward");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aLockedChestReportsItselfAsSecuredAgainstDestruction(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        level.setBlockAndUpdate(pos, BlockRegistry.BRITANNIA_LOCKABLE_CHEST.get().defaultBlockState());

        BritanniaChestBlockEntity chest = (BritanniaChestBlockEntity) level.getBlockEntity(pos);
        check(!chest.securedAgainstDestruction(), "an unlocked chest is ordinary furniture");
        chest.setLocked(true);
        check(chest.securedAgainstDestruction(), "a locked chest must refuse being chopped open");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // The generic host
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void theHostKeepsItsPayloadAcrossAReload(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        level.setBlockAndUpdate(pos, GrabbyRegistry.PLACED_ITEM.get().defaultBlockState());

        GrabbyPlacedItemBlockEntity host = (GrabbyPlacedItemBlockEntity) level.getBlockEntity(pos);
        check(host != null, "no host block entity");
        ItemStack payload = new ItemStack(ItemRegistry.CHEESE.get());
        payload.set(DataComponents.CUSTOM_NAME, Component.literal("Mouldy"));
        host.setGrabbyPayload(payload);
        GrabbyProvenanceAccess.write(host, GrabbyInstanceState.playerPlaced(PLACER, 2L));

        reload(level, pos);

        GrabbyPlacedItemBlockEntity reloaded = (GrabbyPlacedItemBlockEntity) level.getBlockEntity(pos);
        check(ItemStack.matches(payload, reloaded.grabbyPayload()),
                "the host lost or altered the item it was holding");
        check(GrabbyProvenanceAccess.read(level, pos).grabbyManaged(), "provenance was lost");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void theHostsCloneStackIsTheItemItHolds(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        level.setBlockAndUpdate(pos, GrabbyRegistry.PLACED_ITEM.get().defaultBlockState());

        GrabbyPlacedItemBlockEntity host = (GrabbyPlacedItemBlockEntity) level.getBlockEntity(pos);
        ItemStack payload = new ItemStack(ItemRegistry.LUTE.get());
        host.setGrabbyPayload(payload);

        ItemStack cloned = GrabbyRegistry.PLACED_ITEM.get()
                .getCloneItemStack(level, pos, level.getBlockState(pos));
        check(ItemStack.matches(payload, cloned),
                "capturing a host must yield the item it is holding, not the host block");
        helper.succeed();
    }
}
