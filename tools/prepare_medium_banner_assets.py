#!/usr/bin/env python3
"""Prepare perpendicular medium-banner assets without bypassing owner approval."""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw


BANNERS = (
    "tournament_medium",
    "ceremonial_tournament",
    "iron_quarter",
    "outer_ward",
    "ward_of_serpents",
    "serpent_guard",
    "crossroad_guard",
    "argent_shield",
)
CANVAS = (128, 128)
BLUE = (42, 99, 210)
SOURCE = "C:/projects/britannia/raw fiels/tabbard/banner_medium.ai"
SOURCE_SHA256 = "5a219e6e276884e6b7173ec5c6608c8a85b53dcbdeb768b0f2c19ead0423d34d"
DISPLAY_NAMES = {
    "tournament_medium": "Tournament Medium",
    "ceremonial_tournament": "Ceremonial Tournament",
    "iron_quarter": "Iron Quarter",
    "outer_ward": "Outer Ward",
    "ward_of_serpents": "Ward of Serpents",
    "serpent_guard": "Serpent Guard",
    "crossroad_guard": "Crossroad Guard",
    "argent_shield": "Argent Shield",
}
CURRENT_LABELS = DISPLAY_NAMES
CATALOGUE_INDICES = {banner_id: 13 + index for index, banner_id in enumerate(BANNERS)}
APPARENT_EMBEDDED_HARDWARE = {
    "tournament_medium",
    "ceremonial_tournament",
    "iron_quarter",
    "argent_shield",
}


@dataclass(frozen=True)
class GeometryGroup:
    key: str
    resource_id: str
    representative: str
    members: tuple[str, ...]
    evidence: str


