package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.crafting.BannerCraftingEnvironment;
import com.seggellion.britannia_mod.banner.crafting.BannerCraftingRecipe;
import com.seggellion.britannia_mod.banner.crafting.BannerCraftingRecipeSerializer;
import com.seggellion.britannia_mod.banner.crafting.BannerCraftingTags;
import com.seggellion.britannia_mod.banner.crafting.BannerFabricCraftingResolver;
import com.seggellion.britannia_mod.banner.crafting.BannerMountCraftingResolver;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderDataSnapshot;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshotTestFactory;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone14RegisteredTestContent;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone7RegisteredTestContent;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BannerCraftingRecipeTest {
    private static final BannerDefinitionId SMALL =
            BannerDefinitionId.parse("britannia_mod:silver_and_gold_pennon");
    private static RegistrySnapshot production;
    private static RegistryAccess registryAccess;
    private static BannerItem banner;
    private static Item cotton;
    private static Item wool;
    private static Item linen;
    private static Item silk;
    private static Item brass;
    private static Item iron;
    private static Item ambiguous;
    private static Item unknown;
    private static Item remainderFabric;
    private static Item remainderMount;
    private static Map<Item, Set<TagKey<Item>>> tags;
    private static BannerCraftingEnvironment environment;

    @BeforeAll
    static void setup() throws Exception {
        Milestone14RegisteredTestContent.ensureRegistered();
        production = DyeResolverFixtures.productionSnapshot();
        registryAccess = new RegistryAccess.ImmutableRegistryAccess(BuiltInRegistries.REGISTRY.stream().toList());
        banner = Milestone7RegisteredTestContent.banner();
        cotton = new Item(new Item.Properties());
        wool = new Item(new Item.Properties());
        linen = new Item(new Item.Properties());
        silk = new Item(new Item.Properties());
        brass = new Item(new Item.Properties());
        iron = new Item(new Item.Properties());
        ambiguous = new Item(new Item.Properties());
        unknown = new Item(new Item.Properties());
        remainderFabric = new Item(new Item.Properties().craftRemainder(Items.BUCKET));
        remainderMount = new Item(new Item.Properties().craftRemainder(Items.GLASS_BOTTLE));
        IdentityHashMap<Item, Set<TagKey<Item>>> configured = new IdentityHashMap<>();
        configured.put(cotton, Set.of(BannerCraftingTags.COTTON));
        configured.put(wool, Set.of(BannerCraftingTags.WOOL));
        configured.put(linen, Set.of(BannerCraftingTags.LINEN));
        configured.put(silk, Set.of(BannerCraftingTags.SILK));
        configured.put(brass, Set.of(BannerCraftingTags.BRASS));
        configured.put(iron, Set.of(BannerCraftingTags.IRON));
        configured.put(ambiguous, Set.of(BannerCraftingTags.COTTON, BannerCraftingTags.WOOL));
        configured.put(remainderFabric, Set.of(BannerCraftingTags.COTTON));
        configured.put(remainderMount, Set.of(BannerCraftingTags.BRASS));
        tags = Map.copyOf(configured);
        environment = environment(production, true);
    }

    @Test
    void fullDefinitionMaterialMountMatrixProduces264IndependentNaturalStates() {
        Map<FabricMaterialId, Item> materials = Map.of(
                FabricMaterialId.parse("britannia_mod:cotton"), cotton,
                FabricMaterialId.parse("britannia_mod:wool"), wool,
                FabricMaterialId.parse("britannia_mod:linen"), linen,
                FabricMaterialId.parse("britannia_mod:silk"), silk);
        Map<MountId, Item> mounts = Map.of(
                MountId.parse("britannia_mod:brass"), brass,
                MountId.parse("britannia_mod:iron"), iron);
        BannerRenderDataSnapshot renderData = BannerRenderDataSnapshot.fromRegistry(production);
        int combinations = 0;
        for (BannerDefinition definition : production.banners().activeDefinitions()) {
            int cost = area(definition);
            BannerCraftingRecipe recipe = recipe(definition, environment);
            for (Map.Entry<FabricMaterialId, Item> material : materials.entrySet()) {
                for (Map.Entry<MountId, Item> mount : mounts.entrySet()) {
                    CraftingInput input = input(definition.id(), material.getValue(), cost, mount.getValue());
                    assertTrue(recipe.matches(input, null));
                    ItemStack first = recipe.assemble(input, registryAccess);
                    ItemStack second = recipe.assemble(input, registryAccess);
                    assertNotSame(first, second);
                    assertNotSame(first.getComponents(), second.getComponents());
                    BannerInstanceState state = banner.stateAccess().read(first).orElseThrow();
                    assertEquals(definition.id(), state.bannerDefinitionId());
                    assertEquals(material.getKey(), state.materialId());
                    assertEquals(production.fabricMaterials().require(material.getKey()).naturalColourId(),
                            state.resolvedColourId());
                    assertEquals(mount.getKey(), state.mountId());
                    assertTrue(state.sourcePigmentId().isEmpty());
                    assertEquals(BannerDyeingConstants.CURRENT_SCHEMA_VERSION, state.schemaVersion());
                    assertEquals(banner, first.getItem());
                    assertEquals(1, first.getCount());
                    assertTrue(banner.stateAccess().validate(first, production, true).validForColourUpdate());
                    assertTrue(renderData.banners().containsKey(state.bannerDefinitionId()));
                    assertTrue(renderData.materials().get(state.materialId()).displaySrgbByColour()
                            .containsKey(state.resolvedColourId()));
                    assertTrue(renderData.mounts().containsKey(state.mountId()));
                    assertPersistentRoundTrip(first);
                    combinations++;
                }
            }
        }
        assertEquals(264, combinations);
    }

    @Test
    void allFourProvisionalAreaCostsMatchAndCraft() {
        Set<Integer> costs = new java.util.TreeSet<>();
        for (BannerDefinition definition : production.banners().activeDefinitions()) {
            int cost = area(definition);
            costs.add(cost);
            BannerCraftingRecipe recipe = recipe(definition, environment);
            assertTrue(recipe.matches(input(definition.id(), cotton, cost, brass), null));
        }
        assertEquals(Set.of(1, 2, 4, 6), costs);
    }

    @Test
    void exactClassificationRejectsEveryMalformedGrid() {
        BannerDefinition definition = production.banners().require(SMALL);
        BannerCraftingRecipe recipe = recipe(definition, environment);
        assertFalse(recipe.matches(CraftingInput.EMPTY, null));
        assertFalse(recipe.matches(inputWithoutPattern(cotton, 1, brass), null));
        assertFalse(recipe.matches(inputWithTwoPatterns(SMALL, cotton, brass), null));
        assertFalse(recipe.matches(input(BannerDefinitionId.parse("britannia_mod:end_01"), cotton, 1, brass), null));
        assertFalse(recipe.matches(input(SMALL, cotton, 0, brass), null));
        assertFalse(recipe.matches(input(SMALL, cotton, 2, brass), null));
        assertFalse(recipe.matches(input(SMALL, cotton, 1, unknown), null));
        assertFalse(recipe.matches(input(SMALL, unknown, 1, brass), null));
        assertFalse(recipe.matches(input(SMALL, ambiguous, 1, brass), null));
        assertFalse(recipe.matches(inputWithExtra(SMALL, cotton, brass, Items.RED_DYE), null));
        assertFalse(recipe.matches(inputWithExtra(SMALL, cotton, brass, banner), null));
        assertFalse(recipe.matches(inputWithTwoMounts(SMALL, cotton, brass, iron), null));
        assertTrue(recipe.assemble(inputWithExtra(SMALL, cotton, brass, Items.RED_DYE), registryAccess).isEmpty());
    }

    @Test
    void mixedFabricUnitsAndUnconfiguredPatternFailWithoutMutation() {
        BannerDefinition definition = production.banners().activeDefinitions().stream()
                .filter(value -> area(value) == 2).findFirst().orElseThrow();
        BannerCraftingRecipe recipe = recipe(definition, environment);
        ItemStack pattern = new ItemStack(Milestone14RegisteredTestContent.pattern());
        ItemStack cottonStack = new ItemStack(cotton);
        ItemStack woolStack = new ItemStack(wool);
        ItemStack brassStack = new ItemStack(brass);
        List<ItemStack> stacks = padded(pattern, cottonStack, woolStack, brassStack);
        List<ItemStack> before = stacks.stream().map(ItemStack::copy).toList();
        assertFalse(recipe.matches(CraftingInput.of(3, 3, stacks), null));
        for (int index = 0; index < stacks.size(); index++) {
            assertTrue(ItemStack.matches(before.get(index), stacks.get(index)));
        }
    }

    @Test
    void registryAndReloadFailuresReturnNoOutputThenRecover() {
        BannerDefinition definition = production.banners().require(SMALL);
        CraftingInput valid = input(SMALL, cotton, 1, brass);
        assertFalse(recipe(definition, environment(production, false)).matches(valid, null));
        assertFalse(recipe(definition, environment(
                RegistrySnapshotTestFactory.withoutBanner(production, SMALL), true)).matches(valid, null));
        assertFalse(recipe(definition, environment(
                RegistrySnapshotTestFactory.withDisabledBanner(production, SMALL), true)).matches(valid, null));
        FabricMaterialId cottonId = FabricMaterialId.parse("britannia_mod:cotton");
        assertFalse(recipe(definition, environment(
                RegistrySnapshotTestFactory.withoutMaterial(production, cottonId), true)).matches(valid, null));
        assertFalse(recipe(definition, environment(
                RegistrySnapshotTestFactory.withDisabledMaterial(production, cottonId), true)).matches(valid, null));
        assertFalse(recipe(definition, environment(
                RegistrySnapshotTestFactory.withoutPalette(
                        production, production.fabricMaterials().require(cottonId).paletteId()), true))
                .matches(valid, null));
        assertFalse(recipe(definition, environment(
                RegistrySnapshotTestFactory.withoutMount(
                        production, MountId.parse("britannia_mod:brass")), true)).matches(valid, null));
        assertFalse(recipe(definition, environment(
                RegistrySnapshotTestFactory.withDisabledMount(
                        production, MountId.parse("britannia_mod:brass")), true)).matches(valid, null));
        assertTrue(recipe(definition, environment).matches(valid, null));
    }

    @Test
    void reusablePatternRemainderIsExactAndOtherInputsAreConsumed() {
        BannerCraftingRecipe recipe = recipe(production.banners().require(SMALL), environment);
        CraftingInput input = input(SMALL, cotton, 1, brass);
        var remaining = recipe.getRemainingItems(input);
        assertEquals(1, remaining.stream().filter(stack -> !stack.isEmpty()).count());
        ItemStack returned = remaining.stream().filter(stack -> !stack.isEmpty()).findFirst().orElseThrow();
        assertEquals(Milestone14RegisteredTestContent.pattern(), returned.getItem());
        assertEquals(SMALL, returned.get(Milestone14RegisteredTestContent.patternComponent()));
        assertEquals(1, returned.getCount());
        assertTrue(recipe.getRemainingItems(inputWithExtra(SMALL, cotton, brass, unknown))
                .stream().allMatch(ItemStack::isEmpty));
    }

    @Test
    void standardFabricAndMountRemaindersArePreservedAlongsideOnePattern() {
        BannerCraftingRecipe recipe = recipe(production.banners().require(SMALL), environment);
        CraftingInput input = input(SMALL, remainderFabric, 1, remainderMount);
        var remaining = recipe.getRemainingItems(input);
        assertEquals(3, remaining.stream().filter(stack -> !stack.isEmpty()).count());
        assertEquals(1, remaining.stream()
                .filter(stack -> stack.is(Milestone14RegisteredTestContent.pattern())).count());
        assertEquals(1, remaining.stream().filter(stack -> stack.is(Items.BUCKET)).count());
        assertEquals(1, remaining.stream().filter(stack -> stack.is(Items.GLASS_BOTTLE)).count());
    }

    @Test
    void configuredPatternPersistsNetworksAndKeepsDefinitionsDistinct() {
        DataComponentType<BannerDefinitionId> component = Milestone14RegisteredTestContent.patternComponent();
        for (BannerDefinition definition : production.banners().activeDefinitions()) {
            ItemStack pattern = Milestone14RegisteredTestContent.pattern().configured(definition.id());
            Tag encoded = ItemStack.CODEC.encodeStart(
                    registryAccess.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), pattern)
                    .getOrThrow();
            ItemStack decoded = ItemStack.CODEC.parse(
                    registryAccess.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), encoded)
                    .getOrThrow();
            assertEquals(definition.id(), decoded.get(component));

            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
            component.streamCodec().encode(buffer, definition.id());
            assertEquals(definition.id(), component.streamCodec().decode(buffer));
        }
        ItemStack first = Milestone14RegisteredTestContent.pattern().configured(SMALL);
        ItemStack second = Milestone14RegisteredTestContent.pattern().configured(
                BannerDefinitionId.parse("britannia_mod:end_01"));
        assertFalse(ItemStack.isSameItemSameComponents(first, second));
        assertTrue(Milestone14RegisteredTestContent.pattern().definitionId(
                new ItemStack(Milestone14RegisteredTestContent.pattern())).isEmpty());
        assertTrue(Milestone14RegisteredTestContent.pattern().getName(first).toString().contains(SMALL.toString()));
    }

    @Test
    void resolversReturnTagEvidenceAndRejectAmbiguousOrUnsupportedIdentities() {
        BannerFabricCraftingResolver fabricResolver = new BannerFabricCraftingResolver();
        var cottonResolution = fabricResolver.resolve(
                new ItemStack(cotton), production, environment.tagLookup());
        assertEquals(FabricMaterialId.parse("britannia_mod:cotton"),
                cottonResolution.identity().orElseThrow());
        assertEquals(List.of(BannerCraftingTags.COTTON.location()), cottonResolution.evidence());
        assertFalse(fabricResolver.resolve(new ItemStack(unknown), production, environment.tagLookup()).successful());
        assertFalse(fabricResolver.resolve(new ItemStack(ambiguous), production, environment.tagLookup()).successful());

        BannerDefinition source = production.banners().require(SMALL);
        BannerDefinition brassOnly = new BannerDefinition(
                source.schemaVersion(), source.id(), source.displayNameKey(), source.contentStatus(),
                source.sourceReference(), source.catalogueGroup(), source.dimensions(), source.supportedOrientations(),
                List.of(MountId.parse("britannia_mod:brass")), MountId.parse("britannia_mod:brass"),
                source.defaultMaterial(), source.assets(), source.placementProfile());
        RegistrySnapshot restricted = RegistrySnapshotTestFactory.replaceBanner(production, brassOnly);
        var ironResolution = new BannerMountCraftingResolver().resolve(
                new ItemStack(iron), restricted, brassOnly, environment.tagLookup());
        assertFalse(ironResolution.successful());
        assertEquals(List.of(BannerCraftingTags.IRON.location()), ironResolution.evidence());
    }

    @Test
    void deterministicBulkSimulationConsumesExactUnitsAndNeverDuplicatesPattern() {
        BannerCraftingRecipe recipe = recipe(production.banners().require(SMALL), environment);
        ItemStack pattern = Milestone14RegisteredTestContent.pattern().configured(SMALL);
        List<ItemStack> outputs = new ArrayList<>();
        int fabricCount = 4;
        int mountCount = 3;
        while (fabricCount > 0 && mountCount > 0) {
            CraftingInput input = inputWithCounts(pattern, cotton, fabricCount, brass, mountCount);
            assertTrue(recipe.matches(input, null));
            outputs.add(recipe.assemble(input, registryAccess));
            ItemStack returned = recipe.getRemainingItems(input).get(0);
            assertEquals(pattern.get(Milestone14RegisteredTestContent.patternComponent()),
                    returned.get(Milestone14RegisteredTestContent.patternComponent()));
            pattern = returned;
            fabricCount--;
            mountCount--;
        }
        assertEquals(3, outputs.size());
        assertEquals(1, fabricCount);
        assertEquals(0, mountCount);
        assertEquals(1, pattern.getCount());
        assertEquals(3, outputs.stream().mapToInt(System::identityHashCode).distinct().count());
        assertTrue(outputs.stream().allMatch(stack -> stack.getCount() == 1));
    }

    @Test
    void serializerPersistentAndNetworkRoundTripsAndRejectsBadValues() {
        BannerCraftingRecipeSerializer serializer = new BannerCraftingRecipeSerializer();
        BannerCraftingRecipe recipe = new BannerCraftingRecipe(1, SMALL, 1);
        var encoded = serializer.codec().codec().encodeStart(JsonOps.INSTANCE, recipe).getOrThrow();
        BannerCraftingRecipe decoded = serializer.codec().codec().parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(recipe.schemaVersion(), decoded.schemaVersion());
        assertEquals(recipe.definitionId(), decoded.definitionId());
        assertEquals(recipe.fabricUnits(), decoded.fabricUnits());

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
        serializer.streamCodec().encode(buffer, recipe);
        BannerCraftingRecipe network = serializer.streamCodec().decode(buffer);
        assertEquals(recipe.definitionId(), network.definitionId());
        assertEquals(recipe.fabricUnits(), network.fabricUnits());

        assertTrue(serializer.codec().codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"schema_version\":2,\"banner_definition_id\":\"britannia_mod:x\",\"fabric_units\":1}"))
                .error().isPresent());
        assertTrue(serializer.codec().codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"schema_version\":1,\"banner_definition_id\":\"bad id\",\"fabric_units\":1}"))
                .error().isPresent());
        assertTrue(serializer.codec().codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"schema_version\":1,\"banner_definition_id\":\"britannia_mod:x\",\"fabric_units\":0}"))
                .error().isPresent());
        assertTrue(serializer.codec().codec().parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"schema_version\":1,\"banner_definition_id\":\"britannia_mod:x\",\"fabric_units\":1,\"unexpected\":true}"))
                .error().isPresent());
        assertThrows(IllegalArgumentException.class, () -> new BannerCraftingRecipe(1, SMALL, 7));
        assertEquals(RecipeType.CRAFTING, recipe.getType());
        assertTrue(recipe.isSpecial());
        assertTrue(recipe.getResultItem(registryAccess).isEmpty());
    }

    @Test
    void generatedDataContainsExactly33DefinitionRecipesAndSixIdentityTags() throws Exception {
        Path recipeRoot = Path.of("src/main/resources/data/britannia_mod/recipe/banner");
        try (var files = Files.list(recipeRoot)) {
            List<Path> recipes = files.filter(path -> path.toString().endsWith(".json")).sorted().toList();
            assertEquals(33, recipes.size());
            assertEquals(33, recipes.stream().map(path -> {
                try {
                    return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }).filter(json -> "britannia_mod:banner_crafting".equals(json.get("type").getAsString())).count());
        }
        assertEquals(6, Files.walk(Path.of("src/main/resources/data/britannia_mod/tags/item"))
                .filter(path -> path.toString().endsWith(".json")).count());
        assertFalse(Files.exists(Path.of("src/main/resources/data/britannia_mod/recipe/banner/cotton")));
    }

    private static BannerCraftingEnvironment environment(RegistrySnapshot snapshot, boolean available) {
        return new BannerCraftingEnvironment(
                () -> banner,
                Milestone14RegisteredTestContent::pattern,
                Milestone14RegisteredTestContent::patternComponent,
                () -> snapshot,
                () -> available,
                (stack, tag) -> tags.getOrDefault(stack.getItem(), Set.of()).contains(tag),
                new BannerFabricCraftingResolver(),
                new BannerMountCraftingResolver());
    }

    private static BannerCraftingRecipe recipe(BannerDefinition definition, BannerCraftingEnvironment env) {
        return new BannerCraftingRecipe(1, definition.id(), area(definition), env);
    }

    private static int area(BannerDefinition definition) {
        return definition.dimensions().widthBlocks() * definition.dimensions().heightBlocks();
    }

    private static CraftingInput input(
            BannerDefinitionId definitionId, Item fabric, int fabricUnits, Item mount) {
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(Milestone14RegisteredTestContent.pattern().configured(definitionId));
        for (int count = 0; count < fabricUnits; count++) {
            stacks.add(new ItemStack(fabric));
        }
        stacks.add(new ItemStack(mount));
        return CraftingInput.of(3, 3, padded(stacks.toArray(ItemStack[]::new)));
    }

    private static CraftingInput inputWithCounts(
            ItemStack pattern, Item fabric, int fabricCount, Item mount, int mountCount) {
        ItemStack fabricStack = new ItemStack(fabric, fabricCount);
        ItemStack mountStack = new ItemStack(mount, mountCount);
        return CraftingInput.of(3, 3, padded(pattern.copy(), fabricStack, mountStack));
    }

    private static CraftingInput inputWithoutPattern(Item fabric, int fabricUnits, Item mount) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int count = 0; count < fabricUnits; count++) {
            stacks.add(new ItemStack(fabric));
        }
        stacks.add(new ItemStack(mount));
        return CraftingInput.of(3, 3, padded(stacks.toArray(ItemStack[]::new)));
    }

    private static CraftingInput inputWithTwoPatterns(BannerDefinitionId id, Item fabric, Item mount) {
        return CraftingInput.of(3, 3, padded(
                Milestone14RegisteredTestContent.pattern().configured(id),
                Milestone14RegisteredTestContent.pattern().configured(id),
                new ItemStack(fabric), new ItemStack(mount)));
    }

    private static CraftingInput inputWithTwoMounts(
            BannerDefinitionId id, Item fabric, Item firstMount, Item secondMount) {
        return CraftingInput.of(3, 3, padded(
                Milestone14RegisteredTestContent.pattern().configured(id),
                new ItemStack(fabric), new ItemStack(firstMount), new ItemStack(secondMount)));
    }

    private static CraftingInput inputWithExtra(
            BannerDefinitionId id, Item fabric, Item mount, Item extra) {
        return CraftingInput.of(3, 3, padded(
                Milestone14RegisteredTestContent.pattern().configured(id),
                new ItemStack(fabric), new ItemStack(mount), new ItemStack(extra)));
    }

    private static List<ItemStack> padded(ItemStack... stacks) {
        ArrayList<ItemStack> result = new ArrayList<>(List.of(stacks));
        while (result.size() < 9) {
            result.add(ItemStack.EMPTY);
        }
        return result;
    }

    private static void assertPersistentRoundTrip(ItemStack stack) {
        Tag encoded = ItemStack.CODEC.encodeStart(
                registryAccess.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), stack).getOrThrow();
        ItemStack decoded = ItemStack.CODEC.parse(
                registryAccess.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), encoded).getOrThrow();
        assertTrue(ItemStack.matches(stack, decoded));
    }
}
