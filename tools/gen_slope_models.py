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

WHERE THE PIECES MEET.  A box is always cut square to its own axis, so how two
pieces are joined depends entirely on the angle between them:

  * Along the beam (block edges, and the middle of a block whose two halves are
    collinear) the pieces must meet EXACTLY, never overlap.  Two collinear
    prisms that overlap have coplanar side faces, and coplanar faces z-fight -
    that is the shimmer you see when the camera moves.  Cut square and flush,
    the touching end caps face away from each other and backface culling hides
    them.

  * At a bend, cutting both pieces square leaves a wedge open on the outside of
    the bend - the gap at the top of the beam where a ramp starts, and at the
    bottom of the elbow where it ends.  There each piece is extended past the
    centre until its outermost corner reaches the bisector, which is a mitre
    joint: extension = r * tan(bend/2), where r is how far that element reaches
    from the beam centre line.  Per element, so nothing overshoots.  The two
    pieces cross at an angle, so the overlap does not z-fight.

Flat halves reuse the straight model's own profile elements, tilted flanges and
all, so the seam with a normal rail is invisible.  Sloped halves cannot - an
element carries a single rotation and theirs is spent on the 45 degree tilt - so
they rebuild the same silhouette out of horizontal bands instead, measured off
the straight model itself.  Same envelope, so the seam still lines up.
"""

import json
import math
import os

ASSETS = os.path.join("src", "main", "resources", "assets", "trafficcontrol")
MODELS = os.path.join(ASSETS, "models", "block")

CENTRE_Y = 11.0            # beam centre line height at the middle of a block
CENTRE_Z = 8.0
HALF = 8.0 * math.sqrt(2)  # 11.31371 - length of a 45 degree half segment

def r(v):
    return round(v + 0.0, 5)


STRAIGHT = json.load(open(os.path.join(MODELS, "traffic_rail.json")))
POSTS = [e for e in STRAIGHT["elements"] if e["name"].startswith("Post")]
FLAT_PROFILE = [e for e in STRAIGHT["elements"] if not e["name"].startswith("Post")]

TILTED_BANDS = 7           # horizontal slices approximating the W profile


def _profile_quads():
    """The straight profile's five elements as XY polygons, rotations applied."""
    quads = []
    for e in FLAT_PROFILE:
        rot = e.get("rotation")
        pts = []
        for x in (e["from"][0], e["to"][0]):
            for y in (e["from"][1], e["to"][1]):
                if rot and rot["axis"] == "z":
                    a = math.radians(rot["angle"])
                    ox, oy = rot["origin"][0], rot["origin"][1]
                    dx, dy = x - ox, y - oy
                    pts.append((ox + dx * math.cos(a) - dy * math.sin(a),
                                oy + dx * math.sin(a) + dy * math.cos(a)))
                else:
                    pts.append((x, y))
        quads.append([pts[0], pts[1], pts[3], pts[2]])   # cyclic order
    return quads


def _silhouette(y, quads):
    """x range the profile covers at height y, or None above/below it."""
    lo = hi = None
    for quad in quads:
        crossings = []
        for i in range(4):
            (x1, y1), (x2, y2) = quad[i], quad[(i + 1) % 4]
            if (y1 - y) * (y2 - y) <= 0 and y1 != y2:
                crossings.append(x1 + (x2 - x1) * (y - y1) / (y2 - y1))
        if crossings:
            lo = min(crossings) if lo is None else min(lo, min(crossings))
            hi = max(crossings) if hi is None else max(hi, max(crossings))
    return None if lo is None else (lo, hi)


def _tilted_profile():
    """Stack of boxes matching the straight profile's silhouette.

    A tilted element already spends its single rotation on the 45 degree tilt,
    so the W cannot be rebuilt from angled flanges the way the straight model
    does it.  Slicing the real silhouette into horizontal bands keeps the same
    envelope instead - which is what makes the seam with a flat half line up,
    the previous squared-off profile being 0.7px too short at the top.
    """
    quads = _profile_quads()

    step = 0.02
    ys = [6.0 + i * step for i in range(int(11.0 / step) + 1)]
    covered = [y for y in ys if _silhouette(y, quads)]
    top, bottom = max(covered), min(covered)

    bands = []
    height = (top - bottom) / TILTED_BANDS
    for i in range(TILTED_BANDS):
        y0 = bottom + i * height
        y1 = y0 + height
        xs = []
        y = y0
        while y <= y1 + 1e-9:
            got = _silhouette(y, quads)
            if got:
                xs += [got[0], got[1]]
            y += step
        bands.append(("Band%d" % i, r(min(xs)), r(max(xs)), r(y0), r(y1),
                      r(max(0.0, y0 - 1)), r(min(16.0, y1 - 1))))
    return bands


