# Fachliche Invarianten

1. Skalierte Menge = Rezeptmenge × geplante Portionen / Standardportionen.
2. Einheiten gehören zu genau einer Dimension (Masse, Volumen, Anzahl). Innerhalb einer Dimension wird frei umgerechnet.
3. Masse ↔ Volumen nur mit hinterlegter Dichte; Stück ↔ Masse nur mit Stückgewicht. Sonst bleiben Positionen getrennt.
4. Rechnen erfolgt mit `BigDecimal` in Basiseinheiten (g, ml, Stück), nie mit `Float`.
5. Eine fehlende Nährwertangabe ist unbekannt, nicht null. Die UI zeigt Vollständigkeit und markiert Untergrenzen.
6. Energie wird in kJ gespeichert; kcal ist eine abgeleitete Anzeige (÷ 4,184).
7. Zusammengeführt wird nur bei gleicher kanonischer Zutat und kompatibler Dimension – nie über Freitextähnlichkeit.
8. Vorrat wird beim Erzeugen einer Liste abgezogen, niemals unter 0. Planung allein verändert den Vorrat nicht;
   erst „gekocht“ bucht Verbrauch ab.
9. Optionale Zutaten werden standardmäßig nicht eingekauft.
10. Eine erzeugte Einkaufsliste ist ein Snapshot und ändert sich nur nach expliziter Neuberechnung;
    diese zeigt hinzugefügt/geändert/entfällt. Abgehakte Einträge werden dabei nie gelöscht.
11. Jeder generierte Listeneintrag speichert seine Herkunft (Planposition, Rezeptzeile, Beitrag).
12. Kalendertage sind `LocalDate`; Sommer-/Winterzeit hat keinen Einfluss auf die Tageszuordnung.
13. Import: Eine Zutat ohne Zahl (z. B. „Salz und Pfeffer“) erhält die Menge 0 = „nach Bedarf“. Sie erscheint nicht
    auf der Einkaufsliste und beeinflusst die Nährwert-Vollständigkeit nicht.
14. Import: Der Zutatenname besteht aus den führenden Substantiven; Adjektive und Zusätze („rote“, „geschälte“,
    „à ca. 400 g“) werden Hinweis. So laufen z. B. rote, grüne und gelbe Paprika zu einer Einkaufsposition zusammen.
