package com.seggellion.britannia_mod.bank.item;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.TreeSet;

/**
 * A stable, deterministic identifier for "this exact ItemStack" from a banking
 * perspective.
 *
 * <p><b>Gameplay-relevant (participates in the fingerprint):</b> the registry item id, the
 * stack count, and the full data component patch -- exactly the same
 * {@link DataComponentPatch} that backs vanilla's own
 * {@link ItemStack#isSameItemSameComponents(ItemStack, ItemStack)}. Two independently
 * constructed stacks with the same item, same count, and an equal component patch always
 * fingerprint identically, because both are hashed from the same canonical encoding of
 * that patch.
 *
 * <p><b>Safely ignored:</b> NBT compound key ordering. {@link ItemStack#CODEC} (and the
 * component codecs it delegates to, e.g. enchantments, which encode as a compound keyed by
 * enchantment id) always produce map-shaped NBT for anything that behaves as a set/map at
 * the game-logic level; only the containing {@link CompoundTag}'s key iteration order is an
 * artifact of how it happened to be built, not gameplay data. Every compound encountered
 * during canonicalization is therefore re-sorted by key before hashing. List order is
 * preserved as-is: nothing in this mod's real components encodes an order-insensitive
 * collection as a {@link ListTag}, so there is no case to safely ignore there.
 *
 * <p>Count is deliberately included, matching this program's own requirement that two
 * otherwise-identical stacks of different sizes are not the same deposit.
 */
public final class BankItemFingerprint {
    private BankItemFingerprint() {
    }

    public static String fingerprint(ItemStack stack, HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException("Cannot fingerprint an empty ItemStack");
        }

        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        Tag patchTag = DataComponentPatch.CODEC.encodeStart(ops, stack.getComponentsPatch())
                .getOrThrow(error -> new IllegalStateException("Failed to encode components for fingerprint: " + error));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            writeLengthPrefixed(out, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().getBytes(StandardCharsets.UTF_8));
            out.writeInt(stack.getCount());
            canonicalize(patchTag, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return sha256Hex(buffer.toByteArray());
    }

    private static void writeLengthPrefixed(DataOutputStream out, byte[] bytes) throws IOException {
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    /**
     * Recursively serializes an NBT {@link Tag} into a canonical byte form: every value is
     * explicitly type-tagged and length-prefixed where variable-length (so two
     * structurally different tags can never concatenate to the same bytes), and every
     * {@link CompoundTag}'s keys are sorted lexicographically first, so key order never
     * affects the result.
     */
    private static void canonicalize(Tag tag, DataOutputStream out) throws IOException {
        switch (tag) {
            case CompoundTag compound -> {
                out.writeByte(1);
                TreeSet<String> keys = new TreeSet<>(compound.getAllKeys());
                out.writeInt(keys.size());
                for (String key : keys) {
                    writeLengthPrefixed(out, key.getBytes(StandardCharsets.UTF_8));
                    canonicalize(compound.get(key), out);
                }
            }
            case ListTag list -> {
                out.writeByte(2);
                int size = list.size();
                out.writeInt(size);
                for (int i = 0; i < size; i++) {
                    canonicalize(list.get(i), out);
                }
            }
            case StringTag string -> {
                out.writeByte(3);
                writeLengthPrefixed(out, string.getAsString().getBytes(StandardCharsets.UTF_8));
            }
            case ByteTag b -> {
                out.writeByte(4);
                out.writeByte(b.getAsByte());
            }
            case ShortTag s -> {
                out.writeByte(5);
                out.writeShort(s.getAsShort());
            }
            case IntTag i -> {
                out.writeByte(6);
                out.writeInt(i.getAsInt());
            }
            case LongTag l -> {
                out.writeByte(7);
                out.writeLong(l.getAsLong());
            }
            case FloatTag f -> {
                out.writeByte(8);
                out.writeFloat(f.getAsFloat());
            }
            case DoubleTag d -> {
                out.writeByte(9);
                out.writeDouble(d.getAsDouble());
            }
            case ByteArrayTag ba -> {
                out.writeByte(10);
                byte[] values = ba.getAsByteArray();
                out.writeInt(values.length);
                out.write(values);
            }
            case IntArrayTag ia -> {
                out.writeByte(11);
                int[] values = ia.getAsIntArray();
                out.writeInt(values.length);
                for (int value : values) {
                    out.writeInt(value);
                }
            }
            case LongArrayTag la -> {
                out.writeByte(12);
                long[] values = la.getAsLongArray();
                out.writeInt(values.length);
                for (long value : values) {
                    out.writeLong(value);
                }
            }
            default -> throw new IllegalStateException(
                    "Unsupported NBT tag type for canonical fingerprinting: " + tag.getClass());
        }
    }

    private static String sha256Hex(byte[] bytes) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
        byte[] hash = digest.digest(bytes);
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            hex.append(Character.forDigit((value >> 4) & 0xF, 16));
            hex.append(Character.forDigit(value & 0xF, 16));
        }
        return hex.toString();
    }
}
