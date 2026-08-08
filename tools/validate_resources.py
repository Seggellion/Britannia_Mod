#!/usr/bin/env python3
"""
Resource validator for britannia_mod.

The project has no datagen and no test source set, so this stands in for both: it walks the
assets tree the way the client's model bakery does and fails on anything that would show up as a
missing-model fallback, a stretched texture or a z-fighting surface in game.

Checks
  1. every JSON under assets/ parses
  2. every blockstate variant references a model that exists, case-sensitively
  3. every model's parent and textures resolve, case-sensitively
  4. every declared blockstate property combination has a variant (no missing-model states)
  5. no element declares a zero-thickness box with faces on both sides of it (guaranteed z-fight)
  6. no two elements in a model declare identical coplanar overlapping faces
  7. elements stay inside Minecraft's legal -16..32 model range

Usage:  python tools/validate_resources.py [--states states.json] [--scope SUBSTR ...]

`--states` takes a JSON map of {blockstate name: [list of property=value strings]} so check 4 can
run without a live game. Generate it from the Java registry, or omit it to skip that check.

`--scope` limits reporting to paths containing any of the given substrings. The mod has a large
backlog of pre-existing resource breakage elsewhere, so gate new work with a scope rather than
trying to get the whole tree to zero in one go.
"""
import argparse
import itertools
import json
import os
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "britannia_mod")
NAMESPACE = "britannia_mod"

MIN_COORD, MAX_COORD = -16.0, 32.0


class Report:
    def __init__(self, scope=None):
        self.scope = scope or []
        self.errors = []
        self.warnings = []
        self.suppressed = 0

    def _in_scope(self, where):
        return not self.scope or any(fragment in where for fragment in self.scope)

    def error(self, where, message):
        if self._in_scope(where):
            self.errors.append("%s: %s" % (where, message))
        else:
            self.suppressed += 1

    def warn(self, where, message):
        if self._in_scope(where):
            self.warnings.append("%s: %s" % (where, message))
        else:
            self.suppressed += 1


def rel(path):
    return os.path.relpath(path, ROOT).replace("\\", "/")


def split_ref(ref):
    if ":" in ref:
        return ref.split(":", 1)
    return "minecraft", ref


def exists_case_sensitive(path):
    """os.path.exists is case-insensitive on Windows; resource loading in game is not."""
    if not os.path.exists(path):
        return False
    current = os.path.abspath(path)
    while True:
        parent, name = os.path.split(current)
        if parent == current:
            return True
        try:
            if name not in os.listdir(parent):
                return False
        except OSError:
            return False
        current = parent
        if os.path.normcase(current) == os.path.normcase(ROOT):
            return True


def model_path(ref):
    ns, path = split_ref(ref)
    if ns != NAMESPACE:
        return None  # vanilla, assumed present
    return os.path.join(ASSETS, "models", *path.split("/")) + ".json"


def texture_path(ref):
    ns, path = split_ref(ref)
    if ns != NAMESPACE:
        return None
    return os.path.join(ASSETS, "textures", *path.split("/")) + ".png"


def load_json(path, report):
    try:
        with open(path, encoding="utf-8") as fh:
            return json.load(fh)
    except ValueError as exc:
        report.error(rel(path), "does not parse: %s" % exc)
        return None


# ─── checks ──────────────────────────────────────────────────────────────────

def check_blockstates(report):
    directory = os.path.join(ASSETS, "blockstates")
    seen = {}
    for name in sorted(os.listdir(directory)):
        if not name.endswith(".json"):
            continue
        path = os.path.join(directory, name)
        data = load_json(path, report)
        if data is None:
            continue
        refs = []

        def walk(node):
            if isinstance(node, dict):
                if isinstance(node.get("model"), str):
                    refs.append(node["model"])
                for value in node.values():
                    walk(value)
            elif isinstance(node, list):
                for value in node:
                    walk(value)

        walk(data)
        for ref in sorted(set(refs)):
            target = model_path(ref)
            if target and not exists_case_sensitive(target):
                report.error(rel(path), "references missing model '%s'" % ref)
        seen[name[:-5]] = data
    return seen


