"""Generate the hand-authored sandstone-family block models and battlement OBJ meshes."""

from __future__ import annotations

import json
import math
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODEL_DIR = ROOT / "src/main/resources/assets/britannia_mod/models/block/structure/sandstone"
GEOMETRY_DIR = MODEL_DIR / "geometry"
TEXTURE_ROOT = "britannia_mod:block/structure/sandstone/"

SIDE = TEXTURE_ROOT + "custom_sandstone_brick_0"
TOP = TEXTURE_ROOT + "custom_sandstone_brick_top_0"
BOTTOM = TEXTURE_ROOT + "custom_sandstone_brick_0"
ORNATE_1 = TEXTURE_ROOT + "custom_sandstone_ornate_1"
ORNATE_2 = TEXTURE_ROOT + "custom_sandstone_ornate_2"

DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -2.5, 0], "scale": [0.3125] * 3},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25] * 3},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5] * 3},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375] * 3},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375] * 3},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4] * 3},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4] * 3},
    "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}


def write_json(path: Path, value: dict) -> None:
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def face(texture: str, width: float, height: float, rotation: int | None = None) -> dict:
    result: dict[str, object] = {"uv": [0, 0, min(width, 16), min(height, 16)], "texture": texture}
    if rotation is not None:
        result["rotation"] = rotation
    return result


def box(name: str, bounds: tuple[float, float, float, float, float, float],
        *, omit: set[str] | None = None) -> dict:
    x0, y0, z0, x1, y1, z1 = bounds
    omit = omit or set()
    faces = {}
    for direction, width, height, texture in (
        ("north", x1 - x0, y1 - y0, "#side"),
        ("south", x1 - x0, y1 - y0, "#side"),
        ("west", z1 - z0, y1 - y0, "#side"),
        ("east", z1 - z0, y1 - y0, "#side"),
        ("up", x1 - x0, z1 - z0, "#top"),
        ("down", x1 - x0, z1 - z0, "#bottom"),
    ):
        if direction not in omit:
            faces[direction] = face(texture, width, height)
    return {"name": name, "from": [x0, y0, z0], "to": [x1, y1, z1], "faces": faces}


def add_box(elements: list[dict], name: str,
            bounds: tuple[float, float, float, float, float, float]) -> None:
    x0, y0, z0, x1, y1, z1 = bounds
    if y0 < 16 < y1:
        elements.append(box(name + "_lower", (x0, y0, z0, x1, 16, z1), omit={"up"}))
        elements.append(box(name + "_upper", (x0, 16, z0, x1, y1, z1), omit={"down"}))
    else:
        elements.append(box(name, bounds))


def base_model(top_texture: str, elements: list[dict]) -> dict:
    return {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {"particle": SIDE, "side": SIDE, "top": top_texture, "bottom": BOTTOM},
        "elements": elements,
        "display": DISPLAY,
    }


def wall_elements(shape: str) -> list[dict]:
    elements: list[dict] = []
    boxes = {
        "straight": [("main", (0, 0, 0, 16, 32, 7))],
        "corner": [("west_run", (0, 0, 0, 7, 32, 16)), ("north_run", (7, 0, 0, 16, 32, 7))],
        "corner_branch_right": [("east_run", (9, 0, 0, 16, 32, 16)), ("north_run", (0, 0, 0, 9, 32, 7))],
        "t_junction": [("north_run", (0, 0, 0, 16, 32, 7)), ("west_branch", (0, 0, 7, 7, 32, 16))],
        "t_junction_branch_right": [("north_run", (0, 0, 0, 16, 32, 7)), ("east_branch", (9, 0, 7, 16, 32, 16))],
    }[shape]
    for name, bounds in boxes:
        add_box(elements, name, bounds)
    return elements


def window_run(elements: list[dict], prefix: str, axis: str,
               start: float, end: float, fixed0: float, fixed1: float) -> None:
    # A four-piece masonry frame. The 10x24-voxel void is completely absent from the model.
    jamb = 3 if end - start >= 16 else 2
    if axis == "x":
        add_box(elements, prefix + "_sill", (start, 0, fixed0, end, 4, fixed1))
        add_box(elements, prefix + "_jamb_a", (start, 4, fixed0, start + jamb, 28, fixed1))
        add_box(elements, prefix + "_jamb_b", (end - jamb, 4, fixed0, end, 28, fixed1))
        add_box(elements, prefix + "_lintel", (start, 28, fixed0, end, 32, fixed1))
    else:
        add_box(elements, prefix + "_sill", (fixed0, 0, start, fixed1, 4, end))
        add_box(elements, prefix + "_jamb_a", (fixed0, 4, start, fixed1, 28, start + 2))
        add_box(elements, prefix + "_jamb_b", (fixed0, 4, end - 2, fixed1, 28, end))
        add_box(elements, prefix + "_lintel", (fixed0, 28, start, fixed1, 32, end))


