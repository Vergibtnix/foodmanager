# Foodmanager

Ein lokaler Foodmanager für Zuhause auf Basis von Spring Boot. Die Anwendung ist für den Einsatz auf einem Raspberry Pi gedacht und kann im Heimnetz bequem vom Handy aus genutzt werden.

## Funktionen

- Lebensmittel per Handy über die Web-App scannen
- Barcode-Lookup über Open Food Facts als optionale Produkthilfe
- Manuelle Erfassung mit Name, Marke, Ablaufdatum, Menge und Lagerort
- Optionales Bild per Datei-Upload oder externe Bild-URL
- Dashboard mit bald ablaufenden und abgelaufenen Produkten
- Persistente lokale Speicherung über H2-Dateidatenbank
- Optionale PostgreSQL-Datenbank über Docker Compose
- Login über Keycloak mit lokalem Realm-Import

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

Danach ist die App lokal unter `http://localhost:8080` erreichbar.

## Login mit Keycloak

Die App ist jetzt mit Keycloak abgesichert. Beim Aufruf geschützter Seiten wirst du direkt zum Login weitergeleitet.

### Lokale Standarddaten

- Keycloak Admin: `admin` / `admin123`
- Demo-Benutzer im Realm `foodmanager`: `demo` / `demo1234`
- App-Client: `foodmanager-web`
- Client-Secret: `foodmanager-web-secret`

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

Danach rufst du die App wieder unter `http://localhost:8080` auf und wirst bei geschützten Seiten automatisch zu Keycloak weitergeleitet.

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
3. Öffne im Handy-Browser die IP des Raspberry Pi, z. B. `http://192.168.1.50:8080`.
4. Öffne die Seite `Scannen` und erlaube Kamerazugriff.

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
4. Mit `demo / demo1234` anmelden oder deinen eigenen Benutzer im Realm `foodmanager` anlegen.
5. Wenn du eigene Werte verwenden willst, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET` und `KEYCLOAK_ISSUER_URI` in `.env` bzw. den Umgebungsvariablen anpassen.

## Tests ausführen

```powershell
Set-Location "D:\LAP_Aufgaben_Praxis\foodmanager"
.\mvnw.cmd test
```


