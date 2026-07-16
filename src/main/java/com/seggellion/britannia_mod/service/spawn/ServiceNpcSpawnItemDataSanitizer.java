package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.nbt.CompoundTag;

public final class ServiceNpcSpawnItemDataSanitizer {
    private ServiceNpcSpawnItemDataSanitizer() {
    }

    public static void strip(CompoundTag tag) {
        java.util.Set.copyOf(tag.getAllKeys()).forEach(tag::remove);
    }
}
