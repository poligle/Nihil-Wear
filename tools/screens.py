#!/usr/bin/env python3
# screens.py by poligle
#
# Renders of NIHIL for Wear OS for its README and its page: the watch face as
# tools/watchface.py draws it, and the home and the deck with the app's own
# proportions (ui/AppList.kt, ui/DeckView.kt) and fonts.
#
#   python3 tools/screens.py OUTDIR
#
# Needs Pillow.

import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import watchface  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONTS = os.path.join(ROOT, "app", "src", "main", "assets", "fonts")
S = 600
VOID, SURFACE, LINE, OFF, MUTED, DIM, INK = "#000000", "#0E0E0E", "#2A2A2A", "#333333", "#7A7A7A", "#A3A3A3", "#F2F2F2"


def font(name, size):
    return ImageFont.truetype(os.path.join(FONTS, name), round(size))


def round_mask(img):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, img.width - 1, img.height - 1), fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def dot(draw, x, y, r, colour):
    draw.ellipse((x - r, y - r, x + r, y + r), fill=colour)


def fade(colour, alpha):
    value = int(colour[1:], 16)
    return tuple(round(((value >> shift) & 255) * alpha / 255) for shift in (16, 8, 0))


def home():
    img = Image.new("RGB", (S, S), VOID)
    draw = ImageDraw.Draw(img)
    clock = font("Doto-Bold.ttf", S * 0.068)
    draw.text((S / 2, S * 0.14), "10:08", font=clock, fill=DIM, anchor="ms")
    entries = [("nihil", "Deck", INK), ("Alarm", None, None), ("Calendar", None, None), ("Settings", None, None), ("Timer", None, None)]
    middle, row = S / 2 + S * 0.05, S * 0.15
    for i, (title, note, led) in enumerate(entries):
        y = middle + i * row
        distance = abs(y - middle) / row
        near = max(0.0, min(1.0, 1 - distance))
        alpha = max(40, min(255, round(255 * (1 - distance / 3.4))))
        face = font("SpaceGrotesk-Medium.ttf" if near > 0.5 else "SpaceGrotesk-Regular.ttf", S * (0.058 + 0.02 * near))
        colour = fade(INK if near > 0.5 else DIM, alpha)
        width = draw.textlength(title, font=face)
        space = S * 0.05 if led else 0
        left = S / 2 - (width + space) / 2
        if led:
            dot(draw, left + S * 0.012, y - face.size * 0.32, S * 0.012, fade(led, alpha))
        draw.text((left + space, y), title, font=face, fill=colour, anchor="ls")
        if note and near > 0.5:
            draw.text((S / 2, y + S * 0.05), note, font=font("SpaceMono-Regular.ttf", S * 0.036), fill=MUTED, anchor="ms")
    return img


def deck(level=40, playing=True, caffeine=True, night=False):
    img = Image.new("RGB", (S, S), VOID)
    draw = ImageDraw.Draw(img)
    cx = cy = S / 2
    radius = S / 2 - S * 0.03
    lit = round(level * 60 / 100)
    for i in range(60):
        angle = -math.pi / 2 + 2 * math.pi * i / 60
        dot(draw, cx + radius * math.cos(angle), cy + radius * math.sin(angle), S * 0.0075, INK if i < lit else OFF)
    name = font("SpaceGrotesk-Regular.ttf", S * 0.05)
    width = draw.textlength("nihil", font=name)
    left = cx - (width + S * 0.045) / 2
    dot(draw, left + S * 0.012, cy - S * 0.315, S * 0.012, INK)
    draw.text((left + S * 0.045, cy - S * 0.3), "nihil", font=name, fill=DIM, anchor="ls")
    draw.text((cx, cy - S * 0.165), "Track title", font=font("SpaceGrotesk-Medium.ttf", S * 0.064), fill=INK, anchor="ms")
    draw.text((cx, cy - S * 0.1), "Artist", font=font("SpaceMono-Regular.ttf", S * 0.04), fill=MUTED, anchor="ms")

    def key(x, y, r):
        draw.ellipse((x - r, y - r, x + r, y + r), fill=SURFACE, outline=LINE, width=max(1, round(S * 0.006)))

    for x, r in ((cx - S * 0.2, S * 0.062), (cx, S * 0.085), (cx + S * 0.2, S * 0.062)):
        key(x, cy + S * 0.03, r)
    y = cy + S * 0.03
    s = S * 0.085 * 0.36
    if playing:
        draw.rectangle((cx - s * 0.85, y - s, cx - s * 0.25, y + s), fill=INK)
        draw.rectangle((cx + s * 0.25, y - s, cx + s * 0.85, y + s), fill=INK)
    else:
        draw.polygon([(cx - s * 0.7, y - s), (cx + s, y), (cx - s * 0.7, y + s)], fill=INK)
    s = S * 0.062 * 0.36
    for x, sign in ((cx - S * 0.2, -1), (cx + S * 0.2, 1)):
        bar = sorted((x + sign * s * 0.7, x + sign * s))
        draw.rectangle((bar[0], y - s * 0.8, bar[1], y + s * 0.8), fill=INK)
        draw.polygon([(x - sign * s * 0.8, y - s * 0.8), (x + sign * s * 0.55, y), (x - sign * s * 0.8, y + s * 0.8)], fill=INK)
    draw.text((cx, cy + S * 0.19), f"{level} %", font=font("Doto-Bold.ttf", S * 0.07), fill=INK, anchor="ms")
    y = cy + S * 0.305
    r = S * 0.058
    for x, which in ((cx - S * 0.16, "caffeine"), (cx, "night"), (cx + S * 0.16, "lock")):
        key(x, y, r)
        g = r * 0.5
        if which == "caffeine":
            draw.rounded_rectangle((x - g * 0.8, y - g * 0.55, x + g * 0.35, y + g * 0.75), radius=g * 0.2, fill=INK)
            draw.arc((x + g * 0.05, y - g * 0.3, x + g * 0.85, y + g * 0.4), -90, 90, fill=INK, width=max(2, round(g * 0.22)))
            dot(draw, x, y + r + S * 0.028, S * 0.009, INK if caffeine else OFF)
        elif which == "night":
            dot(draw, x, y, g * 0.8, INK)
            dot(draw, x + g * 0.38, y - g * 0.3, g * 0.66, SURFACE)
            dot(draw, x, y + r + S * 0.028, S * 0.009, INK if night else OFF)
        else:
            draw.rounded_rectangle((x - g * 0.7, y - g * 0.1, x + g * 0.7, y + g * 0.85), radius=g * 0.15, fill=INK)
            draw.arc((x - g * 0.45, y - g * 0.75, x + g * 0.45, y + g * 0.35), 180, 360, fill=INK, width=max(2, round(g * 0.22)))
    return img


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "docs", "screens")
    os.makedirs(out, exist_ok=True)
    face = watchface.preview(scale=2).resize((S, S), Image.LANCZOS)
    for name, img in (("face", face), ("home", home()), ("deck", deck())):
        round_mask(img).save(os.path.join(out, name + ".png"), optimize=True)
    print("screens written to", out)


if __name__ == "__main__":
    main()
