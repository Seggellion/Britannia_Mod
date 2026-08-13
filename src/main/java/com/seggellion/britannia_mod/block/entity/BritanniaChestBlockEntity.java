package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.block.BritanniaLockableChestBlock;
import com.seggellion.britannia_mod.grabbyhands.GrabbyInstanceState;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPortableState;
import com.seggellion.britannia_mod.grabbyhands.GrabbyProvenanceHolder;
import com.seggellion.britannia_mod.grabbyhands.GrabbyTransportRefusal;
import net.minecraft.world.item.BlockItem;
import com.seggellion.britannia_mod.item.ChestKeyItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;
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

import java.util.Optional;
import java.util.UUID;

public class BritanniaChestBlockEntity extends BlockEntity
        implements MenuProvider, Container, GrabbyProvenanceHolder, GrabbyPortableState {
    private static final String TAG_LOCK_ID = "LockId";
    private static final String TAG_CHEST_KEY_SEEDED = "ChestKeySeeded";
    private static final String TAG_LOCKED = "Locked";
    private static final String TAG_LOCK_DIFFICULTY = "LockDifficulty";
    
    // 1. We replace SimpleContainer with a direct List of ItemStacks (Vanilla Style)
    // 27 slots, initialized with empty air.
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    private UUID lockId;
    private boolean chestKeySeeded;
    private boolean locked;
    private int lockDifficulty = 1;
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
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int p_155364_, int p_155365_) {}

        @Override
        protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof ChestMenu;
        }
    };

    public BritanniaChestBlockEntity(BlockPos pos, BlockState state) {
        this(BlockRegistry.BRITANNIA_CHEST_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    public BritanniaChestBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        if (state.getBlock() instanceof BritanniaLockableChestBlock) {
            this.lockId = UUID.randomUUID();
            this.locked = false;
            this.lockDifficulty = getDefaultDifficulty(state);
            this.seedChestKeyIfNeeded();
        }
    }

    // =============================================================
    //   SAVING & LOADING (The Fix)
    // =============================================================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // ContainerHelper handles the heavy lifting of saving the list to NBT automatically
        ContainerHelper.saveAllItems(tag, this.items, registries);
        if (this.lockId != null) {
            tag.putUUID(TAG_LOCK_ID, this.lockId);
        }
        tag.putBoolean(TAG_CHEST_KEY_SEEDED, this.chestKeySeeded);
        tag.putBoolean(TAG_LOCKED, this.locked);
        tag.putInt(TAG_LOCK_DIFFICULTY, this.lockDifficulty);
        grabbyState.write(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // Reset items to empty before loading
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        // Load the items from NBT
        ContainerHelper.loadAllItems(tag, this.items, registries);
        if (tag.hasUUID(TAG_LOCK_ID)) {
            this.lockId = tag.getUUID(TAG_LOCK_ID);
        }
        this.chestKeySeeded = tag.getBoolean(TAG_CHEST_KEY_SEEDED);
        this.locked = tag.getBoolean(TAG_LOCKED);
        this.lockDifficulty = tag.contains(TAG_LOCK_DIFFICULTY) ? Math.clamp(tag.getInt(TAG_LOCK_DIFFICULTY), 1, 9) : getDefaultDifficulty(this.getBlockState());
        this.grabbyState = GrabbyInstanceState.read(tag);
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

    public UUID getOrCreateLockId() {
        if (this.lockId == null) {
            this.lockId = UUID.randomUUID();
            this.setChanged();
        }
        return this.lockId;
    }

    public void seedChestKeyIfNeeded() {
        if (this.chestKeySeeded || !(this.getBlockState().getBlock() instanceof BritanniaLockableChestBlock)) return;

        UUID id = this.getOrCreateLockId();
        ItemStack key = ((ChestKeyItem) ItemRegistry.CHEST_KEY.get()).createKey(id);
        for (int i = 0; i < this.items.size(); i++) {
            if (this.items.get(i).isEmpty()) {
                this.items.set(i, key);
                this.chestKeySeeded = true;
                this.setChanged();
                return;
            }
        }
    }

    public boolean isLocked() {
        return this.locked;
    }

    public void setLocked(boolean locked) {
        if (!(this.getBlockState().getBlock() instanceof BritanniaLockableChestBlock)) return;
        this.locked = locked;
        this.setChanged();
    }

    public int getLockDifficulty() {
        return Math.clamp(this.lockDifficulty, 1, 9);
    }

    public void setLockDifficulty(int lockDifficulty) {
        this.lockDifficulty = Math.clamp(lockDifficulty, 1, 9);
        this.setChanged();
    }

    private static int getDefaultDifficulty(BlockState state) {
        if (state.getBlock() instanceof BritanniaLockableChestBlock lockableChest) {
            return lockableChest.getDefaultDifficulty();
        }
        return 1;
    }

    // =============================================================
    //   CONTAINER IMPLEMENTATION
    //   We implement these manually to ensure setChanged() is ALWAYS called.
    // =============================================================

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    public boolean isEmpty() {
        // Efficiently check if all slots are empty
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
        // ContainerHelper.removeItem handles splitting the stack
        ItemStack result = ContainerHelper.removeItem(this.items, slot, amount);
        if (!result.isEmpty()) {
            this.setChanged(); // CRITICAL: Mark dirty on removal
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = ContainerHelper.takeItem(this.items, slot);
        if (!result.isEmpty()) {
            this.setChanged(); // CRITICAL: Mark dirty on removal
        }
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.items.set(slot, stack);
        // Clamp stack size if it exceeds the max
        if (stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }
        this.setChanged(); // CRITICAL: Mark dirty on addition
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
    //   MENUS & EVENTS
    // =============================================================

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.britannia_chest");
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
    public boolean securedAgainstDestruction() {
        // Only while actually locked. An unlocked chest is ordinary furniture and may be chopped.
        return this.locked;
    }

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
