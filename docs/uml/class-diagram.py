#!/usr/bin/env python3
"""
Cinema Booking System - UML class diagram (editable source).

This file is the source of truth for docs/uml/class-diagram.svg and
docs/uml/class-diagram.png.  It lays the diagram out with explicit
coordinates (like a diagram drawn by hand in Astah) and renders the same
scene twice: once as SVG (vector, editable in Inkscape / Illustrator /
draw.io) and once as PNG.

Everything that is drawn comes from the three projects in this repository:

    CinemaClient      Blazor front end
    CinemaApi         ASP.NET Core minimal API + gRPC client
    CinemaApplication Spring Boot service, gRPC service, JPA entity
    PostgreSQL        screenings table (Flyway V1)

Run with:  python docs/uml/class-diagram.py
"""

import os
from PIL import Image, ImageDraw, ImageFont

OUT_DIR = os.path.dirname(os.path.abspath(__file__))
SVG_PATH = os.path.join(OUT_DIR, "class-diagram.svg")
PNG_PATH = os.path.join(OUT_DIR, "class-diagram.png")

# ---------------------------------------------------------------- fonts ----
REGULAR_CANDIDATES = [
    "C:/Windows/Fonts/arial.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
]
BOLD_CANDIDATES = [
    "C:/Windows/Fonts/arialbd.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]


def _pick(candidates):
    for path in candidates:
        if os.path.exists(path):
            return path
    raise SystemExit("No usable TrueType font found.")


FONT_REGULAR = _pick(REGULAR_CANDIDATES)
FONT_BOLD = _pick(BOLD_CANDIDATES)
_FONTS = {}


def font(size, bold=False):
    key = (size, bold)
    if key not in _FONTS:
        _FONTS[key] = ImageFont.truetype(
            FONT_BOLD if bold else FONT_REGULAR, size
        )
    return _FONTS[key]


def text_width(text, size, bold=False):
    box = font(size, bold).getbbox(text)
    return box[2] - box[0]


# ------------------------------------------------------------- styling ----
S_NAME = 13
S_STEREO = 11
S_MEMBER = 12
S_MULT = 11
S_FRAME = 12
S_TITLE = 21
S_SUBTITLE = 12
S_NOTE = 11

PAD_X = 12
ROW = 17
HEADER_H = 25
STEREO_H = 15
EMPTY_H = 11
LINE_W = 1
DASH = (6, 4)

INK = "#000000"
PAPER = "#ffffff"


# -------------------------------------------------------------- canvas ----
class Scene:
    """Backend-independent display list."""

    def __init__(self):
        self.ops = []

    def rect(self, x, y, w, h, width=LINE_W, dash=None, fill=PAPER):
        self.ops.append(("rect", x, y, w, h, width, dash, fill))

    def line(self, x1, y1, x2, y2, width=LINE_W, dash=None):
        self.ops.append(("line", x1, y1, x2, y2, width, dash))

    def polyline(self, pts, width=LINE_W, dash=None):
        self.ops.append(("poly", list(pts), width, dash))

    def polygon(self, pts, fill=INK, width=LINE_W):
        self.ops.append(("polygon", list(pts), fill, width))

    def text(self, x, y, s, size=S_MEMBER, bold=False, anchor="lm"):
        self.ops.append(("text", x, y, s, size, bold, anchor))


def render_svg(scene, width, height, path):
    def attrs(**kw):
        return " ".join(f'{k.replace("_", "-")}="{v}"' for k, v in kw.items())

    out = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" '
        f'height="{height}" viewBox="0 0 {width} {height}">',
        "<defs><style><![CDATA[",
        "text{font-family:Arial,Helvetica,sans-serif;fill:#000;}",
        "]]></style></defs>",
        f'<rect x="0" y="0" width="{width}" height="{height}" fill="#fff"/>',
    ]

    for op in scene.ops:
        kind = op[0]
        if kind == "rect":
            _, x, y, w, h, lw, dash, fill = op
            extra = f' stroke-dasharray="{dash[0]} {dash[1]}"' if dash else ""
            out.append(
                f'<rect x="{x}" y="{y}" width="{w}" height="{h}" '
                f'fill="{fill}" stroke="#000" stroke-width="{lw}"{extra}/>'
            )
        elif kind == "line":
            _, x1, y1, x2, y2, lw, dash = op
            extra = f' stroke-dasharray="{dash[0]} {dash[1]}"' if dash else ""
            out.append(
                f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" '
                f'stroke="#000" stroke-width="{lw}"{extra}/>'
            )
        elif kind == "poly":
            _, pts, lw, dash = op
            extra = f' stroke-dasharray="{dash[0]} {dash[1]}"' if dash else ""
            d = " ".join(f"{p[0]},{p[1]}" for p in pts)
            out.append(
                f'<polyline points="{d}" fill="none" stroke="#000" '
                f'stroke-width="{lw}"{extra}/>'
            )
        elif kind == "polygon":
            _, pts, fill, lw = op
            d = " ".join(f"{p[0]},{p[1]}" for p in pts)
            out.append(
                f'<polygon points="{d}" fill="{fill}" stroke="#000" '
                f'stroke-width="{lw}"/>'
            )
        elif kind == "text":
            _, x, y, s, size, bold, anchor = op
            s = (
                s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            )
            weight = ' font-weight="bold"' if bold else ""
            out.append(
                f'<text x="{x}" y="{y}" font-size="{size}" '
                f'text-anchor="{anchor}" dominant-baseline="central"'
                f"{weight}>{s}</text>"
            )

    out.append("</svg>")
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(out) + "\n")


