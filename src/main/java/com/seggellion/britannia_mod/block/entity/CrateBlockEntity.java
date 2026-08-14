package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.block.CrateBlock;
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
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;

/** The sole persistent inventory for any small, medium, or large crate structure. */
public final class CrateBlockEntity extends BlockEntity implements MenuProvider, Container {
    private NonNullList<ItemStack> items;

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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
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
