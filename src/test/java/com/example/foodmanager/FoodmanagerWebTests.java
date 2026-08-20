package com.example.foodmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.example.foodmanager.repository.FoodItemRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FoodmanagerWebTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private FoodItemRepository foodItemRepository;

	@AfterEach
	void cleanUp() {
		foodItemRepository.deleteAll();
	}

	@Test
	void homePageLoads() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Foodmanager")));
	}

	@Test
	void scanPageAndEmptyBarcodeLookupWork() throws Exception {
		mockMvc.perform(get("/scan"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Barcode scannen")));

		mockMvc.perform(get("/api/barcodes/lookup").param("barcode", "   "))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Bitte gib einen Barcode ein.")));
	}

	@Test
	void canCreateAndListFoodItem() throws Exception {
		mockMvc.perform(multipart("/items")
				.file(new MockMultipartFile("image", new byte[0]))
				.param("name", "Milch")
				.param("brand", "Beispiel Marke")
				.param("barcode", "7613035974685")
				.param("expiryDate", LocalDate.now().plusDays(5).toString())
				.param("quantity", "2")
				.param("storageLocation", "Kühlschrank")
				.param("notes", "Testprodukt")
				.param("externalImageUrl", ""))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/items"));

		assertThat(foodItemRepository.count()).isEqualTo(1);

		mockMvc.perform(get("/items"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Milch")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("Beispiel Marke")));
	}

	@Test
	void canDecodeBarcodeFromUploadedCameraFrame() throws Exception {
		String barcode = "WATERDROP-123";
		BitMatrix matrix = new MultiFormatWriter().encode(barcode, BarcodeFormat.CODE_128, 420, 180);
		BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		ImageIO.write(image, "png", outputStream);

		mockMvc.perform(multipart("/api/barcodes/decode")
				.file(new MockMultipartFile("image", "frame.png", "image/png", outputStream.toByteArray())))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("\"detected\":true")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString(barcode)));
	}
}

