package com.example.foodmanager.web;

import java.net.URI;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.foodmanager.domain.Recipe;
import com.example.foodmanager.service.RecipeImportService;
import com.example.foodmanager.service.RecipeImportService.ImportException;
import com.example.foodmanager.service.RecipeImportService.ImportedRecipe;
import com.example.foodmanager.service.RecipeService;
import com.example.foodmanager.service.RecipeService.RecipeView;
import com.example.foodmanager.service.ShoppingListService;

import jakarta.validation.Valid;

@Controller
public class RecipeController {

    private final RecipeService recipeService;
    private final ShoppingListService shoppingListService;
    private final RecipeImportService recipeImportService;

    public RecipeController(
            RecipeService recipeService,
            ShoppingListService shoppingListService,
            RecipeImportService recipeImportService
    ) {
        this.recipeService = recipeService;
        this.shoppingListService = shoppingListService;
        this.recipeImportService = recipeImportService;
    }

    @GetMapping("/recipes")
    public String list(@RequestParam(defaultValue = "false") boolean onlyReady, Model model) {
        model.addAttribute("pageTitle", "Rezepte");
        model.addAttribute("recipes", recipeService.findAll(onlyReady));
        model.addAttribute("onlyReady", onlyReady);
        return "recipes/list";
    }

    @GetMapping("/recipes/new")
    public String newForm(Model model) {
        model.addAttribute("pageTitle", "Neues Rezept");
        model.addAttribute("formAction", "/recipes");
        model.addAttribute("showImport", true);
        model.addAttribute("recipeForm", new RecipeForm());
        return "recipes/form";
    }

    /** Lädt ein Rezept von einer Webadresse und zeigt es im Formular zur Kontrolle. Gespeichert wird noch nichts. */
    @PostMapping("/recipes/import")
    public String importRecipe(@RequestParam String url, Model model) {
        model.addAttribute("pageTitle", "Neues Rezept");
        model.addAttribute("formAction", "/recipes");
        model.addAttribute("showImport", true);
        model.addAttribute("importUrl", url);

        try {
            ImportedRecipe imported = recipeImportService.importFrom(url);

            RecipeForm form = new RecipeForm();
            form.setTitle(imported.title());
            form.setIngredients(imported.ingredients());
            form.setInstructions(imported.instructions());
            form.setSourceUrl(imported.sourceUrl());

            model.addAttribute("recipeForm", form);
            model.addAttribute("importInfo", "Geladen von " + hostOf(imported.sourceUrl())
                    + ". Bitte kurz prüfen und speichern.");
        } catch (ImportException e) {
            model.addAttribute("recipeForm", new RecipeForm());
            model.addAttribute("importError", e.getMessage());
        }
        return "recipes/form";
    }

    @PostMapping("/recipes")
    public String create(
            @Valid @ModelAttribute("recipeForm") RecipeForm form,
            BindingResult errors,
            Model model,
            RedirectAttributes redirect
    ) {
        if (errors.hasErrors()) {
            model.addAttribute("pageTitle", "Neues Rezept");
            model.addAttribute("formAction", "/recipes");
            model.addAttribute("showImport", true);
            return "recipes/form";
        }
        Recipe recipe = recipeService.create(form);
        redirect.addFlashAttribute("successMessage", "Rezept gespeichert.");
        return "redirect:/recipes/" + recipe.getId();
    }

    @GetMapping("/recipes/{id}")
    public String detail(@PathVariable Long id, Model model) {
        RecipeView view = recipeService.getView(id);
        model.addAttribute("pageTitle", view.recipe().getTitle());
        model.addAttribute("view", view);
        model.addAttribute("sourceHost", hostOf(view.recipe().getSourceUrl()));
        return "recipes/detail";
    }

    @GetMapping("/recipes/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Recipe recipe = recipeService.getById(id);

        RecipeForm form = new RecipeForm();
        form.setTitle(recipe.getTitle());
        form.setIngredients(recipe.getIngredients());
        form.setInstructions(recipe.getInstructions());
        form.setSourceUrl(recipe.getSourceUrl());

        model.addAttribute("pageTitle", "Rezept bearbeiten");
        model.addAttribute("formAction", "/recipes/" + id);
        model.addAttribute("recipeForm", form);
        return "recipes/form";
    }

    @PostMapping("/recipes/{id}")
    public String update(
            @PathVariable Long id,
            @Valid @ModelAttribute("recipeForm") RecipeForm form,
            BindingResult errors,
            Model model,
            RedirectAttributes redirect
    ) {
        if (errors.hasErrors()) {
            model.addAttribute("pageTitle", "Rezept bearbeiten");
            model.addAttribute("formAction", "/recipes/" + id);
            return "recipes/form";
        }
        recipeService.update(id, form);
        redirect.addFlashAttribute("successMessage", "Rezept aktualisiert.");
        return "redirect:/recipes/" + id;
    }

    @PostMapping("/recipes/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        recipeService.delete(id);
        redirect.addFlashAttribute("successMessage", "Rezept gelöscht.");
        return "redirect:/recipes";
    }

    @PostMapping("/recipes/{id}/to-shopping-list")
    public String toShoppingList(@PathVariable Long id, RedirectAttributes redirect) {
        RecipeView view = recipeService.getView(id);
        int added = shoppingListService.addAll(view.missing());
        redirect.addFlashAttribute("successMessage",
                added + " fehlende Zutat" + (added == 1 ? "" : "en") + " auf die Einkaufsliste gesetzt.");
        return "redirect:/shopping-list";
    }

    /** Rechnername ohne "www.", zum Beispiel "chefkoch.de". Null, wenn keine Adresse da ist. */
    private static String hostOf(String url) {
        if (url == null) {
            return null;
        }
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return null;
            }
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}