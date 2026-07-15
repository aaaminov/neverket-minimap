package dev.cartographer.minimap.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModConfigTest {
	@TempDir
	Path directory;

	@Test
	void newConfigUsesTopLeftAndDefaultNightDarkness() {
		ModConfig config = ModConfig.load(this.directory.resolve("missing.json"));

		assertEquals(ModConfig.Corner.TOP_LEFT, config.corner);
		assertEquals(0.5F, config.nightDarkness);
		assertTrue(config.pauseOnFullscreenMap);
		assertEquals(ModConfig.QuickMarkerIcon.TARGET_POINT, config.quickMarkerIcon);
		assertEquals(5, config.maxEdgeBannerMarkers);
	}

	@Test
	void oldConfigWithoutNightDarknessKeepsCompatibleDefault() throws IOException {
		Path path = this.directory.resolve("config.json");
		Files.writeString(path, "{\"corner\":\"BOTTOM_RIGHT\",\"mapLightingMode\":\"DAY_NIGHT\"}");

		ModConfig config = ModConfig.load(path);

		assertEquals(ModConfig.Corner.BOTTOM_RIGHT, config.corner);
		assertEquals(0.5F, config.nightDarkness);
	}

	@Test
	void nightDarknessIsClampedAndRoundedToTenPercentSteps() throws IOException {
		Path path = this.directory.resolve("config.json");
		Files.writeString(path, "{\"nightDarkness\":0.46}");

		ModConfig config = ModConfig.load(path);

		assertEquals(0.5F, config.nightDarkness);
		config.nightDarkness = 0.0F;
		config.save();
		assertEquals(0.0F, config.nightDarkness);
	}
}
