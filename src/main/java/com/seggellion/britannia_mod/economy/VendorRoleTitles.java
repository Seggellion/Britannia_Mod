package com.seggellion.britannia_mod.economy;

import java.util.Locale;

/**
 * Vendor/Trader Milestone 17: humanizes a stamped economic NPC type key into
 * the generic vendor entity's role title (e.g. {@code weaponsmith_vendor} →
 * "Weaponsmith"). Free of Minecraft imports so the contract is plain-JUnit
 * testable; {@code GenericVendorEntity} delegates here.
 */
public final class VendorRoleTitles {
    private VendorRoleTitles() {
    }

    public static String humanize(String economicNpcTypeKey) {
        String key = economicNpcTypeKey;
        if (key == null || key.isBlank()) return "Vendor";
        String base = key.endsWith("_vendor") ? key.substring(0, key.length() - "_vendor".length()) : key;
        String[] words = base.split("_");
        StringBuilder title = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (title.length() > 0) title.append(' ');
            title.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return title.isEmpty() ? "Vendor" : title.toString();
    }
}
