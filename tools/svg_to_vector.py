from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

SVG = "{http://www.w3.org/2000/svg}"

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "assets"
DRAWABLES = ROOT / "app" / "src" / "main" / "res" / "drawable"

MODES = {"lightMode": "light", "darkMode": "dark"}

LINECAP = {"butt": "butt", "round": "round", "square": "square"}
LINEJOIN = {"miter": "miter", "round": "round", "bevel": "bevel"}

def fail(message: str) -> None:
    raise SystemExit(f"svg_to_vector: {message}")

def circle_to_path(cx: float, cy: float, r: float) -> str:
    return (
        f"M{cx - r},{cy} "
        f"A{r},{r} 0 1,0 {cx + r},{cy} "
        f"A{r},{r} 0 1,0 {cx - r},{cy} Z"
    )

NAMED = {"white": "#FFFFFF", "black": "#000000"}

def colour(raw: str | None) -> str | None:
    if raw is None or raw.strip() in ("", "none"):
        return None
    value = NAMED.get(raw.strip().lower(), raw.strip())
    if not re.fullmatch(r"#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})", value):
        fail(f"unsupported colour {value!r} — only hex literals are handled")
    if len(value) == 4:
        value = "#" + "".join(c * 2 for c in value[1:])
    return value.upper()

def convert_element(el: ET.Element) -> list[str]:
    tag = el.tag.replace(SVG, "")
    if tag == "path":
        data = el.get("d")
        if not data:
            fail("a <path> has no d attribute")
    elif tag == "circle":
        data = circle_to_path(
            float(el.get("cx", 0)), float(el.get("cy", 0)), float(el.get("r", 0))
        )
    else:
        fail(f"unsupported element <{tag}> — extend this script or simplify the SVG")

    attrs = [f'android:pathData="{data}"']

    fill = colour(el.get("fill"))
    if fill:
        attrs.append(f'android:fillColor="{fill}"')
    if el.get("fill-rule", "").strip() == "evenodd":
        attrs.append('android:fillType="evenOdd"')

    stroke = colour(el.get("stroke"))
    if stroke:
        attrs.append(f'android:strokeColor="{stroke}"')
        attrs.append(f'android:strokeWidth="{el.get("stroke-width", "1")}"')
        cap = el.get("stroke-linecap")
        if cap:
            attrs.append(f'android:strokeLineCap="{LINECAP[cap]}"')
        join = el.get("stroke-linejoin")
        if join:
            attrs.append(f'android:strokeLineJoin="{LINEJOIN[join]}"')
        limit = el.get("stroke-miterlimit")
        if limit:
            attrs.append(f'android:strokeMiterLimit="{limit}"')

    if not fill and not stroke:
        fail(f"an element paints nothing: {ET.tostring(el)[:120]!r}")
    return attrs

def convert(svg_path: Path, out_path: Path, source: str) -> None:
    root = ET.parse(svg_path).getroot()

    box = root.get("viewBox")
    if not box:
        fail(f"{svg_path.name} has no viewBox")
    parts = [float(p) for p in box.replace(",", " ").split()]
    if len(parts) != 4 or parts[0] or parts[1]:
        fail(f"{svg_path.name}: only viewBoxes starting at 0 0 are handled")
    _, _, vw, vh = parts

    blocks = []
    for el in root:
        tag = el.tag.replace(SVG, "")
        if tag in ("title", "desc", "metadata"):
            continue
        attrs = convert_element(el)
        body = "\n        ".join(attrs)
        blocks.append(f"    <path\n        {body} />")

    if not blocks:
        fail(f"{svg_path.name} produced no paths")

    paths = "\n".join(blocks)
    out_path.write_text(
        f"""<?xml version="1.0" encoding="utf-8"?>
<!--
  GENERATED — do not edit. Source: {source}
  Regenerate with: python3 tools/svg_to_vector.py
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{vw:g}dp"
    android:height="{vh:g}dp"
    android:viewportWidth="{vw:g}"
    android:viewportHeight="{vh:g}">
{paths}
</vector>
""",
        encoding="utf-8",
    )

SETS = [
    (Path("."), "wx"),
    (Path("icons") / "cuteTheme", "cute"),
]

NOT_ICONS = {"background-stars", "panel-card", "panel-pocket", "panel-chip"}

CHROME = {"settings"}

def convert_set(base, prefix) -> tuple[int, list[str]]:
    written = 0
    names: dict[str, set[str]] = {}
    for mode_dir, suffix in MODES.items():
        source_dir = ASSETS / base / mode_dir
        if not source_dir.is_dir():
            fail(f"missing {source_dir}")
        names[suffix] = set()
        for svg in sorted(source_dir.glob("*.svg")):
            if svg.stem in NOT_ICONS:
                continue
            stem = svg.stem.replace("-", "_")
            if stem in CHROME:
                convert(svg, DRAWABLES / f"ic_{stem}_{suffix}.xml",
                        str(source_dir.relative_to(ROOT) / svg.name))
                written += 1
                continue
            names[suffix].add(stem)
            out = DRAWABLES / f"{prefix}_{stem}_{suffix}.xml"
            convert(svg, out, str(source_dir.relative_to(ROOT) / svg.name))
            written += 1

    if names["light"] != names["dark"]:
        only_light = sorted(names["light"] - names["dark"])
        only_dark = sorted(names["dark"] - names["light"])
        fail(f"{prefix}: modes disagree — light only: {only_light}, dark only: {only_dark}")
    return written, sorted(names["light"])

def main() -> int:
    if not ASSETS.is_dir():
        fail(f"no assets directory at {ASSETS}")
    DRAWABLES.mkdir(parents=True, exist_ok=True)

    total = 0
    rosters = {}
    for base, prefix in SETS:
        written, roster = convert_set(base, prefix)
        total += written
        rosters[prefix] = roster

    first, *others = rosters.values()
    for prefix, roster in rosters.items():
        if roster != first:
            fail(f"{prefix} does not cover the same icons as the base set: {roster}")

    print(f"wrote {total} drawables to {DRAWABLES.relative_to(ROOT)}")
    for prefix, roster in rosters.items():
        print(f"{prefix}: " + ", ".join(roster))
    return 0

if __name__ == "__main__":
    sys.exit(main())
