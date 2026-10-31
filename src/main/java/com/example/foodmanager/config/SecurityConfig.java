package com.example.foodmanager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.util.StringUtils;

@Configuration
public class SecurityConfig {

	@Bean
	@ConditionalOnProperty(prefix = "foodmanager.security", name = "enabled", havingValue = "false")
	SecurityFilterChain permissiveSecurityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.oauth2Login(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable);
		return http.build();
	}

	@Bean
	@ConditionalOnProperty(prefix = "foodmanager.security", name = "enabled", havingValue = "true", matchIfMissing = true)
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			ClientRegistrationRepository clientRegistrationRepository,
			@Value("${foodmanager.public-url:}") String publicUrl
	) throws Exception {
		OidcClientInitiatedLogoutSuccessHandler logoutSuccessHandler =
				new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);

		// Hinter Tailscale: feste Adresse. Sonst wie bisher aus der Anfrage.
		logoutSuccessHandler.setPostLogoutRedirectUri(
				StringUtils.hasText(publicUrl) ? publicUrl.trim().replaceAll("/+$", "") + "/" : "{baseUrl}/");

		http
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
				.csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**", "/api/barcodes/**"))
				.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(
								"/", "/error", "/css/**", "/js/**", "/images/**",
								// App-Symbole, Manifest und Offline-Dateien muessen ohne Login ladbar sein.
								// Der Browser holt sie ohne Anmeldedaten.
								"/manifest.webmanifest", "/icons/**", "/favicon.ico",
								"/sw.js", "/offline.html",
								"/h2-console/**", "/oauth2/**", "/login/oauth2/**",
								"/api/barcodes/**"
						).permitAll()
						.anyRequest().authenticated()
				)
				.exceptionHandling(exceptions ->
						exceptions.authenticationEntryPoint(
								new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/keycloak")
						)
				)
				.oauth2Login(Customizer.withDefaults())
				.logout(logout -> logout
						.logoutUrl("/logout")
						.logoutSuccessHandler(logoutSuccessHandler)
						.invalidateHttpSession(true)
						.clearAuthentication(true)
						.deleteCookies("JSESSIONID")
				)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable);

		return http.build();
	}
}