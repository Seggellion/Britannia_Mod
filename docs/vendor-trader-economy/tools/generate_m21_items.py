#!/usr/bin/env python3
"""Vendor/Trader Milestone 21 Phase B: generate the missing economy items.

Creates, for every entry in NEW_ITEMS:
  - a registration line inserted into ItemRegistry.java (simple items, foods
    through the existing cookedFood helper so they carry the mod's real
    weight-restore food mechanics, gold jewelry through the existing
    MaterialQualityJewelryItem system);
  - a flat item model (parent item/generated, layer0 texture);
  - a generated 16x16 texture (pure-python PNG writer; simple palette sprites,
    honest placeholder art that is visually distinct per shape+color);
  - a lang entry.

Idempotent: entries already present in ItemRegistry are skipped; models,
textures and lang entries are only written when absent, so hand-improved art
is never clobbered by a re-run.
"""
import json
import os
import re
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
REGISTRY = os.path.join(REPO, "src/main/java/com/seggellion/britannia_mod/registry/ItemRegistry.java")
MODELS = os.path.join(REPO, "src/main/resources/assets/britannia_mod/models/item")
TEXTURES = os.path.join(REPO, "src/main/resources/assets/britannia_mod/textures/item")

# Items whose models point at REAL pre-existing textures instead of generated
# placeholder art (the jewelry convention the copper pieces already follow).
REAL_TEXTURES = {
    "gold_ring": "jewelry/ring_gold",
    "gold_necklace": "jewelry/necklace_gold",
    "gold_bead_necklace": "jewelry/beads",
    "gold_bracelet": "jewelry/bracelet_gold",
    "gold_earrings": "jewelry/earrings_gold",
}
LANG = os.path.join(REPO, "src/main/resources/assets/britannia_mod/lang/en_us.json")

ANCHOR = 'public static final DeferredHolder<Item, Item> TINKER_HAMMER = ITEMS.register("tinker_hammer",\n        () -> new Item(new Item.Properties().stacksTo(1))\n);'

