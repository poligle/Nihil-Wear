#!/usr/bin/env python3
# watchface.py by poligle
#
# NIHIL's watch face, in Watch Face Format 1 (Wear OS 4 and newer), drawn by
# script as the desktop's brand is: its XML and the preview the watch shows
# for it in its list of faces.
#
#   python3 tools/watchface.py
#
# Writes watchface/src/main/res/raw/watchface.xml and
# watchface/src/main/res/drawable-nodpi/preview.png. Needs Pillow.

import math
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "watchface", "src", "main", "res")

SIZE = 450
LEDS = 60
LED = 7                  # the edge's LEDs, as wide as the app's
EDGE = SIZE / 2 - 14     # their circle
BATTERY = 10
BATTERY_LED = 8
BATTERY_PITCH = 16
BATTERY_Y = 338
TIME_Y, TIME_H, TIME_SIZE = 138, 132, 112
DATE_Y, DATE_H, DATE_SIZE = 282, 40, 24
UNREAD_Y, UNREAD = 116, 10

OFF = "#FF333333"
DIM = "#FFA3A3A3"
INK = "#FFF2F2F2"
SIGNAL = "#FFD71921"


def edge(index):
    angle = -math.pi / 2 + 2 * math.pi * index / LEDS
    return SIZE / 2 + EDGE * math.cos(angle), SIZE / 2 + EDGE * math.sin(angle)


def dot(x, y, size, colour):
    return (f'<Ellipse x="{x - size / 2:.1f}" y="{y - size / 2:.1f}" width="{size}" height="{size}">'
            f'<Fill color="{colour}"/></Ellipse>')


def battery_x(index):
    return SIZE / 2 - (BATTERY - 1) * BATTERY_PITCH / 2 + index * BATTERY_PITCH


def xml():
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             '<!--',
             "    NIHIL's watch face, in Watch Face Format 1 (Wear OS 4 and newer). The time",
             "    in dots; sixty LEDs round the edge, every one there and the unlit faintly,",
             "    the seconds' one lit as the desktop's light runs round its frame; the",
             "    battery as ten LEDs, red at 15 % or less; a LED over the time while",
             "    something is unread. A tap on the time opens NIHIL (nihil-wear://home).",
             "    Drawn by tools/watchface.py: change that, not this.",
             '-->',
             f'<WatchFace width="{SIZE}" height="{SIZE}" clipShape="CIRCLE">',
             '    <Scene backgroundColor="#FF000000">',
             f'        <PartDraw name="edge" x="0" y="0" width="{SIZE}" height="{SIZE}">',
             '            <Variant mode="AMBIENT" target="alpha" value="0"/>']
    for i in range(LEDS):
        lines.append("            " + dot(*edge(i), LED, OFF))
    lines += ['        </PartDraw>',
              f'        <PartDraw name="second" x="0" y="0" width="{SIZE}" height="{SIZE}" pivotX="0.5" pivotY="0.5">',
              '            <Transform target="angle" value="[SECOND] * 6"/>',
              '            <Variant mode="AMBIENT" target="alpha" value="0"/>',
              "            " + dot(*edge(0), LED, INK),
              '        </PartDraw>',
              '        <Condition>',
              '            <Expressions>',
              '                <Expression name="unread">[UNREAD_NOTIFICATION_COUNT] &gt; 0</Expression>',
              '            </Expressions>',
              '            <Compare expression="unread">',
              f'                <PartDraw name="unread" x="0" y="0" width="{SIZE}" height="{SIZE}">',
              "                    " + dot(SIZE / 2, UNREAD_Y, UNREAD, INK),
              '                </PartDraw>',
              '            </Compare>',
              '        </Condition>',
              f'        <Group name="time" x="0" y="{TIME_Y}" width="{SIZE}" height="{TIME_H}">',
              '            <Launch target="nihil-wear://home"/>',
              f'            <DigitalClock x="0" y="0" width="{SIZE}" height="{TIME_H}">',
              f'                <TimeText format="hh:mm" hourFormat="SYNC_TO_DEVICE" align="CENTER" x="0" y="0" width="{SIZE}" height="{TIME_H}">',
              f'                    <Font family="doto" size="{TIME_SIZE}" color="{INK}"/>',
              '                </TimeText>',
              '            </DigitalClock>',
              '            <Variant mode="AMBIENT" target="alpha" value="170"/>',
              '        </Group>',
              f'        <PartText name="date" x="0" y="{DATE_Y}" width="{SIZE}" height="{DATE_H}">',
              '            <Variant mode="AMBIENT" target="alpha" value="140"/>',
              '            <Text align="CENTER">',
              f'                <Font family="space_mono" size="{DATE_SIZE}" color="{DIM}">',
              '                    <Template>%s %s<Parameter expression="[DAY_OF_WEEK_S]"/><Parameter expression="[DAY]"/></Template>',
              '                </Font>',
              '            </Text>',
              '        </PartText>',
              f'        <Group name="battery" x="0" y="0" width="{SIZE}" height="{SIZE}">']
    for i in range(BATTERY):
        x = battery_x(i)
        lines += ['            <Condition>',
                  '                <Expressions>',
                  f'                    <Expression name="low">[BATTERY_PERCENT] &lt;= 15 &amp;&amp; [BATTERY_PERCENT] &gt; {i * 10}</Expression>',
                  f'                    <Expression name="lit">[BATTERY_PERCENT] &gt; {i * 10}</Expression>',
                  '                </Expressions>']
        for name, colour in (("low", SIGNAL), ("lit", INK)):
            lines += [f'                <Compare expression="{name}">',
                      f'                    <PartDraw name="battery-{i}-{name}" x="0" y="0" width="{SIZE}" height="{SIZE}">',
                      "                        " + dot(x, BATTERY_Y, BATTERY_LED, colour),
                      '                    </PartDraw>',
                      '                </Compare>']
        lines += ['                <Default>',
                  f'                    <PartDraw name="battery-{i}" x="0" y="0" width="{SIZE}" height="{SIZE}">',
                  "                        " + dot(x, BATTERY_Y, BATTERY_LED, OFF),
                  '                    </PartDraw>',
                  '                </Default>',
                  '            </Condition>']
    lines += ['            <Variant mode="AMBIENT" target="alpha" value="0"/>',
              '        </Group>',
              '    </Scene>',
              '</WatchFace>']
    return "\n".join(lines) + "\n"


