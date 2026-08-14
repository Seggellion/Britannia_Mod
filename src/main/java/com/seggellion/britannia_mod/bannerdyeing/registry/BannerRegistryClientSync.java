package com.seggellion.britannia_mod.bannerdyeing.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.data.MountDefinition;
import com.seggellion.britannia_mod.banner.data.PlacementProfile;
import com.seggellion.britannia_mod.dye.data.FabricMaterialDefinition;
import com.seggellion.britannia_mod.dye.data.PigmentDefinition;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/**
 * Wire representation and client-side publication path for synchronizing the full
 * {@link RegistrySnapshot} to remote clients, so every client-side consumer of
 * {@link BannerDataRegistries} (creative tab population, item tooltips, dye preview screens)
 * behaves identically in multiplayer and singleplayer. Lives in this package deliberately: it is
 * the one sanctioned caller of the package-private {@link DefinitionRegistry} constructor and
 * {@link BannerDataRegistries#publish} outside the datapack reload path.
 *
 * <p>The wire form carries only each domain's ACTIVE definitions, re-encoded with the exact
 * data-file codecs the reload listener parses ({@code X.CODEC}), so a synced snapshot can never
 * diverge structurally from a locally loaded one. Disabled entries are deliberately not sent:
 * they exist for operator diagnostics on the authoritative side ("disabled" vs "missing"), and a
 * remote client reporting MISSING where the server would say DISABLED affects display-only
 * diagnostic text, never behaviour. Validation issues are likewise not sent -- they belong to the
 * server's own publication ({@link BannerDataRegistries#validationIssues} is keyed on the exact
 * publication instance) and are logged server-side where the operator reads them.
 */
public final class BannerRegistryClientSync {
    /** Synthesized {@link DefinitionEntry#sourceResource()} for entries that arrived by wire. */
    private static final ResourceLocation SYNCED_SOURCE =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "synced_from_server");

    public static final Codec<RegistrySnapshot> WIRE_CODEC = RecordCodecBuilder.<RegistrySnapshot>create(
            instance -> instance.group(
                    BannerDefinition.CODEC.listOf().fieldOf("banners")
                            .forGetter(snapshot -> snapshot.banners().activeDefinitions()),
                    FabricMaterialDefinition.CODEC.listOf().fieldOf("fabric_materials")
                            .forGetter(snapshot -> snapshot.fabricMaterials().activeDefinitions()),
                    PigmentDefinition.CODEC.listOf().fieldOf("pigments")
                            .forGetter(snapshot -> snapshot.pigments().activeDefinitions()),
                    MaterialPalette.CODEC.listOf().fieldOf("material_palettes")
                            .forGetter(snapshot -> snapshot.materialPalettes().activeDefinitions()),
                    MountDefinition.CODEC.listOf().fieldOf("mounts")
                            .forGetter(snapshot -> snapshot.mounts().activeDefinitions()),
                    PlacementProfile.CODEC.listOf().fieldOf("placement_profiles")
                            .forGetter(snapshot -> snapshot.placementProfiles().activeDefinitions())
            ).apply(instance, BannerRegistryClientSync::fromActiveDefinitions));

    private BannerRegistryClientSync() {
    }

    public static RegistrySnapshot fromActiveDefinitions(
            List<BannerDefinition> banners,
            List<FabricMaterialDefinition> fabricMaterials,
            List<PigmentDefinition> pigments,
            List<MaterialPalette> materialPalettes,
            List<MountDefinition> mounts,
            List<PlacementProfile> placementProfiles) {
        return new RegistrySnapshot(
                registryOf(banners, BannerDefinition::id),
                registryOf(fabricMaterials, FabricMaterialDefinition::id),
                registryOf(pigments, PigmentDefinition::id),
                registryOf(materialPalettes, MaterialPalette::id),
                registryOf(mounts, MountDefinition::id),
                registryOf(placementProfiles, PlacementProfile::id));
    }

    /**
     * Publishes a server-synced snapshot into the common lookup entry point. Only ever called on
     * a remote client (the payload handler guards out the integrated-server case, where the
     * server's own datapack publication in the shared JVM is authoritative and must not be
     * replaced by a wire round-trip of itself).
     */
    public static void publishFromServerSync(RegistrySnapshot snapshot) {
        BannerDataRegistries.publish(snapshot, List.of());
    }

    private static <I, T> DefinitionRegistry<I, T> registryOf(List<T> definitions, Function<T, I> idOf) {
        Map<I, DefinitionEntry<I, T>> active = new LinkedHashMap<>();
        for (T definition : definitions) {
            I id = idOf.apply(definition);
            DefinitionEntry<I, T> previous = active.put(id,
                    new DefinitionEntry<>(id, definition, SYNCED_SOURCE));
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate synced definition id " + id);
            }
        }
        return new DefinitionRegistry<>(active, List.of());
    }
}
