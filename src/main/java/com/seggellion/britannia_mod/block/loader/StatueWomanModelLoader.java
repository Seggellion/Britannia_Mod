package britannia_mod.client.model.loader;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;

import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.BlockModel;

import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

import java.util.Set;
import java.util.HashSet;

import java.util.function.Function;

public class StatueWomanModelLoader implements IGeometryLoader<StatueWomanModelLoader.StatueWomanGeometry> {

    @Override
    public StatueWomanGeometry read(JsonObject jsonObject, JsonDeserializationContext context) {
        return new StatueWomanGeometry();
    }

    public static class StatueWomanGeometry implements IUnbakedGeometry<StatueWomanGeometry> {

        @Override
        public BakedModel bake(IGeometryBakingContext context,
                               ModelBaker baker,
                               Function<Material, TextureAtlasSprite> spriteGetter,
                               ModelState modelState,
                               ItemOverrides overrides) {

            // Use "texture" as the material key from your model JSON
            Material material = context.getMaterial("texture");
            TextureAtlasSprite sprite = spriteGetter.apply(material);

            // A very simple model baked as a cube with a single texture
            // You'll want to replace this with something like SimpleBakedModel.Builder if needed
            return new SimpleBakedModel.Builder(context.useAmbientOcclusion(), context.useBlockLight(), context.isGui3d(), context.getTransforms(), overrides)
                    .particle(sprite)
                    .build();
        }

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
            // no-op for simple unbaked geometry
        }
    }
}