# (id, display, kind, extra, shape, primary RGB, secondary RGB)
# kind: simple | food(nutrition, saturation, foodType, multiplierConst) | jewelry(TYPE)
F = "food"
S = "simple"
J = "jewelry"
NEW_ITEMS = [
    # --- foods (real food mechanics through cookedFood) ---------------------
    ("apple_pie", "Apple Pie", F, (6, 0.7, "grain", "GRAIN_MULTIPLIER"), "pie", (214, 158, 86), (176, 58, 46)),
    ("french_bread", "French Bread", F, (5, 0.6, "grain", "GRAIN_MULTIPLIER"), "bread", (198, 146, 82), (150, 102, 54)),
    ("muffins", "Muffins", F, (4, 0.5, "grain", "GRAIN_MULTIPLIER"), "pie", (186, 132, 74), (222, 186, 132)),
    ("wasabi", "Wasabi", F, (1, 0.2, "grain", "GRAIN_MULTIPLIER"), "powder", (120, 168, 74), (86, 130, 52)),
    ("bento_box", "Bento Box", F, (8, 0.9, "fish", "FISH_MULTIPLIER"), "box", (140, 66, 50), (222, 202, 160)),
    ("sushi_rolls", "Sushi Rolls", F, (5, 0.6, "fish", "FISH_MULTIPLIER"), "sushi", (238, 238, 230), (46, 84, 52)),
    ("sushi_platter", "Sushi Platter", F, (8, 0.9, "fish", "FISH_MULTIPLIER"), "sushi", (222, 202, 160), (200, 90, 74)),
    ("green_tea", "Green Tea", F, (1, 0.3, "grain", "GRAIN_MULTIPLIER"), "cup", (168, 190, 120), (108, 140, 70)),
    ("miso_soup", "Miso Soup", F, (4, 0.5, "fish", "FISH_MULTIPLIER"), "bowl", (120, 82, 50), (196, 158, 96)),
    ("white_miso_soup", "White Miso Soup", F, (4, 0.5, "fish", "FISH_MULTIPLIER"), "bowl", (120, 82, 50), (226, 214, 182)),
    ("red_miso_soup", "Red Miso Soup", F, (4, 0.5, "fish", "FISH_MULTIPLIER"), "bowl", (120, 82, 50), (172, 92, 62)),
    ("awase_miso_soup", "Awase Miso Soup", F, (4, 0.5, "fish", "FISH_MULTIPLIER"), "bowl", (120, 82, 50), (204, 170, 110)),
    ("wooden_bowl_of_carrots", "Wooden Bowl of Carrots", F, (4, 0.5, "grain", "GRAIN_MULTIPLIER"), "bowl", (139, 94, 60), (226, 128, 46)),
    ("wooden_bowl_of_corn", "Wooden Bowl of Corn", F, (4, 0.5, "grain", "GRAIN_MULTIPLIER"), "bowl", (139, 94, 60), (232, 200, 82)),
    ("wooden_bowl_of_lettuce", "Wooden Bowl of Lettuce", F, (3, 0.4, "grain", "GRAIN_MULTIPLIER"), "bowl", (139, 94, 60), (128, 180, 84)),
    ("wooden_bowl_of_peas", "Wooden Bowl of Peas", F, (4, 0.5, "grain", "GRAIN_MULTIPLIER"), "bowl", (139, 94, 60), (94, 158, 74)),
    ("wooden_bowl_of_stew", "Wooden Bowl of Stew", F, (7, 0.8, "beef", "BEEF_MULTIPLIER"), "bowl", (139, 94, 60), (150, 84, 48)),
    ("wooden_bowl_of_tomato_soup", "Wooden Bowl of Tomato Soup", F, (5, 0.6, "grain", "GRAIN_MULTIPLIER"), "bowl", (139, 94, 60), (192, 62, 44)),
    ("pewter_bowl_of_corn", "Pewter Bowl of Corn", F, (4, 0.5, "grain", "GRAIN_MULTIPLIER"), "bowl", (150, 150, 158), (232, 200, 82)),
    ("pewter_bowl_of_lettuce", "Pewter Bowl of Lettuce", F, (3, 0.4, "grain", "GRAIN_MULTIPLIER"), "bowl", (150, 150, 158), (128, 180, 84)),
    ("pewter_bowl_of_peas", "Pewter Bowl of Peas", F, (4, 0.5, "grain", "GRAIN_MULTIPLIER"), "bowl", (150, 150, 158), (94, 158, 74)),
    ("pewter_bowl_of_potatos", "Pewter Bowl of Potatoes", F, (5, 0.6, "grain", "GRAIN_MULTIPLIER"), "bowl", (150, 150, 158), (212, 180, 130)),
    # --- provisions / household --------------------------------------------
    ("beverage_bottle", "Beverage Bottle", S, None, "bottle", (96, 130, 96), (210, 220, 210)),
    ("bandage", "Bandage", S, None, "spool", (232, 226, 210), (204, 196, 176)),
    ("empty_pewter_bowl", "Empty Pewter Bowl", S, None, "bowl", (150, 150, 158), (120, 120, 128)),
    ("backpack", "Backpack", S, None, "bag", (128, 88, 52), (92, 62, 38)),
    ("bag", "Bag", S, None, "bag", (160, 116, 70), (120, 84, 50)),
    ("pouch", "Pouch", S, None, "bag", (172, 130, 82), (132, 96, 58)),
    ("wooden_box", "Wooden Box", S, None, "box", (139, 94, 60), (102, 68, 44)),
    ("bedroll", "Bedroll", S, None, "spool", (120, 100, 140), (88, 72, 104)),
    ("kindling", "Kindling", S, None, "sticks", (139, 94, 60), (102, 68, 44)),
    ("jar_of_honey", "Jar of Honey", S, None, "bottle", (222, 168, 62), (160, 116, 40)),
    ("beeswax", "Beeswax", S, None, "powder", (226, 190, 98), (188, 152, 70)),
    ("rolling_pin", "Rolling Pin", S, None, "sticks", (196, 152, 102), (139, 94, 60)),
    ("skillet", "Skillet", S, None, "bowl", (72, 72, 78), (52, 52, 58)),
    ("butcher_knife", "Butcher Knife", S, None, "blade", (198, 202, 210), (110, 74, 44)),
    ("key_ring", "Key Ring", S, None, "ring", (186, 158, 88), (140, 116, 60)),
    ("heating_stand", "Heating Stand", S, None, "stand", (120, 120, 128), (90, 90, 98)),
    ("mortar_pestle", "Mortar and Pestle", S, None, "bowl", (168, 162, 152), (196, 190, 180)),
    # --- games --------------------------------------------------------------
    ("checker_board", "Checker Board", S, None, "board", (222, 202, 160), (110, 74, 44)),
    ("backgammon", "Backgammon Game", S, None, "board", (150, 104, 62), (222, 202, 160)),
    ("dices", "Dice and Cup", S, None, "dice", (238, 238, 230), (60, 60, 66)),
    # --- textiles / tailoring ----------------------------------------------
    ("bolt_of_cloth", "Bolt of Cloth", S, None, "spool", (176, 88, 88), (140, 66, 66)),
    ("uncut_cloth", "Uncut Cloth", S, None, "clothsq", (176, 88, 88), (150, 74, 74)),
    ("cloth", "Cloth", S, None, "clothsq", (198, 198, 208), (166, 166, 178)),
    ("wool", "Wool", S, None, "powder", (232, 230, 224), (204, 200, 192)),
    ("spool_of_thread", "Spool of Thread", S, None, "spool", (200, 176, 140), (150, 104, 62)),
    ("dark_yarn", "Dark Yarn", S, None, "spool", (86, 70, 92), (60, 48, 66)),
    ("light_yarn", "Light Yarn", S, None, "spool", (214, 206, 188), (182, 172, 152)),
    ("sewing_kit", "Sewing Kit", S, None, "box", (150, 104, 62), (198, 202, 210)),
    # --- scribe / navigation ------------------------------------------------
    ("scribes_pen", "Scribe's Pen", S, None, "pen", (222, 218, 206), (110, 74, 44)),
    ("blue_book", "Blue Book", S, None, "book", (70, 96, 160), (48, 66, 116)),
    ("tan_book", "Tan Book", S, None, "book", (198, 168, 120), (150, 122, 84)),
    ("red_book", "Red Book", S, None, "book", (164, 62, 52), (118, 42, 36)),
    ("brown_book", "Brown Book", S, None, "book", (128, 88, 52), (92, 62, 38)),
    ("sextant", "Sextant", S, None, "gear", (186, 158, 88), (140, 116, 60)),
    ("sextant_parts", "Sextant Parts", S, None, "gear", (150, 150, 158), (186, 158, 88)),
    # --- tinker parts -------------------------------------------------------
    ("clock", "Clock", S, None, "gear", (198, 168, 120), (110, 74, 44)),
    ("clock_parts", "Clock Parts", S, None, "gear", (150, 150, 158), (110, 110, 118)),
    ("gears", "Gears", S, None, "gear", (168, 168, 176), (120, 120, 128)),
    ("axle_gears", "Axle with Gears", S, None, "gear", (139, 94, 60), (168, 168, 176)),
    ("axle", "Axle", S, None, "sticks", (139, 94, 60), (168, 168, 176)),
    ("hinge", "Hinge", S, None, "stand", (150, 150, 158), (110, 110, 118)),
    ("springs", "Springs", S, None, "spool", (168, 168, 176), (120, 120, 128)),
    ("nails", "Nails", S, None, "sticks", (168, 168, 176), (120, 120, 128)),
    ("tongs", "Tongs", S, None, "blade", (110, 110, 118), (72, 72, 78)),
    # --- carpentry tools ----------------------------------------------------
    ("draw_knife", "Draw Knife", S, None, "blade", (198, 202, 210), (139, 94, 60)),
    ("froe", "Froe", S, None, "blade", (186, 190, 198), (139, 94, 60)),
    ("scorp", "Scorp", S, None, "blade", (176, 180, 188), (139, 94, 60)),
    ("inshave", "Inshave", S, None, "blade", (166, 170, 178), (139, 94, 60)),
    ("dovetail_saw", "Dovetail Saw", S, None, "saw", (198, 202, 210), (139, 94, 60)),
    ("saw", "Saw", S, None, "saw", (186, 190, 198), (110, 74, 44)),
    ("moulding_plane", "Moulding Plane", S, None, "box", (160, 112, 66), (198, 202, 210)),
    ("smoothing_plane", "Smoothing Plane", S, None, "box", (150, 104, 62), (198, 202, 210)),
    ("jointing_plane", "Jointing Plane", S, None, "box", (139, 94, 60), (198, 202, 210)),
    # --- gems (jeweler stock) ----------------------------------------------
    ("star_sapphire", "Star Sapphire", S, None, "gem", (78, 110, 196), (140, 168, 232)),
    ("ruby", "Ruby", S, None, "gem", (188, 44, 54), (232, 110, 118)),
    ("citrine", "Citrine", S, None, "gem", (226, 178, 62), (240, 212, 128)),
    # amethyst + tourmaline are NOT generated: BlacksmithItemRegistry already
    # registers every craftables ingredient key as an item; duplicating them
    # leaves a null hole in the registry (caught by noItemIdIsRegisteredTwice).
    # Their models/textures/lang entries ARE maintained here.
    ("amber", "Amber", S, None, "gem", (206, 130, 42), (232, 176, 96)),
    # --- necromantic reagents (physical goods; magic retail stays deferred) --
    ("black_pearl", "Black Pearl", S, None, "gem", (56, 56, 68), (108, 108, 128)),
    ("bat_wing", "Bat Wing", S, None, "wing", (74, 62, 84), (50, 42, 58)),
    ("daemon_blood", "Daemon Blood", S, None, "bottle", (128, 30, 30), (180, 54, 54)),
    ("pig_iron", "Pig Iron", S, None, "powder", (96, 96, 104), (68, 68, 76)),
    ("nox_crystal", "Nox Crystal", S, None, "gem", (98, 168, 74), (150, 212, 120)),
    ("grave_dust", "Grave Dust", S, None, "powder", (168, 162, 152), (128, 122, 112)),
    # --- clothing (tradable goods; wearability is future work) --------------
    ("floppy_hat", "Floppy Hat", S, None, "hat", (110, 96, 140), (82, 70, 108)),
    ("wide_brim_hat", "Wide-Brim Hat", S, None, "hat", (150, 104, 62), (110, 74, 44)),
    ("cap", "Cap", S, None, "hat", (96, 110, 140), (70, 82, 108)),
    ("tall_straw_hat", "Tall Straw Hat", S, None, "hat", (216, 192, 120), (178, 154, 88)),
    ("wizards_hat", "Wizard's Hat", S, None, "hat", (70, 62, 130), (100, 90, 176)),
    ("feathered_hat", "Feathered Hat", S, None, "hat", (140, 62, 62), (222, 218, 206)),
    ("tricorne_hat", "Tricorne Hat", S, None, "hat", (72, 60, 48), (110, 92, 72)),
    ("bandana", "Bandana", S, None, "clothsq", (164, 62, 52), (198, 90, 78)),
    ("skull_cap", "Skull Cap", S, None, "hat", (98, 98, 108), (72, 72, 80)),
    ("shirt", "Shirt", S, None, "shirt", (190, 190, 200), (150, 150, 162)),
    ("fancy_shirt", "Fancy Shirt", S, None, "shirt", (206, 178, 226), (162, 130, 188)),
    ("short_pants", "Short Pants", S, None, "pants", (96, 110, 140), (70, 82, 108)),
    ("long_pants", "Long Pants", S, None, "pants", (84, 74, 60), (60, 52, 42)),
    ("plain_dress", "Plain Dress", S, None, "dress", (168, 150, 128), (132, 116, 96)),
    ("fancy_dress", "Fancy Dress", S, None, "dress", (170, 92, 130), (128, 62, 96)),
    ("half_apron", "Half Apron", S, None, "clothsq", (222, 218, 206), (186, 180, 164)),
    ("robe", "Robe", S, None, "dress", (110, 96, 140), (82, 70, 108)),
    ("doublet", "Doublet", S, None, "shirt", (140, 62, 62), (104, 44, 44)),
    ("tunic", "Tunic", S, None, "shirt", (94, 130, 88), (66, 96, 62)),
    ("jester_suit", "Jester Suit", S, None, "dress", (188, 62, 62), (222, 178, 62)),
    ("jester_hat", "Jester Hat", S, None, "hat", (188, 62, 62), (222, 178, 62)),
    ("kilt", "Kilt", S, None, "pants", (94, 110, 74), (140, 62, 62)),
    ("cloak", "Cloak", S, None, "dress", (60, 74, 96), (42, 52, 70)),
    # SE cloth clothing (wave 4): plain cloth garments, no armor mechanics.
    ("kasa", "Kasa", S, None, "hat", (206, 182, 112), (168, 144, 84)),
    ("cloth_ninja_hood", "Cloth Ninja Hood", S, None, "hat", (54, 54, 62), (38, 38, 46)),
    ("monk_robe", "Monk Robe", S, None, "dress", (140, 110, 72), (106, 82, 52)),
    ("thigh_boots", "Thigh Boots", S, None, "boots", (110, 74, 44), (80, 54, 32)),
    ("shoes", "Shoes", S, None, "boots", (128, 88, 52), (92, 62, 38)),
    ("boots", "Boots", S, None, "boots", (139, 94, 60), (102, 68, 44)),
    ("sandals", "Sandals", S, None, "boots", (196, 152, 102), (150, 116, 78)),
    ("bamboo_flute", "Bamboo Flute", S, None, "sticks", (188, 176, 110), (150, 138, 80)),
    ("sheaf_of_hay", "Sheaf of Hay", S, None, "sticks", (216, 192, 120), (178, 154, 88)),
    # --- gold jewelry (existing material-quality system, GOLD tier) ---------
    ("gold_ring", "Gold Ring", J, "RING", "ring", (222, 178, 62), (250, 216, 120)),
    ("gold_necklace", "Gold Necklace", J, "NECKLACE", "ring", (222, 178, 62), (250, 216, 120)),
    ("gold_bead_necklace", "Gold Bead Necklace", J, "BEADS", "ring", (222, 178, 62), (250, 216, 120)),
    ("gold_bracelet", "Gold Bracelet", J, "BRACELET", "ring", (222, 178, 62), (250, 216, 120)),
    ("gold_earrings", "Gold Earrings", J, "EARRINGS", "gem", (222, 178, 62), (250, 216, 120)),
]


