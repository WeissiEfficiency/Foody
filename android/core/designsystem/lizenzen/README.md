# Lizenzen

Die Schriften unter `src/main/res/font/` stehen unter der SIL Open Font License 1.1, Special Elite unter der Apache
License 2.0 (Volltexte hier):

- `fraunces.ttf` – Fraunces (variable Schrift), © 2018 The Fraunces Project Authors → `OFL-Fraunces.txt`
- `figtree.ttf` – Figtree (variable Schrift), © 2022 The Figtree Project Authors → `OFL-Figtree.txt`
- `cinzel.ttf` – Cinzel (variable Schrift, Stil „Düstere Sage“), © 2020 The Cinzel Project Authors → `OFL-Cinzel.txt`
- `eb_garamond.ttf` – EB Garamond (variable Schrift, Stil „Düstere Sage“), © 2017 The EB Garamond Project Authors →
  `OFL-EBGaramond.txt`
- `dela_gothic_one.ttf` – Dela Gothic One (Stil „Anime“), © 2020 The Dela Gothic Project Authors →
  `OFL-DelaGothicOne.txt`
- `mplus_rounded_*.ttf` – M PLUS Rounded 1c (Stil „Anime“), © 2016 The Rounded M+ Project Authors → `OFL-MPLUSRounded1c.txt`
- `kaisei_decol_*.ttf` – Kaisei Decol (Stil „Edo“) → `OFL-KaiseiDecol.txt`
- `zen_kaku_*.ttf` – Zen Kaku Gothic New (Stil „Edo“) → `OFL-ZenKakuGothicNew.txt`
- `unifraktur_cook.ttf` – UnifrakturCook (Stil „Bayrisch“) → `OFL-UnifrakturCook.txt`
- `alegreya.ttf` – Alegreya (variable Schrift, Stil „Bayrisch“), © 2011 The Alegreya Project Authors → `OFL-Alegreya.txt`
- `alegreya_sans_*.ttf` – Alegreya Sans (Stil „Bayrisch“), © 2013 The Alegreya Sans Project Authors →
  `OFL-AlegreyaSans.txt`
- `im_fell_english*.ttf` – IM Fell English, kursiv und SC (Stil „Kosmisches Grauen“), © 2010 Igino Marini →
  `OFL-IMFellEnglish.txt`
- `special_elite.ttf` – Special Elite (Stil „Kosmisches Grauen“), © 2010 Brian J. Bonislawsky DBA Astigmatic (AOETI) →
  `Apache-SpecialElite.txt`. **Geändert:** auf die Zeichen der App beschnitten, Hinting entfernt.
- `exo2.ttf` – Exo 2 (variable Schrift, Stil „Sternenkreuzer“), © 2013 The Exo 2 Project Authors → `OFL-Exo2.txt`
- `share_tech_mono.ttf` – Share Tech Mono (Stil „Sternenkreuzer“) → `OFL-ShareTechMono.txt`

Quelle: github.com/google/fonts (`ofl/<name>`, Special Elite `apache/specialelite`). Fraunces und Figtree geladen am
2026-10-19, Cinzel und EB Garamond am 2026-11-11, die übrigen am 2026-11-13.

**Beschnitt** (`werkzeuge/schriften_beschneiden.py`, außer Fraunces und Figtree): Latein mit Latin-1 und Latin
Extended-A, die übliche Typografie und die Zeichen aus dem Code der App; variable Schriften nur im genutzten Bereich der
Achse `wght`. Schriften, die in ihren Namenseinträgen einen „Reserved Font Name“ nennen (IM Fell English, UnifrakturCook,
Share Tech Mono), liegen **unverändert** bei: Eine veränderte Fassung dürfte diesen Namen nicht tragen.
