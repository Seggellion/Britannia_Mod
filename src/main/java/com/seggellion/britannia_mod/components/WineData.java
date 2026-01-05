
package com.seggellion.britannia_mod.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// Added 'String labelColor' to the end
public record WineData(String wineryName, String grapeType, int year, int quality, String region, String labelColor) {

    public static final WineData EMPTY = new WineData("", "", 0, 0, "", "none");

    public static final Codec<WineData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("wineryName").forGetter(WineData::wineryName),
            Codec.STRING.fieldOf("grapeType").forGetter(WineData::grapeType),
            Codec.INT.fieldOf("year").forGetter(WineData::year),
            Codec.INT.fieldOf("quality").forGetter(WineData::quality),
            Codec.STRING.fieldOf("region").forGetter(WineData::region),
            Codec.STRING.fieldOf("labelColor").orElse("none").forGetter(WineData::labelColor)
    ).apply(instance, WineData::new));

    public static final StreamCodec<ByteBuf, WineData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, WineData::wineryName,
            ByteBufCodecs.STRING_UTF8, WineData::grapeType,
            ByteBufCodecs.VAR_INT, WineData::year,
            ByteBufCodecs.VAR_INT, WineData::quality,
            ByteBufCodecs.STRING_UTF8, WineData::region,
            ByteBufCodecs.STRING_UTF8, WineData::labelColor,
            WineData::new
    );
}