# ---------------------------------------------------------------- PNG writer
def write_png(path, pixels):
    """pixels: 16x16 rows of (r,g,b,a)."""
    raw = b"".join(b"\x00" + b"".join(struct.pack("4B", *px) for px in row) for row in pixels)
    def chunk(tag, data):
        payload = tag + data
        return struct.pack(">I", len(data)) + payload + struct.pack(">I", zlib.crc32(payload) & 0xFFFFFFFF)
    header = struct.pack(">2I5B", 16, 16, 8, 6, 0, 0, 0)
    with open(path, "wb") as fh:
        fh.write(b"\x89PNG\r\n\x1a\n")
        fh.write(chunk(b"IHDR", header))
        fh.write(chunk(b"IDAT", zlib.compress(raw, 9)))
        fh.write(chunk(b"IEND", b""))


def blank():
    return [[(0, 0, 0, 0)] * 16 for _ in range(16)]


def put(px, x, y, color, shade=0):
    if 0 <= x < 16 and 0 <= y < 16:
        r, g, b = color
        f = 1.0 + shade * 0.18
        px[y][x] = (max(0, min(255, int(r * f))), max(0, min(255, int(g * f))),
                    max(0, min(255, int(b * f))), 255)


def rect(px, x0, y0, x1, y1, color, shade=0):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(px, x, y, color, shade)