def render_png(scene, width, height, path, scale=2):
    image = Image.new("RGB", (width * scale, height * scale), PAPER)
    draw = ImageDraw.Draw(image)

    def dashed(p1, p2, width_px, dash):
        on, off = dash[0] * scale, dash[1] * scale
        x1, y1 = p1[0] * scale, p1[1] * scale
        x2, y2 = p2[0] * scale, p2[1] * scale
        total = ((x2 - x1) ** 2 + (y2 - y1) ** 2) ** 0.5
        if total == 0:
            return
        ux, uy = (x2 - x1) / total, (y2 - y1) / total
        pos = 0.0
        while pos < total:
            end = min(pos + on, total)
            draw.line(
                [
                    (x1 + ux * pos, y1 + uy * pos),
                    (x1 + ux * end, y1 + uy * end),
                ],
                fill=INK,
                width=width_px,
            )
            pos += on + off

    for op in scene.ops:
        kind = op[0]
        if kind == "rect":
            _, x, y, w, h, lw, dash, fill = op
            box = [
                x * scale,
                y * scale,
                (x + w) * scale,
                (y + h) * scale,
            ]
            fill_color = None if fill == "none" else fill
            if dash:
                if fill_color:
                    draw.rectangle(box, fill=fill_color)
                corners = [
                    (x, y),
                    (x + w, y),
                    (x + w, y + h),
                    (x, y + h),
                    (x, y),
                ]
                for i in range(4):
                    dashed(corners[i], corners[i + 1], lw, dash)
            else:
                draw.rectangle(box, fill=fill_color, outline=INK, width=lw)
        elif kind == "line":
            _, x1, y1, x2, y2, lw, dash = op
            if dash:
                dashed((x1, y1), (x2, y2), lw, dash)
            else:
                draw.line(
                    [x1 * scale, y1 * scale, x2 * scale, y2 * scale],
                    fill=INK,
                    width=lw,
                )
        elif kind == "poly":
            _, pts, lw, dash = op
            for i in range(len(pts) - 1):
                if dash:
                    dashed(pts[i], pts[i + 1], lw, dash)
                else:
                    draw.line(
                        [
                            pts[i][0] * scale,
                            pts[i][1] * scale,
                            pts[i + 1][0] * scale,
                            pts[i + 1][1] * scale,
                        ],
                        fill=INK,
                        width=lw,
                    )
        elif kind == "polygon":
            _, pts, fill, lw = op
            draw.polygon(
                [(p[0] * scale, p[1] * scale) for p in pts],
                fill=fill,
                outline=INK,
            )
        elif kind == "text":
            _, x, y, s, size, bold, anchor = op
            pil_anchor = {"lm": "lm", "mm": "mm", "rm": "rm"}[anchor]
            draw.text(
                (x * scale, y * scale),
                s,
                font=font(int(round(size * scale)), bold),
                fill=INK,
                anchor=pil_anchor,
            )

    image.save(path, "PNG", dpi=(150, 150))


