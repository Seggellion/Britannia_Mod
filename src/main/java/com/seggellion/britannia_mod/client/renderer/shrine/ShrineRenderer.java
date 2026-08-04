package com.seggellion.britannia_mod.client.renderer.shrine;

import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/** One display-only renderer registered solely for the authoritative anchor entity. */
public final class ShrineRenderer extends GeoBlockRenderer<LargeStructureAnchorBlockEntity> {
    public ShrineRenderer(BlockEntityRendererProvider.Context context) {
        super(new ShrineGeoModel());
    }

    @Override
    public AABB getRenderBoundingBox(LargeStructureAnchorBlockEntity anchor) {
        return anchor.getRenderBoundingBox();
    }
}
