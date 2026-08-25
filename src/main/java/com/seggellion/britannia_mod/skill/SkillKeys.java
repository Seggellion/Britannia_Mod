package com.seggellion.britannia_mod.skill;

import java.util.Locale;
import java.util.Map;

/**
 * The one place a skill name becomes the key the skill tables actually use.
 *
 * <p>Every store in the skill system — {@code SkillManager}'s server-side map, the client's
 * {@code ClientSkillTable}, the recipe catalogue's {@code SkillRequirement} — keys on a lowercase
 * slug, and Rails is the naming authority for what those slugs are. Historically each of those
 * stores normalized on its own: writes lowercased, reads mostly did not, and the recipe data once
 * spelled Blacksmithy as {@code blacksmith}. Two spellings of one skill then behaved as two
 * independent skills, which is exactly the defect this class exists to make unexpressible: every
 * read and every write resolves through {@link #canonical} first, so an alias can only ever reach
 * the one canonical row.
 *
 * <h2>Aliases</h2>
 * {@code blacksmith → blacksmithy} is the single known legacy alias: old recipe data and early
 * fixtures used the short form, while Rails has always seeded the official Ultima Online name
 * {@code blacksmithy}. The map is the narrow migration path for any value persisted under the old
 * spelling — a Rails row, an NBT remnant, a hard-coded caller — and deliberately nothing more.
 * A new alias is added here or nowhere.
 */
public final class SkillKeys {

    /** Legacy spelling → canonical slug. Consulted after trimming and lowercasing. */
    private static final Map<String, String> ALIASES = Map.of(
            "blacksmith", "blacksmithy"
    );

    private SkillKeys() {
    }

    /**
     * The canonical key for any spelling of a skill name: trimmed, lowercased, and with known
     * legacy aliases resolved. Null resolves to an empty string, which no skill table contains.
     */
    public static String canonical(String skillName) {
        if (skillName == null) {
            return "";
        }
        String normalized = skillName.trim().toLowerCase(Locale.ROOT);
        return ALIASES.getOrDefault(normalized, normalized);
    }
}
