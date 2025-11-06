// com/seggellion/britannia_mod/block/entity/ArmoireBlockEntity.java
package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.ModSounds;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;

public class ArmoireBlockEntity extends BlockEntity implements MenuProvider, Container {
    public static final int SIZE = 36; // 9x4 storage

    private final SimpleContainer items = new SimpleContainer(SIZE) {
        @Override
        public boolean stillValid(Player player) {
            return ArmoireBlockEntity.this.stillValid(player);
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

    // ----- MenuProvider -----
    @Override
    public Component getDisplayName() {
        return Component.translatable("container.armoire");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player player) {
        // Vanilla only supports 3-row chests by default; 4 rows requires custom menu if desired
        // For simplicity, still use 3-row UI — easy to swap later if you want a taller GUI
        return ChestMenu.threeRows(id, playerInv, this);
    }

    // ----- Container delegation -----
    @Override public int getContainerSize() { return items.getContainerSize(); }
    @Override public boolean isEmpty() { return items.isEmpty(); }
    @Override public net.minecraft.world.item.ItemStack getItem(int slot) { return items.getItem(slot); }
    @Override public net.minecraft.world.item.ItemStack removeItem(int slot, int amount) { return items.removeItem(slot, amount); }
    @Override public net.minecraft.world.item.ItemStack removeItemNoUpdate(int slot) { return items.removeItemNoUpdate(slot); }
    @Override public void setItem(int slot, net.minecraft.world.item.ItemStack stack) { items.setItem(slot, stack); setChanged(); }
    @Override public void clearContent() { items.clearContent(); }

    @Override
    public boolean stillValid(Player player) {
        if (this.level == null || this.level.getBlockEntity(this.worldPosition) != this) return false;
        return player.distanceToSqr(
                (double)this.worldPosition.getX() + 0.5D,
                (double)this.worldPosition.getY() + 0.5D,
                (double)this.worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    // ----- Open/close management -----
    public void startOpen(Player player) {
        if (this.level == null || this.level.isClientSide) return;
        this.openersCounter.incrementOpeners(player, this.level, this.worldPosition, this.getBlockState());
    }

    public void stopOpen(Player player) {
        if (this.level == null || this.level.isClientSide) return;
        this.openersCounter.decrementOpeners(player, this.level, this.worldPosition, this.getBlockState());
    }
}
