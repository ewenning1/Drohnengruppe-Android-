# Drohnengruppe – sauberer Android-Neubau (Testprojekt 0.8)

**WICHTIG: Dies ist ein vollständiges Android-Quellprojekt und noch KEINE APK.**
Die APK muss mit den normalen Android-Buildwerkzeugen erzeugt werden. In der
Erstellungsumgebung waren Android-SDK, D8 und Gradle nicht verfügbar. Der
native Code wurde daher noch nicht auf einem Gerät ausgeführt oder kompiliert.

## Vorgesehene Funktionen

- Einsatz/Übung, Drohnen, Akkus und Personal lokal verwalten
- Pilot, Co-Pilot optional, Protokollführer auf Einsatzebene
- Qualifikationen A1/A3, A2 und Module Peine; Piloten-Flugstunden berechnen
- GPS über Androids LocationManager mit Berechtigungsabfrage
- Mini-Karte über OpenStreetMap (Internet erforderlich)
- Temperatur via Open-Meteo mit manueller Korrekturmöglichkeit
- Akku-Countdown, Popup in geöffneter App und Systembenachrichtigung bei gesperrtem Gerät
- PDF- und CSV-Gesamtberichte über den Android-Dateispeicherdialog, einschließlich Akku-Diagrammen
- Durchschnittsberechnung/Interpolation verspäteter Akkuabfragen
- PIN 112 zum Abschließen/Bearbeiten und Änderungsverlauf

### Grenzen und notwendige Tests

- Benachrichtigungen sind ohne Erlaubnis deaktiviert. Android kann bei
  Energiesparmaßnahmen ungenaue Alarmzeiten zulassen; eine exakte
  Hintergrundgarantie besteht nicht.
- GPS und Wetter brauchen entsprechende Berechtigungen und meist Internet.
- Mehrere Geräte sind **noch nicht synchronisiert**: Jeder speichert lokal.
- Die App hat noch keinen Datenschutz-/Einsatzzulassungstest durchlaufen.
- Der native Dateiexport ist implementiert, aber noch nicht auf Samsung getestet.
- Ein echter Datenbank- oder Offline-Backup-Test steht ebenfalls aus.

## Cloud-Build ohne Android Studio am eigenen PC

1. Leeres **privates GitHub-Repository** erstellen.
2. Den **entpackten Inhalt dieses Projektordners** dort hochladen (die
   Ordnerstruktur einschließlich `.github/workflows/build-apk.yml` behalten).
3. Unter **Actions** den Workflow `Android-Test-APK` starten (oder nach Push
   automatisch starten lassen).
4. Nach erfolgreichem Lauf unter **Artifacts** die Debug-APK herunterladen und
   auf dem Android-Smartphone installieren.

**Achtung – getrennte Test-App:** Die neue Version hat absichtlich die
Paketkennung `de.feuerwehr.drohnenlog8`, weil ein GitHub-Debug-Build nicht mit
dem Zertifikat der bisherigen 0.4/0.5 signiert wird. Dadurch lässt sie sich
parallel installieren und kann alte Daten nicht überschreiben. Alte Daten
werden **nicht automatisch** übernommen; Testdaten können gegebenenfalls über
JSON-Backup exportiert/importiert werden. Die fehlerhafte alte App bitte nicht
löschen, bevor die neue zuverlässig läuft und Daten gesichert wurden.

Wenn später eine eigene private Release-Signierung und kontrollierte
Migration eingerichtet werden, lassen sich normale Updates erstellen.

## Entwicklung lokal (optional)

Mit Android Studio den Ordner öffnen oder Gradle 8.10.2 und Android SDK 35
verwenden, dann `gradle :app:assembleDebug` ausführen.

## Technische Dateien

- `app/src/main/java/de/feuerwehr/drohnenflug/MainActivity.java`: Standort,
  Wetter, Speicherdialog, Reminder-Brücke, WebView
- `app/src/main/java/de/feuerwehr/drohnenflug/ReminderReceiver.java`: Systembenachrichtigung
- `app/src/main/assets/index.html`: bestehende vollständige UI und Protokolle
- `.github/workflows/build-apk.yml`: Cloud-Build

## Änderungen in 0.8

- Eine zusätzliche feststehende Zurücktaste am unteren Bildschirmrand, solange „Flug vorbereiten“ geöffnet ist. Die obere Zurücktaste bleibt erhalten.
- Die Android-Zurücktaste und Zurückgeste führen aus „Flug vorbereiten“ zur Einsatzübersicht, ohne den Flug zu starten.
- Einsatzadresse (Straße/Hausnummer/Ort, sofern Geocoder verfügbar) ist nach GPS-Abfrage deutlich sichtbar; Koordinaten bleiben separat. Adresse kontrollieren und nötigenfalls manuell angeben.
- Flugzeiten im Einsatzbericht, in CSV und in abgeschlossenen Fluglisten werden als Minuten (mit zwei Dezimalstellen) ausgegeben. Laufende Timer bleiben auf Stunden:Minuten:Sekunden.
- Der manuelle Fernbedienungsakkustand bei Start und Ende wird nun direkt in den Flugdetails zusammen angezeigt. Es gibt keine automatische Bluetooth-Akkumessung.
- Die neue Test-App-ID `de.feuerwehr.drohnenlog8` verhindert Installationskonflikte; ältere Versionen bleiben separat und ihre Daten werden nicht automatisch übernommen.

## Änderungen in 0.7

- Akkuwerte: Flugminute (MM:SS) zusätzlich zur Uhrzeit im Flugprotokoll, PDF und CSV; Startmessung exakt 00:00.
- GPS-Position und automatisch vorgeschlagene Einsatzadresse werden getrennt gespeichert. Geocoder benötigt je nach Gerät eine Datenverbindung; Hausnummer bitte kontrollieren.
- Zurück-Button bei Flug vorbereiten.
- Fernbedienungs-Akku unverändert.

Diese Testversion hat wegen der wechselnden Debug-Signierung bei GitHub Actions eine eigene App-ID (drohnenlog8) und wird neben 0.7 installiert. Daten bleiben in 0.7, werden nicht automatisch übertragen.