def preview(scale=1, second=32, battery=72, time="10:08", date="Wed 11"):
    """The face as the watch's list of faces shows it, drawn as the XML says."""
    s = SIZE * scale
    img = Image.new("RGB", (s, s), "#000000")
    draw = ImageDraw.Draw(img)

    def circle(x, y, size, colour):
        r = size * scale / 2
        draw.ellipse((x * scale - r, y * scale - r, x * scale + r, y * scale + r), fill=colour[3:] and "#" + colour[3:])

    for i in range(LEDS):
        circle(*edge(i), LED, INK if i == second else OFF)
    circle(SIZE / 2, UNREAD_Y, UNREAD, INK)
    fonts = os.path.join(RES, "font")
    clock = ImageFont.truetype(os.path.join(fonts, "doto.ttf"), TIME_SIZE * scale)
    box = draw.textbbox((0, 0), time, font=clock, anchor="ls")
    draw.text((s / 2 - (box[2] - box[0]) / 2, (TIME_Y + TIME_H / 2) * scale + (box[3] - box[1]) / 2), time,
              font=clock, fill="#F2F2F2", anchor="ls")
    mono = ImageFont.truetype(os.path.join(fonts, "space_mono.ttf"), DATE_SIZE * scale)
    draw.text((s / 2, (DATE_Y + DATE_H / 2) * scale), date, font=mono, fill="#A3A3A3", anchor="mm")
    for i in range(BATTERY):
        circle(battery_x(i), BATTERY_Y, BATTERY_LED, INK if battery > i * 10 else OFF)
    return img


def main():
    with open(os.path.join(RES, "raw", "watchface.xml"), "w") as file:
        file.write(xml())
    preview().save(os.path.join(RES, "drawable-nodpi", "preview.png"), optimize=True)
    print("watch face written")


if __name__ == "__main__":
    main()
