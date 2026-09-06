"""Import the supplied Java item exports without modifying their authoring files.

Run with --check to verify the entire source inventory and deterministic runtime output.
No image manipulation: PNGs are copied byte for byte. No extra runtime dependency.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[2]
ASSETS = PROJECT / "src/main/resources/assets/britannia_mod"
MANIFEST = PROJECT / "tools/new-assets/patch18_weapon_assets.json"
SOURCE_LABEL = "weapons"
MELEE = ("viking_sword", "katana", "rapier", "halberd")
METAL_ELEMENTS = {"viking_sword": {1, 2, 3, 4, 5, 6}, "katana": {0, 2, 3, 4},
                  "rapier": {1, 2, 3, 4, 5, 6, 7, 8}, "halberd": set()}


def encoded(value):
    return (json.dumps(value, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def shield_display():
    # Model is centered at [8, 8, 8] below. These are ordinary baked-item transforms.
    return {
        "gui": {"rotation": [0, 180, 0], "scale": [0.65, 0.65, 0.65]},
        "ground": {"rotation": [90, 0, 0], "translation": [0, 3, 0], "scale": [0.5]*3},
        "fixed": {"rotation": [0, 180, 0], "scale": [0.6]*3},
        "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 2, 2], "scale": [0.7]*3},
        "thirdperson_lefthand": {"rotation": [0, -90, 0], "translation": [0, 2, 2], "scale": [0.7]*3},
        "firstperson_righthand": {"rotation": [0, 70, 0], "translation": [-2, -1, 0], "scale": [0.6]*3},
        "firstperson_lefthand": {"rotation": [0, -70, 0], "translation": [2, -1, 0], "scale": [0.6]*3},
    }


def outputs(source):
    generated = {}
    for name in (*MELEE, "decorative_shield"):
        shield = name == "decorative_shield"
        model_source = source / ("decorative_shield - Converted.json" if shield else f"{name}/{name}.json")
        texture_source = source / (f"{name}.png" if shield else f"{name}/{name}.png")
        model = json.loads(model_source.read_text(encoding="utf-8-sig"))
        # Exporter metadata is not part of the Minecraft 1.21.1 item-model format.
        for key in ("format_version", "texture_size", "groups"):
            model.pop(key, None)
        model["parent"] = "minecraft:block/block"
        model["textures"] = {"0": f"britannia_mod:item/weapons/{name}",
                             "particle": f"britannia_mod:item/weapons/{name}"}
        if shield:
            # Authoring export is a wall ornament: translate its center [8,16,14.95]
            # to the item-model center. Geometry, thickness, artwork and UVs are preserved.
            for element in model["elements"]:
                for key in ("from", "to"):
                    element[key] = [round(v+d, 6) for v, d in zip(element[key], (0, -8, -6.95))]
                rotation = element.get("rotation")
                if rotation:
                    rotation["origin"] = [round(v+d, 6) for v, d in zip(rotation["origin"], (0, -8, -6.95))]
            model["gui_light"] = "front"
            model["display"] = shield_display()
            blocking = {"parent": "britannia_mod:item/decorative_shield", "display": copy.deepcopy(model["display"])}
            blocking["display"]["firstperson_righthand"].update(rotation=[0, 10, 0], translation=[-1, 1, -2])
            blocking["display"]["firstperson_lefthand"].update(rotation=[0, -10, 0], translation=[1, 1, -2])
            generated[ASSETS / "models/item/decorative_shield_blocking.json"] = encoded(blocking)
            model["overrides"] = [{"predicate": {"minecraft:blocking": 1}, "model": "britannia_mod:item/decorative_shield_blocking"}]
        else:
            for index in METAL_ELEMENTS[name]:
                for face in model["elements"][index]["faces"].values():
                    face["tintindex"] = 0
        generated[ASSETS / f"models/item/{name}.json"] = encoded(model)
        generated[ASSETS / f"textures/item/weapons/{name}.png"] = texture_source.read_bytes()
    return generated


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True, help="Directory containing the source weapon exports")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    source_files = sorted(p for p in args.source.rglob("*") if p.is_file())
    expected = {"dagger/dagger.json", "dagger/dagger.png", "decorative_shield.bbmodel",
                "decorative_shield.png", "decorative_shield - Converted.json"}
    for name in MELEE:
        expected.update((f"{name}.bbmodel", f"{name}/{name}.json", f"{name}/{name}.png"))
    actual = {p.relative_to(args.source).as_posix() for p in source_files}
    if actual != expected:
        raise SystemExit(f"Source inventory changed: added={actual-expected}, missing={expected-actual}")
    inventory = {"source_root": SOURCE_LABEL, "files": {
        p.relative_to(args.source).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in source_files}}
    generated = outputs(args.source)
    generated[MANIFEST] = encoded(inventory)
    errors = []
    for path, data in generated.items():
        if args.check:
            # Git may check JSON out as CRLF on Windows; source PNG/hash checks stay byte-exact.
            same = path.exists() and (json.loads(path.read_text(encoding="utf-8")) == json.loads(data)
                                      if path.suffix == ".json" else path.read_bytes() == data)
            if not same:
                errors.append(str(path.relative_to(PROJECT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    if errors:
        raise SystemExit("Runtime assets/source manifest differ: " + ", ".join(errors))
    print(f"{'Verified' if args.check else 'Imported'} 5 items; accounted for all {len(source_files)} source files (6 distinct items including existing dagger).")


if __name__ == "__main__":
    main()
