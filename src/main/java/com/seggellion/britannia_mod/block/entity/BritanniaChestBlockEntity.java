// com/seggellion/britannia_mod/content/chest/BritanniaChestBlockEntity.java
package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuConstructor;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;

public class BritanniaChestBlockEntity extends BlockEntity implements MenuProvider, Container {
    public static final int SIZE = 27; // 9x3
    private final SimpleContainer items = new SimpleContainer(SIZE){
        @Override public boolean stillValid(Player player) {
            return BritanniaChestBlockEntity.this.stillValid(player);
        }
    };

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
            // Accept our 9x3 menu as “own” so the counter stays accurate.
            return player.containerMenu instanceof ChestMenu;
        }
    };

    public BritanniaChestBlockEntity(BlockPos pos, BlockState state) {
        this(BlockRegistry.BRITANNIA_CHEST_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    public BritanniaChestBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ----- MenuProvider -----
    @Override public Component getDisplayName() {
        // Let the block supply a translated name; fallback:
        return Component.translatable("container.britannia_chest");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player player) {
        // Use vanilla 9x3 chest UI
        return ChestMenu.threeRows(id, playerInv, this);
    }

    // ----- Container forwarding to 'items' -----
    @Override public int getContainerSize() { return items.getContainerSize(); }
    @Override public boolean isEmpty() { return items.isEmpty(); }
    @Override public net.minecraft.world.item.ItemStack getItem(int slot) { return items.getItem(slot); }
    @Override public net.minecraft.world.item.ItemStack removeItem(int slot, int amount) { return items.removeItem(slot, amount); }
    @Override public net.minecraft.world.item.ItemStack removeItemNoUpdate(int slot) { return items.removeItemNoUpdate(slot); }
    @Override public void setItem(int slot, net.minecraft.world.item.ItemStack stack) { items.setItem(slot, stack); setChanged(); }
    @Override public void clearContent() { items.clearContent(); }
    @Override public boolean stillValid(Player player) {
        if (this.level == null || this.level.getBlockEntity(this.worldPosition) != this) return false;
        return player.distanceToSqr(
                (double) this.worldPosition.getX() + 0.5D,
                (double) this.worldPosition.getY() + 0.5D,
                (double) this.worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    // ----- Open / close hooks (called by block) -----
    public void startOpen(Player player) {
        if (this.level == null || this.level.isClientSide) return;
        this.openersCounter.incrementOpeners(player, this.level, this.worldPosition, this.getBlockState());
    }

    public void stopOpen(Player player) {
        if (this.level == null || this.level.isClientSide) return;
        this.openersCounter.decrementOpeners(player, this.level, this.worldPosition, this.getBlockState());
    }
}
