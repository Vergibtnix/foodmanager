package com.example.foodmanager.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.foodmanager.service.BarcodeImageDecodeService;
import com.example.foodmanager.service.BarcodeLookupService;

@RestController
@RequestMapping("/api/barcodes")
public class BarcodeApiController {

	private final BarcodeLookupService barcodeLookupService;
	private final BarcodeImageDecodeService barcodeImageDecodeService;

	public BarcodeApiController(BarcodeLookupService barcodeLookupService,
			BarcodeImageDecodeService barcodeImageDecodeService) {
		this.barcodeLookupService = barcodeLookupService;
		this.barcodeImageDecodeService = barcodeImageDecodeService;
	}

	@GetMapping("/lookup")
	public BarcodeLookupResult lookup(@RequestParam String barcode) {
		return barcodeLookupService.lookup(barcode);
	}

	@PostMapping(path = "/decode", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public BarcodeDecodeResult decode(@RequestPart("image") MultipartFile image) {
		return barcodeImageDecodeService.decode(image);
	}
}

