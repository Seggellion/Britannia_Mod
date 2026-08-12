package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.QuestGiverEntity;
import com.seggellion.britannia_mod.quest.ClientQuestEntry;
import com.seggellion.britannia_mod.quest.ServerQuestTable;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Milestone 7 (findings Q-08, Q-11): a quest giver's identity is not its display name, and
 * becoming an escort does not make it a different entity.
 *
 * <p>The Rails join key used to be everything after the first colon in {@code personalName}, so
 * renaming an NPC for presentation silently repointed it at another quest or none. And escort
 * activation copied the NPC's NBT, discarded it and spawned a replacement, handing the escort a
 * new UUID while an open dialogue, the spawner and the logs still referred to the old one.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class QuestGiverIdentityGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private QuestGiverIdentityGameTests() {
    }

    /** Every quest giver standing in the world today has only the legacy encoding. */
    @GameTest(template = TEMPLATE)
    public static void aLegacyGiverStillResolvesFromItsName(GameTestHelper helper) {
        QuestGiverEntity giver = spawn(helper);
        giver.setPersonalName("Mitexi:cast_into_the_fire_npc");

        check("cast_into_the_fire_npc".equals(giver.resolveQuestGiverApiId()),
            "a legacy giver must still resolve, got " + giver.resolveQuestGiverApiId());
        helper.succeed();
    }

    /** A plain giver's name IS its key -- the case that made renaming so dangerous. */
    @GameTest(template = TEMPLATE)
    public static void aLegacyGiverWithoutTheEncodingUsesItsWholeName(GameTestHelper helper) {
        QuestGiverEntity giver = spawn(helper);
        giver.setPersonalName("relic_npc");

        check("relic_npc".equals(giver.resolveQuestGiverApiId()),
            "expected the whole name as the key, got " + giver.resolveQuestGiverApiId());
        helper.succeed();
    }

    /** The point of the milestone: presentation can change and identity does not follow. */
    @GameTest(template = TEMPLATE)
    public static void theFieldSurvivesARename(GameTestHelper helper) {
        QuestGiverEntity giver = spawn(helper);
        giver.setQuestGiverApiId("cast_into_the_fire_npc");
        giver.setPersonalName("Mitexi the Wanderer");

        check("cast_into_the_fire_npc".equals(giver.resolveQuestGiverApiId()),
            "renaming an NPC must not change which quest it offers, got "
                + giver.resolveQuestGiverApiId());
        helper.succeed();
    }

    /** The field wins over a stale encoding left in the display name. */
    @GameTest(template = TEMPLATE)
    public static void theFieldWinsOverTheLegacyEncoding(GameTestHelper helper) {
        QuestGiverEntity giver = spawn(helper);
        giver.setPersonalName("Mitexi:stale_key_from_an_old_spawn");
        giver.setQuestGiverApiId("cast_into_the_fire_npc");

        check("cast_into_the_fire_npc".equals(giver.resolveQuestGiverApiId()),
            "the field must win, got " + giver.resolveQuestGiverApiId());
        helper.succeed();
    }

    /** Identity has to survive a reload, or it is no better than the name it replaced. */
    @GameTest(template = TEMPLATE)
    public static void theFieldSurvivesSaveAndLoad(GameTestHelper helper) {
        QuestGiverEntity giver = spawn(helper);
        giver.setQuestGiverApiId("cast_into_the_fire_npc");
        giver.setPersonalName("Mitexi");

        CompoundTag saved = new CompoundTag();
        giver.saveWithoutId(saved);

        QuestGiverEntity reloaded = EntityRegistry.QUEST_GIVER.get().create(helper.getLevel());
        if (reloaded == null) throw new GameTestAssertException("could not create the reloaded giver");
        reloaded.load(saved);

        check("cast_into_the_fire_npc".equals(reloaded.resolveQuestGiverApiId()),
            "the identity did not survive a reload, got " + reloaded.resolveQuestGiverApiId());
        helper.succeed();
    }

    /** Q-11: activation keeps the entity, so every id anyone is holding stays valid. */
    @GameTest(template = TEMPLATE)
    public static void escortActivationKeepsTheSameEntity(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        QuestGiverEntity giver = spawn(helper);
        giver.setPersonalName("Mitexi:escort_britain_to_jhelom");
        giver.setQuestGiverApiId("escort_britain_to_jhelom");
        giver.moveTo(player.getX() + 1.0D, player.getY(), player.getZ(), 0.0F, 0.0F);

        UUID beforeActivation = giver.getUUID();
        ServerQuestTable.replaceFromBootstrap(player, List.of(new ClientQuestEntry(
            "8801", "55", "escort_britain_to_jhelom", "Mitexi", "Escort", "", "", "accepted")));

        com.seggellion.britannia_mod.network.QuestPayloadHandler.activateEscort(
            player, 55L, "8801", beforeActivation);

        QuestGiverEntity escort = (QuestGiverEntity) helper.getLevel().getEntity(beforeActivation);
        check(escort != null, "the escort must still be the entity everyone already had an id for");
        check(escort.isAlive(), "the escort must not have been discarded and replaced");
        check(escort.getTags().contains("escort_active"), "the escort assignment was not applied");
        check(escort.getTags().contains("quest_state_id_8801"),
            "the escort must carry its quest state id");
        check("escort_britain_to_jhelom".equals(escort.resolveQuestGiverApiId()),
            "activation must not disturb the giver's identity");
        helper.succeed();
    }

    // --- helpers ---------------------------------------------------------------------------

    private static QuestGiverEntity spawn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuestGiverEntity giver = EntityRegistry.QUEST_GIVER.get().create(level);
        if (giver == null) throw new GameTestAssertException("quest giver could not be created");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        giver.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(giver);
        return giver;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
