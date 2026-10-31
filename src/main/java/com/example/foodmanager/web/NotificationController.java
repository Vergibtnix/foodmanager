package com.example.foodmanager.web;

import java.io.IOException;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.foodmanager.service.ExpiryNotificationService;

/** Ablauf-Benachrichtigung von Hand auslösen, zum Beispiel zum Ausprobieren. */
@Controller
public class NotificationController {

	private final ExpiryNotificationService notificationService;

	public NotificationController(ExpiryNotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@PostMapping("/notifications/send")
	public String send(RedirectAttributes redirect) {
		String message;
		if (!notificationService.isEnabled()) {
			message = "Benachrichtigung ist aus: FOODMANAGER_NTFY_TOPIC ist nicht gesetzt.";
		} else {
			try {
				message = notificationService.sendNow()
						? "Benachrichtigung verschickt."
						: "Nichts fällig, daher nichts verschickt.";
			} catch (IOException e) {
				message = "Senden fehlgeschlagen: " + e.getMessage();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				message = "Senden abgebrochen.";
			}
		}
		redirect.addFlashAttribute("successMessage", message);
		return "redirect:/";
	}
}
