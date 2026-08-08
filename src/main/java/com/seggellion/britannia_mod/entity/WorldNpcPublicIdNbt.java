package com.seggellion.britannia_mod.entity;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;

final class WorldNpcPublicIdNbt {
    static final String KEY = "WorldNpcPublicId";

    private WorldNpcPublicIdNbt() {
    }

    static void write(CompoundTag tag, @Nullable UUID publicId) {
        if (publicId == null) {
            tag.remove(KEY);
        } else {
            tag.putUUID(KEY, publicId);
        }
    }

    @Nullable
    static UUID read(CompoundTag tag) {
        return tag.hasUUID(KEY) ? tag.getUUID(KEY) : null;
    }
}
