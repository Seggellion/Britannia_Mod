#!/usr/bin/env python3
"""
Repair the things Blockbench drops or cannot express when it re-saves a hand-authored model.

Blockbench round-trips a model on every save. It does not know about NeoForge's `render_type`, it
has no notion of Minecraft's ambient-occlusion problem with oversized models, and it happily
authors zero-thickness planes with a face on both sides. So each of the following silently comes
back every time a model is re-exported, which is why a fix can look like it "did not take":

  render_type        a model sampling the transparent `ornateness` sheet must be on the cutout
                     layer, or Minecraft ignores alpha and its transparent-BLACK texels render as
                     opaque black panels.
  ambientocclusion   a model leaving the 0..16 cube gets its smooth lighting extrapolated past the
                     block bounds and darkens to black. Flat lighting is the fix.
  flat panes         a zero-thickness element declaring both faces of its flat axis renders two
                     quads in the same plane, which always z-fights. They get 0.2 voxels of
                     thickness, centred so the pane does not visibly move.

This is idempotent: run it as often as you like. It runs automatically as the first step of
tools/build_assets.py, so the normal workflow is "edit in Blockbench, run build_assets".
"""
import collections
import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir))
import mcjson  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "britannia_mod")

# Hand-authored sources. Generated files inherit whatever these declare, so fixing them here is
# enough - never edit a generated file.
SOURCES = [
    os.path.join(ASSETS, "models", "block", "structure", "plaster", "*_straight.json"),
    os.path.join(ASSETS, "models", "block", "structure", "sandstone", "*_straight.json"),
    os.path.join(ASSETS, "models", "block", "structure", "plaster", "plaster_wall_joist_edge.json"),
    os.path.join(ASSETS, "models", "block", "structure", "bannister.json"),
    os.path.join(ASSETS, "models", "block", "structure", "plaster_wood_post.json"),
    os.path.join(ASSETS, "models", "block", "structure", "villa_lamp_post.json"),
    os.path.join(ASSETS, "models", "block", "structure", "house_farm_plot.json"),
]

ALPHA_TEXTURES = ("ornateness",)
FLAT_AXIS_FACES = (("west", "east"), ("down", "up"), ("north", "south"))
PANE_THICKNESS = 0.2


def needs_cutout(model):
    return any(isinstance(v, str) and any(t in v for t in ALPHA_TEXTURES)
               for v in (model.get("textures") or {}).values())


def oversized(model):
    return any(c < 0 or c > 16
               for el in model.get("elements", [])
               for c in (el["from"] + el["to"]))


def normalize(path):
    with open(path, encoding="utf-8") as fh:
        model = json.load(fh, object_pairs_hook=collections.OrderedDict)

    fixes = []

    if needs_cutout(model) and model.get("render_type") != "minecraft:cutout":
        model["render_type"] = "minecraft:cutout"
        fixes.append("render_type=cutout")

    if oversized(model) and model.get("ambientocclusion") is not False:
        model["ambientocclusion"] = False
        fixes.append("ambientocclusion=false")

    panes = 0
    for el in model.get("elements", []):
        for axis, (low, high) in enumerate(FLAT_AXIS_FACES):
            if abs(el["to"][axis] - el["from"][axis]) > 1e-9:
                continue
            faces = el.get("faces", {})
            if low in faces and high in faces:
                middle = el["from"][axis]
                el["from"][axis] = round(middle - PANE_THICKNESS / 2, 4)
                el["to"][axis] = round(middle + PANE_THICKNESS / 2, 4)
                panes += 1
            break
    if panes:
        fixes.append("%d flat pane(s) thickened" % panes)

    # display / inventory transforms are never touched. Those are managed in Blockbench.

    if not fixes:
        return None

    # Keep the header keys in Minecraft's usual order so the diff stays readable.
    ordered = collections.OrderedDict()
    for key in ("format_version", "credit", "parent", "ambientocclusion", "render_type",
                "texture_size", "textures"):
        if key in model:
            ordered[key] = model.pop(key)
    for key, value in model.items():
        ordered[key] = value

    mcjson.write(path, ordered)
    return fixes


def main():
    touched = 0
    for pattern in SOURCES:
        for path in sorted(glob.glob(pattern)):
            fixes = normalize(path)
            if fixes:
                touched += 1
                print("  %-44s %s" % (os.path.basename(path), "; ".join(fixes)))
    print("normalized %d source model(s)" % touched)
    return 0


if __name__ == "__main__":
    sys.exit(main())
