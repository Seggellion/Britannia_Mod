package com.seggellion.britannia_mod.crate;

import com.seggellion.britannia_mod.block.entity.CrateStackBlockEntity;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * Opens one crate out of a column, as the crate it is.
 *
 * <h2>The illusion this maintains</h2>
 *
 * <p>A player who opens a crate in a column must get the crate's own inventory, at the crate's own
 * size, under the crate's own name. Nothing about the column — that it holds seven other crates, that
 * they share a block entity, that this one is the fourth from the bottom — belongs in that
 * experience. So the menu is an ordinary {@link ChestMenu} of nine or twenty-seven slots, titled from
 * the variant, exactly as the standalone crate block produces.
 *
 * <p>Bound by crate id, never by index or position. A crate can move down the column while a menu is
 * open, and the menu must keep meaning the same crate; that is what
 * {@link LogicalCrateContainer} resolves on every call, and what this hands to the menu.
 */
public record LogicalCrateMenuProvider(CrateStackBlockEntity stack, int crateId)
        implements MenuProvider {

    public LogicalCrateMenuProvider {
        Objects.requireNonNull(stack, "stack");
    }

    @Override
    public Component getDisplayName() {
        LogicalCrate crate = stack.crateById(crateId);
        return Component.translatable(crate == null
                ? CrateVariant.SMALL.containerTitleKey()
                : crate.variant().containerTitleKey());
    }

    /**
     * @return the crate's own menu, or null if the crate has gone between aiming and opening
     */
    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        LogicalCrate crate = stack.crateById(crateId);
        if (crate == null) {
            return null;
        }
        LogicalCrateContainer container = stack.containerFor(crateId);
        return switch (crate.variant()) {
            case SMALL -> new ChestMenu(MenuType.GENERIC_9x1, containerId, inventory, container, 1);
            case MEDIUM -> ChestMenu.threeRows(containerId, inventory, container);
        };
    }
}
