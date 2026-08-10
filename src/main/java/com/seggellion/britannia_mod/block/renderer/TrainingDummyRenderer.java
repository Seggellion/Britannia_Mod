package com.seggellion.britannia_mod.block.renderer;

import com.seggellion.britannia_mod.block.entity.TrainingDummyBlockEntity;
import com.seggellion.britannia_mod.block.model.TrainingDummyModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public final class TrainingDummyRenderer extends GeoBlockRenderer<TrainingDummyBlockEntity> {
    public TrainingDummyRenderer(BlockEntityRendererProvider.Context context) {
        super(new TrainingDummyModel());
    }

    @Override
    public AABB getRenderBoundingBox(TrainingDummyBlockEntity blockEntity) {
        return blockEntity.getRenderBoundingBox();
    }
}
