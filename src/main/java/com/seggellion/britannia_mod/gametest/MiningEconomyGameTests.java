package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mining milestone 8: every mined metal completes the loop it exists for — mine, refine, and be
 * accepted by the blacksmith. Asserted here rather than in a unit test because
 * {@code UOMetalToolMaterial} resolves real registry items.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class MiningEconomyGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** The drop names the Mining catalogue produces for each approved metal. */
    private static final List<String> MINED_METAL_DROPS = List.of(
            "Iron ore", "Silver ore", "Tin ore", "Shadow Iron ore", "Copper ore",
            "Gold ore", "Agapite ore", "Verite ore", "Valorite ore");

    private MiningEconomyGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** Exactly what {@code SmallForgeBlock} does to turn a mined stack into a metal. */
    private static UOMetalToolMaterial refine(String dropName) {
        return UOMetalToolMaterial.getMaterialByName(
                dropName.toLowerCase(Locale.ROOT).replace(" ore", ""));
    }

    @GameTest(template = TEMPLATE)
    public static void everyMinedMetalRefinesIntoItsOwnIngot(GameTestHelper helper) {
        Map<Item, String> ingotOwners = new HashMap<>();

        for (String drop : MINED_METAL_DROPS) {
            UOMetalToolMaterial metal = refine(drop);
            check(metal != null, drop + " cannot be refined -- the forge would reject it");

            Item ingot = metal.getIngotSupplier().get();
            check(ingot != null && ingot != net.minecraft.world.item.Items.AIR,
                    drop + " refines to no registered ingot");

            String previous = ingotOwners.put(ingot, drop);
            check(previous == null,
                    drop + " and " + previous + " both refine to the same ingot -- the ladder would collapse");
        }
        helper.succeed();
    }

    /**
     * The other half of the loop: the ingot a mined metal produces must be the same ingot
     * {@code BlacksmithCrafting} accepts from the offhand, or refined metal could not be crafted.
     */
    @GameTest(template = TEMPLATE)
    public static void everyRefinedIngotIsAcceptedByTheBlacksmith(GameTestHelper helper) {
        for (String drop : MINED_METAL_DROPS) {
            UOMetalToolMaterial metal = refine(drop);
            Item ingot = metal.getIngotSupplier().get();

            UOMetalToolMaterial accepted = UOMetalToolMaterial.getMaterialByIngot(ingot);
            check(accepted == metal,
                    "the blacksmith resolves " + drop + "'s ingot to "
                            + (accepted == null ? "nothing" : accepted.getMetalName())
                            + " instead of " + metal.getMetalName());
        }
        helper.succeed();
    }

    /** One Silver metal, and no metal left behind by the retirement. */
    @GameTest(template = TEMPLATE)
    public static void theMetalRosterMatchesTheApprovedLadder(GameTestHelper helper) {
        check(UOMetalToolMaterial.values().length == MINED_METAL_DROPS.size(),
                "the metal roster and the mined-metal ladder must stay the same size");
        check(refine("High-Purity Silver ore") == null,
                "the retired premium node must not resolve to a metal of its own");
        helper.succeed();
    }
}
