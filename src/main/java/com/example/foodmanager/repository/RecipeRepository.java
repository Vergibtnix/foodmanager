package com.example.foodmanager.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.foodmanager.domain.Recipe;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
}