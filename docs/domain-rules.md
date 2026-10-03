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
    diese zeigt hinzugefügt/geändert/entfällt. Abgehakte Einträge werden dabei nie gelöscht; sie kommen nur zurück
    auf die Liste, wenn jetzt mehr gebraucht wird als zuvor.
11. Jeder generierte Listeneintrag speichert seine Herkunft (Planposition, Rezeptzeile, Beitrag).
12. Kalendertage sind `LocalDate`; Sommer-/Winterzeit hat keinen Einfluss auf die Tageszuordnung.
13. Import: Eine Zutat ohne Zahl (z. B. „Salz und Pfeffer“) erhält die Menge 0 = „nach Bedarf“. Sie erscheint nicht
    auf der Einkaufsliste und beeinflusst die Nährwert-Vollständigkeit nicht.
14. Import: Der Zutatenname besteht aus den führenden Substantiven; Adjektive und Zusätze („rote“, „geschälte“,
    „à ca. 400 g“) werden Hinweis. So laufen z. B. rote, grüne und gelbe Paprika zu einer Einkaufsposition zusammen.
15. Zutatennamen werden nur über eine feste Synonymliste und einfache Pluralformen vereinheitlicht
    (`IngredientCatalog`), nie über Ähnlichkeit: „saure Sahne“ bleibt eine eigene Zutat (vgl. Regel 7).
16. Zutaten aus der Liste „nie einkaufen“ (Wasser, Leitungswasser) erscheinen nicht auf der Einkaufsliste.
17. Ein Rezept direkt auf die Einkaufsliste zu setzen folgt den Regeln 9, 13 und 16, zieht aber keinen Vorrat ab.
    Die Einträge gelten als manuell, damit eine Neuberechnung (Regel 10) sie nicht als „entfällt“ entfernt.
18. Import: Eine Quelle (URL), die schon als Rezept existiert – auch archiviert –, wird nicht erneut angelegt.
    Eine Kopie eines Rezepts übernimmt die Quelle nicht.
19. Kochmodus: Bei Zeitspannen („10 - 15 Minuten“, „10 bis 15 Minuten“) startet der Timer mit der unteren Grenze. Eine Menge im Schritt
    gehört zu einer Zutat nur, wenn sie direkt davor steht (höchstens zwei kleingeschriebene Wörter dazwischen).
20. Import: Mengenspannen („2 - 3 EL“) rechnen mit der unteren Grenze; die obere bleibt als Hinweis („bis 3“).
    Brüche und gemischte Zahlen („1/2“, „1 1/2“, „1½“) werden als Zahl gelesen, nie nur der Zähler.
21. Stück, Packungen und Dosen werden auf der Einkaufsliste auf ganze Einheiten aufgerundet (nach Vorratsabzug);
    Gramm und Milliliter bleiben exakt. Rechenrauschen unter 0,005 löst kein zusätzliches Stück aus.
