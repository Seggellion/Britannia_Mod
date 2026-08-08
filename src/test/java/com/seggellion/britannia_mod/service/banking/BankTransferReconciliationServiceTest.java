package com.seggellion.britannia_mod.service.banking;

import com.seggellion.britannia_mod.bank.transfer.BankTransferReceiptStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.time.Duration;

/**
 * Isolated from any real {@link net.minecraft.server.MinecraftServer} or the real, shared,
 * concurrently-batched-GameTest receipt store on purpose -- see {@link
 * BankTransferReconciliationService#reconcile}'s own docs for why the empty-scan case
 * specifically needs this isolation to be safely, precisely testable at all.
 */
class BankTransferReconciliationServiceTest {
    @Test
    void anEmptyScanReturnsImmediatelyWithoutTouchingTheServer() {
        BankTransferReceiptStore.ScanResult empty = new BankTransferReceiptStore.ScanResult(List.of(), List.of(), List.of());

        // server is null: if reconcile() touched it in the empty-scan branch at all, this would
        // throw a NullPointerException instead of returning cleanly.
        assertTimeout(Duration.ofSeconds(1), () -> BankTransferReconciliationService.reconcile(null, empty));
    }
}
