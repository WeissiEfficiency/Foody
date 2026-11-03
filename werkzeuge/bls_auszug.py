"""
Erzeugt den BLS-Auszug der App aus der amtlichen Excel-Datei des Bundeslebensmittelschlüssels.

Quelle: Max Rubner-Institut (2025): Bundeslebensmittelschlüssel (BLS), Version 4.0,
DOI 10.25826/Data20251217-134202-0, Lizenz CC BY 4.0 (https://blsdb.de/download).

Aufruf (nur Standardbibliothek):
    python werkzeuge/bls_auszug.py <BLS_4_0_Daten_2025_DE.xlsx> android/core/data/src/main/assets/bls/bls_4_0.tsv

Der Auszug enthält je Lebensmittel nur, was Foody speichert: Code, Name und die Werte je 100 g für Energie (kJ),
Eiweiß, Kohlenhydrate, Zucker, Fett, Ballaststoffe und Salz. Fehlende Werte bleiben leer.
"""
import re
import sys
import zipfile
import xml.etree.ElementTree as ET
from decimal import Decimal, InvalidOperation
from pathlib import Path

NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"

# Spaltenkopf im BLS (Anfang genügt) → Spalte im Auszug
SPALTEN = [
    ("BLS Code", "code"),
    ("Lebensmittelbezeichnung", "name"),
    ("ENERCJ Energie (Kilojoule)", "kj"),
    ("PROT625 Protein", "eiweiss"),
    ("CHO Kohlenhydrate, verfügbar", "kohlenhydrate"),
    ("SUGAR Zucker", "zucker"),
    ("FAT Fett", "fett"),
    ("FIBT Ballaststoffe", "ballaststoffe"),
    ("NACL Salz", "salz"),
]


def spalte(ref: str) -> int:
    n = 0
    for ch in re.match(r"[A-Z]+", ref).group(0):
        n = n * 26 + ord(ch) - 64
    return n - 1


def zeilen(xlsx: Path):
    """Zeilen des ersten Blatts als Listen von Texten."""
    with zipfile.ZipFile(xlsx) as z:
        texte = [
            "".join(t.text or "" for t in si.iter(f"{NS}t"))
            for si in ET.parse(z.open("xl/sharedStrings.xml")).getroot().iter(f"{NS}si")
        ]
        for _, el in ET.iterparse(z.open("xl/worksheets/sheet1.xml")):
            if el.tag != f"{NS}row":
                continue
            werte = {}
            for c in el.iter(f"{NS}c"):
                v = c.find(f"{NS}v")
                if v is None:
                    continue
                werte[spalte(c.get("r"))] = texte[int(v.text)] if c.get("t") == "s" else v.text
            el.clear()
            if werte:
                yield [werte.get(i, "") for i in range(max(werte) + 1)]


def zahl(text: str) -> str:
    """Zahl ohne überflüssige Nullen, höchstens drei Nachkommastellen; keine oder negative Angabe → leer."""
    try:
        d = Decimal(text.strip().replace(",", "."))
    except InvalidOperation:
        return ""
    if d < 0:
        return ""
    d = d.quantize(Decimal("0.001")).normalize()
    return format(d, "f")


def main(xlsx: Path, ziel: Path) -> None:
    it = zeilen(xlsx)
    kopf = next(it)
    index = []
    for anfang, _ in SPALTEN:
        treffer = [i for i, k in enumerate(kopf) if k.startswith(anfang)]
        if not treffer:
            sys.exit(f"Spalte fehlt im BLS: {anfang}")
        index.append(treffer[0])
    ausgabe = []
    for z in it:
        z += [""] * (max(index) + 1 - len(z))
        code, name = z[index[0]].strip(), " ".join(z[index[1]].split())
        if not code or not name:
            continue
        ausgabe.append([code, name.replace("\t", " ")] + [zahl(z[i]) for i in index[2:]])
    ausgabe.sort(key=lambda r: r[0])
    ziel.parent.mkdir(parents=True, exist_ok=True)
    with open(ziel, "w", encoding="utf-8", newline="\n") as f:
        f.write("# Max Rubner-Institut (2025): Bundeslebensmittelschlüssel (BLS), Version 4.0; CC BY 4.0; "
                "Auszug, Werte je 100 g\n")
        f.write("\t".join(s for _, s in SPALTEN) + "\n")
        for r in ausgabe:
            f.write("\t".join(r) + "\n")
    print(f"{len(ausgabe)} Lebensmittel -> {ziel}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(Path(sys.argv[1]), Path(sys.argv[2]))