TILTED_PROFILE = _tilted_profile()


def reach(element):
    """How far this element gets from the beam centre line, in the bend plane.

    The straight model tilts its flanges with a Z rotation, which moves them in
    Y, so the corners have to be rotated before measuring.
    """
    rot = element.get("rotation")
    ys = [element["from"][1], element["to"][1]]

    if rot and rot["axis"] == "z":
        a = math.radians(rot["angle"])
        ox, oy = rot["origin"][0], rot["origin"][1]
        ys = []
        for x in (element["from"][0], element["to"][0]):
            for y in (element["from"][1], element["to"][1]):
                dx, dy = x - ox, y - oy
                ys.append(oy + dx * math.sin(a) + dy * math.cos(a))

    return max(abs(y - CENTRE_Y) for y in ys)


def bend(left, right):
    """Angle the beam turns through at the middle of the block, in degrees."""
    if left == "flat" or right == "flat":
        return 45.0
    if left != right:
        return 0.0      # up/down or down/up: the beam runs straight through
    return 90.0


def mitre(element_reach, angle):
    if angle == 0:
        return 0.0
    return element_reach * math.tan(math.radians(angle / 2))


def flat_half(side, angle):
    """Untilted half reusing the straight rail's own profile."""
    out = []
    for src in FLAT_PROFILE:
        e = json.loads(json.dumps(src))
        e["name"] = side.capitalize() + e["name"]
        over = mitre(reach(src), angle)
        if side == "left":
            e["from"][2], e["to"][2] = 0.0, r(CENTRE_Z + over)
        else:
            e["from"][2], e["to"][2] = r(CENTRE_Z - over), 16.0
        out.append(e)
    return out


def tilted_half(side, level, angle):
    """Half rotated 45 degrees about X so it climbs or drops towards its edge."""
    # Rotating about X leaves the x coordinate alone, so a tilted half keeps its
    # faces on the same x planes as everything else.  Where two pieces overlap at
    # a bend those faces end up coplanar and z-fight, which is the shimmer left
    # at the elbows.  Pulling each tilted half a hair inside - and the two of
    # them by different amounts, for the 90 degree bends where both are tilted -
    # puts those faces strictly inside the neighbouring solid, where they are
    # hidden.  0.05px is a thirtieth of a texture pixel: invisible.
    inset = 0.1 if side == "left" else 0.05

    # Which way the half points, as (dz, dy) from the block centre outwards.
    dz = -1.0 if side == "left" else 1.0
    dy = 1.0 if level == "up" else -1.0

    # Lay the box along Z and solve R_x(angle) so its outer end lands on (dz, dy).
    # For a right half the outer end is at relative z=+L, giving y'=-L*sin(a);
    # a left half is laid along -Z instead, which flips the sign.
    tilt = -45 if (dz * dy > 0) else 45

    out = []
    for name, x0, x1, y0, y1, v0, v1 in TILTED_PROFILE:
        w = x1 - x0
        over = mitre(max(abs(y0 - CENTRE_Y), abs(y1 - CENTRE_Y)), angle)
        if side == "left":
            z0, z1 = CENTRE_Z - HALF, CENTRE_Z + over
        else:
            z0, z1 = CENTRE_Z - over, CENTRE_Z + HALF
        length = z1 - z0

        out.append({
            "name": side.capitalize() + name,
            "from": [r(x0 + inset), r(y0), r(z0)],
            "to": [r(x1 - inset), r(y1), r(z1)],
            "rotation": {"angle": tilt, "axis": "x",
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


def half(side, level, angle):
    if level == "flat":
        return flat_half(side, angle)
    return tilted_half(side, level, angle)


def build(left, right):
    angle = bend(left, right)
    name = "traffic_rail_slope_%s_%s" % (left, right)
    model = {
        "__comment": "Sloped guardrail - generated, see tools/gen_slope_models.py",
        "textures": dict(STRAIGHT["textures"]),
        "elements": half("left", left, angle) + half("right", right, angle)
                    + json.loads(json.dumps(POSTS)),
    }
    path = os.path.join(MODELS, name + ".json")
    with open(path, "w", newline="\n") as fh:
        json.dump(model, fh, indent=4)
        fh.write("\n")
    print("wrote %s (coude %.0f deg)" % (os.path.basename(path), angle))


LEVELS = ["flat", "up", "down"]

if __name__ == "__main__":
    for left in LEVELS:
        for right in LEVELS:
            if left == "flat" and right == "flat":
                continue  # that is just a straight rail
            build(left, right)
