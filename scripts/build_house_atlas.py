#!/usr/bin/env python3
"""Build the player-housing sprite atlas + data file.

Crops only the sprites the game uses out of the licensed Rogue Adventure
interior sheet (NOT committed to the repo) and packs them into
assets/sprites/house_atlas.png, then writes assets/data/house_tiles.json
with atlas coordinates, footprints, and build costs.

Usage: python3 scripts/build_house_atlas.py [path-to-RA_Interior.png]
"""
import json
import os
import sys

from PIL import Image

DEFAULT_SRC = os.path.expanduser(
    "~/Downloads/Idle Fantasy/interior tilesets/RA_Interior.png")
GROUND_SRC = os.path.expanduser(
    "~/Downloads/Idle Fantasy/interior tilesets/RA_Ground_Tiles.png")
EXTRACTED_DIR = os.path.expanduser(
    "~/Downloads/Idle Fantasy/interior tilesets/extracted")

# Sprites sourced from hand-picked extracted files: key -> (filename, x, y, w, h).
# Bed verticals stack two beds; the bottom one (y 24) is the placeable sprite.
# Bed horizontals hold two mirrored side-on beds (left + right half).
BED_TYPES = ["default", "light", "dark"]
FILE_SPRITES = {}
for _t in BED_TYPES:
    for _suffix, _fname in (("", f"{_t}_bed"), ("_alt", f"{_t}_bed_alternate")):
        FILE_SPRITES[f"bed_{_t}{_suffix}"]     = (f"{_fname}_vertical.png", 0, 24, 16, 24)
        FILE_SPRITES[f"bed_{_t}{_suffix}_h"]   = (f"{_fname}_horizontal.png", 24, 0, 24, 19)
        FILE_SPRITES[f"bed_{_t}{_suffix}_h2"]  = (f"{_fname}_horizontal.png", 0, 0, 24, 19)

# Tables and chairs: hand-curated files, whole-file sprites (w/h read at build time).
CHAIR_FACINGS = ["north", "east", "south", "west"]
for _t in BED_TYPES:
    FILE_SPRITES[f"table_{_t}_small"]        = (f"{_t}_small_table.png", 0, 0, None, None)
    FILE_SPRITES[f"table_{_t}_large"]        = (f"{_t}_large_table.png", 0, 0, None, None)
    FILE_SPRITES[f"table_{_t}_long_v"]       = (f"{_t}_long_table_vertical.png", 0, 0, None, None)
    FILE_SPRITES[f"table_{_t}_long_h"]       = (f"{_t}_long_table_horizontal.png", 0, 0, None, None)
    FILE_SPRITES[f"table_{_t}_cloth"]        = (f"{_t}_table_with_tablecloth.png", 0, 0, None, None)
    FILE_SPRITES[f"table_{_t}_cloth_long_v"] = (f"{_t}_long_table_with_tablecloth_vertical.png", 0, 0, None, None)
    FILE_SPRITES[f"table_{_t}_cloth_long_h"] = (f"{_t}_long_table_with_tablecloth_horizontal.png", 0, 0, None, None)
    for _f in CHAIR_FACINGS:
        FILE_SPRITES[f"chair_{_t}_{_f[0]}"] = (f"{_t}_chair_{_f}.png", 0, 0, None, None)

# Table decorations: silver_table_decoration_<name>.png. Only placeable on tables.
TABLE_DECOR = ["cup", "cups", "jug", "narrow_pitcher", "plate", "plates",
               "tall_pitcher", "wide_pitcher"]
for _n in TABLE_DECOR:
    FILE_SPRITES[f"tabledecor_silver_{_n}"] = (f"silver_table_decoration_{_n}.png", 0, 0, None, None)

# Interior walls: hand-picked strips that snap to grid lines to partition rooms.
FILE_SPRITES["wall_v"] = ("wall_vertical.png", 0, 0, None, None)
FILE_SPRITES["wall_h"] = ("wall_horizontal.png", 0, 0, None, None)

# Barrels: <wood>_barrel.png (single) and <wood>_barrel_storage.png (stacked rack).
for _t in BED_TYPES:
    FILE_SPRITES[f"barrel_{_t}"] = (f"{_t}_barrel.png", 0, 0, None, None)
    FILE_SPRITES[f"barrel_storage_{_t}"] = (f"{_t}_barrel_storage.png", 0, 0, None, None)

