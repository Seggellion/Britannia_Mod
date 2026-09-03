package com.seggellion.britannia_mod.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.block.DisplayCaseBlock;
import com.seggellion.britannia_mod.block.entity.DisplayCaseBlockEntity;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
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

    /**
     * Starfarer M1 characterization.
     *
     * <p>The sibling round-trip test above proves components survive generically, but it
     * carries {@code CUSTOM_NAME}. Nothing anywhere named the blessed identity fields on a
     * display case, so display-case compatibility -- a core acceptance criterion for the
     * Starfarer's Medallion, which is expected to be displayed rather than carried -- was
     * satisfied only by inference.
     *
     * <p>This pins it directly: a blessed stack keeps {@code blessed}, {@code owner} and
     * {@code deed_id} across store -> disk save/load -> client sync -> retrieval. The tag
     * shape mirrors exactly what {@code BlessedItemInventorySync} writes, and matches the
     * bank-side equivalent, {@code BankItemCodecGameTests#blessedMarkerRoundTripPreservesTheBlessedTagShape}.
     *
     * <p>A blessed item living outside a player inventory must not lose its identity, or a
     * later sync would be unable to recognise it.
     */
    @Test
    void blessedIdentityMetadataSurvivesTheDisplayCycle() {
        DisplayCaseBlock block = new DisplayCaseBlock(BlockBehaviour.Properties.of());
        DisplayCaseBlockEntity source = entity(block);

        String ownerUuid = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";
        String deedId = "deed-display-case-1";
        ItemStack blessed = new ItemStack(Items.DIAMOND);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("blessed", true);
        tag.putString("owner", ownerUuid);
        tag.putString("deed_id", deedId);
        blessed.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        assertTrue(source.storeOne(blessed));

        DisplayCaseBlockEntity diskCopy = entity(block);
        diskCopy.loadWithComponents(
                source.saveWithoutMetadata(RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        assertBlessedIdentity(diskCopy.displayedItem(), ownerUuid, deedId, "after a disk round trip");

        DisplayCaseBlockEntity clientCopy = entity(block);
        clientCopy.handleUpdateTag(source.getUpdateTag(RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        assertBlessedIdentity(clientCopy.displayedItem(), ownerUuid, deedId, "after a client sync");

        // Read the tag off the stack the player is actually handed back, not merely off the
        // block entity's field, so the retrieval leg is covered too.
        ItemStack retrieved = diskCopy.takeDisplayedItem();
        assertTrue(retrieved.is(Items.DIAMOND));
        assertBlessedIdentity(retrieved, ownerUuid, deedId, "on the retrieved stack");
    }

    private static void assertBlessedIdentity(
            ItemStack stack, String ownerUuid, String deedId, String stage) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        assertNotNull(data, "blessed custom data was dropped " + stage);
        CompoundTag tag = data.copyTag();
        assertTrue(tag.getBoolean("blessed"), "the blessed flag was lost " + stage);
        assertEquals(ownerUuid, tag.getString("owner"), "the owner uuid was lost " + stage);
        assertEquals(deedId, tag.getString("deed_id"), "the deed id was lost " + stage);
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
