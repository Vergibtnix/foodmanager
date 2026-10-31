package com.example.foodmanager.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kleiner JSON-Leser ohne Abhängigkeiten.
 * Liefert Map, List, String, Double, Boolean oder null.
 * Er ist bewusst nachsichtig (rohe Zeilenumbrüche in Texten, Komma am Ende), weil Rezeptseiten
 * ihr JSON oft nicht ganz sauber ausliefern.
 */
final class MiniJson {

    private static final int MAX_DEPTH = 64;

    private final String text;
    private int pos;

    private MiniJson(String text) {
        this.text = text;
    }

    /** Wirft IllegalArgumentException, wenn der Text kein lesbares JSON ist. */
    static Object parse(String text) {
        MiniJson parser = new MiniJson(text);
        Object value = parser.readValue(0);
        parser.skipWhitespace();
        if (parser.pos != text.length()) {
            throw parser.error("Unerwartete Zeichen nach dem JSON");
        }
        return value;
    }

    private Object readValue(int depth) {
        if (depth > MAX_DEPTH) {
            throw error("Zu tief verschachtelt");
        }
        skipWhitespace();
        char c = peek();
        switch (c) {
            case '{':
                return readObject(depth);
            case '[':
                return readArray(depth);
            case '"':
                return readString();
            case 't':
                expectWord("true");
                return Boolean.TRUE;
            case 'f':
                expectWord("false");
                return Boolean.FALSE;
            case 'n':
                expectWord("null");
                return null;
            default:
                return readNumber();
        }
    }

    private Map<String, Object> readObject(int depth) {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        while (true) {
            skipWhitespace();
            if (peek() == '}') { // auch ein Komma vor der Klammer wird toleriert
                pos++;
                return map;
            }
            if (peek() != '"') {
                throw error("Schlüssel erwartet");
            }
            String key = readString();
            skipWhitespace();
            if (peek() != ':') {
                throw error("Doppelpunkt erwartet");
            }
            pos++;
            map.put(key, readValue(depth + 1));
            skipWhitespace();
            char next = peek();
            pos++;
            if (next == '}') {
                return map;
            }
            if (next != ',') {
                throw error("Komma oder } erwartet");
            }
        }
    }

    private List<Object> readArray(int depth) {
        List<Object> list = new ArrayList<>();
        pos++; // [
        while (true) {
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            list.add(readValue(depth + 1));
            skipWhitespace();
            char next = peek();
            pos++;
            if (next == ']') {
                return list;
            }
            if (next != ',') {
                throw error("Komma oder ] erwartet");
            }
        }
    }

    private String readString() {
        StringBuilder sb = new StringBuilder();
        pos++; // öffnendes Anführungszeichen
        while (true) {
            if (pos >= text.length()) {
                throw error("Text nicht beendet");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= text.length()) {
                throw error("Ungültige Escape-Folge");
            }
            char escaped = text.charAt(pos++);
            switch (escaped) {
                case '"':
                    sb.append('"');
                    break;
                case '\\':
                    sb.append('\\');
                    break;
                case '/':
                    sb.append('/');
                    break;
                case 'b':
                    sb.append('\b');
                    break;
                case 'f':
                    sb.append('\f');
                    break;
                case 'n':
                    sb.append('\n');
                    break;
                case 'r':
                    sb.append('\r');
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'u':
                    if (pos + 4 > text.length()) {
                        throw error("Ungültiges \\u");
                    }
                    try {
                        sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException e) {
                        throw error("Ungültiges \\u");
                    }
                    pos += 4;
                    break;
                default:
                    sb.append(escaped); // unbekannte Folge: Zeichen einfach übernehmen
            }
        }
    }

    private Object readNumber() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        if (start == pos) {
            throw error("Unerwartetes Zeichen");
        }
        try {
            return Double.valueOf(text.substring(start, pos));
        } catch (NumberFormatException e) {
            throw error("Ungültige Zahl");
        }
    }

    private void expectWord(String word) {
        if (!text.startsWith(word, pos)) {
            throw error("Unerwartetes Wort");
        }
        pos += word.length();
    }

    private char peek() {
        if (pos >= text.length()) {
            throw error("Unerwartetes Ende");
        }
        return text.charAt(pos);
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (Character.isWhitespace(c) || c == '\uFEFF') {
                pos++;
            } else {
                break;
            }
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " (Zeichen " + pos + ")");
    }
}