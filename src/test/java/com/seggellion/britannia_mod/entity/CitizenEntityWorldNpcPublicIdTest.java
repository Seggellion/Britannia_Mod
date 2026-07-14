package com.seggellion.britannia_mod.entity;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenEntityWorldNpcPublicIdTest {
    @Test
    void optionalWorldNpcPublicIdRoundTripsWithoutChangingExistingCitizenData() {
        CompoundTag tag = existingCitizenTag();
        UUID publicId = UUID.fromString("d566bde8-1154-4c9c-b18b-819eb6b84ced");

        WorldNpcPublicIdNbt.write(tag, publicId);

        assertTrue(tag.hasUUID(WorldNpcPublicIdNbt.KEY));
        assertEquals(publicId, WorldNpcPublicIdNbt.read(tag));
        assertEquals("Britain", tag.getString("cityName"));
        assertEquals("female", tag.getString("gender"));
        assertEquals("Marian", tag.getString("personalName"));
        assertEquals(2, tag.getInt("hairIndex"));
        assertEquals("banker", tag.getString("outfitKey"));
    }

    @Test
    void legacyCitizenDataWithoutTheKeyRemainsNullAndDoesNotGenerateAnId() {
        CompoundTag legacyTag = existingCitizenTag();

        assertFalse(legacyTag.hasUUID(WorldNpcPublicIdNbt.KEY));
        assertNull(WorldNpcPublicIdNbt.read(legacyTag));
        assertFalse(legacyTag.hasUUID(WorldNpcPublicIdNbt.KEY));
    }

    @Test
    void writingNullKeepsTheFieldAbsent() {
        CompoundTag tag = existingCitizenTag();
        tag.putUUID(WorldNpcPublicIdNbt.KEY, UUID.randomUUID());

        WorldNpcPublicIdNbt.write(tag, null);

        assertFalse(tag.hasUUID(WorldNpcPublicIdNbt.KEY));
        assertNull(WorldNpcPublicIdNbt.read(tag));
    }

    private static CompoundTag existingCitizenTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("cityName", "Britain");
        tag.putString("gender", "female");
        tag.putString("personalName", "Marian");
        tag.putInt("hairIndex", 2);
        tag.putString("outfitKey", "banker");
        return tag;
    }
}