GEOMETRY_GROUPS = (
    GeometryGroup(
        "tournament_pair",
        "britannia_mod:banner/medium/tournament_pair/geometry",
        "tournament_medium",
        ("tournament_medium", "ceremonial_tournament"),
        "Matching harlequin fields with the same rectangular body, three-point lower "
        "edge, top attachment treatment, and full-canvas layout.",
    ),
    GeometryGroup(
        "iron_quarter",
        "britannia_mod:banner/medium/iron_quarter/geometry",
        "iron_quarter",
        ("iron_quarter",),
        "Wide single-point field with a distinct top crossbar and proportions.",
    ),
    GeometryGroup(
        "pointed_ward",
        "britannia_mod:banner/medium/pointed_ward/geometry",
        "outer_ward",
        ("outer_ward", "ward_of_serpents"),
        "Matching pentagonal cloth silhouettes with one top attachment edge and a "
        "centered lower point; heraldry alone differs.",
    ),
    GeometryGroup(
        "rounded_guard",
        "britannia_mod:banner/medium/rounded_guard/geometry",
        "serpent_guard",
        ("serpent_guard", "crossroad_guard"),
        "Matching rectangular fields with rounded lower corners; heraldry alone differs.",
    ),
    GeometryGroup(
        "argent_shield",
        "britannia_mod:banner/medium/argent_shield/geometry",
        "argent_shield",
        ("argent_shield",),
        "Narrow U-ended shield field with a distinct top crossbar and proportions.",
    ),
)
GROUP_BY_BANNER = {
    banner_id: group for group in GEOMETRY_GROUPS for banner_id in group.members
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def checkerboard(size: tuple[int, int], cell: int = 8) -> Image.Image:
    image = Image.new("RGBA", size)
    pixels = image.load()
    colours = ((210, 210, 210, 255), (245, 245, 245, 255))
    for y in range(size[1]):
        for x in range(size[0]):
            pixels[x, y] = colours[((x // cell) + (y // cell)) & 1]
    return image


def composite_on_checker(source: Image.Image) -> Image.Image:
    return Image.alpha_composite(checkerboard(source.size), source)


def normalize_mask(base: Image.Image, authored: Image.Image) -> Image.Image:
    """Whiten the authored selection and constrain only its alpha to the base."""
    base_alpha = base.getchannel("A")
    authored_alpha = authored.getchannel("A")
    safe_alpha = ImageChops.darker(authored_alpha, base_alpha)
    normalized = Image.new("RGBA", authored.size, (255, 255, 255, 0))
    normalized.putalpha(safe_alpha)
    return normalized


def make_alignment(base: Image.Image, mask: Image.Image) -> Image.Image:
    base_alpha = base.getchannel("A")
    mask_alpha = mask.getchannel("A")
    image = checkerboard(base.size)
    cyan = Image.new("RGBA", base.size, (0, 180, 210, 0))
    cyan.putalpha(base_alpha.point(lambda value: min(150, value)))
    image = Image.alpha_composite(image, cyan)
    magenta = Image.new("RGBA", base.size, (235, 0, 150, 0))
    magenta.putalpha(mask_alpha.point(lambda value: min(210, value)))
    return Image.alpha_composite(image, magenta)


def make_blue_recolour(base: Image.Image, mask: Image.Image) -> Image.Image:
    tint = Image.new("RGBA", base.size, BLUE + (0,))
    tint.putalpha(mask.getchannel("A"))
    return composite_on_checker(Image.alpha_composite(base, tint))


def make_geometry_preview(base: Image.Image) -> Image.Image:
    """Show the authored silhouette against the proposed 1 x 2 logical footprint."""
    preview = Image.new("RGBA", (256, 320), (32, 35, 42, 255))
    draw = ImageDraw.Draw(preview)
    draw.text((12, 10), "wall_perpendicular | proposed 1 x 2 footprint", fill="white")
    frame = (64, 48, 192, 304)
    draw.rectangle(frame, outline=(110, 175, 255, 255), width=3)
    draw.line((64, 176, 192, 176), fill=(110, 175, 255, 150), width=1)
    cloth = base.resize((128, 256), Image.Resampling.NEAREST)
    preview.alpha_composite(cloth, (64, 48))
    draw.line((56, 48, 200, 48), fill=(210, 170, 70, 255), width=5)
    draw.line((128, 40, 128, 312), fill=(255, 255, 255, 80), width=1)
    return preview


def review_sheet(
    banner_id: str,
    natural: Image.Image,
    mask: Image.Image,
    blue: Image.Image,
    alignment: Image.Image,
) -> Image.Image:
    scale = 2
    panel_size = (CANVAS[0] * scale, CANVAS[1] * scale)
    sheet = Image.new(
        "RGBA", (panel_size[0] * 2, 48 + panel_size[1] * 2), (32, 35, 42, 255)
    )
    draw = ImageDraw.Draw(sheet)
    draw.text(
        (12, 10),
        f"{banner_id} - authoritative 128 x 128 export",
        fill=(255, 255, 255, 255),
    )
    panels = (
        ("Natural base", natural),
        ("Authored mask", mask),
        ("Blue recolour", blue),
        ("Alignment: base cyan / mask magenta", alignment),
    )
    for index, (label, panel) in enumerate(panels):
        column = index % 2
        row = index // 2
        x = column * panel_size[0]
        y = 48 + row * panel_size[1]
        sheet.alpha_composite(
            panel.resize(panel_size, Image.Resampling.NEAREST), (x, y)
        )
        draw.rectangle(
            (x + 4, y + 4, x + 12 + 7 * len(label), y + 22),
            fill=(0, 0, 0, 190),
        )
        draw.text((x + 8, y + 7), label, fill=(255, 255, 255, 255))
    return sheet


def planar_geometry(
    representative: str, resource_id: str, bbox: tuple[int, int, int, int]
) -> dict[str, object]:
    x0, y0, x1, y1 = bbox
    pixel_width = x1 - x0
    pixel_height = y1 - y0
    physical_height = 20.0
    physical_width = physical_height * pixel_width / pixel_height
    left = 8.0 - physical_width / 2.0
    right = 8.0 + physical_width / 2.0
    uv = [x0 / 8.0, y0 / 8.0, x1 / 8.0, y1 / 8.0]
    texture_root = f"britannia_mod:banner/{representative}"
    return {
        "credit": (
            "Proposed perpendicular medium-family planar geometry derived from the authoritative "
            f"128 x 128 alpha bounds for {resource_id}"
        ),
        "parent": "minecraft:block/block",
        "render_type": "minecraft:translucent",
        "textures": {
            "base_texture": f"{texture_root}/base_texture",
            "dye_mask": f"{texture_root}/dye_mask",
            "particle": f"{texture_root}/base_texture",
        },
        "elements": [
            {
                "from": [round(left, 5), -4.0, 7.49],
                "to": [round(right, 5), 16.0, 7.51],
                "faces": {
                    face: {"uv": uv, "texture": "#base_texture"}
                    for face in ("north", "south")
                },
            },
            {
                "from": [round(left, 5), -4.0, 7.47],
                "to": [round(right, 5), 16.0, 7.53],
                "faces": {
                    face: {
                        "uv": uv,
                        "texture": "#dye_mask",
                        "tintindex": 1,
                    }
                    for face in ("north", "south")
                },
            },
        ],
    }


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    intake_root = root / "content" / "banner-final-intake" / "submissions"
    image_data: dict[str, dict[str, object]] = {}
    family_sheets: list[Image.Image] = []

    for banner_id in BANNERS:
        submission = intake_root / banner_id
        base_path = submission / "base_texture.png"
        mask_path = submission / "dye_mask.png"
        if not base_path.is_file() or not mask_path.is_file():
            raise FileNotFoundError(f"Missing Illustrator export for {banner_id}")

        with Image.open(base_path) as opened_base:
            if opened_base.mode != "RGBA" or opened_base.size != CANVAS:
                raise ValueError(
                    f"{banner_id}: base must be 128 x 128 RGBA, got "
                    f"{opened_base.size} {opened_base.mode}"
                )
            base = opened_base.copy()
        with Image.open(mask_path) as opened_mask:
            if opened_mask.size != CANVAS:
                raise ValueError(
                    f"{banner_id}: mask must be 128 x 128, got {opened_mask.size}"
                )
            authored_mask = opened_mask.convert("RGBA")

        mask = normalize_mask(base, authored_mask)
        mask.save(mask_path, "PNG", compress_level=9)

        review = submission / "review"
        review.mkdir(parents=True, exist_ok=True)
        natural = composite_on_checker(base)
        mask_checker = composite_on_checker(mask)
        blue = make_blue_recolour(base, mask)
        alignment = make_alignment(base, mask)
        natural.save(review / "natural_checkerboard.png", "PNG", compress_level=9)
        mask_checker.save(
            review / "dye_mask_checkerboard.png", "PNG", compress_level=9
        )
        blue.save(review / "blue_recolour.png", "PNG", compress_level=9)
        alignment.save(review / "alignment.png", "PNG", compress_level=9)
        geometry_preview = make_geometry_preview(base)
        geometry_preview.save(
            review / "perpendicular_geometry_silhouette.png",
            "PNG",
            compress_level=9,
        )
        sheet = review_sheet(banner_id, natural, mask_checker, blue, alignment)
        sheet.save(review / "review_sheet.png", "PNG", compress_level=9)
        family_sheets.append(sheet)

        base_pixels = list(base.get_flattened_data())
        mask_pixels = list(mask.get_flattened_data())
        active = sum(1 for pixel in mask_pixels if pixel[3] > 0)
        transparent = sum(1 for pixel in mask_pixels if pixel[3] == 0)
        fixed = sum(
            1
            for base_pixel, mask_pixel in zip(base_pixels, mask_pixels)
            if base_pixel[3] > 0 and mask_pixel[3] == 0
        )
        violations = sum(
            1
            for base_pixel, mask_pixel in zip(base_pixels, mask_pixels)
            if mask_pixel[3] > base_pixel[3]
        )
        nonwhite = sum(
            1
            for pixel in mask_pixels
            if pixel[3] > 0 and pixel[:3] != (255, 255, 255)
        )
        bbox = base.getchannel("A").getbbox()
        if (
            bbox is None
            or active == 0
            or transparent == 0
            or fixed == 0
            or violations
            or nonwhite
        ):
            raise ValueError(
                f"{banner_id}: invalid mask active={active}, "
                f"transparent={transparent}, fixed={fixed}, "
                f"alpha_violations={violations}, nonwhite={nonwhite}"
            )

        image_data[banner_id] = {
            "bbox": bbox,
            "base_path": base_path,
            "mask_path": mask_path,
            "review_path": review / "review_sheet.png",
            "geometry_preview_path": review / "perpendicular_geometry_silhouette.png",
            "base_sha256": sha256(base_path),
            "mask_sha256": sha256(mask_path),
            "active": active,
            "transparent": transparent,
            "fixed": fixed,
            "violations": violations,
            "nonwhite": nonwhite,
        }

    def normalized_silhouette(banner_id: str) -> set[int]:
        with Image.open(image_data[banner_id]["base_path"]) as opened:
            alpha = opened.convert("RGBA").getchannel("A")
        cropped = alpha.crop(alpha.getbbox()).resize(CANVAS, Image.Resampling.NEAREST)
        return {
            index
            for index, value in enumerate(cropped.get_flattened_data())
            if value > 16
        }

    for group in GEOMETRY_GROUPS:
        if len(group.members) < 2:
            continue
        silhouettes = [normalized_silhouette(member) for member in group.members]
        intersection = len(set.intersection(*silhouettes))
        union = len(set.union(*silhouettes))
        if union == 0 or intersection / union < 0.95:
            raise ValueError(
                f"Proposed shared medium geometry differs for {group.members}: "
                f"IoU={intersection / union if union else 0:.4f}"
            )

    geometry_records: list[dict[str, object]] = []
    for group in GEOMETRY_GROUPS:
        representative_data = image_data[group.representative]
        geometry_path = (
            intake_root / group.representative / "source" / "geometry.json"
        )
        geometry = planar_geometry(
            group.representative,
            group.resource_id,
            representative_data["bbox"],
        )
        write_json(geometry_path, geometry)
        geometry_records.append(
            {
                "key": group.key,
                "resource_id": group.resource_id,
                "members": list(group.members),
                "classification": (
                    "REUSE_SHARED_MEDIUM_GEOMETRY"
                    if len(group.members) > 1
                    else "CUSTOM_GEOMETRY_REQUIRED"
                ),
                "shape_evidence": group.evidence,
                "source_file": geometry_path.relative_to(root).as_posix(),
                "sha256": sha256(geometry_path),
                "alpha_bounds": list(representative_data["bbox"]),
            }
        )

    asset_records: list[dict[str, object]] = []
    for banner_id in BANNERS:
        submission = intake_root / banner_id
        data = image_data[banner_id]
        group = GROUP_BY_BANNER[banner_id]
        group_record = next(
            record for record in geometry_records if record["key"] == group.key
        )
        final_name = DISPLAY_NAMES[banner_id]
        intake = {
            "schema_version": 1,
            "approval": {
                "status": "NOT_APPROVED",
                "approved_by": "",
                "approved_date": "",
                "notes": (
                    "Draft prepared from the authoritative Illustrator source. "
                    "Product-owner identity, approval date, final provenance, and "
                    "distribution permission have not been supplied."
                ),
            },
            "banner": {
                "stable_id": f"britannia_mod:{banner_id}",
                "final_display_name": final_name,
                "catalogue_index": CATALOGUE_INDICES[banner_id],
                "family": "medium",
                "width_blocks": 1,
                "height_blocks": 2,
                "supported_orientations": ["wall_perpendicular"],
                "supported_mounts": [
                    "britannia_mod:brass",
                    "britannia_mod:iron",
                ],
                "default_mount": "britannia_mod:brass",
                "placement_profile_id": "britannia_mod:medium_perpendicular",
                "geometry_id": group.resource_id,
                "geometry_convention": (
                    "Proposed 20-unit-tall perpendicular medium-family planar geometry derived "
                    "from the complete authored alpha bounds; the 128-pixel UV basis, "
                    "established two-pass thickness, and separate perpendicular mount "
                    "geometry are preserved. Runtime placement remains blocked pending "
                    "approval and placed-aspect verification."
                ),
            },
            "localization": {"language": "en_us", "value": final_name},
            "assets": {
                "base_texture": {
                    "resource_id": f"britannia_mod:banner/{banner_id}/base_texture",
                    "source_file": data["base_path"].relative_to(root).as_posix(),
                    "sha256": data["base_sha256"],
                },
                "dye_mask": {
                    "resource_id": f"britannia_mod:banner/{banner_id}/dye_mask",
                    "source_file": data["mask_path"].relative_to(root).as_posix(),
                    "sha256": data["mask_sha256"],
                },
                "geometry": {
                    "shared_geometry_approved": False,
                    "source_file": group_record["source_file"],
                    "sha256": group_record["sha256"],
                },
            },
            "references": {
                "authoring_source_file": None,
                "preview_image_file": data["review_path"].relative_to(root).as_posix(),
            },
            "provenance": {
                "original_art": False,
                "creator": "",
                "creation_method": (
                    "Named base_texture and authored dye_mask artwork exported "
                    "from banner_medium.ai to one aligned 128 x 128 RGBA artboard. "
                    "Active mask RGB was normalized to white and mask alpha was "
                    "constrained to base alpha without expanding the selection."
                ),
                "source_project_file": SOURCE,
                "distribution_permission_confirmed": False,
                "copied_from_reference_art": None,
            },
            "manual_verification": {
                "performed": False,
                "tester": "",
                "date": "",
                "notes": (
                    "Not performed; use the generated review sheet and the reusable "
                    "perpendicular-medium live-review procedure."
                ),
            },
            "requested_content_status": "in_progress",
        }
        intake_path = submission / f"{banner_id}.yml"
        write_json(intake_path, intake)

        missing = [
            "approval.approved_by",
            "approval.approved_date",
            "provenance.original_art confirmation",
            "provenance.creator",
            "provenance.distribution_permission_confirmed",
            "provenance.copied_from_reference_art",
            "manual product-owner review",
            "placed-render aspect verification",
        ]
        if banner_id in APPARENT_EMBEDDED_HARDWARE:
            missing.append(
                "authoring review of apparent crossbar/attachment pixels in base_texture"
            )
        if not final_name:
            missing.extend(
                ["banner.final_display_name", "localization.value"]
            )
        asset_records.append(
            {
                "stable_id": f"britannia_mod:{banner_id}",
                "catalogue_index": CATALOGUE_INDICES[banner_id],
                "current_display_label": CURRENT_LABELS[banner_id],
                "export_classification": "READY_FOR_EXPORT",
                "integration_readiness": "NOT_READY",
                "base_path": data["base_path"].relative_to(root).as_posix(),
                "base_sha256": data["base_sha256"],
                "mask_path": data["mask_path"].relative_to(root).as_posix(),
                "mask_sha256": data["mask_sha256"],
                "dimensions": list(CANVAS),
                "mode": "RGBA",
                "base_alpha_bounds": list(data["bbox"]),
                "mask_active_pixels": data["active"],
                "mask_transparent_pixels": data["transparent"],
                "fixed_base_pixels": data["fixed"],
                "mask_alpha_exceeds_base": data["violations"],
                "active_mask_nonwhite_pixels": data["nonwhite"],
                "review_sheet": data["review_path"].relative_to(root).as_posix(),
                "geometry_preview": data["geometry_preview_path"]
                .relative_to(root)
                .as_posix(),
                "geometry_id": group.resource_id,
                "geometry_classification": group_record["classification"],
                "intake_path": intake_path.relative_to(root).as_posix(),
                "missing_fields": missing,
            }
        )

    family_review = root / "content" / "banner-final-intake" / "review"
    family_review.mkdir(parents=True, exist_ok=True)
    contact = Image.new(
        "RGBA",
        (family_sheets[0].width * 2, family_sheets[0].height * 4),
        (24, 26, 31, 255),
    )
    for index, sheet in enumerate(family_sheets):
        contact.alpha_composite(
            sheet,
            (
                (index % 2) * sheet.width,
                (index // 2) * sheet.height,
            ),
        )
    contact.save(
        family_review / "medium_perpendicular_family_review.png", "PNG", compress_level=9
    )

    report = {
        "schema_version": 1,
        "source_file": SOURCE,
        "source_sha256": SOURCE_SHA256,
        "canvas": list(CANVAS),
        "placement_profile_proposal": {
            "id": "britannia_mod:medium_perpendicular",
            "dimensions": {"width_blocks": 1, "height_blocks": 2},
            "orientations": ["wall_perpendicular"],
            "orientation_mount_geometry": {
                "wall_perpendicular": "britannia_mod:banner/mount/wall_perpendicular",
            },
            "mount_materials": [
                "britannia_mod:brass",
                "britannia_mod:iron",
            ],
            "runtime_integration": "BLOCKED_PENDING_READY_INTAKE",
            "architecture_note": (
                "The current PlacementProfile codec requires both wall-orientation mount "
                "geometry entries or neither. This perpendicular-only proposal therefore "
                "cannot be integrated without either broadening the profile or making the "
                "mount map compatible with a single supported orientation."
            ),
        },
        "geometry_groups": geometry_records,
        "assets": asset_records,
        "family_review_sheet": (
            family_review / "medium_perpendicular_family_review.png"
        ).relative_to(root).as_posix(),
    }
    write_json(
        root / "content" / "banner-final-intake" / "medium_asset_report.json",
        report,
    )
    print(
        "Prepared eight perpendicular medium-family asset packages; all remain NOT_READY "
        "pending product-owner approval, provenance, and architecture review."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
