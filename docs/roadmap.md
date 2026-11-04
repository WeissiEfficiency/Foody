# Roadmap

Stand: 4. November 2026. Foody ist offline-first (ADR 0001); ein eigener Sync-Server ist der Normalfall, aber
optional (ADR 0006, 0011). Die App nutzt das Internet nur für den Sync, die Produktsuche beim Scan (abschaltbar); KI läuft nur auf dem
Handy (Fotoerkennung, ADR 0017).

## Erledigt: V3

Seit dem 28.10.2026 auf Handy, Tablet und Server – Plan: [`v3-plan.md`](../ToDoS/archiv/v3-plan.md),
Abnahme: [`v3-abnahme.md`](../ToDoS/archiv/v3-abnahme.md) (beide archiviert), Entscheidung: [ADR 0011](adr/0011-v3-umfang-ein-nutzer.md).
Gestrichen gegenüber der v1: Vorrat, Web-App, Haushalte/Einladungen, Teilen als Text, Sterne.

## Erledigt: Aufräumen und Alltagsbefunde

Altlasten der v1, Review-Befunde und Optimierungen, bis 01.11. abgearbeitet (Version 3.1.0):
[`ToDoS/aufraeumplan.md`](../ToDoS/aufraeumplan.md).

## Erledigt: Fotoerkennung und BLS (Version 3.4.1)

Mahlzeit aus Foto auf dem Handy (ADR 0017, Plan [`ToDoS/foto-erkennung-plan.md`](../ToDoS/foto-erkennung-plan.md)) und
Nachschlagen im Bundeslebensmittelschlüssel (ADR 0018). Offen: Test und Abnahme der Fotoerkennung im Alltag (Phase 5).

Die KI-Hilfe über den eigenen Server ist gestrichen (ADR 0019); ihr Plan liegt archiviert in
[`ToDoS/archiv/ki-plan.md`](../ToDoS/archiv/ki-plan.md).

## Später

| Funktion | Vorbild | Anmerkung |
|---|---|---|
| Rezept-Import per Web-Link | Paprika, Samsung Food | Server holt die Seite, Import aus `:domain` |
| Wochenplan wiederholen / als Vorlage | Mealime | |
| Startbildschirm-Widget (Einkauf, nächste Mahlzeit) | Bring!, KptnCook | Jetpack Glance, liest nur aus Room |

## Bewusst nicht geplant

| Funktion | Warum nicht |
|---|---|
| Einkauf in Echtzeit, Haushalte | eine Person, zwei Geräte – Sync beim Öffnen reicht |
| Vorrat und MHD-Erinnerungen | im Alltag nicht gepflegt; die Vorschau der Einkaufsliste deckt „schon da“ ab |
| Web-App | wird nicht benutzt, App auf Handy und Tablet reicht |
| Prospekt-Angebote | Werbepartner |