# Wardrobes: <wood>_wardrobe.png, all 16x24.
for _t in BED_TYPES:
    FILE_SPRITES[f"wardrobe_{_t}"] = (f"{_t}_wardrobe.png", 0, 0, None, None)

# Nightstands: <wood>_nightstand[_variant_<n>].png, all 16x16.
for _t in BED_TYPES:
    FILE_SPRITES[f"nightstand_{_t}"]   = (f"{_t}_nightstand.png", 0, 0, None, None)
    FILE_SPRITES[f"nightstand_{_t}_1"] = (f"{_t}_nightstand_variant_1.png", 0, 0, None, None)
    FILE_SPRITES[f"nightstand_{_t}_2"] = (f"{_t}_nightstand_variant_2.png", 0, 0, None, None)

# Bookshelves: <wood>_bookshelf_variant_<n>.png, all 16x24.
for _t in BED_TYPES:
    for _n in range(1, 5):
        FILE_SPRITES[f"bookshelf_{_t}_{_n}"] = (f"{_t}_bookshelf_variant_{_n}.png", 0, 0, 16, 24)

# Wall shelves and quest boards (wall-mounted decor).
for _n in range(1, 9):
    FILE_SPRITES[f"wallshelf_{_n}"] = (f"shelf_variant_{_n}.png", 0, 0, None, None)
for _n in range(1, 4):
    FILE_SPRITES[f"questboard_{_n}"] = (f"quest_board_variant_{_n}.png", 0, 0, None, None)

# Framed wall art: <wood>_wall_art_<variant>.png, all 12x12.
WALL_ART_VARIANTS = ["sunshine_grass", "sunshine_ocean", "starry_night_grass", "starry_night_ocean"]
for _t in BED_TYPES:
    for _v in WALL_ART_VARIANTS:
        FILE_SPRITES[f"wall_art_{_t}_{_v}"] = (f"{_t}_wall_art_{_v}.png", 0, 0, 12, 12)

# key: (src_x, src_y, w, h) on the source sheet
STRUCTURAL = {
    # Wall faces trimmed to start at the wood cap (the black upper-wall band is cut).
    "wall_left":  (0, 34, 16, 30),
    "wall_mid":   (16, 34, 16, 30),
    "wall_right": (32, 34, 16, 30),
    "floor_brick": (0, 64, 32, 32),
    "floor_wood": (192, 144, 32, 16),
}

# Outdoor ground fill tiles (16x16 blob centers) cropped from RA_Ground_Tiles.png:
# 6 columns x 2 rows of variants, blob centers at a 64px pitch.
GROUND = {f"ground_{col + row * 6 + 1}": (32 + col * 64, 80 + row * 126, 16, 16)
          for row in (0, 1) for col in range(6)}

# key: (rect, footprint_w, footprint_h, wall_mounted, category,
#       level, coins, materials, xp)
ITEMS = {
    # -- furniture --
    # Beds come from the hand-picked FILE_SPRITES above; costs/levels defined here.
    # (rect is ignored for FILE_SPRITES keys; footprint 1x1, sprites overhang visually.)
    # -- wealth --
    # Literal gold on display: priced like the fortune it depicts.
    "gold_chest":       ((288, 240, 16, 16), 1, 1, False, "wealth", 40, 5000000, {"oak_plank": 20}, 400),
    "gold_nugget":      ((272, 240, 16, 16), 1, 1, False, "wealth", 30, 2000000, {}, 250),
    "gold_pile":        ((288, 216, 32, 24), 2, 1, False, "wealth", 50, 20000000, {}, 500),
    "gold_pile_gems":   ((256, 216, 32, 24), 2, 1, False, "wealth", 65, 50000000, {"sapphire": 3, "emerald": 3, "ruby": 3}, 800),
    # -- wall decor (drawn on the north wall) --
    # Framed wall art comes from FILE_SPRITES (12 pieces); game data added below.
    "helmet_trophy":    ((448, 64, 16, 16),  1, 1, True, "wall", 25, 0, {"steel_bar": 3}, 300),
    "shield_round":     ((464, 64, 16, 16),  1, 1, True, "wall", 28, 0, {"steel_bar": 3}, 320),
    "shield_kite":      ((480, 64, 16, 16),  1, 1, True, "wall", 32, 0, {"mithril_bar": 2}, 380),
    "swords_crossed":   ((496, 64, 16, 16),  1, 1, True, "wall", 35, 0, {"mithril_bar": 3}, 420),
    "lamp":             ((704, 112, 16, 16), 1, 1, True, "wall", 5, 0, {"plank": 15}, 100),
}

