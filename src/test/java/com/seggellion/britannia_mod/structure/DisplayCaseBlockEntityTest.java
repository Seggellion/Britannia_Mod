package com.seggellion.britannia_mod.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.block.DisplayCaseBlock;
import com.seggellion.britannia_mod.block.entity.DisplayCaseBlockEntity;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DisplayCaseBlockEntityTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
    }

    @Test
    void componentBearingMerchandiseRoundTripsThroughDiskAndClientTags() {
        DisplayCaseBlock block = new DisplayCaseBlock(BlockBehaviour.Properties.of());
        DisplayCaseBlockEntity source = entity(block);
        ItemStack namedStone = new ItemStack(Items.STONE, 12);
        namedStone.set(DataComponents.CUSTOM_NAME, Component.literal("Vendor sample"));

        assertTrue(source.storeOne(namedStone));
        assertEquals(1, source.displayedItem().getCount());
        assertTrue(ItemStack.isSameItemSameComponents(namedStone, source.displayedItem()));

        DisplayCaseBlockEntity diskCopy = entity(block);
        diskCopy.loadWithComponents(
                source.saveWithoutMetadata(RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        assertTrue(ItemStack.isSameItemSameComponents(
                source.displayedItem(), diskCopy.displayedItem()));
        assertEquals(1, diskCopy.displayedItem().getCount());

        DisplayCaseBlockEntity clientCopy = entity(block);
        clientCopy.handleUpdateTag(source.getUpdateTag(RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        assertTrue(ItemStack.isSameItemSameComponents(
                source.displayedItem(), clientCopy.displayedItem()));
        assertEquals(1, clientCopy.displayedItem().getCount());
    }

    @Test
    void occupiedHostCannotOverwriteAndRetrievalTransfersOwnershipOnce() {
        DisplayCaseBlock block = new DisplayCaseBlock(BlockBehaviour.Properties.of());
        DisplayCaseBlockEntity entity = entity(block);

        assertTrue(entity.storeOne(new ItemStack(Items.APPLE)));
        assertFalse(entity.storeOne(new ItemStack(Items.DIAMOND)));
        ItemStack retrieved = entity.takeDisplayedItem();
        assertTrue(retrieved.is(Items.APPLE));
        assertEquals(1, retrieved.getCount());
        assertTrue(entity.takeDisplayedItem().isEmpty());
        assertFalse(entity.hasDisplayedItem());
    }

    private static DisplayCaseBlockEntity entity(DisplayCaseBlock block) {
        @SuppressWarnings("unchecked")
        BlockEntityType<DisplayCaseBlockEntity>[] holder =
                (BlockEntityType<DisplayCaseBlockEntity>[]) new BlockEntityType<?>[1];
        holder[0] = BlockEntityType.Builder.of(
                (pos, state) -> new DisplayCaseBlockEntity(holder[0], pos, state), block)
                .build(null);
        return new DisplayCaseBlockEntity(
                holder[0], BlockPos.ZERO, block.defaultBlockState());
    }
}
