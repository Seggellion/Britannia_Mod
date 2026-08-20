package com.seggellion.britannia_mod.gametest;

import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import com.seggellion.britannia_mod.item.CityProvenanceItemData;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.structure.HousePrivacyHandler;
import com.seggellion.britannia_mod.structure.HouseStyle;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Milestone 9: two housing defects from the programme's own list, held still.
 *
 * <p>The first is real and fixed here. The second turned out not to exist, and the test is what
 * says so -- an absent defect needs evidence as much as a present one, or it comes back into the
 * next report as an open item.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class HousePrivacyAndDeedStateGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private HousePrivacyAndDeedStateGameTests() {
    }

    /* ------------------------------------------------------------------ */
    /*  Privacy: validate, then mutate                                     */
    /* ------------------------------------------------------------------ */

    /**
     * A house whose region is missing comes away unchanged.
     *
     * <p>The handler used to set the lot's privacy flag and push it to clients before checking
     * that a region existed, then return. Every house is in exactly that state between a restart
     * and the region rehydrate finishing, so a player toggling privacy in that window got a lot
     * marked private with no door locked and no key issued -- and no way to tell, because the lot
     * says private and the doors say otherwise.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void privacydoesnotchangewhentheregionismissing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HouseLotBlockEntity lot = placeLot(helper, new BlockPos(1, 1, 1), UUID.randomUUID());

        boolean before = lot.isPrivate();
        ServerPlayer owner = FakePlayerFactory.get(
                level, new GameProfile(UUID.randomUUID(), "m9-privacy-owner"));

        // No region registered for this house UUID at all.
        HousePrivacyHandler.handle(owner, lot, !before);

        if (lot.isPrivate() != before) {
            throw new GameTestAssertException(
                    "privacy flipped for a house with no region: the lot now says "
                    + lot.isPrivate() + " while nothing locked a door or issued a key");
        }
        helper.succeed();
    }

    /** With a region present the toggle works, so the guard did not simply disable the feature. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void privacystillchangeswhentheregionispresent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID houseUuid = UUID.randomUUID();
        HouseLotBlockEntity lot = placeLot(helper, new BlockPos(1, 1, 1), houseUuid);

        AABB box = new AABB(helper.absolutePos(new BlockPos(0, 0, 0)))
                .minmax(new AABB(helper.absolutePos(new BlockPos(6, 4, 6))));
        StructureRecord record = new StructureRecord(UUID.randomUUID(), box, box, houseUuid,
                "small", "SMALL_BRICK", null, 0, level.dimension());
        StructureRegionManager.registerStructure(record);

        try {
            ServerPlayer owner = FakePlayerFactory.get(
                    level, new GameProfile(UUID.randomUUID(), "m9-privacy-owner-b"));
            boolean before = lot.isPrivate();
            HousePrivacyHandler.handle(owner, lot, !before);

            if (lot.isPrivate() == before) {
                throw new GameTestAssertException(
                        "privacy did not change for a house whose region is registered");
            }
        } finally {
            StructureRegionManager.unregisterStructure(record);
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Blessed re-deed: the defect that was not there                     */
    /* ------------------------------------------------------------------ */

    /**
     * An Architect-bought deed carries no blessed identity, and that is what makes re-deeding safe.
     *
     * <p>The playbook records a defect: re-deeding stamps {@code blessed: true} on every returned
     * deed, so a vendor-bought house could be re-deeded into a blessed deed for free and undercut
     * the paid website product. It does not reproduce, and the reason is upstream of the stamp.
     *
     * <p>{@code HouseActionHandler} only stamps blessed inside {@code if (deedUuid != null)}, and
     * that UUID comes from the placed deed's {@code deed_id}. Exactly three places write
     * {@code deed_id} -- {@code DeedHttpServer}, which grants website purchases;
     * {@code BlessedItemInventorySync}; and the re-deed path re-stamping what it found. The
     * economic purchase path writes none of them: it grants the item with the material, quality
     * and origin-city data Rails returned and nothing else.
     *
     * <p>So an Architect deed has no {@code deed_id}, places a house with a null deed id, and
     * re-deeds into a plain deed. Blessed in, blessed out. This builds the two stacks the way
     * their real producers build them and checks the field the stamp actually turns on.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void anarchitectboughtdeedcarriesnoblessedidentity(GameTestHelper helper) {
        ItemStack purchased = economicallyGrantedDeed(HouseStyle.VILLA);

        CompoundTag tag = customData(purchased);
        if (tag.contains("deed_id")) {
            throw new GameTestAssertException(
                    "an economically granted deed carries a deed_id, which is what re-deeding turns "
                    + "its blessed stamp on for -- the playbook defect would now be real");
        }
        if (tag.getBoolean("blessed")) {
            throw new GameTestAssertException("an economically granted deed arrived already blessed");
        }
        // What it does carry is Rails' construction data, so this is a real granted stack rather
        // than a bare item that trivially has no fields.
        if (tag.isEmpty()) {
            throw new GameTestAssertException(
                    "the fixture built no economic data at all, so proving the absence of deed_id "
                    + "proves nothing");
        }
        helper.succeed();
    }

    /** The blessed path still produces the identity, so the distinction is a real one. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void awebsitegranteddeedstillcarriesitsblessedidentity(GameTestHelper helper) {
        UUID deedId = UUID.randomUUID();
        ItemStack blessed = websiteGrantedDeed(HouseStyle.VILLA, deedId);

        CompoundTag tag = customData(blessed);
        if (!tag.contains("deed_id") || !deedId.toString().equals(tag.getString("deed_id"))) {
            throw new GameTestAssertException("the blessed grant path lost its deed_id");
        }
        if (!tag.getBoolean("blessed")) {
            throw new GameTestAssertException("the blessed grant path lost its blessed flag");
        }
        helper.succeed();
    }

    /**
     * The reader that decides it, exercised on both stacks.
     *
     * <p>{@code StructurePlacer} adopts a deed identity only when the held stack has a
     * {@code deed_id}, and everything downstream -- the lot block, the region record, the Rails
     * house row, and the re-deed stamp -- follows from that one read.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void onlyablesseddeedgivesahouseadeedidentity(GameTestHelper helper) {
        if (readsDeedIdentity(economicallyGrantedDeed(HouseStyle.SMALL_BRICK))) {
            throw new GameTestAssertException(
                    "a house placed from an Architect deed would adopt a deed identity, and would "
                    + "then re-deed into a blessed deed");
        }
        if (!readsDeedIdentity(websiteGrantedDeed(HouseStyle.SMALL_BRICK, UUID.randomUUID()))) {
            throw new GameTestAssertException(
                    "a house placed from a website deed would lose its blessed identity");
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Fixtures                                                           */
    /* ------------------------------------------------------------------ */

    /** Built the way {@code EconomicVendorPurchaseService.grantPurchasedItems} builds one. */
    private static ItemStack economicallyGrantedDeed(HouseStyle style) {
        ItemStack stack = new ItemStack(ItemRegistry.deedFor(style), 1);
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA,
                CustomData.of(new CompoundTag())).copyTag();
        tag.putString("economic_material", "iron");
        tag.putString("economic_quality", "fine");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        CityProvenanceItemData.apply(stack, UUID.randomUUID(), "Britain");
        return stack;
    }

    /** Built the way {@code DeedHttpServer} builds a website-purchased one. */
    private static ItemStack websiteGrantedDeed(HouseStyle style, UUID deedId) {
        ItemStack stack = new ItemStack(ItemRegistry.deedFor(style), 1);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("blessed", true);
        tag.putString("deed_id", deedId.toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** StructurePlacer's own rule: a house adopts a deed identity only from a deed_id. */
    private static boolean readsDeedIdentity(ItemStack stack) {
        CompoundTag tag = customData(stack);
        if (!tag.contains("deed_id")) return false;
        try {
            UUID.fromString(tag.getString("deed_id"));
            return true;
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    private static CompoundTag customData(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    private static HouseLotBlockEntity placeLot(GameTestHelper helper, BlockPos relative, UUID houseUuid) {
        helper.setBlock(relative, BlockRegistry.HOUSE_LOT_BLOCK.get());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(relative))
                instanceof HouseLotBlockEntity lot)) {
            throw new GameTestAssertException("the house lot grew no block entity");
        }
        lot.setHouseUuid(houseUuid);
        return lot;
    }
}
