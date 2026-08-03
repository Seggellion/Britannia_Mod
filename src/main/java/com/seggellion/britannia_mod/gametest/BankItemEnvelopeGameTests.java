package com.seggellion.britannia_mod.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bank.item.BankItemEnvelopeVersion;
import com.seggellion.britannia_mod.bank.item.BankItemIdentity;
import com.seggellion.britannia_mod.bank.item.BankItemSchemaVersion;
import com.seggellion.britannia_mod.service.banking.BankingDepositClient;
import com.seggellion.britannia_mod.service.banking.BankingDepositPrepareRequest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * Milestone 17: the deposit envelope, asserted key-by-key in both the version that carries item
 * identity and the version that does not.
 *
 * <p>Both matter, and the v1 one arguably matters more. Rails refuses an envelope carrying an
 * unknown key, so a build that emits identity against a Rails instance which has not deployed
 * Milestone 16 does not degrade gracefully -- it breaks every deposit on that shard. Third-party
 * operators upgrade on their own schedule, so v1 emission has to stay exactly, byte-for-byte, what
 * it always was, indefinitely.
 *
 * <p>These tests build the envelope directly rather than driving a whole deposit, because what is
 * under test is the JSON contract itself -- {@link BankingDepositProxyServiceGameTests} already
 * covers the flow that produces it.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankItemEnvelopeGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private static final Set<String> V1_KEYS = Set.of("schema_version", "payload", "fingerprint", "weight");

    private BankItemEnvelopeGameTests() {
    }

    // ---------- v1: unchanged, forever ----------

    @GameTest(template = TEMPLATE)
    public static void v1EnvelopeCarriesExactlyTheOriginalFourKeys(GameTestHelper helper) {
        JsonObject item = envelopeItem(BankItemEnvelopeVersion.V1_WITHOUT_IDENTITY, BankItemIdentity.EMPTY);

        check(item.keySet().equals(V1_KEYS),
                "a v1 envelope must carry exactly the original four keys, got " + item.keySet());
        check(item.get("schema_version").getAsInt() == 1, "v1 envelope must declare schema_version 1");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anEmptyIdentityNeverAddsKeysEvenAtVersionTwo(GameTestHelper helper) {
        // An item whose name and key could not be resolved must not produce nulls or blanks:
        // omitting a key is the only way to say "no value", because Rails rejects an empty
        // display_name and a zero count rather than reading either as an absence.
        JsonObject item = envelopeItem(BankItemEnvelopeVersion.V2_WITH_IDENTITY, BankItemIdentity.EMPTY);

        check(item.keySet().equals(V1_KEYS),
                "an empty identity must add no keys at all, got " + item.keySet());
        helper.succeed();
    }

    // ---------- v2: identity beside the payload ----------

    @GameTest(template = TEMPLATE)
    public static void v2EnvelopeCarriesAllThreeIdentityKeys(GameTestHelper helper) {
        JsonObject item = envelopeItem(BankItemEnvelopeVersion.V2_WITH_IDENTITY,
                new BankItemIdentity("Gilded Arrow", "minecraft:arrow", 64));

        check(item.get("schema_version").getAsInt() == 2, "v2 envelope must declare schema_version 2");
        check(item.get("display_name").getAsString().equals("Gilded Arrow"), "wrong display_name on the wire");
        check(item.get("item_key").getAsString().equals("minecraft:arrow"), "wrong item_key on the wire");
        check(item.get("count").getAsInt() == 64, "wrong count on the wire");
        check(item.keySet().size() == 7, "v2 envelope should carry exactly seven keys, got " + item.keySet());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void identityKeysAreIndependentlyOptionalOnTheWire(GameTestHelper helper) {
        JsonObject nameOnly = envelopeItem(BankItemEnvelopeVersion.V2_WITH_IDENTITY,
                new BankItemIdentity("Solitary Ledger", null, null));
        check(nameOnly.has("display_name") && !nameOnly.has("item_key") && !nameOnly.has("count"),
                "a name-only identity must send only display_name, got " + nameOnly.keySet());

        JsonObject countOnly = envelopeItem(BankItemEnvelopeVersion.V2_WITH_IDENTITY,
                new BankItemIdentity(null, null, 12));
        check(countOnly.has("count") && !countOnly.has("display_name") && !countOnly.has("item_key"),
                "a count-only identity must send only count, got " + countOnly.keySet());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void thePayloadIsUnchangedByIdentityAndStillCarriesVersionOneInside(GameTestHelper helper) {
        // The two versions are deliberately separate. If they were still one constant, a payload
        // written by this build would carry inner version 2 and an older client could not decode
        // it on withdrawal -- the item would be stuck in the vault permanently.
        check(BankItemSchemaVersion.CURRENT == 1,
                "the payload format did not change in Milestone 17 and must still be version 1");
        check(!BankItemSchemaVersion.isSupported(BankItemEnvelopeVersion.V2_WITH_IDENTITY),
                "envelope version 2 must not be mistaken for a supported payload format version");

        JsonObject withIdentity = envelopeItem(BankItemEnvelopeVersion.V2_WITH_IDENTITY,
                new BankItemIdentity("Gilded Arrow", "minecraft:arrow", 64));
        JsonObject without = envelopeItem(BankItemEnvelopeVersion.V1_WITHOUT_IDENTITY, BankItemIdentity.EMPTY);
        check(withIdentity.get("payload").getAsString().equals(without.get("payload").getAsString()),
                "identity must not change the payload bytes -- it travels beside them, never inside");
        helper.succeed();
    }

    // ---------- Resolution from a real ItemStack ----------

    @GameTest(template = TEMPLATE)
    public static void identityResolvesNameKeyAndCountFromTheStack(GameTestHelper helper) {
        BankItemIdentity identity = BankItemIdentity.resolve(new ItemStack(Items.ARROW, 64));

        check("minecraft:arrow".equals(identity.itemKey()), "wrong item_key resolved: " + identity.itemKey());
        check(identity.count() != null && identity.count() == 64, "wrong count resolved: " + identity.count());
        check(identity.displayName() != null && !identity.displayName().isBlank(),
                "an arrow should resolve to some display name");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aCustomNamedItemResolvesTheNameThePlayerSees(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Marlo's Blade"));

        BankItemIdentity identity = BankItemIdentity.resolve(stack);
        check("Marlo's Blade".equals(identity.displayName()),
                "the custom name should be what travels, got " + identity.displayName());
        check("minecraft:diamond_sword".equals(identity.itemKey()), "wrong item_key: " + identity.itemKey());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anOverLongNameIsTruncatedHereRatherThanRejectedByRails(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("n".repeat(400)));

        BankItemIdentity identity = BankItemIdentity.resolve(stack);
        check(identity.displayName() != null, "an over-long name must be truncated, never dropped");
        check(identity.displayName().length() == BankItemIdentity.MAX_DISPLAY_NAME_LENGTH,
                "expected truncation to " + BankItemIdentity.MAX_DISPLAY_NAME_LENGTH
                        + ", got " + identity.displayName().length());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void truncationCountsCharactersAndNeverSplitsASurrogatePair(GameTestHelper helper) {
        // Rails counts Unicode characters and requires valid UTF-8. Cutting at a UTF-16 boundary
        // could split a surrogate pair, producing bytes Rails refuses outright -- turning a
        // cosmetic overflow into a failed deposit.
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("𝕬".repeat(400)));

        BankItemIdentity identity = BankItemIdentity.resolve(stack);
        String name = identity.displayName();
        check(name != null, "a multi-byte name must survive truncation");
        check(name.codePointCount(0, name.length()) <= BankItemIdentity.MAX_DISPLAY_NAME_LENGTH,
                "truncation must bound characters, not UTF-16 units");
        check(new String(name.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8).equals(name),
                "truncation split a surrogate pair and produced invalid UTF-8");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void controlCharactersAreStrippedRatherThanCostingTheWholeName(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Cursed\nBlade"));

        BankItemIdentity identity = BankItemIdentity.resolve(stack);
        check("CursedBlade".equals(identity.displayName()),
                "control characters should be removed, leaving the rest: " + identity.displayName());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aNameThatIsNothingButControlCharactersResolvesToNoNameAtAll(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("\n"));

        BankItemIdentity identity = BankItemIdentity.resolve(stack);
        check(identity.displayName() == null,
                "an empty result must be no name at all -- Rails rejects an empty display_name");
        check(identity.itemKey() != null, "the other fields are independent and must survive");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void anEmptyStackResolvesToAnEmptyIdentityRatherThanThrowing(GameTestHelper helper) {
        check(BankItemIdentity.resolve(ItemStack.EMPTY).isEmpty(), "an empty stack must resolve to EMPTY");
        check(BankItemIdentity.resolve(null).isEmpty(), "a null stack must resolve to EMPTY, not throw");
        helper.succeed();
    }

    // ---------- Emission default ----------

    @GameTest(template = TEMPLATE)
    public static void identityEmissionIsOffUnlessDeliberatelyTurnedOn(GameTestHelper helper) {
        // Rails-side acceptance ships first, always. A build that has not been told the target
        // Rails instance has Milestone 16 must keep emitting v1.
        boolean explicitlyEnabled = "2".equals(System.getProperty(BankItemEnvelopeVersion.SYSTEM_PROPERTY));
        check(BankItemEnvelopeVersion.emitsIdentity() == explicitlyEnabled,
                "identity emission must follow " + BankItemEnvelopeVersion.SYSTEM_PROPERTY + " and default to off");
        check(BankItemEnvelopeVersion.emitted() == (explicitlyEnabled ? 2 : 1),
                "emitted envelope version disagrees with the configured one");
        helper.succeed();
    }

    // ---------- Helpers ----------

    private static JsonObject envelopeItem(int envelopeVersion, BankItemIdentity identity) {
        BankingDepositPrepareRequest request = new BankingDepositPrepareRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID().toString(),
                envelopeVersion, new byte[]{1, 2, 3, 4}, "a".repeat(64), 1.5, identity
        );
        String json = new String(BankingDepositClient.serializePrepareForTesting(request), StandardCharsets.UTF_8);
        return JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("item");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
