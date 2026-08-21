package com.seggellion.britannia_mod.mining;

import com.seggellion.britannia_mod.item.BritanniaPickaxeItem;
import com.seggellion.britannia_mod.item.QualityToolItem;

import net.minecraft.world.item.ItemStack;

/**
 * The one answer to "may this tool work a Mining resource".
 *
 * <h2>Why this class exists</h2>
 * Milestone 1 of the OreVein remediation found that the Mining half of the mod had no tool policy
 * at all in its gate, and a private copy of one in its yield handler. {@code MiningBreakGate}
 * decided purely on skill, so a sufficiently skilled player reached vanilla breaking with anything
 * in hand; {@code CustomBlockBreakHandler} then declined to act because the item was not a
 * pickaxe, and the block was destroyed outside the managed transaction — no yield, no restoration
 * debt, no award, and for a Britannia ore no drop at all, because those blocks have no loot table.
 *
 * <p>The reachable route was the two-handed axe: {@code CityGameModeHandler} counts it a special
 * tool and puts the player in survival, and {@code StructureProtectionHandler} counts it an
 * allowed tool and declines to refuse, so nothing between the gate and vanilla breaking stopped
 * it. A bare hand and a vanilla pickaxe were only ever blocked <em>incidentally</em>, by those two
 * handlers, which is not a policy — it is a coincidence that happened to hold.
 *
 * <p>So the rule lives in exactly one place and both sides read it. The gate refuses before any
 * mutation; the yield handler acts only on the same predicate; neither carries its own idea of
 * what a mining tool is.
 *
 * <h2>Scope</h2>
 * Deliberately the identical predicate {@code CustomBlockBreakHandler.isBritanniaPickaxe} already
 * used, so the successful-extraction path is unchanged to the item. The only behavioural
 * difference in milestone 1 is that the tools this rejects are now <em>refused</em> instead of
 * being handed to vanilla.
 *
 * <p>An item tag belongs here eventually — {@code britannia_mod:tools/extracts/<resource>}, stored
 * on the resource definition, the way {@code ManagedDeposits} already does it for clay and silica.
 * That is milestone 2's data migration, and doing it now would pull a later milestone forward.
 * The seam is this class: when the tag arrives, this predicate is what it replaces.
 */
public final class MiningExtractionTool {

    private MiningExtractionTool() {
    }

    /**
     * Whether this stack may work a Mining-catalogued resource.
     *
     * <p>{@code ToolRegistry.PICKAXE} is a {@link QualityToolItem}, so the two checks below cover
     * every project pickaxe. A Britannia shovel is not one of them by design — a shovel works a
     * {@code ManagedDeposits} bed, which is the other resource system and has its own tag — and
     * neither is the two-handed axe.
     */
    public static boolean isAuthorized(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.getItem() instanceof QualityToolItem
                || stack.getItem() instanceof BritanniaPickaxeItem;
    }
}
