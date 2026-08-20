package com.seggellion.britannia_mod.structure;

import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.util.HouseUtil;
import com.seggellion.britannia_mod.util.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Whether a player may change a block, and why not when they may not.
 *
 * <h2>Permission, not game mode</h2>
 * The owner of a house builds in it while staying in whatever game mode they were already in.
 * Nothing here switches anybody to creative, hands out a creative inventory or grants flight;
 * the housing system says yes or no, and the ordinary survival mechanics -- drops, durability,
 * an item consumed from the stack -- happen exactly as they always do.
 *
 * <p>Adventure mode gates block editing on {@code Abilities.mayBuild} rather than on anything an
 * event listener can undo -- {@code CommonHooks.fireBlockBreak} says so in as many words, and
 * {@code ServerPlayerGameMode.destroyBlock} re-checks {@code blockActionRestricted} after the
 * event either way. So the exemption is that one flag, granted by
 * {@link SurvivalZoneHandler} to an owner standing in their own house, and this class is the
 * rule that decides what they may then do with it.
 *
 * <h2>Two kinds of foundation</h2>
 * The house a player bought has a permanent outline, and it is not theirs to remove. It also has
 * a floor, and cutting down through that floor is how a basement gets made. Both are called
 * foundation, and the registry names do not separate them -- the block that most looks like a
 * perimeter course, {@code brick_foundation_spruce}, is the six small houses' interior floor.
 *
 * <p>So the two block tags decide it, and the tags were built from what the blocks actually do
 * in the authored structures. Nothing here names a block id.
 */
public final class HouseBuildRights {

    private HouseBuildRights() {}

    /** Why a block change was permitted or refused. */
    public enum Decision {
        /** No registered house contains this block. The world's own rules apply, unchanged. */
        OUTSIDE_ANY_HOUSE(true, null),

        /** Inside a house this player owns, on a block they may change. */
        ALLOWED(true, null),

        /** Inside somebody else's house. */
        DENIED_NOT_OWNER(false, "This is not your house."),

        /** The permanent outline of the building, protected from its owner too. */
        DENIED_PERIMETER_FOUNDATION(false, "The foundations of a house cannot be removed."),

        /** The lot block, which is the house's ownership record. */
        DENIED_HOUSE_INFRASTRUCTURE(false, "That is part of the house itself.");

        private final boolean permitted;
        @Nullable private final String message;

        Decision(boolean permitted, @Nullable String message) {
            this.permitted = permitted;
            this.message = message;
        }

        public boolean permitted() {
            return permitted;
        }

        /** Null when there is nothing to explain, because nothing was refused. */
        @Nullable
        public String message() {
            return message;
        }

        /** True when this house granted the right, as opposed to simply not having an opinion. */
        public boolean grantedByHouse() {
            return this == ALLOWED;
        }
    }

    /**
     * May {@code player} break the block at {@code pos}?
     *
     * <p>Answers {@link Decision#OUTSIDE_ANY_HOUSE} rather than a yes when no house is involved,
     * so the caller can leave the world's own protection alone instead of overriding it.
     */
    public static Decision evaluateBreak(Level level, BlockPos pos, Player player) {
        StructureRecord house = HouseUtil.enclosingStructure(level, pos);
        if (house == null) return Decision.OUTSIDE_ANY_HOUSE;

        if (!owns(house, player.getUUID())) return Decision.DENIED_NOT_OWNER;

        BlockState state = level.getBlockState(pos);
        if (isHouseInfrastructure(state)) return Decision.DENIED_HOUSE_INFRASTRUCTURE;
        if (state.is(ModTags.Blocks.HOUSE_FOUNDATION)) return Decision.DENIED_PERIMETER_FOUNDATION;

        // Everything else inside a house the player owns, the interior floor slab included --
        // which is the point, because that is how they get down into a basement.
        return Decision.ALLOWED;
    }

    /**
     * May {@code player} place a block at {@code pos}?
     *
     * <p>Same ownership question. There is no perimeter rule on the way in: refusing to remove
     * the outline of a house does not mean refusing to build against it.
     */
    public static Decision evaluatePlace(Level level, BlockPos pos, Player player) {
        StructureRecord house = HouseUtil.enclosingStructure(level, pos);
        if (house == null) return Decision.OUTSIDE_ANY_HOUSE;

        return owns(house, player.getUUID()) ? Decision.ALLOWED : Decision.DENIED_NOT_OWNER;
    }

    /** Whether this player stands inside a house they own, which is what earns the exemption. */
    public static boolean ownsHouseAt(Level level, BlockPos pos, UUID playerUuid) {
        return level != null && ownsHouseAt(level.dimension(), pos, playerUuid);
    }

    /**
     * The same question by dimension. A house at these coordinates in another world is
     * another house, and its owner is not this one.
     */
    public static boolean ownsHouseAt(
            net.minecraft.resources.ResourceKey<Level> dimension, BlockPos pos, UUID playerUuid) {
        StructureRecord house = HouseUtil.enclosingStructure(dimension, pos);
        return house != null && owns(house, playerUuid);
    }

    private static boolean owns(StructureRecord house, UUID playerUuid) {
        return house.getOwnerUuid() != null && house.getOwnerUuid().equals(playerUuid);
    }

    /**
     * Blocks that carry the house's own identity, and are therefore never remodelling material.
     *
     * <p>The lot block is the ownership record: it holds the house UUID, the owner and the
     * privacy flag, and {@code HouseUtil.findLot} reads it to decide whether a door will look at
     * a key. Breaking it does not release the house -- the region stays registered -- it just
     * leaves the house unable to answer questions about itself. Re-deeding is the way out of a
     * house, and it goes through {@code HouseActionHandler}.
     *
     * <p>Kept as a check on the block type rather than a tag because there is exactly one of
     * them and it is not a material anybody decorates with.
     */
    private static boolean isHouseInfrastructure(BlockState state) {
        return state.is(BlockRegistry.HOUSE_LOT_BLOCK.get());
    }
}
