# Foodmanager

Ein lokaler Foodmanager für Zuhause auf Basis von Spring Boot. Die Anwendung ist für den Einsatz auf einem Raspberry Pi gedacht und kann im Heimnetz bequem vom Handy aus genutzt werden.

## Funktionen

- Lebensmittel per Handy über die Web-App scannen
- Barcode-Lookup über Open Food Facts als optionale Produkthilfe
- Manuelle Erfassung mit Name, Marke, Ablaufdatum, Menge mit Einheit (Stück, g, ml) und Lagerort
- Optionales Bild per Datei-Upload oder externe Bild-URL
- Dashboard mit bald ablaufenden und abgelaufenen Produkten
- Rezepte mit Vorratsabgleich (auch Mengen, z. B. „250 g Mehl“) und „Was kann ich kochen?“, bald Ablaufendes zuerst
- Tägliche Ablauf-Benachrichtigung aufs Handy per ntfy
- Persistente lokale Speicherung über H2-Dateidatenbank
- Optionale PostgreSQL-Datenbank über Docker Compose
- Login über Keycloak mit lokalem Realm-Import
- GS1-Codes (DataMatrix/QR): Ablaufdatum wird beim Scan automatisch übernommen
- Serien-Scan zum Einräumen: bekannte Produkte +1, neue auf die Liste „Noch einzuräumen“
- Mehrere Ablaufdaten pro Produkt (Chargen), verbraucht wird automatisch das älteste zuerst
- „Rückgängig“ nach Verbrauchen, Aufstocken und Löschen
- Mehrere Personen gleichzeitig: Schutz vor Überschreiben, Einkaufsliste aktualisiert sich live
- Gemerkte Produktdaten: ein zweiter Scan klappt sofort, auch wenn Open Food Facts nicht erreichbar ist
- Ablaufdatum per Foto (Texterkennung im Browser) als Vorschlag
- Tägliches Backup von Datenbank, Bildern und Benutzern

## Technik

- Java 21
- Spring Boot
- Spring MVC + Thymeleaf
- Spring Data JPA
- H2-Dateidatenbank
- PostgreSQL (optional über Docker)
- Spring Security + OAuth2/OIDC mit Keycloak

## Starten unter Windows

```powershell
Set-Location "D:\LAP_Aufgaben_Praxis\foodmanager"
.\mvnw.cmd spring-boot:run
```

Danach ist die App lokal unter `http://localhost:8085` erreichbar.

## Login mit Keycloak

Die App ist jetzt mit Keycloak abgesichert. Beim Aufruf geschützter Seiten wirst du direkt zum Login weitergeleitet.

### Lokale Standarddaten

- Keycloak Admin: `admin` / `admin123` (nur lokal, auf dem Pi ein eigenes Passwort in der `.env`)
- App-Client: `foodmanager-web`
- Client-Secret: `foodmanager-web-secret`

Es gibt bewusst **keinen Demo-Benutzer** mehr. Benutzer legst du in Keycloak an (siehe „Benutzer anlegen“).

### Benutzer anlegen (z. B. für die Familie)

1. Keycloak-Admin öffnen: lokal `http://localhost:8081`, auf dem Pi `https://<TS_HOST>:8443`.
2. Mit dem Admin-Konto anmelden und oben links den Realm **foodmanager** wählen.
3. **Users → Create new user**: Benutzername eintragen, **Create**.
4. Reiter **Credentials → Set password**: Passwort setzen. „Temporary“ an lassen, dann wählt die Person beim
   ersten Login ihr eigenes Passwort.

Jede Person bekommt am besten einen eigenen Benutzer. Alle sehen dasselbe Inventar, „Rückgängig“ gilt aber
nur für die eigenen Änderungen.

### Alten Demo-Benutzer auf dem Pi löschen

Der Realm wird nur beim allerersten Start importiert. Wurde der Pi schon mit dem alten Import gestartet,
existiert `demo / demo1234` dort noch. Entweder in der Admin-Oberfläche unter **Users** löschen oder per Befehl:

