#!/usr/bin/env python3
"""Generates the Dotter Android icon: a halftone sun on a dusk sky.

One design (below) becomes the adaptive icon layers (vector drawables, with a
monochrome layer for themed icons), the legacy PNG launcher icons for Android
7.x, the splash colours, and a 512 px Play Store icon.

Needs rsvg-convert (brew install librsvg) and Pillow. Run from the repo root:
    python3 scripts/android-icon.py
"""
import math, os, subprocess, tempfile
from PIL import Image, ImageDraw

RES = 'android/app/src/main/res'
STORE = 'android/store'
CX, CY, R, SP = 54, 56, 31, 6.4          # 108 x 108 adaptive canvas; the safe zone is the middle 66
RMAX, RMIN, POWER = 3.3, 0.35, 1.5
SKY = [(0.0, '#2a2f6b'), (0.55, '#b5617a'), (1.0, '#f6a35e')]
INK = '#fff3dc'
SPLASH_BG = '#0b1026'


def dots():
    out, rows = [], int(2 * R / (SP * 0.866)) + 3
    for j in range(-rows, rows + 1):
        y, off = CY + j * SP * 0.866, (SP / 2 if j % 2 else 0)
        for i in range(-rows, rows + 1):
            x = CX + i * SP + off
            d = math.hypot(x - CX, y - CY)
            if d > R:
                continue
            r = RMIN + (RMAX - RMIN) * (1 - (d / R) ** POWER)
            if r >= 0.5:
                out.append((round(x, 2), round(y, 2), round(r, 2)))
    return out


DOTS = dots()


def path_data():
    return ''.join('M%g,%ga%g,%g 0 1,0 %g,0a%g,%g 0 1,0 %g,0z' % (x - r, y, r, r, 2 * r, r, r, -2 * r) for x, y, r in DOTS)


def svg(background=True):
    s = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">']
    if background:
        stops = ''.join('<stop offset="%g" stop-color="%s"/>' % st for st in SKY)
        s.append('<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1">%s</linearGradient></defs><rect width="108" height="108" fill="url(#g)"/>' % stops)
    s += ['<circle cx="%g" cy="%g" r="%g" fill="%s"/>' % (x, y, r, INK) for x, y, r in DOTS]
    s.append('</svg>')
    return ''.join(s)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, 'w').write(text)


def vector(fill, body):
    return ('<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n%s</vector>\n' % body)


def render(svg_text, px, out):
    with tempfile.NamedTemporaryFile('w', suffix='.svg', delete=False) as f:
        f.write(svg_text)
    subprocess.check_call(['rsvg-convert', '-w', str(px), '-h', str(px), f.name, '-o', out])
    os.unlink(f.name)


def masked(px, shape):
    tmp = tempfile.mktemp(suffix='.png')
    render(svg(), px, tmp)
    im = Image.open(tmp).convert('RGBA')
    os.unlink(tmp)
    m = Image.new('L', (px * 4, px * 4), 0)
    d = ImageDraw.Draw(m)
    if shape == 'round':
        d.ellipse((0, 0, px * 4 - 1, px * 4 - 1), fill=255)
    else:
        d.rounded_rectangle((0, 0, px * 4 - 1, px * 4 - 1), radius=int(px * 4 * 0.18), fill=255)
    m = m.resize((px, px), Image.LANCZOS)
    out = Image.new('RGBA', (px, px), (0, 0, 0, 0))
    out.paste(im, (0, 0), m)
    return out


# adaptive layers
grad = ''.join('        <item android:offset="%g" android:color="%s"/>\n' % st for st in SKY)
write(RES + '/drawable/ic_launcher_background.xml', vector(None,
      '    <path android:pathData="M0,0h108v108h-108z">\n        <aapt:attr name="android:fillColor">\n'
      '            <gradient android:startX="0" android:startY="0" android:endX="0" android:endY="108" android:type="linear">\n'
      + grad.replace('        <item', '                <item') +
      '            </gradient>\n        </aapt:attr>\n    </path>\n'))
write(RES + '/drawable/ic_launcher_foreground.xml', vector(None, '    <path android:fillColor="%s" android:pathData="%s"/>\n' % (INK.replace('#', '#FF'), path_data())))
write(RES + '/drawable/ic_launcher_monochrome.xml', vector(None, '    <path android:fillColor="#FF000000" android:pathData="%s"/>\n' % path_data()))
adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_launcher_background"/>\n    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>\n</adaptive-icon>\n')
write(RES + '/mipmap-anydpi-v26/ic_launcher.xml', adaptive)
write(RES + '/mipmap-anydpi-v26/ic_launcher_round.xml', adaptive)

# legacy launcher icons (Android 7.x) and the old Capacitor leftovers
for name, px in (('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)):
    d = '%s/mipmap-%s' % (RES, name)
    masked(px, 'square').save(d + '/ic_launcher.png')
    masked(px, 'round').save(d + '/ic_launcher_round.png')
    old = d + '/ic_launcher_foreground.png'
    if os.path.exists(old):
        os.remove(old)
old = RES + '/drawable-v24/ic_launcher_foreground.xml'
if os.path.exists(old):
    os.remove(old)

# splash colours (the icon itself is the splash icon)
write(RES + '/values/ic_launcher_background.xml', '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">%s</color>\n    <color name="splash_background">%s</color>\n</resources>\n' % (SKY[0][1].upper(), SPLASH_BG.upper()))

# Play Store listing icon
os.makedirs(STORE, exist_ok=True)
render(svg(), 512, STORE + '/icon-512.png')
print('%d dots; wrote adaptive layers, legacy icons and %s/icon-512.png' % (len(DOTS), STORE))
