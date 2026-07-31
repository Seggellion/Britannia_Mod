package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.banner.structure.BannerFootprint;
import com.seggellion.britannia_mod.banner.structure.BannerPlacedStructure;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import com.seggellion.britannia_mod.dye.state.DyeTubState;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BannerSchemaHardeningTest {
    @Test
    void bannerStateRoundTripsAndAllowsOnlyItsDocumentedOptionalField() {
        BannerInstanceState state = bannerState(Optional.of(PigmentId.parse("britannia_mod:madder_red")));
        JsonObject encoded = encode(BannerInstanceState.CODEC, state);
        encoded.addProperty("future_ignored_metadata", "safe");
        assertEquals(state, parse(BannerInstanceState.CODEC, encoded));

        encoded.remove("source_pigment_id");
        assertTrue(parse(BannerInstanceState.CODEC, encoded).sourcePigmentId().isEmpty());
        for (String required : java.util.List.of(
                "schema_version", "banner_definition_id", "material_id", "resolved_colour_id", "mount_id")) {
            JsonObject missing = encode(BannerInstanceState.CODEC, state);
            missing.remove(required);
            assertFalse(BannerInstanceState.CODEC.parse(JsonOps.INSTANCE, missing).result().isPresent(), required);
        }
    }

    @Test
    void bannerStateRejectsUnknownSchemasAndCorruptIdentifiers() {
        JsonObject unknown = encode(BannerInstanceState.CODEC, bannerState(Optional.empty()));
        unknown.addProperty("schema_version", 2);
        assertFalse(BannerInstanceState.CODEC.parse(JsonOps.INSTANCE, unknown).result().isPresent());

        JsonObject corrupt = encode(BannerInstanceState.CODEC, bannerState(Optional.empty()));
        corrupt.addProperty("material_id", "not a resource location");
        assertFalse(BannerInstanceState.CODEC.parse(JsonOps.INSTANCE, corrupt).result().isPresent());
    }

    @Test
    void dyeTubSchemaPreservesUnlimitedAsAbsentAndRejectsImpossibleOrUnknownData() {
        DyeTubState unlimited = DyeTubState.loadedUnlimited(PigmentId.parse("britannia_mod:madder_red"));
        JsonObject encoded = encode(DyeTubState.CODEC, unlimited);
        encoded.addProperty("future_ignored_metadata", true);
        DyeTubState decoded = parse(DyeTubState.CODEC, encoded);
        assertEquals(unlimited, decoded);
        assertTrue(decoded.remainingUses().isEmpty());

        assertFalse(DyeTubState.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {"schema_version":1,"remaining_uses":1}
                        """)).result().isPresent());
        assertFalse(DyeTubState.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {"schema_version":2,"pigment_id":"britannia_mod:madder_red"}
                        """)).result().isPresent());
    }

    @Test
    void placedStructureRoundTripsButRejectsUnknownSchemaAndNonRectangles() {
        BannerPlacedStructure current = BannerPlacedStructure.fromFootprint(
                BannerOrientation.WALL_PERPENDICULAR,
                BannerFootprint.fromDimensions(
                        new com.seggellion.britannia_mod.banner.data.BannerDimensions(3, 2, false))
                        .footprint());
        JsonObject encoded = encode(BannerPlacedStructure.CODEC, current);
        encoded.addProperty("future_ignored_metadata", 17);
        assertEquals(current, parse(BannerPlacedStructure.CODEC, encoded));

        JsonObject unknown = encode(BannerPlacedStructure.CODEC, current);
        unknown.addProperty("schema_version", 2);
        assertFalse(BannerPlacedStructure.CODEC.parse(JsonOps.INSTANCE, unknown).result().isPresent());

        JsonObject incomplete = encode(BannerPlacedStructure.CODEC, current);
        incomplete.getAsJsonArray("occupied_offsets").remove(5);
        assertFalse(BannerPlacedStructure.CODEC.parse(JsonOps.INSTANCE, incomplete).result().isPresent());

        JsonObject corruptOrientation = encode(BannerPlacedStructure.CODEC, current);
        corruptOrientation.addProperty("orientation", "free_floating");
        assertFalse(BannerPlacedStructure.CODEC.parse(JsonOps.INSTANCE, corruptOrientation).result().isPresent());
    }

    private static BannerInstanceState bannerState(Optional<PigmentId> source) {
        return new BannerInstanceState(
                1,
                BannerDefinitionId.parse("britannia_mod:small_curtain"),
                FabricMaterialId.parse("britannia_mod:cotton"),
                ResolvedColourId.parse("britannia_mod:cotton_red"),
                source,
                MountId.parse("britannia_mod:iron"));
    }

    private static <T> JsonObject encode(Codec<T> codec, T value) {
        return codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow().getAsJsonObject();
    }

    private static <T> T parse(Codec<T> codec, JsonObject value) {
        return codec.parse(JsonOps.INSTANCE, value).getOrThrow();
    }
}