```bash
docker exec foodmanager-keycloak /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8080 --realm master --user admin --password '<KEYCLOAK_ADMIN_PASSWORD>'
docker exec foodmanager-keycloak sh -c \
  'ID=$(/opt/keycloak/bin/kcadm.sh get users -r foodmanager -q username=demo --fields id --format csv --noquotes); \
   [ -n "$ID" ] && /opt/keycloak/bin/kcadm.sh delete users/$ID -r foodmanager && echo gelöscht'
```

### Starten von Keycloak und Datenbank per Docker

```powershell
Set-Location "D:\LAP_Aufgaben_Praxis\foodmanager"
docker compose up -d
```

Keycloak läuft dann standardmäßig unter `http://localhost:8081`.

### Anwendung starten

```powershell
Set-Location "D:\LAP_Aufgaben_Praxis\foodmanager"
.\mvnw.cmd spring-boot:run
```

Danach rufst du die App wieder unter `http://localhost:8085` auf und wirst bei geschützten Seiten automatisch zu Keycloak weitergeleitet.

## Datenbank über Docker nutzen

Standardmäßig läuft das Projekt lokal weiter mit der H2-Dateidatenbank. Wenn du die DB auf Docker umstellen möchtest, ist jetzt zusätzlich PostgreSQL per Compose vorbereitet.

Für das Login-System ist Keycloak im selben `docker-compose.yml` enthalten und importiert automatisch das Realm aus `src/main/resources/keycloak/realm-foodmanager.json`.

### 1. Optional: Umgebungsdatei anlegen

Du kannst die Beispielwerte aus `.env.example` in eine eigene `.env` übernehmen und dort bei Bedarf Benutzer, Passwort und Port anpassen.

### 2. PostgreSQL in Docker starten

```powershell
Set-Location "D:\LAP_Aufgaben_Praxis\foodmanager"
docker compose up -d
```

Die Daten liegen danach persistent im Docker-Volume `foodmanager-postgres-data`.

### 3. Spring Boot mit PostgreSQL-Profil starten

```powershell
Set-Location "D:\LAP_Aufgaben_Praxis\foodmanager"
$env:SPRING_PROFILES_ACTIVE="postgres"
.\mvnw.cmd spring-boot:run
```

Alternativ ohne dauerhaft gesetzte Session-Variable:

```powershell
Set-Location "D:\LAP_Aufgaben_Praxis\foodmanager"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=postgres"
```

### 4. Docker-DB wieder stoppen

```powershell
Set-Location "D:\LAP_Aufgaben_Praxis\foodmanager"
docker compose down
```

Wenn du auch das Daten-Volume entfernen willst:

```powershell
Set-Location "D:\LAP_Aufgaben_Praxis\foodmanager"
docker compose down -v
```

## Nutzung mit Raspberry Pi

1. Starte die App auf dem Raspberry Pi.
2. Verbinde dein Handy mit demselben WLAN.
3. Öffne im Handy-Browser die IP des Raspberry Pi, z. B. `http://192.168.1.50:8085`.
4. Öffne die Seite `Scannen` und erlaube Kamerazugriff.

### Alles per Docker auf dem Pi (über Tailscale)

```bash
cp .env.example .env    # TS_HOST, Passwörter und Client-Secret eintragen
docker compose up -d --build
```

Das `Dockerfile` baut die App selbst, auf dem Pi braucht es kein Java. Keycloak importiert den Realm aus
`src/main/resources/keycloak/` nur beim ersten Start. Gibt es den Realm schon im Volume `keycloak-data`,
müssen neue Redirect-Adressen in der Keycloak-Admin-Oberfläche nachgetragen werden.

## Ablauf-Benachrichtigung (ntfy)

