package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.seggellion.britannia_mod.bannerdyeing.api.StableResourceId;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.BannerDyeingIdFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.BannerDyeingIdFixtures.IdFixture;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.stream.Stream;
import net.minecraft.ResourceLocationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class StableIdentifierTest {
    static Stream<IdFixture<?>> identifierFixtures() {
        return BannerDyeingIdFixtures.all().stream();
    }

    @ParameterizedTest(name = "{0} constructs a valid namespaced ID")
    @MethodSource("identifierFixtures")
    void validNamespacedConstruction(IdFixture<?> fixture) {
        StableResourceId id = fixture.factory().apply(BannerDyeingIdFixtures.NAMESPACE, BannerDyeingIdFixtures.PATH);

        assertEquals(BannerDyeingIdFixtures.NAMESPACE, id.value().getNamespace());
        assertEquals(BannerDyeingIdFixtures.PATH, id.value().getPath());
    }

    @ParameterizedTest(name = "{0} has stable equality and hash behavior")
    @MethodSource("identifierFixtures")
    void equalityAndHashBehavior(IdFixture<?> fixture) {
        StableResourceId first = fixture.factory().apply(BannerDyeingIdFixtures.NAMESPACE, BannerDyeingIdFixtures.PATH);
        StableResourceId equal = fixture.parser().apply(first.toString());
        StableResourceId different = fixture.factory().apply(BannerDyeingIdFixtures.NAMESPACE, "test/different");

        assertEquals(first, equal);
        assertEquals(first.hashCode(), equal.hashCode());
        assertNotEquals(first, different);
    }

    @ParameterizedTest(name = "{0} uses the namespaced string representation")
    @MethodSource("identifierFixtures")
    void stringRepresentation(IdFixture<?> fixture) {
        StableResourceId id = fixture.factory().apply(BannerDyeingIdFixtures.NAMESPACE, BannerDyeingIdFixtures.PATH);

        assertEquals("britannia_mod:test/example", id.toString());
    }

    @ParameterizedTest(name = "{0} persistent codec round-trips")
    @MethodSource("identifierFixtures")
    void persistentCodecRoundTrip(IdFixture<?> fixture) {
        assertPersistentCodecRoundTrip(fixture);
    }

    @ParameterizedTest(name = "{0} network codec round-trips")
    @MethodSource("identifierFixtures")
    void networkCodecRoundTrip(IdFixture<?> fixture) {
        assertNetworkCodecRoundTrip(fixture);
    }

    @ParameterizedTest(name = "{0} rejects an invalid namespace")
    @MethodSource("identifierFixtures")
    void invalidNamespaceRejected(IdFixture<?> fixture) {
        assertThrows(ResourceLocationException.class,
                () -> fixture.factory().apply("Invalid Namespace", BannerDyeingIdFixtures.PATH));
    }

    @ParameterizedTest(name = "{0} rejects an invalid path")
    @MethodSource("identifierFixtures")
    void invalidPathRejected(IdFixture<?> fixture) {
        assertThrows(ResourceLocationException.class,
                () -> fixture.factory().apply(BannerDyeingIdFixtures.NAMESPACE, "Invalid Path"));
    }

    @ParameterizedTest(name = "{0} rejects a null ResourceLocation")
    @MethodSource("identifierFixtures")
    void nullRejected(IdFixture<?> fixture) {
        assertThrows(NullPointerException.class, () -> fixture.constructor().apply(null));
    }

    private static <T extends StableResourceId> void assertPersistentCodecRoundTrip(IdFixture<T> fixture) {
        T original = fixture.factory().apply(BannerDyeingIdFixtures.NAMESPACE, BannerDyeingIdFixtures.PATH);
        JsonElement encoded = fixture.codec().encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        T decoded = fixture.codec().parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(original, decoded);
        assertEquals("britannia_mod:test/example", encoded.getAsString());
    }

    private static <T extends StableResourceId> void assertNetworkCodecRoundTrip(IdFixture<T> fixture) {
        T original = fixture.factory().apply(BannerDyeingIdFixtures.NAMESPACE, BannerDyeingIdFixtures.PATH);
        ByteBuf buffer = Unpooled.buffer();
        try {
            fixture.streamCodec().encode(buffer, original);
            assertEquals(original, fixture.streamCodec().decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
