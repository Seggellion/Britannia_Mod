#!/usr/bin/env python3
"""
Answer "I changed a model and nothing happened - which file does this block actually use?"

Two things make that non-obvious for the architectural blocks:

  1. Most junction models are GENERATED from a _straight. Editing the generated file works until
     the next generator run overwrites it, and editing the straight changes nothing until you DO
     run the generator.
  2. Which model you see depends on the block state. A wall standing in a corner renders
     _corner, not _straight; a fully surrounded joist floor renders _enclosed; a window whose side
     has been toggled renders _window_right.

Usage:
    python tools/which_model.py wood_support_floor
    python tools/which_model.py plaster_wall_blank --state shape=corner
    python tools/which_model.py --generated        # list every generated file in the tree
"""
import argparse
import glob
import json
import os
import sys
from collections import OrderedDict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "britannia_mod")

GENERATED_MARK = "GENERATED FILE"


def model_file(ref):
    ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
    if ns != "britannia_mod":
        return None
    return os.path.join(ASSETS, "models", *path.split("/")) + ".json"


def is_generated(path):
    if not path or not os.path.exists(path):
        return False
    try:
        with open(path, encoding="utf-8") as fh:
            return GENERATED_MARK in (json.load(fh).get("comment") or "")
    except (ValueError, OSError):
        return False


def source_of(path):
    """The model a generated file was built from, taken out of its banner."""
    try:
        with open(path, encoding="utf-8") as fh:
            comment = json.load(fh).get("comment") or ""
    except (ValueError, OSError):
        return None
    if " from " not in comment:
        return None
    return comment.split(" from ", 1)[1].split(".json")[0] + ".json"


def report_block(name, filters):
    path = os.path.join(ASSETS, "blockstates", name + ".json")
    if not os.path.exists(path):
        print("No blockstate called '%s'." % name)
        return 1

    with open(path, encoding="utf-8") as fh:
        data = json.load(fh)
    if "multipart" in data:
        print("%s uses multipart; inspect %s by hand." % (name, path))
        return 0

    grouped = OrderedDict()
    for key, entry in data.get("variants", {}).items():
        props = [p for p in key.split(",") if p]
        if any(f not in props for f in filters):
            continue
        ref = entry["model"] if isinstance(entry, dict) else entry[0]["model"]
        grouped.setdefault(ref, []).append(key or "<no properties>")

    if not grouped:
        print("No states of %s match %s" % (name, ", ".join(filters)))
        return 1

    print("%s -> %d distinct model(s)\n" % (name, len(grouped)))
    for ref, states in grouped.items():
        target = model_file(ref)
        # model_file returns None for vanilla refs like minecraft:block/air. Those live in the
        # game jar, not this repo, and are always present - reporting them missing was a bug.
        vanilla = target is None
        exists = vanilla or os.path.exists(target)
        rel = ref if vanilla else os.path.relpath(target, ROOT).replace("\\", "/")

        if not exists:
            tag = "  [MISSING - renders purple/black]"
        elif vanilla:
            tag = "  [vanilla model - nothing to edit]"
        elif is_generated(target):
            tag = "  [GENERATED - edit %s and re-run the generator]" % (source_of(target) or "its source")
        else:
            tag = "  [source - edit this file directly]"

        print("  %s%s" % (rel, tag))
        print("      %d state(s), e.g. %s" % (len(states), states[0]))
    return 0


def list_generated():
    hits = []
    for path in glob.glob(os.path.join(ASSETS, "models", "**", "*.json"), recursive=True):
        if is_generated(path):
            hits.append((os.path.relpath(path, ROOT).replace("\\", "/"), source_of(path)))
    print("%d generated model(s) - edits to these are overwritten:\n" % len(hits))
    for rel, src in sorted(hits):
        print("  %-96s  from %s" % (rel, src))
    return 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("block", nargs="?", help="blockstate name, e.g. wood_support_floor")
    parser.add_argument("--state", action="append", default=[],
                        help="only states carrying this property, e.g. --state shape=corner")
    parser.add_argument("--generated", action="store_true", help="list every generated model")
    args = parser.parse_args()

    if args.generated:
        return list_generated()
    if not args.block:
        parser.print_help()
        return 1
    return report_block(args.block, args.state)


if __name__ == "__main__":
    sys.exit(main())