def check_models(report):
    root = os.path.join(ASSETS, "models")
    for base, _, files in os.walk(root):
        for name in sorted(files):
            if not name.endswith(".json"):
                continue
            path = os.path.join(base, name)
            data = load_json(path, report)
            if data is None:
                continue

            parent = data.get("parent")
            if isinstance(parent, str):
                target = model_path(parent)
                if target and not exists_case_sensitive(target):
                    report.error(rel(path), "parent model missing: '%s'" % parent)

            for key, value in (data.get("textures") or {}).items():
                if not isinstance(value, str) or value.startswith("#"):
                    continue
                target = texture_path(value)
                if target and not exists_case_sensitive(target):
                    report.error(rel(path), "texture '%s' -> missing '%s'" % (key, value))

            check_geometry(path, data, report)


def check_geometry(path, data, report):
    elements = data.get("elements")
    if not elements:
        return

    quads = defaultdict(list)
    for index, element in enumerate(elements):
        try:
            lo = [float(v) for v in element["from"]]
            hi = [float(v) for v in element["to"]]
        except (KeyError, TypeError, ValueError):
            report.error(rel(path), "element %d has a malformed from/to" % index)
            continue

        for axis, (a, b) in enumerate(zip(lo, hi)):
            if b < a:
                report.error(rel(path), "element %d has to < from on axis %d" % (index, axis))
            if a < MIN_COORD or b > MAX_COORD:
                report.error(rel(path),
                             "element %d leaves the legal -16..32 range on axis %d (%g..%g)"
                             % (index, axis, a, b))

        faces = element.get("faces") or {}

        # A zero-thickness element with faces on both sides of the flat axis renders two quads in
        # exactly the same plane, which always z-fights.
        for axis, pair in ((0, ("west", "east")), (1, ("down", "up")), (2, ("north", "south"))):
            if abs(hi[axis] - lo[axis]) < 1e-9 and pair[0] in faces and pair[1] in faces:
                report.error(rel(path),
                             "element %d is flat on axis %d but declares both '%s' and '%s' "
                             "- those quads are coplanar and will z-fight" % (index, axis, *pair))

        for face, spec in faces.items():
            uv = spec.get("uv")
            if uv is not None and len(uv) != 4:
                report.error(rel(path), "element %d face '%s' has a malformed uv" % (index, face))
            key = quad_key(face, lo, hi)
            if key is not None:
                quads[key].append(index)

    for (face, plane, extent), indices in quads.items():
        if len(indices) > 1:
            report.warn(rel(path),
                        "elements %s all declare a '%s' face at %g over the same footprint "
                        "- one of them is redundant" % (indices, face, plane))


def quad_key(face, lo, hi):
    axis = {"west": 0, "east": 0, "down": 1, "up": 1, "north": 2, "south": 2}.get(face)
    if axis is None:
        return None
    plane = lo[axis] if face in ("west", "down", "north") else hi[axis]
    extent = tuple(round(v, 4) for i in range(3) if i != axis for v in (lo[i], hi[i]))
    return (face, round(plane, 4), extent)


def check_state_coverage(report, blockstates, expected):
    for name, combos in expected.items():
        data = blockstates.get(name)
        if data is None:
            report.error("blockstates/%s.json" % name, "declared by the registry but absent")
            continue
        if "multipart" in data:
            continue
        variants = data.get("variants", {})
        # A block with no properties has the single variant "", which splits to [""] rather than
        # to an empty list - normalise so it matches an empty expected combination.
        present = {frozenset(p for p in key.split(",") if p) for key in variants}
        for combo in combos:
            if frozenset(combo) not in present:
                report.error("blockstates/%s.json" % name,
                             "no variant for state '%s'"
                             % (",".join(sorted(combo)) if combo else "<no properties>"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--states", help="JSON map of blockstate name -> list of property lists")
    parser.add_argument("--scope", nargs="*", default=None,
                        help="only report paths containing one of these substrings")
    args = parser.parse_args()

    report = Report(args.scope)
    blockstates = check_blockstates(report)
    check_models(report)

    if args.states:
        with open(args.states, encoding="utf-8") as fh:
            expected = json.load(fh)
        check_state_coverage(report, blockstates, expected)

    for warning in report.warnings:
        print("WARN  " + warning)
    for error in report.errors:
        print("ERROR " + error)

    print("\n%d error(s), %d warning(s)" % (len(report.errors), len(report.warnings)), end="")
    if report.suppressed:
        print(", %d out-of-scope finding(s) suppressed" % report.suppressed, end="")
    print()
    return 1 if report.errors else 0


if __name__ == "__main__":
    sys.exit(main())
