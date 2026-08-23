package com.seggellion.britannia_mod.resource.preview;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceDepositPreviewEvaluatorInputTest {
    private static final UUID PREVIEW = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID DEPOSIT = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID SHARD = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID SERVER = UUID.fromString("44444444-4444-4444-8444-444444444444");

    @Test
    void rejectsTheWrongTargetBeforeItCanInspectAWorld() {
        ResourceDepositPreviewProtocol.Failure failure = assertInstanceOf(
                ResourceDepositPreviewProtocol.Failure.class,
                ResourceDepositPreviewEvaluator.evaluate(null, request("iron", "minecraft:overworld"),
                        UUID.fromString("99999999-9999-4999-8999-999999999999")));

        assertEquals("wrong_target", failure.code());
    }

    @Test
    void rejectsUnsupportedResourcesBeforeItCanInspectAWorld() {
        ResourceDepositPreviewProtocol.Failure failure = assertInstanceOf(
                ResourceDepositPreviewProtocol.Failure.class,
                ResourceDepositPreviewEvaluator.evaluate(null,
                        request("not_in_the_catalogue", "minecraft:overworld"), SERVER));

        assertEquals("unknown_resource", failure.code());
    }

    @Test
    void rejectsMalformedDimensionsBeforeItCanInspectAWorld() {
        ResourceDepositPreviewProtocol.Failure failure = assertInstanceOf(
                ResourceDepositPreviewProtocol.Failure.class,
                ResourceDepositPreviewEvaluator.evaluate(null, request("iron", "not a dimension"), SERVER));

        assertEquals("invalid_dimension", failure.code());
    }

    @Test
    void rejectsOutOfRangeAuthoredGeometryInTheCatalogueOwnWordsBeforeAnyWorld() {
        ResourceDepositPreviewProtocol.Failure above = assertInstanceOf(
                ResourceDepositPreviewProtocol.Failure.class,
                ResourceDepositPreviewEvaluator.evaluate(null,
                        request("iron", "minecraft:overworld", 200), SERVER));
        assertEquals("geometry_rejected", above.code());
        assertFalse(above.retryable(), "a bad authored radius is not a transient condition");
        assertTrue(above.detail().contains("128"),
                "the refusal must quote the catalogue bound: " + above.detail());

        ResourceDepositPreviewProtocol.Failure below = assertInstanceOf(
                ResourceDepositPreviewProtocol.Failure.class,
                ResourceDepositPreviewEvaluator.evaluate(null,
                        request("gold", "minecraft:overworld", 4), SERVER));
        assertEquals("geometry_rejected", below.code());
        assertTrue(below.detail().contains("below the minimum"),
                "the refusal must say which bound failed: " + below.detail());
    }

    @Test
    void acceptsBoundaryGeometryProvenByFallingThroughToTheDimensionCheck() {
        // A boundary radius must pass the geometry gate; with no live server the very next
        // check is the dimension parse, so reaching invalid_dimension IS the acceptance proof.
        for (int boundary : new int[] {1, 128}) {
            ResourceDepositPreviewProtocol.Failure failure = assertInstanceOf(
                    ResourceDepositPreviewProtocol.Failure.class,
                    ResourceDepositPreviewEvaluator.evaluate(null,
                            request("iron", "not a dimension", boundary), SERVER));
            assertEquals("invalid_dimension", failure.code(),
                    "boundary radius " + boundary + " must clear the geometry gate");
        }
    }

    private static ResourceDepositPreviewProtocol.Request request(String resource, String dimension) {
        return new ResourceDepositPreviewProtocol.Request(PREVIEW, DEPOSIT, 7, resource,
                new ResourceDepositPreviewProtocol.Target(
                        SHARD, SERVER, "Britannia", dimension), -12, 34);
    }

    private static ResourceDepositPreviewProtocol.Request request(
            String resource, String dimension, int radius) {
        return new ResourceDepositPreviewProtocol.Request(PREVIEW, DEPOSIT, 7, resource,
                new ResourceDepositPreviewProtocol.Target(
                        SHARD, SERVER, "Britannia", dimension), -12, 34,
                java.util.Optional.of(new ResourceDepositPreviewProtocol.Geometry(radius)));
    }
}
