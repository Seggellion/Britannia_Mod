package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.blessed.BlessedItemLifecycleMetadata;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.jetbrains.annotations.Nullable;

public class TrashBarrelBlockEntity extends BlockEntity implements Container, MenuProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    private int timer = 0;
    private static final int DELETE_INTERVAL = 3600; // 3 minutes in ticks

    public TrashBarrelBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.TRASH_BARREL_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TrashBarrelBlockEntity be) {
        if (be.isEmpty()) {
            be.timer = 0; // Reset timer if barrel is cleared manually
            return;
        }

        be.timer++;

        if (be.timer >= DELETE_INTERVAL) {
            int deleted = be.clearOrdinaryContents();
            be.timer = 0;
            be.setChanged();

            // Nothing was ordinary, so nothing was emptied. Saying "Emptying the trash barrel!"
            // over a barrel holding only a protected item would be a lie about what just happened.
            if (deleted == 0) {
                return;
            }

            // Display the UO-style message above the block
            Component message = Component.literal("Emptying the trash barrel!").withStyle(ChatFormatting.GOLD);
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 1.2;
            double z = pos.getZ() + 0.5;

            // Send message to nearby players
            for (Player p : level.players()) {
                if (p.distanceToSqr(x, y, z) < 64) {
                    // 'true' puts it in the action bar overlay (above hotbar)
                    p.displayClientMessage(message, true);
                }
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Trash Barrel");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        // Built-in Generic 3x9 (27 slots) handler
        return ChestMenu.threeRows(containerId, playerInventory, this);
    }

    // --- NBT Data (1.21 standard) ---
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items, provider);
        this.timer = tag.getInt("Timer");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        ContainerHelper.saveAllItems(tag, this.items, provider);
        tag.putInt("Timer", this.timer);
    }

    // --- Container Interface Implementation ---
    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemstack : this.items) {
            if (!itemstack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return this.items.get(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack result = ContainerHelper.removeItem(this.items, index, count);
        if (!result.isEmpty()) this.setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        return ContainerHelper.takeItem(this.items, index);
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        this.items.set(index, stack);
        stack.limitSize(this.getMaxStackSize());
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    /**
     * Empties the barrel of everything the timer is allowed to destroy, leaving blessed items
     * where they are.
     *
     * <p>The timed clear used to be an unconditional {@code items.clear()}, which silently
     * annihilated whatever was inside -- including a permanent account entitlement, with no
     * signal to Rails and no way to tell it had happened. A medallion dropped in here was simply
     * gone. Playbook 16.2 requires that this cannot happen.
     *
     * <p>Protection, not destruction reporting: the item survives, its materialization stays
     * active, and nothing is reported. Preserving the item is strictly better than manufacturing
     * a destroyed state and asking an operator to restore it.
     *
     * <p>Blessed-ness is judged by {@link BlessedItemLifecycleMetadata#isBlessed}, so a pre-M6
     * legacy blessed deed is protected too even though it has no materialization identity --
     * losing one of those is just as bad, and the barrel is not the place to draw that
     * distinction.
     *
     * <p>Deliberately implemented here rather than in {@link #clearContent()}: this is the only
     * caller in the mod, and the timed sweep is the behaviour that needed narrowing. A caller
     * that explicitly asks to clear the container still gets exactly that.
     *
     * @return how many ordinary stacks were removed
     */
    public int clearOrdinaryContents() {
        int removed = 0;
        for (int slot = 0; slot < this.items.size(); slot++) {
            ItemStack stack = this.items.get(slot);
            if (stack.isEmpty()) continue;

            if (BlessedItemLifecycleMetadata.isBlessed(stack)) {
                LOGGER.info("Trash barrel spared a blessed item at {} slot {} item={}",
                        this.worldPosition, slot, stack.getItem());
                continue;
            }

            this.items.set(slot, ItemStack.EMPTY);
            removed++;
        }
        return removed;
    }

    @Override
    public void clearContent() {
        this.items.clear();
        this.setChanged();
    }
}