package com.seggellion.britannia_mod.service;

import com.seggellion.britannia_mod.dialogue.DialogueLayout;
import com.seggellion.britannia_mod.dialogue.DialogueViewModel;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceDialogueControllerTest {
    @Test
    void createsTheOrderedBankTellerViewModelFromPersistentDefinitions() {
        ServiceDialogueController controller = ServiceDialogueController.open(
                bankRegistry(),
                "bank_teller",
                new ServiceDialogueContext("Marian", "female", "Bank Teller", "Britain")
        );

        DialogueViewModel view = controller.viewModel();

        assertEquals("Marian", view.npcName());
        assertEquals("female", view.npcGender());
        assertEquals("Bank Teller", view.professionLabel());
        assertEquals("Welcome to the bank of Britain. How may I assist you?", view.body());
        assertEquals(List.of("open_bank_box", "create_bank_check", "goodbye"),
                view.options().stream().map(option -> option.id()).toList());
        assertEquals(List.of("Open my bank box.", "I would like to create a bank check.", "Goodbye."),
                view.options().stream().map(option -> option.label()).toList());
        assertFalse(view.completed());

        DialogueLayout layout = DialogueLayout.calculate(800, 2, view.options().size(), 9, true);
        assertEquals(163, layout.textX());
        assertEquals(640, layout.buttonStartX());
        assertEquals(457, layout.maxTextWidth());
        assertEquals(3, view.options().size());
    }

    @Test
    void optionSelectionResolvesServerOwnedActionAndStubDoesNotMutateDefinitions() {
        ServiceNpcRegistrySnapshot registry = bankRegistry();
        ServiceNpcTypeDefinition originalType = registry.serviceNpcTypes().get("bank_teller");
        ServiceDialogueController controller = ServiceDialogueController.open(
                registry,
                "bank_teller",
                new ServiceDialogueContext("Marian", "female", "Bank Teller", "Britain")
        );

        DialogueSelectionResult result = controller.select("open_bank_box");

        assertEquals(DialogueSelectionOutcome.SERVICE_RESULT, result.outcome());
        assertEquals(ServiceResultCode.SERVICE_NOT_AVAILABLE, result.serviceResult().code());
        assertEquals("service_not_available", result.serviceResult().code().wireName());
        assertEquals("Banking services are not available yet.", result.serviceResult().message());
        assertEquals("greeting", controller.currentNodeKey());
        assertFalse(controller.isClosed());
        assertEquals(originalType, registry.serviceNpcTypes().get("bank_teller"));
        assertEquals(List.of("bank.open", "bank.create_check"), originalType.allowedServiceKeys());

        DialogueSelectionResult checkResult = controller.select("create_bank_check");
        assertEquals(DialogueSelectionOutcome.SERVICE_RESULT, checkResult.outcome());
        assertEquals(ServiceResultCode.SERVICE_NOT_AVAILABLE, checkResult.serviceResult().code());
        assertEquals("Banking services are not available yet.", checkResult.serviceResult().message());
    }

    @Test
    void unknownAndDisallowedServicesReturnDistinctClosedResults() {
        ServiceActionDispatcher dispatcher = new ServiceActionDispatcher();
        ServiceNpcTypeDefinition onlyOpen = new ServiceNpcTypeDefinition(
                "bank_teller",
                "Bank Teller",
                "banker",
                "britannia_mod:service_npc",
                "bank_teller_default",
                List.of("bank.open"),
                true,
                true,
                1
        );

        assertEquals(ServiceResultCode.UNSUPPORTED_SERVICE,
                dispatcher.dispatch(onlyOpen, "bank.withdraw").code());
        assertEquals("unsupported_service",
                dispatcher.dispatch(onlyOpen, "bank.withdraw").code().wireName());
        assertEquals(ServiceResultCode.SERVICE_NOT_PERMITTED,
                dispatcher.dispatch(onlyOpen, "bank.create_check").code());
        assertEquals("service_not_permitted",
                dispatcher.dispatch(onlyOpen, "bank.create_check").code().wireName());
    }

    @Test
    void closeAndNavigationAreLocalTypedTransitions() {
        ServiceDialogueController bank = ServiceDialogueController.open(
                bankRegistry(),
                "bank_teller",
                new ServiceDialogueContext("Marian", "female", "Bank Teller", "Britain")
        );
        assertEquals(DialogueSelectionOutcome.CLOSED, bank.select("goodbye").outcome());
        assertTrue(bank.isClosed());
        assertTrue(bank.viewModel().options().isEmpty());
        assertEquals(DialogueSelectionOutcome.INVALID_OPTION, bank.select("open_bank_box").outcome());

        ServiceDialogueController guide = ServiceDialogueController.open(
                navigationRegistry(),
                "guide",
                new ServiceDialogueContext("Julia", "female", "Guide", "Britain")
        );
        assertEquals(DialogueSelectionOutcome.NAVIGATED, guide.select("continue").outcome());
        assertEquals("details", guide.currentNodeKey());
        assertEquals("Details for Julia.", guide.viewModel().body());
    }

    @Test
    void interpolationNeverEvaluatesUnknownTemplates() {
        ServiceDialogueContext context = new ServiceDialogueContext("Marian", "female", "Bank Teller", "Britain");

        assertEquals("Marian serves Britain as Bank Teller.",
                SafeDialogueInterpolator.interpolate(
                        "%{npc_name} serves %{city_name} as %{profession_name}.",
                        context
                ));
        assertThrows(IllegalArgumentException.class,
                () -> SafeDialogueInterpolator.interpolate("%{player_class}", context));
        assertThrows(IllegalArgumentException.class,
                () -> SafeDialogueInterpolator.interpolate("%s", context));
    }

    @Test
    void stubDispatchHasNoInventoryCurrencyQuestWorldOrNetworkMutationSurface() {
        Map<String, Integer> inventory = new LinkedHashMap<>(Map.of("britannia_mod:gold", 25));
        Map<String, Integer> currency = new LinkedHashMap<>(Map.of("bank_balance", 100));
        Map<String, String> questState = new LinkedHashMap<>(Map.of("active_quest", "escort_1"));
        Map<String, Integer> worldState = new LinkedHashMap<>(Map.of("spawned_entities", 3));
        Map<String, Integer> networkState = new LinkedHashMap<>(Map.of("sent_packets", 0));
        Map<String, Integer> inventoryBefore = Map.copyOf(inventory);
        Map<String, Integer> currencyBefore = Map.copyOf(currency);
        Map<String, String> questBefore = Map.copyOf(questState);
        Map<String, Integer> worldBefore = Map.copyOf(worldState);
        Map<String, Integer> networkBefore = Map.copyOf(networkState);

        ServiceDialogueController controller = ServiceDialogueController.open(
                bankRegistry(),
                "bank_teller",
                new ServiceDialogueContext("Marian", "female", "Bank Teller", "Britain")
        );
        controller.select("open_bank_box");
        controller.select("create_bank_check");

        assertEquals(inventoryBefore, inventory);
        assertEquals(currencyBefore, currency);
        assertEquals(questBefore, questState);
        assertEquals(worldBefore, worldState);
        assertEquals(networkBefore, networkState);
    }

    private static ServiceNpcRegistrySnapshot bankRegistry() {
        Map<String, ServiceActionDefinition> actions = actions();
        ServiceDialogueNodeDefinition greeting = new ServiceDialogueNodeDefinition(
                "greeting",
                "Welcome to the bank of %{city_name}. How may I assist you?",
                List.of(
                        new ServiceDialogueOptionDefinition(
                                "open_bank_box",
                                "Open my bank box.",
                                DialogueActionType.INVOKE_SERVICE,
                                null,
                                "bank.open"
                        ),
                        new ServiceDialogueOptionDefinition(
                                "create_bank_check",
                                "I would like to create a bank check.",
                                DialogueActionType.INVOKE_SERVICE,
                                null,
                                "bank.create_check"
                        ),
                        new ServiceDialogueOptionDefinition(
                                "goodbye",
                                "Goodbye.",
                                DialogueActionType.CLOSE,
                                null,
                                null
                        )
                )
        );
        ServiceDialogueSetDefinition dialogue =
                new ServiceDialogueSetDefinition("bank_teller_default", "greeting", List.of(greeting), 1);
        ServiceNpcTypeDefinition npcType = new ServiceNpcTypeDefinition(
                "bank_teller",
                "Bank Teller",
                "banker",
                "britannia_mod:service_npc",
                "bank_teller_default",
                List.of("bank.open", "bank.create_check"),
                true,
                true,
                1
        );
        return new ServiceNpcRegistrySnapshot(
                1,
                10,
                actions,
                Map.of(npcType.key(), npcType),
                Map.of(dialogue.key(), dialogue)
        );
    }

    private static ServiceNpcRegistrySnapshot navigationRegistry() {
        ServiceDialogueSetDefinition dialogue = new ServiceDialogueSetDefinition(
                "guide_default",
                "greeting",
                List.of(
                        new ServiceDialogueNodeDefinition(
                                "greeting",
                                "Hello.",
                                List.of(new ServiceDialogueOptionDefinition(
                                        "continue",
                                        "Continue.",
                                        DialogueActionType.NAVIGATE,
                                        "details",
                                        null
                                ))
                        ),
                        new ServiceDialogueNodeDefinition(
                                "details",
                                "Details for %{npc_name}.",
                                List.of(new ServiceDialogueOptionDefinition(
                                        "close_details",
                                        "Goodbye.",
                                        DialogueActionType.CLOSE,
                                        null,
                                        null
                                ))
                        )
                ),
                1
        );
        ServiceNpcTypeDefinition guide = new ServiceNpcTypeDefinition(
                "guide",
                "Guide",
                "guide",
                "britannia_mod:service_npc",
                "guide_default",
                List.of(),
                true,
                false,
                1
        );
        return new ServiceNpcRegistrySnapshot(
                1,
                11,
                actions(),
                Map.of(guide.key(), guide),
                Map.of(dialogue.key(), dialogue)
        );
    }

    private static Map<String, ServiceActionDefinition> actions() {
        Map<String, ServiceActionDefinition> actions = new LinkedHashMap<>();
        actions.put("bank.open", new ServiceActionDefinition("bank.open", "Open bank account", "Open."));
        actions.put("bank.create_check", new ServiceActionDefinition("bank.create_check", "Create bank check", "Check."));
        return actions;
    }
}
