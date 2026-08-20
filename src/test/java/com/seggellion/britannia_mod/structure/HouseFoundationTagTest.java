package com.seggellion.britannia_mod.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 5, first task: the two tags that say which part of a house is whose.
 *
 * <pre>
 *   britannia_mod:house_foundation        the exterior perimeter -- nobody breaks it
 *   britannia_mod:house_floor_foundation  the interior ground slab -- the owner cuts a basement
 *                                         through it
 * </pre>
 *
 * <p>The split cannot be made from the registry names. Every block in either tag has
 * "foundation" in its name, and the one that most looks like a perimeter block --
 * {@code brick_foundation_spruce} -- is the six small houses' interior floor, laid 49 at a time
 * as a 7x7 slab. Getting it backwards would protect the floor a player is supposed to dig
 * through and leave the walls of their house breakable.
 *
 * <p>So membership is measured against the authored structures, and this is where that
 * measurement is enforced: a perimeter block never appears at y=0, and a slab block appears
 * nowhere else.
 *
 * <p>The tags carry no behaviour yet. The protection rework that reads them is the rest of
 * Milestone 5 and waits on the creative-mode decision.
 */
class HouseFoundationTagTest {

    private static final Path ROOT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path TAGS = ROOT.resolve("src/main/resources/data/britannia_mod/tags/block");
    private static final Path STRUCTURES = ROOT.resolve("src/main/resources/data/britannia_mod/structures");

    @Test
    void everyPerimeterBlockIsAWallBaseInEveryHouseThatUsesIt() throws IOException {
        Set<String> perimeter = tag("house_foundation");
        for (HouseStyle style : HouseStyle.values()) {
            for (var entry : foundationLayers(style).entrySet()) {
                if (!perimeter.contains(entry.getKey())) continue;
                assertTrue(entry.getValue().stream().allMatch(y -> y >= 1),
                        style + " lays " + entry.getKey() + " at " + entry.getValue() + ", including "
                                + "the floor. It is tagged as exterior perimeter, so protecting it "
                                + "would protect part of a house's floor.");
            }
        }
    }

    @Test
    void everyFloorSlabBlockIsAGroundFloorInEveryHouseThatUsesIt() throws IOException {
        Set<String> slab = tag("house_floor_foundation");
        for (HouseStyle style : HouseStyle.values()) {
            for (var entry : foundationLayers(style).entrySet()) {
                if (!slab.contains(entry.getKey())) continue;

                // The castle has a single stray stone_foundation ten blocks up, alone. It is an
                // authoring stray rather than a wall course, and it is named here so that a real
                // second use of a slab block as a wall fails this instead of hiding behind it.
                Set<Integer> aboveGround = new TreeSet<>(entry.getValue());
                aboveGround.remove(0);
                boolean castleStray = style == HouseStyle.CASTLE
                        && entry.getKey().equals("britannia_mod:stone_foundation")
                        && aboveGround.equals(Set.of(10));
                assertTrue(aboveGround.isEmpty() || castleStray,
                        style + " lays " + entry.getKey() + " at " + entry.getValue() + ". It is "
                                + "tagged as an interior ground slab, which the owner may break, so "
                                + "using it up a wall would make that wall breakable.");
            }
        }
    }

    @Test
    void thetwoTagsDoNotOverlap() throws IOException {
        Set<String> both = new LinkedHashSet<>(tag("house_foundation"));
        both.retainAll(tag("house_floor_foundation"));
        assertTrue(both.isEmpty(),
                "a block cannot be both protected and the thing an owner digs through: " + both);
    }

    @Test
    void everyFoundationBlockUsedByAHouseIsClassified() throws IOException {
        Set<String> classified = new LinkedHashSet<>(tag("house_foundation"));
        classified.addAll(tag("house_floor_foundation"));

        Set<String> unclassified = new TreeSet<>();
        for (HouseStyle style : HouseStyle.values()) {
            for (String block : foundationLayers(style).keySet()) {
                if (!classified.contains(block)) unclassified.add(block);
            }
        }
        assertTrue(unclassified.isEmpty(),
                "these foundation blocks are in shipped houses but in neither tag, so the "
                        + "protection rules will have no opinion about them: " + unclassified);
    }

    @Test
    void theSmallHousesInteriorFloorIsClassifiedAsFloorAndNotAsPerimeter() throws IOException {
        String slabBlock = "britannia_mod:brick_foundation_spruce";
        assertTrue(tag("house_floor_foundation").contains(slabBlock),
                slabBlock + " is the six small houses' interior floor; if it is not in the floor "
                        + "tag those houses have no way down into a basement");
        assertTrue(!tag("house_foundation").contains(slabBlock),
                slabBlock + " must not be treated as exterior perimeter -- its name says foundation, "
                        + "its use says floor, and the structures are the authority");
    }

    @Test
    void theNewFloorFoundationIsClassifiedWithItsFamily() throws IOException {
        assertTrue(tag("house_floor_foundation").contains("britannia_mod:wooden_board_floor_foundation"),
                "the block created precisely to be an interior structural floor is not tagged as one");
    }

    /* ------------------------------------------------------------------ */

    private static Set<String> tag(String name) throws IOException {
        Path path = TAGS.resolve(name + ".json");
        assertTrue(Files.exists(path), "missing tag: " + path
                + " (note: tags/block, singular -- the plural folder is vestigial and ignored)");

        JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        JsonArray values = json.getAsJsonArray("values");
        Set<String> blocks = new LinkedHashSet<>();
        for (JsonElement value : values) blocks.add(value.getAsString());
        assertEquals(values.size(), blocks.size(), name + " lists a block twice");
        return blocks;
    }

    private static TreeMap<String, Set<Integer>> foundationLayers(HouseStyle style) throws IOException {
        CompoundTag structure = read(style);
        ListTag palette = structure.getList("palette", Tag.TAG_COMPOUND);
        String[] names = new String[palette.size()];
        for (int index = 0; index < palette.size(); index++) {
            names[index] = palette.getCompound(index).getString("Name");
        }

        TreeMap<String, Set<Integer>> layers = new TreeMap<>();
        ListTag blocks = structure.getList("blocks", Tag.TAG_COMPOUND);
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompound(index);
            String name = names[block.getInt("state")];
            if (!name.contains("foundation")) continue;
            layers.computeIfAbsent(name, ignored -> new TreeSet<>())
                    .add(block.getList("pos", Tag.TAG_INT).getInt(1));
        }
        return layers;
    }

    private static CompoundTag read(HouseStyle style) throws IOException {
        try (InputStream in = Files.newInputStream(STRUCTURES.resolve(style.getStructureFile()));
             DataInputStream data = new DataInputStream(in)) {
            return NbtIo.read(data);
        }
    }
}
