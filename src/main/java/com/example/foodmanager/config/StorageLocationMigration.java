package com.example.foodmanager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.example.foodmanager.service.ItemService;

/** Beim Start: Items mit alten Lagerort-Namen (z. B. "Klo", "Abstellraum") auf die festen Namen umstellen. */
@Component
public class StorageLocationMigration implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(StorageLocationMigration.class);

	private final ItemService itemService;

	public StorageLocationMigration(ItemService itemService) {
		this.itemService = itemService;
	}

	@Override
	public void run(ApplicationArguments args) {
		int changed = itemService.migrateLegacyLocations();
		if (changed > 0) {
			log.info("{} Items auf die neuen Lagerort-Namen umgestellt.", changed);
		}
	}
}