# -------------------------------------------------------------- classes ----
def klass(cid, name, stereo=None, attrs=(), methods=(), x=0, y=0, w=300):
    return {
        "id": cid,
        "name": name,
        "stereo": stereo,
        "attrs": list(attrs),
        "methods": list(methods),
        "x": x,
        "y": y,
        "w": w,
    }


def comp_height(rows):
    return max(EMPTY_H, 8 + len(rows) * ROW)


def measure(box):
    stereo_h = STEREO_H if box["stereo"] else 0
    box["stereo_h"] = stereo_h
    box["h"] = (
        stereo_h
        + HEADER_H
        + comp_height(box["attrs"])
        + comp_height(box["methods"])
    )
    box["header_h"] = stereo_h + HEADER_H
    box["attrs_top"] = box["y"] + box["header_h"]
    box["methods_top"] = box["attrs_top"] + comp_height(box["attrs"])

    needed = 0
    for line in [box["name"]] + box["attrs"] + box["methods"]:
        needed = max(needed, text_width(line, S_MEMBER, line == box["name"]))
    if box["stereo"]:
        needed = max(needed, text_width(box["stereo"], S_STEREO))
    if needed + 2 * PAD_X > box["w"]:
        print(
            f"  ! box '{box['id']}' content {needed + 2 * PAD_X}px "
            f"exceeds width {box['w']}px"
        )
    return box


CLASSES = [
    # ---- Blazor front end ------------------------------------------------
    klass(
        "home",
        "Home",
        "«Razor component»",
        attrs=[
            "- screenings : List<ScreeningDto>",
            "- isLoading : bool",
            "- errorMessage : string?",
            "- httpClientFactory : IHttpClientFactory",
        ],
        methods=[
            "+ OnInitializedAsync() : Task",
            "+ LoadScreenings() : Task",
            "+ FormatDate(value : string) : string",
            "+ FormatTime(value : string) : string",
        ],
        x=70,
        y=150,
        w=310,
    ),
    klass(
        "dto",
        "Home.ScreeningDto",
        None,
        attrs=[
            "- id : long",
            "- movieTitle : string",
            "- startTime : string",
            "- room : string",
        ],
        x=70,
        y=430,
        w=310,
    ),
    # ---- ASP.NET Core Web API -------------------------------------------
    klass(
        "program",
        "Program",
        None,
        attrs=["- grpcServerUrl : string"],
        methods=[
            '+ MapGet("/api/screenings")',
            '+ MapPost("/api/screenings", dto)',
        ],
        x=560,
        y=150,
        w=300,
    ),
    klass(
        "reqdto",
        "CreateScreeningRequestDto",
        "«record»",
        attrs=[
            "- MovieTitle : string",
            "- StartTime : string",
            "- Room : string",
        ],
        x=560,
        y=430,
        w=300,
    ),
    # ---- gRPC contract ---------------------------------------------------
    klass(
        "svc",
        "ScreeningService",
        "«interface, from screenings.proto»",
        methods=[
            "+ listScreenings(EmptyRequest) : ScreeningList",
            "+ createScreening(req) : CreateScreeningResponse",
        ],
        x=980,
        y=150,
        w=360,
    ),
    klass(
        "client",
        "ScreeningServiceClient",
        "«generated C# client»",
        methods=[
            "+ ListScreeningsAsync(EmptyRequest)",
            "+ CreateScreeningAsync(CreateScreeningRequest)",
        ],
        x=980,
        y=430,
        w=380,
    ),
    klass(
        "createreq",
        "CreateScreeningRequest",
        "«message»",
        attrs=[
            "- movie_title : string",
            "- start_time : string",
            "- room : string",
        ],
        x=980,
        y=700,
        w=310,
    ),
    klass(
        "createresp",
        "CreateScreeningResponse",
        "«message»",
        attrs=[
            "- success : bool",
            "- message : string",
            "- screening : Screening",
        ],
        x=1330,
        y=700,
        w=330,
    ),
    klass(
        "screening",
        "Screening",
        "«message»",
        attrs=[
            "- id : int64",
            "- movie_title : string",
            "- start_time : string",
            "- room : string",
        ],
        x=1480,
        y=150,
        w=300,
    ),
    klass(
        "screeninglist",
        "ScreeningList",
        "«message»",
        attrs=["- screenings : repeated Screening"],
        x=1480,
        y=350,
        w=300,
    ),
    # ---- Spring Boot service --------------------------------------------
    klass(
        "implbase",
        "ScreeningServiceImplBase",
        "«abstract, generated»",
        methods=[
            "+ listScreenings(EmptyRequest, StreamObserver)",
            "+ createScreening(CreateScreeningRequest, StreamObserver)",
        ],
        x=980,
        y=990,
        w=430,
    ),
    klass(
        "grpcsvc",
        "ScreeningGrpcService",
        "«@Service»",
        attrs=["- screeningRepository : ScreeningRepository"],
        methods=[
            "+ listScreenings(EmptyRequest, StreamObserver)",
        ],
        x=980,
        y=1210,
        w=400,
    ),
    klass(
        "javapp",
        "CinemaApplication",
        "«@SpringBootApplication»",
        methods=["+ main(args : String[]) : void"],
        x=70,
        y=1210,
        w=230,
    ),
    klass(
        "grpccfg",
        "GrpcServerConfig",
        "«@Configuration»",
        methods=["+ grpcServer(service) : Server"],
        x=450,
        y=1210,
        w=250,
    ),
    klass(
        "repo",
        "ScreeningRepository",
        "«interface, Spring Data»",
        methods=[
            "+ findAll() : List<ScreeningEntity>",
            "+ save(entity) : ScreeningEntity",
        ],
        x=1470,
        y=1210,
        w=310,
    ),
    klass(
        "entity",
        "ScreeningEntity",
        "«@Entity»",
        attrs=[
            "- id : Long",
            "- movieTitle : String",
            "- startTime : LocalDateTime",
            "- room : String",
        ],
        methods=[
            "+ getId() : Long",
            "+ getMovieTitle() : String",
            "+ getStartTime() : LocalDateTime",
            "+ getRoom() : String",
        ],
        x=1470,
        y=1440,
        w=310,
    ),
    # ---- PostgreSQL ------------------------------------------------------
    klass(
        "table",
        "screenings",
        "«table»",
        attrs=[
            "- id : BIGINT PK",
            "- movie_title : VARCHAR(255)",
            "- start_time : TIMESTAMP(6)",
            "- room : VARCHAR(255)",
        ],
        x=1870,
        y=1440,
        w=290,
    ),
]

