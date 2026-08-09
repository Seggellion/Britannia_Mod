package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceNpcSpawnItemDataTest {
    @Test
    void stripsEveryBlockEntityFieldFromItemData() {
        CompoundTag tag = new CompoundTag();
        tag.putString("SpawnPointId", "id");
        tag.putString("CityPublicId", "city");
        tag.putString("ServiceNpcTypeKey", "bank_teller");
        tag.putBoolean("Enabled", false);
        tag.putLong("ConfigurationRevision", 7L);
        tag.putString("RegistrationState", "REGISTERED");
        tag.putString("AssignedNpcPublicId", "npc");
        tag.putString("IdentityDimension", "minecraft:overworld");
        tag.putString("UnknownFutureField", "also must not copy");

        ServiceNpcSpawnItemDataSanitizer.strip(tag);

        assertTrue(tag.isEmpty());
    }
}
