"""Generate assets/trafficcontrol/blockstates/traffic_rail.json.

facing drives the y rotation (the models are authored for facing=east), shape
drives which model/submodels are used.
"""
import json, os

Y = {"east": 0, "south": 90, "west": 180, "north": 270}
LEFT = {"model": "trafficcontrol:traffic_rail_left"}
RIGHT = {"model": "trafficcontrol:traffic_rail_right"}

STRAIGHT = {
    "straight": {},
    "straight_left": {"left": LEFT},
    "straight_right": {"right": RIGHT},
    "straight_both": {"left": LEFT, "right": RIGHT},
}
CORNERS = []
for arm in ("left", "right"):
    for turn in ("front", "back"):
        CORNERS += ["corner_%s_%s" % (arm, turn),
                    "diagonal_%s_%s" % (arm, turn),
                    "diagonal_%s_%s_bend_%s" % (arm, turn, arm),
                    "diagonal_%s_%s_bend_%s" % (arm, turn, turn)]
LEVELS = ["flat", "up", "down"]
SLOPES = ["slope_%s_%s" % (l, r) for l in LEVELS for r in LEVELS
          if not (l == "flat" and r == "flat")]

variants = {"normal": [{}], "inventory": [{}]}

for facing, y in Y.items():
    for shape, submodels in STRAIGHT.items():
        v = {"model": "trafficcontrol:traffic_rail"}
        if y:
            v["y"] = y
        if submodels:
            v["submodel"] = submodels
        variants["facing=%s,shape=%s" % (facing, shape)] = v
    for shape in CORNERS + SLOPES:
        v = {"model": "trafficcontrol:traffic_rail_" + shape}
        if y:
            v["y"] = y
        variants["facing=%s,shape=%s" % (facing, shape)] = v

out = {
    "forge_marker": 1,
    "defaults": {"model": "trafficcontrol:traffic_rail"},
    "variants": variants,
}
path = os.path.join("src", "main", "resources", "assets", "trafficcontrol",
                    "blockstates", "traffic_rail.json")
with open(path, "w", newline="\n") as fh:
    json.dump(out, fh, indent=4)
    fh.write("\n")
print("wrote", path, len(variants), "variants")
