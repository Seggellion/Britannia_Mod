package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.item.DyeTubItem;
import com.seggellion.britannia_mod.dye.item.DyeTubStateAccess;
import com.seggellion.britannia_mod.dye.item.DyeTubTooltip;
import com.seggellion.britannia_mod.dye.state.DyeTubState;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DyeTubTooltipTest {
    private static RegistrySnapshot production;

    @BeforeAll
    static void loadData() throws Exception {
        com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone6RegisteredTestContent.ensureRegistered();
        production = DyeResolverFixtures.productionSnapshot();
    }

    @Test
    void emptyTooltipUsesEmptyAndHintKeys() {
        List<Component> lines = DyeTubTooltip.lines(DyeTubState.empty(), production);
        assertEquals(List.of(
                "tooltip.britannia_mod.dye_tub.empty",
                "tooltip.britannia_mod.dye_tub.off_hand_hint"), keys(lines));
    }

    @Test
    void unlimitedTooltipUsesRegistryPigmentNameAndUnlimitedKey() {
        List<Component> lines = DyeTubTooltip.lines(
                DyeTubState.loadedUnlimited(PigmentId.parse("britannia_mod:madder_red")), production);
        assertEquals("tooltip.britannia_mod.dye_tub.contains", key(lines.get(0)));
        Object[] containsArgs = contents(lines.get(0)).getArgs();
        assertEquals("pigment.britannia_mod.madder_red", key((Component) containsArgs[0]));
        assertEquals("tooltip.britannia_mod.dye_tub.uses", key(lines.get(1)));
        assertEquals("tooltip.britannia_mod.dye_tub.unlimited",
                key((Component) contents(lines.get(1)).getArgs()[0]));
    }

    @Test
    void finiteTooltipShowsExactCount() {
        DyeTubState finite = new DyeTubState(
                BannerDyeingConstants.CURRENT_SCHEMA_VERSION,
                Optional.of(PigmentId.parse("britannia_mod:ice_blue")),
                Optional.of(12));
        Component uses = DyeTubTooltip.lines(finite, production).get(1);
        assertEquals("tooltip.britannia_mod.dye_tub.uses", key(uses));
        assertArrayEquals(new Object[] {12}, contents(uses).getArgs());
    }

    @Test
    void missingPigmentFallbackPreservesStableId() {
        PigmentId missing = PigmentId.parse("britannia_mod:removed_after_reload");
        Component contains = DyeTubTooltip.lines(DyeTubState.loadedUnlimited(missing), RegistrySnapshot.empty()).get(0);
        Component fallback = (Component) contents(contains).getArgs()[0];
        assertEquals("tooltip.britannia_mod.dye_tub.missing_pigment", key(fallback));
        assertArrayEquals(new Object[] {missing.toString()}, contents(fallback).getArgs());
    }

    @Test
    void tooltipReadDoesNotMutateMissingComponent() {
        DataComponentType<DyeTubState> type = DataComponentRegistry.createDyeTubStateType();
        ItemStack stack = new ItemStack(new DyeTubItem(new Item.Properties().stacksTo(1)));
        DyeTubState state = DyeTubStateAccess.read(stack, type);
        DyeTubTooltip.lines(state, production);
        assertTrue(stack.getComponentsPatch().isEmpty());
    }

    private static List<String> keys(List<Component> components) {
        return components.stream().map(DyeTubTooltipTest::key).toList();
    }

    private static String key(Component component) {
        return contents(component).getKey();
    }

    private static TranslatableContents contents(Component component) {
        return (TranslatableContents) component.getContents();
    }
}
