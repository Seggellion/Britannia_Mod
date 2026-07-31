package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.migration.BannerDefinitionMigrations;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import io.netty.buffer.Unpooled;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class BannerMigrationHardeningTest {
    private static final Map<String, String> EXPECTED_ALIASES = expectedAliases();

    @Test
    void everyOperatorObtainableHistoricalIdHasOneApprovedCanonicalAlias() {
        assertEquals(14, BannerDefinitionMigrations.aliases().size());
        EXPECTED_ALIASES.forEach((historical, canonical) ->
                assertEquals(ResourceLocation.parse(canonical),
                        BannerDefinitionMigrations.aliases().get(ResourceLocation.parse(historical)), historical));
    }

    @Test
    void persistentAndStreamCodecsCanonicalizeOnlyTheDefinitionId() {
        BannerInstanceState expected = current("britannia_mod:star_standard");
        JsonObject legacy = BannerInstanceState.CODEC.encodeStart(JsonOps.INSTANCE, expected)
                .getOrThrow().getAsJsonObject();
        legacy.addProperty("banner_definition_id", "britannia_mod:end_01");
        BannerInstanceState decoded = BannerInstanceState.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertEquals(expected, decoded);

        JsonObject encoded = BannerInstanceState.CODEC.encodeStart(JsonOps.INSTANCE, decoded)
                .getOrThrow().getAsJsonObject();
        assertEquals("britannia_mod:star_standard", encoded.get("banner_definition_id").getAsString());
        assertEquals(expected.materialId(), decoded.materialId());
        assertEquals(expected.resolvedColourId(), decoded.resolvedColourId());
        assertEquals(expected.sourcePigmentId(), decoded.sourcePigmentId());
        assertEquals(expected.mountId(), decoded.mountId());

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(1);
            buffer.writeResourceLocation(ResourceLocation.parse("britannia_mod:end_02"));
            buffer.writeResourceLocation(expected.materialId().value());
            buffer.writeResourceLocation(expected.resolvedColourId().value());
            buffer.writeBoolean(true);
            buffer.writeResourceLocation(expected.sourcePigmentId().orElseThrow().value());
            buffer.writeResourceLocation(expected.mountId().value());
            BannerInstanceState streamed = BannerInstanceState.STREAM_CODEC.decode(buffer);
            assertEquals(BannerDefinitionId.parse("britannia_mod:ship_standard"),
                    streamed.bannerDefinitionId());
            assertEquals(expected.materialId(), streamed.materialId());
            assertEquals(expected.resolvedColourId(), streamed.resolvedColourId());
            assertEquals(expected.sourcePigmentId(), streamed.sourcePigmentId());
            assertEquals(expected.mountId(), streamed.mountId());
        } finally {
            buffer.release();
        }
    }

    @Test
    void allAliasesAreIdempotentAndUnknownIdsRemainRecoverableRawIdentities() {
        EXPECTED_ALIASES.forEach((historical, canonical) -> {
            BannerDefinitionId first = BannerDefinitionId.parse(historical);
            BannerDefinitionId second = new BannerDefinitionId(first.value());
            assertEquals(BannerDefinitionId.parse(canonical), first);
            assertEquals(first, second);
        });
        BannerDefinitionId missing = BannerDefinitionId.parse("other_pack:removed_banner");
        assertEquals("other_pack:removed_banner", missing.toString());
        assertFalse(BannerDefinitionMigrations.aliases().containsKey(missing.value()));
    }

    @Test
    void migrationDiagnosticsAreDeduplicatedAndStrictlyBounded() {
        for (int repetition = 0; repetition < 100; repetition++) {
            EXPECTED_ALIASES.keySet().forEach(BannerDefinitionId::parse);
        }
        assertTrue(BannerDefinitionMigrations.loggedAliasCount() <=
                BannerDefinitionMigrations.MAX_LOGGED_ALIASES);
        assertTrue(BannerDefinitionMigrations.loggedAliasCount() <= EXPECTED_ALIASES.size());
    }

    @Test
    void noHistoricalAliasIsAnActiveReleasedDefinition() throws Exception {
        JsonObject catalogue = JsonParser.parseString(
                java.nio.file.Files.readString(java.nio.file.Path.of("content/banner_catalogue.yml")))
                .getAsJsonObject();
        var active = catalogue.getAsJsonArray("banners").asList().stream()
                .map(value -> "britannia_mod:" + value.getAsJsonObject().get("id").getAsString())
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(java.util.Collections.disjoint(active, EXPECTED_ALIASES.keySet()));
        assertTrue(active.containsAll(EXPECTED_ALIASES.values()));
    }

    private static BannerInstanceState current(String definition) {
        return new BannerInstanceState(
                1,
                BannerDefinitionId.parse(definition),
                FabricMaterialId.parse("britannia_mod:cotton"),
                ResolvedColourId.parse("britannia_mod:cotton_red"),
                Optional.of(PigmentId.parse("britannia_mod:madder_red")),
                MountId.parse("britannia_mod:iron"));
    }

    private static Map<String, String> expectedAliases() {
        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        aliases.put("britannia_mod:x_small_unnamed_01", "britannia_mod:small_curtain");
        aliases.put("britannia_mod:end_01", "britannia_mod:star_standard");
        aliases.put("britannia_mod:end_02", "britannia_mod:ship_standard");
        aliases.put("britannia_mod:medium_wall_01", "britannia_mod:verdant_grape_pennon");
        aliases.put("britannia_mod:medium_wall_02", "britannia_mod:silver_rosette_pennon");
        aliases.put("britannia_mod:medium_wall_03", "britannia_mod:four_seals_pennon");
        aliases.put("britannia_mod:medium_wall_04", "britannia_mod:twin_spades_pennon");
        aliases.put("britannia_mod:medium_wall_05", "britannia_mod:ankh_pennon");
        aliases.put("britannia_mod:large_01", "britannia_mod:tournament_curtain");
        aliases.put("britannia_mod:large_02", "britannia_mod:threefold_chain_standard");
        aliases.put("britannia_mod:large_03", "britannia_mod:iron_serpent_standard");
        aliases.put("britannia_mod:large_04", "britannia_mod:silver_fleur_curtain");
        aliases.put("britannia_mod:large_05", "britannia_mod:gilded_trellis_curtain");
        aliases.put("britannia_mod:large_06", "britannia_mod:gilded_chevron_curtain");
        return Map.copyOf(aliases);
    }
}
