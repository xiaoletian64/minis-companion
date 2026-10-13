#!/usr/bin/env python3
"""Generate launcher icons for minis-companion: dark teal rounded square,
gold chat bubble with three dots + a sync arrow (companion/data manager vibe)."""
from PIL import Image, ImageDraw
import os

TEAL = (16, 32, 39, 255)      # download-hub bg
GOLD = (228, 200, 139, 255)   # download-hub accent
OUT = "/var/minis/workspace/minis-companion/android/app/src/main/res"

def make(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # background rounded square
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.225), fill=TEAL)
    m = size * 0.17
    bw = size - 2 * m
    bh = bw * 0.60
    # chat bubble
    d.rounded_rectangle([m, m, m + bw, m + bh], radius=int(size * 0.09), fill=GOLD)
    # bubble tail
    d.polygon([(m + bw * 0.22, m + bh * 0.98), (m + bw * 0.44, m + bh * 0.98),
               (m + bw * 0.28, m + bh + size * 0.085)], fill=GOLD)
    # three dots
    cy = m + bh * 0.5
    rr = size * 0.042
    for dx in (-0.18, 0.0, 0.18):
        cx = m + bw * 0.5 + dx * bw
        d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=TEAL)
    # sync arrow: small arc with arrowhead, bottom-right
    acx, acy, ar = m + bw * 0.78, m + bh + size * 0.155, size * 0.085
    box = [acx - ar, acy - ar, acx + ar, acy + ar]
    d.arc(box, start=-70, end=150, fill=GOLD, width=max(2, int(size * 0.030)))
    ah = size * 0.045
    d.polygon([(acx + ar * 0.98, acy - ar * 0.28), (acx + ar * 0.45, acy - ar * 0.75),
               (acx + ar * 0.38, acy + ar * 0.18)], fill=GOLD)
    return img

for dpi, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    p = os.path.join(OUT, f"mipmap-{dpi}")
    os.makedirs(p, exist_ok=True)
    make(px).save(os.path.join(p, "ic_launcher.png"))
    print(f"mipmap-{dpi}: {px}px")
