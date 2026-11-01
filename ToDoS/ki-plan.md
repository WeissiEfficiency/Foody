# Foody – KI-Hilfe (eigenes Projekt)

Stand: 2026-10-28. Ursprünglich Phase 7 der V3 ([`v3-plan.md`](archiv/v3-plan.md)), auf Wunsch des Nutzers ausgelagert:
erst Umstieg auf die V3 (Phase 8), danach dieses Projekt. Rahmen aus [ADR 0011](../docs/adr/0011-v3-umfang-ein-nutzer.md):
KI nur über den eigenen Server (`POST /ki/…`), nur auf Knopfdruck, jedes Ergebnis als bearbeitbare Vorschau, ohne
Server oder Netz ausgeblendet.

**Schon da:** Server-Gerüst mit `GET /ki/status` (`FOODY_KI_KEY` fehlt → `eingerichtet: false`) und `KiKontingent`
(Anfragen je Konto und Stunde, `FOODY_KI_PRO_STUNDE`), siehe `docs/architecture.md`.

**Getrennt davon:** „Mahlzeit aus Foto eintragen“ läuft komplett auf dem Gerät, ohne Server und ohne Cloud-KI –
eigener Plan in [`foto-erkennung-plan.md`](foto-erkennung-plan.md).

## Vorher klären
- [ ] Modellwahl und Kosten (ADR), Datenschutzhinweis in der App

## Bauplan (≈ 1,5–2 Wochen)
- [ ] `:core:ki` (App) und `/ki/*` (Server); jedes Ergebnis als Vorschau im Rezept-Editor bzw. Planer, nie direkt
      gespeichert; Knöpfe nur sichtbar mit Server und Netz
- [ ] **Rezept erfassen:** aus Foto (Kochbuch, Zettel) oder Freitext → Zutaten mit Menge/Einheit, Schritte,
      Portionen; Zutaten über den Katalog vereinheitlicht
- [ ] **Rezept anpassen:** „vegetarisch“, „weniger kcal“, „ohne Ofen“ … → neue Variante als Kopie
- [ ] **Wochenplan vorschlagen:** ergänzt „Leere Tage füllen“ um einen KI-Vorschlag aus den eigenen Rezepten
- [ ] **Nährwerte schätzen:** fehlende Werte einer Zutat vorschlagen, als „geschätzt“ markiert

## Abnahme
- [ ] Rezept aus Foto oder Freitext erfassen
- [ ] Rezept anpassen („vegetarisch“, „weniger kcal“, …) als neue Variante
- [ ] Wochenplan-Vorschlag aus den eigenen Rezepten
- [ ] Fehlende Nährwerte einer Zutat schätzen, als „geschätzt“ markiert
- [ ] Ergebnisse immer als bearbeitbare Vorschau; ohne Server/Netz ausgeblendet; Datenschutzhinweis

## Risiken
| Risiko | Gewicht | Gegenmaßnahme |
|---|---|---|
| KI liefert falsche Mengen oder Nährwerte | mittel | immer Vorschau, „geschätzt“-Markierung, Einheiten über `:domain` geprüft |
| KI-Kosten oder Ausfall des Dienstes | gering | Grenzen je Stunde, App funktioniert ohne KI vollständig |