BOXES = {}
for _box in CLASSES:
    measure(_box)
    BOXES[_box["id"]] = _box


# ------------------------------------------------------------- geometry ----
def anchor(spec):
    """('home', 'r', 200) -> point on the right edge of Home at y = 200."""
    cid, side, value = spec
    box = BOXES[cid]
    if side == "l":
        return (box["x"], value)
    if side == "r":
        return (box["x"] + box["w"], value)
    if side == "t":
        return (value, box["y"])
    if side == "b":
        return (value, box["y"] + box["h"])
    raise ValueError(side)


DIRS = {"l": (-1, 0), "r": (1, 0), "t": (0, -1), "b": (0, 1)}


def ortho(p1, d1, p2, d2, stub=24):
    """Simple orthogonal route out of p1 in direction d1 into p2 from d2."""
    def shift(p, d, amount):
        dx, dy = DIRS[d]
        return (p[0] + dx * amount, p[1] + dy * amount)

    s, e = shift(p1, d1, stub), shift(p2, d2, stub)
    pts = [p1, s]
    h1, h2 = d1 in "lr", d2 in "lr"

    if h1 and h2:
        if abs(s[1] - e[1]) < 1:
            pts.append(e)
        else:
            mid = (s[0] + e[0]) / 2
            pts += [(mid, s[1]), (mid, e[1]), e]
    elif (not h1) and (not h2):
        if abs(s[0] - e[0]) < 1:
            pts.append(e)
        else:
            mid = (s[1] + e[1]) / 2
            pts += [(s[0], mid), (e[0], mid), e]
    elif h1:
        pts += [(e[0], s[1]), e]
    else:
        pts += [(s[0], e[1]), e]

    pts.append(p2)
    cleaned = [pts[0]]
    for point in pts[1:]:
        if abs(point[0] - cleaned[-1][0]) > 0.5 or (
            abs(point[1] - cleaned[-1][1]) > 0.5
        ):
            cleaned.append(point)
    return cleaned


def polyline_length(pts):
    total = 0.0
    for i in range(len(pts) - 1):
        total += (
            (pts[i + 1][0] - pts[i][0]) ** 2
            + (pts[i + 1][1] - pts[i][1]) ** 2
        ) ** 0.5
    return total


