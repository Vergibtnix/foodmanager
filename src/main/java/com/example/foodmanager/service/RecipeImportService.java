package com.example.foodmanager.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Lädt ein Rezept von einer Webseite. Die meisten Rezeptseiten legen ihre Daten als JSON-LD
 * (schema.org "Recipe") im HTML ab. Daraus werden Titel, Zutaten und Zubereitung gelesen.
 *
 * Weil der Server die Adresse selbst abruft, sind Adressen im internen Netz gesperrt
 * (localhost, 192.168.x.x, Keycloak und so weiter).
 */
@Service
public class RecipeImportService {

    public record ImportedRecipe(String title, String ingredients, String instructions, String sourceUrl) {
    }

    /** Fehler mit einer Meldung, die man dem Benutzer direkt zeigen kann. */
    public static class ImportException extends RuntimeException {
        public ImportException(String message) {
            super(message);
        }
    }

    private static final int MAX_URL_LENGTH = 2000;
    private static final int MAX_REDIRECTS = 4;
    private static final int MAX_BYTES = 3 * 1024 * 1024;
    private static final int MAX_TITLE = 200;
    private static final int MAX_INGREDIENTS = 4000;
    private static final int MAX_INSTRUCTIONS = 8000;

    private static final Pattern JSON_LD = Pattern.compile(
            "<script[^>]*type\\s*=\\s*[\"']?application/ld\\+json[\"']?[^>]*>(.*?)</script>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CHARSET = Pattern.compile("charset\\s*=\\s*\"?([\\w.:-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#([xX][0-9a-fA-F]+|\\d+);");
    private static final Pattern LINE_BREAK_TAGS = Pattern.compile("(?i)<br\\s*/?>|</p>|</li>|</div>|</h[1-6]>");
    private static final Pattern ANY_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern NUMBERED = Pattern.compile("^\\d+[.)].*");

