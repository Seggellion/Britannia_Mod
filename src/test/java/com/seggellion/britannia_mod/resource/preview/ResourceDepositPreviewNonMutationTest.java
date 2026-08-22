package com.seggellion.britannia_mod.resource.preview;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static guardrails supplement the live GameTest that snapshots blocks and ledgers. */
class ResourceDepositPreviewNonMutationTest {
    @Test
    void evaluatorCannotReachAnyManagedWorldMutationEntryPoint() throws IOException {
        String source = readProjectSource("resource/preview/ResourceDepositPreviewEvaluator.java");

        assertFalse(source.contains("MaterializationService.materialize("));
        assertFalse(source.contains(".register("));
        assertFalse(source.contains("setBlock("));
        assertFalse(source.contains("recordProgress("));
        assertFalse(source.contains("BrokenBlock"));
        assertTrue(source.contains("MaterializationService.inspect("));
        assertTrue(source.contains("DepositLedger.get(level).all()"));
    }

    @Test
    void readOnlyInspectionEndsBeforeTheOnlyWritePath() throws IOException {
        String source = readProjectSource("resource/placement/MaterializationService.java");
        int inspectionStart = source.indexOf("public static Inspection inspect(");
        int writeBoundary = source.indexOf("public static Result materialize(", inspectionStart);
        String inspection = source.substring(inspectionStart, writeBoundary);

        assertFalse(inspection.contains("setBlock("));
        assertTrue(inspection.contains("evaluate(level"));
    }

    private static String readProjectSource(String relativePath) throws IOException {
        Path cursor;
        try {
            cursor = Path.of(ResourceDepositPreviewNonMutationTest.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath();
        } catch (Exception invalidLocation) {
            throw new IOException("cannot resolve test class location", invalidLocation);
        }
        while (cursor != null) {
            Path candidate = cursor.resolve("src/main/java/com/seggellion/britannia_mod").resolve(relativePath);
            if (Files.isRegularFile(candidate)) return Files.readString(candidate);
            cursor = cursor.getParent();
        }
        throw new IOException("cannot locate project source: " + relativePath);
    }
}
