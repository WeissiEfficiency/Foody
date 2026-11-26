"""Erzeugt das Pergament des Necronomicon (Kosmisches Grauen, Variante Immersiv) als WebP.

Plan „Zwei Gesichter“, Schritt 4: verwittertes Pergament über die ganze Seite mit Fasern, Flecken, Schimmel, einem
Becherrand und dunklen bis verkohlten Rändern. Die Mitte bleibt so hell, dass Tinte und Nebentext 4,5:1 halten; das
Skript bricht ab, wenn der dunkelste Punkt unter Text zu dunkel wird. `PergamentTest` prüft dasselbe am fertigen Bild.

Aufruf (aus dem Wurzelordner): python werkzeuge/pergament_erzeugen.py
Ergebnis: android/core/designsystem/src/main/res/drawable-nodpi/grauen_pergament.webp (höchstens 300 KB)
"""

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

BREITE, HOEHE = 720, 1560
RAND = 0.03  # verkohlter Rand (Anteil der Breite); Text hält 16 dp ≈ 4,4 % Abstand
PRUEFRAND = 0.04  # ab hier kann Text liegen
GRUND = np.array([0xCD, 0xBB, 0x93], dtype=np.float32)
ZIEL = Path(__file__).resolve().parent.parent / "android/core/designsystem/src/main/res/drawable-nodpi/grauen_pergament.webp"
MINDEST_LEUCHTDICHTE = 0.30  # Text #17110B und Nebentext #33291B halten darauf 4,5:1

zufall = np.random.default_rng(1928)  # „The Call of Cthulhu“


