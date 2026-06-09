package com.seggellion.britannia_mod.teleport;

public record TeleportResult(boolean success, String failureReason, String debugContext) {
    public static TeleportResult success(String debugContext) {
        return new TeleportResult(true, "", debugContext);
    }

    public static TeleportResult failure(String failureReason, String debugContext) {
        return new TeleportResult(false, failureReason, debugContext);
    }
}
