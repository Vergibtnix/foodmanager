package com.example.foodmanager.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import com.example.foodmanager.service.ImageStorageService;

@Controller
public class ImageController {

	private final ImageStorageService imageStorageService;

	public ImageController(ImageStorageService imageStorageService) {
		this.imageStorageService = imageStorageService;
	}

	@GetMapping("/images/{filename:.+}")
	public ResponseEntity<Resource> getImage(@PathVariable String filename) throws IOException {
		Resource resource = imageStorageService.loadAsResource(filename);
		if (resource == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bild nicht gefunden.");
		}

		MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
		Path path = Path.of(resource.getURI());
		String probeContentType = Files.probeContentType(path);
		if (probeContentType != null) {
			mediaType = MediaType.parseMediaType(probeContentType);
		}

		return ResponseEntity.ok()
			.header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
			.contentType(mediaType)
			.body(resource);
	}
}

