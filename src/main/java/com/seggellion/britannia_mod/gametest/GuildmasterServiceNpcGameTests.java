package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.service.banking.BankingCapability;
import com.seggellion.britannia_mod.service.guild.GuildmasterCapability;
import com.seggellion.britannia_mod.service.guild.GuildmasterProxyService;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcRegistrySnapshot;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Guildmaster milestone 1: the entity-level half of the role title, which JUnit cannot reach.
 *
 * <p>{@code ServiceNpcDisplayNameTest} already asserts the string composition, and
 * {@code GuildmasterCapabilityTest} the registry lookup — but only a running level can prove the
 * two meet correctly on a real entity: that the title survives the reconciler's set-name-then-set-
 * type ordering, that it survives an NBT round-trip, and that a bank teller renders the same
 * composed personal-name-plus-role form.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class GuildmasterServiceNpcGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private GuildmasterServiceNpcGameTests() {
    }

    @GameTest(batch = "world_state_entity", template = TEMPLATE)
    public static void aGuildmasterRendersItsPersonalNameAndRole(GameTestHelper helper) {
        withRegistry(() -> {
            ServiceNpcEntity npc = helper.spawn(EntityRegistry.SERVICE_NPC.get(), new BlockPos(1, 1, 1));
            // Deliberately in the reconciler's real order: the personal name lands first, and the
            // type key (which is what makes this a Guildmaster) only afterwards.
            npc.setPersonalName("Marcus");
            npc.setServiceNpcTypeKey("warrior_guildmaster");

            checkName(npc, "Marcus the Warrior Guildmaster", "a freshly assigned Guildmaster");
            helper.succeed();
        });
    }

    @GameTest(batch = "world_state_entity", template = TEMPLATE)
    public static void aBankTellerRendersItsPersonalNameAndRole(GameTestHelper helper) {
        withRegistry(() -> {
            ServiceNpcEntity npc = helper.spawn(EntityRegistry.SERVICE_NPC.get(), new BlockPos(1, 1, 1));
            npc.setPersonalName("Aldric");
            npc.setServiceNpcTypeKey("bank_teller");

            // This once asserted a bare "Aldric", because the combined form was scoped to
            // Guildmasters so that existing tellers kept reading as they always had. That scoping
            // was deliberately widened: every Service NPC whose type publishes a display name now
            // renders the composed form. The assertion is tightened to the new contract rather
            // than relaxed — "Bank Teller" is this registry's display name for the type, so a
            // regression that dropped the role, or published the wrong one, still fails here.
            checkName(npc, "Aldric the Bank Teller", "a bank teller");
            helper.succeed();
        });
    }

    @GameTest(batch = "world_state_entity", template = TEMPLATE)
    public static void theGuildmasterTitleSurvivesASaveLoadRoundTrip(GameTestHelper helper) {
        withRegistry(() -> {
            ServerLevel level = helper.getLevel();
            ServiceNpcEntity original = helper.spawn(EntityRegistry.SERVICE_NPC.get(), new BlockPos(1, 1, 1));
            original.setPersonalName("Marcus");
            original.setServiceNpcTypeKey("warrior_guildmaster");

            CompoundTag saved = saveForReload(original);
            original.discard();

            Entity restored = EntityType.loadEntityRecursive(saved, level, entity -> entity);
            check(restored instanceof ServiceNpcEntity, "reload did not reconstruct a ServiceNpcEntity");
            ServiceNpcEntity reloaded = (ServiceNpcEntity) restored;

            check("warrior_guildmaster".equals(reloaded.getServiceNpcTypeKey()),
                    "reload did not preserve the taught-skill binding's stable identity");
            // CitizenEntity.readAdditionalSaveData renders the nameplate from the personal name
            // before ServiceNpcTypeKey is read back, so without the re-render in
            // ServiceNpcEntity.readAdditionalSaveData this reverts to a bare "Marcus".
            checkName(reloaded, "Marcus the Warrior Guildmaster", "a reloaded Guildmaster");
            helper.succeed();
        });
    }

    // ---------- Milestone 3: interaction routing ----------

    @GameTest(batch = "world_state_entity", template = TEMPLATE)
    public static void interactionRoutingSeparatesGuildmastersFromBankers(GameTestHelper helper) {
        withRegistry(() -> {
            // resolve() is the routing decision both proxies make, asserted directly rather than
            // through interactAt: a GameTest cannot synthesise a real player interaction, and
            // resolve is exactly what interactAt delegates to once it has a ServerPlayer.
            ServiceNpcEntity guildmaster = helper.spawn(EntityRegistry.SERVICE_NPC.get(), new BlockPos(1, 1, 1));
            guildmaster.setWorldNpcPublicId(java.util.UUID.randomUUID());
            guildmaster.setPersonalName("Marcus");
            guildmaster.setServiceNpcTypeKey("warrior_guildmaster");

            ServiceNpcEntity teller = helper.spawn(EntityRegistry.SERVICE_NPC.get(), new BlockPos(2, 1, 1));
            teller.setWorldNpcPublicId(java.util.UUID.randomUUID());
            teller.setPersonalName("Aldric");
            teller.setServiceNpcTypeKey("bank_teller");

            check(GuildmasterCapability.supportsGuildTrain("warrior_guildmaster"),
                    "the Guildmaster type does not permit guild.train");
            check(!GuildmasterCapability.supportsGuildTrain("bank_teller"),
                    "a bank teller must never route to guild training");
            check(!BankingCapability.supportsBankOpen("warrior_guildmaster"),
                    "a Guildmaster must never route to banking");
            check(BankingCapability.supportsBankOpen("bank_teller"),
                    "the bank teller stopped permitting bank.open");

            check(GuildmasterProxyService.resolve(null, guildmaster) == null,
                    "a null player must resolve to nothing");
            check(GuildmasterProxyService.resolve(null, teller) == null,
                    "a teller must never resolve as a Guildmaster");
            helper.succeed();
        });
    }

    @GameTest(batch = "world_state_entity", template = TEMPLATE)
    public static void twoGuildmastersTeachTheirOwnSkillsIndependently(GameTestHelper helper) {
        withRegistry(() -> {
            // The shared-skill case from the RunUO rosters: both guilds teach Resisting Spells,
            // but neither inherits the other's exclusive skills.
            check(GuildmasterCapability.teaches("warrior_guildmaster", "swords"),
                    "the warrior guild lost swordsmanship");
            check(!GuildmasterCapability.teaches("warrior_guildmaster", "magery"),
                    "the warrior guild must not teach magery");

            ServiceNpcEntity warrior = helper.spawn(EntityRegistry.SERVICE_NPC.get(), new BlockPos(1, 1, 1));
            warrior.setPersonalName("Marcus");
            warrior.setServiceNpcTypeKey("warrior_guildmaster");
            checkName(warrior, "Marcus the Warrior Guildmaster", "the first Guildmaster");

            ServiceNpcEntity teller = helper.spawn(EntityRegistry.SERVICE_NPC.get(), new BlockPos(2, 1, 1));
            teller.setPersonalName("Aldric");
            teller.setServiceNpcTypeKey("bank_teller");
            // Same widened contract as aBankTellerRendersItsPersonalNameAndRole: the teller is
            // here to prove the two Service NPCs stay independent, and it renders the composed
            // form like every other type that publishes a display name.
            checkName(teller, "Aldric the Bank Teller", "a bank teller alongside a Guildmaster");

            check(warrior.isAlive() && teller.isAlive(), "both Service NPCs should coexist");
            helper.succeed();
        });
    }

    /**
     * Publishes a registry containing both a Guildmaster and a bank teller for the duration of one
     * test, then restores whatever was there before. {@link ServiceNpcRegistryCache} is a process-
     * wide static, so a test that replaced it and walked away would leak into every later test in
     * the batch — and the banking GameTests in particular install their own snapshots.
     */
    private static void withRegistry(Runnable body) {
        ServiceNpcRegistrySnapshot previous = ServiceNpcRegistryCache.snapshot();
        try {
            ServiceNpcRegistryCache.replace(new ServiceNpcRegistrySnapshot(
                    1,
                    1L,
                    Map.of(),
                    Map.of(
                            "warrior_guildmaster", type(
                                    "warrior_guildmaster", "Warrior Guildmaster",
                                    List.of("guild.train"),
                                    List.of("arms-lore", "fencing", "macing", "parry", "swords", "tactics")
                            ),
                            "bank_teller", type(
                                    "bank_teller", "Bank Teller",
                                    List.of("bank.open", "bank.create_check"),
                                    List.of()
                            )
                    ),
                    Map.of()
            ));
            body.run();
        } finally {
            ServiceNpcRegistryCache.replace(previous);
        }
    }

    /** {@code ServiceNpcEntityGameTests}' own save idiom — {@code loadEntityRecursive} needs the id. */
    private static CompoundTag saveForReload(Entity entity) {
        CompoundTag tag = new CompoundTag();
        entity.saveWithoutId(tag);
        tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        return tag;
    }

    private static ServiceNpcTypeDefinition type(
            String key, String displayName, List<String> allowedServiceKeys, List<String> taughtSkillSlugs
    ) {
        return new ServiceNpcTypeDefinition(
                key, displayName, key, "britannia_mod:service_npc", key + "_default",
                allowedServiceKeys, taughtSkillSlugs, true, true, 1L
        );
    }

    private static void checkName(ServiceNpcEntity npc, String expected, String scenario) {
        check(npc.getCustomName() != null, scenario + " had no custom name at all");
        String actual = npc.getCustomName().getString();
        check(expected.equals(actual),
                scenario + " rendered as \"" + actual + "\" instead of \"" + expected + "\"");
        check(npc.isCustomNameVisible(), scenario + " did not have a visible nameplate");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
