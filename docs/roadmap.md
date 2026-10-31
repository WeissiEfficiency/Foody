# Roadmap

Stand: 31. Oktober 2026. Foody ist offline-first (ADR 0001); ein eigener Sync-Server ist der Normalfall, aber
optional (ADR 0006, 0011). Die App nutzt das Internet nur für den Sync, die Produktsuche beim Scan (abschaltbar) und
künftig die KI-Hilfe über den eigenen Server.

## Erledigt: V3

Seit dem 28.10.2026 auf Handy, Tablet und Server – Plan: [`ToDoS/v3-plan.md`](../ToDoS/v3-plan.md),
Abnahme: [`ToDoS/v3-abnahme.md`](../ToDoS/v3-abnahme.md), Entscheidung: [ADR 0011](adr/0011-v3-umfang-ein-nutzer.md).
Gestrichen gegenüber der v1: Vorrat, Web-App, Haushalte/Einladungen, Teilen als Text, Sterne.

## Jetzt: Aufräumen und Alltagsbefunde

Altlasten der v1, offene Review-Befunde (W1–W6) und Optimierungen: [`ToDoS/aufraeumplan.md`](../ToDoS/aufraeumplan.md).

## Danach: KI-Hilfe

Eigenes Projekt nach dem Aufräumen (über den eigenen Server): Rezept erfassen und anpassen, Wochenplan
vorschlagen, Nährwerte schätzen – Plan: [`ToDoS/ki-plan.md`](../ToDoS/ki-plan.md).

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
