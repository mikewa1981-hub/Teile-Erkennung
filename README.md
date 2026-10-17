# Teile-Erkennung

Private Android-App: LEGO-Teil mit der Handykamera scannen, erkennen lassen und sehen, in welchen Sets es vorkommt. Gefundene Sets lassen sich per Knopfdruck in die eigene Sammlung übernehmen. Die Sammlung wird mit Rebrickable abgeglichen und kann am PC auf rebrickable.com verwaltet werden.

## Funktionen

- **Scannen**: Live-Kameravorschau mit Zielrahmen und Lichtschalter, Foto aufnehmen.
- **Erkennung**: Das Foto geht an den kostenlosen Dienst [Brickognize](https://brickognize.com). Du bekommst bis zu 8 Treffer mit Vergleichsbild und Übereinstimmung.
- **Vergleich**: Dein Foto steht neben dem Referenzbild des markierten Treffers. In der Teileansicht siehst du zusätzlich das Rebrickable-Bild.
- **Sets**: Alle Sets mit dem Teil (nach Farbe filterbar oder "Alle Farben"), mit Bild, Jahr und Teilezahl. Antippen öffnet eine Großansicht.
- **Sammlung**: Plus-Knopf bei jedem Set. Die Sammlung wird lokal auf dem Handy gespeichert.
- **Abgleich per Knopfdruck**: "Mit PC abgleichen" gleicht in beide Richtungen mit deiner Rebrickable-Sammlung ab (neue Sets hoch, am PC hinzugefügte Sets herunter, Löschungen auf beiden Seiten).

## APK bekommen

Die APK wird von GitHub Actions gebaut:

1. Im Repo auf **Actions** gehen, den letzten Lauf "APK bauen" öffnen.
2. Unten bei **Artifacts** `Teile-Erkennung-APK` herunterladen und entpacken.
3. `app-debug.apk` aufs Handy kopieren und installieren (einmalig "Installation aus unbekannten Quellen" für den Dateimanager oder Browser erlauben).

Neu bauen lassen: Actions → "APK bauen" → **Run workflow**. Alternativ das Projekt in Android Studio öffnen und auf dem Handy starten.

## Einrichten

1. Kostenloses Konto auf [rebrickable.com](https://rebrickable.com) anlegen.
2. Dort unter Einstellungen → API den **API-Key** erzeugen.
3. In der App unter **Einstellungen** den Key speichern und mit Rebrickable-Benutzername und -Passwort anmelden. Das Passwort wird nur für die Anmeldung verwendet, gespeichert wird nur ein Token.

## Hinweise

- Die Erkennung klappt am besten mit einem einzelnen Teil auf hellem, einfarbigem Untergrund, gutem Licht und mittig im Bild.
- Das Foto wird zur Erkennung an Brickognize gesendet. Sonst gehen keine Fotos ins Netz.
- Rebrickable begrenzt Anfragen. "Alle Farben" lädt deshalb Farbe für Farbe und braucht bei Standardteilen etwas länger.
- Es wird eine Debug-APK gebaut, nur für den privaten Gebrauch gedacht.

## Technik

Kotlin, Jetpack Compose, CameraX, OkHttp, Coil. Keine Datenbank: Einstellungen und Sammlung liegen in SharedPreferences.
