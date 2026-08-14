package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.entity.CitizenEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Vendor/Trader Milestone 9: authoritative origin-city provenance on NPC-sold
 * products, following {@link BlacksmithItemData}'s established mechanism — a
 * named compound inside vanilla {@code minecraft:custom_data}. Because
 * custom_data participates in stack equality, stacks with different origins
 * can never silently merge, and vanilla serialization carries the data across
 * save/load, bank round-trips, and drops unchanged.
 *
 * <p>Written SERVER-SIDE ONLY, from the economic projection's reconciler-
 * stamped assignment city (or, from Milestone 14 on, the Rails transaction
 * payload). No client path writes this compound, so a client cannot forge the
 * provenance used by any transaction; server-side flows must treat it as
 * display/audit data and re-derive authority from Rails when it matters.
 */
public final class CityProvenanceItemData {
    private static final String ROOT = "BritanniaCityProvenance";
    private static final String CITY_PUBLIC_ID = "origin_city_public_id";
    private static final String CITY_NAME = "origin_city_name";

    private CityProvenanceItemData() {
    }

    public static void apply(ItemStack stack, UUID cityPublicId, String cityName) {
        if (stack.isEmpty() || cityPublicId == null || cityName == null || cityName.isBlank()) return;
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        CompoundTag data = root.contains(ROOT) ? root.getCompound(ROOT) : new CompoundTag();
        data.putUUID(CITY_PUBLIC_ID, cityPublicId);
        data.putString(CITY_NAME, cityName);
        root.put(ROOT, data);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    /** Stamps from an economic projection's authoritative assignment city. */
    public static void applyFromVendor(ItemStack stack, CitizenEntity vendor) {
        if (vendor.getEconomicCityPublicId() == null) return;
        apply(stack, vendor.getEconomicCityPublicId(), vendor.getCityName());
    }

    public static boolean hasProvenance(ItemStack stack) {
        return !stack.isEmpty() && data(stack).hasUUID(CITY_PUBLIC_ID);
    }

    @Nullable
    public static UUID cityPublicId(ItemStack stack) {
        CompoundTag data = data(stack);
        return data.hasUUID(CITY_PUBLIC_ID) ? data.getUUID(CITY_PUBLIC_ID) : null;
    }

    @Nullable
    public static String cityName(ItemStack stack) {
        String name = data(stack).getString(CITY_NAME);
        return name.isBlank() ? null : name;
    }

    private static CompoundTag data(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag())).copyTag();
        return root.contains(ROOT) ? root.getCompound(ROOT) : new CompoundTag();
    }
}
