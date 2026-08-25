package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Root-owned, synchronized merchandise state for one display case. */
public final class DisplayCaseBlockEntity extends BlockEntity {
    private static final String DISPLAYED_ITEM_TAG = "DisplayedItem";

    private ItemStack displayedItem = ItemStack.EMPTY;

    public DisplayCaseBlockEntity(BlockPos pos, BlockState state) {
        this(BlockEntityRegistry.DISPLAY_CASE.get(), pos, state);
    }

    /** Test-friendly constructor that also keeps the production registry lookup out of persistence tests. */
    public DisplayCaseBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ItemStack displayedItem() {
        return displayedItem;
    }

    public boolean hasDisplayedItem() {
        return !displayedItem.isEmpty();
    }

    /** Stores exactly one item, preserving every data component on the source stack. */
    public boolean storeOne(ItemStack source) {
        Objects.requireNonNull(source, "source");
        if (source.isEmpty() || hasDisplayedItem()) {
            return false;
        }
        displayedItem = source.copyWithCount(1);
        setChangedAndSync();
        return true;
    }

    /** Removes the merchandise from persistent state and transfers ownership to the caller. */
    public ItemStack takeDisplayedItem() {
        if (displayedItem.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = displayedItem;
        displayedItem = ItemStack.EMPTY;
        setChangedAndSync();
        return taken;
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!displayedItem.isEmpty()) {
            tag.put(DISPLAYED_ITEM_TAG, displayedItem.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        displayedItem = tag.contains(DISPLAYED_ITEM_TAG)
                ? ItemStack.parse(registries, tag.getCompound(DISPLAYED_ITEM_TAG))
                        .orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
