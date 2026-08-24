"""Inspect and repair the shipped house structures, without opening Minecraft.

A house is not just a pile of blocks: placement reads specific things out of it, and when one of
those is missing or in the wrong place the failure is a message in chat rather than a crash. This
tool answers, for every registered house, the questions that actually decide whether it works.

    python tools/audit_house_structures.py                       # audit only; never writes
    python tools/audit_house_structures.py --strip-vegetation    # audit, then repair vegetation
    python tools/audit_house_structures.py --strip-vegetation --check   # say what it would repair

Auditing and repair are deliberately separate. The default run reads, reports and exits non-zero,
so CI can fail on a broken structure without silently rewriting a committed asset; only an explicit
`--strip-vegetation` writes anything, and only vegetation. Every other problem this reports is a
structure to fix in the sandbox and re-export -- a tool that invented a sign or moved a controller
would be guessing at architecture.

What it checks
--------------
house sign
    Exactly one `britannia_mod:house_sign` per house. The sign is the house's controller anchor:
    `HouseSignBlock.useWithoutItem` reads the `HouseLotBlockEntity` from `pos.below()`, and
    `StructurePlacer` puts the lot block there for that reason. A house with no sign has nowhere
    to put a controller; a house with two has two candidates and no rule to choose between them.

controller cell
    The cell directly beneath the sign must be air in the authored structure, because placement
    writes the lot block into it. If a house authored something there, placing the house would
    silently delete it.

vegetation
    Terrain vegetation swept up by the structure-block export. A house is a building; it should
    not stamp grass, ferns and flowers into whatever ground it lands on. The only growth a house
    is meant to introduce comes from `HouseFarmPlot`, which is a real authored block and is left
    alone -- as is anything else that is a plant by registry name but architecture by intent.

Why both copies are rewritten
-----------------------------
The mod reads each structure from two places in two formats: `assets/` gzip for the client ghost
preview, `data/` raw for server placement. `tools/ship_structures.py` documents the pipeline. An
edit applied to one and not the other is a house that previews as one shape and places as another.
"""
import argparse
import collections
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt_io

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO_ROOT, "src", "main", "resources", "assets", "britannia_mod", "structures")
DATA = os.path.join(REPO_ROOT, "src", "main", "resources", "data", "britannia_mod", "structures")

HOUSE_SIGN = "britannia_mod:house_sign"

# Terrain vegetation, by exact registry id. A list rather than a substring rule on purpose: a
# substring rule would take `britannia_mod:house_farm_plot` for holding "farm", and would take
# every future block whose name happens to contain "moss" or "leaf". Adding a block here has to
# be a decision somebody made.
PROHIBITED_VEGETATION = (
    "minecraft:short_grass",
    "minecraft:tall_grass",
    "minecraft:fern",
    "minecraft:large_fern",
    "minecraft:dead_bush",
    "minecraft:sweet_berry_bush",
    "minecraft:dandelion",
    "minecraft:poppy",
    "minecraft:blue_orchid",
    "minecraft:allium",
    "minecraft:azure_bluet",
    "minecraft:red_tulip",
    "minecraft:orange_tulip",
    "minecraft:white_tulip",
    "minecraft:pink_tulip",
    "minecraft:oxeye_daisy",
    "minecraft:cornflower",
    "minecraft:lily_of_the_valley",
    "minecraft:sunflower",
    "minecraft:lilac",
    "minecraft:rose_bush",
    "minecraft:peony",
    "minecraft:brown_mushroom",
    "minecraft:red_mushroom",
    "minecraft:seagrass",
    "minecraft:tall_seagrass",
    "britannia_mod:fern",
    "britannia_mod:managed_flower",
    "britannia_mod:blood_moss",
)

# Plants that are architecture. Named so the rule is a decision and not an omission.
INTENTIONAL_PLANTS = (
    "britannia_mod:house_farm_plot",
    "britannia_mod:hedge_bush",
)


