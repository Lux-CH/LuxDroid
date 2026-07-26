#!/usr/bin/env python3
"""Recolor the vendored MapLibre styles into Apple Maps-like light/dark palettes.

Only paint colors are touched (background-color, fill-color, fill-outline-color,
fill-extrusion-color, line-color, text-color, text-halo-color). Layer order,
filters, layout and every other property are preserved byte-for-byte.

The pristine upstream styles are kept next to the recolored ones as *.orig.
"""

import json
import os
import shutil
from collections import OrderedDict

TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
STYLE_DIR = os.path.join(
    os.path.dirname(TOOLS_DIR), "app", "src", "main", "assets", "map_styles",
)
ORIG_DIR = os.path.join(TOOLS_DIR, "map_styles_orig")

COLOR_KEYS = (
    "background-color",
    "fill-color",
    "fill-outline-color",
    "fill-extrusion-color",
    "line-color",
    "text-color",
    "text-halo-color",
)

# ---------------------------------------------------------------- light -----

LIGHT_LAND = "#F4F2EC"
LIGHT_WATER = "#9DC6F0"
LIGHT_PARK = "#C8E6C0"
LIGHT_ROAD = "#FFFFFF"
LIGHT_CASING = "#E8E0D8"
LIGHT_MOTORWAY = "#FCD690"
LIGHT_MOTORWAY_CASING = "#F0BE6E"
LIGHT_RAIL = "#D5D0C9"
LIGHT_HALO = "rgba(255,255,255,0.85)"

LIGHT = {
    "background": {"background-color": LIGHT_LAND},

    "park": {"fill-color": LIGHT_PARK, "fill-outline-color": "#B7D9AC"},
    "park_outline": {"line-color": "#B7D9AC"},
    "landuse_residential": {"fill-color": "#EFECE5"},
    "landcover_wood": {"fill-color": "#C2E0B8"},
    "landcover_grass": {"fill-color": "#CFE8C4"},
    "landcover_ice": {"fill-color": "#E8EFF2"},
    "landcover_wetland": {"fill-color": "#D6E6D2"},
    "landcover_sand": {"fill-color": "#F0E6C8"},
    "landuse_pitch": {"fill-color": "#D9E7CE"},
    "landuse_track": {"fill-color": "#D9E7CE"},
    "landuse_cemetery": {"fill-color": "#DCE6CE"},
    "landuse_hospital": {"fill-color": "#F5E3E3"},
    "landuse_school": {"fill-color": "#EFEAD6"},

    "water": {"fill-color": LIGHT_WATER},
    "waterway_tunnel": {"line-color": LIGHT_WATER},
    "waterway_river": {"line-color": LIGHT_WATER},
    "waterway_other": {"line-color": LIGHT_WATER},

    "aeroway_fill": {"fill-color": "#E9E5DE"},
    "aeroway_runway": {"line-color": LIGHT_ROAD},
    "aeroway_taxiway": {"line-color": LIGHT_ROAD},

    "building": {"fill-color": "#EAE6DE", "fill-outline-color": "#DCD6CC"},
    "building-3d": {"fill-extrusion-color": "#EAE6DE"},

    "boundary_3": {"line-color": "#C9C3BA"},
    "boundary_2": {"line-color": "#ADA69D"},
    "boundary_disputed": {"line-color": "#ADA69D"},

    "waterway_line_label": {"text-color": "#6E9BC8", "text-halo-color": LIGHT_HALO},
    "water_name_point_label": {"text-color": "#5C87B8", "text-halo-color": LIGHT_HALO},
    "water_name_line_label": {"text-color": "#5C87B8", "text-halo-color": LIGHT_HALO},
    "highway-name-path": {"text-color": "#8A8378", "text-halo-color": LIGHT_HALO},
    "highway-name-minor": {"text-color": "#6B6B6B", "text-halo-color": LIGHT_HALO},
    "highway-name-major": {"text-color": "#6B6B6B", "text-halo-color": LIGHT_HALO},
    "label_other": {"text-color": "#6B6B6B", "text-halo-color": LIGHT_HALO},
    "label_village": {"text-color": "#555555", "text-halo-color": LIGHT_HALO},
    "label_town": {"text-color": "#4A4A4A", "text-halo-color": LIGHT_HALO},
    "label_state": {"text-color": "#7A7A7A", "text-halo-color": LIGHT_HALO},
    "label_city": {"text-color": "#3C3C3C", "text-halo-color": LIGHT_HALO},
    "label_city_capital": {"text-color": "#3C3C3C", "text-halo-color": LIGHT_HALO},
    "label_country_3": {"text-color": "#3C3C3C", "text-halo-color": LIGHT_HALO},
    "label_country_2": {"text-color": "#3C3C3C", "text-halo-color": LIGHT_HALO},
    "label_country_1": {"text-color": "#3C3C3C", "text-halo-color": LIGHT_HALO},
}


