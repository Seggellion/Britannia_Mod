package com.seggellion.britannia_mod.dye.service;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Only the documented {@code colour_family_} prefix participates in colour-family tie-breaking. */
final class ColourFamilyTags {
    private static final String PREFIX = "colour_family_";

    private ColourFamilyTags() {
    }

    static List<String> shared(List<String> pigmentTags, List<String> entryTags) {
        Set<String> entryFamilies = new TreeSet<>();
        entryTags.stream().filter(ColourFamilyTags::isFamily).forEach(entryFamilies::add);
        TreeSet<String> shared = new TreeSet<>();
        pigmentTags.stream().filter(ColourFamilyTags::isFamily)
                .filter(entryFamilies::contains).forEach(shared::add);
        return List.copyOf(shared);
    }

    private static boolean isFamily(String tag) {
        return tag.startsWith(PREFIX) && tag.length() > PREFIX.length();
    }
}
