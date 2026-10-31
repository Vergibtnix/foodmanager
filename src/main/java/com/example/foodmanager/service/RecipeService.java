package com.example.foodmanager.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.example.foodmanager.domain.Item;
import com.example.foodmanager.domain.QuantityUnit;
import com.example.foodmanager.domain.Recipe;
import com.example.foodmanager.repository.RecipeRepository;
import com.example.foodmanager.web.RecipeForm;

@Service
@Transactional
public class RecipeService {

    /**
     * Eine Zutat und ob sie im Inventar vorhanden ist (Menge größer 0 und, wenn vergleichbar, genug davon).
     * note: Zusatzinfo wie "nur 200 g da"; expiring: das passende Item läuft bald ab oder ist abgelaufen.
     */
    public record IngredientStatus(String text, boolean available, String matchedItem, String note, boolean expiring) {
    }

    public record RecipeView(Recipe recipe, List<IngredientStatus> ingredients, int have, int total) {

        public boolean complete() {
            return total > 0 && have == total;
        }

        /** Namen der Vorratsprodukte, die bald ablaufen und von diesem Rezept verbraucht werden. */
        public List<String> expiringItems() {
            return ingredients.stream()
                    .filter(i -> i.available() && i.expiring())
                    .map(IngredientStatus::matchedItem)
                    .distinct()
                    .toList();
        }

        public int expiringCount() {
            return expiringItems().size();
        }

        public int percent() {
            return total == 0 ? 0 : Math.round(100f * have / total);
        }

        public int missingCount() {
            return total - have;
        }

        /** Texte der fehlenden Zutaten, zum Beispiel für die Einkaufsliste. */
        public List<String> missing() {
            return ingredients.stream().filter(i -> !i.available()).map(IngredientStatus::text).toList();
        }
    }

    private record Stock(String normalized, String displayName, QuantityUnit unit, int quantity, boolean expiring) {
    }

    /** Menge aus einer Rezeptzeile, schon in die Basiseinheit umgerechnet: "1,5 kg" = 1500 g. */
    record Amount(double value, QuantityUnit unit) {
    }

