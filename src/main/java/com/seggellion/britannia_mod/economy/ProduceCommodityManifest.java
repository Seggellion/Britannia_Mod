package com.seggellion.britannia_mod.economy;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Packaged, exact registry identities shared with the Rails insert-only rollout. */
public final class ProduceCommodityManifest {
    public record Entry(String itemId, String category, String subcategory, String itemName,
                        String displayName, List<String> cropIds) {}
    private static final List<Entry> ENTRIES = read();
    private ProduceCommodityManifest() {}

    public static List<Entry> entries() { return ENTRIES; }

    private static List<Entry> read() {
        try (var stream = ProduceCommodityManifest.class.getResourceAsStream("/economy/supported_produce.json")) {
            if (stream == null) throw new IllegalStateException("Missing supported produce manifest");
            var data = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (data.get("schema_version").getAsInt() != 1) throw new IllegalStateException("Unknown produce schema");
            var result = new ArrayList<Entry>();
            var ids = new HashSet<String>();
            for (var value : data.getAsJsonArray("entries")) {
                var row = value.getAsJsonObject();
                String id = row.get("item_id").getAsString();
                String category = row.get("category").getAsString();
                String subcategory = row.get("subcategory").getAsString();
                String name = row.get("item_name").getAsString();
                if (!ids.add(id) || !id.matches("(britannia_mod|minecraft):[a-z0-9_]+")
                        || !category.equals("produce") || !Set.of("fruit", "vegetable").contains(subcategory)
                        || !row.get("commodity_key").getAsString().equals(category + "|" + subcategory + "|" + name))
                    throw new IllegalStateException("Invalid produce identity: " + id);
                result.add(new Entry(id, category, subcategory, name, row.get("display_name").getAsString(),
                        row.getAsJsonArray("crop_ids").asList().stream().map(v -> v.getAsString()).toList()));
            }
            return List.copyOf(result);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot load supported produce manifest", e);
        }
    }
}

