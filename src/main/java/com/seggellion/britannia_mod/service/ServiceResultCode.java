package com.seggellion.britannia_mod.service;

public enum ServiceResultCode {
    SERVICE_NOT_AVAILABLE("service_not_available"),
    UNSUPPORTED_SERVICE("unsupported_service"),
    SERVICE_NOT_PERMITTED("service_not_permitted");

    private final String wireName;

    ServiceResultCode(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