def point_along(pts, distance):
    remaining = distance
    for i in range(len(pts) - 1):
        p, q = pts[i], pts[i + 1]
        seg = ((q[0] - p[0]) ** 2 + (q[1] - p[1]) ** 2) ** 0.5
        if seg == 0:
            continue
        if remaining <= seg:
            t = remaining / seg
            return (p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t)
        remaining -= seg
    return pts[-1]


# kind: dep | assoc | inherit | realize | composition
RELATIONS = [
    dict(
        kind="dep",
        a=("home", "r", 200),
        b=("program", "l", 200),
        route="direct",
        label="«HTTP GET /api/screenings»",
    ),
    dict(
        kind="composition",
        a=("home", "b", 225),
        b=("dto", "t", 225),
        route="direct",
        ma="1",
        mb="0..*",
    ),
    dict(
        kind="dep",
        a=("program", "b", 710),
        b=("reqdto", "t", 710),
        route="direct",
        label="«uses»",
    ),
    dict(
        kind="dep",
        a=("program", "r", 190),
        b=("client", "l", 470),
        via=[(880, 190), (880, 470)],
        label="«creates per request»",
        label_side="left",
        label_at=0.72,
        ma="1",
        mb="0..*",
    ),
    dict(
        kind="dep",
        a=("client", "t", 1120),
        b=("svc", "b", 1120),
        route="direct",
        label="«gRPC over HTTP/2»",
    ),
    dict(
        kind="dep",
        a=("client", "b", 1150),
        b=("createreq", "t", 1150),
        route="direct",
        label="«sends»",
    ),
    dict(
        kind="dep",
        a=("client", "r", 470),
        b=("createresp", "t", 1495),
        via=[(1385, 470), (1385, 680), (1495, 680)],
        label="«receives»",
    ),
    dict(
        kind="composition",
        a=("screeninglist", "t", 1630),
        b=("screening", "b", 1630),
        route="direct",
        ma="1",
        mb="0..*",
    ),
    dict(
        kind="composition",
        a=("createresp", "r", 760),
        b=("screening", "r", 200),
        via=[(1800, 760), (1800, 200)],
        ma="1",
        mb="0..1",
    ),
    dict(
        kind="realize",
        a=("implbase", "l", 1030),
        b=("svc", "l", 190),
        via=[(940, 1030), (940, 190)],
    ),
    dict(
        kind="inherit",
        a=("grpcsvc", "t", 1150),
        b=("implbase", "b", 1150),
        route="direct",
    ),
    dict(
        kind="assoc",
        a=("grpcsvc", "r", 1250),
        b=("repo", "l", 1250),
        route="direct",
        ma="1",
        mb="1",
        label="«uses»",
    ),
    dict(
        kind="assoc",
        a=("grpccfg", "r", 1240),
        b=("grpcsvc", "l", 1240),
        route="direct",
        ma="1",
        mb="1",
        label="«registers»",
    ),
    dict(
        kind="dep",
        a=("javapp", "r", 1240),
        b=("grpccfg", "l", 1240),
        route="direct",
        label="«bootstraps»",
    ),
    dict(
        kind="assoc",
        a=("repo", "b", 1625),
        b=("entity", "t", 1625),
        route="direct",
        ma="1",
        mb="0..*",
        label="«persists»",
    ),
    dict(
        kind="dep",
        a=("entity", "r", 1490),
        b=("table", "l", 1490),
        route="direct",
        label="«maps to»",
    ),
]


def resolve(rel):
    if "via" in rel:
        return [anchor(rel["a"])] + rel["via"] + [anchor(rel["b"])]
    if rel.get("route") == "direct":
        return [anchor(rel["a"]), anchor(rel["b"])]
    return ortho(
        anchor(rel["a"]), rel["a"][1], anchor(rel["b"]), rel["b"][1]
    )