def as_list(tag):
    """The payload of a TAG_List, which nbt_io models as (tag_id, (element_id, [payload, ...]))."""
    return tag[1][1]


def load(path):
    name, compound, gzipped = nbt_io.read_file(path)
    return name, compound, gzipped


def palette_names(compound):
    return [entry["Name"][1] for entry in as_list(compound["palette"])]


def cells(compound):
    """position tuple -> (block name, block entry) for every authored cell."""
    names = palette_names(compound)
    found = {}
    for entry in as_list(compound["blocks"]):
        found[tuple(as_list(entry["pos"]))] = (names[entry["state"][1]], entry)
    return found


def audit(compound):
    """Everything that decides whether this structure works, as data. Never writes, never prints."""
    names = palette_names(compound)
    size = as_list(compound["size"])
    entries = as_list(compound["blocks"])

    grid = {}
    duplicates = []
    out_of_range = []
    outside_size = []
    for entry in entries:
        index = entry["state"][1]
        if index < 0 or index >= len(names):
            out_of_range.append(index)
            continue
        position = tuple(as_list(entry["pos"]))
        if position in grid:
            duplicates.append(position)
        grid[position] = (names[index], entry)
        if len(size) == 3 and any(
                position[axis] < 0 or position[axis] >= size[axis] for axis in range(3)):
            outside_size.append(position)

    signs = sorted(position for position, (name, _) in grid.items() if name == HOUSE_SIGN)
    vegetation = collections.Counter(
        name for name, _ in grid.values() if name in PROHIBITED_VEGETATION)
    controller = None
    controller_block = None
    if len(signs) == 1:
        controller = (signs[0][0], signs[0][1] - 1, signs[0][2])
        controller_block = grid.get(controller, ("<outside the structure>", None))[0]

    problems = []
    # Shape first: a malformed size or palette makes every later answer meaningless rather than
    # merely wrong, so it is reported on its own terms.
    if len(size) != 3 or any(axis <= 0 for axis in size):
        problems.append("size is %s; a structure needs three positive dimensions" % (size,))
    if not names:
        problems.append("empty palette; the structure describes no blocks at all")
    if out_of_range:
        problems.append("%d cells index outside the palette (%s)"
                        % (len(out_of_range), sorted(set(out_of_range))[:5]))
    if duplicates:
        problems.append("%d positions are authored twice (%s)"
                        % (len(duplicates), duplicates[:3]))
    if outside_size:
        problems.append("%d cells fall outside the declared size (%s)"
                        % (len(outside_size), outside_size[:3]))

    if len(signs) != 1:
        problems.append("%d house signs; exactly one is required, because the sign is where the "
                        "controller goes" % len(signs))
    elif controller_block != "minecraft:air":
        problems.append("the controller cell %s holds %s; placement writes the lot block there and "
                        "would destroy it" % (controller, controller_block))

    if vegetation:
        problems.append("terrain vegetation: %s" % dict(vegetation))

    return {
        "size": size,
        "blocks": len(entries),
        "signs": signs,
        "controller": controller,
        "controller_block": controller_block,
        "vegetation": vegetation,
        "problems": problems,
    }


def strip_vegetation(compound):
    """Replace every prohibited vegetation cell with air. Returns how many were replaced.

    The palette is left exactly as it is. An unused palette entry costs a handful of bytes and
    changes no index, whereas compacting it would renumber every `state` in the file -- a far
    larger edit, and one whose correctness is much harder to see in a diff.
    """
    names = palette_names(compound)
    air_index = None
    for index, name in enumerate(names):
        if name == "minecraft:air":
            air_index = index
            break
    if air_index is None:
        # No air in the palette: append one rather than inventing a different filler.
        compound["palette"][1][1].append({"Name": (nbt_io.TAG_STRING, "minecraft:air")})
        air_index = len(names)

    replaced = 0
    for entry in as_list(compound["blocks"]):
        if names[entry["state"][1]] in PROHIBITED_VEGETATION:
            entry["state"] = (nbt_io.TAG_INT, air_index)
            # A vegetation cell never carries block-entity data, but if a future one did, carrying
            # it onto an air cell would be a corrupt structure rather than a tidy one.
            entry.pop("nbt", None)
            replaced += 1
    return replaced


