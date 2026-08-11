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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Vendor/Trader Milestone 11: the machine-readable RunUO buyback completeness
 * test the playbook requires — every player-to-vendor buyback row in the
 * project-owned mapping must resolve to a Trader target or carry an explicit
 * unresolved/unsupported status. A silently dropped or statusless row fails
 * the build, so coverage can never regress unnoticed.
 */
final class RunuoBuybackCoverageTest {
    /** The ten legacy trader categories plus the five owner-approved new types (OQ-3). */
    private static final Set<String> KNOWN_TRADERS = Set.of(
            "wood_trader", "fish_trader", "salvage_trader", "alcohol_trader", "ore_trader",
            "stone_trader", "meat_trader", "grain_trader", "produce_trader", "fur_leather_trader",
            "reagent_trader", "provision_trader", "textile_trader", "glass_trader", "scribe_trader"
    );
    private static final Set<String> SELL_STATUSES = Set.of(
            "PROPOSED_DEFAULT", "REQUIRES_NEW_TRADER", "REQUIRES_OWNER_MAPPING"
    );
    private static final Set<String> PAYOUT_DENOMINATIONS = Set.of(
            "copper", "silver", "gold", "unresolved"
    );

    private static JsonObject mapping;

    @BeforeAll
    static void load() throws IOException {
        // Gradle test workers run from a build subdirectory; walk up to the repo root.
        Path relative = Path.of("docs", "vendor-trader-economy", "runuo_ultimacraft_mapping.json");
        Path base = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path path = null;
        for (int depth = 0; depth < 6 && base != null; depth++, base = base.getParent()) {
            Path candidate = base.resolve(relative);
            if (Files.exists(candidate)) {
                path = candidate;
                break;
            }
        }
        assertTrue(path != null, "mapping source not found walking up from "
                + System.getProperty("user.dir"));
        mapping = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    @Test
    void everyBuybackRowIsAccountedFor() {
        int total = 0;
        for (Map.Entry<String, JsonElement> catalog : mapping.getAsJsonObject("catalogs").entrySet()) {
            List<JsonElement> rows = catalog.getValue().getAsJsonObject()
                    .getAsJsonArray("sell_rows").asList();
            for (JsonElement element : rows) {
                JsonObject row = element.getAsJsonObject();
                total++;
                String id = catalog.getKey() + " sell #" + row.get("ordinal").getAsInt();

                String status = row.has("buyback_status") ? row.get("buyback_status").getAsString() : null;
                assertTrue(status != null && SELL_STATUSES.contains(status),
                        id + " has no valid buyback_status: " + status);

                switch (status) {
                    case "PROPOSED_DEFAULT", "REQUIRES_NEW_TRADER" -> {
                        String trader = row.has("target_trader") && !row.get("target_trader").isJsonNull()
                                ? row.get("target_trader").getAsString() : null;
                        assertTrue(trader != null && KNOWN_TRADERS.contains(trader),
                                id + " maps to an unknown trader: " + trader);
                        assertFalse(row.get("valuation_strategy").isJsonNull(),
                                id + " has no valuation strategy");
                    }
                    case "REQUIRES_OWNER_MAPPING" -> {
                        // Explicitly unresolved is allowed — but must stay explicit.
                        assertTrue(row.has("payout_denomination_status"),
                                id + " unresolved row lost its denomination status");
                    }
                    default -> fail(id + " unreachable status " + status);
                }

                assertTrue(row.has("payout_denomination_proposal")
                                && PAYOUT_DENOMINATIONS.contains(
                                row.get("payout_denomination_proposal").getAsString()),
                        id + " has no valid payout denomination proposal");
            }
        }
        assertTrue(total >= 915, "buyback row count regressed: " + total);
    }

    @Test
    void vendorsNeverBuyFromPlayersInTheMapping() {
        for (JsonElement element : mapping.getAsJsonArray("vendors")) {
            JsonObject vendor = element.getAsJsonObject();
            assertFalse(vendor.get("vendor_can_buy_from_player").getAsBoolean(),
                    vendor.get("vendor_class").getAsString()
                            + " must not expose player-to-vendor buyback (owner rule)");
        }
    }
}
