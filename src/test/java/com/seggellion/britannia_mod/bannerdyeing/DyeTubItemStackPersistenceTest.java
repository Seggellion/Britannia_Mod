package com.seggellion.britannia_mod.bannerdyeing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone6RegisteredTestContent;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.item.DyeTubStateAccess;
import com.seggellion.britannia_mod.dye.state.DyeTubState;
import io.netty.buffer.Unpooled;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DyeTubItemStackPersistenceTest {
    private static RegistryAccess registryAccess;

    @BeforeAll
    static void registerTestContent() {
        Milestone6RegisteredTestContent.ensureRegistered();
        registryAccess = new RegistryAccess.ImmutableRegistryAccess(BuiltInRegistries.REGISTRY.stream().toList());
    }

    @Test
    void emptyDefaultSurvivesActualItemStackPersistentRoundTrip() {
        ItemStack decoded = persistentRoundTrip(new ItemStack(Milestone6RegisteredTestContent.tub()));
        assertEquals(DyeTubState.empty(), DyeTubStateAccess.read(decoded, Milestone6RegisteredTestContent.component()));
        assertTrue(decoded.has(Milestone6RegisteredTestContent.component()));
    }

    @Test
    void loadedUnlimitedPigmentSurvivesActualItemStackPersistentRoundTrip() {
        DyeTubState state = DyeTubState.loadedUnlimited(PigmentId.parse("britannia_mod:madder_red"));
        ItemStack stack = new ItemStack(Milestone6RegisteredTestContent.tub());
        DyeTubStateAccess.write(stack, Milestone6RegisteredTestContent.component(), state);
        ItemStack decoded = persistentRoundTrip(stack);
        assertEquals(state, DyeTubStateAccess.read(decoded, Milestone6RegisteredTestContent.component()));
        assertEquals(PigmentId.parse("britannia_mod:madder_red"),
                DyeTubStateAccess.read(decoded, Milestone6RegisteredTestContent.component()).pigmentId().orElseThrow());
        assertTrue(DyeTubStateAccess.read(decoded, Milestone6RegisteredTestContent.component()).remainingUses().isEmpty());
    }

    @Test
    void finiteFixtureSurvivesActualItemStackPersistentRoundTrip() {
        DyeTubState state = new DyeTubState(
                BannerDyeingConstants.CURRENT_SCHEMA_VERSION,
                Optional.of(PigmentId.parse("britannia_mod:ice_blue")),
                Optional.of(7));
        ItemStack stack = new ItemStack(Milestone6RegisteredTestContent.tub());
        DyeTubStateAccess.write(stack, Milestone6RegisteredTestContent.component(), state);
        assertEquals(state, DyeTubStateAccess.read(
                persistentRoundTrip(stack), Milestone6RegisteredTestContent.component()));
    }

    @Test
    void registeredNetworkComponentRoundTripPreservesLoadedState() {
        DyeTubState state = DyeTubState.loadedUnlimited(PigmentId.parse("britannia_mod:woad_blue"));
        ItemStack stack = new ItemStack(Milestone6RegisteredTestContent.tub());
        DyeTubStateAccess.write(stack, Milestone6RegisteredTestContent.component(), state);

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
        Milestone6RegisteredTestContent.component().streamCodec().encode(buffer,
                DyeTubStateAccess.read(stack, Milestone6RegisteredTestContent.component()));
        DyeTubState decodedState = Milestone6RegisteredTestContent.component().streamCodec().decode(buffer);
        ItemStack decoded = new ItemStack(Milestone6RegisteredTestContent.tub());
        DyeTubStateAccess.write(decoded, Milestone6RegisteredTestContent.component(), decodedState);

        assertEquals(state, DyeTubStateAccess.read(decoded, Milestone6RegisteredTestContent.component()));
    }

    @Test
    void differentLoadedStatesDoNotMergeAfterRoundTrip() {
        ItemStack red = new ItemStack(Milestone6RegisteredTestContent.tub());
        ItemStack blue = new ItemStack(Milestone6RegisteredTestContent.tub());
        DyeTubStateAccess.write(red, Milestone6RegisteredTestContent.component(),
                DyeTubState.loadedUnlimited(PigmentId.parse("britannia_mod:madder_red")));
        DyeTubStateAccess.write(blue, Milestone6RegisteredTestContent.component(),
                DyeTubState.loadedUnlimited(PigmentId.parse("britannia_mod:woad_blue")));
        red = persistentRoundTrip(red);
        blue = persistentRoundTrip(blue);
        assertFalse(ItemStack.isSameItemSameComponents(red, blue));
        assertEquals(1, red.getMaxStackSize());
        assertEquals(1, blue.getMaxStackSize());
    }

    private static ItemStack persistentRoundTrip(ItemStack stack) {
        Tag encoded = stack.save(registryAccess);
        return ItemStack.parse(registryAccess, encoded).orElseThrow();
    }
}
