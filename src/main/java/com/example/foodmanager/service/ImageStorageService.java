package com.example.foodmanager.service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageStorageService {

	private final Path storagePath;
	private final TaskScheduler taskScheduler;
	private final Map<String, ScheduledFuture<?>> pendingDeletes = new ConcurrentHashMap<>();

	public ImageStorageService(
			@Value("${foodmanager.storage.image-dir}") String storageDir,
			TaskScheduler taskScheduler
	) throws IOException {
		this.taskScheduler = taskScheduler;
		this.storagePath = Paths.get(storageDir).toAbsolutePath().normalize();
		Files.createDirectories(this.storagePath);
	}

	public String store(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			return null;
		}

		String originalFilename = StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(), "bild"));
		String extension = "";
		int extensionIndex = originalFilename.lastIndexOf('.');
		if (extensionIndex >= 0) {
			extension = originalFilename.substring(extensionIndex);
		}

		String filename = UUID.randomUUID() + extension;
		Path target = storagePath.resolve(filename);
		Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
		return filename;
	}

	public Resource loadAsResource(String filename) {
		try {
			Path file = storagePath.resolve(filename).normalize();
			if (!file.startsWith(storagePath) || !Files.exists(file)) {
				return null;
			}
			return new UrlResource(file.toUri());
		}
		catch (MalformedURLException ex) {
			return null;
		}
	}

	/** Bild erst später löschen, damit "Rückgängig" nach dem Löschen eines Items das Bild noch findet. */
	public void deleteLater(String filename, Duration delay) {
		if (!StringUtils.hasText(filename)) {
			return;
		}
		ScheduledFuture<?> task = taskScheduler.schedule(() -> {
			pendingDeletes.remove(filename);
			deleteIfExists(filename);
		}, Instant.now().plus(delay));
		pendingDeletes.put(filename, task);
	}

	/** Geplantes Löschen abbrechen (das Item wurde wiederhergestellt). */
	public void cancelDelete(String filename) {
		if (!StringUtils.hasText(filename)) {
			return;
		}
		ScheduledFuture<?> task = pendingDeletes.remove(filename);
		if (task != null) {
			task.cancel(false);
		}
	}

	public void deleteIfExists(String filename) {
		if (!StringUtils.hasText(filename)) {
			return;
		}

		try {
			Path file = storagePath.resolve(filename).normalize();
			if (file.startsWith(storagePath)) {
				Files.deleteIfExists(file);
			}
		}
		catch (IOException ignored) {
		}
	}
}

