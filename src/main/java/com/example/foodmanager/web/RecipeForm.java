package com.example.foodmanager.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RecipeForm {

    @NotBlank(message = "Bitte einen Titel eingeben.")
    @Size(max = 200, message = "Der Titel darf höchstens 200 Zeichen haben.")
    private String title;

    @NotBlank(message = "Bitte mindestens eine Zutat eingeben.")
    @Size(max = 4000, message = "Die Zutatenliste ist zu lang.")
    private String ingredients;

    @Size(max = 8000, message = "Die Anleitung ist zu lang.")
    private String instructions;

    @Size(max = 500, message = "Die Quelladresse ist zu lang.")
    private String sourceUrl;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getIngredients() { return ingredients; }
    public void setIngredients(String ingredients) { this.ingredients = ingredients; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
}