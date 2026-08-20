package com.seggellion.britannia_mod.economy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Housing Deed Milestone 9: the two halves of the retail accounting must agree.
 *
 * <p>{@code runuo_ultimacraft_mapping.json} carries a status on every RunUO retail row;
 * {@code economic_vendor_rollout.json} carries the histogram of those statuses, and Rails keeps
 * its own copy of the same rollout file. Nothing previously compared the two, so a row could be
 * flipped in one and not counted in the other and both suites would stay green -- which is how
 * accounting stops being true without anybody noticing.
 *
 * <p>This is the drift guard. When it fails, one of the two files has moved on its own, and the
 * Rails copy needs the identical change.
 */
final class RunuoAccountingDriftTest {

    private static JsonObject mapping;
    private static JsonObject rollout;

    @BeforeAll
    static void load() throws IOException {
        mapping = parse(Path.of("docs", "vendor-trader-economy", "runuo_ultimacraft_mapping.json"));
        rollout = parse(Path.of("docs", "vendor-trader-economy", "economic_vendor_rollout.json"));
    }

    private static JsonObject parse(Path relative) throws IOException {
        Path base = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (base != null) {
            Path candidate = base.resolve(relative);
            if (Files.exists(candidate)) {
                return JsonParser.parseString(Files.readString(candidate)).getAsJsonObject();
            }
            base = base.getParent();
        }
        throw new IOException("could not find " + relative);
    }

    @Test
    void theRolloutHistogramMatchesTheMatrixRowByRow() {
        Map<String, Integer> counted = new TreeMap<>();
        for (Map.Entry<String, JsonElement> catalog : mapping.getAsJsonObject("catalogs").entrySet()) {
            for (JsonElement element : catalog.getValue().getAsJsonObject().getAsJsonArray("buy_rows")) {
                String status = element.getAsJsonObject().get("product_status").getAsString();
                counted.merge(status, 1, Integer::sum);
            }
        }

        Map<String, Integer> declared = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : rollout.getAsJsonObject("row_status_counts").entrySet()) {
            declared.put(entry.getKey(), entry.getValue().getAsInt());
        }

        assertEquals(declared, counted,
                "the rollout histogram and the retail matrix disagree. Whichever moved, the Rails "
                        + "copy of economic_vendor_rollout.json needs the same change -- the two "
                        + "repositories mirror this file.");
    }

    /**
     * The ten deeds the Architect actually sells are recorded as sold.
     *
     * <p>They were marked {@code excluded_service} under a 2026-08-11 decision that deed retail
     * stayed outside the vendor economy. Milestones 6 to 8 reversed that: the deeds are Rails
     * Products with material recipes and gold prices, bought from a staffed Architect. The nine
     * still excluded are houses this programme did not author.
     */
    @Test
    void theTenImplementedDeedsAreSeededWithItemIds() {
        JsonObject deeds = mapping.getAsJsonObject("catalogs").getAsJsonObject("SBHouseDeed");
        int seeded = 0;
        for (JsonElement element : deeds.getAsJsonArray("buy_rows")) {
            JsonObject row = element.getAsJsonObject();
            if (!"seeded".equals(row.get("product_status").getAsString())) continue;
            seeded++;
            assertEquals(false, row.get("uc_item_id").isJsonNull(),
                    row.get("type").getAsString() + " is seeded without an item id");
        }
        assertEquals(10, seeded,
                "expected the ten authored house deeds to be seeded; the other nine RunUO deeds "
                        + "are houses this programme deliberately did not author");
    }

    @Test
    void theArchitectIsRecordedAsImplemented() {
        for (JsonElement element : mapping.getAsJsonArray("vendors")) {
            JsonObject vendor = element.getAsJsonObject();
            if (!"Architect".equals(vendor.get("vendor_class").getAsString())) continue;
            assertEquals("implemented:architect_vendor", vendor.get("vendor_rollout").getAsString(),
                    "the Architect sells deeds through the economic vendor path now");
            return;
        }
        throw new AssertionError("the Architect is not in the vendor matrix at all");
    }
}
