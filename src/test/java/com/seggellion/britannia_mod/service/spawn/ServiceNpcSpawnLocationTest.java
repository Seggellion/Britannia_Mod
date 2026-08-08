package com.seggellion.britannia_mod.service.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ServiceNpcSpawnLocationTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");

    @Test
    void worldNameIsDiagnosticOnlyAndDoesNotAffectIdentity() {
        ServiceNpcSpawnLocation first = new ServiceNpcSpawnLocation("world", OVERWORLD, new BlockPos(1, 64, 2));
        ServiceNpcSpawnLocation renamed = new ServiceNpcSpawnLocation("renamed-world", OVERWORLD, new BlockPos(1, 64, 2));

        assertEquals(first, renamed);
        assertEquals(first.hashCode(), renamed.hashCode());
    }

    @Test
    void dimensionOrCoordinateDifferenceIsStillADifferentIdentity() {
        ServiceNpcSpawnLocation base = new ServiceNpcSpawnLocation("world", OVERWORLD, new BlockPos(1, 64, 2));
        ServiceNpcSpawnLocation differentPos = new ServiceNpcSpawnLocation("world", OVERWORLD, new BlockPos(2, 64, 2));
        ServiceNpcSpawnLocation differentDimension = new ServiceNpcSpawnLocation(
            "world", ResourceLocation.parse("minecraft:the_nether"), new BlockPos(1, 64, 2)
        );

        assertNotEquals(base, differentPos);
        assertNotEquals(base, differentDimension);
    }
}