def java_gzip(plain):
    """Exactly what java.util.zip.GZIPOutputStream writes, which is what Minecraft writes.

    Python's gzip module records the source filename and an OS byte; Java records neither, and
    `HouseStructureShippingTest` compares the committed client copy against Java's own output. So
    the header is written by hand -- ten fixed bytes, no FNAME flag, zero MTIME, zero OS -- around
    a raw deflate stream at zlib's default level, which is the level Java's Deflater defaults to.
    """
    # OS byte 0xFF (unknown). JDK 16 changed GZIPOutputStream from 0x00 to this, and the
    # committed exports carry it -- see HouseStructureShippingTest, which compares the two.
    header = bytes([0x1F, 0x8B, 0x08, 0x00, 0, 0, 0, 0, 0x00, 0xFF])
    compressor = zlib.compressobj(zlib.Z_DEFAULT_COMPRESSION, zlib.DEFLATED, -zlib.MAX_WBITS)
    body = compressor.compress(plain) + compressor.flush()
    trailer = struct.pack("<II", zlib.crc32(plain) & 0xFFFFFFFF, len(plain) & 0xFFFFFFFF)
    return header + body + trailer


def write_pair(stub, compound, root_name):
    plain = nbt_io.serialise(root_name, compound)
    with open(os.path.join(DATA, stub + ".nbt"), "wb") as handle:
        handle.write(plain)
    with open(os.path.join(ASSETS, stub + ".nbt"), "wb") as handle:
        handle.write(java_gzip(plain))


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--strip-vegetation", action="store_true",
                        help="REPAIR: replace prohibited vegetation cells with air, in both copies")
    parser.add_argument("--check", action="store_true",
                        help="with --strip-vegetation, report what would be repaired and write nothing")
    args = parser.parse_args()

    # Auditing and repairing are separated so CI can fail on a bad structure without rewriting a
    # committed asset behind anyone's back. Nothing below writes unless --strip-vegetation was
    # asked for explicitly and --check was not.
    repairing = args.strip_vegetation and not args.check

    stubs = sorted(name[:-4] for name in os.listdir(DATA) if name.endswith(".nbt"))
    failing = 0
    repaired = 0

    for stub in stubs:
        root_name, compound, _ = load(os.path.join(DATA, stub + ".nbt"))
        report = audit(compound)

        print("== %-24s %-14s %6d blocks" % (
            stub, "x".join(str(value) for value in report["size"]), report["blocks"]))
        if len(report["signs"]) == 1:
            print("     sign     %s -> controller %s (%s)"
                  % (report["signs"][0], report["controller"], report["controller_block"]))

        for problem in report["problems"]:
            print("     PROBLEM  %s" % problem)

        if report["vegetation"] and args.strip_vegetation:
            if repairing:
                replaced = strip_vegetation(compound)
                write_pair(stub, compound, root_name)
                repaired += replaced
                print("     repaired %d vegetation cells and rewrote both copies" % replaced)
                # Re-read what was actually written rather than trusting the in-memory edit.
                report = audit(load(os.path.join(DATA, stub + ".nbt"))[1])
                for problem in report["problems"]:
                    print("     STILL    %s" % problem)
            else:
                print("     would repair %d vegetation cells"
                      % sum(report["vegetation"].values()))

        if report["problems"]:
            failing += 1

    print("\n%d house%s inspected, %d still failing%s"
          % (len(stubs), "" if len(stubs) == 1 else "s", failing,
             ", %d cells repaired" % repaired if repaired else ""))
    if not repairing and failing:
        print("Nothing was written. Repair vegetation with --strip-vegetation; anything else is a "
              "structure to fix in the sandbox and re-export.")
    return 1 if failing else 0


if __name__ == "__main__":
    sys.exit(main())
