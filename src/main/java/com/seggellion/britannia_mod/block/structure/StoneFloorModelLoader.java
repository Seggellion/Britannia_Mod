package com.seggellion.britannia_mod.client.model;

import com.mojang.datafixers.util.Pair;
import com.mojang.math.Transformation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.SimpleModelState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.function.Function;

public class StoneFloorModelLoader implements IUnbakedGeometry<StoneFloorModelLoader> {

    public static final StoneFloorModelLoader INSTANCE = new StoneFloorModelLoader();

    @Override
    public BakedModel bake(
        IGeometryBakingContext context,
        ModelBaker baker,
        Function<Material, TextureAtlasSprite> spriteGetter,
        ModelState modelState,
        ItemOverrides overrides
    ) {
        // 1) Load all four floor variants into a list
        List<TextureAtlasSprite> floorSprites = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            Material mat = new Material(
                TextureAtlas.LOCATION_BLOCKS,
                ResourceLocation.parse("britannia_mod:block/structure/stone_floor_" + i)
            );
            floorSprites.add(spriteGetter.apply(mat));
        }

        // 2) Load all three wall variants into a list
        List<TextureAtlasSprite> wallSprites = new ArrayList<>();
        for (String name : List.of("foundation", "bottom", "top")) {
            Material mat = new Material(
                TextureAtlas.LOCATION_BLOCKS,
                ResourceLocation.parse("britannia_mod:block/structure/stone_wall_" + name)
            );
            wallSprites.add(spriteGetter.apply(mat));
        }

        // 3) Return an anonymous BakedModel that picks a random sprite per face
        return new BakedModel() {
            private final FaceBakery BAKERY = new FaceBakery();

            // Helper to create a single quad for a given face + sprite
            private BakedQuad makeQuad(Direction face, TextureAtlasSprite sprite) {
                // Define the UV for the full face (0→16 on both axes)
                BlockFaceUV uv = new BlockFaceUV(new float[]{0, 0, 16, 16}, 0);
                BlockElementFace elementFace = new BlockElementFace(null, -1, "", uv);

                // Identity transform: no rotation, no scale change, no translation
                Transformation identityTransform = new Transformation(
                    new Vector3f(0, 0, 0),   // translation
                    new Quaternionf(),       // left rotation (identity)
                    new Vector3f(1, 1, 1),   // scale
                    new Quaternionf()        // right rotation (identity)
                );
                ModelState identityState = new SimpleModelState(identityTransform);

                // Bake one face of the cube:
                return BAKERY.bakeQuad(
                    new Vector3f(0, 0, 0),    // "from" corner (0,0,0)
                    new Vector3f(16, 16, 16), // "to" corner   (16,16,16)
                    elementFace,
                    sprite,
                    face,
                    identityState,
                    null,
                    true
                );
            }

            // Legacy getQuads (called by some parts of the engine)
            @Override
            public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
                return getQuads(state, side, rand, ModelData.EMPTY, null);
            }

            // Primary getQuads: pick a random variant for floor vs. wall
            @Override
            public List<BakedQuad> getQuads(
                BlockState state,
                Direction side,
                RandomSource rand,
                ModelData extraData,
                RenderType renderType
            ) {
                if (side == null) {
                    return Collections.emptyList();
                }

                TextureAtlasSprite chosenSprite;
                if (side == Direction.UP) {
                    // Randomly pick one of the four floor textures
                    chosenSprite = floorSprites.get(rand.nextInt(floorSprites.size()));
                } else {
                    // Randomly pick one of the three wall textures
                    chosenSprite = wallSprites.get(rand.nextInt(wallSprites.size()));
                }

                return Collections.singletonList(makeQuad(side, chosenSprite));
            }

            @Override public boolean useAmbientOcclusion()       { return true; }
            @Override public boolean isGui3d()                  { return true; }
            @Override public boolean usesBlockLight()           { return true; }
            @Override public boolean isCustomRenderer()         { return false; }
            @Override public TextureAtlasSprite getParticleIcon() {
                // You can choose a default particle icon (e.g., first wall sprite)
                return wallSprites.get(0);
            }
            @Override public ItemOverrides getOverrides()       { return overrides; }
        };
    }

    // This method is NOT part of IUnbakedGeometry, so we omit @Override.
    // It tells the model loader which textures to stitch into the atlas.
    public Collection<Material> getMaterials(
        Function<ResourceLocation, UnbakedModel> modelGetter,
        Set<Pair<String, String>> missingTextureErrors
    ) {
        List<Material> materials = new ArrayList<>();

        // 4 floor variants
        for (int i = 1; i <= 4; i++) {
            materials.add(new Material(
                TextureAtlas.LOCATION_BLOCKS,
                ResourceLocation.parse("britannia_mod:block/structure/stone_floor_" + i)
            ));
        }

        // 3 wall variants
        for (String name : List.of("foundation", "bottom", "top")) {
            materials.add(new Material(
                TextureAtlas.LOCATION_BLOCKS,
                ResourceLocation.parse("britannia_mod:block/structure/stone_wall_" + name)
            ));
        }

        return materials;
    }
}
