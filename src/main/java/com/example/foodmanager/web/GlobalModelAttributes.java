package com.example.foodmanager.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.example.foodmanager.domain.StorageLocations;

/**
 * Stellt Werte bereit, die jede Seite (z. B. die Navigationsleiste) braucht.
 */
@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAttributes {

    private final String keycloakUrl;

    public GlobalModelAttributes(
            @Value("${foodmanager.keycloak.public-url:${spring.security.oauth2.client.provider.keycloak.issuer-uri:}}") String keycloakUrl
    ) {
        this.keycloakUrl = keycloakUrl;
    }

    /** Link zur Keycloak-Kontoverwaltung, z. B. https://foodmanager.tailXXXX.ts.net:8443/realms/foodmanager/account */
    @ModelAttribute("accountUrl")
    public String accountUrl() {
        if (keycloakUrl == null || keycloakUrl.isBlank()) {
            return null;
        }
        return keycloakUrl.replaceAll("/+$", "") + "/account";
    }

    /** Die festen Lagerorte für Formular und Suche. */
    @ModelAttribute("storageLocations")
    public List<String> storageLocations() {
        return StorageLocations.ALL;
    }
}