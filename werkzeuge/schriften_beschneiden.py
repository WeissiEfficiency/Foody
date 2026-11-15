"""
Beschneidet die Schriften der Stile (ADR 0020) auf die Zeichen, die Foody braucht, und legt sie nach `res/font`.

Quelle: github.com/google/fonts (Ordner `ofl/…`, Special Elite unter `apache/specialelite`). Die Dateien werden von Hand
geladen und mit ihrem Originalnamen in einen Ordner gelegt, Liste unten (QUELLEN).

Aufruf (braucht fonttools, `pip install fonttools`):
    python werkzeuge/schriften_beschneiden.py <ordner-mit-den-originalen>

Was bleibt:
- Latein mit Latin-1 und Latin Extended-A (Namen wie „Crème brûlée“ oder „Łódź“), die übliche Typografie (– „“ › · …)
  und jedes weitere Zeichen, das im Code der App steht (etwa 済 月 籠 ✓ ▶ ＋). Zeichen, die eine Schrift nicht hat,
  zeichnet Android in der Systemschrift.
- Bei variablen Schriften nur der Bereich der Achse `wght`, den die App nutzt.
- Die Standard-Features von fonttools (Kerning, Ligaturen, Zeichenzusammensetzung); Kapitälchen, Ziffernvarianten
  und senkrechte Formen fallen weg, die App nutzt sie nicht. Hinting fällt weg (Android rendert ohne).

Ausnahme: Nennt eine Schrift in ihren Namenseinträgen einen „Reserved Font Name“ (OFL), darf eine veränderte Fassung
diesen Namen nicht tragen. Solche Schriften (IM Fell English, UnifrakturCook, Share Tech Mono) kopiert das Werkzeug
unverändert.
"""
import shutil
import re
import sys
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

WURZEL = Path(__file__).resolve().parent.parent
ZIEL = WURZEL / "android/core/designsystem/src/main/res/font"

# Zieldatei → (Originaldatei, Bereich der Achse wght oder None für statische Schriften)
QUELLEN = {
    # Q · Düstere Sage
    "cinzel.ttf": ("Cinzel[wght].ttf", (500, 700)),
    "eb_garamond.ttf": ("EBGaramond[wght].ttf", (400, 700)),
    # R · Anime
    "dela_gothic_one.ttf": ("DelaGothicOne-Regular.ttf", None),
    "mplus_rounded_medium.ttf": ("MPLUSRounded1c-Medium.ttf", None),
    "mplus_rounded_bold.ttf": ("MPLUSRounded1c-Bold.ttf", None),
    "mplus_rounded_extrabold.ttf": ("MPLUSRounded1c-ExtraBold.ttf", None),
    # S · Edo
    "kaisei_decol_medium.ttf": ("KaiseiDecol-Medium.ttf", None),
    "kaisei_decol_bold.ttf": ("KaiseiDecol-Bold.ttf", None),
    "zen_kaku_regular.ttf": ("ZenKakuGothicNew-Regular.ttf", None),
    "zen_kaku_medium.ttf": ("ZenKakuGothicNew-Medium.ttf", None),
    "zen_kaku_bold.ttf": ("ZenKakuGothicNew-Bold.ttf", None),
    # T · Bayrisch
    "unifraktur_cook.ttf": ("UnifrakturCook-Bold.ttf", None),
    "alegreya.ttf": ("Alegreya[wght].ttf", (500, 800)),
    "alegreya_sans_regular.ttf": ("AlegreyaSans-Regular.ttf", None),
    "alegreya_sans_medium.ttf": ("AlegreyaSans-Medium.ttf", None),
    "alegreya_sans_bold.ttf": ("AlegreyaSans-Bold.ttf", None),
    # U · Kosmisches Grauen
    "im_fell_english.ttf": ("IMFeENrm28P.ttf", None),
    "im_fell_english_kursiv.ttf": ("IMFeENit28P.ttf", None),
    "im_fell_english_sc.ttf": ("IMFeENsc28P.ttf", None),
    "special_elite.ttf": ("SpecialElite-Regular.ttf", None),
    # V · Sternenkreuzer
    "exo2.ttf": ("Exo2[wght].ttf", (500, 800)),
    "share_tech_mono.ttf": ("ShareTechMono-Regular.ttf", None),
}

GRUNDZEICHEN = [
    *range(0x20, 0x7F),  # ASCII
    *range(0xA0, 0x180),  # Latin-1 und Latin Extended-A
    0x2C6, 0x2DA, 0x2DC,
    *range(0x2010, 0x2028), *range(0x2030, 0x203B),  # Striche, Anführungen, Punkte, ‹ ›
    0x20AC, 0x2122, 0x2212, 0x2215, 0x2026, 0x2116,
]


def zeichen_der_app() -> set[int]:
    """Alle Zeichen aus dem Quelltext und den Texten der App (nur `src/main`)."""
    gefunden: set[int] = set()
    for datei in (WURZEL / "android").rglob("*"):
        if datei.suffix not in (".kt", ".xml") or "src/main" not in datei.as_posix() or "/build/" in datei.as_posix():
            continue
        gefunden.update(ord(c) for c in datei.read_text(encoding="utf-8") if ord(c) > 0x7E)
    return gefunden


def reservierter_name(schrift: TTFont) -> bool:
    """`True`, wenn Copyright oder Lizenz der Schrift einen „Reserved Font Name“ nennen."""
    texte = (str(r) for r in schrift["name"].names if r.nameID in (0, 13))
    return any("reserved font name" in t.lower() for t in texte)


def beschneiden(quelle: Path, ziel: Path, achse, zeichen: set[int]) -> bool:
    """Legt [quelle] beschnitten nach [ziel]; `False`, wenn sie wegen eines reservierten Namens unverändert bleibt."""
    # Erst beschneiden, dann die Achse einschränken (umgekehrt stolpert fonttools über die Variationen)
    schrift = TTFont(quelle)
    if reservierter_name(schrift):
        shutil.copyfile(quelle, ziel)
        return False
    optionen = subset.Options()
    optionen.hinting = False
    optionen.name_IDs = ["*"]  # Copyright und Lizenz bleiben in der Datei
    optionen.name_languages = ["*"]
    optionen.notdef_outline = True
    beschnitt = subset.Subsetter(optionen)
    beschnitt.populate(unicodes=sorted(zeichen))
    beschnitt.subset(schrift)
    if achse is not None:
        schrift = instancer.instantiateVariableFont(schrift, {"wght": achse})
    schrift.save(ziel)
    return True


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    ordner = Path(sys.argv[1])
    zeichen = set(GRUNDZEICHEN) | zeichen_der_app()
    vorher = nachher = 0
    for ziel, (name, achse) in QUELLEN.items():
        quelle = ordner / name
        if not quelle.exists():
            sys.exit(f"Fehlt: {quelle}")
        beschnitten = beschneiden(quelle, ZIEL / ziel, achse, zeichen)
        a, b = quelle.stat().st_size, (ZIEL / ziel).stat().st_size
        vorher += a
        nachher += b
        hinweis = "" if beschnitten else "  (unverändert, Reserved Font Name)"
        print(f"{ziel:32} {a / 1024:8.0f} KB → {b / 1024:6.0f} KB{hinweis}")
    print(f"{'zusammen':32} {vorher / 1024:8.0f} KB → {nachher / 1024:6.0f} KB")
    fremd = sorted(c for c in zeichen_der_app() if c > 0x2E80)
    print("Ostasiatische Zeichen aus dem Code:", "".join(chr(c) for c in fremd if re.match(r"\S", chr(c))))


if __name__ == "__main__":
    main()
