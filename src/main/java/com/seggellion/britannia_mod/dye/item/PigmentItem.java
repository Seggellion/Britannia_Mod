package com.seggellion.britannia_mod.dye.item;

import com.seggellion.britannia_mod.dye.api.PigmentId;
import java.util.Objects;
import net.minecraft.world.item.Item;

/** One registered item carries one immutable, server-owned pigment identity. */
public final class PigmentItem extends Item {
    private final PigmentId pigmentId;

    public PigmentItem(Properties properties, PigmentId pigmentId) {
        super(properties);
        this.pigmentId = Objects.requireNonNull(pigmentId, "pigmentId");
    }

    public PigmentId pigmentId() {
        return pigmentId;
    }
}
