package com.seggellion.britannia_mod.client.renderer;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

class MoongateRenderBufferTest {
    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void placedPortalUsesTheSameTranslucentEntityMaterialAsItsNamedItemModel() {
        var type = MoongateBlockEntityRenderer.PORTAL_RENDER_TYPE;
        assertSame(NeoForgeRenderTypes.ITEM_LAYERED_TRANSLUCENT.get(), type);
        assertNotSame(RenderType.translucent(), type, "chunk material must never enter the BER buffer source");
        assertSame(DefaultVertexFormat.NEW_ENTITY, type.format());
        assertEquals(VertexFormat.Mode.QUADS, type.mode());
        assertTrue(type.format().contains(VertexFormatElement.UV1), "entity overlay coordinate missing");
        assertTrue(type.format().contains(VertexFormatElement.UV2), "packed light coordinate missing");
        assertTrue(type.format().contains(VertexFormatElement.NORMAL), "shader normal missing");
    }

    @Test
    void selectedBufferAcceptsCompleteEntityQuadsWithLightOverlayAlphaAndNormals() {
        var type = MoongateBlockEntityRenderer.PORTAL_RENDER_TYPE;
        try (var storage = new ByteBufferBuilder(4096)) {
            var consumer = new BufferBuilder(storage, type.mode(), type.format());
            for (int i = 0; i < 4; i++) {
                consumer.addVertex(i % 2, i / 2, 0)
                        .setColor(255, 255, 255, 127).setUv(i % 2, i / 2)
                        .setOverlay(0x000A0000).setLight(0x00F000F0).setNormal(0, 0, 1);
            }
            try (var mesh = consumer.buildOrThrow()) {
                assertEquals(4, mesh.drawState().vertexCount());
                assertSame(DefaultVertexFormat.NEW_ENTITY, mesh.drawState().format());
                assertEquals(4 * DefaultVertexFormat.NEW_ENTITY.getVertexSize(), mesh.vertexBuffer().remaining());
            }
        }
    }
}
