package com.seggellion.britannia_mod.economy;

import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ProduceCommodityManifestTest {
    @Test void approvedAdditionsAndExactNamespacesCannotDrift() throws Exception {
        var file = Path.of(System.getProperty("britannia.projectDir"), "src/main/resources/economy/supported_produce.json");
        var data = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        var approved = Set.of(("yellow_onion green_onion watermelon beans vanilla_melon pineapple strawberry blueberry "
                + "raspberry cranberry blackberry huckleberry mulberry elderberry cherries snow_peas peas turnips lemon "
                + "lime orange olive plum bell_peppers cucumbers honeydew cantaloupe broccoli cauliflower rhubarb celery "
                + "radish parsnip yam rutabaga grapes").split(" "));
        var actual = new HashSet<String>();
        for (var value : data.getAsJsonArray("entries")) {
            var row = value.getAsJsonObject();
            if (row.get("added").getAsBoolean()) {
                actual.add(row.getAsJsonArray("crop_ids").get(0).getAsString());
                assertTrue(row.get("base_price").getAsDouble() > 0);
                assertFalse(row.get("comparison").getAsString().isBlank());
            }
            String id = row.get("item_id").getAsString();
            var mapping = CommodityMappings.forId(id).orElseThrow();
            assertEquals(row.get("commodity_key").getAsString(), mapping.normalizedKey());
            assertEquals(CommodityUnit.QUANTITY, mapping.unit());
            assertTrue(CommodityMappings.forId("unrelated:" + id.split(":")[1]).isEmpty());
        }
        assertEquals(approved, actual);
        assertEquals("produce|fruit|melon_slice", CommodityMappings.forId("minecraft:melon_slice").orElseThrow().normalizedKey());
        assertTrue(CommodityMappings.forId("britannia_mod:melon_slice").isEmpty());
        assertEquals("carrots", CommodityMappings.forId("minecraft:carrot").orElseThrow().itemName());
        assertEquals("apple", CommodityMappings.forId("minecraft:apple").orElseThrow().itemName());
        assertEquals("broccoli", CommodityMappings.forId("broccoli").orElseThrow().itemName());
    }
}

