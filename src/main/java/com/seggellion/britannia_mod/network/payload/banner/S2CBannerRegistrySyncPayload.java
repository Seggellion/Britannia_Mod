package com.seggellion.britannia_mod.network.payload.banner;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bannerdyeing.registry.BannerRegistryClientSync;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Full banner-dyeing registry snapshot, synchronized on login and server data-pack reload so
 * remote clients hold the same {@code BannerDataRegistries} content an integrated client gets
 * from the shared JVM. Companion to {@link S2CBannerRenderDataPayload}, which carries the
 * display-geometry subset; this one carries the authoritative definition content that item
 * tooltips, dye preview screens, and creative-tab population validate against.
 *
 * <p>{@code published} mirrors {@link com.seggellion.britannia_mod.bannerdyeing.registry
 * .BannerDataRegistries#isAvailable()} on the sending server: a server whose banner data never
 * loaded sends {@code false} with an empty snapshot, and the client keeps the same
 * degrade-to-nothing behaviour it has always had when no publication exists.
 *
 * <p>The body is the {@link BannerRegistryClientSync#WIRE_CODEC} NBT encoding -- the same codecs
 * the datapack reload parses, so wire content cannot diverge structurally from local content.
 */
public record S2CBannerRegistrySyncPayload(boolean published, RegistrySnapshot snapshot)
        implements CustomPacketPayload {
    /**
     * Regression ceiling, an order of magnitude above the measured full production snapshot
     * (35 banners + 4 materials/palettes + pigments + mounts + profiles encode well under 64 KiB)
     * but far below NeoForge's 1 MiB custom-payload limit.
     */
    public static final int MAX_ENCODED_BYTES = 256 * 1024;
    public static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath(
            BritanniaMod.MODID, "banner_registry_sync");
    public static final Type<S2CBannerRegistrySyncPayload> TYPE = new Type<>(TYPE_ID);
    public static final StreamCodec<FriendlyByteBuf, S2CBannerRegistrySyncPayload> STREAM_CODEC =
            StreamCodec.of(S2CBannerRegistrySyncPayload::encode, S2CBannerRegistrySyncPayload::decode);

    public S2CBannerRegistrySyncPayload {
        Objects.requireNonNull(snapshot, "snapshot");
    }

    private static void encode(FriendlyByteBuf buffer, S2CBannerRegistrySyncPayload payload) {
        int startIndex = buffer.writerIndex();
        buffer.writeBoolean(payload.published);
        Tag encoded = BannerRegistryClientSync.WIRE_CODEC
                .encodeStart(NbtOps.INSTANCE, payload.snapshot)
                .getOrThrow(message -> new IllegalArgumentException(
                        "Banner registry snapshot failed to encode: " + message));
        buffer.writeNbt(encoded);
        int encodedBytes = buffer.writerIndex() - startIndex;
        if (encodedBytes > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Banner registry sync payload exceeds "
                    + MAX_ENCODED_BYTES + " bytes: " + encodedBytes);
        }
    }

    private static S2CBannerRegistrySyncPayload decode(FriendlyByteBuf buffer) {
        boolean published = buffer.readBoolean();
        CompoundTag tag = buffer.readNbt();
        if (tag == null) {
            throw new IllegalArgumentException("Banner registry sync payload carried no snapshot tag");
        }
        RegistrySnapshot snapshot = BannerRegistryClientSync.WIRE_CODEC
                .parse(NbtOps.INSTANCE, tag)
                .getOrThrow(message -> new IllegalArgumentException(
                        "Banner registry snapshot failed to decode: " + message));
        return new S2CBannerRegistrySyncPayload(published, snapshot);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
