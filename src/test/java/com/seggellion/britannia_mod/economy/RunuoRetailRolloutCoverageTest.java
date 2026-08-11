package com.seggellion.britannia_mod.economy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vendor/Trader Milestone 17: the machine-readable RETAIL completeness test —
 * the milestone's acceptance criterion is that the project matrix reports
 * every required RunUO Vendor as implemented or explicitly excluded by
 * approved design, and this test makes that claim regress-proof: a vendor or
 * retail row that loses its rollout status fails the build. The buyback-side
 * twin is {@link RunuoBuybackCoverageTest} (Milestone 11).
 */
final class RunuoRetailRolloutCoverageTest {
    private static final Set<String> VENDOR_ROLLOUT_PREFIXES = Set.of(
            "implemented:", "merged_into:", "merged_into_trader:"
    );
    private static final Set<String> VENDOR_ROLLOUT_TERMINALS = Set.of(
            "excluded_service", "excluded_unsupported", "excluded_guildmaster_territory",
            "excluded_pending_mobile_fulfillment"
    );
    private static final Set<String> ROW_STATUSES = Set.of(
            "seeded", "seeded_material",
            "excluded_pending_item", "excluded_pending_magic", "excluded_pending_mobile_fulfillment",
            "excluded_pending_commodity_family", "excluded_service", "excluded_unsupported",
            "excluded_no_retail_vendor"
    );

    private static JsonObject mapping;
    private static JsonObject rollout;

    @BeforeAll
    static void load() throws IOException {
        mapping = parse(Path.of("docs", "vendor-trader-economy", "runuo_ultimacraft_mapping.json"));
        rollout = parse(Path.of("docs", "vendor-trader-economy", "economic_vendor_rollout.json"));
    }

    private static JsonObject parse(Path relative) throws IOException {
        Path base = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int depth = 0; depth < 6 && base != null; depth++, base = base.getParent()) {
            Path candidate = base.resolve(relative);
            if (Files.exists(candidate)) {
                return JsonParser.parseString(Files.readString(candidate)).getAsJsonObject();
            }
        }
        throw new IOException(relative + " not found walking up from " + System.getProperty("user.dir"));
    }

    @Test
    void everyVendorCarriesARolloutDisposition() {
        int total = 0;
        for (JsonElement element : mapping.getAsJsonArray("vendors")) {
            JsonObject vendor = element.getAsJsonObject();
            total++;
            String id = vendor.get("vendor_class").getAsString();
            assertTrue(vendor.has("vendor_rollout"), id + " has no vendor_rollout disposition");
            String rolloutStatus = vendor.get("vendor_rollout").getAsString();
            boolean valid = VENDOR_ROLLOUT_TERMINALS.contains(rolloutStatus)
                    || VENDOR_ROLLOUT_PREFIXES.stream().anyMatch(rolloutStatus::startsWith);
            assertTrue(valid, id + " has unknown vendor_rollout " + rolloutStatus);
        }
        assertTrue(total >= 74, "vendor count regressed: " + total);
    }

    @Test
    void everyRetailRowIsImplementedOrExplicitlyExcluded() {
        int total = 0;
        int seeded = 0;
        for (Map.Entry<String, JsonElement> catalog : mapping.getAsJsonObject("catalogs").entrySet()) {
            List<JsonElement> rows = catalog.getValue().getAsJsonObject()
                    .getAsJsonArray("buy_rows").asList();
            for (JsonElement element : rows) {
                JsonObject row = element.getAsJsonObject();
                total++;
                String id = catalog.getKey() + " buy #" + row.get("ordinal").getAsInt();
                assertTrue(row.has("product_status"), id + " has no product_status");
                String status = row.get("product_status").getAsString();
                assertTrue(ROW_STATUSES.contains(status), id + " has unknown product_status " + status);
                if (status.startsWith("seeded")) {
                    seeded++;
                    assertTrue(row.has("uc_item_id") && !row.get("uc_item_id").isJsonNull(),
                            id + " is seeded without an item id");
                }
            }
        }
        assertEquals(1015, total, "retail row count changed; regenerate the rollout");
        assertTrue(seeded >= 260, "seeded retail coverage regressed: " + seeded);
    }

    @Test
    void theRolloutArtifactMatchesTheMatrixAccounting() {
        long products = rollout.getAsJsonArray("products").size();
        long vendorTypes = rollout.getAsJsonArray("vendor_types").size();
        assertTrue(products >= 90, "rollout product count regressed: " + products);
        assertTrue(vendorTypes >= 30, "rollout vendor type count regressed: " + vendorTypes);

        // Every product carries exactly one requirements shape and a real price.
        for (JsonElement element : rollout.getAsJsonArray("products")) {
            JsonObject product = element.getAsJsonObject();
            String id = product.get("item_id").getAsString();
            boolean commodity = product.has("commodity_requirements");
            boolean material = product.has("material_requirements");
            assertTrue(commodity ^ material, id + " must have exactly one requirements shape");
            assertTrue(product.get("price_gold").getAsInt() >= 1, id + " has no RunUO gold price");
        }
    }
}
