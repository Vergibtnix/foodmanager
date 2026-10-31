package com.example.foodmanager.service;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Schickt offenen Seiten (Einkaufsliste, Einräum-Liste) eine Nachricht, sobald jemand etwas ändert.
 * Die Seite lädt dann ihren Inhalt neu. Technik: Server-Sent Events, eine offene Verbindung pro Seite.
 */
@Service
public class LiveUpdateService {

	private static final long TIMEOUT_MS = 30 * 60 * 1000L;

	private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

	public SseEmitter subscribe() {
		SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
		emitters.add(emitter);
		emitter.onCompletion(() -> emitters.remove(emitter));
		emitter.onTimeout(() -> emitters.remove(emitter));
		emitter.onError(e -> emitters.remove(emitter));
		return emitter;
	}

	/** Erst nach dem Speichern senden, sonst lädt die andere Seite noch den alten Stand. */
	@TransactionalEventListener(fallbackExecution = true)
	public void onChange(DataChangedEvent event) {
		send(SseEmitter.event().name("changed").data(event.topic()));
	}

	/** Hält die Verbindung offen, damit Proxys (Tailscale) sie nicht als untätig schließen. */
	@Scheduled(fixedRate = 25_000)
	public void heartbeat() {
		send(SseEmitter.event().comment("ping"));
	}

	private void send(SseEmitter.SseEventBuilder event) {
		for (SseEmitter emitter : emitters) {
			try {
				emitter.send(event);
			} catch (IOException | IllegalStateException e) {
				emitters.remove(emitter);
			}
		}
	}
}
