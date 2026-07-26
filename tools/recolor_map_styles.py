#!/usr/bin/env python3
import json
import os
import shutil
import sys

STYLE_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app", "src", "main", "assets", "map_styles",
)

DARK = {
    "background": {"background-color": "#223026"},
    "water": {"fill-color": "#1C3448"},
    "waterway": {"line-color": "#1C3448"},
    "water_name": {"text-color": "#7FA3C4", "text-halo-color": "rgba(0,0,0,0.65)"},
    "landcover_ice_shelf": {"fill-color": "#2E3538"},
    "landcover_glacier": {"fill-color": "#2E3538"},
    "landuse_residential": {"fill-color": "#2C3238", "fill-opacity": 0.9},
    "landcover_wood": {
        "fill-color": "#1E2F23",
        "fill-opacity": ["interpolate", ["exponential", 0.3], ["zoom"], 8, 0, 10, 0.95, 13, 0.8],
    },
    "landuse_park": {"fill-color": "#26402C"},
    "building": {"fill-color": "#333A40", "fill-outline-color": "#3B424A"},
    "aeroway-taxiway": {"line-color": "#34383A"},
    "aeroway-runway-casing": {"line-color": "#40454A"},
    "aeroway-area": {"fill-color": "#2C3032"},
    "aeroway-runway": {"line-color": "#34383A"},
    "road_area_pier": {"fill-color": "#34383A"},
    "road_pier": {"line-color": "#34383A"},
    "highway_path": {"line-color": "#4A4E52"},
    "highway_minor": {"line-color": "#4A4E52"},
    "highway_major_casing": {"line-color": "#22262A"},
    "highway_major_inner": {"line-color": "#5C6166"},
    "highway_major_subtle": {"line-color": "#4A4E52"},
    "highway_motorway_casing": {"line-color": "#3C3B33"},
    "highway_motorway_inner": {"line-color": "#6B6A5E"},
    "highway_motorway_subtle": {"line-color": "#4E4F49"},
    "railway_transit": {"line-color": "#44494D"},
    "railway_transit_dashline": {"line-color": "#2C3134"},
    "railway_minor": {"line-color": "#44494D"},
    "railway_minor_dashline": {"line-color": "#2C3134"},
    "railway": {"line-color": "#44494D"},
    "railway_dashline": {"line-color": "#2C3134"},
    "highway_name_other": {"text-color": "#A8ADB2", "text-halo-color": "rgba(0,0,0,0.75)"},
    "highway_name_motorway": {"text-color": "#C0B99E", "text-halo-color": "rgba(0,0,0,0.75)"},
    "boundary_state": {"line-color": "#3D4245"},
    "boundary_country_z0-4": {"line-color": "#4A5054"},
    "boundary_country_z5-": {"line-color": "#4A5054"},
}

DARK_LABEL_LAYERS = (
    "place_other", "place_suburb", "place_village", "place_town", "place_city",
    "place_city_large", "place_state", "place_country_other", "place_country_minor",
    "place_country_major",
)
DARK_LABEL_PAINT = {"text-color": "#A8ADB2", "text-halo-color": "rgba(0,0,0,0.7)"}

LIGHT = {
    "park": {"fill-color": "#CDE8C4", "fill-outline-color": "#B5DCA8", "fill-opacity": 0.8},
    "park_outline": {"line-color": "#B5DCA8"},
    "landcover_wood": {"fill-color": "#BBDDB0", "fill-opacity": 0.55},
    "landcover_grass": {"fill-color": "#CDE8C4", "fill-opacity": 0.45},
    "landuse_pitch": {"fill-color": "#CFE6C2"},
    "landuse_track": {"fill-color": "#CFE6C2"},
    "landuse_cemetery": {"fill-color": "#D6E4C6"},
    "landuse_hospital": {"fill-color": "#F6DFDF"},
    "landuse_school": {"fill-color": "#F0E8D2"},
    "landcover_sand": {"fill-color": "#F2E7C6"},
    "water": {"fill-color": "#A8CCF0"},
    "waterway_river": {"line-color": "#A8CCF0"},
    "waterway_other": {"line-color": "#A8CCF0"},
    "waterway_tunnel": {"line-color": "#A8CCF0"},
    "waterway_line_label": {"text-color": "#5E8FC4"},
    "water_name_point_label": {"text-color": "#4F7FB5"},
    "water_name_line_label": {"text-color": "#4F7FB5"},
    "road_motorway": {"line-color": "#FBD690"},
    "road_motorway_link": {"line-color": "#FBD690"},
    "road_link": {"line-color": "#FBD690"},
    "tunnel_motorway": {"line-color": "#FBD690"},
    "tunnel_motorway_link": {"line-color": "#FBD690"},
    "tunnel_link": {"line-color": "#FBD690"},
    "bridge_motorway": {"line-color": "#FBD690"},
    "bridge_motorway_link": {"line-color": "#FBD690"},
    "bridge_link": {"line-color": "#FBD690"},
}

PALETTES = {"dark_nopoi.json": DARK, "liberty_nopoi.json": LIGHT}


def source_path(name):
    original = os.path.join(STYLE_DIR, name + ".orig")
    live = os.path.join(STYLE_DIR, name)
    if not os.path.exists(original):
        shutil.copyfile(live, original)
    return original


def apply(name, palette, label_layers=(), label_paint=None):
    with open(source_path(name)) as handle:
        style = json.load(handle)

    touched = 0
    for layer in style["layers"]:
        overrides = dict(palette.get(layer["id"], {}))
        if label_paint and layer["id"] in label_layers:
            overrides.update(label_paint)
        if not overrides:
            continue
        layer.setdefault("paint", {}).update(overrides)
        touched += 1

    with open(os.path.join(STYLE_DIR, name), "w") as handle:
        json.dump(style, handle, separators=(",", ":"))
    return touched


def main():
    for name, palette in PALETTES.items():
        if name == "dark_nopoi.json":
            count = apply(name, palette, DARK_LABEL_LAYERS, DARK_LABEL_PAINT)
        else:
            count = apply(name, palette)
        print(name, "recolored layers:", count)


if __name__ == "__main__":
    sys.exit(main())
