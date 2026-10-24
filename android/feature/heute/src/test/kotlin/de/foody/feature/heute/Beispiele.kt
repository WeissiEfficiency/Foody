package de.foody.feature.heute

import de.foody.core.data.heute.ArtikelKurz
import de.foody.core.data.heute.GeplanteMahlzeit
import de.foody.core.data.heute.OffenerEinkauf
import de.foody.core.designsystem.komponenten.SyncAnzeige
import de.foody.domain.Mahlzeit
import de.foody.domain.Summe
import java.time.LocalDate

/** Erfundene Daten für Bild- und Screenshot-Tests (keine echten Rezepte). */
val BEISPIEL_TAG: LocalDate = LocalDate.of(2026, 10, 24)

fun beispielGeplant(bild: String? = null) = GeplanteMahlzeit(
    planEintragId = "p1", datum = BEISPIEL_TAG, slot = "ABENDESSEN", rezeptId = "r1", titel = "Ofengemüse mit Feta und Kräuterquark",
    portionen = 2, kcalJePortion = 540, zeitMinuten = 45, bild = bild, gekocht = false,
)

fun beispielZustand(bild: String? = null) = HeuteZustand(
    datum = BEISPIEL_TAG,
    naechste = NaechsteMahlzeit(beispielGeplant(bild), morgen = false),
    inspiration = null,
    tagebuch = TagebuchHeute(
        summe = Summe(kcal = 860, eiweiss = 42, kohlenhydrate = 96, fett = 31, vollstaendig = true),
        ziel = 2000,
        mahlzeiten = listOf(
            MahlzeitAbschnitt(Mahlzeit.FRUEHSTUECK, listOf(EintragKurz("e1", "Haferflocken mit Apfel", 380, true)), emptyList()),
            MahlzeitAbschnitt(Mahlzeit.MITTAGESSEN, listOf(EintragKurz("e2", "Linsensuppe", 480, true)), emptyList()),
            MahlzeitAbschnitt(Mahlzeit.SNACK, emptyList(), emptyList()),
            MahlzeitAbschnitt(Mahlzeit.ABENDESSEN, emptyList(), listOf(beispielGeplant(bild))),
        ),
    ),
    einkauf = OffenerEinkauf(
        listeId = "l1", anzahl = 9,
        artikel = listOf(
            ArtikelKurz("Paprika", "3 Stk."), ArtikelKurz("Feta", "200 g"), ArtikelKurz("Zucchini", "500 g"),
            ArtikelKurz("Kräuterquark", "250 g"), ArtikelKurz("Rote Linsen", "300 g"), ArtikelKurz("Zitrone", "1 Stk."),
        ),
    ),
    sync = SyncAnzeige.Synchron,
    datenbankLeer = false,
)

val KEINE_AKTIONEN = HeuteAktionen({ _, _ -> }, {}, {}, {}, {}, {}, {})
