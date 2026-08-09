package com.seggellion.britannia_mod.service;

public enum DialogueActionType {
    NAVIGATE("navigate"),
    INVOKE_SERVICE("invoke_service"),
    CLOSE("close");

    private final String wireName;

    DialogueActionType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static DialogueActionType fromWireName(String value) {
        for (DialogueActionType type : values()) {
            if (type.wireName.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported dialogue action type: " + value);
    }
}