# -------------------------------------------------------------- drawing ----
def draw_arrow(scene, pts, kind):
    (x1, y1), (x2, y2) = pts[-2], pts[-1]
    dx, dy = x2 - x1, y2 - y1
    length = (dx * dx + dy * dy) ** 0.5 or 1.0
    ux, uy = dx / length, dy / length
    px, py = -uy, ux

    if kind in ("dep", "assoc"):
        size, spread = 11, 0.42
        scene.line(
            x2,
            y2,
            x2 - ux * size + px * size * spread,
            y2 - uy * size + py * size * spread,
        )
        scene.line(
            x2,
            y2,
            x2 - ux * size - px * size * spread,
            y2 - uy * size - py * size * spread,
        )
    elif kind in ("inherit", "realize"):
        size, half = 15, 7
        base_x, base_y = x2 - ux * size, y2 - uy * size
        scene.polygon(
            [
                (x2, y2),
                (base_x + px * half, base_y + py * half),
                (base_x - px * half, base_y - py * half),
            ],
            fill=PAPER,
        )
    elif kind == "composition":
        sx, sy = pts[0]
        (nx, ny) = pts[1]
        vx, vy = nx - sx, ny - sy
        norm = (vx * vx + vy * vy) ** 0.5 or 1.0
        vx, vy = vx / norm, vy / norm
        qx, qy = -vy, vx
        depth, half = 16, 6
        mid1 = (sx + vx * (depth / 2), sy + vy * (depth / 2))
        mid2 = (sx + vx * depth, sy + vy * depth)
        scene.polygon(
            [
                (sx, sy),
                (mid1[0] + qx * half, mid1[1] + qy * half),
                mid2,
                (mid1[0] - qx * half, mid1[1] - qy * half),
            ],
            fill=INK,
        )
        pts = pts[1:]


def label_spot(pts, side="right", fraction=0.5):
    """Pick a readable spot for a relationship label."""
    best_index, best_len = 0, -1
    for i in range(len(pts) - 1):
        seg = (
            (pts[i + 1][0] - pts[i][0]) ** 2
            + (pts[i + 1][1] - pts[i][1]) ** 2
        ) ** 0.5
        if seg > best_len:
            best_index, best_len = i, seg
    p, q = pts[best_index], pts[best_index + 1]
    mid = (
        p[0] + (q[0] - p[0]) * fraction,
        p[1] + (q[1] - p[1]) * fraction,
    )
    horizontal = abs(p[0] - q[0]) > abs(p[1] - q[1])
    if horizontal:
        return (mid[0], mid[1] - 9, "mm")
    if side == "left":
        return (mid[0] - 9, mid[1], "rm")
    return (mid[0] + 9, mid[1], "lm")


def draw_relationship(scene, rel):
    pts = resolve(rel)
    kind = rel["kind"]
    dash = DASH if kind in ("dep", "realize") else None

    draw_arrow(scene, list(pts), kind)
    body = pts if kind == "composition" else pts
    scene.polyline(body, dash=dash)

    if rel.get("label"):
        x, y, anchor_mode = label_spot(
            pts,
            rel.get("label_side", "right"),
            rel.get("label_at", 0.5),
        )
        width = text_width(rel["label"], S_MULT)
        pad = 4
        if anchor_mode == "mm":
            x0 = x - width / 2 - pad
        elif anchor_mode == "rm":
            x0 = x - width - pad
        else:
            x0 = x - pad
        y0 = y - 8
        scene.rect(x0, y0, width + 2 * pad, 15, width=0, fill=PAPER)
        scene.text(x, y, rel["label"], S_MULT, anchor=anchor_mode)

    for spec, mult, at_end in (
        (rel.get("a"), rel.get("ma"), False),
        (rel.get("b"), rel.get("mb"), True),
    ):
        if not mult:
            continue
        total = polyline_length(pts)
        offset = total - 14 if at_end else 14
        px, py = point_along(pts, offset)
        side = spec[1]
        if side in ("l", "r"):
            x, y = (px + 8) if at_end else (px - 8), py - 10
            mode = "rm" if at_end else "lm"
        else:
            x, y = px + 8, py - 9
            mode = "lm"
        scene.text(x, y, mult, S_MULT, anchor=mode)


