package com.example.foodmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.example.foodmanager.repository.ItemRepository;
import com.example.foodmanager.repository.PendingScanRepository;
import com.example.foodmanager.repository.ProductInfoRepository;
import com.example.foodmanager.repository.RecipeRepository;
import com.example.foodmanager.repository.ShoppingEntryRepository;
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
	private ItemRepository itemRepository;

	@Autowired
	private RecipeRepository recipeRepository;

	@Autowired
	private PendingScanRepository pendingScanRepository;

	@Autowired
	private ProductInfoRepository productInfoRepository;

	@Autowired
	private ShoppingEntryRepository shoppingEntryRepository;

	@AfterEach
	void cleanUp() {
		itemRepository.deleteAll();
		recipeRepository.deleteAll();
		pendingScanRepository.deleteAll();
		productInfoRepository.deleteAll();
		shoppingEntryRepository.deleteAll();
	}

	@Test
	void homePageLoads() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("<title>Foodmanager</title>")))
				.andExpect(content().string(containsString("Weitere Funktionen")));
	}

	@Test
	void scanPageAndEmptyBarcodeLookupWork() throws Exception {
		mockMvc.perform(get("/scan"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Barcode scannen")));

		mockMvc.perform(get("/api/barcodes/lookup").param("barcode", "   "))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Bitte gib einen Barcode ein.")));
	}

	@Test
	void canCreateAndListItem() throws Exception {
		mockMvc.perform(multipart("/items")
						.file(new MockMultipartFile("image", new byte[0]))
						.param("name", "Milch")
						.param("brand", "Beispiel Marke")
						.param("barcode", "7613035974685")
						.param("expiryDate", LocalDate.now().plusDays(5).toString())
						.param("quantity", "2")
						.param("storageLocation", "Eiskasten")
						.param("category", "Lebensmittel")          // Pflichtfeld (@NotBlank)
						.param("notes", "Testprodukt")
						.param("externalImageUrl", ""))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/items"));

		assertThat(itemRepository.count()).isEqualTo(1);

		mockMvc.perform(get("/items"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Milch")))
				.andExpect(content().string(containsString("Beispiel Marke")));
	}

	@Test
	void createItemWithoutCategoryShowsValidationError() throws Exception {
		mockMvc.perform(multipart("/items")
						.file(new MockMultipartFile("image", new byte[0]))
						.param("name", "Milch")
						.param("storageLocation", "Eiskasten")
						.param("quantity", "1"))
				.andExpect(status().isOk())   // Formular wird erneut angezeigt
				.andExpect(content().string(containsString("Bitte eine Kategorie eingeben!")));

		assertThat(itemRepository.count()).isZero();
	}

	@Test
	void unknownStorageLocationIsRejected() throws Exception {
		mockMvc.perform(multipart("/items")
						.file(new MockMultipartFile("image", new byte[0]))
						.param("name", "Schraubenzieher")
						.param("storageLocation", "Dachboden")
						.param("category", "Werkzeug")
						.param("quantity", "1"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Bitte einen Lagerort aus der Liste wählen.")));

		assertThat(itemRepository.count()).isZero();
	}

	@Test
	void locationPageFromQrCodeShowsItemsByCategory() throws Exception {
		createItem("Milch", "Lebensmittel", "Eiskasten");
		createItem("Cola", "Getränk", "eiskasten");
		createItem("Hammer", "Werkzeug", "Keller");

		mockMvc.perform(get("/locations").param("name", "Eiskasten"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Milch")))
				.andExpect(content().string(containsString("Cola")))
				.andExpect(content().string(containsString("Lebensmittel")))
				.andExpect(content().string(containsString("Getränk")))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString("Hammer"))));
	}

	@Test
	void labelPageHasQrCodeForEveryFixedLocation() throws Exception {
		var result = mockMvc.perform(get("/labels"))
				.andExpect(status().isOk());

		for (String location : com.example.foodmanager.domain.StorageLocations.ALL) {
			result.andExpect(content().string(containsString(location)));
		}

		mockMvc.perform(get("/labels/qr").param("location", "Tiefkühler Groß"))
				.andExpect(status().isOk())
				.andExpect(content().contentType("image/png"));
	}

	private void createItem(String name, String category, String location) throws Exception {
		mockMvc.perform(multipart("/items")
						.file(new MockMultipartFile("image", new byte[0]))
						.param("name", name)
						.param("category", category)
						.param("storageLocation", location)
						.param("quantity", "1"))
				.andExpect(status().is3xxRedirection());
	}

	@Test
	void quantityWithUnitAndQuickActionStepsByUnit() throws Exception {
		mockMvc.perform(multipart("/items")
						.file(new MockMultipartFile("image", new byte[0]))
						.param("name", "Mehl")
						.param("category", "Lebensmittel")
						.param("storageLocation", "Keller")
						.param("quantity", "500")
						.param("unit", "GRAMM"))
				.andExpect(status().is3xxRedirection());

		mockMvc.perform(get("/items"))
				.andExpect(content().string(containsString("500 g")));

		Long id = itemRepository.findAll().get(0).getId();
		mockMvc.perform(post("/items/{id}/adjust", id).param("delta", "-1"))
				.andExpect(status().is3xxRedirection());

		assertThat(itemRepository.findById(id).orElseThrow().getQuantity()).isEqualTo(400);
	}

	@Test
	void recipeComparesAmountsAndMarksExpiringItems() throws Exception {
		mockMvc.perform(multipart("/items")
						.file(new MockMultipartFile("image", new byte[0]))
						.param("name", "Milch")
						.param("category", "Lebensmittel")
						.param("storageLocation", "Eiskasten")
						.param("expiryDate", LocalDate.now().plusDays(2).toString())
						.param("quantity", "1000")
						.param("unit", "MILLILITER"))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(multipart("/items")
						.file(new MockMultipartFile("image", new byte[0]))
						.param("name", "Mehl")
						.param("category", "Lebensmittel")
						.param("storageLocation", "Keller")
						.param("quantity", "100")
						.param("unit", "GRAMM"))
				.andExpect(status().is3xxRedirection());

		mockMvc.perform(post("/recipes")
						.param("title", "Palatschinken")
						.param("ingredients", "½ l Milch\n250 g Mehl"))
				.andExpect(status().is3xxRedirection());
		Long recipeId = recipeRepository.findAll().get(0).getId();

		mockMvc.perform(get("/recipes/{id}", recipeId))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("läuft bald ab")))
				.andExpect(content().string(containsString("zu wenig: nur 100 g da")));

		// Dashboard (angemeldet): eine Zutat fehlt, die Milch läuft bald ab
		mockMvc.perform(get("/").with(user("demo")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Was kann ich kochen?")))
				.andExpect(content().string(containsString("Palatschinken")))
				.andExpect(content().string(containsString("Es fehlt: 250 g Mehl")))
				.andExpect(content().string(containsString("verbraucht Milch")));
	}

	@Test
	void editWithOutdatedVersionShowsConflictInsteadOfOverwriting() throws Exception {
		createItem("Milch", "Lebensmittel", "Eiskasten");
		var item = itemRepository.findAll().get(0);
		long openedVersion = item.getVersion();

		// Jemand anderes ändert die Menge, während das Formular offen ist
		mockMvc.perform(post("/items/{id}/adjust", item.getId()).param("delta", "1"))
				.andExpect(status().is3xxRedirection());

		mockMvc.perform(multipart("/items/{id}", item.getId())
						.file(new MockMultipartFile("image", new byte[0]))
						.param("version", String.valueOf(openedVersion))
						.param("name", "Vollmilch")
						.param("category", "Lebensmittel")
						.param("storageLocation", "Eiskasten")
						.param("quantity", "1"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Jemand anderes hat dieses Item gerade geändert")));

		var current = itemRepository.findById(item.getId()).orElseThrow();
		assertThat(current.getName()).isEqualTo("Milch");
		assertThat(current.getQuantity()).isEqualTo(2);
	}

	@Test
	void undoRestoresQuantityAndDeletedItem() throws Exception {
		createItem("Milch", "Lebensmittel", "Eiskasten");
		Long id = itemRepository.findAll().get(0).getId();
		var session = new org.springframework.mock.web.MockHttpSession();

		var adjust = mockMvc.perform(post("/items/{id}/adjust", id).param("delta", "-1").session(session))
				.andExpect(status().is3xxRedirection())
				.andReturn();
		String token = (String) adjust.getFlashMap().get("undoToken");
		assertThat(itemRepository.findById(id).orElseThrow().getQuantity()).isZero();

		mockMvc.perform(post("/undo").param("token", token).session(session))
				.andExpect(status().is3xxRedirection());
		assertThat(itemRepository.findById(id).orElseThrow().getQuantity()).isEqualTo(1);

		var delete = mockMvc.perform(post("/items/{id}/delete", id).session(session))
				.andExpect(status().is3xxRedirection())
				.andReturn();
		assertThat(itemRepository.count()).isZero();

		mockMvc.perform(post("/undo").param("token", (String) delete.getFlashMap().get("undoToken")).session(session))
				.andExpect(status().is3xxRedirection());
		assertThat(itemRepository.findAll()).singleElement()
				.satisfies(restored -> assertThat(restored.getName()).isEqualTo("Milch"));
	}

	@Test
	void seriesScanAddsBatchForKnownProductAndQueuesUnknown() throws Exception {
		mockMvc.perform(multipart("/items")
						.file(new MockMultipartFile("image", new byte[0]))
						.param("name", "Joghurt")
						.param("barcode", "4012345678901")
						.param("category", "Lebensmittel")
						.param("storageLocation", "Eiskasten")
						.param("expiryDate", "2026-11-10")
						.param("quantity", "1"))
				.andExpect(status().is3xxRedirection());

		// GS1-DataMatrix mit anderem Ablaufdatum: eigene Charge
		mockMvc.perform(post("/scan/series").param("code", "0104012345678901172611031042"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("\"status\":\"added\"")));

		Long id = itemRepository.findAll().get(0).getId();
		mockMvc.perform(get("/items/{id}", id))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("2 Chargen")))
				.andExpect(content().string(containsString("bis 03.11.2026")));

		// Unbekanntes Produkt (Daten aus dem eigenen Speicher, kein Internet nötig)
		productInfoRepository.save(namedProduct("9001234567890", "Topfen"));
		mockMvc.perform(post("/scan/series").param("code", "9001234567890"))
				.andExpect(content().string(containsString("\"status\":\"pending\"")));
		mockMvc.perform(post("/scan/series").param("code", "9001234567890"))
				.andExpect(content().string(containsString("Topfen (2×)")));

		mockMvc.perform(get("/scan/inbox"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Topfen")))
				.andExpect(content().string(containsString("2× gescannt")));

		// Anlegen aus der Liste entfernt den Eintrag
		Long pendingId = pendingScanRepository.findAll().get(0).getId();
		mockMvc.perform(multipart("/items")
						.file(new MockMultipartFile("image", new byte[0]))
						.param("name", "Topfen")
						.param("barcode", "9001234567890")
						.param("category", "Lebensmittel")
						.param("storageLocation", "Eiskasten")
						.param("quantity", "2")
						.param("pendingScanId", String.valueOf(pendingId)))
				.andExpect(redirectedUrl("/items"));
		assertThat(pendingScanRepository.count()).isZero();
	}

	@Test
	void shoppingListCheckSetsStateInsteadOfToggling() throws Exception {
		mockMvc.perform(post("/shopping-list/add").param("name", "Brot"));
		Long id = shoppingEntryRepository.findAll().get(0).getId();

		// Zwei Leute haken gleichzeitig ab: bleibt abgehakt
		mockMvc.perform(post("/shopping-list/{id}/toggle", id).param("done", "true"));
		mockMvc.perform(post("/shopping-list/{id}/toggle", id).param("done", "true"));
		assertThat(shoppingEntryRepository.findById(id).orElseThrow().isDone()).isTrue();
	}

	@Test
	void changedPagesRenderWithBatchesAndUndoButton() throws Exception {
		createItem("Milch", "Lebensmittel", "Eiskasten");
		Long id = itemRepository.findAll().get(0).getId();
		mockMvc.perform(post("/items/{id}/adjust", id).param("delta", "1").param("expiry", "2030-01-01"));

		mockMvc.perform(get("/items/{id}/edit", id))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("mehrere Ablaufdaten")))
				.andExpect(content().string(containsString("Datum vom Foto lesen")));

		var adjust = mockMvc.perform(post("/items/{id}/adjust", id).param("delta", "-1").param("next", "/locations"))
				.andReturn();
		mockMvc.perform(get("/locations").flashAttrs(adjust.getFlashMap()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Rückgängig")));

		mockMvc.perform(get("/shopping-list"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("data-live=\"shopping items\"")));

		mockMvc.perform(get("/scan"))
				.andExpect(content().string(containsString("Serien-Scan")));
	}

	@Test
	void categoryFilterAndDashboardLinks() throws Exception {
		createItem("Milch", "Lebensmittel", "Eiskasten");
		createItem("Hammer", "Werkzeug", "Keller");

		mockMvc.perform(get("/items").param("type", "Werkzeug"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Hammer")))
				.andExpect(content().string(org.hamcrest.Matchers.not(containsString(">Milch<"))));

		mockMvc.perform(get("/").with(user("demo")))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("/items?filter=expired")))
				.andExpect(content().string(containsString("class=\"tabbar\"")))
				.andExpect(content().string(containsString("Als App installieren")));
	}

	private static com.example.foodmanager.domain.ProductInfo namedProduct(String barcode, String name) {
		var info = new com.example.foodmanager.domain.ProductInfo(barcode);
		info.setName(name);
		return info;
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
				.andExpect(content().string(containsString("\"detected\":true")))
				.andExpect(content().string(containsString(barcode)));
	}
}