package com.example.foodmanager.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.foodmanager.domain.Item;
import com.example.foodmanager.service.RecipeService.RecipeView;

/**
 * Schickt einmal am Tag eine Übersicht der abgelaufenen und bald ablaufenden Produkte per ntfy aufs Handy.
 * Auf dem Handy die ntfy-App installieren und dasselbe Topic abonnieren.
 * Ohne gesetztes Topic ist die Benachrichtigung ausgeschaltet.
 */
@Service
public class ExpiryNotificationService {

	private static final Logger log = LoggerFactory.getLogger(ExpiryNotificationService.class);
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.");

	/** Was verschickt wird. Leer, wenn nichts fällig ist. */
	public record Message(String title, String body, boolean urgent) {
	}

	private final ItemService itemService;
	private final RecipeService recipeService;
	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	private final String server;
	private final String topic;
	private final String token;
	private final String publicUrl;

	public ExpiryNotificationService(
			ItemService itemService,
			RecipeService recipeService,
			@Value("${foodmanager.notify.ntfy-server:https://ntfy.sh}") String server,
			@Value("${foodmanager.notify.ntfy-topic:}") String topic,
			@Value("${foodmanager.notify.ntfy-token:}") String token,
			@Value("${foodmanager.public-url:}") String publicUrl
	) {
		this.itemService = itemService;
		this.recipeService = recipeService;
		this.server = server.trim().replaceAll("/+$", "");
		this.topic = topic.trim();
		this.token = token.trim();
		this.publicUrl = publicUrl.trim().replaceAll("/+$", "");
	}

	public boolean isEnabled() {
		return StringUtils.hasText(topic);
	}

	/** Täglich zur eingestellten Uhrzeit (Standard 18:30). Schickt nur etwas, wenn auch etwas fällig ist. */
	@Scheduled(cron = "${foodmanager.notify.cron:0 30 18 * * *}", zone = "${foodmanager.notify.zone:Europe/Vienna}")
	public void sendDaily() {
		if (!isEnabled()) {
			return;
		}
		try {
			if (!sendNow()) {
				log.info("Ablauf-Benachrichtigung: nichts fällig, nichts gesendet.");
			}
		} catch (IOException e) {
			log.warn("Ablauf-Benachrichtigung fehlgeschlagen: {}", e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** Schickt die Übersicht sofort. Gibt false zurück, wenn nichts fällig war. */
	public boolean sendNow() throws IOException, InterruptedException {
		if (!isEnabled()) {
			throw new IllegalStateException("Kein ntfy-Topic eingestellt.");
		}
		Message message = buildMessage();
		if (message == null) {
			return false;
		}
		publish(message);
		return true;
	}

	/** Text der Benachrichtigung. Null, wenn nichts abgelaufen ist oder bald abläuft. */
	public Message buildMessage() {
		List<Item> due = itemService.findDueSoon(Integer.MAX_VALUE).stream()
				.filter(item -> ItemService.quantityOf(item) > 0)
				.toList();
		if (due.isEmpty()) {
			return null;
		}

		List<Item> expired = due.stream().filter(Item::isExpired).toList();
		List<Item> soon = due.stream().filter(item -> !item.isExpired()).toList();

		StringBuilder body = new StringBuilder();
		if (!expired.isEmpty()) {
			body.append("Abgelaufen:\n");
			expired.forEach(item -> body.append(line(item)));
		}
		if (!soon.isEmpty()) {
			if (!body.isEmpty()) {
				body.append('\n');
			}
			body.append("Läuft bald ab:\n");
			soon.forEach(item -> body.append(line(item)));
		}

		// Tipp aus den Rezepten: das erste, das bald Ablaufendes verbraucht
		recipeService.suggest(1).stream()
				.filter(view -> view.expiringCount() > 0)
				.findFirst()
				.ifPresent(view -> body.append("\nKoch-Tipp: ").append(tip(view)));

		String title = expired.isEmpty()
				? soon.size() + (soon.size() == 1 ? " Produkt läuft" : " Produkte laufen") + " bald ab"
				: expired.size() + " abgelaufen, " + soon.size() + " bald fällig";
		return new Message(title, body.toString().trim(), !expired.isEmpty());
	}

	private static String line(Item item) {
		String location = item.getStorageLocation() != null ? ", " + item.getStorageLocation() : "";
		return "• " + item.getName() + " (" + item.getQuantityLabel() + location + ") – " + item.getExpiryDate().format(DATE) + "\n";
	}

	private static String tip(RecipeView view) {
		return view.recipe().getTitle() + " (verbraucht " + String.join(", ", view.expiringItems()) + ")";
	}

	private void publish(Message message) throws IOException, InterruptedException {
		// JSON an die Wurzel-Adresse: so funktionieren Umlaute auch im Titel (in HTTP-Headern nicht)
		StringBuilder json = new StringBuilder("{")
				.append("\"topic\":").append(quote(topic))
				.append(",\"title\":").append(quote(message.title()))
				.append(",\"message\":").append(quote(message.body()))
				.append(",\"tags\":[").append(quote(message.urgent() ? "warning" : "hourglass")).append(']')
				.append(",\"priority\":").append(message.urgent() ? 4 : 3);
		if (StringUtils.hasText(publicUrl)) {
			json.append(",\"click\":").append(quote(publicUrl + "/"));
		}
		json.append('}');

		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(server + "/"))
				.timeout(Duration.ofSeconds(15))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json.toString()));
		if (StringUtils.hasText(token)) {
			request.header("Authorization", "Bearer " + token);
		}

		HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() >= 300) {
			throw new IOException("ntfy antwortet mit HTTP " + response.statusCode() + ": " + response.body());
		}
	}

	/** Text als JSON-String mit Anführungszeichen. */
	static String quote(String value) {
		StringBuilder out = new StringBuilder("\"");
		for (char c : value.toCharArray()) {
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> {
					if (c < 0x20) {
						out.append(String.format("\\u%04x", (int) c));
					} else {
						out.append(c);
					}
				}
			}
		}
		return out.append('"').toString();
	}
}
