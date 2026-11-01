package de.foody.core.testing

import com.github.takahirom.roborazzi.RoborazziOptions

/**
 * Optionen für alle Referenzbilder: Aufnahme in halber Auflösung (E5: hält das Repo klein, Text bleibt lesbar).
 * Höchstens 0,1 % geänderte Pixel gelten als gleich (Schriftglättung); schon ein umgefärbtes Herz ist mehr.
 */
val FoodyBildvergleich = RoborazziOptions(
    recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
    compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f),
)
