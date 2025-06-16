package com.seggellion.britannia_mod.client.model;

import com.mojang.math.Transformation;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.SimpleModelState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class FaceBakeryHelper {

    private static final FaceBakery BAKERY = new FaceBakery();

    public static BakedQuad makeQuad(Direction face, TextureAtlasSprite sprite) {
        Vector3f from = new Vector3f(0, 0, 0);
        Vector3f to = new Vector3f(16, 16, 16);

        BlockFaceUV uv = new BlockFaceUV(new float[]{0, 0, 16, 16}, 0);
        BlockElementFace blockFace = new BlockElementFace(null, -1, "", uv);

        // ✅ Proper identity Transformation
        Transformation identityTransform = new Transformation(
            new Vector3f(0, 0, 0),     // translation
            new Quaternionf(),         // left rotation
            new Vector3f(1, 1, 1),     // scale
            new Quaternionf()          // right rotation
        );

        ModelState identityState = new SimpleModelState(identityTransform);

        return BAKERY.bakeQuad(
            from,
            to,
            blockFace,
            sprite,
            face,
            identityState,
            null,
            true
        );
    }
}
