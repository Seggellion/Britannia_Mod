package com.seggellion.britannia_mod.banner.item;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import com.seggellion.britannia_mod.bannerdyeing.registry.BannerDataRegistries;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** One reusable component-bearing design token for every banner definition. */
public final class BannerPatternItem extends Item {
    private final DataComponentType<BannerDefinitionId> definitionComponent;

    public BannerPatternItem(Properties properties, DataComponentType<BannerDefinitionId> definitionComponent) {
        super(properties);
        this.definitionComponent = Objects.requireNonNull(definitionComponent, "definitionComponent");
    }

    public Optional<BannerDefinitionId> definitionId(ItemStack stack) {
        if (stack.getItem() != this) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.get(definitionComponent));
    }

    public ItemStack configured(BannerDefinitionId definitionId) {
        ItemStack stack = new ItemStack(this);
        stack.set(definitionComponent, Objects.requireNonNull(definitionId, "definitionId"));
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        Optional<BannerDefinitionId> definitionId = definitionId(stack);
        if (definitionId.isEmpty()) {
            return Component.translatable("item.britannia_mod.banner_pattern.unconfigured");
        }
        RegistrySnapshot snapshot = BannerDataRegistries.current();
        return snapshot.banners().find(definitionId.orElseThrow())
                .<Component>map(definition -> Component.translatable(
                        "item.britannia_mod.banner_pattern.configured",
                        Component.translatable(definition.displayNameKey())))
                .orElseGet(() -> Component.translatable(
                        "item.britannia_mod.banner_pattern.missing", definitionId.orElseThrow().toString()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        Optional<BannerDefinitionId> definitionId = definitionId(stack);
        if (definitionId.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.britannia_mod.banner_pattern.unconfigured"));
            return;
        }
        RegistrySnapshot snapshot = BannerDataRegistries.current();
        snapshot.banners().find(definitionId.orElseThrow()).ifPresentOrElse(definition -> {
            tooltip.add(Component.translatable(
                    "tooltip.britannia_mod.banner_pattern.definition",
                    Component.translatable(definition.displayNameKey())));
            if (definition.contentStatus() != BannerContentStatus.COMPLETE
                    || definition.dimensions().provisional()) {
                tooltip.add(Component.translatable("tooltip.britannia_mod.banner_pattern.provisional"));
            }
        }, () -> tooltip.add(Component.translatable(
                "tooltip.britannia_mod.banner_pattern.missing", definitionId.orElseThrow().toString())));
    }
}
