#!/usr/bin/env python3
"""Renders the web app icons (web/public/icons/*.png) from the Android adaptive icon's vector layers.

Reuses scripts/render-launcher-icons.py from the Android project, so the icons always match the app.
Re-run after changing the icon. macOS only (Quick Look), needs Pillow:
    python3 web/scripts/render-icons.py
"""
import importlib.util
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "web/public/icons"

spec = importlib.util.spec_from_file_location("launcher", ROOT / "scripts/render-launcher-icons.py")
launcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(launcher)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    square = launcher.render_square()
    rounded = launcher.masked(square, round_icon=False)
    outputs = {
        "icon-192.png": rounded.resize((192, 192), Image.LANCZOS),
        "icon-512.png": rounded.resize((512, 512), Image.LANCZOS),
        # Full bleed: the launcher applies its own mask shape.
        "icon-maskable-512.png": square.resize((512, 512), Image.LANCZOS),
        # iOS rounds the corners itself and shows transparency as black.
        "apple-touch-icon.png": square.resize((180, 180), Image.LANCZOS),
    }
    for name, image in outputs.items():
        image.save(OUT / name, "PNG")
        print((OUT / name).relative_to(ROOT))


if __name__ == "__main__":
    main()
