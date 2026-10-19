#!/usr/bin/env python3
"""Renders the Google Play listing graphics into docs/play-store/graphics/ (docs/play-store.md):

- icon-512.png: the app icon, 512x512, as Play asks for it (square, Play rounds the corners itself)
- feature-graphic.png: 1024x500 banner shown at the top of the listing

Both come from the adaptive icon's vector layers via render-launcher-icons.py, so they always match
the app. macOS only (Quick Look + system Arial font), needs Pillow:
    python3 scripts/render-store-graphics.py
"""
import importlib.util
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs/play-store/graphics"

spec = importlib.util.spec_from_file_location("launcher", ROOT / "scripts/render-launcher-icons.py")
launcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(launcher)

# Colors of ic_launcher_background.xml (gradient) and the green of ic_launcher_foreground.xml.
TOP = (0x15, 0x65, 0xC0)
BOTTOM = (0x0B, 0x2E, 0x6B)
ACCENT = (0x00, 0xE6, 0x76)
FONT_BOLD = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
FONT = "/System/Library/Fonts/Supplemental/Arial.ttf"


def feature_graphic(square: Image.Image) -> Image.Image:
    width, height = 1024, 500
    image = Image.new("RGB", (width, height))
    draw = ImageDraw.Draw(image)
    for y in range(height):
        t = y / (height - 1)
        draw.line([(0, y), (width, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(TOP, BOTTOM)))

    icon = launcher.masked(square, round_icon=False).resize((300, 300), Image.LANCZOS)
    image.paste(icon, (80, 100), icon)

    x = 430
    draw.text((x, 135), "Баркодове", font=ImageFont.truetype(FONT_BOLD, 76), fill="white")
    draw.rectangle([x, 235, x + 90, 241], fill=ACCENT)
    body = ImageFont.truetype(FONT, 34)
    draw.text((x, 270), "Всички баркодове от стикера", font=body, fill="white")
    draw.text((x, 315), "наведнъж, готови за копиране", font=body, fill="white")
    return image


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    square = launcher.render_square()
    square.resize((512, 512), Image.LANCZOS).save(OUT / "icon-512.png", "PNG")
    feature_graphic(square).save(OUT / "feature-graphic.png", "PNG")
    for name in ("icon-512.png", "feature-graphic.png"):
        print((OUT / name).relative_to(ROOT))


if __name__ == "__main__":
    main()
