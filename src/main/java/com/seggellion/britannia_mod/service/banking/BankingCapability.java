package com.seggellion.britannia_mod.service.banking;

import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;

import javax.annotation.Nullable;

/**
 * Whether a given {@code serviceNpcTypeKey} currently permits {@code bank.open}, looked up
 * from the same {@link ServiceNpcRegistryCache} snapshot the rest of the mod already trusts
 * (populated server-side by world bootstrap). This is the "do not hardcode always bank"
 * gate: a teller only triggers the banking flow if its live, Rails-published type
 * definition says so, exactly like {@link com.seggellion.britannia_mod.service.ServiceActionDispatcher}
 * already checks for the dialogue-menu path.
 */
public final class BankingCapability {
    static final String BANK_OPEN_SERVICE_KEY = "bank.open";

    private BankingCapability() {
    }

    public static boolean supportsBankOpen(@Nullable String serviceNpcTypeKey) {
        if (serviceNpcTypeKey == null || serviceNpcTypeKey.isBlank()) return false;
        ServiceNpcTypeDefinition definition = ServiceNpcRegistryCache.snapshot().serviceNpcTypes().get(serviceNpcTypeKey);
        return definition != null && definition.active() && definition.allowedServiceKeys().contains(BANK_OPEN_SERVICE_KEY);
    }
}
