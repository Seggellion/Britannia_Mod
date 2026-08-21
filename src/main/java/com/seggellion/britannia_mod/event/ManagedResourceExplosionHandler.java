package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.deposit.ManagedDeposits;
import com.seggellion.britannia_mod.mining.MineableDefinition;
import com.seggellion.britannia_mod.mining.Mineables;
import com.seggellion.britannia_mod.mining.MiningProvenance;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * One explosion policy for both managed resource systems (milestone 1).
 *
 * <h2>The hole this closes</h2>
 * Extraction is a transaction: validate, record the restoration debt, empty the cell, hand over
 * the yield. An explosion is none of that. It does not fire {@code BlockEvent.BreakEvent}, so
 * neither the Mining gate nor {@code ManagedDepositInteractionHandler} ever saw it, and the block
 * was simply deleted — no debt, so the deposit never came back, and for the mod's own ore blocks
 * not even a drop, because they carry no loot table. A creeper could permanently remove a vein.
 *
 * <p>The answer is to take the cell out of the blast rather than to compensate afterwards, which
 * is how {@code FlowerInteractionHandler} and {@code ManagedVegetationInteractionHandler} already
 * protect their resources. Nothing is dropped, nothing is yielded, nothing is scheduled, and the
 * resource is still standing when the smoke clears — which is exactly the required policy.
 *
 * <h2>What is covered, and what deliberately is not</h2>
 * <ul>
 *   <li><b>Every {@link ManagedDeposits} bed</b> — clay and silica today, whatever is added later.
 *   <li><b>Every ACTIVE Mining resource of category ORE</b> — the metal ladder, which is the part
 *       with economic weight and the part that only exists where an operator placed it.
 *   <li><b>Not category STONE.</b> Stone, deepslate, granite, tuff and the rest are catalogued so
 *       that working them trains Mining, but they are also ordinary terrain by the million.
 *       Making all of it blast-proof would end TNT excavation and leave creeper craters with
 *       floating walls — a change to how the world behaves, far outside a containment milestone.
 *       Stone lost to an explosion is terrain lost, not economy lost.
 *   <li><b>Not player-placed blocks.</b> Somebody's own iron-ore wall is construction, and
 *       {@link MiningProvenance} already says so. The break gate makes the same exception, and an
 *       explosion must not be stricter than a pickaxe.
 * </ul>
 *
 * <p>The STONE boundary is a stated policy, not an oversight, and it is pinned by a GameTest so a
 * later change to it has to be deliberate.
 */
public class ManagedResourceExplosionHandler {

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> isProtectedFromExplosions(level, pos));
    }

    /**
     * Whether an explosion must leave this cell alone. Also the seam the GameTests drive.
     *
     * <p>The block question is asked first and the provenance question only of the few cells that
     * answered yes. This runs once per cell in a blast, and the overwhelming majority of them are
     * dirt and stone that no lookup should be spent on — whereas provenance costs a saved-data
     * fetch, which is worth paying only for a cell that would otherwise be protected.
     */
    public static boolean isProtectedFromExplosions(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        boolean managed = ManagedDeposits.resolve(state).isPresent()
                || Mineables.resolve(state)
                        .map(definition -> definition.category() == MineableDefinition.Category.ORE)
                        .orElse(false);
        return managed && !MiningProvenance.isPlayerPlaced(level, pos);
    }
}
