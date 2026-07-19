package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bank.item.BankItemCodec;
import com.seggellion.britannia_mod.bank.item.BankItemDecodeResult;
import com.seggellion.britannia_mod.bank.item.BankItemEquality;
import com.seggellion.britannia_mod.bank.item.BankItemFingerprint;
import com.seggellion.britannia_mod.bank.item.BankItemNesting;
import com.seggellion.britannia_mod.bank.item.BankItemSchemaVersion;
import com.seggellion.britannia_mod.component.WineData;
import com.seggellion.britannia_mod.item.GradeStoneItem;
import com.seggellion.britannia_mod.item.MaterialQualityJewelryItem;
import com.seggellion.britannia_mod.item.PurityOreItem;
import com.seggellion.britannia_mod.item.QualitySwordItem;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.item.WineBottleBlockItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.WeaponRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Milestone 8 Slice 1: {@link BankItemCodec}, {@link BankItemFingerprint}, and
 * {@link BankItemEquality} in isolation. This is pure serialization infrastructure -- no
 * Rails calls, no eligibility policy, no transfer protocol, and nothing here touches
 * {@code bank.open} or {@link com.seggellion.britannia_mod.entity.ServiceNpcEntity}.
 *
 * <p>A real running server is required (not a plain JUnit test) because several of these
 * fixtures are this mod's own {@code DeferredRegister}-registered items (the wine bottle,
 * the quality sword, jewelry, grade stone, purity ore) and vanilla's data-driven enchantment
 * registry -- none of these are populated outside an actual mod-loading lifecycle, which a
 * bare JUnit JVM never triggers. This follows the same convention already established by
 * {@link BankingProxyServiceGameTests} and {@link ServiceNpcEntityGameTests}.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class BankItemCodecGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private BankItemCodecGameTests() {
    }

    // ---------- Wine bottle: the milestone's explicitly named round trip ----------

    @GameTest(template = TEMPLATE)
    public static void wineBottleRoundTripPreservesEveryField(GameTestHelper helper) {
        ItemStack original = wineBottleStack(1997);
        ItemStack decoded = roundTrip(helper, original);

        WineData originalData = WineBottleBlockItem.getWineData(original);
        WineData decodedData = WineBottleBlockItem.getWineData(decoded);
        check(originalData.equals(decodedData),
                "wine data did not survive the round trip: " + originalData + " vs " + decodedData);

        assertSemanticallyEqualAndSameFingerprint(helper, original, decoded);
        helper.succeed();
    }

    // ---------- Every other real custom-data-bearing item type in this mod ----------
    // "crafter", "hue", and "insured" do not exist anywhere in this codebase (grepped, not
    // assumed) -- these are the real hand-rolled CUSTOM_DATA fields that do: Quality/
    // Material (QualitySwordItem, MaterialQualityJewelryItem), GradeValue/StoneType
    // (GradeStoneItem), Purity/OreType (PurityOreItem), and blessed/owner/deed_id (the exact
    // tag shape BlessedItemInventorySync writes). All of them share the single vanilla
    // CUSTOM_DATA component, so this also proves the codec handles that component generically
    // rather than needing per-item special-casing.

    @GameTest(template = TEMPLATE)
    public static void qualitySwordRoundTripPreservesQualityAndMaterial(GameTestHelper helper) {
        ItemStack original = qualitySwordStack(4, UOMetalToolMaterial.VALORITE);
        ItemStack decoded = roundTrip(helper, original);

        check(QualitySwordItem.getQuality(decoded) == 4, "Quality did not survive the round trip");
        check("valorite".equals(QualitySwordItem.getMaterial(decoded)), "Material did not survive the round trip");

        assertSemanticallyEqualAndSameFingerprint(helper, original, decoded);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void jewelryRoundTripPreservesQualityMaterialAndType(GameTestHelper helper) {
        ItemStack original = jewelryStack(MaterialQualityJewelryItem.QualityTier.EXCEPTIONAL, MaterialQualityJewelryItem.UOMaterial.GOLD);
        ItemStack decoded = roundTrip(helper, original);

        check(MaterialQualityJewelryItem.getQuality(decoded) == MaterialQualityJewelryItem.QualityTier.EXCEPTIONAL.level(),
                "jewelry quality did not survive the round trip");
        check(MaterialQualityJewelryItem.getMaterial(decoded) == MaterialQualityJewelryItem.UOMaterial.GOLD,
                "jewelry material did not survive the round trip");
        check(MaterialQualityJewelryItem.getJewelryType(decoded) == MaterialQualityJewelryItem.JewelryType.RING,
                "jewelry type did not survive the round trip");

        assertSemanticallyEqualAndSameFingerprint(helper, original, decoded);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void gradeStoneRoundTripPreservesGradeAndStoneType(GameTestHelper helper) {
        ItemStack original = gradeStoneStack(5, "marble");
        ItemStack decoded = roundTrip(helper, original);

        GradeStoneItem item = (GradeStoneItem) decoded.getItem();
        check(item.getGradeValue(decoded) == 5, "grade value did not survive the round trip");
        check("marble".equals(item.getStoneType(decoded)), "stone type did not survive the round trip");

        assertSemanticallyEqualAndSameFingerprint(helper, original, decoded);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void purityOreRoundTripPreservesPurityAndOreType(GameTestHelper helper) {
        ItemStack original = purityOreStack(5, "valorite ore");
        ItemStack decoded = roundTrip(helper, original);

        PurityOreItem item = (PurityOreItem) decoded.getItem();
        check(item.getPurity(decoded) == 5, "purity did not survive the round trip");
        check("valorite ore".equals(item.getOreType(decoded)), "ore type did not survive the round trip");

        assertSemanticallyEqualAndSameFingerprint(helper, original, decoded);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void blessedMarkerRoundTripPreservesTheBlessedTagShape(GameTestHelper helper) {
        String ownerUuid = UUID.randomUUID().toString();
        ItemStack original = blessedMarkerStack(ownerUuid, "deed-test-1");
        ItemStack decoded = roundTrip(helper, original);

        CompoundTag decodedTag = decoded.get(DataComponents.CUSTOM_DATA).copyTag();
        check(decodedTag.getBoolean("blessed"), "the blessed flag did not survive the round trip");
        check(ownerUuid.equals(decodedTag.getString("owner")), "the owner uuid did not survive the round trip");
        check("deed-test-1".equals(decodedTag.getString("deed_id")), "the deed id did not survive the round trip");

        assertSemanticallyEqualAndSameFingerprint(helper, original, decoded);
        helper.succeed();
    }

    // ---------- Vanilla-only stacks ----------

    @GameTest(template = TEMPLATE)
    public static void plainVanillaStackRoundTrips(GameTestHelper helper) {
        ItemStack original = plainVanillaStack(5);
        ItemStack decoded = roundTrip(helper, original);
        assertSemanticallyEqualAndSameFingerprint(helper, original, decoded);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void enchantedVanillaStackRoundTrips(GameTestHelper helper) {
        ItemStack original = enchantedVanillaStack(helper.getLevel(), 3);
        ItemStack decoded = roundTrip(helper, original);
        check(decoded.getEnchantments().getLevel(sharpness(helper.getLevel())) == 3,
                "enchantment level did not survive the round trip");
        assertSemanticallyEqualAndSameFingerprint(helper, original, decoded);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void namedAndLoredVanillaStackRoundTrips(GameTestHelper helper) {
        ItemStack original = namedAndLoredVanillaStack();
        ItemStack decoded = roundTrip(helper, original);
        check(decoded.getHoverName().getString().equals(original.getHoverName().getString()),
                "custom name did not survive the round trip");
        check(decoded.get(DataComponents.LORE).equals(original.get(DataComponents.LORE)),
                "lore did not survive the round trip");
        assertSemanticallyEqualAndSameFingerprint(helper, original, decoded);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void damagedVanillaStackRoundTrips(GameTestHelper helper) {
        ItemStack original = damagedVanillaStack(7);
        ItemStack decoded = roundTrip(helper, original);
        check(decoded.getDamageValue() == 7, "damage value did not survive the round trip");
        assertSemanticallyEqualAndSameFingerprint(helper, original, decoded);
        helper.succeed();
    }

    // ---------- Nested containers (ADR-009) ----------
    // ADR-009 (docs/ultimacraft_banking_service_npc_compatibility_map.md): nested containers
    // are bankable and "the canonical ItemStack codec must serialize container contents
    // recursively; it must not treat a nested container as an opaque blob." Vanilla's own
    // ItemContainerContents.CODEC (the real DataComponents.CONTAINER component a shulker box
    // uses -- confirmed by reading ItemContainerContents.java directly, not assumed) already
    // encodes each contained ItemStack through ItemStack.CODEC itself, and its equals() uses
    // ItemStack.listMatches (full per-stack component equality), not a shortcut. Since
    // BankItemCodec wraps ItemStack.CODEC generically rather than hand-selecting fields, this
    // recursion should already work with no special-casing. These tests prove that directly.

    @GameTest(template = TEMPLATE)
    public static void shulkerBoxRoundTripPreservesNestedItemsIncludingNestedCustomData(GameTestHelper helper) {
        ItemStack nestedSword = qualitySwordStack(4, UOMetalToolMaterial.VALORITE);
        ItemStack nestedDiamonds = new ItemStack(Items.DIAMOND, 3);
        ItemStack original = shulkerBoxStack(List.of(nestedSword, nestedDiamonds));

        ItemStack decoded = roundTrip(helper, original);

        List<ItemStack> decodedContents = decoded.get(DataComponents.CONTAINER).stream().toList();
        check(decodedContents.size() == 2, "nested item count did not survive the round trip: " + decodedContents.size());
        check(BankItemEquality.semanticEquals(decodedContents.get(0), nestedSword),
                "nested quality sword was not semantically equal after the round trip");
        check(QualitySwordItem.getQuality(decodedContents.get(0)) == 4,
                "nested quality sword's Quality custom data did not survive the round trip");
        check("valorite".equals(QualitySwordItem.getMaterial(decodedContents.get(0))),
                "nested quality sword's Material custom data did not survive the round trip");
        check(BankItemEquality.semanticEquals(decodedContents.get(1), nestedDiamonds),
                "nested plain diamond stack was not semantically equal after the round trip");

        assertSemanticallyEqualAndSameFingerprint(helper, original, decoded);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fingerprintDiffersWhenContainerContentsDifferButTheContainerItselfDoesNot(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();

        ItemStack emptyContentsSword = qualitySwordStack(4, UOMetalToolMaterial.VALORITE);
        ItemStack differentContentsSword = qualitySwordStack(3, UOMetalToolMaterial.VALORITE);
        ItemStack boxA = shulkerBoxStack(List.of(emptyContentsSword));
        ItemStack boxB = shulkerBoxStack(List.of(differentContentsSword));

        check(!BankItemFingerprint.fingerprint(boxA, registries).equals(BankItemFingerprint.fingerprint(boxB, registries)),
                "two shulker boxes with different nested contents (same outer item) fingerprinted identically -- "
                        + "the container was treated as an opaque blob, contradicting ADR-009");
        check(!BankItemEquality.semanticEquals(boxA, boxB),
                "two shulker boxes with different nested contents were considered semantically equal");

        ItemStack boxAAgain = shulkerBoxStack(List.of(qualitySwordStack(4, UOMetalToolMaterial.VALORITE)));
        check(BankItemFingerprint.fingerprint(boxA, registries).equals(BankItemFingerprint.fingerprint(boxAAgain, registries)),
                "two independently constructed shulker boxes with gameplay-identical contents fingerprinted differently");

        helper.succeed();
    }

    // ---------- Fingerprint stability ----------

    @GameTest(template = TEMPLATE)
    public static void fingerprintIsStableForIndependentlyConstructedIdenticalWineBottles(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack a = wineBottleStack(1997);
        ItemStack b = wineBottleStack(1997);

        check(BankItemFingerprint.fingerprint(a, registries).equals(BankItemFingerprint.fingerprint(b, registries)),
                "two independently constructed, gameplay-identical wine bottles fingerprinted differently");
        check(BankItemEquality.semanticEquals(a, b),
                "two gameplay-identical wine bottles were not semantically equal");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fingerprintIsStableRegardlessOfCustomDataSetterCallOrder(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();

        ItemStack a = new ItemStack(ItemRegistry.COPPER_RING.get());
        MaterialQualityJewelryItem.setMaterial(a, MaterialQualityJewelryItem.UOMaterial.GOLD);
        MaterialQualityJewelryItem.setQuality(a, MaterialQualityJewelryItem.QualityTier.EXCEPTIONAL);
        MaterialQualityJewelryItem.setJewelryType(a, MaterialQualityJewelryItem.JewelryType.RING);

        ItemStack b = new ItemStack(ItemRegistry.COPPER_RING.get());
        MaterialQualityJewelryItem.setJewelryType(b, MaterialQualityJewelryItem.JewelryType.RING);
        MaterialQualityJewelryItem.setQuality(b, MaterialQualityJewelryItem.QualityTier.EXCEPTIONAL);
        MaterialQualityJewelryItem.setMaterial(b, MaterialQualityJewelryItem.UOMaterial.GOLD);

        check(BankItemFingerprint.fingerprint(a, registries).equals(BankItemFingerprint.fingerprint(b, registries)),
                "two gameplay-identical jewelry items fingerprinted differently under a different setter call order");
        check(BankItemEquality.semanticEquals(a, b),
                "two gameplay-identical jewelry items (built in a different setter order) were not semantically equal");
        helper.succeed();
    }

    // ---------- Fingerprint sensitivity: several concrete, minimal, real differences ----------

    @GameTest(template = TEMPLATE)
    public static void fingerprintDiffersForADifferentEnchantmentLevel(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack levelTwo = enchantedVanillaStack(helper.getLevel(), 2);
        ItemStack levelThree = enchantedVanillaStack(helper.getLevel(), 3);

        check(!BankItemFingerprint.fingerprint(levelTwo, registries).equals(BankItemFingerprint.fingerprint(levelThree, registries)),
                "different enchantment levels produced the same fingerprint");
        check(!BankItemEquality.semanticEquals(levelTwo, levelThree),
                "different enchantment levels were considered semantically equal");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fingerprintDiffersForADifferentCustomDataField(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack qualityFour = qualitySwordStack(4, UOMetalToolMaterial.VALORITE);
        ItemStack qualityThree = qualitySwordStack(3, UOMetalToolMaterial.VALORITE);

        check(!BankItemFingerprint.fingerprint(qualityFour, registries).equals(BankItemFingerprint.fingerprint(qualityThree, registries)),
                "different Quality custom-data values produced the same fingerprint");
        check(!BankItemEquality.semanticEquals(qualityFour, qualityThree),
                "different Quality custom-data values were considered semantically equal");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fingerprintDiffersForADifferentMaterialCustomDataValue(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack ironSword = qualitySwordStack(4, UOMetalToolMaterial.IRON);
        ItemStack valoriteSword = qualitySwordStack(4, UOMetalToolMaterial.VALORITE);

        check(!BankItemFingerprint.fingerprint(ironSword, registries).equals(BankItemFingerprint.fingerprint(valoriteSword, registries)),
                "different Material custom-data values produced the same fingerprint");
        check(!BankItemEquality.semanticEquals(ironSword, valoriteSword),
                "different Material custom-data values were considered semantically equal");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fingerprintDiffersForADifferentCount(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack five = new ItemStack(Items.DIAMOND, 5);
        ItemStack six = new ItemStack(Items.DIAMOND, 6);

        check(!BankItemFingerprint.fingerprint(five, registries).equals(BankItemFingerprint.fingerprint(six, registries)),
                "different stack counts produced the same fingerprint");
        check(!BankItemEquality.semanticEquals(five, six),
                "different stack counts were considered semantically equal");
        helper.succeed();
    }

    // ---------- Semantic equality agrees with fingerprint comparison, proven directly ----------

    @GameTest(template = TEMPLATE)
    public static void semanticEqualityAgreesWithFingerprintComparisonAcrossRepresentativeCases(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ServerLevel level = helper.getLevel();

        record Case(String label, ItemStack a, ItemStack b) {
        }

        List<Case> cases = List.of(
                new Case("identical wine bottles", wineBottleStack(1997), wineBottleStack(1997)),
                new Case("different wine vintage", wineBottleStack(1997), wineBottleStack(1998)),
                new Case("identical plain stacks", plainVanillaStack(5), plainVanillaStack(5)),
                new Case("different counts", new ItemStack(Items.DIAMOND, 5), new ItemStack(Items.DIAMOND, 6)),
                new Case("same enchantment level", enchantedVanillaStack(level, 3), enchantedVanillaStack(level, 3)),
                new Case("different enchantment level", enchantedVanillaStack(level, 2), enchantedVanillaStack(level, 3)),
                new Case("identical jewelry", jewelryStack(MaterialQualityJewelryItem.QualityTier.FINE, MaterialQualityJewelryItem.UOMaterial.SILVER),
                        jewelryStack(MaterialQualityJewelryItem.QualityTier.FINE, MaterialQualityJewelryItem.UOMaterial.SILVER)),
                new Case("different jewelry quality", jewelryStack(MaterialQualityJewelryItem.QualityTier.FINE, MaterialQualityJewelryItem.UOMaterial.SILVER),
                        jewelryStack(MaterialQualityJewelryItem.QualityTier.CRUDE, MaterialQualityJewelryItem.UOMaterial.SILVER))
        );

        for (Case testCase : cases) {
            boolean semanticEqual = BankItemEquality.semanticEquals(testCase.a(), testCase.b());
            boolean fingerprintEqual = BankItemFingerprint.fingerprint(testCase.a(), registries)
                    .equals(BankItemFingerprint.fingerprint(testCase.b(), registries));
            check(semanticEqual == fingerprintEqual,
                    "semantic equality and fingerprint comparison disagreed for case \"" + testCase.label()
                            + "\": semanticEquals=" + semanticEqual + " fingerprintEqual=" + fingerprintEqual);
        }

        helper.succeed();
    }

    // ---------- Unsupported schema version and corrupt payloads are rejected cleanly ----------

    @GameTest(template = TEMPLATE)
    public static void unsupportedSchemaVersionIsRejectedCleanly(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        byte[] validPayload = BankItemCodec.serialize(plainVanillaStack(1), registries);

        // Re-encode the real serialized payload with a schema_version this codec cannot
        // support, rather than hand-building a synthetic one -- this proves the real decode
        // path rejects it, not a fabricated shortcut.
        byte[] futureVersionPayload = withSchemaVersion(validPayload, 999);

        BankItemDecodeResult result = BankItemCodec.deserialize(futureVersionPayload, registries);
        check(result instanceof BankItemDecodeResult.UnsupportedSchemaVersion unsupported && unsupported.foundVersion() == 999,
                "an unsupported schema version was not reported as UnsupportedSchemaVersion(999): " + result);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void corruptOrTruncatedPayloadIsRejectedCleanly(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        byte[] validPayload = BankItemCodec.serialize(wineBottleStack(1997), registries);

        byte[] truncated = Arrays.copyOf(validPayload, validPayload.length / 2);
        BankItemDecodeResult truncatedResult = BankItemCodec.deserialize(truncated, registries);
        check(truncatedResult instanceof BankItemDecodeResult.Corrupt,
                "a truncated payload was not reported as Corrupt: " + truncatedResult);

        byte[] garbage = {1, 2, 3, 4, 5, 6, 7, 8};
        BankItemDecodeResult garbageResult = BankItemCodec.deserialize(garbage, registries);
        check(garbageResult instanceof BankItemDecodeResult.Corrupt,
                "a garbage payload was not reported as Corrupt: " + garbageResult);

        BankItemDecodeResult emptyResult = BankItemCodec.deserialize(new byte[0], registries);
        check(emptyResult instanceof BankItemDecodeResult.Corrupt,
                "an empty payload was not reported as Corrupt: " + emptyResult);

        BankItemDecodeResult nullResult = BankItemCodec.deserialize(null, registries);
        check(nullResult instanceof BankItemDecodeResult.Corrupt,
                "a null payload was not reported as Corrupt: " + nullResult);

        helper.succeed();
    }

    // ---------- Shared nesting-depth guard (BankItemNesting.MAX_DEPTH) ----------
    // serialize()/fingerprint() throw (caller-error convention, matching their own existing
    // empty-stack throw): the input is always an in-memory ItemStack a real caller already
    // holds, not untrusted bytes. deserialize() returns its existing typed Corrupt result
    // instead (external-input convention, matching its own existing corrupt/truncated-payload
    // handling): the payload is untrusted, so a rejection must be a value the caller branches
    // on, not a thrown exception.

    @GameTest(template = TEMPLATE)
    public static void serializeSucceedsWhenNestedExactlyAtMaxDepth(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack atLimit = nestedShulkerBoxChain(BankItemNesting.MAX_DEPTH);

        byte[] payload = BankItemCodec.serialize(atLimit, registries);
        BankItemDecodeResult result = BankItemCodec.deserialize(payload, registries);
        check(result instanceof BankItemDecodeResult.Success,
                "a stack nested exactly at MAX_DEPTH did not round trip successfully: " + result);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void serializeThrowsWhenNestedBeyondMaxDepth(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack tooDeep = nestedShulkerBoxChain(BankItemNesting.MAX_DEPTH + 1);

        boolean threw = false;
        try {
            BankItemCodec.serialize(tooDeep, registries);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        check(threw, "serialize() did not throw IllegalArgumentException for a stack nested one level beyond MAX_DEPTH");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void deserializeSucceedsWhenPayloadNestedExactlyAtMaxDepth(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack atLimit = nestedShulkerBoxChain(BankItemNesting.MAX_DEPTH);

        byte[] payload = buildPayloadBypassingSerializeGuard(atLimit, registries);
        BankItemDecodeResult result = BankItemCodec.deserialize(payload, registries);
        check(result instanceof BankItemDecodeResult.Success,
                "a payload nested exactly at MAX_DEPTH was not decoded successfully: " + result);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void deserializeReturnsCorruptWhenPayloadNestedBeyondMaxDepth(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack tooDeep = nestedShulkerBoxChain(BankItemNesting.MAX_DEPTH + 1);

        // Built by hand, bypassing serialize()'s own throwing guard, to simulate an
        // adversarial payload arriving over the wire/storage -- exactly the case the raw-tag
        // pre-check inside deserialize() (checked before ItemStack.CODEC.parse ever runs)
        // exists for.
        byte[] payload = buildPayloadBypassingSerializeGuard(tooDeep, registries);
        BankItemDecodeResult result = BankItemCodec.deserialize(payload, registries);
        check(result instanceof BankItemDecodeResult.Corrupt,
                "deserialize() did not report Corrupt for a payload nested one level beyond MAX_DEPTH: " + result);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fingerprintSucceedsWhenNestedExactlyAtMaxDepth(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack atLimit = nestedShulkerBoxChain(BankItemNesting.MAX_DEPTH);

        String fingerprint = BankItemFingerprint.fingerprint(atLimit, registries);
        check(fingerprint != null && !fingerprint.isEmpty(),
                "fingerprint() did not produce a real value for a stack nested exactly at MAX_DEPTH");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fingerprintThrowsWhenNestedBeyondMaxDepth(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack tooDeep = nestedShulkerBoxChain(BankItemNesting.MAX_DEPTH + 1);

        boolean threw = false;
        try {
            BankItemFingerprint.fingerprint(tooDeep, registries);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        check(threw, "fingerprint() did not throw IllegalArgumentException for a stack nested one level beyond MAX_DEPTH");
        helper.succeed();
    }

    // ---------- Payload size measurement ----------

    @GameTest(template = TEMPLATE)
    public static void measurePayloadSizeReflectsTheActualSerializedLength(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        byte[] payload = BankItemCodec.serialize(wineBottleStack(1997), registries);
        check(BankItemCodec.measurePayloadSize(payload) == payload.length,
                "measurePayloadSize did not match the actual payload length");
        check(payload.length > 0, "serialized payload was unexpectedly empty");
        helper.succeed();
    }

    // ---------- serialize()'s empty/null-stack precondition ----------
    // Deliberately a thrown IllegalArgumentException, not a typed BankItemDecodeResult-style
    // result, unlike every failure mode in deserialize(). The two are not the same kind of
    // failure: deserialize()'s input is a byte[] payload that can legitimately arrive
    // corrupted or from an incompatible schema version -- an external, expected condition
    // every real caller (deposit/withdraw validation, startup reconciliation) must branch on
    // regardless of how careful this mod's own code is. serialize()'s input is always an
    // ItemStack the caller already holds a live reference to (a player inventory slot this
    // mod's own code selected); an empty or null stack reaching this method can only mean the
    // caller's own logic is wrong -- e.g. it forgot to check isEmpty() before calling, or
    // invoked this before the (still-unbuilt) eligibility policy would have rejected the
    // attempt. That is exactly the class of failure this codebase already reserves thrown
    // exceptions for elsewhere (see the two IllegalStateExceptions later in this same method
    // for the "vanilla codec itself misbehaved" case), not a typed result a caller is expected
    // to recover from in the normal course of operation.

    @GameTest(template = TEMPLATE)
    public static void serializingAnEmptyStackThrowsIllegalArgumentException(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        boolean threw = false;
        try {
            BankItemCodec.serialize(ItemStack.EMPTY, registries);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        check(threw, "serialize() did not throw IllegalArgumentException for an empty ItemStack");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void serializingANullStackThrowsIllegalArgumentException(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        boolean threw = false;
        try {
            BankItemCodec.serialize(null, registries);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        check(threw, "serialize() did not throw IllegalArgumentException for a null ItemStack");
        helper.succeed();
    }

    // ---------- Fixture construction ----------

    private static ItemStack plainVanillaStack(int count) {
        return new ItemStack(Items.DIAMOND, count);
    }

    private static Holder<Enchantment> sharpness(ServerLevel level) {
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
    }

    private static ItemStack enchantedVanillaStack(ServerLevel level, int enchantmentLevel) {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(sharpness(level), enchantmentLevel);
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        return stack;
    }

    private static ItemStack namedAndLoredVanillaStack() {
        ItemStack stack = new ItemStack(Items.STICK);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Wand of Testing"));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("A humble stick."))));
        return stack;
    }

    private static ItemStack damagedVanillaStack(int damage) {
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        stack.set(DataComponents.DAMAGE, damage);
        return stack;
    }

    private static ItemStack wineBottleStack(int year) {
        ItemStack stack = new ItemStack(ItemRegistry.WINE_BOTTLE_GREEN.get());
        WineBottleBlockItem.setWineData(stack, "Britannia Vintners", "Merlot", year, 82, "Trinsic", "green");
        return stack;
    }

    private static ItemStack qualitySwordStack(int quality, UOMetalToolMaterial material) {
        ItemStack stack = new ItemStack(WeaponRegistry.VIKING_SWORD.get());
        QualitySwordItem.setQuality(stack, quality);
        QualitySwordItem.setMaterial(stack, material);
        return stack;
    }

    private static ItemStack shulkerBoxStack(List<ItemStack> contents) {
        ItemStack stack = new ItemStack(Items.SHULKER_BOX);
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        return stack;
    }

    /**
     * A chain of {@code depth} nested shulker boxes wrapping a plain diamond at the bottom:
     * {@code depth == 1} is just the diamond itself (no container), {@code depth == N} is
     * N - 1 shulker boxes deep. Built by direct component construction, not any normal
     * gameplay action, specifically to exercise the {@link BankItemNesting#MAX_DEPTH} guard.
     */
    private static ItemStack nestedShulkerBoxChain(int depth) {
        ItemStack current = new ItemStack(Items.DIAMOND);
        for (int level = 1; level < depth; level++) {
            ItemStack box = new ItemStack(Items.SHULKER_BOX);
            box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(current)));
            current = box;
        }
        return current;
    }

    /**
     * Replicates {@link BankItemCodec#serialize}'s own envelope format exactly, but without
     * its nesting-depth guard -- used only to construct an "already too deep" payload for
     * testing {@link BankItemCodec#deserialize}'s own independent depth check, since
     * {@code serialize} itself now refuses to produce one.
     */
    private static byte[] buildPayloadBypassingSerializeGuard(ItemStack stack, HolderLookup.Provider registries) {
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        Tag itemTag = ItemStack.CODEC.encodeStart(ops, stack)
                .getOrThrow(error -> new IllegalStateException("test setup failed to encode: " + error));

        CompoundTag outer = new CompoundTag();
        outer.putInt("schema_version", BankItemSchemaVersion.CURRENT);
        outer.put("item", itemTag);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            NbtIo.writeCompressed(outer, buffer);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return buffer.toByteArray();
    }

    private static ItemStack jewelryStack(MaterialQualityJewelryItem.QualityTier quality, MaterialQualityJewelryItem.UOMaterial material) {
        ItemStack stack = new ItemStack(ItemRegistry.COPPER_RING.get());
        MaterialQualityJewelryItem.setQuality(stack, quality);
        MaterialQualityJewelryItem.setMaterial(stack, material);
        MaterialQualityJewelryItem.setJewelryType(stack, MaterialQualityJewelryItem.JewelryType.RING);
        return stack;
    }

    private static ItemStack gradeStoneStack(int gradeValue, String stoneType) {
        ItemStack stack = new ItemStack(ItemRegistry.GRADE_STONE_ITEM.get());
        GradeStoneItem item = (GradeStoneItem) stack.getItem();
        item.setGradeValue(stack, gradeValue);
        item.setStoneType(stack, stoneType);
        return stack;
    }

    private static ItemStack purityOreStack(int purity, String oreType) {
        ItemStack stack = new ItemStack(ItemRegistry.PURITY_ORE_ITEM.get());
        PurityOreItem item = (PurityOreItem) stack.getItem();
        item.setPurity(stack, purity);
        item.setOreType(stack, oreType);
        return stack;
    }

    /**
     * Mirrors the exact tag shape {@code BlessedItemInventorySync} writes ("blessed",
     * "owner", "deed_id" on {@link DataComponents#CUSTOM_DATA}) without invoking that class,
     * since it performs inventory-scan side effects this test does not want.
     */
    private static ItemStack blessedMarkerStack(String ownerUuid, String deedId) {
        ItemStack stack = new ItemStack(Items.GOLDEN_APPLE);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("blessed", true);
        tag.putString("owner", ownerUuid);
        tag.putString("deed_id", deedId);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    // ---------- Round-trip / assertion helpers ----------

    private static ItemStack roundTrip(GameTestHelper helper, ItemStack original) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        byte[] payload = BankItemCodec.serialize(original, registries);
        BankItemDecodeResult result = BankItemCodec.deserialize(payload, registries);
        if (!(result instanceof BankItemDecodeResult.Success success)) {
            throw new IllegalStateException("round trip did not decode successfully: " + result);
        }
        return success.stack();
    }

    private static void assertSemanticallyEqualAndSameFingerprint(GameTestHelper helper, ItemStack original, ItemStack decoded) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        check(BankItemEquality.semanticEquals(original, decoded),
                "round-tripped stack was not semantically equal to the original: " + original + " vs " + decoded);
        check(BankItemFingerprint.fingerprint(original, registries).equals(BankItemFingerprint.fingerprint(decoded, registries)),
                "round-tripped stack did not produce the same fingerprint as the original");
    }

    /**
     * Rewrites the {@code schema_version} field of an already-serialized payload, exercising
     * the exact same compressed-NBT envelope {@link BankItemCodec} itself writes rather than
     * fabricating a shortcut payload.
     */
    private static byte[] withSchemaVersion(byte[] payload, int version) {
        CompoundTag outer;
        try {
            outer = NbtIo.readCompressed(new ByteArrayInputStream(payload), NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        outer.putInt("schema_version", version);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            NbtIo.writeCompressed(outer, buffer);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return buffer.toByteArray();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
