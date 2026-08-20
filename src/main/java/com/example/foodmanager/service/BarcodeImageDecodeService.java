package com.example.foodmanager.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.example.foodmanager.web.BarcodeDecodeResult;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

@Service
public class BarcodeImageDecodeService {

	private static final List<BarcodeFormat> SUPPORTED_FORMATS = List.of(
		BarcodeFormat.EAN_13,
		BarcodeFormat.EAN_8,
		BarcodeFormat.UPC_A,
		BarcodeFormat.UPC_E,
		BarcodeFormat.CODE_128,
		BarcodeFormat.CODE_39,
		BarcodeFormat.QR_CODE
	);

	public BarcodeDecodeResult decode(MultipartFile image) {
		if (image == null || image.isEmpty()) {
			return BarcodeDecodeResult.notDetected("Kein Kamerabild empfangen.");
		}

		try {
			BufferedImage bufferedImage = ImageIO.read(image.getInputStream());
			if (bufferedImage == null) {
				return BarcodeDecodeResult.notDetected("Kamerabild konnte nicht gelesen werden.");
			}

			String barcode = decodeBufferedImage(bufferedImage);
			if (StringUtils.hasText(barcode)) {
				return new BarcodeDecodeResult(true, barcode.trim(), "Barcode im Kamerabild erkannt.");
			}

			return BarcodeDecodeResult.notDetected(
				"Im Kamerabild wurde kein Barcode erkannt. Halte den Code näher, ruhig, scharf und ohne Spiegelungen vor die Kamera."
			);
		}
		catch (IOException ex) {
			return BarcodeDecodeResult.notDetected("Kamerabild konnte nicht ausgewertet werden.");
		}
	}

	private String decodeBufferedImage(BufferedImage originalImage) {
		for (BufferedImage imageVariant : buildImageVariants(originalImage)) {
			String barcode = tryDecode(imageVariant);
			if (StringUtils.hasText(barcode)) {
				return barcode;
			}
		}
		return "";
	}

	private List<BufferedImage> buildImageVariants(BufferedImage originalImage) {
		List<BufferedImage> variants = new ArrayList<>();
		variants.add(originalImage);
		addCenteredCropVariant(variants, originalImage, 0.85d);
		addCenteredCropVariant(variants, originalImage, 0.70d);
		return variants;
	}

	private void addCenteredCropVariant(List<BufferedImage> variants, BufferedImage image, double ratio) {
		int cropWidth = Math.max(1, (int) Math.round(image.getWidth() * ratio));
		int cropHeight = Math.max(1, (int) Math.round(image.getHeight() * ratio));
		if (cropWidth >= image.getWidth() || cropHeight >= image.getHeight()) {
			return;
		}

		int x = (image.getWidth() - cropWidth) / 2;
		int y = (image.getHeight() - cropHeight) / 2;
		variants.add(image.getSubimage(x, y, cropWidth, cropHeight));
	}

	private String tryDecode(BufferedImage image) {
		MultiFormatReader reader = new MultiFormatReader();
		try {
			BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
			Result result = reader.decode(bitmap, createHints());
			return result.getText();
		}
		catch (NotFoundException ex) {
			return "";
		}
		finally {
			reader.reset();
		}
	}

	private Map<DecodeHintType, Object> createHints() {
		Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
		hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
		hints.put(DecodeHintType.POSSIBLE_FORMATS, SUPPORTED_FORMATS);
		return hints;
	}
}
