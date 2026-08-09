#!/usr/bin/env python3
"""
Run this after editing models in Blockbench, before starting the client.

    python tools/prepare_client.py

It does NOT write to your model files. Every model in this project is hand-authored; the tools
only check them and get them onto the client's resource path:

  1. validate  - blockstates and models resolve, every block state has a variant, and the
                 Blockbench-drop problems below are reported as warnings
  2. copy      - runs gradlew processResources, because the dev client reads
                 build/resources/main, not the source tree. Editing a model and reloading in
                 game does nothing until this has run.
  3. verify    - confirms every asset actually arrived, so "my edit did nothing" is answerable
                 with a fact

Things Blockbench silently drops when it re-saves a model, which the validator now warns about:

  render_type          a model using the transparent `ornateness` sheet needs
                       "render_type": "minecraft:cutout" or Minecraft ignores alpha and its
                       transparent-black texels render as opaque black panels
  ambientocclusion     a model leaving the 0..16 cube needs "ambientocclusion": false, or smooth
                       lighting extrapolates past the block bounds and the upper part goes black
  zero-thickness pane  an element flat on one axis with a face on both sides renders two quads in
                       the same plane and always z-fights - give it a little thickness

`python tools/normalize_sources.py` will apply those three fixes for you if you would rather not
do them by hand, but nothing runs it automatically.
"""
import hashlib
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOOLS = os.path.join(ROOT, "tools")

SCOPE = ["structure/plaster", "structure/sandstone", "structure/bannister",
         "structure/plaster_wood_post", "structure/villa_lamp_post", "structure/house_farm_plot",
         "blockstates/plaster", "blockstates/ornate", "blockstates/sandstone",
         "blockstates/regular_sandstone", "blockstates/wood_support_floor",
         "blockstates/bannister", "blockstates/villa_lamp_post", "blockstates/house_farm_plot"]


def verify_in_sync():
    src = os.path.join(ROOT, "src", "main", "resources")
    out = os.path.join(ROOT, "build", "resources", "main")
    stale = []
    for base, _, files in os.walk(src):
        for name in files:
            source = os.path.join(base, name)
            copied = os.path.join(out, os.path.relpath(source, src))
            if not os.path.exists(copied):
                stale.append((os.path.relpath(source, ROOT), "not copied"))
            elif hashlib.md5(open(source, "rb").read()).digest() != \
                    hashlib.md5(open(copied, "rb").read()).digest():
                stale.append((os.path.relpath(source, ROOT), "differs"))
    if stale:
        print("\n%d asset(s) did NOT reach the client's resource path:" % len(stale))
        for path, why in stale[:20]:
            print("   %-90s %s" % (path.replace("\\", "/"), why))
        return 1
    print("all assets are in sync - restart the client and your edits will be there")
    return 0


def main():
    print("=== validating (reporting only, nothing is rewritten)")
    states = os.path.join(ROOT, "build", "expected_states.json")
    os.makedirs(os.path.dirname(states), exist_ok=True)
    with open(states, "w", encoding="utf-8") as fh:
        subprocess.run([sys.executable, os.path.join(TOOLS, "gen_expected_states.py")],
                       cwd=ROOT, stdout=fh, check=True)
    code = subprocess.run(
        [sys.executable, os.path.join(TOOLS, "validate_resources.py"),
         "--states", states, "--scope"] + SCOPE, cwd=ROOT).returncode
    if code:
        print("\nvalidation found errors - fix those first, the client would show missing models")
        return code

    print("\n=== copying resources into build/ (what the client reads)")
    gradlew = os.path.join(ROOT, "gradlew.bat" if os.name == "nt" else "gradlew")
    if subprocess.run([gradlew, "processResources", "--console=plain", "-q"], cwd=ROOT).returncode:
        print("gradlew processResources FAILED - the client will still see the old models")
        return 1

    print("\n=== verifying")
    return verify_in_sync()


if __name__ == "__main__":
    sys.exit(main())