# Room purchase ladder: index 0 = second room (the starter room is free).
ROOMS = [
    {"level": 15, "coins": 25000,   "materials": {"plank": 150, "iron_nail": 200}, "xp": 1000},
    {"level": 30, "coins": 75000,   "materials": {"oak_plank": 200, "carved_stone": 100}, "xp": 2500},
    {"level": 50, "coins": 200000,  "materials": {"willow_plank": 300, "stone_block": 300}, "xp": 5000},
    {"level": 70, "coins": 500000,  "materials": {"maple_plank": 400, "stone_block": 500}, "xp": 9000},
    {"level": 90, "coins": 1500000, "materials": {"yew_plank": 500, "stone_block": 800}, "xp": 15000},
]

# Per-new-cell expansion cost by room index (starter room first).
EXPANSION = [
    {"coins": 2000,  "materials": {"plank": 10}, "xp": 100},
    {"coins": 4000,  "materials": {"oak_plank": 15}, "xp": 150},
    {"coins": 8000,  "materials": {"willow_plank": 20}, "xp": 220},
    {"coins": 15000, "materials": {"maple_plank": 30}, "xp": 320},
    {"coins": 25000, "materials": {"yew_plank": 40}, "xp": 420},
    {"coins": 40000, "materials": {"yew_plank": 50}, "xp": 500},
]

# Sprite anchor overrides (default "center"): how the sprite sits in its footprint.
ANCHORS = {
    "wall_v": "left_edge",
    "wall_h": "top_edge",
}
for _n in ["cup", "cups", "jug", "narrow_pitcher", "plate", "plates",
           "tall_pitcher", "wide_pitcher"]:
    ANCHORS[f"tabledecor_silver_{_n}"] = "table"

# Bed game data: base key -> (level, materials, xp). Alternates match their base.
BED_DATA = {
    "bed_default": (1,  {"plank": 40, "iron_nail": 20}, 200),
    "bed_light":   (20, {"oak_plank": 60, "steel_nail": 30}, 450),
    "bed_dark":    (35, {"willow_plank": 60, "steel_nail": 30}, 600),
}
for _t in BED_TYPES:
    _lvl, _mats, _xp = BED_DATA[f"bed_{_t}"]
    for _suffix in ("", "_alt"):
        for _facing in ("", "_h", "_h2"):
            ITEMS[f"bed_{_t}{_suffix}{_facing}"] = (
                (0, 0, 0, 0), 1, 1, False, "furniture", _lvl, 0, _mats, _xp)

# Table/chair game data. Wood tier shifts level and swaps materials.
WOOD_TIER = {
    "default": (0, "plank", "iron_nail", 1.0),
    "light":   (18, "oak_plank", "steel_nail", 1.5),
    "dark":    (32, "willow_plank", "steel_nail", 2.0),
}
TABLE_KINDS = {
    # kind -> (fw, fh, base_level, planks, nails, coins, xp)
    "small":        (1, 1, 3, 30, 10, 0, 150),
    "long_v":       (1, 2, 5, 50, 20, 0, 250),
    "long_h":       (2, 1, 5, 50, 20, 0, 250),
    # Square tables visually cover 2x2 cells, so their footprint matches (the whole
    # tabletop counts as table surface for decorations).
    "large":        (2, 2, 8, 70, 30, 0, 350),
    "cloth":        (2, 2, 12, 60, 20, 500, 400),
    "cloth_long_v": (1, 2, 12, 50, 20, 500, 350),
    "cloth_long_h": (2, 1, 12, 50, 20, 500, 350),
}
for _t in BED_TYPES:
    _dl, _plank, _nail, _xpm = WOOD_TIER[_t]
    for _kind, (_fw, _fh, _lvl, _p, _n, _c, _xp) in TABLE_KINDS.items():
        ITEMS[f"table_{_t}_{_kind}"] = ((0, 0, 0, 0), _fw, _fh, False, "furniture",
                                        _lvl + _dl, _c, {_plank: _p, _nail: _n}, int(_xp * _xpm))
    for _f in CHAIR_FACINGS:
        ITEMS[f"chair_{_t}_{_f[0]}"] = ((0, 0, 0, 0), 1, 1, False, "furniture",
                                        1 + _dl, 0, {_plank: 20, _nail: 10}, int(100 * _xpm))