    // Mengen und Einheiten am Zeilenanfang: "200 g", "2 EL", "1,5 l", "½ Bund"
    private static final Pattern AMOUNT = Pattern.compile(
            "^[\\d.,/½¼¾⅓]+\\s*(?:(?:g|kg|mg|ml|l|cl|dl|el|tl|stk|stück|stücke|prise|prisen|dose|dosen|packung|packungen|pkg|bund|zehe|zehen|tasse|tassen|becher|scheibe|scheiben|glas|gläser)\\b\\.?)?\\s*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    // Zahl und das Wort danach: "200 g", "1,5 l", "2 Eier"
    private static final Pattern NUMBER_AND_WORD = Pattern.compile(
            "^([\\d.,/½¼¾⅓]+)\\s*([\\p{L}]+\\.?)?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    // Einheiten, die sich nicht mit g, ml oder Stück vergleichen lassen
    private static final Set<String> OTHER_UNITS = Set.of(
            "el", "tl", "prise", "prisen", "dose", "dosen", "packung", "packungen", "pkg", "bund",
            "zehe", "zehen", "tasse", "tassen", "becher", "scheibe", "scheiben", "glas", "gläser");
    private static final Pattern PARENS = Pattern.compile("\\(.*?\\)");
    private static final Pattern NOISE = Pattern.compile(
            "^(?:etwas|ca\\.?|circa|ein|eine|einen|einem|einige|frisch|frische|frischer|frisches)\\s+",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final RecipeRepository recipeRepository;
    private final ItemService itemService;

    public RecipeService(RecipeRepository recipeRepository, ItemService itemService) {
        this.recipeRepository = recipeRepository;
        this.itemService = itemService;
    }

    // ------------------------------------------------------------
    // Lesen
    // ------------------------------------------------------------

    /** Kochbare zuerst, dann die, die mehr bald ablaufende Produkte verbrauchen, dann nach Anteil vorhandener Zutaten. */
    @Transactional(readOnly = true)
    public List<RecipeView> findAll(boolean onlyReady) {
        List<Stock> stock = loadStock();

        return recipeRepository.findAll().stream()
                .map(recipe -> buildView(recipe, stock))
                .filter(view -> !onlyReady || view.complete())
                .sorted(Comparator
                        .comparing(RecipeView::complete).reversed()
                        .thenComparing(Comparator.comparingInt(RecipeView::expiringCount).reversed())
                        .thenComparing(Comparator.comparingInt(RecipeView::percent).reversed())
                        .thenComparing(view -> view.recipe().getTitle(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * "Was kann ich kochen?": Rezepte, bei denen höchstens eine Zutat fehlt.
     * Bevorzugt werden Rezepte, die bald ablaufende Produkte verbrauchen.
     */
    @Transactional(readOnly = true)
    public List<RecipeView> suggest(int limit) {
        List<Stock> stock = loadStock();

        return recipeRepository.findAll().stream()
                .map(recipe -> buildView(recipe, stock))
                .filter(view -> view.total() > 0 && view.missingCount() <= 1)
                .sorted(Comparator
                        .comparingInt(RecipeView::expiringCount).reversed()
                        .thenComparing(Comparator.comparing(RecipeView::complete).reversed())
                        .thenComparing(view -> view.recipe().getTitle(), String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecipeView getView(Long id) {
        return buildView(getById(id), loadStock());
    }

    @Transactional(readOnly = true)
    public Recipe getById(Long id) {
        return recipeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rezept nicht gefunden."));
    }

    // ------------------------------------------------------------
    // Schreiben
    // ------------------------------------------------------------

    public Recipe create(RecipeForm form) {
        Recipe recipe = new Recipe();
        apply(recipe, form);
        return recipeRepository.save(recipe);
    }

    public Recipe update(Long id, RecipeForm form) {
        Recipe recipe = getById(id);
        apply(recipe, form);
        return recipeRepository.save(recipe);
    }

    public void delete(Long id) {
        recipeRepository.deleteById(id);
    }

    private void apply(Recipe recipe, RecipeForm form) {
        recipe.setTitle(form.getTitle().trim());
        recipe.setIngredients(form.getIngredients().trim());
        recipe.setInstructions(StringUtils.hasText(form.getInstructions()) ? form.getInstructions().trim() : null);
        recipe.setSourceUrl(cleanSourceUrl(form.getSourceUrl()));
    }

    /** Nur echte Webadressen speichern, damit der Link später gefahrlos angezeigt werden kann. */
    private String cleanSourceUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String url = value.trim();
        boolean web = url.regionMatches(true, 0, "http://", 0, 7) || url.regionMatches(true, 0, "https://", 0, 8);
        return web && url.length() <= 500 ? url : null;
    }

    // ------------------------------------------------------------
    // Abgleich mit dem Inventar
    // ------------------------------------------------------------

    /** Vorrat mit Menge größer 0, das früheste Ablaufdatum zuerst. So wird bei mehreren Treffern das dringendste Item gezeigt. */
    private List<Stock> loadStock() {
        LocalDate dueUntil = LocalDate.now().plusDays(itemService.getExpiringSoonDays());

        List<Stock> stock = new ArrayList<>();
        itemService.findAll("", "", "").stream()
                .filter(item -> ItemService.quantityOf(item) > 0)
                .sorted(Comparator.comparing(Item::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(item -> stock.add(new Stock(
                        normalize(item.getName()),
                        item.getName(),
                        item.getUnit(),
                        ItemService.quantityOf(item),
                        item.getExpiryDate() != null && !item.getExpiryDate().isAfter(dueUntil))));
        return stock;
    }

    private RecipeView buildView(Recipe recipe, List<Stock> stock) {
        List<String> lines = Arrays.stream(recipe.getIngredients().split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();

        List<IngredientStatus> statuses = new ArrayList<>();
        int have = 0;

        for (String line : lines) {
            IngredientStatus status = check(line, stock);
            if (status.available()) {
                have++;
            }
            statuses.add(status);
        }

        return new RecipeView(recipe, statuses, have, statuses.size());
    }

    /**
     * Sucht passende Items. Steht in der Zeile eine Menge in g, ml oder Stück und haben die Items dieselbe Einheit,
     * wird zusätzlich geprüft, ob genug da ist. Sonst reicht der Name.
     */
    private IngredientStatus check(String line, List<Stock> stock) {
        String key = normalize(line);
        List<Stock> matches = stock.stream()
                .filter(s -> matches(key, s.normalized()))
                .toList();

        if (matches.isEmpty()) {
            return new IngredientStatus(line, false, null, null, false);
        }

        Stock first = matches.get(0);
        Amount needed = parseAmount(line).orElse(null);
        if (needed != null) {
            List<Stock> sameUnit = matches.stream().filter(s -> s.unit() == needed.unit()).toList();
            if (!sameUnit.isEmpty()) {
                int total = sameUnit.stream().mapToInt(Stock::quantity).sum();
                if (total < needed.value()) {
                    return new IngredientStatus(line, false, sameUnit.get(0).displayName(),
                            "nur " + needed.unit().format(total) + " da", false);
                }
                first = sameUnit.get(0);
            }
        }
        return new IngredientStatus(line, true, first.displayName(), null, first.expiring());
    }

    /** Menge am Zeilenanfang in g, ml oder Stück. Leer bei "2 EL Öl", "etwas Salz" oder unlesbaren Zahlen. */
    static Optional<Amount> parseAmount(String line) {
        Matcher m = NUMBER_AND_WORD.matcher(line == null ? "" : line.trim());
        if (!m.find()) {
            return Optional.empty();
        }
        Double number = parseNumber(m.group(1));
        if (number == null || number <= 0) {
            return Optional.empty();
        }

        String word = m.group(2) == null ? "" : m.group(2).toLowerCase(Locale.GERMAN).replace(".", "");
        if (OTHER_UNITS.contains(word)) {
            return Optional.empty();
        }
        // Unbekanntes Wort: das ist schon die Zutat ("2 Eier"), also Stück
        QuantityUnit.Parsed unit = QuantityUnit.parse(word).orElse(new QuantityUnit.Parsed(QuantityUnit.STUECK, 1));
        return Optional.of(new Amount(number * unit.factor(), unit.unit()));
    }

    /** "2", "1,5", "1.5", "1/2", "½". Null, wenn es keine Zahl ist. */
    static Double parseNumber(String raw) {
        String s = raw.replace("½", ".5").replace("¼", ".25").replace("¾", ".75").replace("⅓", ".33").replace(',', '.');
        try {
            int slash = s.indexOf('/');
            if (slash > 0) {
                return Double.parseDouble(s.substring(0, slash)) / Double.parseDouble(s.substring(slash + 1));
            }
            // "1½" wurde zu "1.5", "½" zu ".5"
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Kleinbuchstaben, ohne Klammern, Mengen und Einheiten, nur der Teil vor dem ersten Komma. */
    static String normalize(String raw) {
        String s = raw == null ? "" : raw.toLowerCase(Locale.GERMAN);
        s = PARENS.matcher(s).replaceAll(" ").trim();
        s = AMOUNT.matcher(s).replaceFirst("");
        s = NOISE.matcher(s).replaceFirst("");
        int comma = s.indexOf(',');
        if (comma >= 0) {
            s = s.substring(0, comma);
        }
        return s.trim();
    }

    /** Gleich, oder der kürzere Begriff (mindestens 3 Zeichen) steckt im längeren: "Milch" passt zu "Vollmilch". */
    static boolean matches(String ingredient, String itemName) {
        if (ingredient.isBlank() || itemName.isBlank()) {
            return false;
        }
        if (ingredient.equals(itemName)) {
            return true;
        }
        boolean ingredientIsShorter = ingredient.length() <= itemName.length();
        String shorter = ingredientIsShorter ? ingredient : itemName;
        String longer = ingredientIsShorter ? itemName : ingredient;
        return shorter.length() >= 3 && longer.contains(shorter);
    }
}