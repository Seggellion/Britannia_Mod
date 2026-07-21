package com.seggellion.britannia_mod.banner.item;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.dye.data.FabricMaterialDefinition;
import com.seggellion.britannia_mod.dye.palette.MaterialPalette;
import com.seggellion.britannia_mod.dye.palette.MaterialPaletteEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Safe display projection. It never resolves colours, repairs state, or mutates an ItemStack. */
public final class BannerTooltip {
    private BannerTooltip() {
    }

    public static List<Component> lines(
            BannerStateValidation validation, RegistrySnapshot snapshot, boolean developmentBuild) {
        Objects.requireNonNull(validation, "validation");
        Objects.requireNonNull(snapshot, "snapshot");
        List<Component> lines = new ArrayList<>();
        if (validation.storedState().isEmpty()) {
            lines.add(Component.translatable("tooltip.britannia_mod.banner.unconfigured")
                    .withStyle(ChatFormatting.YELLOW));
            lines.add(Component.translatable("tooltip.britannia_mod.banner.missing_state")
                    .withStyle(ChatFormatting.RED));
            return List.copyOf(lines);
        }

        BannerInstanceState state = validation.storedState().orElseThrow();
        BannerDefinition definition = snapshot.banners().find(state.bannerDefinitionId()).orElse(null);
        FabricMaterialDefinition material = snapshot.fabricMaterials().find(state.materialId()).orElse(null);
        MaterialPalette palette = material == null ? null
                : snapshot.materialPalettes().find(material.paletteId()).orElse(null);
        MaterialPaletteEntry colour = palette == null ? null : palette.entries().stream()
                .filter(entry -> entry.id().equals(state.resolvedColourId())).findFirst().orElse(null);

        if (definition == null) {
            lines.add(missing("definition", state.bannerDefinitionId().toString()));
        }
        if (material == null) {
            lines.add(missing("material", state.materialId().toString()));
        } else {
            lines.add(Component.translatable("tooltip.britannia_mod.banner.material",
                    Component.translatable(material.displayNameKey())).withStyle(ChatFormatting.GRAY));
        }
        if (colour == null) {
            lines.add(missing("colour", state.resolvedColourId().toString()));
        } else {
            lines.add(Component.translatable("tooltip.britannia_mod.banner.colour",
                    Component.translatable(colour.displayNameKey())).withStyle(ChatFormatting.GRAY));
        }
        state.sourcePigmentId().ifPresent(pigmentId -> snapshot.pigments().find(pigmentId)
                .ifPresentOrElse(
                        pigment -> lines.add(Component.translatable("tooltip.britannia_mod.banner.dyed_with",
                                Component.translatable(pigment.displayNameKey())).withStyle(ChatFormatting.GRAY)),
                        () -> lines.add(missing("pigment", pigmentId.toString()))));
        if (state.sourcePigmentId().isEmpty()) {
            lines.add(Component.translatable("tooltip.britannia_mod.banner.natural")
                    .withStyle(ChatFormatting.GRAY));
        }
        snapshot.mounts().find(state.mountId()).ifPresentOrElse(
                mount -> lines.add(Component.translatable("tooltip.britannia_mod.banner.mount",
                        Component.translatable(mount.displayNameKey())).withStyle(ChatFormatting.GRAY)),
                () -> lines.add(missing("mount", state.mountId().toString())));

        if (definition != null) {
            lines.add(Component.translatable("tooltip.britannia_mod.banner.size",
                    definition.dimensions().widthBlocks(), definition.dimensions().heightBlocks())
                    .withStyle(ChatFormatting.GRAY));
            Component orientations = orientationText(definition);
            lines.add(Component.translatable("tooltip.britannia_mod.banner.placement", orientations)
                    .withStyle(ChatFormatting.GRAY));
            if (developmentBuild && definition.contentStatus() == BannerContentStatus.PLACEHOLDER) {
                lines.add(Component.translatable("tooltip.britannia_mod.banner.placeholder_warning")
                        .withStyle(ChatFormatting.YELLOW));
            }
            if (definition.dimensions().provisional()) {
                lines.add(Component.translatable("tooltip.britannia_mod.banner.provisional_dimensions")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
        if (validation.status() == BannerStateStatus.REPAIRABLE) {
            lines.add(Component.translatable("tooltip.britannia_mod.banner.repairable_colour")
                    .withStyle(ChatFormatting.YELLOW));
        }
        return List.copyOf(lines);
    }

    private static Component orientationText(BannerDefinition definition) {
        boolean parallel = definition.supportedOrientations().contains(BannerOrientation.WALL_PARALLEL);
        boolean perpendicular = definition.supportedOrientations().contains(BannerOrientation.WALL_PERPENDICULAR);
        if (parallel && perpendicular) {
            return Component.translatable("tooltip.britannia_mod.banner.orientation.both",
                    Component.translatable("tooltip.britannia_mod.banner.orientation.parallel"),
                    Component.translatable("tooltip.britannia_mod.banner.orientation.perpendicular"));
        }
        return Component.translatable(parallel
                ? "tooltip.britannia_mod.banner.orientation.parallel"
                : "tooltip.britannia_mod.banner.orientation.perpendicular");
    }

    private static Component missing(String domain, String id) {
        return Component.translatable("tooltip.britannia_mod.banner.missing_" + domain, id)
                .withStyle(ChatFormatting.RED);
    }
}
