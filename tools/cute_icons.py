from __future__ import annotations

import math
import pathlib

BOX = 108

DARK = {
    "cloud": "#B4BEDC",
    "cloud_back": "#7A82A8",
    "sun": "#FFD98A",
    "sun_ray": "#FFC94D",
    "moon": "#FFE8B0",
    "rain": "#7FD3F0",
    "snow": "#E8F6FF",
    "snow_edge": "#E8F6FF",
    "bolt": "#FFD166",
    "mist": "#8E97BF",
    "face": "#39405C",
    "blush": "#FF9EB5",
}

LIGHT = {
    "cloud": "#98A2CC",
    "cloud_back": "#C6CCE6",
    "sun": "#FFC94D",
    "sun_ray": "#F2A63B",
    "moon": "#F0C669",
    "rain": "#3BAFD6",
    "snow": "#DCEAFB",
    "snow_edge": "#8492C0",
    "bolt": "#F0A500",
    "mist": "#98A2CC",
    "face": "#2B3050",
    "blush": "#F4839D",
}

def circle(cx, cy, r, fill):
    return f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{fill}"/>'

def path(d, fill="none", stroke=None, width=0):
    bits = [f'<path d="{d}"', f'fill="{fill}"']
    if stroke:
        bits.append(
            f'stroke="{stroke}" stroke-width="{width}" '
            'stroke-linecap="round" stroke-linejoin="round"'
        )
    return " ".join(bits) + "/>"

def line(x1, y1, x2, y2, stroke, width):
    return path(f"M{x1},{y1} L{x2},{y2}", stroke=stroke, width=width)

def cloud(p, key="cloud", cx=0.0, cy=0.0, scale=1.0):
    f = p[key]

    def sx(x):
        return round(54 + (x - 54) * scale + cx, 2)

    def sy(y):
        return round(54 + (y - 54) * scale + cy, 2)

    def sr(r):
        return round(r * scale, 2)

    left, right = sx(26), sx(84)
    top, bottom = sy(58), sy(74)
    radius = sr(8)
    return [
        circle(sx(40), sy(58), sr(14), f),
        circle(sx(56), sy(51), sr(18), f),
        circle(sx(71), sy(59), sr(13), f),
        path(
            f"M{left},{top} H{right} V{sy(66)} "
            f"A{radius},{radius} 0 0 1 {sx(76)},{bottom} "
            f"H{sx(34)} A{radius},{radius} 0 0 1 {left},{sy(66)} Z",
            fill=f,
        ),
    ]

def face(p, cx=54, cy=56, mood="happy", scale=1.0, blush=True):
    eye_dx = 9 * scale
    eye_r = 3.1 * scale
    out = [
        circle(round(cx - eye_dx, 2), cy, round(eye_r, 2), p["face"]),
        circle(round(cx + eye_dx, 2), cy, round(eye_r, 2), p["face"]),
    ]
    if blush:
        out += [
            circle(round(cx - eye_dx - 6.5 * scale, 2), round(cy + 5 * scale, 2),
                   round(3.4 * scale, 2), p["blush"]),
            circle(round(cx + eye_dx + 6.5 * scale, 2), round(cy + 5 * scale, 2),
                   round(3.4 * scale, 2), p["blush"]),
        ]

    my = round(cy + 7 * scale, 2)
    w = 5 * scale
    if mood == "happy":
        d = f"M{round(cx - w, 2)},{my} Q{cx},{round(my + 4.5 * scale, 2)} {round(cx + w, 2)},{my}"
    elif mood == "ooh":
        mx, my_ = round(4.4 * scale, 2), round(3.1 * scale, 2)
        oy = round(cy + 11.5 * scale, 2)
        out.append(path(
            f"M{round(cx - mx, 2)},{oy} A{mx},{my_} 0 1 0 {round(cx + mx, 2)},{oy} "
            f"A{mx},{my_} 0 1 0 {round(cx - mx, 2)},{oy} Z",
            fill=p["face"],
        ))
        return out
    else:
        d = f"M{round(cx - w * 0.7, 2)},{my} L{round(cx + w * 0.7, 2)},{my}"
    out.append(path(d, stroke=p["face"], width=round(2.6 * scale, 2)))
    return out

def sun(p, cx=54, cy=50, r=19, rays=True, with_face=True):
    out = []
    if rays:
        for i in range(8):
            a = math.radians(i * 45 + 22.5)
            x1 = round(cx + math.cos(a) * (r + 5), 2)
            y1 = round(cy + math.sin(a) * (r + 5), 2)
            x2 = round(cx + math.cos(a) * (r + 12), 2)
            y2 = round(cy + math.sin(a) * (r + 12), 2)
            out.append(line(x1, y1, x2, y2, p["sun_ray"], 4.5))
    out.append(circle(cx, cy, r, p["sun"]))
    if with_face:
        out += face(p, cx=cx, cy=cy - 1, scale=0.95)
    return out

