package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.item.DyeTubItem;
import com.seggellion.britannia_mod.dye.item.DyeTubStateAccess;
import com.seggellion.britannia_mod.dye.item.PigmentItem;
import com.seggellion.britannia_mod.dye.state.DyeTubState;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import java.util.Optional;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

class DyeTubComponentTest {
    private static final PigmentId RED = PigmentId.parse("britannia_mod:madder_red");

    @BeforeAll
    static void bootstrapRegistries() {
        com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone6RegisteredTestContent.ensureRegistered();
    }

    @Test
    void registeredDeclarationHasExpectedId() {
        assertEquals("britannia_mod:dye_tub_state", DataComponentRegistry.DYE_TUB_STATE.getId().toString());
    }

    @Test
    void componentTypeHasPersistentAndNetworkCodecs() {
        DataComponentType<DyeTubState> type = DataComponentRegistry.createDyeTubStateType();
        assertSame(DyeTubState.CODEC, type.codec());
        assertSame(DyeTubState.STREAM_CODEC, type.streamCodec());
    }

    @Test
    void newTubHasCanonicalExplicitEmptyState() {
        DataComponentType<DyeTubState> type = DataComponentRegistry.createDyeTubStateType();
        DyeTubItem item = new DyeTubItem(new Item.Properties().stacksTo(1)
                .component(type, DyeTubState.empty()));
        ItemStack stack = new ItemStack(item);
        assertTrue(stack.has(type));
        assertEquals(DyeTubState.empty(), DyeTubStateAccess.read(stack, type));
    }

    @Test
    void missingComponentReadsEmptyWithoutMutation() {
        DataComponentType<DyeTubState> type = DataComponentRegistry.createDyeTubStateType();
        ItemStack stack = new ItemStack(new DyeTubItem(new Item.Properties().stacksTo(1)));
        assertFalse(stack.has(type));
        assertEquals(DyeTubState.empty(), DyeTubStateAccess.read(stack, type));
        assertFalse(stack.has(type));
    }

    @Test
    void settingStatePreservesCustomNameAndUnrelatedComponents() {
        DataComponentType<DyeTubState> type = DataComponentRegistry.createDyeTubStateType();
        ItemStack stack = new ItemStack(new DyeTubItem(new Item.Properties().stacksTo(1)));
        Component name = Component.literal("Named Tub");
        stack.set(DataComponents.CUSTOM_NAME, name);
        DyeTubStateAccess.write(stack, type, DyeTubState.loadedUnlimited(RED));
        assertEquals(name, stack.get(DataComponents.CUSTOM_NAME));
        assertEquals(RED, DyeTubStateAccess.read(stack, type).pigmentId().orElseThrow());
    }

    @Test
    void finiteAndUnlimitedStatesRemainDistinct() {
        DyeTubState unlimited = DyeTubState.loadedUnlimited(RED);
        DyeTubState finite = new DyeTubState(
                BannerDyeingConstants.CURRENT_SCHEMA_VERSION, Optional.of(RED), Optional.of(3));
        assertNotEquals(unlimited, finite);
    }

    @Test
    void tubMaxStackIsOneAndPigmentsRemainStackable() {
        DyeTubItem tub = new DyeTubItem(new Item.Properties().stacksTo(1));
        PigmentItem pigment = new PigmentItem(new Item.Properties(), RED);
        assertEquals(1, new ItemStack(tub).getMaxStackSize());
        assertEquals(64, new ItemStack(pigment).getMaxStackSize());
        assertTrue(new ItemStack(pigment).isStackable());
    }

    @Test
    void differentlyLoadedTubsCannotMerge() {
        DataComponentType<DyeTubState> type = DataComponentRegistry.createDyeTubStateType();
        DyeTubItem tub = new DyeTubItem(new Item.Properties().stacksTo(1)
                .component(type, DyeTubState.empty()));
        ItemStack red = new ItemStack(tub);
        ItemStack blue = new ItemStack(tub);
        DyeTubStateAccess.write(red, type, DyeTubState.loadedUnlimited(RED));
        DyeTubStateAccess.write(blue, type,
                DyeTubState.loadedUnlimited(PigmentId.parse("britannia_mod:woad_blue")));
        assertFalse(ItemStack.isSameItemSameComponents(red, blue));
        assertNotNull(red.get(type));
        assertNotNull(blue.get(type));
    }
}