def rauschen(skala: int) -> np.ndarray:
    """Weiches Rauschen in [-1, 1] mit Strukturen der Größe [skala] Pixel."""
    klein = zufall.standard_normal((HOEHE // skala + 2, BREITE // skala + 2)).astype(np.float32)
    bild = Image.fromarray(((klein - klein.min()) / np.ptp(klein) * 255).astype(np.uint8))
    bild = bild.resize((BREITE, HOEHE), Image.BICUBIC).filter(ImageFilter.GaussianBlur(skala / 3))
    a = np.asarray(bild, dtype=np.float32) / 255
    return (a - a.mean()) / (np.abs(a - a.mean()).max() + 1e-6)


def leuchtdichte(rgb: np.ndarray) -> np.ndarray:
    c = rgb / 255
    lin = np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    return 0.2126 * lin[..., 0] + 0.7152 * lin[..., 1] + 0.0722 * lin[..., 2]


def maske(zeichnen) -> np.ndarray:
    m = Image.new("L", (BREITE, HOEHE), 0)
    zeichnen(ImageDraw.Draw(m))
    return np.asarray(m, dtype=np.float32) / 255


bild = np.broadcast_to(GRUND, (HOEHE, BREITE, 3)).copy()

# Wolkige Helligkeit und feine Körnung
bild *= (1 + 0.035 * rauschen(160) + 0.02 * rauschen(40))[..., None]
bild += zufall.normal(0, 2.2, (HOEHE, BREITE, 1)).astype(np.float32)

# Fasern: lange dünne Striche, etwas heller oder dunkler
def fasern(d: ImageDraw.ImageDraw):
    for _ in range(1700):
        x, y = zufall.uniform(0, BREITE), zufall.uniform(0, HOEHE)
        laenge, winkel = zufall.uniform(12, 70), zufall.normal(0, 0.35)
        d.line([(x, y), (x + laenge * np.cos(winkel), y + laenge * np.sin(winkel))], fill=int(zufall.uniform(60, 160)), width=1)
faser = np.asarray(Image.fromarray((maske(fasern) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.6)), dtype=np.float32) / 255
bild *= (1 - 0.09 * faser)[..., None]

# Flecken: weiche, unregelmäßige braune Wolken
def flecken(d: ImageDraw.ImageDraw):
    for _ in range(14):
        cx, cy, r = zufall.uniform(0, BREITE), zufall.uniform(0, HOEHE), zufall.uniform(25, 110)
        for _ in range(6):
            ox, oy, rr = zufall.normal(0, r / 3), zufall.normal(0, r / 3), r * zufall.uniform(0.4, 0.8)
            d.ellipse([cx + ox - rr, cy + oy - rr, cx + ox + rr, cy + oy + rr], fill=int(zufall.uniform(90, 200)))
fleck = np.asarray(Image.fromarray((maske(flecken) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(28)), dtype=np.float32) / 255
braun = np.array([0x8A, 0x6A, 0x42], dtype=np.float32)
bild = bild * (1 - 0.16 * fleck[..., None]) + braun * 0.16 * fleck[..., None]

# Becherrand unten rechts
def becher(d: ImageDraw.ImageDraw):
    cx, cy, r = BREITE * 0.72, HOEHE * 0.78, 74
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=190, width=5)
    d.arc([cx - r - 3, cy - r + 2, cx + r - 3, cy + r + 2], 200, 320, fill=130, width=3)
ring = np.asarray(Image.fromarray((maske(becher) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2.2)), dtype=np.float32) / 255
bild = bild * (1 - 0.13 * ring[..., None]) + braun * 0.13 * ring[..., None]

# Schimmel: grünlich-graue Sprenkel in einer Ecke oben links und am rechten Rand
def schimmel(d: ImageDraw.ImageDraw):
    for cx, cy, n in [(BREITE * 0.1, HOEHE * 0.08, 260), (BREITE * 0.93, HOEHE * 0.45, 140)]:
        for _ in range(n):
            x, y, r = zufall.normal(cx, 45), zufall.normal(cy, 55), zufall.uniform(0.8, 3.2)
            d.ellipse([x - r, y - r, x + r, y + r], fill=int(zufall.uniform(80, 220)))
schim = np.asarray(Image.fromarray((maske(schimmel) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2)), dtype=np.float32) / 255
moos = np.array([0x6E, 0x77, 0x5E], dtype=np.float32)
bild = bild * (1 - 0.22 * schim[..., None]) + moos * 0.22 * schim[..., None]

# Ränder: zum Rand hin dunkel, ganz außen verkohlt, mit unruhiger Kante
yy, xx = np.mgrid[0:HOEHE, 0:BREITE].astype(np.float32)
abstand = np.minimum.reduce([xx / BREITE, (BREITE - 1 - xx) / BREITE, yy / BREITE, (HOEHE - 1 - yy) / BREITE])
abstand += 0.01 * rauschen(26) + 0.004 * rauschen(7)
# Weiche Abdunklung weit hinein (Alter), dann der verkohlte Rand
alter = np.clip(1 - abstand / 0.22, 0, 1) ** 2
bild *= (1 - 0.12 * alter)[..., None]
dunkel = np.clip(1 - abstand / RAND, 0, 1) ** 1.5
kohle = np.array([0x2A, 0x1A, 0x0E], dtype=np.float32)
bild = bild * (1 - 0.85 * dunkel[..., None]) + kohle * 0.85 * dunkel[..., None]

bild = np.clip(bild, 0, 255).astype(np.uint8)

# Prüfung: dunkelster Punkt unter Text (ohne den Rand)
mx, my = int(BREITE * PRUEFRAND), int(BREITE * PRUEFRAND)
innen = leuchtdichte(bild[my:HOEHE - my, mx:BREITE - mx].astype(np.float32))
print(f"Leuchtdichte innen: min {innen.min():.3f}, Mittel {innen.mean():.3f}")
assert innen.min() >= MINDEST_LEUCHTDICHTE, "Pergament unter Text zu dunkel"

ZIEL.parent.mkdir(parents=True, exist_ok=True)
Image.fromarray(bild).save(ZIEL, "WEBP", quality=90, method=6)
groesse = ZIEL.stat().st_size
print(f"{ZIEL.name}: {groesse // 1024} KB")
assert groesse <= 300 * 1024, "Pergament größer als 300 KB"
