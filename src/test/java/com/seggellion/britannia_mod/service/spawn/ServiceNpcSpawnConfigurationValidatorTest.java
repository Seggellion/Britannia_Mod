package com.seggellion.britannia_mod.service.spawn;

import com.seggellion.britannia_mod.city.BootstrapCityDefinition;
import com.seggellion.britannia_mod.city.BootstrapCityRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceDialogueSetDefinition;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceNpcSpawnConfigurationValidatorTest {
    private final UUID cityId = UUID.randomUUID();

    @Test
    void acceptsOnlyBootstrapCityAndActiveSpawnableType() {
        assertEquals(ServiceNpcSpawnValidationError.NONE,
                ServiceNpcSpawnConfigurationValidator.validate(cities(), registry(true, true), cityId, "bank_teller"));
        assertEquals(ServiceNpcSpawnValidationError.CITY_UNAVAILABLE,
                ServiceNpcSpawnConfigurationValidator.validate(cities(), registry(true, true), UUID.randomUUID(), "bank_teller"));
    }

    @Test
    void unavailableRegistriesAndInvalidKeysFailClosed() {
        assertEquals(ServiceNpcSpawnValidationError.REGISTRY_UNAVAILABLE,
                ServiceNpcSpawnConfigurationValidator.validate(
                        BootstrapCityRegistrySnapshot.unavailable(), registry(true, true), cityId, "bank_teller"));
        assertEquals(ServiceNpcSpawnValidationError.REGISTRY_UNAVAILABLE,
                ServiceNpcSpawnConfigurationValidator.validate(cities(), ServiceNpcRegistrySnapshot.empty(), cityId, "bank_teller"));
        assertEquals(ServiceNpcSpawnValidationError.OVERSIZED_FIELD,
                ServiceNpcSpawnConfigurationValidator.validate(cities(), registry(true, true), cityId, "Bank Teller"));
    }

    @Test
    void registriesWithoutConfigurableOptionsAreUnavailable() {
        assertEquals(ServiceNpcSpawnValidationError.REGISTRY_UNAVAILABLE,
                ServiceNpcSpawnConfigurationValidator.validate(
                        BootstrapCityRegistrySnapshot.available(List.of()),
                        registry(true, true),
                        cityId,
                        "bank_teller"
                ));

        ServiceNpcRegistrySnapshot dialogueOnly = new ServiceNpcRegistrySnapshot(
                1,
                1L,
                Map.of(),
                Map.of(),
                Map.of("bank_default", new ServiceDialogueSetDefinition(
                        "bank_default", "start", List.of(), 1L
                ))
        );
        assertEquals(ServiceNpcSpawnValidationError.REGISTRY_UNAVAILABLE,
                ServiceNpcSpawnConfigurationValidator.validate(cities(), dialogueOnly, cityId, "bank_teller"));
        assertEquals(ServiceNpcSpawnValidationError.REGISTRY_UNAVAILABLE,
                ServiceNpcSpawnConfigurationValidator.validate(cities(), registry(false, true), cityId, "bank_teller"));
        assertEquals(ServiceNpcSpawnValidationError.REGISTRY_UNAVAILABLE,
                ServiceNpcSpawnConfigurationValidator.validate(cities(), registry(true, false), cityId, "bank_teller"));
    }

    @Test
    void selectedNonConfigurableTypeReportsItsSpecificErrorWhenOtherOptionsExist() {
        ServiceNpcTypeDefinition active = new ServiceNpcTypeDefinition(
                "bank_teller", "Bank Teller", "banker", "minecraft:villager", "bank_default",
                List.of("open_bank"), true, true, 1L
        );
        ServiceNpcTypeDefinition inactive = new ServiceNpcTypeDefinition(
                "retired", "Retired", "retired", "minecraft:villager", "retired_default",
                List.of(), false, true, 1L
        );
        ServiceNpcTypeDefinition unspawnable = new ServiceNpcTypeDefinition(
                "trainer", "Trainer", "trainer", "minecraft:villager", "trainer_default",
                List.of(), true, false, 1L
        );
        ServiceNpcRegistrySnapshot snapshot = new ServiceNpcRegistrySnapshot(
                1,
                1L,
                Map.of(),
                Map.of(active.key(), active, inactive.key(), inactive, unspawnable.key(), unspawnable),
                Map.of()
        );

        assertEquals(ServiceNpcSpawnValidationError.TYPE_INACTIVE,
                ServiceNpcSpawnConfigurationValidator.validate(cities(), snapshot, cityId, inactive.key()));
        assertEquals(ServiceNpcSpawnValidationError.TYPE_NOT_SPAWNABLE,
                ServiceNpcSpawnConfigurationValidator.validate(cities(), snapshot, cityId, unspawnable.key()));
    }

    @Test
    void unsentInactiveTypesDoNotMakeTheSpawnableOptionRegistryUnavailable() {
        ServiceNpcTypeDefinition active = new ServiceNpcTypeDefinition(
                "bank_teller", "Bank Teller", "banker", "minecraft:villager", "bank_default",
                List.of("open_bank"), true, true, 1L
        );
        ServiceNpcTypeDefinition inactive = new ServiceNpcTypeDefinition(
                "retired", "x".repeat(129), "retired", "minecraft:villager", "retired_default",
                List.of(), false, false, 1L
        );
        ServiceNpcRegistrySnapshot snapshot = new ServiceNpcRegistrySnapshot(
                1, 1L, Map.of(), Map.of(active.key(), active, inactive.key(), inactive), Map.of()
        );

        assertEquals(ServiceNpcSpawnValidationError.NONE,
                ServiceNpcSpawnConfigurationValidator.validate(cities(), snapshot, cityId, active.key()));
    }

    private BootstrapCityRegistrySnapshot cities() {
        return BootstrapCityRegistrySnapshot.available(List.of(new BootstrapCityDefinition(cityId, "Britain")));
    }

    private ServiceNpcRegistrySnapshot registry(boolean active, boolean spawnable) {
        ServiceNpcTypeDefinition type = new ServiceNpcTypeDefinition(
                "bank_teller", "Bank Teller", "banker", "minecraft:villager", "bank_default",
                List.of("open_bank"), active, spawnable, 1L
        );
        return new ServiceNpcRegistrySnapshot(1, 1L, Map.of(), Map.of(type.key(), type), Map.of());
    }
}
