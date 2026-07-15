package com.seggellion.britannia_mod.service;

import com.seggellion.britannia_mod.dialogue.DialogueOptionViewModel;
import com.seggellion.britannia_mod.dialogue.DialogueViewModel;

import java.util.List;
import java.util.Objects;

public final class ServiceDialogueController {
    private final ServiceNpcTypeDefinition npcType;
    private final ServiceDialogueSetDefinition dialogue;
    private final ServiceDialogueContext context;
    private final ServiceActionDispatcher dispatcher;
    private String currentNodeKey;
    private boolean closed;

    private ServiceDialogueController(
            ServiceNpcTypeDefinition npcType,
            ServiceDialogueSetDefinition dialogue,
            ServiceDialogueContext context,
            ServiceActionDispatcher dispatcher
    ) {
        this.npcType = npcType;
        this.dialogue = dialogue;
        this.context = context;
        this.dispatcher = dispatcher;
        this.currentNodeKey = dialogue.entryNodeKey();
    }

    public static ServiceDialogueController open(
            ServiceNpcRegistrySnapshot registry,
            String npcTypeKey,
            ServiceDialogueContext context
    ) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(context, "context");
        ServiceNpcTypeDefinition npcType = registry.serviceNpcTypes().get(npcTypeKey);
        if (npcType == null || !npcType.active()) {
            throw new IllegalArgumentException("Unknown or inactive Service NPC type: " + npcTypeKey);
        }
        ServiceDialogueSetDefinition dialogue = registry.dialogueSets().get(npcType.defaultDialogueKey());
        if (dialogue == null || !dialogue.nodesByKey().containsKey(dialogue.entryNodeKey())) {
            throw new IllegalArgumentException("Service NPC type has no valid default dialogue: " + npcTypeKey);
        }
        return new ServiceDialogueController(npcType, dialogue, context, new ServiceActionDispatcher());
    }

    public DialogueViewModel viewModel() {
        ServiceDialogueNodeDefinition node = currentNode();
        List<DialogueOptionViewModel> options = closed
                ? List.of()
                : node.options().stream()
                    .map(option -> new DialogueOptionViewModel(
                            option.id(),
                            SafeDialogueInterpolator.interpolate(option.label(), context),
                            false
                    ))
                    .toList();
        return new DialogueViewModel(
                context.npcName(),
                context.npcGender(),
                npcType.displayName(),
                node.key(),
                SafeDialogueInterpolator.interpolate(node.body(), context),
                "service",
                closed,
                options
        );
    }

    public DialogueSelectionResult select(String optionId) {
        if (closed || optionId == null) {
            return DialogueSelectionResult.invalidOption();
        }

        ServiceDialogueOptionDefinition selected = currentNode().options().stream()
                .filter(option -> option.id().equals(optionId))
                .findFirst()
                .orElse(null);
        if (selected == null) {
            return DialogueSelectionResult.invalidOption();
        }

        return switch (selected.actionType()) {
            case NAVIGATE -> {
                currentNodeKey = selected.targetNodeKey();
                yield DialogueSelectionResult.navigated();
            }
            case INVOKE_SERVICE -> DialogueSelectionResult.service(
                    dispatcher.dispatch(npcType, selected.serviceKey())
            );
            case CLOSE -> {
                closed = true;
                yield DialogueSelectionResult.closed();
            }
        };
    }

    public boolean isClosed() {
        return closed;
    }

    public String currentNodeKey() {
        return currentNodeKey;
    }

    private ServiceDialogueNodeDefinition currentNode() {
        ServiceDialogueNodeDefinition node = dialogue.nodesByKey().get(currentNodeKey);
        if (node == null) {
            throw new IllegalStateException("Current service dialogue node is missing: " + currentNodeKey);
        }
        return node;
    }
}
