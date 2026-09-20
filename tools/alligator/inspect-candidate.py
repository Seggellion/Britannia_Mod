"""Inspect the actual bundled hotfix bytes and source identity; emits a reviewable JSON manifest."""
import argparse
import hashlib
import json
import pathlib
import tomllib
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("jar", type=pathlib.Path)
parser.add_argument("--head", required=True)
parser.add_argument("--branch", required=True)
args = parser.parse_args()
with zipfile.ZipFile(args.jar) as jar:
    names = set(jar.namelist())
    metadata = tomllib.loads(jar.read("META-INF/neoforge.mods.toml").decode())
    assert metadata["mods"][0]["version"] == "0.1.8d"
    dependencies = {entry["modId"]: entry for entry in metadata["dependencies"]["britannia_mod"]}
    assert dependencies["minecraft"]["versionRange"] == "[1.21.1,1.21.2)"
    assert dependencies["neoforge"]["versionRange"] == "[21.1.72]"
    provenance = dict(line.split("=", 1) for line in jar.read("britannia_mod_build.properties").decode().splitlines()
                      if "=" in line and not line.startswith("#"))
    assert provenance["git.head"] == args.head, provenance
    assert provenance["git.branch"] == args.branch, provenance
    assert provenance["git.dirty"] == "false", provenance
    assert provenance["mod.version"] == "0.1.8d", provenance
    prefix = "com/seggellion/britannia_mod/"
    required = ["entity/AlligatorEntity.class", "entity/ai/AlligatorNavigation.class",
                "entity/ai/AlligatorMoveControl.class", "entity/ai/AlligatorAirBudget.class",
                "entity/ai/AlligatorWaterGoals$IdleWater.class", "entity/ai/AlligatorWaterGoals$RecoverAir.class"]
    for name in required:
        assert prefix + name in names, name
    assert not any(name.startswith(prefix + "gametest/") for name in names)
    assert not any("data/britannia_alligator" in name or name.endswith("service_npc_spawn_test_empty.nbt") for name in names)
    assert not any(name.endswith(".bbmodel") for name in names)
    embedded = sorted(name for name in names if name.startswith("META-INF/jarjar/") and name.endswith(".jar"))
    assert any(name.endswith("geckolib-neoforge-1.21.1-4.6.6.jar") for name in embedded), embedded
    assert any(name.endswith("nanohttpd-2.2.0.jar") for name in embedded), embedded
    clips = json.loads(jar.read("assets/britannia_mod/animations/alligator.animation.json"))["animations"]
    assert "animation.model.walk" in clips
    entity = jar.read(prefix + "entity/AlligatorEntity.class")
    assert b"animation.model.walk" in entity
    assert b"animation.model.idle" not in entity and b"animation.model.attack" not in entity
    assert b"net/minecraft/client" not in entity
    result = dict(path=str(args.jar.resolve()), bytes=args.jar.stat().st_size,
                  sha256=hashlib.sha256(args.jar.read_bytes()).hexdigest().upper(),
                  provenance=provenance, minecraft_range=dependencies["minecraft"]["versionRange"],
                  neoforge_range=dependencies["neoforge"]["versionRange"], embedded=embedded,
                  alligator_classes=required, alligator_clips=sorted(clips), gametest_leaks=False)
print(json.dumps(result, indent=2))
