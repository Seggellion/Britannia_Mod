package com.seggellion.britannia_mod.resource.deposit;

import com.seggellion.britannia_mod.resource.shape.ShapeRotation;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepositRemovalLedgerTest {
    private static final UUID OPERATION = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final BlockPos ORIGIN = new BlockPos(10, 32, 20);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void writeAheadProgressAndCompletedTombstoneRoundTripWithoutActiveDeposit() {
        DepositLedger ledger = new DepositLedger();
        DepositInstance instance = instance(0x8f00aaL, "v1|admin|world_admin_map|fixture");
        ledger.register(instance);

        DepositLedger.RemovalRegistration begun = ledger.beginRemoval(OPERATION, instance, 3);
        assertTrue(begun.accepted());
        assertFalse(begun.record().completed());
        ledger.recordRemovalProgress(OPERATION, 30, 2);

        DepositLedger afterProgress = new DepositLedger(ledger.save(new CompoundTag(), null), null);
        DepositRemovalRecord progress = afterProgress.removalByOperation(OPERATION).orElseThrow();
        assertEquals(30, progress.removedBlocks());
        assertEquals(2, progress.depletedDebts());
        assertTrue(afterProgress.byId(instance.instanceId()).isPresent());

        DepositRemovalRecord completed = afterProgress.completeRemoval(OPERATION, 1234L);
        assertTrue(completed.completed());
        assertEquals(1234L, completed.completedAt());
        assertTrue(afterProgress.byId(instance.instanceId()).isEmpty());

        DepositLedger restarted = new DepositLedger(afterProgress.save(new CompoundTag(), null), null);
        assertTrue(restarted.byId(instance.instanceId()).isEmpty());
        assertEquals(completed, restarted.removalByOperation(OPERATION).orElseThrow());
        assertEquals(completed, restarted.removalByInstance(instance.instanceId()).orElseThrow());
    }

    @Test
    void sameOperationReplaysButSecondOperationOrIdentityCollisionIsRefused() {
        DepositLedger ledger = new DepositLedger();
        DepositInstance instance = instance(0x8f00aaL, "v1|admin|world_admin_map|fixture");
        ledger.register(instance);
        assertTrue(ledger.beginRemoval(OPERATION, instance, 0).accepted());
        assertTrue(ledger.beginRemoval(OPERATION, instance, 0).accepted());
        assertFalse(ledger.beginRemoval(UUID.randomUUID(), instance, 0).accepted());

        DepositInstance impostor = instance(0x8f00aaL, "v1|admin|world_admin_map|other");
        assertFalse(ledger.beginRemoval(OPERATION, impostor, 0).accepted());
    }

    private static DepositInstance instance(long id, String sourceIdentity) {
        return new DepositInstance(id, "britannia_mod:iron", 1, DepositSource.ADMIN,
                sourceIdentity, ORIGIN, 7L, 1, ShapeRotation.XZ,
                ORIGIN.offset(-1, -4, -1), ORIGIN.offset(1, 4, 1), 35,
                35, 0, DepositInstance.CURRENT_MATERIALIZATION_VERSION);
    }
}