def draw_class(scene, box):
    x, y, w, h = box["x"], box["y"], box["w"], box["h"]
    scene.rect(x, y, w, h, width=1, fill=PAPER)

    cursor = y
    if box["stereo"]:
        scene.text(x + w / 2, cursor + STEREO_H / 2 + 1, box["stereo"], S_STEREO,
                   anchor="mm")
        cursor += STEREO_H
    scene.text(x + w / 2, cursor + HEADER_H / 2, box["name"], S_NAME, bold=True,
               anchor="mm")
    scene.line(x, box["attrs_top"], x + w, box["attrs_top"])
    scene.line(x, box["methods_top"], x + w, box["methods_top"])

    for index, line in enumerate(box["attrs"]):
        scene.text(x + PAD_X, box["attrs_top"] + 8 + ROW / 2 + index * ROW,
                   line, S_MEMBER, anchor="lm")
    for index, line in enumerate(box["methods"]):
        scene.text(x + PAD_X, box["methods_top"] + 8 + ROW / 2 + index * ROW,
                   line, S_MEMBER, anchor="lm")


def draw_frame(scene, box_ids, title):
    xs = [BOXES[i]["x"] for i in box_ids]
    ys = [BOXES[i]["y"] for i in box_ids]
    xe = [BOXES[i]["x"] + BOXES[i]["w"] for i in box_ids]
    ye = [BOXES[i]["y"] + BOXES[i]["h"] for i in box_ids]
    x0, y0 = min(xs) - 25, min(ys) - 45
    x1, y1 = max(xe) + 25, max(ye) + 30
    scene.rect(x0, y0, x1 - x0, y1 - y0, width=1, fill="none")

    tab_w = text_width(title, S_FRAME) + 24
    scene.rect(x0, y0, tab_w, 22, width=1, fill=PAPER)
    scene.text(x0 + 12, y0 + 11, title, S_FRAME, anchor="lm")


def draw_note(scene, x, y, w, text):
    lines = []
    for paragraph in text.split("\n"):
        current = ""
        for word in paragraph.split(" "):
            probe = (current + " " + word).strip()
            if text_width(probe, S_NOTE) > w - 22:
                lines.append(current)
                current = word
            else:
                current = probe
        lines.append(current)
    h = 18 + len(lines) * 15
    scene.rect(x, y, w, h, width=1, fill=PAPER)
    fold = 13
    scene.line(x + w - fold, y, x + w - fold, y + fold)
    scene.line(x + w - fold, y + fold, x + w, y + fold)
    for index, line in enumerate(lines):
        scene.text(x + 11, y + 14 + index * 15, line, S_NOTE, anchor="lm")
    return (x, y, w, h)


# ---------------------------------------------------------------- compose ----
def build():
    scene = Scene()

    width = 2240
    height = 1740

    scene.text(60, 46, "Cinema Booking System - UML Class Diagram", S_TITLE,
               bold=True, anchor="lm")
    scene.text(
        60, 76,
        "Only functionality that exists in the source code is shown.",
        S_SUBTITLE, anchor="lm",
    )

    draw_frame(
        scene,
        ["home", "dto"],
        "CinemaClient  (Blazor Server)",
    )
    draw_frame(
        scene,
        ["program", "reqdto"],
        "CinemaApi  (ASP.NET Core minimal API)",
    )
    draw_frame(
        scene,
        ["svc", "client", "createreq", "createresp", "screening",
         "screeninglist"],
        "gRPC contract  (screenings.proto)",
    )
    draw_frame(
        scene,
        ["implbase", "grpcsvc", "javapp", "grpccfg", "repo", "entity"],
        "CinemaApplication  (Spring Boot, JPA)",
    )
    draw_frame(scene, ["table"], "PostgreSQL  (cinema_booking)")

    for box in CLASSES:
        draw_class(scene, box)

    for rel in RELATIONS:
        draw_relationship(scene, rel)

    note_text = (
        "createScreening is declared in screenings.proto and is called by the "
        "C# API, but ScreeningGrpcService overrides only listScreenings. The "
        "generated base class therefore answers UNIMPLEMENTED, which the C# "
        "API maps to HTTP 501 Not Implemented."
    )
    nx, ny, nw, nh = draw_note(scene, 100, 1390, 520, note_text)
    scene.line(nx + nw, ny + nh / 2, 985, 1268, dash=DASH)

    render_svg(scene, width, height, SVG_PATH)
    render_png(scene, width, height, PNG_PATH)

    print(f"SVG: {SVG_PATH}")
    print(f"PNG: {PNG_PATH} ({width * 2} x {height * 2} px)")
    for box in CLASSES:
        print(
            f"  {box['id']:<14} x={box['x']:<5} y={box['y']:<5} "
            f"w={box['w']:<4} h={box['h']}"
        )


if __name__ == "__main__":
    build()
