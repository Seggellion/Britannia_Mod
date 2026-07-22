package com.seggellion.britannia_mod.banner.preview;

import com.seggellion.britannia_mod.banner.structure.BannerCellRole;
import com.seggellion.britannia_mod.banner.structure.BannerLocalOffset;
import net.minecraft.core.BlockPos;

public record BannerPlacementPreviewCell(
        BannerLocalOffset offset,
        BlockPos worldPosition,
        BannerCellRole role,
        boolean blocked) {
    public BannerPlacementPreviewCell {
        java.util.Objects.requireNonNull(offset, "offset");
        worldPosition = worldPosition.immutable();
        java.util.Objects.requireNonNull(role, "role");
    }
}
