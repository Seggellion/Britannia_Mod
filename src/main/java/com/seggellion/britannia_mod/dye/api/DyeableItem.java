package com.seggellion.britannia_mod.dye.api;

import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/** Minimal common-side contract shared by banners and future dyeable textiles. */
public interface DyeableItem {
    DyeableStateRead readDyeableState(ItemStack stack, RegistrySnapshot snapshot, boolean registryAvailable);

    DyeableStateUpdate planColourUpdate(
            ItemStack stack,
            ResolvedColourId resolvedColourId,
            Optional<PigmentId> sourcePigmentId,
            RegistrySnapshot snapshot,
            boolean registryAvailable);

    boolean applyColourUpdate(ItemStack stack, DyeableStateUpdate update);
}
