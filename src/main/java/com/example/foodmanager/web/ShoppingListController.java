package com.example.foodmanager.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.foodmanager.domain.Item;
import com.example.foodmanager.service.ShoppingListService;

@Controller
public class ShoppingListController {

    private final ShoppingListService shoppingListService;

    public ShoppingListController(ShoppingListService shoppingListService) {
        this.shoppingListService = shoppingListService;
    }

    @GetMapping("/shopping-list")
    public String list(Model model) {
        model.addAttribute("pageTitle", "Einkaufsliste");
        model.addAttribute("suggestions", shoppingListService.findSuggestions());
        model.addAttribute("entries", shoppingListService.findEntries());
        return "shopping-list";
    }

    @PostMapping("/shopping-list/add")
    public String add(@RequestParam String name, RedirectAttributes redirect) {
        if (!shoppingListService.add(name)) {
            redirect.addFlashAttribute("infoMessage", "Das steht schon auf der Liste oder ist leer.");
        }
        return "redirect:/shopping-list";
    }

    /** done: gewünschter Zustand (true = abgehakt). */
    @PostMapping("/shopping-list/{id}/toggle")
    public String toggle(@PathVariable Long id, @RequestParam boolean done) {
        shoppingListService.setDone(id, done);
        return "redirect:/shopping-list";
    }

    @PostMapping("/shopping-list/{id}/delete")
    public String delete(@PathVariable Long id) {
        shoppingListService.delete(id);
        return "redirect:/shopping-list";
    }

    @PostMapping("/shopping-list/clear-done")
    public String clearDone() {
        shoppingListService.clearDone();
        return "redirect:/shopping-list";
    }

    @PostMapping("/shopping-list/bought/{itemId}")
    public String bought(@PathVariable Long itemId, RedirectAttributes redirect) {
        Item item = shoppingListService.markBought(itemId);
        redirect.addFlashAttribute("successMessage", item.getName() + ": Bestand aufgefüllt.");
        return "redirect:/shopping-list";
    }
}
