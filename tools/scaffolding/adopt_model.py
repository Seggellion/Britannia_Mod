#!/usr/bin/env python3
"""
Take ownership of a generated model so you can hand-author it in Blockbench.

Removing the GENERATED banner is all it takes: the generators skip any model that does not carry
one. Use this when you want to draw a corner or junction yourself instead of having it composed
from the _straight.

    python tools/adopt_model.py plaster_ornate_wall_upper_corner
    python tools/adopt_model.py --all plaster_ornate_wall_upper   # every variant of one family
    python tools/adopt_model.py --list                            # what is still generated

The reverse is just deleting the file and re-running tools/build_assets.py.
"""
import argparse
import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir))
import mcjson  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MODELS = os.path.join(ROOT, "src", "main", "resources", "assets", "britannia_mod", "models")


def candidates(name, every):
    """
    Exact match by default. With --all, every block model whose name starts with `name` - but not
    item models: those carry the inventory transform and must stay generated, or the icon scale
    goes back to overflowing its slot.
    """
    if not every:
        return sorted(glob.glob(os.path.join(MODELS, "**", name + ".json"), recursive=True))
    hits = sorted(glob.glob(os.path.join(MODELS, "**", name + "*.json"), recursive=True))
    return [p for p in hits if os.sep + "item" + os.sep not in p]


def adopt(path):
    with open(path, encoding="utf-8") as fh:
        model = json.load(fh)
    if "GENERATED FILE" not in (model.get("comment") or ""):
        return False
    model.pop("comment", None)
    mcjson.write(path, model)
    return True


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("name", nargs="?", help="model name, e.g. plaster_ornate_wall_upper_corner")
    parser.add_argument("--all", action="store_true", help="adopt every variant matching the name")
    parser.add_argument("--list", action="store_true", help="list models still generated")
    args = parser.parse_args()

    if args.list or not args.name:
        count = 0
        for path in sorted(glob.glob(os.path.join(MODELS, "**", "*.json"), recursive=True)):
            try:
                with open(path, encoding="utf-8") as fh:
                    if "GENERATED FILE" in (json.load(fh).get("comment") or ""):
                        print("  " + os.path.relpath(path, ROOT).replace("\\", "/"))
                        count += 1
            except (ValueError, OSError):
                pass
        print("%d model(s) still generated - any of these can be adopted." % count)
        return 0

    paths = candidates(args.name, args.all)
    if not paths:
        print("No model matching '%s'." % args.name)
        return 1

    for path in paths:
        rel = os.path.relpath(path, ROOT).replace("\\", "/")
        print("  %s %s" % ("adopted " if adopt(path) else "already yours:", rel))
    print("\nThese are yours now - build_assets.py will leave them alone.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
