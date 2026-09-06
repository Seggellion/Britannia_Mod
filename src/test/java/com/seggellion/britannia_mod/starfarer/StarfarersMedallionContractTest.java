package com.seggellion.britannia_mod.starfarer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.block.DisplayCaseBlock;
import com.seggellion.britannia_mod.block.entity.DisplayCaseBlockEntity;
import com.seggellion.britannia_mod.item.StarfarersMedallionItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.GameData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starfarer M5. The medallion exists, resolves under its canonical id, and does
 * nothing.
 *
 * <p>"Does nothing" is the substance of this test, not a throwaway line. The item is
 * desirable because of what it records, so any capability it accidentally gained would
 * change what the reward IS.
 */
class StarfarersMedallionContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path SOURCES = PROJECT.resolve("src/main/java/com/seggellion/britannia_mod");
    private static final Path RESOURCES = PROJECT.resolve("src/main/resources");
    private static final Path ASSETS = RESOURCES.resolve("assets/britannia_mod");
    private static final Path DATA = RESOURCES.resolve("data/britannia_mod");

    /**
     * The texture the model points at TODAY. It is a vanilla stand-in, not approved art.
     *
     * <p>This constant is the reason a placeholder cannot ship silently: when real
     * medallion art lands it must be added at
     * {@code assets/britannia_mod/textures/item/starfarers_medallion.png}, the model
     * repointed to {@code britannia_mod:item/starfarers_medallion}, and this line
     * changed by hand. Nobody can swap the art without noticing this test, and nobody
     * can call the placeholder finished without editing a constant that says it is not.
     */
    private static final String PLACEHOLDER_TEXTURE = "minecraft:item/gold_ingot";

    /**
     * A real registered instance of the production class, built with the production
     * Properties. The DeferredHolder itself is never bound in the plain-JUnit harness --
     * nothing runs the mod event bus here -- so the item under test is registered
     * directly, the way MilestoneTwoRegisteredTestContent does. `getId()` on the holder
     * still works unbound, so the canonical-id assertion uses the real registration.
     */
    private static Item medallion;

    @BeforeAll
    static void bootstrapVanillaRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();
        medallion = Registry.register(
                BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath("britannia_mod", "m5_test_starfarers_medallion"),
                new StarfarersMedallionItem(new Item.Properties()));
    }

    @Test
    void theResourceIdIsCanonicalAndUnique() {
        assertEquals("britannia_mod:starfarers_medallion",
                ItemRegistry.STARFARERS_MEDALLION.getId().toString(),
                "Rails stores this exact string as the entitlement's product_title");

        assertEquals(1, ItemRegistry.ITEMS.getEntries().stream()
                        .filter(holder -> holder.getId().getPath().equals("starfarers_medallion"))
                        .count(),
                "exactly one registration, so a medallion is never ambiguous");
    }

    // --- it does nothing ------------------------------------------------------

    @Test
    void itGrantsNoCombatGatheringOrMovementAdvantage() {
        ItemStack stack = new ItemStack(medallion);

        // Every 1.21.1 item carries an ATTRIBUTE_MODIFIERS component; a plain one's is
        // simply empty. So presence proves nothing and emptiness is the real assertion.
        assertTrue(stack.getAttributeModifiers().modifiers().isEmpty(),
                "an attribute modifier would make the medallion a stat item");
        assertFalse(stack.has(DataComponents.TOOL), "the medallion is not a tool");
    }

    @Test
    void itIsNotFoodAndCannotBeConsumed() {
        ItemStack stack = new ItemStack(medallion);

        assertFalse(stack.has(DataComponents.FOOD));
        // 1.21.1 has no CONSUMABLE component; an item with no use animation is one
        // nothing happens when you hold right-click on.
        assertEquals(UseAnim.NONE, stack.getUseAnimation());
    }

    @Test
    void itHasNoDurabilityToSpendOrRepair() {
        ItemStack stack = new ItemStack(medallion);

        assertFalse(stack.isDamageableItem());
        assertEquals(0, stack.getMaxDamage());
    }

    /**
     * Single-stack is a safety property, not flavour. Two copies of one medallion carry
     * identical custom data, so a stackable item would silently MERGE them -- which is
     * exactly how the offhand duplication characterized in M1 hides itself, growing a
     * stack from one to two with nothing visible on screen. A duplicate must occupy its
     * own slot where it can be seen.
     */
    @Test
    void itNeverStacksSoADuplicateCannotHideInsideAnExistingStack() {
        assertEquals(1, new ItemStack(medallion).getMaxStackSize());
        assertFalse(new ItemStack(medallion).isStackable());
    }

    /** Rarity colours a name. It confers nothing. */
    @Test
    void rarityIsPresentationOnly() {
        ItemStack stack = new ItemStack(medallion);

        assertEquals(Rarity.EPIC, stack.getRarity());
        assertTrue(stack.getAttributeModifiers().modifiers().isEmpty());
    }

    /**
     * Rescue behaviour belongs to the later lifecycle milestone, where it can be applied
     * to every blessed item and tested. If a future change makes the medallion
     * fire-resistant on its own, that is a rescue decision being smuggled in as a
     * property of one item, and this assertion should be the thing that objects.
     */
    @Test
    void itIsNotQuietlyFireResistantAheadOfTheRescueMilestone() {
        ItemStack stack = new ItemStack(medallion);

        assertFalse(stack.has(DataComponents.FIRE_RESISTANT));
    }

    @Test
    void nothingCraftsSmeltsOrOtherwiseProducesIt() throws IOException {
        Path recipes = DATA.resolve("recipe");
        if (!Files.isDirectory(recipes)) {
            recipes = DATA.resolve("recipes");
        }
        if (!Files.isDirectory(recipes)) {
            return; // no recipe tree at all; nothing can reference it
        }

        try (Stream<Path> tree = Files.walk(recipes)) {
            List<Path> mentions = tree
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .filter(path -> readOrEmpty(path).contains("starfarers_medallion"))
                    .toList();

            assertTrue(mentions.isEmpty(),
                    "the medallion is granted, never produced -- found: " + mentions);
        }
    }

    // --- assets ---------------------------------------------------------------

    @Test
    void theItemModelResolvesAndItsArtIsStillAnAdmittedPlaceholder() throws IOException {
        JsonObject model = readJson(ASSETS.resolve("models/item/starfarers_medallion.json"));

        assertTrue(model.get("parent").getAsString().endsWith("item/generated"));
        assertEquals(PLACEHOLDER_TEXTURE,
                model.getAsJsonObject("textures").get("layer0").getAsString(),
                "the model still points at a vanilla stand-in. When real art lands, add "
                        + "textures/item/starfarers_medallion.png, repoint the model, and update "
                        + "PLACEHOLDER_TEXTURE -- deliberately by hand, so approved artwork is "
                        + "never something that happened by accident.");

        assertFalse(Files.exists(ASSETS.resolve("textures/item/starfarers_medallion.png")),
                "a real texture exists but the model still points at the placeholder");
    }

    @Test
    void theNameAndLoreAreLocalized() throws IOException {
        JsonObject language = readJson(ASSETS.resolve("lang/en_us.json"));

        for (String key : List.of(
                "item.britannia_mod.starfarers_medallion",
                "item.britannia_mod.starfarers_medallion.lore",
                "item.britannia_mod.starfarers_medallion.occasion")) {
            assertTrue(language.has(key), key);
            assertFalse(language.get(key).getAsString().isBlank(), key);
        }

        assertEquals("Starfarer's Medallion",
                language.get("item.britannia_mod.starfarers_medallion").getAsString());
    }

    /**
     * The tooltip is the item's whole substance, so it is rendered unconditionally
     * rather than behind the advanced-tooltip key.
     */
    @Test
    void theTooltipIsNotHiddenBehindAdvancedMode() throws IOException {
        String source = Files.readString(SOURCES.resolve("item/StarfarersMedallionItem.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("appendHoverText"), "the item renders lore");
        assertFalse(source.contains("flag.isAdvanced()"),
                "lore must show to every player, not only with advanced tooltips");
        assertTrue(source.contains("starfarers_medallion.lore"));
        assertTrue(source.contains("starfarers_medallion.occasion"));
    }

    @Test
    void itIsReachableFromACreativeTab() throws IOException {
        String source = Files.readString(
                SOURCES.resolve("registry/CreativeTabRegistry.java"), StandardCharsets.UTF_8);

        assertTrue(source.contains("ItemRegistry.STARFARERS_MEDALLION"),
                "an operator must be able to obtain one without a Rails round trip");
    }

    /**
     * The instance above proves the CLASS behaves; this proves production actually wires
     * that class, with the same plain Properties, rather than a bare Item that would
     * silently lose the lore and the single-stack guarantee.
     */
    @Test
    void productionRegistersTheMedallionsOwnClass() throws IOException {
        String source = Files.readString(
                SOURCES.resolve("registry/ItemRegistry.java"), StandardCharsets.UTF_8);

        assertTrue(source.contains("ITEMS.register(\"starfarers_medallion\""));
        assertTrue(source.contains("new StarfarersMedallionItem(new Item.Properties())"));
        assertNotNull(medallion);
    }

    // --- display case ---------------------------------------------------------

    /**
     * Playbook section 14 makes display-case compatibility part of core acceptance: a
     * medallion is meant to be displayed rather than carried, and the display case must
     * take it through the EXISTING generic support, with no per-item special case.
     *
     * <p>M1 already proved the case preserves arbitrary component data. What this adds is
     * that the medallion specifically goes in, survives a disk round trip, and comes back
     * out as itself -- including the blessed identity Rails stamped on it, which is what a
     * later sync has to recognise.
     */
    @Test
    void itCanBeDisplayedAndRetrievedThroughTheGenericDisplayCase() {
        DisplayCaseBlock block = new DisplayCaseBlock(BlockBehaviour.Properties.of());
        DisplayCaseBlockEntity source = displayCase(block);

        ItemStack medallionStack = new ItemStack(medallion);
        CompoundTag blessed = new CompoundTag();
        blessed.putBoolean("blessed", true);
        blessed.putString("owner", "3f2504e0-4f89-11d3-9a0c-0305e82c3301");
        blessed.putString("deed_id", "entitlement-uuid");
        blessed.putString("instance_uuid", "per-shard-uuid");
        medallionStack.set(DataComponents.CUSTOM_DATA, CustomData.of(blessed));

        assertTrue(source.storeOne(medallionStack), "the generic case accepts it");

        DisplayCaseBlockEntity diskCopy = displayCase(block);
        diskCopy.loadWithComponents(
                source.saveWithoutMetadata(RegistryAccess.EMPTY), RegistryAccess.EMPTY);

        ItemStack retrieved = diskCopy.takeDisplayedItem();
        assertEquals(medallion, retrieved.getItem());
        CustomData data = retrieved.get(DataComponents.CUSTOM_DATA);
        assertNotNull(data, "the blessed identity survived the display cycle");
        assertEquals("entitlement-uuid", data.copyTag().getString("deed_id"));
        assertEquals("per-shard-uuid", data.copyTag().getString("instance_uuid"));
    }

    /** No per-item branch anywhere in the display case for this item. */
    @Test
    void theDisplayCaseHasNoSpecialCaseForTheMedallion() throws IOException {
        for (String file : List.of("block/DisplayCaseBlock.java",
                                   "block/entity/DisplayCaseBlockEntity.java")) {
            String source = Files.readString(SOURCES.resolve(file), StandardCharsets.UTF_8);
            assertFalse(source.toLowerCase().contains("medallion"),
                    file + " must not know the medallion exists");
            assertFalse(source.toLowerCase().contains("starfarer"), file);
        }
    }

    private static DisplayCaseBlockEntity displayCase(DisplayCaseBlock block) {
        @SuppressWarnings("unchecked")
        BlockEntityType<DisplayCaseBlockEntity>[] holder =
                (BlockEntityType<DisplayCaseBlockEntity>[]) new BlockEntityType<?>[1];
        holder[0] = BlockEntityType.Builder.of(
                (pos, state) -> new DisplayCaseBlockEntity(holder[0], pos, state), block).build(null);
        return new DisplayCaseBlockEntity(holder[0], BlockPos.ZERO, block.defaultBlockState());
    }

    private static JsonObject readJson(Path path) throws IOException {
        assertTrue(Files.exists(path), "missing resource " + path);
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static String readOrEmpty(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            return "";
        }
    }
}
