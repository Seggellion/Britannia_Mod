package com.seggellion.britannia_mod.service.spawn;

import com.seggellion.britannia_mod.city.BootstrapCityDefinition;
import com.seggellion.britannia_mod.city.BootstrapCityRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ServiceNpcSpawnConfigurationValidator {
    public static final int MAX_CITY_OPTIONS = 4_096;
    public static final int MAX_TYPE_OPTIONS = 1_024;
    public static final int MAX_TYPE_KEY_BYTES = 64;
    public static final int MAX_LABEL_BYTES = 128;
    private static final Pattern TYPE_KEY = Pattern.compile("^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$");

    private ServiceNpcSpawnConfigurationValidator() {
    }

    public static ServiceNpcSpawnValidationError validate(
            BootstrapCityRegistrySnapshot cities,
            ServiceNpcRegistrySnapshot serviceTypes,
            UUID cityPublicId,
            String typeKey
    ) {
        if (typeKey == null
                || typeKey.getBytes(StandardCharsets.UTF_8).length > MAX_TYPE_KEY_BYTES
                || !TYPE_KEY.matcher(typeKey).matches()) {
            return ServiceNpcSpawnValidationError.OVERSIZED_FIELD;
        }
        if (!cities.available()
                || cities.cities().size() > MAX_CITY_OPTIONS
                || cities.cities().values().stream().map(BootstrapCityDefinition::displayName)
                .anyMatch(label -> !bounded(label, MAX_LABEL_BYTES))) {
            return ServiceNpcSpawnValidationError.REGISTRY_UNAVAILABLE;
        }
        if (cities.find(cityPublicId) == null) return ServiceNpcSpawnValidationError.CITY_UNAVAILABLE;

        long spawnableCount = serviceTypes.serviceNpcTypes().values().stream()
                .filter(ServiceNpcTypeDefinition::active)
                .filter(ServiceNpcTypeDefinition::spawnable)
                .count();
        if (serviceTypes.isEmpty()
                || spawnableCount > MAX_TYPE_OPTIONS
                || serviceTypes.serviceNpcTypes().values().stream()
                .filter(ServiceNpcTypeDefinition::active)
                .filter(ServiceNpcTypeDefinition::spawnable)
                .anyMatch(type ->
                !bounded(type.key(), MAX_TYPE_KEY_BYTES) || !bounded(type.displayName(), MAX_LABEL_BYTES))) {
            return ServiceNpcSpawnValidationError.REGISTRY_UNAVAILABLE;
        }
        ServiceNpcTypeDefinition type = serviceTypes.serviceNpcTypes().get(typeKey);
        if (type == null) return ServiceNpcSpawnValidationError.TYPE_UNAVAILABLE;
        if (!type.active()) return ServiceNpcSpawnValidationError.TYPE_INACTIVE;
        if (!type.spawnable()) return ServiceNpcSpawnValidationError.TYPE_NOT_SPAWNABLE;
        return ServiceNpcSpawnValidationError.NONE;
    }

    private static boolean bounded(String value, int maxBytes) {
        return value != null && value.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
    }
}
