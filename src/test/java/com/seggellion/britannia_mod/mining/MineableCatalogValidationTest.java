package com.seggellion.britannia_mod.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import org.junit.jupiter.api.Test;

/**
 * Milestone 2: invalid catalogues must fail loudly at parse time (duplicates, out-of-range skills,
 * missing identities, impossible combinations, malformed references), and a future rock must be
 * addable as pure data.
 */
class MineableCatalogValidationTest {

    private static String definition(String id, String overrides) {
        String base = """
                {
                  "id": "%s",
                  "display_name": "Test %s",
                  "category": "stone",
                  "status": "active",
                  "required_mining": 10.0,
                  "challenge": 10.0,
                  "blocks": ["minecraft:%s"],
                  "drop": "Test Drop",
                  "economy_commodity": null,
                  "restorable": true
                }""".formatted(id, id, id);
        if (overrides.isEmpty()) return base;
        return base.substring(0, base.length() - 1) + "," + overrides + "}";
    }

    private static String catalog(String... definitions) {
        return "{\"schema\":1,\"mineables\":[" + String.join(",", definitions) + "]}";
    }

    private static MineableCatalog parse(String json) {
        return MineableCatalog.parse(new StringReader(json));
    }

    @Test
    void unsupportedSchemaIsRejected() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> parse("{\"schema\":2,\"mineables\":[]}"));
        assertTrue(error.getMessage().contains("schema"));
    }

    @Test
    void emptyCatalogueIsRejected() {
        assertThrows(IllegalStateException.class, () -> parse(catalog()));
    }

    @Test
    void duplicateDefinitionIdsAreRejected() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> parse(catalog(definition("marble", ""), definition("marble", ""))));
        assertTrue(error.getMessage().contains("Duplicate mineable definition id"));
    }

    @Test
    void oneBlockClaimedTwiceIsRejected() {
        String second = definition("slate", "").replace("minecraft:slate", "minecraft:marble");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> parse(catalog(definition("marble", ""), second)));
        assertTrue(error.getMessage().contains("claimed by both"));
    }

    @Test
    void outOfRangeRequirementIsRejected() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> parse(catalog(definition("marble", "\"required_mining\": 105.0")
                        .replace("\"required_mining\": 10.0,", ""))));
        assertTrue(error.getMessage().contains("out of range"));
    }

    @Test
    void outOfRangeChallengeIsRejected() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> parse(catalog(definition("marble", "\"challenge\": -1.0")
                        .replace("\"challenge\": 10.0,", ""))));
        assertTrue(error.getMessage().contains("out of range"));
    }

    @Test
    void missingResourceIdentityIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> parse(catalog(definition("marble", "").replace("\"drop\": \"Test Drop\",", ""))));
        assertThrows(IllegalStateException.class,
                () -> parse(catalog(definition("marble", "").replace("\"Test Drop\"", "\"\""))));
        assertThrows(IllegalStateException.class,
                () -> parse(catalog(definition("marble", "").replace("\"Test marble\"", "\"\""))));
    }

    @Test
    void emptyBlockListIsRejected() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> parse(catalog(definition("marble", "").replace("[\"minecraft:marble\"]", "[]"))));
        assertTrue(error.getMessage().contains("claims no blocks"));
    }

    @Test
    void malformedBlockIdIsRejected() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> parse(catalog(definition("marble", "")
                        .replace("minecraft:marble", "not a block id"))));
        assertTrue(error.getMessage().contains("Malformed block id"));
    }

    @Test
    void unknownCategoryOrStatusIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> parse(catalog(definition("marble", "").replace("\"stone\"", "\"gemstone\""))));
        assertThrows(IllegalStateException.class,
                () -> parse(catalog(definition("marble", "").replace("\"active\"", "\"enabled\""))));
    }

    @Test
    void activeButUnrestorableIsAnImpossibleCombination() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> parse(catalog(definition("marble", "")
                        .replace("\"restorable\": true", "\"restorable\": false"))));
        assertTrue(error.getMessage().contains("must be restorable"));
    }

    @Test
    void futureRockIsRegistrableAsPureData() {
        MineableCatalog extended = parse(catalog(
                definition("marble", "\"notes\": \"future rock added with zero code changes\"")));
        MineableDefinition marble = extended.resolveBlock("minecraft:marble").orElseThrow();
        assertEquals("marble", marble.id());
        assertEquals(10.0f, marble.requiredMining(), 0.0f);
        assertEquals(MineableDefinition.Category.STONE, marble.category());
        assertTrue(extended.resolveBlock("minecraft:unrelated").isEmpty());
    }
}
