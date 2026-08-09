package com.seggellion.britannia_mod.bannerdyeing.registry;

import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationReport;

public record RegistryLoadResult(boolean published, RegistrySnapshot snapshot, ValidationReport report) {
}
