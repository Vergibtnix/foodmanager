package com.example.foodmanager.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.foodmanager.domain.Item;
import com.example.foodmanager.service.ItemService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/** QR-Etiketten für Lagerorte und Kisten. Scannen öffnet den Inhalt in der App. */
@Controller
public class LabelController {

    private static final int QR_SIZE = 360;

    private final ItemService itemService;
    private final String publicUrl;

    public LabelController(ItemService itemService, @Value("${foodmanager.public-url:}") String publicUrl) {
        this.itemService = itemService;
        this.publicUrl = publicUrl;
    }

    /** Ein Etikett je Lagerort. Mit ?only=Keller wird nur dieses eine angezeigt und gedruckt. */
    @GetMapping("/labels")
    public String labels(@RequestParam(required = false) String only, Model model) {
        Map<String, List<Item>> groups = itemService.groupByLocation();

        Map<String, Integer> labels = new LinkedHashMap<>();
        for (String location : itemService.findLocations()) {
            if (!StringUtils.hasText(only) || location.equalsIgnoreCase(only.trim())) {
                labels.put(location, groups.getOrDefault(location, List.of()).size());
            }
        }

        model.addAttribute("pageTitle", "QR-Etiketten");
        model.addAttribute("only", StringUtils.hasText(only) ? only.trim() : null);
        model.addAttribute("labels", labels);
        return "labels";
    }

    @GetMapping("/labels/qr")
    public ResponseEntity<byte[]> qr(@RequestParam String location) {
        String text = location == null ? "" : location.trim();
        if (text.isEmpty() || text.length() > 100) {
            return ResponseEntity.badRequest().build();
        }

        // Feste Adresse (PUBLIC_URL), damit gedruckte Codes auch am Handy funktionieren.
        // Ohne sie die Adresse, die der Browser gerade benutzt (auf dem Pi also http://192.168.0.134:8085/...).
        UriComponentsBuilder base = StringUtils.hasText(publicUrl)
                ? UriComponentsBuilder.fromUriString(publicUrl.trim().replaceAll("/+$", ""))
                : ServletUriComponentsBuilder.fromCurrentContextPath();

        String url = base
                .path("/locations")
                .queryParam("name", text)
                .build()
                .encode()
                .toUriString();

        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            BitMatrix matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                    .contentType(MediaType.IMAGE_PNG)
                    .body(out.toByteArray());
        } catch (WriterException | IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "QR-Code konnte nicht erzeugt werden.", e);
        }
    }
}