def window_elements(shape: str) -> list[dict]:
    elements: list[dict] = []
    if shape == "corner":
        add_box(elements, "corner_pillar", (0, 0, 0, 7, 32, 7))
        window_run(elements, "north", "x", 7, 16, 0, 7)
        window_run(elements, "west", "z", 7, 16, 0, 7)
        return elements
    if shape == "corner_branch_right":
        add_box(elements, "corner_pillar", (9, 0, 0, 16, 32, 7))
        window_run(elements, "north", "x", 0, 9, 0, 7)
        window_run(elements, "east", "z", 7, 16, 9, 16)
        return elements
    window_run(elements, "north", "x", 0, 16, 0, 7)
    if shape == "t_junction":
        window_run(elements, "west", "z", 7, 16, 0, 7)
    elif shape == "t_junction_branch_right":
        window_run(elements, "east", "z", 7, 16, 9, 16)
    return elements


def generate_json_models() -> None:
    ordinary = [
        "regular_sandstone_wall",
        "sandstone_block_wall",
        "sandstone_post",
        "sandstone_column",
    ]
    ornate = {
        "ornate_sandstone_wall": ORNATE_1,
        "ornate_sandstone_post": ORNATE_1,
    }
    shapes = ["straight", "corner", "corner_branch_right", "t_junction", "t_junction_branch_right"]

    for block_id in ordinary:
        for shape in shapes:
            write_json(MODEL_DIR / f"{block_id}_{shape}.json", base_model(TOP, wall_elements(shape)))
    for block_id, ornate_top in ornate.items():
        for shape in shapes:
            write_json(MODEL_DIR / f"{block_id}_{shape}.json", base_model(ornate_top, wall_elements(shape)))
    for block_id, top_texture in {"sandstone_window": TOP, "ornate_sandstone_window": ORNATE_2}.items():
        for shape in shapes:
            write_json(MODEL_DIR / f"{block_id}_{shape}.json", base_model(top_texture, window_elements(shape)))


class Obj:
    def __init__(self, mtllib: str) -> None:
        self.lines = ["# UltimaCraft sandstone battlement geometry", f"mtllib {mtllib}"]
        self.vertices: list[tuple[float, float, float]] = []
        self.uvs: list[tuple[float, float]] = []
        self.normals: list[tuple[float, float, float]] = []

    def vertex(self, value: tuple[float, float, float]) -> int:
        self.vertices.append(value)
        return len(self.vertices)

    def polygon(self, material: str, indices: list[int]) -> None:
        points = [self.vertices[index - 1] for index in indices]
        a, b, c = points[:3]
        ab = tuple(b[i] - a[i] for i in range(3))
        ac = tuple(c[i] - a[i] for i in range(3))
        cross = (ab[1] * ac[2] - ab[2] * ac[1],
                 ab[2] * ac[0] - ab[0] * ac[2],
                 ab[0] * ac[1] - ab[1] * ac[0])
        length = math.sqrt(sum(component * component for component in cross))
        normal = tuple(component / length for component in cross)
        self.normals.append(normal)
        normal_index = len(self.normals)

        coordinates = [(0, 0), (0, 1), (1, 1), (1, 0)] if len(indices) == 4 else [(0, 0), (0, 1), (1, 1)]
        uv_indices = []
        for uv in coordinates[:len(indices)]:
            self.uvs.append(uv)
            uv_indices.append(len(self.uvs))

        self.lines.append(f"usemtl {material}")
        self.lines.append("f " + " ".join(
            f"{vertex}/{uv}/{normal_index}" for vertex, uv in zip(indices, uv_indices)))

    def cuboid_cap(self, x0: float, x1: float, z0: float, z1: float) -> None:
        y0, y1 = 31 / 16, 2.0
        v = [self.vertex(point) for point in (
            (x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1),
            (x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1),
        )]
        self.polygon("top", [v[4], v[7], v[6], v[5]])
        self.polygon("side", [v[0], v[4], v[5], v[1]])
        self.polygon("side", [v[1], v[5], v[6], v[2]])
        self.polygon("side", [v[2], v[6], v[7], v[3]])
        self.polygon("side", [v[3], v[7], v[4], v[0]])

    def render(self) -> str:
        header = self.lines[:2]
        body = self.lines[2:]
        values = header
        values += [f"v {x:.6f} {y:.6f} {z:.6f}" for x, y, z in self.vertices]
        values += [f"vt {u:.6f} {v:.6f}" for u, v in self.uvs]
        values += [f"vn {x:.6f} {y:.6f} {z:.6f}" for x, y, z in self.normals]
        values += body
        return "\n".join(values) + "\n"


