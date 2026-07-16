package com.seggellion.britannia_mod.service.spawn;

public final class ServiceNpcSpawnMenuRequestValidator {
    public record Facts(
            boolean authenticated,
            boolean authorized,
            boolean correctMenu,
            boolean correctOwner,
            boolean containerMatches,
            boolean dimensionMatches,
            boolean withinDistance,
            boolean positionMatches,
            boolean blockPresent,
            boolean correctBlock,
            boolean correctBlockEntity,
            boolean uuidMatches,
            boolean menuStillValid,
            boolean revisionMatches
    ) {}

    private ServiceNpcSpawnMenuRequestValidator() {
    }

    public static ServiceNpcSpawnValidationError validate(Facts facts) {
        if (!facts.authenticated()) return ServiceNpcSpawnValidationError.UNAUTHORIZED;
        if (!facts.authorized()) return ServiceNpcSpawnValidationError.UNAUTHORIZED;
        if (!facts.correctMenu()) return ServiceNpcSpawnValidationError.WRONG_MENU;
        if (!facts.correctOwner()) return ServiceNpcSpawnValidationError.WRONG_OWNER;
        if (!facts.containerMatches()) return ServiceNpcSpawnValidationError.CONTAINER_MISMATCH;
        if (!facts.dimensionMatches()) return ServiceNpcSpawnValidationError.WRONG_DIMENSION;
        if (!facts.withinDistance()) return ServiceNpcSpawnValidationError.TOO_FAR;
        if (!facts.positionMatches()) return ServiceNpcSpawnValidationError.WRONG_POSITION;
        if (!facts.blockPresent()) return ServiceNpcSpawnValidationError.MISSING_BLOCK;
        if (!facts.correctBlock()) return ServiceNpcSpawnValidationError.WRONG_BLOCK;
        if (!facts.correctBlockEntity()) return ServiceNpcSpawnValidationError.WRONG_BLOCK_ENTITY;
        if (!facts.uuidMatches()) return ServiceNpcSpawnValidationError.UUID_MISMATCH;
        if (!facts.menuStillValid()) return ServiceNpcSpawnValidationError.WRONG_MENU;
        if (!facts.revisionMatches()) return ServiceNpcSpawnValidationError.STALE_REVISION;
        return ServiceNpcSpawnValidationError.NONE;
    }
}
