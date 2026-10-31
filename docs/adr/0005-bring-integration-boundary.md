# ADR 0005: Bring!-Integrationsgrenze

Status: abgelöst durch ADR 0011 (2026-10-31). Die V3 hat kein Teilen der Einkaufsliste als Text mehr (gestrichen
mit dem V3-Umfang, `ToDoS/archiv/v3-abnahme.md`, „Gestrichen“); `ShoppingListExporter` gibt es nicht mehr. Weiter gilt:
keine inoffiziellen Bring!-Endpunkte. Ursprünglicher Text:

Status: angenommen. Es gibt keine öffentlich dokumentierte Bring!-Listen-API. Die App exportiert über die
neutrale Schnittstelle `ShoppingListExporter` (aktuell `AndroidShareExporter`, Klartext über das Sharesheet).
Keine inoffiziellen Endpunkte, kein Reverse Engineering. Eine direkte Integration erst nach offizieller
Partnerfreigabe.
