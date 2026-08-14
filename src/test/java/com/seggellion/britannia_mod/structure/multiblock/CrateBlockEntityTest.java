package com.seggellion.britannia_mod.structure.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.entity.CrateBlockEntity;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CrateBlockEntityTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
    }

    @Test
    void eachCrateSizeUsesItsDeclaredVanillaRowCapacity() {
        assertEquals(9, entity(crate(9, 0, 0, 0), Direction.NORTH).getContainerSize());
        assertEquals(27, entity(crate(27, 0, 0, 0), Direction.NORTH).getContainerSize());
        assertEquals(54, entity(crate(54, 1, 1, 1), Direction.NORTH).getContainerSize());
    }

    @Test
    void inventoryRoundTripsThroughPersistentBlockEntityData() {
        CrateBlock block = crate(54, 1, 1, 1);
        CrateBlockEntity source = entity(block, Direction.WEST);
        source.setItem(0, new ItemStack(Items.DIAMOND, 7));
        source.setItem(53, new ItemStack(Items.GOLD_INGOT, 19));

        CompoundTag saved = source.saveWithoutMetadata(RegistryAccess.EMPTY);
        CrateBlockEntity decoded = entity(block, Direction.WEST);
        decoded.loadWithComponents(saved, RegistryAccess.EMPTY);

        assertEquals(Items.DIAMOND, decoded.getItem(0).getItem());
        assertEquals(7, decoded.getItem(0).getCount());
        assertEquals(Items.GOLD_INGOT, decoded.getItem(53).getItem());
        assertEquals(19, decoded.getItem(53).getCount());
        assertFalse(decoded.isEmpty());
    }

    @Test
    void largeCrateEncodesOneInventoryRootAcrossEightOccupiedCells() {
        CrateBlock block = crate(54, 1, 1, 1);
        long roots = block.cells().stream()
                .map(cell -> block.stateFor(Direction.SOUTH, cell))
                .filter(block::isRoot)
                .count();

        assertEquals(8, block.cells().size());
        assertEquals(1, roots);
    }

    @Test
    void unsupportedInventoryRowCountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> crate(18, 0, 0, 0));
    }

    private static CrateBlock crate(int slots, int maxX, int maxY, int maxZ) {
        return new CrateBlock(
                BlockBehaviour.Properties.of(), slots, "container.test.crate",
                0, maxX, 0, maxY, 0, maxZ,
                (x, y, z) -> Block.box(0, 0, 0, 16, 16, 16));
    }

    private static CrateBlockEntity entity(CrateBlock block, Direction facing) {
        @SuppressWarnings("unchecked")
        BlockEntityType<CrateBlockEntity>[] holder =
                (BlockEntityType<CrateBlockEntity>[]) new BlockEntityType<?>[1];
        holder[0] = BlockEntityType.Builder.of(
                (pos, state) -> new CrateBlockEntity(holder[0], pos, state), block).build(null);
        return new CrateBlockEntity(
                holder[0], BlockPos.ZERO, block.defaultBlockState().setValue(CrateBlock.FACING, facing));
    }
}
