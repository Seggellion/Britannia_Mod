package com.seggellion.britannia_mod.client.renderer.entity;

import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.world.entity.LivingEntity;

public class NoHatVillagerModel<T extends LivingEntity> extends VillagerModel<T> {

    public NoHatVillagerModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        // Head with nose
        PartDefinition head = partdefinition.addOrReplaceChild("head",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F),
            PartPose.ZERO
        );

        // Add nose as a child of the head
        head.addOrReplaceChild("nose",
            CubeListBuilder.create()
                .texOffs(24, 0)
                .addBox(-1.0F, -1.0F, -6.0F, 2.0F, 4.0F, 2.0F),
            PartPose.offset(0.0F, -2.0F, 0.0F)
        );

        // Vanilla-style hat structure but minimized
        PartDefinition hat = partdefinition.addOrReplaceChild("hat",
            CubeListBuilder.create()
                .texOffs(32, 0) // Original texture coordinates
                .addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, 
                        new CubeDeformation(0.51F)), // Slightly larger deformation
            PartPose.ZERO
        );

        hat.addOrReplaceChild("hat_rim",
            CubeListBuilder.create()
                .texOffs(30, 47) // Original texture coordinates
                .addBox(-8.0F, -8.0F, -6.0F, 16.0F, 16.0F, 1.0F, 
                        new CubeDeformation(0.01F)), // Almost flat
            PartPose.ZERO
        );

        // Body
        partdefinition.addOrReplaceChild("body",
            CubeListBuilder.create()
                .texOffs(16, 20)
                .addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F),
            PartPose.ZERO
        );

        // Arms
        partdefinition.addOrReplaceChild("arms",
            CubeListBuilder.create()
                .texOffs(44, 22)
                .addBox(-8.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F)
                .addBox(4.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F),
            PartPose.offset(0.0F, 2.0F, 0.0F)
        );

        // Legs
        partdefinition.addOrReplaceChild("right_leg",
            CubeListBuilder.create()
                .texOffs(0, 22)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offset(-2.0F, 12.0F, 0.0F)
        );

        partdefinition.addOrReplaceChild("left_leg",
            CubeListBuilder.create()
                .texOffs(0, 22)
                .mirror()
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
            PartPose.offset(2.0F, 12.0F, 0.0F)
        );

        return LayerDefinition.create(meshdefinition, 64, 64);
    }
}