package com.seggellion.britannia_mod.dye.item;

import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.state.DyeTubState;
import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class DyeTubTooltip {
    private DyeTubTooltip() {
    }

    public static List<Component> lines(DyeTubState state, RegistrySnapshot snapshot) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(snapshot, "snapshot");
        if (state.pigmentId().isEmpty()) {
            return List.of(
                    Component.translatable("tooltip.britannia_mod.dye_tub.empty")
                            .withStyle(ChatFormatting.GRAY),
                    Component.translatable("tooltip.britannia_mod.dye_tub.off_hand_hint")
                            .withStyle(ChatFormatting.DARK_GRAY));
        }

        PigmentId pigmentId = state.pigmentId().orElseThrow();
        Component pigmentName = snapshot.pigments().find(pigmentId)
                .<Component>map(definition -> Component.translatable(definition.displayNameKey()))
                .orElseGet(() -> Component.translatable(
                        "tooltip.britannia_mod.dye_tub.missing_pigment", pigmentId.toString()));
        Component uses = state.remainingUses()
                .<Component>map(count -> Component.translatable("tooltip.britannia_mod.dye_tub.uses", count))
                .orElseGet(() -> Component.translatable(
                        "tooltip.britannia_mod.dye_tub.uses",
                        Component.translatable("tooltip.britannia_mod.dye_tub.unlimited")));
        return List.of(
                Component.translatable("tooltip.britannia_mod.dye_tub.contains", pigmentName)
                        .withStyle(ChatFormatting.GRAY),
                uses.copy().withStyle(ChatFormatting.GRAY));
    }
}
