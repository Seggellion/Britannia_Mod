package com.seggellion.britannia_mod.starfarer;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.block.DisplayCaseBlock;
import com.seggellion.britannia_mod.block.entity.DisplayCaseBlockEntity;
import com.seggellion.britannia_mod.blessed.BlessedItemLifecycleMetadata;
import com.seggellion.britannia_mod.client.renderer.StarfarersMedallionLayer;
import com.seggellion.britannia_mod.item.StarfarersMedallionItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.GameData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import javax.imageio.ImageIO;

/**
 * Starfarer M5 and Phase 2 equipment contract. The medallion resolves under its
 * canonical id and can be worn by its stamped owner without granting stats.
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

    private static final String PRODUCTION_TEXTURE = "britannia_mod:item/starfarers_medallion";

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

    // --- wearable, but no gameplay advantage --------------------------------

    @Test
    void itIsAVanillaChestWearableButNotArmor() {
        assertTrue(medallion instanceof Equipable);
        assertEquals(EquipmentSlot.CHEST, ((Equipable) medallion).getEquipmentSlot());
        assertEquals(SoundEvents.ARMOR_EQUIP_GENERIC, ((Equipable) medallion).getEquipSound());
        assertFalse(medallion instanceof ArmorItem);
    }

    @Test
    void theSlotGateRequiresTheStampedOwnerAndTheChestSlot() {
        UUID owner = UUID.randomUUID();
        ItemStack stack = new ItemStack(medallion);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("blessed", true);
        tag.putString("owner", owner.toString());
        tag.putString("deed_id", "deed-for-wearing");
        tag.putString("instance_uuid", UUID.randomUUID().toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        assertEquals(owner, BlessedItemLifecycleMetadata.ownerOf(stack).orElseThrow());
        assertFalse(((StarfarersMedallionItem) medallion).canEquip(stack, EquipmentSlot.HEAD, null));
        assertFalse(((StarfarersMedallionItem) medallion).canEquip(stack, EquipmentSlot.CHEST, null),
                "an item with no player cannot bypass owner validation");
        tag.putString("owner", "not-a-uuid");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        assertTrue(BlessedItemLifecycleMetadata.ownerOf(stack).isEmpty());
    }

    @Test
    void theCommonItemHasNoClientRendererDependency() throws IOException {
        String source = Files.readString(SOURCES.resolve("item/StarfarersMedallionItem.java"),
                StandardCharsets.UTF_8);
        assertFalse(source.contains("net.minecraft.client."));
        assertFalse(source.contains("IClientItemExtensions"));
        assertFalse(source.contains("initializeClient"));
        assertFalse(source.contains("StarfarersMedallionLayer"));
    }

    @Test
    void theWornLayerFailsClosedForAbsentCorruptAndForeignEquipment() {
        UUID owner = UUID.randomUUID();
        ItemStack stack = new ItemStack(medallion);
        assertFalse(StarfarersMedallionLayer.shouldRender(stack, owner));
        assertFalse(StarfarersMedallionLayer.shouldRender(new ItemStack(Items.GOLD_INGOT), owner));

        CompoundTag tag = new CompoundTag();
        tag.putBoolean("blessed", true);
        tag.putString("owner", owner.toString());
        tag.putString("deed_id", "worn-layer-test");
        tag.putString("instance_uuid", UUID.randomUUID().toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        assertTrue(StarfarersMedallionLayer.shouldRender(stack, owner));
        assertFalse(StarfarersMedallionLayer.shouldRender(stack, UUID.randomUUID()));

        tag.putString("instance_uuid", "corrupt-instance");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        assertFalse(StarfarersMedallionLayer.shouldRender(stack, owner));
    }

    @Test
    void theVanillaWornLayerIsClientOnlyAndRegisteredForBothSkins() throws IOException {
        String setup = Files.readString(SOURCES.resolve("ClientModSetup.java"), StandardCharsets.UTF_8);
        String layer = Files.readString(SOURCES.resolve("client/renderer/StarfarersMedallionLayer.java"),
                StandardCharsets.UTF_8);

        assertTrue(setup.contains("value = Dist.CLIENT"));
        assertTrue(setup.contains("EntityRenderersEvent.AddLayers"));
        assertTrue(setup.contains("PlayerSkin.Model.WIDE"));
        assertTrue(setup.contains("PlayerSkin.Model.SLIM"));
        assertTrue(setup.contains("renderer.addLayer(new StarfarersMedallionLayer("));
        assertTrue(layer.contains("@OnlyIn(Dist.CLIENT)"));
        assertTrue(layer.contains("getItemBySlot(EquipmentSlot.CHEST)"));
        assertTrue(layer.contains("getParentModel().body.translateAndRotate(poseStack)"));
        assertTrue(layer.contains("ItemDisplayContext.FIXED"));
        assertTrue(layer.contains("poseStack.translate(0.0D, 0.22D, -0.18D)"),
                "the upright bail needs clearance below the player's head");
        int uprightRoll = layer.indexOf("poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F))");
        assertTrue(uprightRoll > layer.indexOf("getParentModel().body.translateAndRotate(poseStack)"));
        assertTrue(uprightRoll < layer.indexOf("itemRenderer.renderStatic("));
        assertFalse(layer.contains("geckolib"));
        assertFalse(layer.contains("GeoRender"));
    }

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
        // Equipping is instantaneous; it must not acquire a food/drink use animation.
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
    void theProductionItemModelUsesOneVanillaModelAndTextureContract() throws IOException {
        JsonObject model = readJson(ASSETS.resolve("models/item/starfarers_medallion.json"));

        assertEquals("minecraft:block/block", model.get("parent").getAsString());
        assertEquals(PRODUCTION_TEXTURE,
                model.getAsJsonObject("textures").get("medallion").getAsString());
        assertEquals(PRODUCTION_TEXTURE,
                model.getAsJsonObject("textures").get("particle").getAsString());
        assertFalse(model.toString().contains("minecraft:item/gold_ingot"));
        assertDoesNotThrow(() -> BlockModel.fromString(model.toString()),
                "the production JSON must parse through Minecraft's real model loader");
        JsonArray elements = model.getAsJsonArray("elements");
        assertTrue(elements.size() > 0 && elements.size() <= 12,
                "the ceremonial model stays within the vanilla element budget");
        assertEquals(Set.of("north", "south"),
                elements.get(0).getAsJsonObject().getAsJsonObject("faces").keySet(),
                "square side faces would show as detached bars around the round texture");
        for (var entry : elements) {
            JsonObject faces = entry.getAsJsonObject().getAsJsonObject("faces");
            assertFalse(faces.isEmpty());
            for (var face : faces.entrySet()) {
                assertEquals("#medallion", face.getValue().getAsJsonObject()
                        .get("texture").getAsString());
            }
        }
        for (String context : List.of("gui", "ground", "fixed",
                "firstperson_righthand", "firstperson_lefthand",
                "thirdperson_righthand", "thirdperson_lefthand")) {
            assertTrue(model.getAsJsonObject("display").has(context), context);
        }
    }

    @Test
    void theProductionTextureIsSmallTransparentAndNonempty() throws IOException {
        Path texture = ASSETS.resolve("textures/item/starfarers_medallion.png");
        assertTrue(Files.isRegularFile(texture), texture.toString());
        BufferedImage image = ImageIO.read(texture.toFile());
        assertNotNull(image, "the production PNG decodes");
        assertEquals(64, image.getWidth());
        assertEquals(64, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha());
        assertEquals(0, image.getRGB(0, 0) >>> 24, "transparent silhouette corners");
        assertTrue((image.getRGB(32, 32) >>> 24) > 0, "the medallion face is visible");
    }

    @Test
    void theEditableBlockbenchSourceMatchesTheRuntimeExport() throws IOException {
        JsonObject source = readJson(ASSETS.resolve("models/item/starfarers_medallion.bbmodel"));
        JsonObject model = readJson(ASSETS.resolve("models/item/starfarers_medallion.json"));
        assertEquals("java_block", source.getAsJsonObject("meta")
                .get("model_format").getAsString());
        assertEquals(64, source.getAsJsonObject("resolution").get("width").getAsInt());
        assertEquals(64, source.getAsJsonObject("resolution").get("height").getAsInt());
        assertEquals(model.getAsJsonObject("display"), source.getAsJsonObject("display"));

        JsonArray sourceElements = source.getAsJsonArray("elements");
        JsonArray modelElements = model.getAsJsonArray("elements");
        assertEquals(modelElements.size(), sourceElements.size());
        for (int i = 0; i < modelElements.size(); i++) {
            JsonObject editable = sourceElements.get(i).getAsJsonObject();
            JsonObject exported = modelElements.get(i).getAsJsonObject();
            assertEquals(exported.get("name"), editable.get("name"));
            assertEquals(exported.get("from"), editable.get("from"));
            assertEquals(exported.get("to"), editable.get("to"));
            JsonObject exportedFaces = exported.getAsJsonObject("faces");
            JsonObject editableFaces = editable.getAsJsonObject("faces");
            for (var face : exportedFaces.entrySet()) {
                JsonArray exportedUv = face.getValue().getAsJsonObject().getAsJsonArray("uv");
                JsonArray editableUv = editableFaces.getAsJsonObject(face.getKey()).getAsJsonArray("uv");
                for (int n = 0; n < 4; n++) {
                    assertEquals(exportedUv.get(n).getAsDouble(),
                            editableUv.get(n).getAsDouble() / 4.0, 0.00001,
                            "Blockbench pixel UV and vanilla 0–16 UV differ at " + i + "/" + face.getKey());
                }
            }
        }

        String embedded = source.getAsJsonArray("textures").get(0).getAsJsonObject()
                .get("source").getAsString();
        assertTrue(embedded.startsWith("data:image/png;base64,"));
        assertArrayEquals(Files.readAllBytes(ASSETS.resolve("textures/item/starfarers_medallion.png")),
                Base64.getDecoder().decode(embedded.substring("data:image/png;base64,".length())));
    }

    @Test
    void theNameAndLoreAreLocalized() throws IOException {
        JsonObject language = readJson(ASSETS.resolve("lang/en_us.json"));

        for (String key : List.of(
                "item.britannia_mod.starfarers_medallion",
                "item.britannia_mod.starfarers_medallion.lore",
                "item.britannia_mod.starfarers_medallion.provenance",
                "item.britannia_mod.starfarers_medallion.occasion")) {
            assertTrue(language.has(key), key);
            assertFalse(language.get(key).getAsString().isBlank(), key);
        }

        assertEquals("Starfarer’s Medallion",
                language.get("item.britannia_mod.starfarers_medallion").getAsString());
        assertEquals("Bestowed upon travelers who answered a call from beyond the skies of Britannia.",
                language.get("item.britannia_mod.starfarers_medallion.lore").getAsString());
        assertEquals("Struck for those who answered the call.",
                language.get("item.britannia_mod.starfarers_medallion.provenance").getAsString());
        assertEquals("Squadron 42 - Manchester, 2956",
                language.get("item.britannia_mod.starfarers_medallion.occasion").getAsString());
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
        int lore = source.indexOf("starfarers_medallion.lore");
        int provenance = source.indexOf("starfarers_medallion.provenance");
        int occasion = source.indexOf("starfarers_medallion.occasion");
        assertTrue(lore >= 0 && lore < provenance && provenance < occasion,
                "the restrained tooltip shows lore, provenance, then occasion");
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
