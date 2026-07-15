package com.seggellion.britannia_mod.service;

import java.util.Set;

public final class ServiceActionDispatcher {
    private static final String UNAVAILABLE_MESSAGE = "Banking services are not available yet.";
    private static final Set<String> SUPPORTED_SERVICES = Set.of("bank.open", "bank.create_check");

    public ServiceActionResult dispatch(ServiceNpcTypeDefinition npcType, String serviceKey) {
        if (!SUPPORTED_SERVICES.contains(serviceKey)) {
            return new ServiceActionResult(
                    ServiceResultCode.UNSUPPORTED_SERVICE,
                    "Unsupported service."
            );
        }
        if (!npcType.allowedServiceKeys().contains(serviceKey)) {
            return new ServiceActionResult(
                    ServiceResultCode.SERVICE_NOT_PERMITTED,
                    "This Service NPC does not permit that service."
            );
        }

        return switch (serviceKey) {
            case "bank.open", "bank.create_check" -> new ServiceActionResult(
                    ServiceResultCode.SERVICE_NOT_AVAILABLE,
                    UNAVAILABLE_MESSAGE
            );
            default -> throw new IllegalStateException("Closed dispatcher accepted an unknown service");
        };
    }
}
