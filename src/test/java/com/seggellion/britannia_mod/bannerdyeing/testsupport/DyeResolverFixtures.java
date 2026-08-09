package com.seggellion.britannia_mod.bannerdyeing.testsupport;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.seggellion.britannia_mod.bannerdyeing.registry.DefinitionResource;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistryDataLoader;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistryDomain;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistryLoadResult;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshotPublisher;
import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationPolicy;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import com.seggellion.britannia_mod.dye.colour.ColourMath;
import com.seggellion.britannia_mod.dye.colour.OklabColour;
import com.seggellion.britannia_mod.dye.data.FabricMaterialDefinition;
import com.seggellion.britannia_mod.dye.data.PigmentDefinition;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import com.seggellion.britannia_mod.dye.palette.MaterialPaletteEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public final class DyeResolverFixtures {
    public static final FabricMaterialId MATERIAL_ID = FabricMaterialId.parse("britannia_mod:resolver_material");
    public static final PigmentId PIGMENT_ID = PigmentId.parse("britannia_mod:resolver_pigment");
    public static final ResourceLocation PALETTE_ID = ResourceLocation.parse("britannia_mod:resolver_palette");

    private DyeResolverFixtures() {
    }

    public static PigmentDefinition pigment(String srgb, List<String> tags) {
        return new PigmentDefinition(CoreDataFixtures.SCHEMA, PIGMENT_ID,
                "pigment.britannia_mod.resolver_pigment", srgb, components(ColourMath.toOklab(srgb)), tags,
                "development");
    }

    public static MaterialPaletteEntry entry(
            String path, String srgb, int priority, List<String> tags,
            List<String> allowed, List<String> excluded) {
        return new MaterialPaletteEntry(ResolvedColourId.parse("britannia_mod:" + path),
                "colour.britannia_mod." + path, srgb, components(ColourMath.toOklab(srgb)), priority,
                tags, allowed, excluded);
    }

    public static FabricMaterialDefinition material(ResolvedColourId natural) {
        return new FabricMaterialDefinition(CoreDataFixtures.SCHEMA, MATERIAL_ID,
                "material.britannia_mod.resolver_material", natural, PALETTE_ID, List.of("fabric"));
    }

    public static RegistrySnapshot snapshot(
            PigmentDefinition pigment, FabricMaterialDefinition material, MaterialPalette palette) {
        RegistryLoadResult result = load(List.of(
                resource(RegistryDomain.PIGMENT, "britannia_mod:pigments/resolver.json",
                        PigmentDefinition.CODEC, pigment),
                resource(RegistryDomain.FABRIC_MATERIAL, "britannia_mod:fabric_materials/resolver.json",
                        FabricMaterialDefinition.CODEC, material),
                resource(RegistryDomain.MATERIAL_PALETTE, "britannia_mod:material_palettes/resolver.json",
                        MaterialPalette.CODEC, palette)));
        if (!result.published()) {
            throw new IllegalArgumentException("Invalid resolver fixture: " + result.report().issues());
        }
        return result.snapshot();
    }

    public static RegistrySnapshot productionSnapshot() throws Exception {
        Path dataRoot = Path.of(System.getProperty("britannia.projectDir", "."),"src/main/resources/data/britannia_mod");
        List<DefinitionResource> resources = new ArrayList<>();
        for (RegistryDomain domain : RegistryDomain.values()) {
            Path folder = dataRoot.resolve(domain.folder());
            if (!Files.isDirectory(folder)) {
                continue;
            }
            try (var paths = Files.list(folder)) {
                for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(".json"))
                        .sorted().toList()) {
                    resources.add(DefinitionResource.text(domain,
                            "britannia_mod:" + domain.folder() + "/" + path.getFileName(), Files.readString(path)));
                }
            }
        }
        RegistryLoadResult result = load(resources);
        if (!result.published()) {
            throw new IllegalStateException("Production fixture failed: " + result.report().issues());
        }
        return result.snapshot();
    }

    private static RegistryLoadResult load(List<DefinitionResource> resources) {
        RegistryDataLoader loader = new RegistryDataLoader();
        return loader.apply(loader.prepare(resources), ValidationPolicy.DEVELOPMENT_FAIL_FAST,
                new RegistrySnapshotPublisher());
    }

    private static List<Double> components(OklabColour colour) {
        return List.of(colour.lightness(), colour.a(), colour.b());
    }

    private static <T> DefinitionResource resource(
            RegistryDomain domain, String source, Codec<T> codec, T definition) {
        return DefinitionResource.text(domain, source,
                codec.encodeStart(JsonOps.INSTANCE, definition).getOrThrow().toString());
    }
}