1. Auf dem Handy die App **ntfy** installieren und ein Topic mit langem, zufälligem Namen abonnieren.
2. Denselben Namen in der `.env` als `FOODMANAGER_NTFY_TOPIC` eintragen.
3. Jeden Tag um 18:30 kommt eine Liste der abgelaufenen und bald ablaufenden Produkte, dazu ein Koch-Tipp.
   Ist nichts fällig, wird nichts geschickt. Mit „Jetzt benachrichtigen“ auf dem Dashboard kann man es sofort testen.

Uhrzeit ändern: `FOODMANAGER_NOTIFY_CRON` (Standard `0 30 18 * * *`). Eigener ntfy-Server: `FOODMANAGER_NTFY_SERVER`
und bei Bedarf `FOODMANAGER_NTFY_TOKEN`.

## Backup und Wiederherstellen

Der Container `foodmanager-backup` sichert beim Start und danach alle 24 Stunden nach `./backups`:

- `db_<Datum>.dump`: die Datenbank (Inventar, Rezepte, Einkaufsliste)
- `images_<Datum>.tar.gz`: die hochgeladenen Bilder
- `keycloak_<Datum>.tar.gz`: die Benutzer und Passwörter

Backups älter als 14 Tage werden gelöscht (`BACKUP_KEEP_DAYS` in der `.env`). Wichtig: Kopiere `./backups`
ab und zu auf einen anderen Rechner oder USB-Stick. Stirbt die SD-Karte, ist sonst auch das Backup weg.

Prüfen, ob es läuft: `docker logs foodmanager-backup` und `ls -lh backups/`.

**Datenbank wiederherstellen** (ersetzt den aktuellen Stand):

```bash
docker compose stop app
docker exec -i foodmanager-db pg_restore -U foodmanager -d foodmanager --clean --if-exists < backups/db_2026-10-30_0300.dump
docker compose start app
```

**Bilder wiederherstellen:** `tar -xzf backups/images_2026-10-30_0300.tar.gz -C data/`

**Keycloak-Benutzer wiederherstellen** (nur wenn die Benutzer weg sind):

```bash
docker compose stop keycloak
docker run --rm -v foodmanager_keycloak-data:/kc -v "$PWD/backups":/b alpine \
  sh -c 'rm -rf /kc/* && tar -xzf /b/keycloak_2026-10-30_0300.tar.gz -C /kc'
docker compose start keycloak
```

(Der Volume-Name beginnt mit dem Ordnernamen, prüfen mit `docker volume ls`.)

## Wichtige Speicherorte

- Datenbank: `./data/foodmanager-db`
- Hochgeladene Bilder: `./data/images`
- Docker-PostgreSQL-Volume: `foodmanager-postgres-data`
- Keycloak-Realm-Import: `src/main/resources/keycloak/realm-foodmanager.json`

## Hinweise zum Scanner

Die Scan-Seite nutzt den nativen Browser-`BarcodeDetector`, wenn dieser vorhanden ist. Das klappt besonders gut auf aktuellen Android-Geräten in Chrome/Edge. Wenn dein Browser das nicht unterstützt, kannst du den Barcode manuell eintragen und trotzdem alle Produkte verwalten.

Für erste lokale Tests kannst du auch einfach die Kamera deines Windows-Laptops verwenden. Die Scan-Seite versucht zuerst eine rückseitige Kamera zu nutzen und fällt danach automatisch auf die verfügbare Webcam zurück.

## Was du für Keycloak machen musst

1. `docker compose up -d` starten.
2. Die App mit `.\mvnw.cmd spring-boot:run` starten.
3. Im Browser die App öffnen.
4. Einen Benutzer im Realm `foodmanager` anlegen (siehe „Benutzer anlegen“) und damit anmelden.
5. Wenn du eigene Werte verwenden willst, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET` und `KEYCLOAK_ISSUER_URI` in `.env` bzw. den Umgebungsvariablen anpassen.

## Tests ausführen

```powershell
Set-Location "D:\LAP_Aufgaben_Praxis\foodmanager"
.\mvnw.cmd test
```


