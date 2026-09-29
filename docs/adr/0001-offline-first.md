# ADR 0001: Offline-first ohne Konto

Status: angenommen. Alle Kernfunktionen laufen lokal ohne Netzwerk. Kein Server, kein Konto im MVP
(Datenminimierung, geringeres Risiko). Sync wird erst nach stabilem Datenmodell betrachtet; UUIDs,
`createdAt/updatedAt` und Versionsfelder sind dafür bereits vorhanden.
