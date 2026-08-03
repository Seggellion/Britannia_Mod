package com.seggellion.britannia_mod.structure.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ShrineItemStateTest {
    private static RegistryAccess registryAccess;
    private static ShrineItem item;

    @BeforeAll
    static void setup() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
        registryAccess = new RegistryAccess.ImmutableRegistryAccess(BuiltInRegistries.REGISTRY.stream().toList());
        item = MilestoneTwoRegisteredTestContent.shrine();
    }

    @Test
    void defaultGiveStackResolvesOnlyToApprovedDefaultDuringServerValidation() {
        ItemStack rawGiveStack = new ItemStack(item);
        var validation = item.stateAccess().validateForPlacement(
                rawGiveStack, ShrineMonolithDefinitions.catalogue());
        assertTrue(validation.valid());
        assertTrue(validation.usedDefault());
        assertEquals(ShrineItemStateAccess.defaultState(), validation.state().orElseThrow());
        assertTrue(item.stateAccess().read(rawGiveStack).isEmpty());
    }

    @Test
    void configuredStateRoundTripsThroughCodecAndRegisteredItemStackCodec() {
        ShrineItemState expected = state("shrine", "justice");
        Tag encodedState = ShrineItemState.CODEC.encodeStart(NbtOps.INSTANCE, expected).getOrThrow();
        assertEquals(expected, ShrineItemState.CODEC.parse(NbtOps.INSTANCE, encodedState).getOrThrow());
        ItemStack stack = item.stateAccess().configuredStack(expected);
        Tag encodedStack = stack.save(registryAccess);
        ItemStack decoded = ItemStack.parse(registryAccess, encodedStack).orElseThrow();
        assertEquals(item, decoded.getItem());
        assertEquals(expected, item.stateAccess().read(decoded).orElseThrow());
    }

    @Test
    void configuredStateRoundTripsThroughRegisteredComponentStreamCodec() {
        ShrineItemState expected = state("shrine", "valor");
        RegistryFriendlyByteBuf componentBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
        MilestoneTwoRegisteredTestContent.component().streamCodec().encode(componentBuffer, expected);
        assertEquals(expected,
                MilestoneTwoRegisteredTestContent.component().streamCodec().decode(componentBuffer));
    }

    @Test
    void missingFamilyAndVariantIdsRemainRepresentableWithoutSubstitution() {
        ShrineItemState missingFamily = state("removed_family", "honesty");
        ShrineItemState missingVariant = state("shrine", "removed_variant");
        ItemStack familyStack = item.stateAccess().configuredStack(missingFamily);
        ItemStack variantStack = item.stateAccess().configuredStack(missingVariant);
        assertEquals(ShrineItemStateAccess.Status.UNSUPPORTED_FAMILY,
                item.stateAccess().validateForPlacement(familyStack,
                        ShrineMonolithDefinitions.catalogue()).status());
        assertEquals(ShrineItemStateAccess.Status.VARIANT_MISSING,
                item.stateAccess().validateForPlacement(variantStack,
                        ShrineMonolithDefinitions.catalogue()).status());
        assertEquals("removed_family", item.stateAccess().read(familyStack).orElseThrow().familyId().value());
        assertEquals("removed_variant", item.stateAccess().read(variantStack).orElseThrow().variantId().value());
    }

    @Test
    void malformedAndFutureConfiguredStateFailAtCodecBoundary() {
        CompoundTag malformed = new CompoundTag();
        malformed.putInt("schema_version", 1);
        malformed.putString("family_id", "shrine");
        assertTrue(ShrineItemState.CODEC.parse(NbtOps.INSTANCE, malformed).error().isPresent());
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 999);
        future.putString("family_id", "shrine");
        future.putString("variant_id", "honesty");
        assertTrue(ShrineItemState.CODEC.parse(NbtOps.INSTANCE, future).error().isPresent());
    }

    @Test
    void itemStateCannotCarryFootprintModelTextureOrRenderOffsets() {
        ShrineItemState expected = state("shrine", "honesty");
        String encoded = ShrineItemState.CODEC.encodeStart(NbtOps.INSTANCE, expected).getOrThrow().toString();
        assertTrue(encoded.contains("schema_version"));
        assertTrue(encoded.contains("family_id"));
        assertTrue(encoded.contains("variant_id"));
        assertFalse(encoded.contains("footprint"));
        assertFalse(encoded.contains("model"));
        assertFalse(encoded.contains("texture"));
        assertFalse(encoded.contains("offset"));
    }

    @Test
    void copiedStacksHaveIndependentImmutableComponentPatches() {
        ItemStack original = item.stateAccess().configuredStack(state("shrine", "honesty"));
        ItemStack copy = original.copy();
        copy.set(MilestoneTwoRegisteredTestContent.component(), state("shrine", "compassion"));
        assertEquals("honesty", item.stateAccess().read(original).orElseThrow().variantId().value());
        assertEquals("compassion", item.stateAccess().read(copy).orElseThrow().variantId().value());
        assertNotEquals(item.stateAccess().read(original), item.stateAccess().read(copy));
    }

    @Test
    void recoveredConfiguredStackPreservesAllThreeFields() {
        ShrineItemState expected = state("shrine", "spirituality");
        ItemStack recovered = item.stateAccess().configuredStack(expected);
        assertEquals(1, recovered.getCount());
        assertEquals(expected, item.stateAccess().read(recovered).orElseThrow());
    }

    private static ShrineItemState state(String family, String variant) {
        return new ShrineItemState(1, new FamilyId(family), new VariantId(variant));
    }
}
