package com.seggellion.britannia_mod.grabbyhands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.DecorativeMultiblockBlock.Cell;
import com.seggellion.britannia_mod.block.entity.CrateBlockEntity;
import com.seggellion.britannia_mod.grabbyhands.testsupport.FakeGrabbyWorld;
import com.seggellion.britannia_mod.item.DecorativeMultiblockItem;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Crates are the first genuinely multi-cell object Grabby Hands carries.
 *
 * <p>Two things had to become true for that, and both are asserted here: any cell of the structure
 * has to answer for the whole object, and the inventory has to travel inside the item rather than
 * spilling on the floor when the structure dismantles.
 */
class GrabbyCrateTransportTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
    }

    @Test
    void everyCellOfALargeCrateResolvesToTheSingleAnchor() {
        CrateBlock block = crate(54, 1, 1, 1);
        Direction facing = Direction.SOUTH;
        BlockPos anchor = new BlockPos(30, 64, -12);
        FakeGrabbyWorld world = new FakeGrabbyWorld(new ArrayList<>());

        List<BlockPos> cellPositions = new ArrayList<>();
        for (Cell cell : block.cells()) {
            BlockPos position = block.worldPosition(anchor, facing, cell);
            world.placeRaw(position, block.stateFor(facing, cell), ItemStack.EMPTY);
            cellPositions.add(position);
        }

        assertEquals(8, cellPositions.size(), "a 2x2x2 crate should occupy eight cells");
        for (BlockPos clicked : cellPositions) {
            assertEquals(anchor, GrabbyRootResolver.resolveRoot(world, clicked),
                    "clicking " + clicked + " should resolve to the anchor that owns the inventory");
        }
    }

    @Test
    void aSingleCellCrateStillResolvesToItself() {
        CrateBlock block = crate(9, 0, 0, 0);
        FakeGrabbyWorld world = new FakeGrabbyWorld(new ArrayList<>());
        BlockPos pos = new BlockPos(4, 70, 9);
        world.placeRaw(pos, block.stateFor(Direction.EAST, block.cells().get(0)), ItemStack.EMPTY);

        assertEquals(pos, GrabbyRootResolver.resolveRoot(world, pos));
    }

    @Test
    void anOrdinaryBlockIsUnaffectedByMultiblockResolution() {
        FakeGrabbyWorld world = new FakeGrabbyWorld(new ArrayList<>());
        BlockPos pos = new BlockPos(1, 2, 3);
        world.placeRaw(pos, net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(),
                ItemStack.EMPTY);

        assertEquals(pos, GrabbyRootResolver.resolveRoot(world, pos));
    }

    @Test
    void crateContentsTravelInThePortableItemRatherThanOnTheFloor() {
        CrateBlock block = crate(27, 0, 0, 0);
        CrateBlockEntity crate = entity(block, Direction.NORTH);
        crate.setItem(0, new ItemStack(Items.DIAMOND, 5));
        crate.setItem(26, new ItemStack(Items.EMERALD, 3));

        ItemStack portable = new ItemStack(Items.CHEST);
        crate.writePortableState(portable, RegistryAccess.EMPTY);

        // Detaching is what stops the dismantle cascade dropping a second copy.
        assertTrue(crate.detachForTransport(), "a stocked crate has something to detach");
        assertTrue(crate.isEmpty(), "detach must leave nothing for beforeDismantle to drop");

        crate.restorePortableState(portable, RegistryAccess.EMPTY);
        assertEquals(Items.DIAMOND, crate.getItem(0).getItem());
        assertEquals(5, crate.getItem(0).getCount());
        assertEquals(Items.EMERALD, crate.getItem(26).getItem());
        assertEquals(3, crate.getItem(26).getCount());
    }

    @Test
    void anEmptyCrateHasNothingToDetach() {
        CrateBlockEntity crate = entity(crate(9, 0, 0, 0), Direction.NORTH);
        assertFalse(crate.detachForTransport(), "an empty crate must not claim it detached anything");
    }

    @Test
    void aCrateHoldingAFullContainerRefusesTransport() {
        CrateBlockEntity crate = entity(crate(9, 0, 0, 0), Direction.NORTH);
        assertEquals(Optional.empty(), crate.transportRefusal(), "an ordinary crate travels");

        ItemStack nested = new ItemStack(Items.CHEST);
        CompoundTag payload = new CompoundTag();
        payload.putString("id", "minecraft:chest");
        nested.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(payload));
        crate.setItem(0, nested);

        assertEquals(Optional.of(GrabbyTransportRefusal.NESTED_CONTAINER), crate.transportRefusal(),
                "a crate carrying a stocked container must refuse rather than nest inventories");
    }

    @Test
    void provenanceSurvivesSaveAndLoadButNeverRidesInTheItem() {
        CrateBlock block = crate(9, 0, 0, 0);
        CrateBlockEntity crate = entity(block, Direction.WEST);
        crate.setGrabbyState(GrabbyInstanceState.playerPlaced(
                java.util.UUID.nameUUIDFromBytes(new byte[] {1, 2, 3}), 4242L));

        CompoundTag saved = crate.saveWithoutMetadata(RegistryAccess.EMPTY);
        CrateBlockEntity reloaded = entity(block, Direction.WEST);
        reloaded.loadWithComponents(saved, RegistryAccess.EMPTY);
        assertTrue(reloaded.grabbyState().grabbyManaged(),
                "a player-placed crate must still be player-placed after a world reload");

        // The placement transaction stamps provenance fresh, so carrying the old placer would be
        // both pointless and misleading.
        ItemStack portable = new ItemStack(Items.CHEST);
        crate.writePortableState(portable, RegistryAccess.EMPTY);
        CompoundTag carried = portable
                .getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY)
                .copyTag();
        assertFalse(carried.contains(GrabbyInstanceState.TAG_KEY),
                "provenance must not travel inside the portable item");
    }

    @Test
    void theCrateItemOwnsItsOwnPlacementSoGrabbyDoesNotGuessTheAnchor() {
        CrateBlock block = crate(54, 1, 1, 1);
        BlockItem item = new DecorativeMultiblockItem(block, new net.minecraft.world.item.Item.Properties());

        assertTrue(item instanceof GrabbyStructurePlacementItem,
                "Grabby must be told where a multi-cell object roots rather than assuming the"
                        + " clicked cell, which is what BlockItem.place would have used");
        assertSame(block, item.getBlock());
    }

    private static CrateBlock crate(int slots, int maxX, int maxY, int maxZ) {
        return new CrateBlock(
                BlockBehaviour.Properties.of(), slots, "container.test.crate",
                0, maxX, 0, maxY, 0, maxZ,
                (x, y, z) -> Block.box(0, 0, 0, 16, 16, 16));
    }

    /**
     * Registers the block entity type as well as building it.
     *
     * <p>{@code writePortableState} goes through {@code BlockItem.setBlockEntityData}, which stamps
     * the type's registry key into the item. An unregistered type has no key, so a crate built only
     * with {@code Builder.of} would fail inside vanilla rather than inside anything this test means
     * to check.
     */
    private static CrateBlockEntity entity(CrateBlock block, Direction facing) {
        @SuppressWarnings("unchecked")
        BlockEntityType<CrateBlockEntity>[] holder =
                (BlockEntityType<CrateBlockEntity>[]) new BlockEntityType<?>[1];
        holder[0] = BlockEntityType.Builder.of(
                (pos, state) -> new CrateBlockEntity(holder[0], pos, state), block).build(null);
        Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(
                        "britannia_mod", "test_crate_" + TYPE_SEQUENCE.incrementAndGet()),
                holder[0]);
        BlockState rootState = block.defaultBlockState().setValue(CrateBlock.FACING, facing);
        return new CrateBlockEntity(holder[0], BlockPos.ZERO, rootState);
    }

    private static final java.util.concurrent.atomic.AtomicInteger TYPE_SEQUENCE =
            new java.util.concurrent.atomic.AtomicInteger();
}
