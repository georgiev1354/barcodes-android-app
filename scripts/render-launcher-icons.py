#!/usr/bin/env python3
"""Renders raster launcher icons (mipmap-xxxhdpi/ic_launcher.webp, ic_launcher_round.webp) from the
adaptive icon's vector layers.

The launcher itself uses the adaptive icon (mipmap-anydpi wins on every supported API level), so
the rasters are never shown on a device. They are for the update server, which takes the app icon
from the APK as the first PNG/WebP behind android:icon and can't draw vectors (docs/self-update.md).
Only xxxhdpi (192x192) is made: with more densities the first raster would be mdpi, 48x48. Re-run after changing
res/drawable/ic_launcher_background.xml or ic_launcher_foreground.xml.

macOS only: the SVG is rendered with Quick Look (qlmanage). Needs Pillow.
    python3 scripts/render-launcher-icons.py
"""
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
LAYERS = [RES / "drawable/ic_launcher_background.xml", RES / "drawable/ic_launcher_foreground.xml"]
SIZES = {"xxxhdpi": 192}
RENDER = 1024
A = "{http://schemas.android.com/apk/res/android}"

# Legacy icons show the middle 72 of the adaptive icon's 108 units (the rest is bleed for masks).
VIEWBOX = "18 18 72 72"


def color(value: str) -> tuple[str, float]:
    """#RGB/#RRGGBB/#AARRGGBB -> (#RRGGBB, alpha)."""
    v = value.lstrip("#")
    if len(v) == 3:
        v = "".join(c * 2 for c in v)
    if len(v) == 8:
        return "#" + v[2:], int(v[:2], 16) / 255
    return "#" + v, 1.0


def svg_paths(vector: Path, gradients: list[str]) -> list[str]:
    out = []
    for path in ET.parse(vector).getroot().iter("path"):
        attrs = [f'd="{path.get(A + "pathData")}"']
        fill = path.get(A + "fillColor")
        gradient = path.find(".//gradient")
        if gradient is not None:
            gid = f"g{len(gradients)}"
            start, start_a = color(gradient.get(A + "startColor"))
            end, end_a = color(gradient.get(A + "endColor"))
            gradients.append(
                f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" '
                f'x1="{gradient.get(A + "startX")}" y1="{gradient.get(A + "startY")}" '
                f'x2="{gradient.get(A + "endX")}" y2="{gradient.get(A + "endY")}">'
                f'<stop offset="0" stop-color="{start}" stop-opacity="{start_a}"/>'
                f'<stop offset="1" stop-color="{end}" stop-opacity="{end_a}"/></linearGradient>'
            )
            attrs.append(f'fill="url(#{gid})"')
        elif fill:
            c, a = color(fill)
            attrs.append(f'fill="{c}" fill-opacity="{a * float(path.get(A + "fillAlpha", 1))}"')
        else:
            attrs.append('fill="none"')
        stroke = path.get(A + "strokeColor")
        if stroke:
            c, a = color(stroke)
            attrs += [
                f'stroke="{c}"',
                f'stroke-opacity="{a * float(path.get(A + "strokeAlpha", 1))}"',
                f'stroke-width="{path.get(A + "strokeWidth", "0")}"',
                f'stroke-linecap="{path.get(A + "strokeLineCap", "butt")}"',
                f'stroke-linejoin="{path.get(A + "strokeLineJoin", "miter")}"',
            ]
        out.append(f"<path {' '.join(attrs)}/>")
    return out


def render_square() -> Image.Image:
    gradients: list[str] = []
    paths = [p for layer in LAYERS for p in svg_paths(layer, gradients)]
    svg = (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{RENDER}" height="{RENDER}" viewBox="{VIEWBOX}">'
        f"<defs>{''.join(gradients)}</defs>{''.join(paths)}</svg>"
    )
    with tempfile.TemporaryDirectory() as tmp:
        src = Path(tmp) / "icon.svg"
        src.write_text(svg)
        subprocess.run(["qlmanage", "-t", "-s", str(RENDER), "-o", tmp, str(src)],
                       check=True, capture_output=True)
        image = Image.open(Path(tmp) / "icon.svg.png").convert("RGBA")
    if image.size != (RENDER, RENDER):
        sys.exit(f"Quick Look rendered {image.size}, expected {RENDER}x{RENDER}")
    return image


def masked(image: Image.Image, round_icon: bool) -> Image.Image:
    mask = Image.new("L", image.size, 0)
    draw = ImageDraw.Draw(mask)
    box = (0, 0, image.width - 1, image.height - 1)
    if round_icon:
        draw.ellipse(box, fill=255)
    else:
        draw.rounded_rectangle(box, radius=image.width * 0.18, fill=255)
    out = image.copy()
    out.putalpha(mask)
    return out


def main() -> None:
    square = render_square()
    for name, round_icon in (("ic_launcher", False), ("ic_launcher_round", True)):
        icon = masked(square, round_icon)
        for density, size in SIZES.items():
            folder = RES / f"mipmap-{density}"
            folder.mkdir(exist_ok=True)
            target = folder / f"{name}.webp"
            icon.resize((size, size), Image.LANCZOS).save(target, "WEBP", lossless=True)
            print(target.relative_to(ROOT))


if __name__ == "__main__":
    main()
