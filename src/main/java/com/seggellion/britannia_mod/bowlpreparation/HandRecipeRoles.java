package com.seggellion.britannia_mod.bowlpreparation;

import java.util.Optional;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Immutable role and actual-slot snapshot; equal-item roles deterministically use MAIN as driver. */
public record HandRecipeRoles(InteractionHand driverHand, ItemStack expectedMain, ItemStack expectedOff) {
    public HandRecipeRoles {
        expectedMain = expectedMain.copy();
        expectedOff = expectedOff.copy();
    }
    @Override public ItemStack expectedMain() { return expectedMain.copy(); }
    @Override public ItemStack expectedOff() { return expectedOff.copy(); }
    public InteractionHand ingredientHand() { return driverHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND; }
    public boolean matches(Player player) {
        return ItemStack.matches(expectedMain, player.getMainHandItem()) && ItemStack.matches(expectedOff, player.getOffhandItem())
                && player.getMainHandItem() != player.getOffhandItem();
    }
    public static Optional<HandRecipeRoles> match(ItemStack main, ItemStack off, Item driver, Item ingredient) {
        if (main.isEmpty() || off.isEmpty() || main == off) return Optional.empty();
        if (main.is(driver) && off.is(ingredient)) return Optional.of(new HandRecipeRoles(InteractionHand.MAIN_HAND, main, off));
        if (off.is(driver) && main.is(ingredient)) return Optional.of(new HandRecipeRoles(InteractionHand.OFF_HAND, main, off));
        return Optional.empty();
    }
}