def moon(p, cx=52, cy=50, r=23, with_face=True):
    bite = r * 0.78
    half = bite / 2
    ix = round(cx + half, 2)
    iy = math.sqrt(r * r - half * half)
    top_y = round(cy - iy, 2)
    bot_y = round(cy + iy, 2)
    out = [
        path(
            f"M{ix},{top_y} A{r},{r} 0 1 0 {ix},{bot_y} "
            f"A{r},{r} 0 0 1 {ix},{top_y} Z",
            fill=p["moon"],
        )
    ]
    if with_face:
        out += face(p, cx=round(cx - r + bite / 2, 2), cy=cy, scale=0.62, blush=r > 18)
    return out

def drops(p, xs, y=78, length=9, width=3.6):
    return [
        line(x, y, round(x - 3.4, 2), round(y + length, 2), p["rain"], width)
        for x in xs
    ]

def flakes(p, xs, y=82, r=4.2):
    out = []
    for i, x in enumerate(xs):
        cy = y + (2.5 if i % 2 else 0)
        out.append(circle(x, round(cy, 2), r, p["snow"]))
        if p["snow_edge"] != p["snow"]:
            out.append(
                path(
                    f"M{round(x - r, 2)},{cy} A{r},{r} 0 1 0 {round(x + r, 2)},{cy} "
                    f"A{r},{r} 0 1 0 {round(x - r, 2)},{cy} Z",
                    stroke=p["snow_edge"],
                    width=1.2,
                )
            )
    return out

def bolt(p, cx=54, y=74):
    return [
        path(
            f"M{cx + 4},{y} L{cx - 9},{y + 15} L{cx - 1},{y + 15} "
            f"L{cx - 5},{y + 27} L{cx + 10},{y + 10} L{cx + 1},{y + 10} Z",
            fill=p["bolt"],
        )
    ]

def draw(name, p):
    if name == "sun":
        return sun(p, cy=52, r=21)

    if name == "moon":
        return moon(p, cx=63, cy=54, r=25)

    if name == "cloudy-sun":
        return sun(p, cx=70, cy=38, r=14, with_face=False) + cloud(p, cy=4) + face(p, cy=59)

    if name == "moon-with-sun":
        return moon(p, cx=76, cy=36, r=16, with_face=False) + cloud(p, cy=4) + face(p, cy=59)

    if name == "light-clouds":
        return cloud(p) + face(p, cy=55)

    if name == "heavy-clouds":
        return (
            cloud(p, key="cloud_back", cx=13, cy=-15, scale=0.66)
            + cloud(p, cy=3)
            + face(p, cy=58)
        )

    if name == "mist":
        out = cloud(p, cy=-6) + face(p, cy=49, mood="calm")
        for i, y in enumerate((76, 87, 98)):
            half = 27 - i * 5
            out.append(line(54 - half, y, 54 + half, y, p["mist"], 5))
        return out

    if name == "rain":
        return cloud(p, cy=-4) + face(p, cy=51) + drops(p, (40, 54, 68))

    if name == "heavy-rain":
        return (
            cloud(p, cy=-6)
            + face(p, cy=49, mood="ooh")
            + drops(p, (36, 50, 64, 78), y=72, length=11, width=4)
            + drops(p, (43, 57, 71), y=86, length=9, width=4)
        )

    if name == "snow":
        return cloud(p, cy=-4) + face(p, cy=51) + flakes(p, (40, 54, 68))

    if name == "heavy-snow":
        return (
            cloud(p, cy=-7)
            + face(p, cy=48, mood="ooh")
            + flakes(p, (36, 50, 64, 78), y=76)
            + flakes(p, (43, 57, 71), y=91, r=3.6)
        )

    if name == "lightning":
        return cloud(p, cy=-8) + face(p, cy=47, mood="ooh") + bolt(p, y=70)

    if name == "thunderstorm":
        return (
            cloud(p, cy=-8)
            + face(p, cy=47, mood="ooh")
            + bolt(p, cx=54, y=70)
            + drops(p, (34, 41), y=76, length=10, width=3.6)
            + drops(p, (72, 79), y=76, length=10, width=3.6)
        )

    raise SystemExit(f"no drawing for {name}")

NAMES = [
    "sun", "moon", "cloudy-sun", "moon-with-sun", "light-clouds",
    "heavy-clouds", "mist", "rain", "heavy-rain", "snow", "heavy-snow",
    "lightning", "thunderstorm",
]

def main():
    root = pathlib.Path(__file__).resolve().parent.parent
    out_root = root / "app" / "assets" / "icons" / "cuteTheme"

    for folder, palette in (("darkMode", DARK), ("lightMode", LIGHT)):
        target = out_root / folder
        target.mkdir(parents=True, exist_ok=True)
        for name in NAMES:
            body = "\n".join(draw(name, palette))
            svg = (
                f'<svg width="{BOX}" height="{BOX}" viewBox="0 0 {BOX} {BOX}" '
                'fill="none" xmlns="http://www.w3.org/2000/svg">\n'
                f"{body}\n</svg>\n"
            )
            (target / f"{name}.svg").write_text(svg)
    print(f"wrote {len(NAMES) * 2} files under {out_root}")

if __name__ == "__main__":
    main()
