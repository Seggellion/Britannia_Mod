#!/usr/bin/env python3
"""
Generate the anti-repetition variant set for vanilla terrain blocks.

Vanilla renders a full cube through `block/cube_all`, so every face samples the same sprite.
Blockstate `y` rotation therefore only re-orients the top and bottom faces -- the four sides are
visually identical before and after, which is why vanilla sand (four `y` rotations of one texture)
looks varied from above and completely uniform on a cut face.

Instead we keep the geometry fixed and vary the UVs. Each of the eight shared parents under
`britannia_mod:block/world/cube_all_*` declares the same 0..16 cube with one member of the
dihedral group of the square applied identically to all six faces:

    r0 r90 r180 r270      face `rotation`, uv [0, 0, 16, 16]
    m0 m90 m180 m270      face `rotation`, uv [16, 0, 0, 16]  (U reversed -- the same mirroring
                                                               vanilla itself uses in cube_mirrored)

That is the complete set: a V-mirror is not a ninth orientation, it is a U-mirror composed with
rotation 180. Because the transform is uniform across all six faces, every face direction gets the
full variant count -- floors, ceilings and all four wall orientations vary equally.

Each block then contributes `textures x 8` leaf models and an equally weighted blockstate. Minecraft
picks between them with `WeightedBakedModel`, seeded from `Mth.getSeed(pos)`, so selection is
deterministic per position and stable across chunk reloads.

Textures live in the mod namespace so the vanilla PNG is left alone; only the blockstate and the
item model are overridden in `assets/minecraft`.

Usage:  python tools/generate_terrain_variants.py [--check]

`--check` regenerates into memory and fails if anything on disk differs, so an existing block can be
proven untouched when a new one is added.
"""
import argparse
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets")

FACES = ["down", "up", "north", "south", "west", "east"]
IDENTITY_UV = "[ 0, 0, 16, 16 ]"
MIRRORED_UV = "[ 16, 0, 0, 16 ]"

# (suffix, uv, face rotation) -- the eight elements of the dihedral group of the square.
TRANSFORMS = [("r%d" % r, IDENTITY_UV, r) for r in (0, 90, 180, 270)] \
           + [("m%d" % r, MIRRORED_UV, r) for r in (0, 90, 180, 270)]

# Vanilla blocks whose appearance we replace. `texture` names the mod sprites
# (block/world/<texture><n>.png) and `leaf` prefixes the generated models.
BLOCKS = [
    {"block": "stone", "texture": "stone", "leaf": "stone_block", "count": 4},
    {"block": "sand", "texture": "sand", "leaf": "sand_block", "count": 4},
]

PARENT_DIR = os.path.join(ASSETS, "britannia_mod", "models", "block", "world")
LEAF_DIR = os.path.join(ASSETS, "minecraft", "models", "block")
BLOCKSTATE_DIR = os.path.join(ASSETS, "minecraft", "blockstates")
ITEM_DIR = os.path.join(ASSETS, "minecraft", "models", "item")


def parent_model(uv, rotation):
    rot = ', "rotation": %d' % rotation if rotation else ""
    faces = ',\n'.join(
        '        "%s":%s{ "uv": %s, "texture": "#all", "cullface": "%s"%s }'
        % (f, " " * (6 - len(f)), uv, f, rot) for f in FACES)
    return ('{\n'
            '  "parent": "minecraft:block/block",\n'
            '  "textures": { "particle": "#all" },\n'
            '  "elements": [\n'
            '    { "from": [ 0, 0, 0 ],\n'
            '      "to":   [ 16, 16, 16 ],\n'
            '      "faces": {\n'
            '%s\n'
            '      }\n'
            '    }\n'
            '  ]\n'
            '}\n' % faces)


def leaf_model(suffix, texture, index):
    return ('{\n'
            '  "parent": "britannia_mod:block/world/cube_all_%s",\n'
            '  "textures": {\n'
            '    "all": "britannia_mod:block/world/%s%d"\n'
            '  }\n'
            '}\n' % (suffix, texture, index))


def blockstate(models):
    body = ',\n'.join('      { "model": "minecraft:block/%s" }' % m for m in models)
    return '{\n  "variants": {\n    "": [\n%s\n    ]\n  }\n}\n' % body


def item_model(model):
    return '{\n  "parent": "minecraft:block/%s"\n}\n' % model


def build():
    """Return {absolute path: file text} for the whole generated set."""
    out = {}
    for suffix, uv, rotation in TRANSFORMS:
        out[os.path.join(PARENT_DIR, "cube_all_%s.json" % suffix)] = parent_model(uv, rotation)

    for spec in BLOCKS:
        models = []
        for index in range(1, spec["count"] + 1):
            for suffix, _uv, _rot in TRANSFORMS:
                name = "%s%d_%s" % (spec["leaf"], index, suffix)
                out[os.path.join(LEAF_DIR, "%s.json" % name)] = \
                    leaf_model(suffix, spec["texture"], index)
                models.append(name)
        out[os.path.join(BLOCKSTATE_DIR, "%s.json" % spec["block"])] = blockstate(models)
        out[os.path.join(ITEM_DIR, "%s.json" % spec["block"])] = item_model(models[0])
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true",
                    help="fail if the tree differs from what would be generated")
    args = ap.parse_args()

    files = build()
    drift, written = [], 0
    for path, text in sorted(files.items()):
        current = None
        if os.path.exists(path):
            # Universal newlines on read: core.autocrlf checks these out as CRLF on Windows, and a
            # line-ending difference is not drift. Writes below always emit LF.
            with open(path) as fh:
                current = fh.read()
        if current == text:
            continue
        if args.check:
            drift.append(os.path.relpath(path, ROOT).replace(os.sep, "/"))
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", newline="\n") as fh:
            fh.write(text)
        written += 1

    if args.check:
        for path in drift:
            print("DRIFT %s" % path)
        print("%d file(s) checked, %d differ" % (len(files), len(drift)))
        return 1 if drift else 0

    print("%d file(s) generated, %d written, %d already current"
          % (len(files), written, len(files) - written))
    for spec in BLOCKS:
        print("  minecraft:%-6s %d textures x %d orientations = %d variants"
              % (spec["block"], spec["count"], len(TRANSFORMS),
                 spec["count"] * len(TRANSFORMS)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
