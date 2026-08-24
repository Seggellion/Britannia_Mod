package com.seggellion.britannia_mod.gametest;

import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.BritanniaChestBlockEntity;
import com.seggellion.britannia_mod.block.entity.WineBottleBlockEntity;
import com.seggellion.britannia_mod.component.WineData;
import com.seggellion.britannia_mod.grabbyhands.GrabbyInstanceState;
import com.seggellion.britannia_mod.grabbyhands.GrabbyProvenanceAccess;
import com.seggellion.britannia_mod.item.WineBottleBlockItem;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;
import com.seggellion.britannia_mod.structure.SurvivalZoneHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * What comes back when an owner takes something out of their own house.
 *
 * <h2>Why this needed its own file</h2>
 *
 * <p>Owners could not break anything in their houses at all, so nothing had ever exercised what a
 * house removal produces. Once the client stopped refusing the swing, the ordinary break pipeline
 * decided — and for this mod's furniture its answer was wrong twice over. Most of these blocks have
 * no loot table, so a break returned nothing whatsoever; and where a drop did exist it was a fresh
 * item, so a wine bottle came back blank and a chest came back empty with its contents in a heap on
 * the floor.
 *
 * <p>{@code HouseObjectRemoval} routes those removals through the Grabby pickup transaction, which
 * already solves "out of the world with its whole state, exactly once". These tests hold the three
 * properties that matter and could each be broken independently: nothing is lost, nothing is
 * duplicated, and nothing is destroyed that should not be.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class HouseRemovalLifecycleGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private HouseRemovalLifecycleGameTests() {
    }

    /**
     * Breaking a bottle the owner placed returns the bottle, with its wine.
     *
     * <p>The bottle has no loot table, so before this the break returned nothing at all: an owner
     * tidying up their own house destroyed a vintage every time.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void ametadatabearingblockkeepsitsmetadata(GameTestHelper helper) {
        House house = House.around(helper);
        try {
            BlockPos relative = new BlockPos(2, 1, 2);
            BlockPos target = helper.absolutePos(relative);
            helper.setBlock(relative, BlockRegistry.WINE_BOTTLE_GREEN_BLOCK.get());

            WineBottleBlockEntity bottle =
                    (WineBottleBlockEntity) helper.getLevel().getBlockEntity(target);
            bottle.setWineData(new WineData("Britannia Vineyards", "Verdant", 271, 88, "Yew", "red"));
            markPlayerPlaced(helper, target, house.ownerUuid);

            ServerPlayer owner = house.owner(helper, "removal-wine");
            if (!breakBlock(helper, owner, target)) {
                throw new GameTestAssertException("the owner could not remove their own wine bottle");
            }

            ItemStack recovered = firstMatching(owner, ItemRegistry.WINE_BOTTLE_GREEN.get());
            if (recovered.isEmpty()) {
                throw new GameTestAssertException("the bottle did not come back as an item at all");
            }
            WineData wine = WineBottleBlockItem.getWineData(recovered);
            if (!"Britannia Vineyards".equals(wine.wineryName()) || wine.year() != 271 || wine.quality() != 88) {
                throw new GameTestAssertException("the recovered bottle lost its wine data: " + wine);
            }
        } finally {
            house.close();
        }
        helper.succeed();
    }

    /**
     * Breaking a filled chest preserves its contents, and produces them exactly once.
     *
     * <p>The contents travel inside the chest item rather than spilling, which is what makes the
     * "exactly once" claim structural: the transaction captures them into the item and then clears
     * the block entity, so the block's own {@code onRemove} has nothing left to drop.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void acontainerkeepsitscontentsexactlyonce(GameTestHelper helper) {
        House house = House.around(helper);
        try {
            BlockPos relative = new BlockPos(2, 1, 3);
            BlockPos target = helper.absolutePos(relative);
            helper.setBlock(relative, BlockRegistry.CHEST_WOODEN.get());

            BritanniaChestBlockEntity chest =
                    (BritanniaChestBlockEntity) helper.getLevel().getBlockEntity(target);
            chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
            chest.setItem(1, new ItemStack(Items.GOLD_INGOT, 3));
            chest.setChanged();
            markPlayerPlaced(helper, target, house.ownerUuid);

            ServerPlayer owner = house.owner(helper, "removal-chest");
            if (!breakBlock(helper, owner, target)) {
                throw new GameTestAssertException("the owner could not remove their own chest");
            }

            ItemStack recovered = firstMatching(owner, BlockRegistry.CHEST_WOODEN.get().asItem());
            if (recovered.isEmpty()) {
                throw new GameTestAssertException("the chest did not come back as an item");
            }
            if (!recovered.has(DataComponents.BLOCK_ENTITY_DATA)) {
                throw new GameTestAssertException(
                        "the chest came back empty; its contents were not carried into the item");
            }

            // Nothing on the floor. A second copy of the contents here would be the duplication
            // exploit this whole route exists to make impossible.
            List<ItemEntity> spilled = helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class, new AABB(target).inflate(3.0D));
            if (!spilled.isEmpty()) {
                throw new GameTestAssertException(
                        "removing a chest spilled " + spilled.size() + " stacks as well as filling "
                                + "the item; that is a duplication");
            }
        } finally {
            house.close();
        }
        helper.succeed();
    }

    /** A stranger takes nothing, whatever the block is worth. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void anonownertakesnothingoutofthehouse(GameTestHelper helper) {
        House house = House.around(helper);
        try {
            BlockPos relative = new BlockPos(3, 1, 2);
            BlockPos target = helper.absolutePos(relative);
            helper.setBlock(relative, BlockRegistry.WINE_BOTTLE_GREEN_BLOCK.get());
            markPlayerPlaced(helper, target, house.ownerUuid);

            ServerPlayer stranger = house.stranger(helper, "removal-stranger");
            if (breakBlock(helper, stranger, target)) {
                throw new GameTestAssertException("a stranger removed a bottle from somebody's house");
            }
            if (!helper.getLevel().getBlockState(target).is(BlockRegistry.WINE_BOTTLE_GREEN_BLOCK.get())) {
                throw new GameTestAssertException("the bottle is gone after a refused removal");
            }
            if (!firstMatching(stranger, ItemRegistry.WINE_BOTTLE_GREEN.get()).isEmpty()) {
                throw new GameTestAssertException("a refused removal still handed the stranger the item");
            }
        } finally {
            house.close();
        }
        helper.succeed();
    }

    /**
     * An ordinary building block still breaks the ordinary way.
     *
     * <p>The stateful route is for objects Grabby Hands owns. Walls and floors — what an owner
     * actually remodels with — must not be diverted through it, or every remodel would depend on
     * inventory room.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void anordinaryblockstillbreaksordinarily(GameTestHelper helper) {
        House house = House.around(helper);
        try {
            BlockPos relative = new BlockPos(4, 1, 2);
            BlockPos target = helper.absolutePos(relative);
            helper.setBlock(relative, Blocks.WHITE_WOOL);

            ServerPlayer owner = house.owner(helper, "removal-ordinary");
            if (!breakBlock(helper, owner, target)) {
                throw new GameTestAssertException("the owner could not break wool in their own house");
            }
        } finally {
            house.close();
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */

    /**
     * Swings at a block and answers whether it left the world.
     *
     * <p>Deliberately not {@code destroyBlock}'s own return value. A stateful removal cancels the
     * break event and takes the object itself, so {@code destroyBlock} reports {@code false} for
     * the very case that succeeded — the boolean means "the ordinary pipeline destroyed it", which
     * is not the question any of these tests is asking.
     */
    private static boolean breakBlock(GameTestHelper helper, ServerPlayer player, BlockPos target) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        player.gameMode.destroyBlock(target);
        return helper.getLevel().getBlockState(target).isAir();
    }

    /** Stamps player provenance, which is what separates a possession from house scenery. */
    private static void markPlayerPlaced(GameTestHelper helper, BlockPos target, UUID placer) {
        GrabbyProvenanceAccess.write(helper.getLevel().getBlockEntity(target),
                GrabbyInstanceState.playerPlaced(placer, helper.getLevel().getGameTime()));
    }

    private static ItemStack firstMatching(ServerPlayer player, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** A registered house around the test structure, and the players who do and do not own it. */
    private static final class House implements AutoCloseable {
        private final StructureRecord record;
        private final UUID ownerUuid;

        private House(StructureRecord record) {
            this.record = record;
            this.ownerUuid = record.getOwnerUuid();
            StructureRegionManager.registerStructure(record);
        }

        static House around(GameTestHelper helper) {
            BlockPos origin = helper.absolutePos(new BlockPos(0, 0, 0));
            AABB structureBox = new AABB(origin.getX(), origin.getY(), origin.getZ(),
                    origin.getX() + 8, origin.getY() + 6, origin.getZ() + 8);
            AABB fullBox = new AABB(structureBox.minX, structureBox.minY - 10.0D, structureBox.minZ,
                    structureBox.maxX, structureBox.maxY, structureBox.maxZ);
            return new House(new StructureRecord(UUID.randomUUID(), structureBox, fullBox,
                    UUID.randomUUID(), "small", "SMALL_BRICK", null, 0, helper.getLevel().dimension()));
        }

        ServerPlayer owner(GameTestHelper helper, String name) {
            return player(helper, name, ownerUuid);
        }

        ServerPlayer stranger(GameTestHelper helper, String name) {
            return player(helper, name, UUID.randomUUID());
        }

        private ServerPlayer player(GameTestHelper helper, String name, UUID uuid) {
            ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(uuid, name));
            player.getInventory().clearContent();
            Vec3 centre = record.getStructureBox().getCenter();
            player.setPos(centre.x, record.getStructureBox().minY + 1, centre.z);
            player.gameMode.changeGameModeForPlayer(GameType.ADVENTURE);
            SurvivalZoneHandler.applyTo(player);
            return player;
        }

        @Override
        public void close() {
            StructureRegionManager.unregisterStructure(record);
            SurvivalZoneHandler.forgetLentRights();
        }
    }
}
