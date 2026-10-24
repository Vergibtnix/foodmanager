package com.example.foodmanager.service;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.foodmanager.web.BarcodeDecodeResult;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.LuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;

@Service
public class BarcodeImageDecodeService {

	// ------------------------------------------------------------
	// PUBLIC API
	// ------------------------------------------------------------

	public BarcodeDecodeResult decode(MultipartFile image) {
		try {
			BufferedImage buffered = ImageIO.read(image.getInputStream());
			return decodeBuffered(buffered);
		} catch (Exception e) {
			return new BarcodeDecodeResult(false, "", "Bild konnte nicht verarbeitet werden");
		}
	}

	public BarcodeDecodeResult decodeZoom(MultipartFile image) {
		try {
			BufferedImage original = ImageIO.read(image.getInputStream());

			int w = original.getWidth();
			int h = original.getHeight();

			int zoomW = (int) (w * 0.6);
			int zoomH = (int) (h * 0.6);

			int x = (w - zoomW) / 2;
			int y = (h - zoomH) / 2;

			BufferedImage zoomed = original.getSubimage(x, y, zoomW, zoomH);

			return decodeBuffered(zoomed);
		} catch (Exception e) {
			return new BarcodeDecodeResult(false, "", "Zoom-Analyse fehlgeschlagen");
		}
	}

	public BarcodeDecodeResult decodeContrast(MultipartFile image) {
		try {
			BufferedImage original = ImageIO.read(image.getInputStream());

			BufferedImage contrasted = new BufferedImage(
					original.getWidth(),
					original.getHeight(),
					BufferedImage.TYPE_BYTE_BINARY
			);

			Graphics2D g = contrasted.createGraphics();
			g.drawImage(original, 0, 0, null);
			g.dispose();

			return decodeBuffered(contrasted);
		} catch (Exception e) {
			return new BarcodeDecodeResult(false, "", "Kontrast-Analyse fehlgeschlagen");
		}
	}

	// ------------------------------------------------------------
	// CORE ZXING DECODER (ALLE FORMATE)
	// ------------------------------------------------------------
	private BarcodeDecodeResult decodeBuffered(BufferedImage image) {
		try {
			LuminanceSource source = new BufferedImageLuminanceSource(image);
			BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

			Map<DecodeHintType, Object> hints = new HashMap<>();
			hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(
					BarcodeFormat.AZTEC,
					BarcodeFormat.CODABAR,
					BarcodeFormat.CODE_39,
					BarcodeFormat.CODE_93,
					BarcodeFormat.CODE_128,
					BarcodeFormat.DATA_MATRIX,
					BarcodeFormat.EAN_8,
					BarcodeFormat.EAN_13,
					BarcodeFormat.ITF,
					BarcodeFormat.MAXICODE,
					BarcodeFormat.PDF_417,
					BarcodeFormat.QR_CODE,
					BarcodeFormat.RSS_14,
					BarcodeFormat.RSS_EXPANDED,
					BarcodeFormat.UPC_A,
					BarcodeFormat.UPC_E,
					BarcodeFormat.UPC_EAN_EXTENSION
			));

			Result result = new MultiFormatReader().decode(bitmap, hints);

			return new BarcodeDecodeResult(true, result.getText(), "Barcode erkannt");

		} catch (Exception e) {
			return new BarcodeDecodeResult(false, "", "Kein Barcode erkannt");
		}
	}
}
