package com.seggellion.britannia_mod.skill.crafting;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.Map;

/**
 * The single authority for how much Blacksmithy it takes to <em>work</em> a metal.
 *
 * <p>Recipes say how hard a shape is; this says how hard a material is. Before this class the
 * second question was never asked anywhere in the mod: {@code BlacksmithCrafting} read the
 * recipe's own requirement and then accepted whatever ingot happened to be in the offhand, so a
 * Blacksmithy 0.0 smith could forge a Valorite dagger as easily as an iron one. Both halves are
 * now consulted, and the harder of the two decides.
 *
 * <h2>Where these numbers come from</h2>
 *
 * <p>They are recovered, not invented. This repository already encodes the Ultima Online smithing
 * ladder once — as the Mining ladder in {@code data/britannia_mod/mining/mineables.json}, whose
 * {@code required_mining} values reproduce RunUO's {@code DefBlacksmithy} sub-resource table
 * exactly (0 / 65 / 70 / 75 / 85 / 90 / 95 / 99) with Tin standing in the slot UO gives Dull
 * Copper, a metal this project does not have. Each requirement below therefore has at least one
 * in-repo attestation, and most have two:
 *
 * <table>
 *   <caption>Provenance of every value</caption>
 *   <tr><th>Metal</th><th>Required</th><th>Attested by</th></tr>
 *   <tr><td>Iron</td><td>0</td><td>mineables.json {@code iron} + UO iron</td></tr>
 *   <tr><td>Silver</td><td>55</td><td>mineables.json {@code silver} (project-specific metal)</td></tr>
 *   <tr><td>Tin</td><td>65</td><td>mineables.json {@code tin} + UO dull copper</td></tr>
 *   <tr><td>Shadow Iron</td><td>70</td><td>mineables.json {@code shadow_iron} + UO shadow iron</td></tr>
 *   <tr><td>Copper</td><td>75</td><td>mineables.json {@code copper} + UO copper</td></tr>
 *   <tr><td>Bronze</td><td>80</td><td>UO bronze <em>only</em> — see below</td></tr>
 *   <tr><td>Gold</td><td>85</td><td>mineables.json {@code gold} + UO gold</td></tr>
 *   <tr><td>Agapite</td><td>90</td><td>mineables.json {@code agapite} + UO agapite</td></tr>
 *   <tr><td>Verite</td><td>95</td><td>mineables.json {@code verite} + UO verite</td></tr>
 *   <tr><td>Valorite</td><td>99</td><td>mineables.json {@code valorite} + UO valorite</td></tr>
 * </table>
 *
 * <p><b>Bronze is the one value with no in-repo analogue.</b> It is alloyed in the forge and never
 * mined, so {@code mineables.json} has nothing to say about it. UO puts bronze at 80, which is
 * also exactly where this project's own economy already places it — between Copper (75) and Gold
 * (85), matching the tier note on {@link UOMetalToolMaterial#BRONZE}. It is flagged here rather
 * than buried so the owner can move it with one edit if the intent was different.
 *
 * <p>Deliberately kept level with, but separate from, the Mining ladder. Mining a metal and
 * working it are different progressions that merely happen to start from the same UO table; a
 * future decision to make Valorite harder to smith than to mine changes one number here and
 * nothing in the extraction pipeline.
 *
 * <p>The map is total over {@link UOMetalToolMaterial} and a unit test enforces that. An unmapped
 * metal fails <em>closed</em> — nobody can work it — because a new metal silently defaulting to 0
 * is precisely the failure this class exists to make impossible.
 */
public final class MetalProgression {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The canonical Blacksmithy skill slug, spelled the way the skill tables spell it. */
    public static final String SKILL_ID = "blacksmithy";

    /**
     * The answer for a metal nobody has assigned a requirement to: unreachable, so an unmapped
     * metal is refused to everyone instead of being handed to novices.
     */
    public static final float UNKNOWN_METAL_REQUIREMENT = Float.MAX_VALUE;

    private static final Map<UOMetalToolMaterial, Float> REQUIREMENTS =
            new EnumMap<>(UOMetalToolMaterial.class);

    static {
        REQUIREMENTS.put(UOMetalToolMaterial.IRON, 0.0f);
        REQUIREMENTS.put(UOMetalToolMaterial.SILVER, 55.0f);
        REQUIREMENTS.put(UOMetalToolMaterial.TIN, 65.0f);
        REQUIREMENTS.put(UOMetalToolMaterial.SHADOW_IRON, 70.0f);
        REQUIREMENTS.put(UOMetalToolMaterial.COPPER, 75.0f);
        // The only requirement not attested by this repository's own Mining ladder; bronze is
        // alloyed, never mined. See the class note before changing it.
        REQUIREMENTS.put(UOMetalToolMaterial.BRONZE, 80.0f);
        REQUIREMENTS.put(UOMetalToolMaterial.GOLD, 85.0f);
        REQUIREMENTS.put(UOMetalToolMaterial.AGAPITE, 90.0f);
        REQUIREMENTS.put(UOMetalToolMaterial.VERITE, 95.0f);
        REQUIREMENTS.put(UOMetalToolMaterial.VALORITE, 99.0f);
    }

    private MetalProgression() {
    }

    /**
     * Blacksmithy needed to work this metal at all, whatever is being made from it.
     *
     * @param material the metal being worked, or null when the offhand holds nothing recognised
     * @return the requirement, or {@link #UNKNOWN_METAL_REQUIREMENT} for an unmapped or null metal
     */
    public static float requiredBlacksmithy(UOMetalToolMaterial material) {
        if (material == null) {
            return UNKNOWN_METAL_REQUIREMENT;
        }
        Float requirement = REQUIREMENTS.get(material);
        if (requirement == null) {
            // Reachable only if a metal is added to the enum without a requirement, which the
            // completeness unit test refuses. Denying is the safe half of that mistake.
            LOGGER.error("No Blacksmithy requirement is defined for metal {}; refusing to work it.",
                    material.name());
            return UNKNOWN_METAL_REQUIREMENT;
        }
        return requirement;
    }

    /** Whether this Blacksmithy value may work this metal; inclusive, matching the Mining gate. */
    public static boolean canWork(float blacksmithy, UOMetalToolMaterial material) {
        return blacksmithy >= requiredBlacksmithy(material);
    }

    /** Every mapped requirement, for tests, tooling and the progression report. */
    public static Map<UOMetalToolMaterial, Float> all() {
        return Map.copyOf(REQUIREMENTS);
    }
}