# Table decoration game data: bought silverware, sits on tables only.
for _n in TABLE_DECOR:
    ITEMS[f"tabledecor_silver_{_n}"] = ((0, 0, 0, 0), 1, 1, False, "furniture",
                                        10, 2500, {}, 100)

# Interior wall game data: cheap, spammable partition pieces.
ITEMS["wall_v"] = ((0, 0, 0, 0), 1, 1, False, "walls", 5, 0, {"plank": 30, "iron_nail": 10}, 150)
ITEMS["wall_h"] = ((0, 0, 0, 0), 1, 1, False, "walls", 5, 0, {"plank": 30, "iron_nail": 10}, 150)

# Barrel game data by wood type.
for _t in BED_TYPES:
    _dl, _plank, _nail, _xpm = WOOD_TIER[_t]
    ITEMS[f"barrel_{_t}"] = ((0, 0, 0, 0), 1, 1, False, "furniture",
                             8 + _dl, 0, {_plank: 30, _nail: 10}, int(180 * _xpm))
    ITEMS[f"barrel_storage_{_t}"] = ((0, 0, 0, 0), 1, 1, False, "furniture",
                                     16 + _dl, 0, {_plank: 60, _nail: 20}, int(360 * _xpm))

# Wardrobe game data by wood type.
for _t in BED_TYPES:
    _dl, _plank, _nail, _xpm = WOOD_TIER[_t]
    ITEMS[f"wardrobe_{_t}"] = ((0, 0, 0, 0), 1, 1, False, "furniture",
                               12 + _dl, 0, {_plank: 70, _nail: 30}, int(450 * _xpm))

# Nightstand game data: three variants per wood, priced like small furniture.
for _t in BED_TYPES:
    _dl, _plank, _nail, _xpm = WOOD_TIER[_t]
    for _key in (f"nightstand_{_t}", f"nightstand_{_t}_1", f"nightstand_{_t}_2"):
        ITEMS[_key] = ((0, 0, 0, 0), 1, 1, False, "furniture",
                       3 + _dl, 0, {_plank: 25, _nail: 10}, int(120 * _xpm))

# Bookshelf game data by wood type.
BOOKSHELF_DATA = {
    "default": (15, {"oak_plank": 50, "iron_nail": 20}, 350),
    "light":   (25, {"oak_plank": 60, "steel_nail": 25}, 450),
    "dark":    (38, {"willow_plank": 60, "steel_nail": 30}, 600),
}
for _t in BED_TYPES:
    _lvl, _mats, _xp = BOOKSHELF_DATA[_t]
    for _n in range(1, 5):
        ITEMS[f"bookshelf_{_t}_{_n}"] = ((0, 0, 0, 0), 1, 1, False, "furniture", _lvl, 0, _mats, _xp)

# Wall shelf / quest board game data.
for _n in range(1, 9):
    ITEMS[f"wallshelf_{_n}"] = ((0, 0, 0, 0), 1, 1, True, "wall", 8, 0,
                                {"plank": 25, "iron_nail": 10}, 150)
for _n in range(1, 4):
    ITEMS[f"questboard_{_n}"] = ((0, 0, 0, 0), 2, 1, True, "wall", 20, 0,
                                 {"plank": 40, "iron_nail": 15}, 300)

# Wall art game data: level scales with the frame wood.
WALL_ART_LEVELS = {"default": 18, "light": 25, "dark": 32}
for _t in BED_TYPES:
    for _v in WALL_ART_VARIANTS:
        ITEMS[f"wall_art_{_t}_{_v}"] = (
            (0, 0, 0, 0), 1, 1, True, "wall", WALL_ART_LEVELS[_t], 500, {"oak_plank": 25}, 200)

# Rotation cycles.
ROTATES = {"wall_v": "wall_h", "wall_h": "wall_v"}
for _t in BED_TYPES:
    for _suffix in ("", "_alt"):
        _base = f"bed_{_t}{_suffix}"
        ROTATES[_base] = _base + "_h"
        ROTATES[_base + "_h"] = _base + "_h2"
        ROTATES[_base + "_h2"] = _base
    for _kind in ("long", "cloth_long"):
        ROTATES[f"table_{_t}_{_kind}_v"] = f"table_{_t}_{_kind}_h"
        ROTATES[f"table_{_t}_{_kind}_h"] = f"table_{_t}_{_kind}_v"
    # chairs cycle clockwise through the four facings
    ROTATES[f"chair_{_t}_n"] = f"chair_{_t}_e"
    ROTATES[f"chair_{_t}_e"] = f"chair_{_t}_s"
    ROTATES[f"chair_{_t}_s"] = f"chair_{_t}_w"
    ROTATES[f"chair_{_t}_w"] = f"chair_{_t}_n"

