package com.seggellion.britannia_mod.bank.item;

import com.mojang.serialization.DataResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Canonical, versioned, registry-aware serialization contract for a single ItemStack,
 * for durable storage outside the normal Minecraft world save (an opaque Rails bank-item
 * payload).
 *
 * This wraps vanilla's own {@link ItemStack#CODEC} -- the same registry-aware codec vanilla
 * uses for every other durable ItemStack persistence path (item entities, container block
 * entities, etc.) -- rather than hand-selecting fields. {@link ItemStack#CODEC} already
 * encodes the registry item id, count, and the complete data component patch (every vanilla
 * and modded component that differs from the item's prototype defaults, including this mod's
 * {@code WINE_DATA} and every hand-rolled {@code CUSTOM_DATA} field). The only thing this
 * class adds on top is an explicit {@link BankItemSchemaVersion} envelope, because
 * {@code ItemStack.CODEC} itself carries no version tag and this mod has no existing
 * out-of-world-save persistence format for items to extend.
 *
 * Item-eligibility policy is enforced via {@link BankItemEligibility#checkEligible} -- see that
 * class for the full, current list of carve-outs (currency, quest-bound items, unsupported mod
 * origins) and the deliberate order they are checked in. Two boundaries remain explicitly out
 * of scope, not silently missing: bank cheques (no cheque {@code Item} exists yet to check
 * against -- not yet applicable, not yet decided), and component-level origin (a foreign
 * enchantment or other component attached to an item whose own registry key is otherwise
 * supported is not detected -- ADR-014's own stated boundary). Every item that clears
 * {@link BankItemEligibility#checkEligible} is serialized as-is.
 */
public final class BankItemCodec {
    private static final String KEY_SCHEMA_VERSION = "schema_version";
    private static final String KEY_ITEM = "item";

    private BankItemCodec() {
    }

    /**
     * Serializes {@code stack} into a durable, opaque byte payload carrying an explicit
     * schema version tag. Throws for a programming error (a null/empty stack, or one nested
     * deeper than {@link BankItemNesting#MAX_DEPTH}, is never a valid banked item) and for any
     * eligibility violation {@link BankItemEligibility#checkEligible} finds anywhere in the
     * nested structure (currency, quest-bound, unsupported origin), via
     * {@link BankItemEligibility.IneligibleItemException} rather than silently serializing an
     * ineligible item as a generic one. It does not return a result type because there is no
     * recoverable caller decision to make here, unlike {@link #deserialize}. The nesting-depth
     * check specifically is a caller-error guard, not a security boundary -- {@code stack} is
     * always an in-memory ItemStack a real caller already holds (a player's inventory slot),
     * not untrusted bytes, so an over-the-limit stack reaching here can only mean it was
     * assembled some other way (a test, a command) than a real deposit ever would.
     */
    public static byte[] serialize(ItemStack stack, HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException("Cannot serialize an empty ItemStack for banking");
        }
        BankItemEligibility.checkEligible(stack);
        if (BankItemNesting.containerNestingDepthOf(stack) > BankItemNesting.MAX_DEPTH) {
            throw new IllegalArgumentException(
                    "Cannot serialize an ItemStack nested more than " + BankItemNesting.MAX_DEPTH + " containers deep");
        }

        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        Tag itemTag = ItemStack.CODEC.encodeStart(ops, stack)
                .getOrThrow(error -> new IllegalStateException("Failed to encode ItemStack for banking: " + error));

        CompoundTag outer = new CompoundTag();
        outer.putInt(KEY_SCHEMA_VERSION, BankItemSchemaVersion.CURRENT);
        outer.put(KEY_ITEM, itemTag);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            NbtIo.writeCompressed(outer, buffer);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write bank item payload", e);
        }
        return buffer.toByteArray();
    }

    /**
     * Decodes a payload produced by {@link #serialize}. Never throws: a corrupt/truncated
     * payload or an unsupported schema version is reported as a typed result, not a crash,
     * because both are expected conditions a real caller (deposit/withdraw validation,
     * startup reconciliation) must branch on.
     */
    public static BankItemDecodeResult deserialize(byte[] payload, HolderLookup.Provider registries) {
        if (payload == null || payload.length == 0) {
            return new BankItemDecodeResult.Corrupt("payload is empty");
        }

        CompoundTag outer;
        try {
            outer = NbtIo.readCompressed(new ByteArrayInputStream(payload), NbtAccounter.unlimitedHeap());
        } catch (IOException | RuntimeException e) {
            return new BankItemDecodeResult.Corrupt("payload is not valid compressed NBT: " + e.getMessage());
        }

        if (!(outer.get(KEY_SCHEMA_VERSION) instanceof IntTag versionTag)) {
            return new BankItemDecodeResult.Corrupt("missing or malformed " + KEY_SCHEMA_VERSION + " field");
        }
        int version = versionTag.getAsInt();
        if (!BankItemSchemaVersion.isSupported(version)) {
            return new BankItemDecodeResult.UnsupportedSchemaVersion(version);
        }

        Tag itemTag = outer.get(KEY_ITEM);
        if (itemTag == null) {
            return new BankItemDecodeResult.Corrupt("missing " + KEY_ITEM + " field");
        }
        // Checked on the raw, undecoded tag -- before ItemStack.CODEC.parse ever runs -- so a
        // maliciously deep payload is rejected before vanilla's own unbounded codec recursion
        // touches it. This is the real security boundary for nesting depth (unlike serialize's
        // caller-error throw): the payload is untrusted external input, not an in-memory
        // ItemStack a caller already holds.
        if (BankItemNesting.containerNestingDepthOfTag(itemTag) > BankItemNesting.MAX_DEPTH) {
            return new BankItemDecodeResult.Corrupt(
                    "item payload is nested more than " + BankItemNesting.MAX_DEPTH + " containers deep");
        }

        try {
            RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
            DataResult<ItemStack> result = ItemStack.CODEC.parse(ops, itemTag);
            if (result.error().isPresent()) {
                return new BankItemDecodeResult.Corrupt("item payload failed to decode: " + result.error().get().message());
            }
            ItemStack stack = result.result().orElseThrow(
                    () -> new IllegalStateException("DataResult had neither a result nor an error"));
            return new BankItemDecodeResult.Success(stack);
        } catch (RuntimeException e) {
            return new BankItemDecodeResult.Corrupt("unexpected error decoding item payload: " + e.getMessage());
        }
    }

    /**
     * The size, in bytes, of an already-serialized payload. No limit is enforced here --
     * Rails-side payload/version/size validation (Milestone 8 Rails scope) is a separate,
     * later concern. This exists only so that later validation has something real to call.
     */
    public static int measurePayloadSize(byte[] serializedPayload) {
        return serializedPayload == null ? 0 : serializedPayload.length;
    }
}
