"""Ship structure-block exports into the mod's resources, in both formats it needs.

The authoring loop
------------------
    1. Build the house in the sandbox world and save it with a structure block.
       Minecraft writes a gzip-compressed .nbt into the world's generated folder.
    2. If the house is one the housing corrections apply to, run
       tools/author_house_structures.py over that folder first.
    3. Run this tool.
    4. Run the format test:  ./gradlew test --tests '*HouseStructureShippingTest*'

Why two copies
--------------
The mod reads the same structure from two different places, in two different formats, and
both are load-bearing:

    assets/britannia_mod/structures/<name>.nbt   gzip (1f 8b)
        The client's ghost preview. ClientEventHandler reads it through the client resource
        manager with NbtIo.readCompressed.

    data/britannia_mod/structures/<name>.nbt     raw NBT (0a 00)
        Server placement. StructurePlacer reads it through the server resource manager with
        NbtIo.read.

So the pipeline is: copy the export to assets/ byte for byte, and gunzip it into data/.
Copying rather than recompressing is deliberate -- gzip output depends on the compressor, and
a re-compressed file would differ from what is committed even when the structure is identical,
which would make "did this structure change?" impossible to answer from a diff.

A missing or wrongly compressed client copy does not throw in game. It shows up as a ghost
preview that silently fails to draw, which is why the format test exists.

Usage
-----
    python tools/ship_structures.py                      # default export directory
    python tools/ship_structures.py <export directory>
    python tools/ship_structures.py --check              # report, write nothing, non-zero if stale
"""
import argparse
import gzip
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt_io

DEFAULT_EXPORT_DIR = (r"C:\Users\dusti\curseforge\minecraft\Instances\UltimaCraft - Britannia"
                      r"\saves\Sandbox\generated\britannia_mod\structures")

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO_ROOT, "src", "main", "resources", "assets", "britannia_mod", "structures")
DATA = os.path.join(REPO_ROOT, "src", "main", "resources", "data", "britannia_mod", "structures")

GZIP_MAGIC = b"\x1f\x8b"
RAW_NBT_MAGIC = b"\x0a\x00"


def describe(compound):
    size = compound["size"][1][1]
    blocks = len(compound["blocks"][1][1])
    return "%dx%dx%d, %d blocks" % (size[0], size[1], size[2], blocks)


def read_if_present(path):
    return open(path, "rb").read() if os.path.exists(path) else None


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("export_dir", nargs="?", default=DEFAULT_EXPORT_DIR)
    parser.add_argument("--check", action="store_true",
                        help="report what is out of date and write nothing")
    args = parser.parse_args()

    if not os.path.isdir(args.export_dir):
        print("no such export directory: %s" % args.export_dir)
        return 1

    exports = sorted(name for name in os.listdir(args.export_dir) if name.endswith(".nbt"))
    if not exports:
        print("no .nbt exports in %s" % args.export_dir)
        return 1

    os.makedirs(ASSETS, exist_ok=True)
    os.makedirs(DATA, exist_ok=True)

    stale = 0
    for name in exports:
        source = os.path.join(args.export_dir, name)
        raw = open(source, "rb").read()

        if raw[:2] != GZIP_MAGIC:
            print("%-30s REFUSED: not a gzip export (magic %s). Structure blocks write gzip; "
                  "this looks like an already-unpacked copy." % (name, raw[:2].hex()))
            stale += 1
            continue

        try:
            plain = gzip.decompress(raw)
            _, compound = nbt_io.parse(plain)
        except Exception as unreadable:
            print("%-30s REFUSED: unreadable (%s)" % (name, unreadable))
            stale += 1
            continue

        if plain[:2] != RAW_NBT_MAGIC:
            print("%-30s REFUSED: uncompressed form does not start as an NBT compound" % name)
            stale += 1
            continue

        asset_path = os.path.join(ASSETS, name)
        data_path = os.path.join(DATA, name)
        asset_current = read_if_present(asset_path)
        data_current = read_if_present(data_path)

        if asset_current == raw and data_current == plain:
            print("%-30s up to date        (%s)" % (name, describe(compound)))
            continue

        stale += 1
        what = []
        if asset_current != raw:
            what.append("assets" if asset_current is None else "assets (changed)")
        if data_current != plain:
            what.append("data" if data_current is None else "data (changed)")
        print("%-30s %-17s (%s)" % (name, "+".join(what), describe(compound)))

        if args.check:
            continue

        with open(asset_path, "wb") as handle:
            handle.write(raw)
        with open(data_path, "wb") as handle:
            handle.write(plain)

    if args.check:
        print("\n%d of %d structures out of date." % (stale, len(exports)))
        return 1 if stale else 0

    print("\n%d structures processed, %d written." % (len(exports), stale))
    return 0


if __name__ == "__main__":
    sys.exit(main())
