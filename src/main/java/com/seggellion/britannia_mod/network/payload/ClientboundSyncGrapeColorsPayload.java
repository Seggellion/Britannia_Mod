package com.seggellion.britannia_mod.network.payload;

import com.mojang.logging.LogUtils;
import io.netty.handler.codec.DecoderException;
import com.seggellion.britannia_mod.winery.GrapeColor;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;
import org.slf4j.Logger;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shard's grape id-to-colour table, sent to a client once its world bootstrap has been applied.
 *
 * <p>Only the colour crosses the wire. The server's catalogue also carries agronomy, altitude bands,
 * chemistry, difficulty and naming; none of that has a client-side reader, so none of it is sent.
 * What the client cannot do without is turning the variety id it already receives on a farming block
 * entity -- and the one stamped into a harvested grape stack -- into the colour that picks a model
 * or an item texture.
 *
 * <p>Server to client only. Nothing accepts a catalogue in the other direction, so a client cannot
 * recolour anyone's grapes, its own included, beyond what the server told it.
 */
public record ClientboundSyncGrapeColorsPayload(List<Entry> entries) implements CustomPacketPayload {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Type<ClientboundSyncGrapeColorsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "sync_grape_colors"));

    /**
     * Production publishes roughly 945 varieties, so this leaves room for the catalogue to grow
     * several times over while still being a bound. Paired with {@link #MAX_ID_LENGTH} it caps the
     * decoded packet well inside the vanilla 1 MiB custom-payload ceiling.
     */
    public static final int MAX_ENTRIES = 4096;

    /** Variety ids are Rails slugs; this is generous for one and still bounds the worst case. */
    public static final int MAX_ID_LENGTH = 64;

    /** What an unrecognised colour name decodes to, matching the world bootstrap's own parser. */
    public static final GrapeColor UNKNOWN_COLOR_FALLBACK = GrapeColor.PURPLE;

    public static final StreamCodec<FriendlyByteBuf, ClientboundSyncGrapeColorsPayload> STREAM_CODEC = StreamCodec.of(
            ClientboundSyncGrapeColorsPayload::encode,
            ClientboundSyncGrapeColorsPayload::decode
    );

    /** One catalogue row: the id a block entity or item stack stores, and the colour to draw it in. */
    public record Entry(String varietyId, GrapeColor color) {
    }

    private static void encode(FriendlyByteBuf buf, ClientboundSyncGrapeColorsPayload payload) {
        List<Entry> entries = payload.entries();
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeUtf(entry.varietyId(), MAX_ID_LENGTH);
            buf.writeUtf(entry.color().getSerializedName(), MAX_ID_LENGTH);
        }
    }

    private static ClientboundSyncGrapeColorsPayload decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) {
            throw new DecoderException(
                    "Grape colour catalogue declares " + size + " entries, limit is " + MAX_ENTRIES);
        }
        List<Entry> entries = new ArrayList<>(Math.min(size, 256));
        for (int i = 0; i < size; i++) {
            String varietyId = buf.readUtf(MAX_ID_LENGTH);
            // A colour name this build does not know decodes to the same fallback the bootstrap
            // parser uses, so a newer shard adding a colour cannot drop a client's connection.
            GrapeColor color = GrapeColor.fromName(buf.readUtf(MAX_ID_LENGTH), UNKNOWN_COLOR_FALLBACK);
            if (varietyId.isBlank()) {
                continue;
            }
            entries.add(new Entry(varietyId, color));
        }
        return new ClientboundSyncGrapeColorsPayload(entries);
    }

    /** The payload a given catalogue produces, truncated to {@link #MAX_ENTRIES} if it must be. */
    public static ClientboundSyncGrapeColorsPayload of(Map<String, GrapeColor> catalogue) {
        List<Entry> entries = new ArrayList<>();
        if (catalogue != null) {
            for (Map.Entry<String, GrapeColor> row : catalogue.entrySet()) {
                if (row.getKey() == null || row.getKey().isBlank() || row.getValue() == null) {
                    continue;
                }
                if (row.getKey().length() > MAX_ID_LENGTH) {
                    continue;
                }
                if (entries.size() == MAX_ENTRIES) {
                    LOGGER.warn("Grape colour catalogue exceeds {} entries; the remainder will render "
                            + "with the fallback colour on clients", MAX_ENTRIES);
                    break;
                }
                entries.add(new Entry(row.getKey(), row.getValue()));
            }
        }
        return new ClientboundSyncGrapeColorsPayload(entries);
    }

    /** The catalogue this payload carries, in the shape {@link GrapeVarietyManager} installs. */
    public Map<String, GrapeColor> asCatalogue() {
        Map<String, GrapeColor> catalogue = new LinkedHashMap<>();
        for (Entry entry : entries) {
            catalogue.put(entry.varietyId(), entry.color());
        }
        return catalogue;
    }

    public static void handle(ClientboundSyncGrapeColorsPayload payload) {
        // One line, never one per variety: this runs on every login against a catalogue of hundreds.
        LOGGER.info("Installing shard grape colour catalogue count={}", payload.entries().size());
        GrapeVarietyManager.replaceSyncedColors(payload.asCatalogue());
    }

    public static void send(ServerPlayer player, Map<String, GrapeColor> catalogue) {
        PacketDistributor.sendToPlayer(player, of(catalogue));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
