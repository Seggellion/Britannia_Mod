package com.seggellion.britannia_mod.economy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads the mirrored economy contracts without relying on checkout ancestors. */
final class RunuoContractFixtures {
    private RunuoContractFixtures() {}

    static JsonObject load(String filename) throws IOException {
        String resource = "/release-contracts/vendor-trader-economy/" + filename;
        JsonObject fixture;
        try (InputStream input = RunuoContractFixtures.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Required RunUO contract fixture missing: " + resource);
            }
            fixture = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }

        // The development tree keeps its internal matrix. Fail if its mirrored
        // public test fixture drifts; public checkouts contain the fixture alone.
        String projectDir = System.getProperty("britannia.projectDir");
        if (projectDir != null) {
            Path canonical = Path.of(projectDir, "docs", "vendor-trader-economy", filename);
            if (Files.exists(canonical)) {
                JsonObject internal = JsonParser.parseString(Files.readString(canonical))
                        .getAsJsonObject();
                if (!fixture.equals(internal)) {
                    throw new IOException("RunUO contract fixture differs from " + canonical);
                }
            }
        }
        return fixture;
    }
}
