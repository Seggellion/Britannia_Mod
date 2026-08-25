package com.seggellion.britannia_mod.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.skill.crafting.SkillRequirement;
import org.junit.jupiter.api.Test;

/**
 * The canonical skill-key resolver: one spelling per skill, everywhere.
 *
 * <p>This is the blacksmith/blacksmithy defect made unexpressible. Rails seeds the official
 * Ultima Online name {@code blacksmithy}; old recipe data spelled it {@code blacksmith}; and any
 * store that normalized writes but not reads (or vice versa) split the one skill into two — a
 * player's real progression on one key, every lookup landing on the other's permanent zero.
 */
class SkillKeysTest {

    @Test
    void canonicalLowercasesAndTrims() {
        assertEquals("mining", SkillKeys.canonical("Mining"));
        assertEquals("mining", SkillKeys.canonical("  MINING  "));
        assertEquals("blacksmithy", SkillKeys.canonical("Blacksmithy"));
    }

    @Test
    void theLegacyBlacksmithAliasResolvesToBlacksmithy() {
        assertEquals("blacksmithy", SkillKeys.canonical("blacksmith"));
        assertEquals("blacksmithy", SkillKeys.canonical("Blacksmith"));
        assertEquals("blacksmithy", SkillKeys.canonical(" BLACKSMITH "));
    }

    @Test
    void canonicalKeysPassThroughUnchanged() {
        for (String slug : new String[] {"blacksmithy", "mining", "fishing", "tailoring",
                "carpentry", "magery", "musicianship"}) {
            assertEquals(slug, SkillKeys.canonical(slug), slug + " is already canonical");
        }
    }

    @Test
    void nullResolvesToAKeyNoTableContains() {
        assertEquals("", SkillKeys.canonical(null));
    }

    /** The recipe catalogue's requirement keys resolve through the same canonicalization. */
    @Test
    void skillRequirementsNormalizeThroughTheSameResolver() {
        assertEquals("blacksmithy", new SkillRequirement("blacksmith", 14.5f).skillKey());
        assertEquals("blacksmithy", new SkillRequirement("Blacksmithy", 14.5f).skillKey());
        assertEquals("tailoring", new SkillRequirement("Tailoring", 50.0f).skillKey());
    }

    /**
     * The client projection resolves aliases on read too: the synced map is keyed by the server's
     * canonical slugs, and a legacy spelling must reach the same row rather than a phantom zero.
     */
    @Test
    void clientSkillTableReadsResolveAliases() {
        ClientSkillTable.beginSession();
        assertTrue(ClientSkillTable.applyAuthoritativeSync(
                SkillManager.SkillDataState.AVAILABLE,
                java.util.Map.of("blacksmithy", 42.5f),
                1L, false));
        try {
            assertEquals(42.5f, ClientSkillTable.get("blacksmithy"));
            assertEquals(42.5f, ClientSkillTable.get("blacksmith"));
            assertEquals(42.5f, ClientSkillTable.get("Blacksmith"));
            assertEquals(1L, ClientSkillTable.revision());
        } finally {
            ClientSkillTable.clear();
        }
    }
}
