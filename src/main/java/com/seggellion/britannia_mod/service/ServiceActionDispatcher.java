package com.seggellion.britannia_mod.service;

import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.service.banking.BankingProxyService;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

public final class ServiceActionDispatcher {
    private static final String UNAVAILABLE_MESSAGE = "Banking services are not available yet.";
    private static final Set<String> SUPPORTED_SERVICES = Set.of("bank.open", "bank.create_check");

    /**
     * Context-free dialogue-menu path. Unchanged by Milestone 7 Slice A: it has no
     * player/entity/world context to act on (see {@link ServiceDialogueContext}, which
     * carries only display strings), and {@link ServiceDialogueController#select} is
     * still its only caller, so {@code bank.open} still resolves to
     * {@code SERVICE_NOT_AVAILABLE} here exactly as before Slice A. This is deliberate,
     * not an oversight: the real flow now lives in {@link #dispatchBankOpen}, reached
     * through direct entity interaction (Slice A), not through this stub dialogue path
     * (which remains reserved for the not-yet-built Slice B screen).
     */
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

    /**
     * The real, live-context {@code bank.open} path: Milestone 7 Slice A's direct entity
     * interaction entry point ({@link ServiceNpcEntity#interactAt}) calls this instead of
     * {@link #dispatch}, since only here is a real {@link ServerPlayer} and
     * {@link ServiceNpcEntity} available to revalidate and dispatch against. Scoped to
     * bank.open only — bank.create_check and every other service key are untouched by
     * this method and continue to resolve only through the stub {@link #dispatch} path
     * above until their own milestone wires them up the same way.
     */
    public static void dispatchBankOpen(ServerPlayer player, ServiceNpcEntity entity) {
        BankingProxyService.handle(player, entity);
    }
}
