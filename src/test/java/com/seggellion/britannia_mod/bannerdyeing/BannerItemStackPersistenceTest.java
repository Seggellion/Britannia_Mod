package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.item.BannerItemFactory;
import com.seggellion.britannia_mod.banner.item.BannerItemStateAccess;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone7RegisteredTestContent;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class BannerItemStackPersistenceTest {
    private static final BannerDefinitionId WARD = BannerDefinitionId.parse("britannia_mod:ward_of_serpents");
    private static final FabricMaterialId COTTON = FabricMaterialId.parse("britannia_mod:cotton");
    private static final PigmentId MADDER = PigmentId.parse("britannia_mod:madder_red");
    private static final MountId BRASS = MountId.parse("britannia_mod:brass");
    private static RegistryAccess registryAccess;
    private static RegistrySnapshot production;
    private static BannerItem item;
    private static BannerItemStateAccess access;
    private static BannerItemFactory factory;

    @BeforeAll
    static void setup() throws Exception {
        Milestone7RegisteredTestContent.ensureRegistered();
        registryAccess = new RegistryAccess.ImmutableRegistryAccess(BuiltInRegistries.REGISTRY.stream().toList());
        production = DyeResolverFixtures.productionSnapshot();
        item = Milestone7RegisteredTestContent.banner();
        access = item.stateAccess();
        factory = new BannerItemFactory(item, access);
    }

    @Test
    void rawUnconfiguredItemRoundTripRemainsDistinctFromConfigured() {
        ItemStack raw = persistentRoundTrip(new ItemStack(item));
        assertTrue(access.read(raw).isEmpty());
        assertFalse(ItemStack.isSameItemSameComponents(raw, natural(WARD)));
    }

    @Test
    void naturalCottonActualRegisteredItemStackRoundTripPreservesEveryField() {
        assertRoundTrip(natural(WARD));
        assertTrue(access.read(persistentRoundTrip(natural(WARD))).orElseThrow().sourcePigmentId().isEmpty());
    }

    @Test
    void craftedMaterialActualRegisteredItemStackRoundTrips() {
        for (String material : new String[] {"cotton", "wool", "linen", "silk"}) {
            ItemStack stack = factory.craftedMaterialBanner(WARD,
                    FabricMaterialId.parse("britannia_mod:" + material), Optional.empty(), production, true)
                    .stack().orElseThrow();
            assertRoundTrip(stack);
        }
    }

    @Test
    void fullySpecifiedDyedStackAndNetworkComponentRoundTripPreserveSourcePigment() {
        ItemStack stack = dyed();
        BannerInstanceState state = access.read(stack).orElseThrow();
        assertRoundTrip(stack);

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
        Milestone7RegisteredTestContent.component().streamCodec().encode(buffer, state);
        BannerInstanceState decoded = Milestone7RegisteredTestContent.component().streamCodec().decode(buffer);
        assertEquals(state, decoded);
        assertEquals(Optional.of(MADDER), decoded.sourcePigmentId());
    }

    @ParameterizedTest
    @MethodSource("definitions")
    void everyCatalogueDefinitionPersistsOnTheSameRegisteredItem(BannerDefinition definition) {
        ItemStack stack = natural(definition.id());
        ItemStack decoded = persistentRoundTrip(stack);
        assertEquals(item, decoded.getItem());
        assertEquals(definition.id(), access.read(decoded).orElseThrow().bannerDefinitionId());
        assertEquals(access.read(stack), access.read(decoded));
    }

    @Test
    void customNameAndUnrelatedComponentSurviveRoundTripAndStateUpdate() {
        ItemStack stack = natural(WARD);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("The Old Standard"));
        CompoundTag data = new CompoundTag();
        data.putInt("unrelated_value", 17);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        ItemStack decoded = persistentRoundTrip(stack);
        assertEquals("The Old Standard", decoded.getHoverName().getString());
        assertEquals(17, decoded.get(DataComponents.CUSTOM_DATA).copyTag().getInt("unrelated_value"));
    }

    @Test
    void eachIndependentStateDifferencePreventsComponentMergeCompatibility() {
        BannerInstanceState base = access.read(dyed()).orElseThrow();
        assertDifferent(base, new BannerInstanceState(1,
                BannerDefinitionId.parse("britannia_mod:outer_ward"), base.materialId(), base.resolvedColourId(),
                base.sourcePigmentId(), base.mountId()));
        assertDifferent(base, new BannerInstanceState(1, base.bannerDefinitionId(),
                FabricMaterialId.parse("britannia_mod:silk"), ResolvedColourId.parse("britannia_mod:silk_ruby"),
                base.sourcePigmentId(), base.mountId()));
        assertDifferent(base, new BannerInstanceState(1, base.bannerDefinitionId(), base.materialId(),
                ResolvedColourId.parse("britannia_mod:cotton_blue"), base.sourcePigmentId(), base.mountId()));
        assertDifferent(base, new BannerInstanceState(1, base.bannerDefinitionId(), base.materialId(),
                base.resolvedColourId(), Optional.of(PigmentId.parse("britannia_mod:woad_blue")), base.mountId()));
        assertDifferent(base, new BannerInstanceState(1, base.bannerDefinitionId(), base.materialId(),
                base.resolvedColourId(), Optional.empty(), base.mountId()));
        assertDifferent(base, new BannerInstanceState(1, base.bannerDefinitionId(), base.materialId(),
                base.resolvedColourId(), base.sourcePigmentId(), MountId.parse("britannia_mod:iron")));
    }

    @Test
    void identicalStatesAreComponentCompatibleButMaximumStackSizeIsOne() {
        ItemStack first = dyed();
        ItemStack second = persistentRoundTrip(dyed());
        assertTrue(ItemStack.isSameItemSameComponents(first, second));
        assertEquals(1, first.getMaxStackSize());
        assertEquals(1, second.getMaxStackSize());
    }

    private static Stream<BannerDefinition> definitions() {
        return production.banners().activeDefinitions().stream();
    }

    private static void assertDifferent(BannerInstanceState first, BannerInstanceState second) {
        ItemStack left = stateStack(first);
        ItemStack right = stateStack(second);
        assertFalse(ItemStack.isSameItemSameComponents(persistentRoundTrip(left), persistentRoundTrip(right)));
    }

    private static void assertRoundTrip(ItemStack stack) {
        ItemStack decoded = persistentRoundTrip(stack);
        assertEquals(item, decoded.getItem());
        assertEquals(access.read(stack), access.read(decoded));
    }

    private static ItemStack natural(BannerDefinitionId definitionId) {
        return factory.naturalCottonAdminBanner(definitionId, production, true).stack().orElseThrow();
    }

    private static ItemStack dyed() {
        return factory.fullySpecifiedBanner(WARD, COTTON,
                ResolvedColourId.parse("britannia_mod:cotton_red"), Optional.of(MADDER), BRASS,
                production, true).stack().orElseThrow();
    }

    private static ItemStack stateStack(BannerInstanceState state) {
        ItemStack stack = new ItemStack(item);
        stack.set(Milestone7RegisteredTestContent.component(), state);
        return stack;
    }

    private static ItemStack persistentRoundTrip(ItemStack stack) {
        Tag encoded = stack.save(registryAccess);
        return ItemStack.parse(registryAccess, encoded).orElseThrow();
    }
}
