package com.example.foodmanager.config;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.util.StringUtils;

/**
 * Richtet den Login über Keycloak von Hand ein, statt ihn beim Start per Abruf zu entdecken.
 *
 * Zwei Adressen werden gebraucht:
 *  - öffentlich: die Adresse, die der Browser sieht (zum Beispiel https://foodmanager.tailXXXX.ts.net:8443/realms/foodmanager)
 *  - intern: die Adresse, über die die App Keycloak im Docker-Netz erreicht (http://foodmanager-keycloak:8080/realms/foodmanager)
 *
 * Vorteil: Die App startet auch, wenn Keycloak noch nicht bereit ist. Es gibt keine Abstürze mehr beim Hochfahren des Pi.
 * Lokal reicht eine einzige Adresse, dann sind beide gleich.
 */
@Configuration
public class KeycloakClientConfig {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(
            @Value("${foodmanager.keycloak.public-url}") String publicUrl,
            @Value("${foodmanager.keycloak.internal-url}") String internalUrl,
            @Value("${foodmanager.keycloak.client-id}") String clientId,
            @Value("${foodmanager.keycloak.client-secret}") String clientSecret,
            @Value("${foodmanager.public-url:}") String appUrl
    ) {
        String browserBase = stripTrailingSlash(publicUrl);
        String serverBase = stripTrailingSlash(internalUrl);

        // Mit fester App-Adresse (hinter Tailscale) ist die Rückkehr-Adresse eindeutig,
        // sonst wird sie wie bisher aus der Anfrage gebildet.
        String redirectUri = StringUtils.hasText(appUrl)
                ? stripTrailingSlash(appUrl) + "/login/oauth2/code/keycloak"
                : "{baseUrl}/login/oauth2/code/{registrationId}";

        ClientRegistration keycloak = ClientRegistration.withRegistrationId("keycloak")
                .clientName("Keycloak")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope("openid", "profile", "email")
                // Der Browser geht zur öffentlichen Adresse ...
                .authorizationUri(browserBase + "/protocol/openid-connect/auth")
                // ... die App selbst spricht intern mit Keycloak.
                .tokenUri(serverBase + "/protocol/openid-connect/token")
                .jwkSetUri(serverBase + "/protocol/openid-connect/certs")
                // Der Aussteller im Token ist immer die öffentliche Adresse.
                .issuerUri(browserBase)
                .userNameAttributeName("preferred_username")
                .providerConfigurationMetadata(Map.of(
                        "end_session_endpoint", browserBase + "/protocol/openid-connect/logout"))
                .build();

        return new InMemoryClientRegistrationRepository(keycloak);
    }

    private static String stripTrailingSlash(String value) {
        return value == null ? "" : value.trim().replaceAll("/+$", "");
    }
}