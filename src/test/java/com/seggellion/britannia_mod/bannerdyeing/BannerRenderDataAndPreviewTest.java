package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.*;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.data.BannerAssets;
import com.seggellion.britannia_mod.banner.renderdata.BannerPreviewRenderState;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderDataSnapshot;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone7RegisteredTestContent;
import com.seggellion.britannia_mod.client.banner.BannerPreviewStacks;
import com.seggellion.britannia_mod.client.banner.BannerAssetAvailability;
import com.seggellion.britannia_mod.client.banner.BannerRenderStateExtractor;
import com.seggellion.britannia_mod.client.banner.ClientBannerRenderPublication;
import com.seggellion.britannia_mod.client.banner.ClientBannerRenderData;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import com.seggellion.britannia_mod.dye.preview.DyePreviewDisplayData;
import com.seggellion.britannia_mod.dye.service.MatchType;
import com.seggellion.britannia_mod.network.payload.banner.S2CBannerRenderDataPayload;
import com.seggellion.britannia_mod.network.payload.dye.C2SConfirmDyeApplicationPayload;
import com.seggellion.britannia_mod.network.payload.dye.C2SCancelDyePreviewPayload;
import com.seggellion.britannia_mod.network.payload.dye.S2CDyeApplicationResultPayload;
import com.seggellion.britannia_mod.network.payload.dye.S2COpenDyePreviewPayload;
import com.seggellion.britannia_mod.dye.preview.DyeApplicationResultCode;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BannerRenderDataAndPreviewTest {
    private static BannerRenderDataSnapshot snapshot;

    @BeforeAll
    static void load() throws Exception {
        Milestone7RegisteredTestContent.ensureRegistered();
        snapshot = BannerRenderDataSnapshot.fromRegistry(DyeResolverFixtures.productionSnapshot());
    }

    @Test
    void authoritativeDisplaySnapshotRoundTripsExactly() {
        S2CBannerRenderDataPayload payload = new S2CBannerRenderDataPayload(snapshot);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            S2CBannerRenderDataPayload.STREAM_CODEC.encode(buffer, payload);
            assertEquals(payload, S2CBannerRenderDataPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
        assertEquals("britannia_mod:banner_render_data", S2CBannerRenderDataPayload.TYPE_ID.toString());
    }

    @Test
    void synchronizedDefinitionsExposeExactlyGeometryBaseAndMask() throws Exception {
        assertArrayEquals(new String[] {"geometry", "baseTexture", "dyeMask"},
                java.util.Arrays.stream(BannerAssets.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toArray(String[]::new));
        assertTrue(snapshot.banners().values().stream().allMatch(definition ->
                definition.assets().baseTexture() != null && definition.assets().dyeMask() != null));
        String payloadSource = Files.readString(Path.of(System.getProperty("britannia.projectDir", "."),
                "src/main/java/com/seggellion/britannia_mod/network/payload/banner/"
                        + "S2CBannerRenderDataPayload.java"));
        assertTrue(payloadSource.contains("assets.baseTexture()"));
        assertTrue(payloadSource.contains("assets.dyeMask()"));
        assertFalse(payloadSource.contains("fabricBase"));
        assertFalse(payloadSource.contains("staticOverlay"));
    }

    @Test
    void clientPublicationReplacementIsAtomicAndGenerationInvalidated() {
        long before = ClientBannerRenderData.current().generation();
        ClientBannerRenderData.replace(snapshot);
        assertTrue(ClientBannerRenderData.current().available());
        assertEquals(snapshot, ClientBannerRenderData.current().snapshot());
        assertTrue(ClientBannerRenderData.current().generation() > before);
        long replaced = ClientBannerRenderData.current().generation();
        ClientBannerRenderData.clear();
        assertFalse(ClientBannerRenderData.current().available());
        assertTrue(ClientBannerRenderData.current().generation() > replaced);
    }

    @Test
    void previewStacksAreDetachedAndDifferOnlyByServerSuppliedColour() {
        BannerPreviewRenderState current = current();
        BannerPreviewRenderState proposed = new BannerPreviewRenderState(
                current.bannerDefinitionId(), current.materialId(), dyed(),
                Optional.of(PigmentId.parse("britannia_mod:madder_red")), current.mountId());
        BannerPreviewStacks previews = BannerPreviewStacks.create(
                Milestone7RegisteredTestContent.banner(), Milestone7RegisteredTestContent.component(),
                current, proposed);
        ItemStack currentStack = previews.current();
        ItemStack proposedStack = previews.proposed();
        BannerInstanceState currentState = currentStack.get(Milestone7RegisteredTestContent.component());
        BannerInstanceState proposedState = proposedStack.get(Milestone7RegisteredTestContent.component());
        assertEquals(current.bannerDefinitionId(), proposedState.bannerDefinitionId());
        assertEquals(current.materialId(), proposedState.materialId());
        assertEquals(current.mountId(), proposedState.mountId());
        assertNotEquals(currentState.resolvedColourId(), proposedState.resolvedColourId());
        assertTrue(proposedState.sourcePigmentId().isPresent());
        var publication = new ClientBannerRenderPublication(snapshot, 1, true);
        assertFalse(BannerRenderStateExtractor.extract(currentStack,
                Milestone7RegisteredTestContent.banner(), Milestone7RegisteredTestContent.component(),
                publication, BannerAssetAvailability.allExpected()).recolourActive());
        assertTrue(BannerRenderStateExtractor.extract(proposedStack,
                Milestone7RegisteredTestContent.banner(), Milestone7RegisteredTestContent.component(),
                publication, BannerAssetAvailability.allExpected()).recolourActive());
        proposedStack.set(Milestone7RegisteredTestContent.component(), currentState);
        assertEquals(currentState, previews.current().get(Milestone7RegisteredTestContent.component()));
        assertEquals(dyed(), previews.proposed().get(Milestone7RegisteredTestContent.component()).resolvedColourId());
    }

    @Test
    void previewPayloadCarriesOnlyDisplayDescriptorsAndConfirmationStaysUuidOnly() {
        BannerPreviewRenderState current = current();
        BannerPreviewRenderState proposed = new BannerPreviewRenderState(
                current.bannerDefinitionId(), current.materialId(), dyed(),
                Optional.of(PigmentId.parse("britannia_mod:madder_red")), current.mountId());
        S2COpenDyePreviewPayload payload = new S2COpenDyePreviewPayload(
                UUID.randomUUID(), display(), current, proposed, 30_000);
        assertEquals(current, payload.currentRenderState());
        assertEquals(proposed, payload.proposedRenderState());
        assertArrayEquals(new Class<?>[] {UUID.class}, java.util.Arrays.stream(
                C2SConfirmDyeApplicationPayload.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getType)
                .toArray(Class<?>[]::new));
    }

    @Test
    void productionPayloadsStayWithinExplicitRegressionBudgets() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            S2CBannerRenderDataPayload.STREAM_CODEC.encode(
                    buffer, new S2CBannerRenderDataPayload(snapshot));
            int renderDataBytes = buffer.readableBytes();
            assertTrue(renderDataBytes <= S2CBannerRenderDataPayload.MAX_ENCODED_BYTES);
            System.out.println("Gate F production render-data payload bytes=" + renderDataBytes);

            buffer.clear();
            BannerPreviewRenderState current = current();
            BannerPreviewRenderState proposed = new BannerPreviewRenderState(
                    current.bannerDefinitionId(), current.materialId(), dyed(),
                    Optional.of(PigmentId.parse("britannia_mod:madder_red")), current.mountId());
            S2COpenDyePreviewPayload.STREAM_CODEC.encode(buffer, new S2COpenDyePreviewPayload(
                    UUID.randomUUID(), display(), current, proposed, 30_000));
            int previewBytes = buffer.readableBytes();
            assertTrue(previewBytes <= S2COpenDyePreviewPayload.MAX_ENCODED_BYTES);
            System.out.println("Gate F preview payload bytes=" + previewBytes);

            UUID token = UUID.randomUUID();
            buffer.clear();
            C2SConfirmDyeApplicationPayload.STREAM_CODEC.encode(
                    buffer, new C2SConfirmDyeApplicationPayload(token));
            assertEquals(16, buffer.readableBytes());
            buffer.clear();
            C2SCancelDyePreviewPayload.STREAM_CODEC.encode(
                    buffer, new C2SCancelDyePreviewPayload(token));
            assertEquals(16, buffer.readableBytes());
            buffer.clear();
            S2CDyeApplicationResultPayload.STREAM_CODEC.encode(buffer,
                    new S2CDyeApplicationResultPayload(
                            token, DyeApplicationResultCode.SUCCESS, true));
            assertTrue(buffer.readableBytes() <= 18);
        } finally {
            buffer.release();
        }
    }

    @Test
    void synchronizedSnapshotRejectsCountsAboveItsProtocolBoundBeforeAllocation() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(S2CBannerRenderDataPayload.MAX_BANNERS + 1);
            assertThrows(IllegalArgumentException.class,
                    () -> S2CBannerRenderDataPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void screenUsesSharedItemRendererTwiceAndKeepsBothSwatches() throws Exception {
        String screen = Files.readString(Path.of(System.getProperty("britannia.projectDir", "."),
                "src/main/java/com/seggellion/britannia_mod/client/screen/DyePreviewScreen.java"));
        assertTrue(screen.contains("BannerPreviewStacks.create"));
        assertEquals(2, occurrences(screen, "graphics.renderItem("));
        assertTrue(occurrences(screen, "graphics.fill(") >= 2);
        assertFalse(screen.contains("player.getMainHandItem"));
        assertFalse(screen.contains("set(DataComponentRegistry"));
    }

    @Test
    void serverSyncRunsOnLoginAndReloadWhileConfirmationServiceHasNoClientDependency() throws Exception {
        String sync = Files.readString(Path.of(System.getProperty("britannia.projectDir", "."),
                "src/main/java/com/seggellion/britannia_mod/banner/renderdata/BannerRenderDataSync.java"));
        assertTrue(sync.contains("OnDatapackSyncEvent"));
        assertTrue(sync.contains("event.getRelevantPlayers"));
        String service = Files.readString(Path.of(System.getProperty("britannia.projectDir", "."),
                "src/main/java/com/seggellion/britannia_mod/dye/preview/DyePreviewSessionService.java"));
        assertFalse(service.contains("client.banner"));
        assertFalse(service.contains("BannerRenderDataSnapshot"));
    }

    private static BannerPreviewRenderState current() {
        return new BannerPreviewRenderState(
                snapshot.banners().keySet().stream().findFirst().orElseThrow(),
                cotton(), natural(), Optional.empty(), brass());
    }

    private static DyePreviewDisplayData display() {
        return new DyePreviewDisplayData("banner", "material", "mount", "current", Optional.empty(),
                "pigment", "new", MatchType.EXPLICIT_MAPPING, 0, 0xEEE4CC, 0xA51C30, true, false);
    }

    private static FabricMaterialId cotton() { return FabricMaterialId.parse("britannia_mod:cotton"); }
    private static ResolvedColourId natural() { return snapshot.materials().get(cotton()).naturalColourId(); }
    private static ResolvedColourId dyed() { return snapshot.materials().get(cotton()).displaySrgbByColour().keySet()
            .stream().filter(id -> !id.equals(natural())).findFirst().orElseThrow(); }
    private static MountId brass() { return MountId.parse("britannia_mod:brass"); }
    private static int occurrences(String source, String needle) {
        int count = 0, offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) { count++; offset += needle.length(); }
        return count;
    }
}
