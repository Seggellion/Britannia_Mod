package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

/**
 * A resource deposit whose extraction the economy controls.
 *
 * <h2>Why a bespoke block and not the vanilla material</h2>
 * A deposit's identity is its block. {@code minecraft:clay} generates by the thousand in river
 * beds and lake margins, and if the extraction rule keyed on that block then world generation
 * would decide how much clay the cities can buy. A block only this mod places is a location
 * authority by construction: a deposit exists where an administrator put one, nowhere else, and
 * no amount of vanilla terrain changes that.
 *
 * <p>This is the same arrangement the ores already use — {@code britannia_mod:silver_ore} is
 * placed by {@code /populateores} from Rails' vein table and never generates — with the one
 * difference that matters here: what may work a deposit is looked up per deposit rather than
 * assumed to be the pickaxe. See {@code ManagedDeposits}.
 *
 * <p>No block item and no loot table, again like the ores. The block cannot be picked up, and
 * breaking it by any route other than the authorized one drops nothing, so the deposit is not a
 * way to launder material into the economy.
 */
public class ManagedDepositBlock extends Block {

    public ManagedDepositBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /**
     * A deposit stays where it was put (milestone 1).
     *
     * <p>A deposit's identity <em>is</em> its coordinates: the extraction rule, the restoration
     * record and the operator's {@code /manageddeposit inspect} all key on the position. A piston
     * could previously shove a bed one block sideways, at which point the restoration record
     * pointed at empty ground and the bed itself stood somewhere nothing knew about. Refusing the
     * move is the fix; trying to repair provenance afterwards is not.
     *
     * <p>Overridden here rather than set per registration so a deposit added later cannot be
     * declared without it. {@code FlowerBlock} and {@code ServiceNpcSpawnBlock} take the same
     * position for the same reason.
     */
    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }
}
