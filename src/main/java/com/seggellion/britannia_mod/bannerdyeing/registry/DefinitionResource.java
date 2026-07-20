package com.seggellion.britannia_mod.bannerdyeing.registry;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** An effective resource selected by normal resource-pack priority rules. */
public record DefinitionResource(
        RegistryDomain domain,
        ResourceLocation sourceResource,
        String contents,
        Optional<String> readError) {

    public DefinitionResource {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(sourceResource, "sourceResource");
        contents = Objects.requireNonNull(contents, "contents");
        readError = Objects.requireNonNull(readError, "readError");
    }

    public static DefinitionResource text(RegistryDomain domain, String sourceResource, String contents) {
        return new DefinitionResource(domain, ResourceLocation.parse(sourceResource), contents, Optional.empty());
    }

    static DefinitionResource unreadable(
            RegistryDomain domain, ResourceLocation sourceResource, String message) {
        return new DefinitionResource(domain, sourceResource, "", Optional.of(message));
    }
}
