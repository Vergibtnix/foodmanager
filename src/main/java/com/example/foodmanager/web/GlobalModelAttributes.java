package com.example.foodmanager.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Stellt Werte bereit, die jede Seite (z. B. die Navigationsleiste) braucht.
 */
@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAttributes {

    private final String issuerUri;

    public GlobalModelAttributes(
            @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri:}") String issuerUri
    ) {
        this.issuerUri = issuerUri;
    }

    /** Link zur Keycloak-Kontoverwaltung, z. B. http://192.168.0.134:8081/realms/foodmanager/account */
    @ModelAttribute("accountUrl")
    public String accountUrl() {
        if (issuerUri == null || issuerUri.isBlank()) {
            return null;
        }
        return issuerUri.replaceAll("/+$", "") + "/account";
    }
}