package com.seggellion.britannia_mod.bannerdyeing.testsupport;

import com.mojang.serialization.Codec;
import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.api.PlacementProfileId;
import com.seggellion.britannia_mod.bannerdyeing.api.StableResourceId;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public final class BannerDyeingIdFixtures {
    public static final String NAMESPACE = "britannia_mod";
    public static final String PATH = "test/example";

    private BannerDyeingIdFixtures() {
    }

    public static List<IdFixture<?>> all() {
        return List.of(
                new IdFixture<>("banner definition", BannerDefinitionId::of, BannerDefinitionId::parse,
                        BannerDefinitionId::new, BannerDefinitionId.CODEC, BannerDefinitionId.STREAM_CODEC),
                new IdFixture<>("fabric material", FabricMaterialId::of, FabricMaterialId::parse,
                        FabricMaterialId::new, FabricMaterialId.CODEC, FabricMaterialId.STREAM_CODEC),
                new IdFixture<>("pigment", PigmentId::of, PigmentId::parse,
                        PigmentId::new, PigmentId.CODEC, PigmentId.STREAM_CODEC),
                new IdFixture<>("resolved colour", ResolvedColourId::of, ResolvedColourId::parse,
                        ResolvedColourId::new, ResolvedColourId.CODEC, ResolvedColourId.STREAM_CODEC),
                new IdFixture<>("mount", MountId::of, MountId::parse,
                        MountId::new, MountId.CODEC, MountId.STREAM_CODEC),
                new IdFixture<>("placement profile", PlacementProfileId::of, PlacementProfileId::parse,
                        PlacementProfileId::new, PlacementProfileId.CODEC, PlacementProfileId.STREAM_CODEC));
    }

    public record IdFixture<T extends StableResourceId>(
            String name,
            BiFunction<String, String, T> factory,
            Function<String, T> parser,
            Function<ResourceLocation, T> constructor,
            Codec<T> codec,
            StreamCodec<ByteBuf, T> streamCodec) {

        @Override
        public String toString() {
            return name;
        }
    }
}
