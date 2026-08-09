package com.seggellion.britannia_mod.service;

public record DialogueSelectionResult(
        DialogueSelectionOutcome outcome,
        ServiceActionResult serviceResult
) {
    public static DialogueSelectionResult navigated() {
        return new DialogueSelectionResult(DialogueSelectionOutcome.NAVIGATED, null);
    }

    public static DialogueSelectionResult closed() {
        return new DialogueSelectionResult(DialogueSelectionOutcome.CLOSED, null);
    }

    public static DialogueSelectionResult service(ServiceActionResult result) {
        return new DialogueSelectionResult(DialogueSelectionOutcome.SERVICE_RESULT, result);
    }

    public static DialogueSelectionResult invalidOption() {
        return new DialogueSelectionResult(DialogueSelectionOutcome.INVALID_OPTION, null);
    }
}
