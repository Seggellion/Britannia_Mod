package com.seggellion.britannia_mod.service.banking;

import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the "do not hardcode always bank" gate reads live capability from the same
 * registry cache {@link com.seggellion.britannia_mod.service.ServiceActionDispatcher}
 * already trusts, not a hardcoded key.
 */
class BankingCapabilityTest {
    @AfterEach
    void clearRegistry() {
        ServiceNpcRegistryCache.clear();
    }

    @Test
    void aTellerWhoseTypeAllowsBankOpenSupportsIt() {
        ServiceNpcRegistryCache.replace(registryWith(bankTeller("bank_teller", true, List.of("bank.open"))));
        assertTrue(BankingCapability.supportsBankOpen("bank_teller"));
    }

    @Test
    void aTellerWhoseTypeDoesNotAllowBankOpenDoesNotSupportIt() {
        ServiceNpcRegistryCache.replace(registryWith(bankTeller("guide", true, List.of())));
        assertFalse(BankingCapability.supportsBankOpen("guide"));
    }

    @Test
    void anInactiveServiceTypeDoesNotSupportBankOpenEvenIfAllowlisted() {
        ServiceNpcRegistryCache.replace(registryWith(bankTeller("bank_teller", false, List.of("bank.open"))));
        assertFalse(BankingCapability.supportsBankOpen("bank_teller"));
    }

    @Test
    void anUnknownServiceNpcTypeKeyDoesNotSupportBankOpen() {
        ServiceNpcRegistryCache.replace(registryWith(bankTeller("bank_teller", true, List.of("bank.open"))));
        assertFalse(BankingCapability.supportsBankOpen("nonexistent_type"));
    }

    @Test
    void nullOrBlankKeyDoesNotSupportBankOpen() {
        ServiceNpcRegistryCache.replace(registryWith(bankTeller("bank_teller", true, List.of("bank.open"))));
        assertFalse(BankingCapability.supportsBankOpen(null));
        assertFalse(BankingCapability.supportsBankOpen(""));
    }

    @Test
    void emptyRegistrySupportsNothing() {
        assertFalse(BankingCapability.supportsBankOpen("bank_teller"));
    }

    private static ServiceNpcTypeDefinition bankTeller(String key, boolean active, List<String> allowedServiceKeys) {
        return new ServiceNpcTypeDefinition(
                key, "Bank Teller", "banker", "britannia_mod:service_npc",
                key + "_default", allowedServiceKeys, active, true, 1
        );
    }

    private static ServiceNpcRegistrySnapshot registryWith(ServiceNpcTypeDefinition type) {
        return new ServiceNpcRegistrySnapshot(1, 1, Map.of(), Map.of(type.key(), type), Map.of());
    }
}
