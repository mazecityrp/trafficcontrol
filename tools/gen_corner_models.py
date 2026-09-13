"""Generate the four diagonal-corner models for trafficcontrol's guardrail (traffic_rail).

Frame of reference: the model is authored for facing=east (the blockstate applies
y=90/180/270 for south/west/north, exactly like the straight rail model).

For facing=east the straight rail beam runs along Z at x=6 (plate x 5.5..6.5) and
the post sits at x 6.5..9.5 / z 6.5..9.5.  A corner block replaces that straight
beam with a single 45 degree diagonal that joins the beam line of the arm arriving
along Z to the beam line of the perpendicular arm.

Element rotation about Y in Minecraft is right handed:
    x' = x*cos(a) + z*sin(a)
    z' = -x*sin(a) + z*cos(a)
(verified against crossing_gate_lamps_{ne,nw}_support.json in this repo).
"""

import json
import math
import os

OUT = os.path.join(
    "src", "main", "resources", "assets", "trafficcontrol", "models", "block"
)

SHORT = 6 * math.sqrt(2)   # 8.48528  - diagonal that cuts a block corner
LONG = 10 * math.sqrt(2)   # 14.14214 - diagonal that crosses the block
OVER = 0.5                 # mitre overlap so the joint has no notch


def r(v):
    return round(v + 0.0, 5)


# Cross section of the beam, in the un-rotated frame (x = across, y = height).
# The straight model tilts its flanges with a Z rotation; an element can only
# carry one rotation, so the diagonal keeps the same envelope with square flanges.
PROFILE = [
    ("Beam", 5.5, 6.5, 8.0, 16.0, 0.0, 8.0),
    ("UpperFlange", 4.4, 5.6, 12.0, 15.0, 2.0, 5.0),
    ("LowerFlange", 4.4, 5.6, 6.0, 9.0, 6.0, 9.0),
]


def beam_elements(origin_x, origin_z, angle, z_from, z_to):
    """Profile boxes stretched along Z then pivoted around (origin_x, origin_z)."""
    length = z_to - z_from
    out = []
    for name, x0, x1, y0, y1, v0, v1 in PROFILE:
        w = x1 - x0
        h = y1 - y0
        out.append({
            "name": name,
            "from": [r(x0), r(y0), r(z_from)],
            "to": [r(x1), r(y1), r(z_to)],
            "rotation": {"angle": angle, "axis": "y",
                         "origin": [r(origin_x), 16, r(origin_z)]},
            "faces": {
                "north": {"uv": [0, r(v0), r(w), r(v1)], "texture": "#1"},
                "south": {"uv": [0, r(v0), r(w), r(v1)], "texture": "#1"},
                "east": {"uv": [0, r(v0), r(min(length, 16)), r(v1)], "texture": "#1"},
                "west": {"uv": [0, r(v0), r(min(length, 16)), r(v1)], "texture": "#1"},
                "up": {"uv": [0, 0, r(w), r(min(length, 16))], "texture": "#1"},
                "down": {"uv": [0, 0, r(w), r(min(length, 16))], "texture": "#1"},
            },
        })
    return out


def post_elements(cx, cz):
    """The 3x3 post, kept axis aligned, tucked against the back of the diagonal."""
    x0, x1 = cx - 1.5, cx + 1.5
    z0, z1 = cz - 1.5, cz + 1.5
    faces_lower = {
        side: {"uv": [0, 0, 3, 16], "texture": "#0"}
        for side in ("north", "east", "south", "west")
    }
    faces_lower["up"] = {"uv": [0, 0, 3, 3], "texture": "#-1"}
    faces_lower["down"] = {"uv": [0, 0, 3, 3], "texture": "#0"}

    faces_upper = {
        side: {"uv": [0, 0, 3, 4], "texture": "#0"}
        for side in ("north", "east", "south", "west")
    }
    faces_upper["up"] = {"uv": [0, 0, 3, 3], "texture": "#0"}
    faces_upper["down"] = {"uv": [0, 0, 3, 3], "texture": "#-1"}

    return [
        {"name": "Post", "from": [r(x0), 0, r(z0)], "to": [r(x1), 16, r(z1)],
         "faces": faces_lower},
        {"name": "Post2", "from": [r(x0), 16, r(z0)], "to": [r(x1), 20, r(z1)],
         "faces": faces_upper},
    ]


def build(name, origin_z, angle, length, forward, post):
    """forward=True: the beam runs towards +Z from the origin, else towards -Z."""
    if forward:
        z_from, z_to = origin_z - OVER, origin_z + length + OVER
    else:
        z_from, z_to = origin_z - length - OVER, origin_z + OVER

    model = {
        "__comment": "Diagonal guardrail corner - generated, see tools/gen_corner_models.py",
        "textures": {
            "0": "blocks/log_oak",
            "1": "trafficcontrol:blocks/generic",
            "particle": "trafficcontrol:blocks/generic",
        },
        "elements": beam_elements(6.0, origin_z, angle, z_from, z_to)
                    + post_elements(*post),
    }

    path = os.path.join(OUT, name + ".json")
    with open(path, "w", newline="\n") as fh:
        json.dump(model, fh, indent=4)
        fh.write("\n")
    print("wrote", path)


# entry on the LEFT edge (north, x=6) -> exit through the BACK edge (west, z=6)
build("traffic_rail_corner_left_back", 0.0, -45, SHORT, True, (4.8, 4.8))
# entry on the LEFT edge (north, x=6) -> exit through the FRONT edge (east, z=10)
build("traffic_rail_corner_left_front", 0.0, 45, LONG, True, (12.85, 3.15))
# entry on the RIGHT edge (south, x=6) -> exit through the BACK edge (west, z=10)
build("traffic_rail_corner_right_back", 16.0, 45, SHORT, False, (4.8, 11.2))
# entry on the RIGHT edge (south, x=6) -> exit through the FRONT edge (east, z=6)
build("traffic_rail_corner_right_front", 16.0, -45, LONG, False, (12.85, 12.85))