def straight_obj() -> Obj:
    obj = Obj("sandstone_battlement.mtl")
    nw, ne, se, sw, hw, he, hsw, hse = [obj.vertex(point) for point in (
        (0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1),
        (0, 31 / 16, 15 / 16), (1, 31 / 16, 15 / 16),
        (0, 31 / 16, 1), (1, 31 / 16, 1),
    )]
    obj.polygon("bottom", [nw, ne, se, sw])
    obj.polygon("side", [nw, hw, he, ne])
    obj.polygon("side", [sw, se, hse, hsw])
    obj.polygon("side", [nw, sw, hsw, hw])
    obj.polygon("side", [ne, he, hse, se])
    obj.cuboid_cap(0, 1, 15 / 16, 1)
    return obj


def corner_obj(mirror: bool) -> Obj:
    obj = Obj("sandstone_battlement.mtl")

    def point(x: float, y: float, z: float) -> tuple[float, float, float]:
        return (1 - x if mirror else x, y, z)

    a, b, se, c, d, e, f, g = [obj.vertex(point(*value)) for value in (
        (0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1),
        (15 / 16, 31 / 16, 15 / 16), (1, 31 / 16, 15 / 16),
        (15 / 16, 31 / 16, 1), (1, 31 / 16, 1),
    )]
    faces = [
        ("bottom", [a, b, se, c]),
        ("side", [a, d, e, b]),
        ("side", [a, c, f, d]),
        ("side", [b, e, g, se]),
        ("side", [c, se, g, f]),
    ]
    for material, indices in faces:
        obj.polygon(material, list(reversed(indices)) if mirror else indices)
    if mirror:
        obj.cuboid_cap(0, 1 / 16, 15 / 16, 1)
    else:
        obj.cuboid_cap(15 / 16, 1, 15 / 16, 1)
    return obj


def generate_battlements() -> None:
    GEOMETRY_DIR.mkdir(parents=True, exist_ok=True)
    (GEOMETRY_DIR / "sandstone_battlement.mtl").write_text(
        "# UltimaCraft sandstone materials\n"
        "newmtl side\nKa 1 1 1\nKd 1 1 1\nillum 1\nmap_Kd #side\n\n"
        "newmtl top\nKa 1 1 1\nKd 1 1 1\nillum 1\nmap_Kd #top\n\n"
        "newmtl bottom\nKa 1 1 1\nKd 1 1 1\nillum 1\nmap_Kd #bottom\n",
        encoding="utf-8")

    meshes = {
        "straight": straight_obj(),
        "corner": corner_obj(False),
        "corner_branch_right": corner_obj(True),
        "t_junction": straight_obj(),
        "t_junction_branch_right": straight_obj(),
    }
    for shape, obj in meshes.items():
        mesh_name = f"sandstone_battlement_{shape}.obj"
        (GEOMETRY_DIR / mesh_name).write_text(obj.render(), encoding="utf-8")
        write_json(MODEL_DIR / f"sandstone_battlement_{shape}.json", {
            "loader": "neoforge:obj",
            "model": f"britannia_mod:models/block/structure/sandstone/geometry/{mesh_name}",
            "automatic_culling": False,
            "shade_quads": True,
            "flip_v": True,
            "emissive_ambient": False,
            "textures": {"particle": SIDE, "side": SIDE, "top": TOP, "bottom": BOTTOM},
            "display": DISPLAY,
        })


if __name__ == "__main__":
    generate_json_models()
    generate_battlements()
