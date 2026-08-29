package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.grabbyhands.GrabbyInstanceState;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPortableState;
import com.seggellion.britannia_mod.grabbyhands.GrabbyProvenanceHolder;
import com.seggellion.britannia_mod.grabbyhands.GrabbyTransportRefusal;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.CustomData;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;

/** The sole persistent inventory for any small, medium, or large crate structure. */
public final class CrateBlockEntity extends BlockEntity
        implements MenuProvider, Container, GrabbyProvenanceHolder, GrabbyPortableState {
    private NonNullList<ItemStack> items;
    private static final String TAG_ORIGIN = "OriginOffset";

    private GrabbyInstanceState grabbyState = GrabbyInstanceState.worldPlaced();

    /**
     * How far below its own cells this crate's art is drawn, in hundredths of a voxel.
     *
     * <p>Zero for every crate that stands on the ground, which is every crate saved before crates
     * could stand on each other - so the field is absent from their data and reads back as zero
     * without a migration. Negative for a large crate resting on another large crate's lid, which is
     * below the first cell it is allowed to occupy.
     */
    private int originHundredths;

    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            level.playSound(null, pos, ModSounds.CHEST_OPEN.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            level.playSound(null, pos, ModSounds.CHEST_CLOSE.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        @Override
        protected void openerCountChanged(
                Level level, BlockPos pos, BlockState state, int previousCount, int newCount) {
        }

        @Override
        protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof ChestMenu;
        }
    };

    public CrateBlockEntity(BlockPos pos, BlockState state) {
        this(BlockRegistry.CRATE_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    public CrateBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.items = NonNullList.withSize(slotCount(state), ItemStack.EMPTY);
    }

    private static int slotCount(BlockState state) {
        if (state.getBlock() instanceof CrateBlock crate) {
            return crate.slotCount();
        }
        throw new IllegalArgumentException("CrateBlockEntity requires a CrateBlock state");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        grabbyState.write(tag);
        if (originHundredths != 0) {
            tag.putInt(TAG_ORIGIN, originHundredths);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        grabbyState = GrabbyInstanceState.read(tag);
        originHundredths = tag.getInt(TAG_ORIGIN);
    }

    /* ─── standing on another crate ──────────────────────────── */

    /** How far below its own cells this crate's art is drawn. Zero for a crate on the ground. */
    public int originHundredths() {
        return originHundredths;
    }

    /** Whether this crate is resting on something below the cells it occupies. */
    public boolean hasFoundation() {
        return originHundredths != 0;
    }

    /** Moves the whole crate to a new physical origin, contents and identity untouched. */
    public void setOriginHundredths(int origin) {
        if (origin != originHundredths) {
            originHundredths = origin;
            setChanged();
        }
    }

    /**
     * What a client needs to draw this crate, which is where it stands and nothing else.
     *
     * <p>Deliberately not {@code saveAdditional}: a large crate holds fifty-four slots, and
     * broadcasting them to everyone who can see it - on every change - to decide a rendering offset
     * would be an enormous amount of traffic for a picture that never shows an item. Contents reach a
     * player through the menu they open, exactly as before.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (originHundredths != 0) {
            tag.putInt(TAG_ORIGIN, originHundredths);
        }
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Reads that offset back and asks for a redraw, for the same reason a column does.
     *
     * <p>A block entity change does not mark a chunk section dirty, and this crate is chunk-baked
     * terrain, so without this a crate that started resting on another would keep its old geometry
     * until something unrelated disturbed the section.
     */
    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        originHundredths = tag.getInt(TAG_ORIGIN);
        redraw();
    }

    @Override
    public void onDataPacket(
            Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            handleUpdateTag(tag, registries);
        }
    }

    /** Marks the sections this crate draws into as needing a rebuild. Client only. */
    private void redraw() {
        if (level == null || !level.isClientSide) {
            return;
        }
        for (int cell = -1; cell <= 2; cell++) {
            BlockPos pos = worldPosition.above(cell);
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos),
                    Block.UPDATE_ALL);
        }
    }

    @Override
    public GrabbyInstanceState grabbyState() {
        return grabbyState;
    }

    @Override
    public void setGrabbyState(GrabbyInstanceState state) {
        this.grabbyState = Objects.requireNonNull(state, "state");
        setChanged();
    }

    @Override
    public int occupiedSlotCount() {
        int occupied = 0;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                occupied++;
            }
        }
        return occupied;
    }

    /**
     * Saves wholesale rather than field by field, so a field added to this block entity later travels
     * without anyone having to remember this method.
     */
    @Override
    public void writePortableState(ItemStack portable, HolderLookup.Provider registries) {
        CompoundTag data = new CompoundTag();
        saveAdditional(data, registries);
        // Provenance is stamped fresh by the placement transaction; carrying the old placer would be
        // both pointless and misleading.
        data.remove(GrabbyInstanceState.TAG_KEY);
        // Where a crate was standing is a fact about that spot, not about the crate. A carried crate
        // that remembered it would be drawn sunk into the ground wherever it was put down next.
        data.remove(TAG_ORIGIN);
        BlockItem.setBlockEntityData(portable, getType(), data);
    }

    /**
     * Undoes a detach, and nothing more. Detaching only cleared the contents, so only the contents
     * come back; reloading the whole tag would also reset provenance the transaction has decided.
     */
    @Override
    public void restorePortableState(ItemStack portable, HolderLookup.Provider registries) {
        CompoundTag data = portable
                .getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY)
                .copyTag();
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(data, items, registries);
        setChanged();
    }

    @Override
    public boolean detachForTransport() {
        if (isEmpty()) {
            return false;
        }
        // The contents are already captured in the portable item. Clearing them here is what stops
        // the dismantle cascade spilling a second copy onto the floor during pickup.
        clearContent();
        return true;
    }

    @Override
    public Optional<GrabbyTransportRefusal> transportRefusal() {
        if (openersCounter.getOpenerCount() > 0) {
            return Optional.of(GrabbyTransportRefusal.IN_USE);
        }
        for (ItemStack stack : items) {
            if (holdsContents(stack)) {
                return Optional.of(GrabbyTransportRefusal.NESTED_CONTAINER);
            }
        }
        return Optional.empty();
    }

    /** Whether a stack is itself a container carrying something. */
    private static boolean holdsContents(ItemStack stack) {
        return !stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).isEmpty();
    }

    @Override
    public int getContainerSize() {
        return slotCount(getBlockState());
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        setChanged();
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public Component getDisplayName() {
        if (getBlockState().getBlock() instanceof CrateBlock crate) {
            return Component.translatable(crate.containerTitleKey());
        }
        return Component.translatable("container.britannia_mod.crate");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return switch (getContainerSize()) {
            case 9 -> new ChestMenu(MenuType.GENERIC_9x1, containerId, inventory, this, 1);
            case 27 -> ChestMenu.threeRows(containerId, inventory, this);
            case 54 -> ChestMenu.sixRows(containerId, inventory, this);
            default -> throw new IllegalStateException("Unsupported crate inventory size");
        };
    }

    @Override
    public void startOpen(Player player) {
        if (!isRemoved() && level != null && !level.isClientSide) {
            openersCounter.incrementOpeners(player, level, worldPosition, getBlockState());
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (!isRemoved() && level != null && !level.isClientSide) {
            openersCounter.decrementOpeners(player, level, worldPosition, getBlockState());
        }
    }
}