def _light_transportation(layer_id):
    """Roads share one rule set across tunnel_/road_/bridge_ prefixes."""
    name = layer_id
    for prefix in ("tunnel_", "road_", "bridge_"):
        if name.startswith(prefix):
            name = name[len(prefix):]
            break
    else:
        return None

    if name == "area_pattern":
        return {"fill-color": "#EDE9E1"}
    if "rail" in name:
        return {"line-color": LIGHT_RAIL}
    if name.endswith("_casing"):
        base = name[: -len("_casing")]
        if "motorway" in base or "link" in base:
            return {"line-color": LIGHT_MOTORWAY_CASING}
        return {"line-color": LIGHT_CASING}
    if "motorway" in name or name == "link":
        return {"line-color": LIGHT_MOTORWAY}
    return {"line-color": LIGHT_ROAD}


# ----------------------------------------------------------------- dark -----

DARK_LAND = "#242527"
DARK_LAND_ALT = "#2B2C2E"
DARK_WATER = "#1B2735"
DARK_PARK = "#263229"
DARK_ROAD = "#3A3B3D"
DARK_ROAD_MAJOR = "#4A4B4D"
DARK_ROAD_CASING = "#202123"
DARK_LABEL = "#9A9A9E"
DARK_HALO = "rgba(0,0,0,0.6)"

DARK = {
    "background": {"background-color": DARK_LAND},
    "water": {"fill-color": DARK_WATER},
    "waterway": {"line-color": DARK_WATER},
    "landcover_ice_shelf": {"fill-color": "#2E3033"},
    "landcover_glacier": {"fill-color": "#2E3033"},
    "landuse_residential": {"fill-color": DARK_LAND_ALT},
    "landcover_wood": {"fill-color": DARK_PARK},
    "landuse_park": {"fill-color": DARK_PARK},

    "water_name": {"text-color": "#7E96B4", "text-halo-color": DARK_HALO},

    "building": {"fill-color": "#2A2B2D", "fill-outline-color": "#313234"},

    "aeroway-taxiway": {"line-color": "#303133"},
    "aeroway-runway-casing": {"line-color": "#3A3B3D"},
    "aeroway-area": {"fill-color": "#2A2B2D"},
    "aeroway-runway": {"line-color": "#303133"},

    "road_area_pier": {"fill-color": "#2E2F31"},
    "road_pier": {"line-color": "#2E2F31"},

    "highway_path": {"line-color": "#313234"},
    "highway_minor": {"line-color": DARK_ROAD},
    "highway_major_casing": {"line-color": DARK_ROAD_CASING},
    "highway_major_inner": {"line-color": DARK_ROAD_MAJOR},
    "highway_major_subtle": {"line-color": DARK_ROAD},
    "highway_motorway_casing": {"line-color": DARK_ROAD_CASING},
    "highway_motorway_inner": {"line-color": "#55565A"},
    "highway_motorway_subtle": {"line-color": "#3F4042"},

    "railway_transit": {"line-color": "#3A3B3D"},
    "railway_transit_dashline": {"line-color": "#2A2B2D"},
    "railway_minor": {"line-color": "#3A3B3D"},
    "railway_minor_dashline": {"line-color": "#2A2B2D"},
    "railway": {"line-color": "#3A3B3D"},
    "railway_dashline": {"line-color": "#2A2B2D"},

    "highway_name_other": {"text-color": "#8A8A8E", "text-halo-color": "rgba(0,0,0,0.7)"},
    "highway_name_motorway": {"text-color": "#8A8A8E", "text-halo-color": "rgba(0,0,0,0.7)"},

    "boundary_state": {"line-color": "#3D3E40"},
    "boundary_country_z0-4": {"line-color": "#46474A"},
    "boundary_country_z5-": {"line-color": "#46474A"},
}

for _place in (
    "place_other", "place_suburb", "place_village", "place_town", "place_city",
    "place_city_large", "place_state", "place_country_other",
    "place_country_minor", "place_country_major",
):
    DARK[_place] = {"text-color": DARK_LABEL, "text-halo-color": DARK_HALO}


def recolor(style, table, fallback=None):
    changed = 0
    for layer in style.get("layers", []):
        layer_id = layer.get("id")
        rules = table.get(layer_id)
        if rules is None and fallback is not None:
            rules = fallback(layer_id)
        if not rules:
            continue
        paint = layer.get("paint")
        if paint is None:
            continue
        for key, value in rules.items():
            if key in COLOR_KEYS and key in paint:
                paint[key] = value
                changed += 1
    return changed


def load_source(path):
    os.makedirs(ORIG_DIR, exist_ok=True)
    original = os.path.join(ORIG_DIR, os.path.basename(path) + ".orig")
    if not os.path.exists(original):
        shutil.copyfile(path, original)
    with open(original, encoding="utf-8") as handle:
        return json.load(handle, object_pairs_hook=OrderedDict)


def write(path, style):
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(style, handle, ensure_ascii=False, indent=2)
        handle.write("\n")


def main():
    light_path = os.path.join(STYLE_DIR, "liberty_nopoi.json")
    dark_path = os.path.join(STYLE_DIR, "dark_nopoi.json")

    light = load_source(light_path)
    count = recolor(light, LIGHT, _light_transportation)
    write(light_path, light)
    print("liberty_nopoi.json: {} paint colors recolored".format(count))

    dark = load_source(dark_path)
    count = recolor(dark, DARK)
    write(dark_path, dark)
    print("dark_nopoi.json: {} paint colors recolored".format(count))


if __name__ == "__main__":
    main()
