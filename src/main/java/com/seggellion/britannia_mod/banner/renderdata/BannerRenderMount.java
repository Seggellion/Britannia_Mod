package com.seggellion.britannia_mod.banner.renderdata;

import com.seggellion.britannia_mod.banner.api.MountId;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Display-only mount asset projection synchronized from the authoritative server snapshot. */
public record BannerRenderMount(MountId id, ResourceLocation geometry, ResourceLocation texture) {
    public BannerRenderMount {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(texture, "texture");
    }
}