def outline(px, x0, y0, x1, y1, color):
    for x in range(x0, x1 + 1):
        put(px, x, y0, color, -2)
        put(px, x, y1, color, -2)
    for y in range(y0, y1 + 1):
        put(px, x0, y, color, -2)
        put(px, x1, y, color, -2)


def shape_pixels(shape, primary, secondary):
    px = blank()
    if shape == "pie":
        rect(px, 3, 6, 12, 11, primary)
        rect(px, 4, 5, 11, 5, primary, 1)
        rect(px, 5, 7, 10, 9, secondary)
        outline(px, 3, 5, 12, 11, primary)
    elif shape == "bread":
        rect(px, 2, 7, 13, 10, primary)
        rect(px, 3, 6, 12, 6, primary, 1)
        for x in (4, 7, 10):
            put(px, x, 7, secondary, 1)
        outline(px, 2, 6, 13, 10, primary)
    elif shape == "bowl":
        rect(px, 3, 8, 12, 11, primary)
        rect(px, 4, 12, 11, 12, primary, -1)
        rect(px, 4, 7, 11, 8, secondary)
        outline(px, 3, 7, 12, 12, primary)
    elif shape == "cup":
        rect(px, 5, 6, 10, 11, primary)
        rect(px, 6, 6, 9, 7, secondary, 1)
        put(px, 11, 8, primary); put(px, 12, 8, primary); put(px, 12, 9, primary); put(px, 11, 10, primary)
    elif shape == "sushi":
        for cx in (4, 8, 12):
            rect(px, cx - 1, 6, cx + 1, 9, primary)
            put(px, cx, 7, secondary)
        rect(px, 2, 10, 13, 11, secondary, -1)
    elif shape == "bottle":
        rect(px, 6, 2, 9, 4, secondary)
        rect(px, 5, 5, 10, 12, primary)
        rect(px, 6, 6, 7, 8, secondary, 2)
        outline(px, 5, 5, 10, 12, primary)
    elif shape == "box":
        rect(px, 3, 5, 12, 12, primary)
        rect(px, 3, 5, 12, 7, primary, 1)
        rect(px, 5, 8, 10, 10, secondary)
        outline(px, 3, 5, 12, 12, primary)
    elif shape == "bag":
        rect(px, 4, 6, 11, 12, primary)
        rect(px, 5, 4, 10, 5, secondary)
        rect(px, 6, 3, 9, 3, secondary, -1)
        outline(px, 4, 6, 11, 12, primary)
    elif shape == "spool":
        rect(px, 4, 4, 11, 5, secondary)
        rect(px, 4, 11, 11, 12, secondary)
        rect(px, 5, 6, 10, 10, primary)
        for y in (7, 9):
            rect(px, 5, y, 10, y, primary, 1)
    elif shape == "clothsq":
        rect(px, 3, 4, 12, 12, primary)
        for d in range(4, 12, 3):
            rect(px, 3, d, 12, d, secondary)
        outline(px, 3, 4, 12, 12, primary)
    elif shape == "book":
        rect(px, 4, 3, 11, 12, primary)
        rect(px, 4, 3, 5, 12, primary, -2)
        rect(px, 7, 5, 10, 5, secondary, 2)
        outline(px, 4, 3, 11, 12, primary)
    elif shape == "pen":
        for i in range(9):
            put(px, 4 + i, 12 - i, primary)
            put(px, 5 + i, 12 - i, secondary, -1)
        put(px, 3, 13, secondary, -2)
    elif shape == "gem":
        for dy in range(6):
            width = dy if dy <= 2 else 5 - dy
            for dx in range(-width, width + 1):
                put(px, 8 + dx, 5 + dy, primary if abs(dx) == width else secondary)
        put(px, 8, 6, secondary, 2)
    elif shape == "powder":
        rect(px, 4, 10, 11, 12, primary)
        rect(px, 5, 9, 10, 9, primary, 1)
        rect(px, 6, 8, 9, 8, secondary, 1)
    elif shape == "wing":
        for i in range(6):
            put(px, 4 + i, 10 - i, primary)
            put(px, 5 + i, 10 - i, primary, -1)
            put(px, 4 + i, 11 - i, secondary, -1)
    elif shape == "blade":
        for i in range(7):
            put(px, 4 + i, 9 - i, primary)
            put(px, 4 + i, 10 - i, primary, -1)
        rect(px, 3, 11, 5, 13, secondary)
    elif shape == "saw":
        rect(px, 3, 6, 11, 8, primary)
        for x in range(3, 12, 2):
            put(px, x, 9, primary, -2)
        rect(px, 11, 5, 13, 9, secondary)
    elif shape == "hat":
        rect(px, 5, 5, 10, 8, primary)
        rect(px, 3, 9, 12, 10, primary, -1)
        rect(px, 5, 8, 10, 8, secondary)
    elif shape == "shirt":
        rect(px, 5, 4, 10, 11, primary)
        rect(px, 3, 4, 4, 7, primary, -1)
        rect(px, 11, 4, 12, 7, primary, -1)
        rect(px, 7, 4, 8, 5, secondary)
    elif shape == "pants":
        rect(px, 5, 4, 10, 6, primary)
        rect(px, 5, 7, 7, 12, primary, -1)
        rect(px, 8, 7, 10, 12, primary, -1)
        rect(px, 5, 4, 10, 4, secondary)
    elif shape == "dress":
        rect(px, 6, 3, 9, 6, primary)
        for dy in range(7):
            rect(px, 6 - dy // 2, 6 + dy, 9 + dy // 2, 6 + dy, primary if dy % 2 == 0 else secondary)
    elif shape == "boots":
        rect(px, 4, 5, 6, 12, primary)
        rect(px, 4, 11, 8, 12, primary, -1)
        rect(px, 9, 5, 11, 12, secondary)
        rect(px, 9, 11, 13, 12, secondary, -1)
    elif shape == "ring":
        for dy in range(-3, 4):
            for dx in range(-3, 4):
                d2 = dx * dx + dy * dy
                if 4 <= d2 <= 9:
                    put(px, 8 + dx, 8 + dy, primary if d2 > 6 else secondary)
        put(px, 8, 4, secondary, 2)
    elif shape == "gear":
        for dy in range(-4, 5):
            for dx in range(-4, 5):
                d2 = dx * dx + dy * dy
                if 6 <= d2 <= 16:
                    put(px, 8 + dx, 8 + dy, primary)
                elif d2 < 3:
                    put(px, 8 + dx, 8 + dy, secondary)
        for dx, dy in ((0, -5), (0, 5), (-5, 0), (5, 0)):
            put(px, 8 + dx, 8 + dy, primary, -1)
    elif shape == "board":
        rect(px, 3, 4, 12, 11, secondary)
        for y in range(4, 12):
            for x in range(3, 13):
                if (x + y) % 2 == 0:
                    put(px, x, y, primary)
        outline(px, 3, 4, 12, 11, secondary)
    elif shape == "dice":
        rect(px, 4, 5, 8, 9, primary)
        outline(px, 4, 5, 8, 9, primary)
        put(px, 6, 7, secondary, -3)
        rect(px, 8, 8, 12, 12, primary, -1)
        put(px, 9, 9, secondary, -3)
        put(px, 11, 11, secondary, -3)
    elif shape == "sticks":
        for i in range(9):
            put(px, 3 + i, 11 - i, primary)
            put(px, 4 + i, 12 - i, secondary, -1)
            put(px, 5 + i, 11 - i, primary, -1)
    elif shape == "stand":
        rect(px, 7, 4, 8, 11, primary)
        rect(px, 4, 12, 11, 12, primary, -1)
        rect(px, 5, 4, 10, 5, secondary)
    else:
        rect(px, 4, 4, 11, 11, primary)
        rect(px, 6, 6, 9, 9, secondary)
    return px


def main():
    registry = open(REGISTRY, encoding="utf-8", errors="replace").read()
    existing = set(re.findall(r'"([a-z0-9_]+)"', registry))

    java_lines = []
    lang = json.load(open(LANG, encoding="utf-8-sig"))
    created = skipped = 0

    for item_id, display, kind, extra, shape, primary, secondary in NEW_ITEMS:
        if item_id in existing:
            skipped += 1
        else:
            const = item_id.upper()
            if kind == F:
                nutrition, saturation, food_type, mult = extra
                java_lines.append(
                    f'public static final DeferredHolder<Item, WeightedCookedFoodItem> {const} = cookedFood(\n'
                    f'        "{item_id}", {nutrition}, {saturation}f, "{food_type}", '
                    f'WeightedCookedFoodItem.{mult});')
            elif kind == J:
                java_lines.append(
                    f'public static final DeferredHolder<Item, Item> {const} = ITEMS.register("{item_id}",\n'
                    f'    () -> new MaterialQualityJewelryItem(MaterialQualityJewelryItem.JewelryType.{extra},\n'
                    f'        MaterialQualityJewelryItem.UOMaterial.GOLD,\n'
                    f'        new Item.Properties()));')
            else:
                java_lines.append(
                    f'public static final DeferredHolder<Item, Item> {const} = ITEMS.register("{item_id}",\n'
                    f'        () -> new Item(new Item.Properties()));')
            created += 1

        # Gold jewelry reuses the REAL existing jewelry textures (the same
        # convention the copper pieces follow); no generated placeholder art.
        real_texture = REAL_TEXTURES.get(item_id)

        model_path = os.path.join(MODELS, item_id + ".json")
        if not os.path.exists(model_path):
            layer0 = f"britannia_mod:item/{real_texture or item_id}"
            with open(model_path, "w", encoding="utf-8", newline="\n") as fh:
                json.dump({"parent": "item/generated",
                           "textures": {"layer0": layer0}}, fh, indent=2)
                fh.write("\n")

        if not real_texture:
            texture_path = os.path.join(TEXTURES, item_id + ".png")
            if not os.path.exists(texture_path):
                write_png(texture_path, shape_pixels(shape, primary, secondary))

        lang.setdefault(f"item.britannia_mod.{item_id}", display)

    if java_lines:
        block = ("\n\n// Vendor/Trader Milestone 21: economy items generated by\n"
                 "// docs/vendor-trader-economy/tools/generate_m21_items.py -- foods carry the\n"
                 "// mod's real weight-restore mechanics via cookedFood; gold jewelry uses the\n"
                 "// existing material-quality system; the rest are tradable goods whose deeper\n"
                 "// mechanics (wearing, containers, tools) arrive with their own features.\n"
                 + "\n\n".join(java_lines))
        assert ANCHOR in registry, "ItemRegistry anchor moved"
        registry = registry.replace(ANCHOR, ANCHOR + block, 1)
        open(REGISTRY, "w", encoding="utf-8", newline="").write(registry)

    with open(LANG, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(lang, fh, indent=2, ensure_ascii=False, sort_keys=True)
        fh.write("\n")

    print(f"registered {created} new items ({skipped} already existed); models/textures/lang ensured")


if __name__ == "__main__":
    main()
