package com.seggellion.britannia_mod.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class AdaptiveRoofBlockEntityDataTest {
    private static final ResourceLocation STONE =
            ResourceLocation.withDefaultNamespace("block/stone");

    @Test
    void savedTextureRoundTripsAndAbsentDataClearsLegacyState() {
        CompoundTag tag = new CompoundTag();

        AdaptiveRoofBlockEntity.writeTexture(tag, STONE, false);
        assertEquals(STONE, AdaptiveRoofBlockEntity.readTexture(tag));

        AdaptiveRoofBlockEntity.writeTexture(tag, null, false);
        assertFalse(tag.contains("BottomTexture"));
        assertNull(AdaptiveRoofBlockEntity.readTexture(tag));
    }

    @Test
    void updateTagCarriesAnExplicitClearSentinel() {
        CompoundTag tag = new CompoundTag();

        AdaptiveRoofBlockEntity.writeTexture(tag, null, true);

        assertTrue(tag.contains("BottomTexture"));
        assertEquals("minecraft:block/air", tag.getString("BottomTexture"));
        assertNull(AdaptiveRoofBlockEntity.readTexture(tag));
    }

    @Test
    void malformedTextureDataIsTreatedAsCleared() {
        CompoundTag tag = new CompoundTag();
        tag.putString("BottomTexture", "not a valid resource id");

        assertNull(AdaptiveRoofBlockEntity.readTexture(tag));
    }
}
