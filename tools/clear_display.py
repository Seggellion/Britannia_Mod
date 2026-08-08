#!/usr/bin/env python3
"""
Remove a `display` block from a model so Blockbench starts from a clean slate.

Use this on models where the display values are not yours. With no display block a model inherits
minecraft:block/block's transforms, which is the same baseline Blockbench shows for a fresh model,
and whatever you then set in the Display tab is the only thing in the file.

    python tools/clear_display.py plaster_wall_blank_straight
    python tools/clear_display.py --all-plaster        # every plaster _straight
    python tools/clear_display.py --show               # print current values, change nothing

Nothing else in this project writes to `display`. This only runs when you run it.
"""
import argparse
import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mcjson  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PLASTER = os.path.join(ROOT, "src", "main", "resources", "assets", "britannia_mod",
                       "models", "block", "structure", "plaster")


def show(paths):
    for path in paths:
        with open(path, encoding="utf-8") as fh:
            gui = (json.load(fh).get("display") or {}).get("gui")
        print("  %-46s %s" % (os.path.basename(path), json.dumps(gui) if gui else "no display"))
    return 0


def clear(path):
    with open(path, encoding="utf-8") as fh:
        model = json.load(fh)
    if "display" not in model:
        return False
    model.pop("display")
    mcjson.write(path, model)
    return True


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("name", nargs="?", help="model file name without .json")
    parser.add_argument("--all-plaster", action="store_true",
                        help="every plaster _straight model")
    parser.add_argument("--show", action="store_true", help="print current gui values only")
    args = parser.parse_args()

    if args.all_plaster or (args.show and not args.name):
        paths = sorted(glob.glob(os.path.join(PLASTER, "*_straight.json")))
    elif args.name:
        paths = sorted(glob.glob(os.path.join(PLASTER, args.name + ".json")))
        if not paths:
            print("No model called '%s' in the plaster folder." % args.name)
            return 1
    else:
        parser.print_help()
        return 1

    if args.show:
        return show(paths)

    for path in paths:
        rel = os.path.relpath(path, ROOT).replace("\\", "/")
        print("  %s %s" % ("cleared " if clear(path) else "no display:", rel))
    print("\nSet these in Blockbench's Display tab, then run tools/prepare_client.py.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
