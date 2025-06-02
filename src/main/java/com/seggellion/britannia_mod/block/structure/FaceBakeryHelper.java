package com.seggellion.britannia_mod.client.model;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.client.resources.model.ModelState;
import net.neoforged.neoforge.client.model.SimpleModelState;
import com.mojang.math.Transformation;

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