    private static final String[][] NAMED_ENTITIES = {
            {"&nbsp;", " "}, {"&lt;", "<"}, {"&gt;", ">"}, {"&quot;", "\""}, {"&apos;", "'"},
            {"&frac12;", "½"}, {"&frac14;", "¼"}, {"&frac34;", "¾"}, {"&deg;", "°"},
            {"&ndash;", "–"}, {"&mdash;", "—"}, {"&auml;", "ä"}, {"&ouml;", "ö"}, {"&uuml;", "ü"},
            {"&Auml;", "Ä"}, {"&Ouml;", "Ö"}, {"&Uuml;", "Ü"}, {"&szlig;", "ß"}
    };

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER) // Weiterleitungen prüfen wir selbst, Schritt für Schritt
            .build();

    // ------------------------------------------------------------
    // Öffentliche Methode
    // ------------------------------------------------------------

    public ImportedRecipe importFrom(String rawUrl) {
        URI uri = parseUrl(rawUrl);
        String html = fetch(uri);
        return parse(html, uri.toString());
    }

    // ------------------------------------------------------------
    // Adresse prüfen
    // ------------------------------------------------------------

    static URI parseUrl(String rawUrl) {
        String value = rawUrl == null ? "" : rawUrl.trim();
        if (value.isEmpty()) {
            throw new ImportException("Bitte eine Webadresse eingeben.");
        }
        if (value.length() > MAX_URL_LENGTH) {
            throw new ImportException("Die Adresse ist zu lang.");
        }
        if (!value.contains("://")) {
            value = "https://" + value; // "chefkoch.de/..." ohne https:// ist auch in Ordnung
        }

        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new ImportException("Das ist keine gültige Webadresse.");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new ImportException("Es sind nur Adressen mit http oder https erlaubt.");
        }
        if (!StringUtils.hasText(uri.getHost())) {
            throw new ImportException("Das ist keine gültige Webadresse.");
        }
        if (uri.getUserInfo() != null) {
            throw new ImportException("Adressen mit Benutzername sind nicht erlaubt.");
        }
        return uri;
    }

    // ------------------------------------------------------------
    // Seite laden
    // ------------------------------------------------------------

    private String fetch(URI start) {
        URI uri = start;

        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            assertPublicHost(uri.getHost());

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (compatible; Foodmanager/1.0)")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "de,en;q=0.8")
                    .GET()
                    .build();

            try {
                HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    int status = response.statusCode();

                    if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                        String location = response.headers().firstValue("Location").orElse(null);
                        if (location == null) {
                            throw new ImportException("Die Seite leitet ins Leere weiter.");
                        }
                        uri = parseUrl(uri.resolve(location).toString());
                        continue;
                    }

                    if (status == 403 || status == 401 || status == 429) {
                        throw new ImportException("Die Seite lässt keinen automatischen Abruf zu (Status " + status + ").");
                    }
                    if (status != 200) {
                        throw new ImportException("Die Seite konnte nicht geladen werden (Status " + status + ").");
                    }

                    String contentType = response.headers().firstValue("Content-Type").orElse(null);
                    if (contentType != null) {
                        String lower = contentType.toLowerCase(Locale.ROOT);
                        if (!lower.contains("html") && !lower.contains("xml")) {
                            throw new ImportException("Unter dieser Adresse liegt keine Webseite.");
                        }
                    }

                    InputStream stream = body;
                    String encoding = response.headers().firstValue("Content-Encoding").orElse("");
                    if (encoding.equalsIgnoreCase("gzip")) {
                        stream = new GZIPInputStream(body);
                    }

                    return new String(readLimited(stream), charsetOf(contentType));
                }
            } catch (ImportException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ImportException("Das Laden wurde unterbrochen.");
            } catch (IOException e) {
                throw new ImportException("Die Seite konnte nicht geladen werden. Ist die Adresse erreichbar?");
            }
        }

        throw new ImportException("Die Adresse leitet zu oft weiter.");
    }

    private byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BYTES) {
                break; // Der Rest wird ignoriert, die Rezeptdaten stehen fast immer weiter vorne.
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private Charset charsetOf(String contentType) {
        if (contentType != null) {
            Matcher matcher = CHARSET.matcher(contentType);
            if (matcher.find()) {
                try {
                    return Charset.forName(matcher.group(1));
                } catch (Exception e) {
                    // unbekannter Zeichensatz: UTF-8 nehmen
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private void assertPublicHost(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isInternal(address)) {
                    throw new ImportException("Diese Adresse liegt im internen Netz und ist nicht erlaubt.");
                }
            }
        } catch (UnknownHostException e) {
            throw new ImportException("Die Adresse wurde nicht gefunden. Prüfe die Schreibweise.");
        }
    }

    static boolean isInternal(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            if (first == 0) {
                return true; // 0.0.0.0/8
            }
            if (first == 100 && second >= 64 && second <= 127) {
                return true; // 100.64.0.0/10, zum Beispiel Tailscale
            }
        } else if (bytes.length == 16) {
            if ((bytes[0] & 0xFE) == 0xFC) {
                return true; // fc00::/7, interne IPv6-Adressen
            }
        }
        return false;
    }

    // ------------------------------------------------------------
    // Rezeptdaten aus dem HTML lesen
    // ------------------------------------------------------------

    static ImportedRecipe parse(String html, String sourceUrl) {
        Matcher matcher = JSON_LD.matcher(html);

        while (matcher.find()) {
            Object json;
            try {
                json = MiniJson.parse(cleanJsonBlock(matcher.group(1)));
            } catch (IllegalArgumentException e) {
                continue; // dieser Block ist kein lesbares JSON, der nächste vielleicht
            }

            Map<String, Object> recipe = findRecipe(json, 0);
            if (recipe == null) {
                continue;
            }

            ImportedRecipe imported = toImported(recipe, sourceUrl);
            if (imported != null) {
                return imported;
            }
        }

        throw new ImportException("Auf dieser Seite wurden keine Rezeptdaten gefunden. "
                + "Manche Seiten liefern sie nur mit JavaScript oder sperren das Auslesen.");
    }

    private static String cleanJsonBlock(String block) {
        return block.trim()
                .replace("<!--", "")
                .replace("-->", "")
                .replace("//<![CDATA[", "")
                .replace("/*<![CDATA[*/", "")
                .replace("<![CDATA[", "")
                .replace("]]>", "")
                .trim();
    }

    /** Sucht den "Recipe"-Knoten: direkt, in einer Liste, in "@graph" oder in "mainEntity". */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> findRecipe(Object node, int depth) {
        if (node == null || depth > 8) {
            return null;
        }
        if (node instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> found = findRecipe(item, depth + 1);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (node instanceof Map<?, ?> raw) {
            Map<String, Object> map = (Map<String, Object>) raw;
            if (isRecipeType(map.get("@type"))) {
                return map;
            }
            for (String key : new String[] {"@graph", "mainEntity", "mainEntityOfPage"}) {
                Map<String, Object> found = findRecipe(map.get(key), depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean isRecipeType(Object type) {
        if (type instanceof String s) {
            return s.equals("Recipe") || s.endsWith("/Recipe") || s.endsWith(":Recipe");
        }
        if (type instanceof List<?> list) {
            return list.stream().anyMatch(RecipeImportService::isRecipeType);
        }
        return false;
    }

    private static ImportedRecipe toImported(Map<String, Object> recipe, String sourceUrl) {
        List<String> ingredients = new ArrayList<>();
        Object rawIngredients = recipe.get("recipeIngredient");
        if (rawIngredients == null) {
            rawIngredients = recipe.get("ingredients");
        }
        collectLines(rawIngredients, ingredients, 0);

        if (ingredients.isEmpty()) {
            return null; // ohne Zutaten ist der Import nutzlos
        }

        String title = recipe.get("name") instanceof String name ? toPlainText(name).replace('\n', ' ').trim() : "";
        if (title.isEmpty()) {
            title = "Importiertes Rezept";
        }
        if (title.length() > MAX_TITLE) {
            title = title.substring(0, MAX_TITLE);
        }

        return new ImportedRecipe(
                title,
                joinLimited(ingredients, MAX_INGREDIENTS),
                joinLimited(instructionsOf(recipe), MAX_INSTRUCTIONS),
                sourceUrl);
    }

    /** Sammelt Text aus einem String, einer Liste oder einem Objekt (text oder name), eine Zeile pro Eintrag. */
    private static void collectLines(Object node, List<String> out, int depth) {
        if (node == null || depth > 6) {
            return;
        }
        if (node instanceof String s) {
            for (String line : toPlainText(s).split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    out.add(trimmed);
                }
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) {
                collectLines(item, out, depth + 1);
            }
        } else if (node instanceof Map<?, ?> map) {
            Object value = map.get("text") != null ? map.get("text") : map.get("name");
            collectLines(value, out, depth + 1);
        }
    }

    /** Zubereitung: ein String, eine Liste von Schritten oder Abschnitte mit "itemListElement". */
    private static List<String> instructionsOf(Map<String, Object> recipe) {
        List<String> steps = new ArrayList<>();
        collectSteps(recipe.get("recipeInstructions"), steps, 0);

        // Mehrere Schritte ohne eigene Nummern bekommen welche
        if (steps.size() > 1 && !NUMBERED.matcher(steps.get(0)).matches()) {
            List<String> numbered = new ArrayList<>();
            for (int i = 0; i < steps.size(); i++) {
                numbered.add((i + 1) + ". " + steps.get(i));
            }
            return numbered;
        }
        return steps;
    }

    private static void collectSteps(Object node, List<String> out, int depth) {
        if (node == null || depth > 6) {
            return;
        }
        if (node instanceof Map<?, ?> map && map.get("itemListElement") != null) {
            collectSteps(map.get("itemListElement"), out, depth + 1); // Abschnitt mit Unterschritten
            return;
        }
        collectLines(node, out, depth);
    }

    private static String joinLimited(List<String> lines, int max) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            int needed = (sb.length() == 0 ? 0 : 1) + line.length();
            if (sb.length() + needed > max) {
                if (sb.length() == 0) {
                    sb.append(line, 0, max); // eine einzige, sehr lange Zeile kürzen
                }
                break;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------
    // HTML-Reste in Text umwandeln
    // ------------------------------------------------------------

    /** Entfernt HTML-Tags, wandelt Umbrüche in Zeilen um und löst &amp;-Zeichen auf. */
    static String toPlainText(String raw) {
        if (raw == null) {
            return "";
        }
        String text = LINE_BREAK_TAGS.matcher(raw).replaceAll("\n");
        text = ANY_TAG.matcher(text).replaceAll("");
        text = decodeEntities(text).replace('\u00a0', ' ');

        StringBuilder out = new StringBuilder();
        for (String line : text.split("\\R")) {
            String cleaned = line.replaceAll("[ \\t\\x0B\\f]+", " ").trim();
            if (!cleaned.isEmpty()) {
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append(cleaned);
            }
        }
        return out.toString();
    }

    static String decodeEntities(String text) {
        Matcher matcher = NUMERIC_ENTITY.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String code = matcher.group(1);
            int codePoint;
            try {
                codePoint = (code.charAt(0) == 'x' || code.charAt(0) == 'X')
                        ? Integer.parseInt(code.substring(1), 16)
                        : Integer.parseInt(code);
            } catch (NumberFormatException e) {
                codePoint = -1;
            }
            String replacement = Character.isValidCodePoint(codePoint) ? new String(Character.toChars(codePoint)) : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        String result = sb.toString();
        for (String[] pair : NAMED_ENTITIES) {
            result = result.replace(pair[0], pair[1]);
        }
        return result.replace("&amp;", "&"); // zuletzt, damit nichts doppelt aufgelöst wird
    }
}