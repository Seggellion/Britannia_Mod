package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.grabbyhands.GrabbyInstanceState;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPortableState;
import com.seggellion.britannia_mod.grabbyhands.GrabbyProvenanceHolder;
import com.seggellion.britannia_mod.grabbyhands.GrabbyTransportRefusal;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.BlockItem;
import java.util.Optional;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;

public class ArmoireBlockEntity extends BlockEntity
        implements MenuProvider, Container, GrabbyProvenanceHolder, GrabbyPortableState {
    
    // Adjusted to 27 to match ChestMenu.threeRows (Vanilla UI). 
    // If you want 36+ slots, you must create a custom MenuType, as Vanilla has no 4-row UI.
    public static final int SIZE = 27; 

    // Replaced SimpleContainer with direct NonNullList (The Vanilla Way)
    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private GrabbyInstanceState grabbyState = GrabbyInstanceState.worldPlaced();

    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            level.playSound(null, pos, ModSounds.CHEST_OPEN.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            level.playSound(null, pos, ModSounds.CHEST_CLOSE.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int prevCount, int newCount) {}

        @Override
        protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof ChestMenu;
        }
    };

    public ArmoireBlockEntity(BlockPos pos, BlockState state) {
        this(BlockRegistry.ARMOIRE_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    public ArmoireBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public GrabbyInstanceState grabbyState() {
        return grabbyState;
    }

    @Override
    public void setGrabbyState(GrabbyInstanceState state) {
        this.grabbyState = java.util.Objects.requireNonNull(state, "state");
        setChanged();
    }


    // =============================================================
    //   DATA PERSISTENCE (The Fix)
    // =============================================================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // Writes the inventory list to the NBT tag
        ContainerHelper.saveAllItems(tag, this.items, registries);
        grabbyState.write(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // Resets list and reads from NBT tag
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items, registries);
        this.grabbyState = GrabbyInstanceState.read(tag);
    }

    // =============================================================
    //   CONTAINER IMPLEMENTATION
    // =============================================================

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(this.items, slot, amount);
        if (!result.isEmpty()) {
            this.setChanged(); // Ensures the game knows to save
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(this.items, slot);
        if (!result.isEmpty()) {
            this.setChanged();
        }
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.items.set(slot, stack);
        if (stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }
        this.setChanged(); // Ensures the game knows to save
    }

    @Override
    public void clearContent() {
        this.items.clear();
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    // =============================================================
    //   MENU & EVENTS
    // =============================================================

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.armoire");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player player) {
        return ChestMenu.threeRows(id, playerInv, this);
    }

    public void startOpen(Player player) {
        if (this.level != null && !this.level.isClientSide) {
            this.openersCounter.incrementOpeners(player, this.level, this.worldPosition, this.getBlockState());
        }
    }

    // =============================================================
    //   GRABBY HANDS TRANSPORT
    // =============================================================

    /**
     * Contents ride in {@code BLOCK_ENTITY_DATA}, which {@code BlockItem.place} already restores.
     *
     * <p>Placement therefore needs no container-specific step: vanilla's
     * {@code updateCustomBlockEntityTag} loads this straight back into the freshly created block
     * entity. Deliberately not written into {@code getCloneItemStack}, because creative middle-click
     * uses that and would become an inventory duplicator.
     */
    @Override
    public int occupiedSlotCount() {
        int occupied = 0;
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) {
                occupied++;
            }
        }
        return occupied;
    }

    @Override
    public void writePortableState(ItemStack portable, HolderLookup.Provider registries) {
        // Everything saveAdditional writes, not just the contents. For the lockable chest that means
        // the lock id, the locked flag, the difficulty and - critically - the ChestKeySeeded marker:
        // LockpickingEventHandler calls seedChestKeyIfNeeded() on every right-click, so a chest that
        // forgot it had already been keyed would mint a fresh key on every place/pickup cycle.
        //
        // Saving wholesale rather than field by field also means a field added here later travels
        // without anyone remembering to update this method.
        CompoundTag data = new CompoundTag();
        this.saveAdditional(data, registries);
        // Provenance is stamped fresh by the placement transaction; carrying the old placer would be
        // both pointless and misleading.
        data.remove(GrabbyInstanceState.TAG_KEY);
        BlockItem.setBlockEntityData(portable, this.getType(), data);
    }

    /**
     * Undoes a detach, and nothing more.
     *
     * <p>Deliberately narrower than {@link #writePortableState}: detaching only cleared the contents,
     * so only the contents come back. Reloading the whole tag here would also reset provenance, which
     * the transaction has already decided.
     */
    @Override
    public void restorePortableState(ItemStack portable, HolderLookup.Provider registries) {
        CompoundTag data = portable.getOrDefault(
                net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(data, this.items, registries);
        this.setChanged();
    }

    @Override
    public boolean detachForTransport() {
        if (this.isEmpty()) {
            return false;
        }
        // The contents are already captured in the portable item. Clearing them here is what stops
        // onRemove spilling a second copy onto the floor during pickup.
        this.clearContent();
        return true;
    }

    @Override
    public Optional<GrabbyTransportRefusal> transportRefusal() {
        if (this.openersCounter.getOpenerCount() > 0) {
            return Optional.of(GrabbyTransportRefusal.IN_USE);
        }
        for (ItemStack stack : this.items) {
            if (holdsContents(stack)) {
                return Optional.of(GrabbyTransportRefusal.NESTED_CONTAINER);
            }
        }
        return Optional.empty();
    }

    /** Whether a stack is itself a container carrying something. */
    private static boolean holdsContents(ItemStack stack) {
        return !stack.getOrDefault(
                        net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
                        net.minecraft.world.item.component.CustomData.EMPTY)
                .isEmpty();
    }

    public void stopOpen(Player player) {
        if (this.level != null && !this.level.isClientSide) {
            this.openersCounter.decrementOpeners(player, this.level, this.worldPosition, this.getBlockState());
        }
    }
}