# Rotated variants stay out of the palette; they are reached by rotating.
HIDDEN = {k for k in ROTATES if k.endswith("_h") or k.endswith("_h2")}
HIDDEN |= {f"chair_{_t}_{f}" for _t in BED_TYPES for f in ("e", "s", "w")}
HIDDEN |= {f"table_{_t}_long_h" for _t in BED_TYPES}
HIDDEN |= {f"table_{_t}_cloth_long_h" for _t in BED_TYPES}
# But _v is also in ROTATES; make sure visible bases stay visible.
HIDDEN -= {f"table_{_t}_{k}_v" for _t in BED_TYPES for k in ("long", "cloth_long")}
HIDDEN -= {f"chair_{_t}_n" for _t in BED_TYPES}
# Both wall orientations are first-class palette entries, not hidden variants.
HIDDEN -= {"wall_h"}

# Hidden rotation variants display their cycle's visible member's name.
NAME_KEYS = {}
for _k in HIDDEN:
    _cur = _k
    for _ in range(6):
        _cur = ROTATES.get(_cur, _cur)
        if _cur not in HIDDEN:
            break
    NAME_KEYS[_k] = _cur

CELL_W, CELL_H, COLS = 64, 40, 8


def main():
    src_path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_SRC
    src = Image.open(src_path).convert("RGBA")
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

    ground_src = Image.open(GROUND_SRC).convert("RGBA")
    keys = list(STRUCTURAL) + list(GROUND) + list(ITEMS)
    rows = (len(keys) + COLS - 1) // COLS
    atlas = Image.new("RGBA", (COLS * CELL_W, rows * CELL_H), (0, 0, 0, 0))
    coords = {}
    for i, key in enumerate(keys):
        if key in FILE_SPRITES:
            fname, fx, fy, fw, fh = FILE_SPRITES[key]
            sheet = Image.open(os.path.join(EXTRACTED_DIR, fname)).convert("RGBA")
            if fw is None:
                fw, fh = sheet.width - fx, sheet.height - fy
            rect = (fx, fy, fw, fh)
        elif key in GROUND:
            rect, sheet = GROUND[key], ground_src
        elif key in STRUCTURAL:
            rect, sheet = STRUCTURAL[key], src
        else:
            rect, sheet = ITEMS[key][0], src
        x, y, w, h = rect
        assert w <= CELL_W and h <= CELL_H, f"{key} exceeds atlas cell"
        ax, ay = (i % COLS) * CELL_W, (i // COLS) * CELL_H
        atlas.paste(sheet.crop((x, y, x + w, y + h)), (ax, ay))
        coords[key] = (ax, ay, w, h)

    out_png = os.path.join(repo, "app/src/main/assets/sprites/house_atlas.png")
    atlas.save(out_png)

    data = {
        "structural": {k: dict(zip("xywh", coords[k])) for k in STRUCTURAL},
        "grounds": {k: dict(zip("xywh", coords[k])) for k in GROUND},
        "items": {},
        "rooms": ROOMS,
        "expansion": EXPANSION,
    }
    for key, (_, fw, fh, wall, cat, level, coins, mats, xp) in ITEMS.items():
        x, y, w, h = coords[key]
        data["items"][key] = {
            "x": x, "y": y, "w": w, "h": h,
            "footprint_w": fw, "footprint_h": fh,
            "wall_mounted": wall, "category": cat,
            "anchor": ANCHORS.get(key, "center"),
            "rotates_to": ROTATES.get(key),
            "hidden": key in HIDDEN,
            "name_key": NAME_KEYS.get(key),
            "table_only": key.startswith("tabledecor_"),
            "level_required": level, "coin_cost": coins,
            "materials": mats, "xp": xp,
        }
    out_json = os.path.join(repo, "app/src/main/assets/data/house_tiles.json")
    with open(out_json, "w") as f:
        json.dump(data, f, indent=1)
    print(f"atlas: {out_png} {atlas.size}")
    print(f"data:  {out_json} ({len(ITEMS)} items)")


if __name__ == "__main__":
    main()
