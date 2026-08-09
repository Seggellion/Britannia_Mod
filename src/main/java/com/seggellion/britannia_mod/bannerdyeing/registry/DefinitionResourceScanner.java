package com.seggellion.britannia_mod.bannerdyeing.registry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** Reads only the effective resource at each path, preserving normal pack override semantics. */
final class DefinitionResourceScanner {
    private DefinitionResourceScanner() {
    }

    static List<DefinitionResource> scan(ResourceManager resourceManager) {
        List<DefinitionResource> resources = new ArrayList<>();
        for (RegistryDomain domain : RegistryDomain.values()) {
            Map<ResourceLocation, Resource> effective = resourceManager.listResources(
                    domain.folder(), location -> location.getPath().endsWith(".json"));
            effective.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> resources.add(read(domain, entry.getKey(), entry.getValue())));
        }
        resources.sort(Comparator.comparing((DefinitionResource resource) -> resource.domain().name())
                .thenComparing(resource -> resource.sourceResource().toString()));
        return List.copyOf(resources);
    }

    private static DefinitionResource read(
            RegistryDomain domain, ResourceLocation source, Resource resource) {
        try (BufferedReader reader = resource.openAsReader()) {
            String contents = reader.lines().collect(Collectors.joining("\n"));
            return new DefinitionResource(domain, source, contents, java.util.Optional.empty());
        } catch (IOException | UncheckedIOException exception) {
            return DefinitionResource.unreadable(domain, source, exception.getMessage());
        }
    }
}
