"""Generate the sloped guardrail models (traffic_rail_slope_*).

Frame of reference: facing=east, like every other traffic_rail model - the
blockstate applies y=90/180/270 for south/west/north.  In that frame the run
goes along Z, "left" is -Z and "right" is +Z.

A sloped run is a staircase: every rail block sits one block along and one block
lower (or higher) than the previous one, which makes the beam a continuous 45
degree line passing through the beam centre (z=8, y=11) of every block.  So a
block is simply TWO INDEPENDENT HALVES that meet at that centre, each half being
flat, rising towards its edge, or falling towards it:

    FLAT   z=8,y=11  ->  edge, y=11
    UP     z=8,y=11  ->  edge, y=19
    DOWN   z=8,y=11  ->  edge, y=3

Because both halves always meet at y=11 in the middle of the block, the post is
unchanged and a slope lines up with a flat rail without any special transition
piece: (FLAT, DOWN) is the top of a ramp, (UP, FLAT) the bottom of one, and
(UP, DOWN) a block in the middle of it.

Element rotation about X is right handed:
    y' = y*cos(a) - z*sin(a)
    z' = y*sin(a) + z*cos(a)
(same convention as the Y rotation verified in gen_corner_models.py).

Flat halves reuse the straight model's own profile elements, tilted flanges and
all, so the seam with a normal rail is invisible.  Sloped halves cannot: an
element carries a single rotation and theirs is spent on the 45 degree tilt, so
they fall back to the squared-off profile the corner models already use.
"""

import json
import math
import os

ASSETS = os.path.join("src", "main", "resources", "assets", "trafficcontrol")
MODELS = os.path.join(ASSETS, "models", "block")

CENTRE_Y = 11.0            # beam centre line height at the middle of a block
CENTRE_Z = 8.0
HALF = 8.0 * math.sqrt(2)  # 11.31371 - length of a 45 degree half segment
OVER = 0.5                 # overlap at the block edge and across the middle

STRAIGHT = json.load(open(os.path.join(MODELS, "traffic_rail.json")))
POSTS = [e for e in STRAIGHT["elements"] if e["name"].startswith("Post")]
FLAT_PROFILE = [e for e in STRAIGHT["elements"] if not e["name"].startswith("Post")]

# Squared-off profile for the tilted halves, matching the corner models.
TILTED_PROFILE = [
    ("Beam", 5.5, 6.5, 8.0, 16.0, 0.0, 8.0),
    ("UpperFlange", 4.4, 5.6, 12.0, 15.0, 2.0, 5.0),
    ("LowerFlange", 4.4, 5.6, 6.0, 9.0, 6.0, 9.0),
]


def r(v):
    return round(v + 0.0, 5)


def flat_half(side):
    """Untilted half reusing the straight rail's own profile."""
    z0, z1 = (0.0, CENTRE_Z + OVER) if side == "left" else (CENTRE_Z - OVER, 16.0)
    out = []
    for src in FLAT_PROFILE:
        e = json.loads(json.dumps(src))
        e["name"] = side.capitalize() + e["name"]
        e["from"][2] = r(z0)
        e["to"][2] = r(z1)
        out.append(e)
    return out


def tilted_half(side, level):
    """Half rotated 45 degrees about X so it climbs or drops towards its edge."""
    # Which way the half points, as (dz, dy) from the block centre outwards.
    dz = -1.0 if side == "left" else 1.0
    dy = 1.0 if level == "up" else -1.0

    # Lay the box along Z and solve R_x(angle) so its outer end lands on (dz, dy).
    # For a right half the outer end is at relative z=+L, giving y'=-L*sin(a);
    # a left half is laid along -Z instead, which flips the sign.
    angle = -45 if (dz * dy > 0) else 45

    if side == "left":
        z0, z1 = CENTRE_Z - HALF - OVER, CENTRE_Z + OVER
    else:
        z0, z1 = CENTRE_Z - OVER, CENTRE_Z + HALF + OVER
    length = z1 - z0

    out = []
    for name, x0, x1, y0, y1, v0, v1 in TILTED_PROFILE:
        w = x1 - x0
        out.append({
            "name": side.capitalize() + name,
            "from": [r(x0), r(y0), r(z0)],
            "to": [r(x1), r(y1), r(z1)],
            "rotation": {"angle": angle, "axis": "x",
                         "origin": [8, r(CENTRE_Y), r(CENTRE_Z)]},
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


def half(side, level):
    return flat_half(side) if level == "flat" else tilted_half(side, level)


def build(left, right):
    name = "traffic_rail_slope_%s_%s" % (left, right)
    model = {
        "__comment": "Sloped guardrail - generated, see tools/gen_slope_models.py",
        "textures": dict(STRAIGHT["textures"]),
        "elements": half("left", left) + half("right", right)
                    + json.loads(json.dumps(POSTS)),
    }
    path = os.path.join(MODELS, name + ".json")
    with open(path, "w", newline="\n") as fh:
        json.dump(model, fh, indent=4)
        fh.write("\n")
    print("wrote", path)


LEVELS = ["flat", "up", "down"]
for left in LEVELS:
    for right in LEVELS:
        if left == "flat" and right == "flat":
            continue  # that is just a straight rail
        build(left, right)
