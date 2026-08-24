package dev.neverket.minimap.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
	void newConfigUsesTheDefaultPresetWithoutChangingMapMode() {
		ModConfig config = ModConfig.load(this.directory.resolve("missing.json"));

		assertEquals(ModConfig.Corner.TOP_LEFT, config.corner);
		assertEquals(112, config.size);
		assertEquals(1.0F, config.opacity);
		assertEquals(8, config.zoom);
		assertFalse(config.showCardinalDirections);
		assertTrue(config.showTerrainContours);
		assertEquals(16, config.terrainContourRangeChunks);
		assertEquals(ModConfig.RecordingMode.MAPS, config.recordingMode);
		assertEquals(ModConfig.MapDetailMode.LOADED_TERRAIN_DETAIL, config.mapDetailMode);
		assertEquals(1.0F, config.nightDarkness);
		assertTrue(config.pauseOnFullscreenMap);
		assertEquals(ModConfig.QuickMarkerIcon.YELLOW_X, config.quickMarkerIcon);
		assertTrue(config.showMinimapBorder);
		assertEquals(ModConfig.MinimapBorderColor.BLACK, config.minimapBorderColor);
		assertEquals(6, config.maxEdgeBannerMarkers);
		assertEquals(ModConfig.BiomeHighlightColor.CYAN, config.biomeHighlightColor);
		assertEquals(0.35F, config.biomeHighlightOpacity);
		assertTrue(config.showRecordingAreaOnBiomeHighlight);
	}

	@Test
	void oldConfigWithoutNightDarknessUsesTheCurrentDefault() throws IOException {
		Path path = this.directory.resolve("config.json");
		Files.writeString(path, "{\"corner\":\"BOTTOM_RIGHT\",\"mapLightingMode\":\"DAY_NIGHT\"}");

		ModConfig config = ModConfig.load(path);

		assertEquals(ModConfig.Corner.BOTTOM_RIGHT, config.corner);
		assertEquals(1.0F, config.nightDarkness);
	}

	@Test
	void rangedSettingsAreClampedAndRoundedToTheirUiSteps() throws IOException {
		Path path = this.directory.resolve("config.json");
		Files.writeString(path, "{\"nightDarkness\":0.46,\"biomeHighlightOpacity\":0.33,\"maxEdgeBannerMarkers\":99}");

		ModConfig config = ModConfig.load(path);

		assertEquals(0.45F, config.nightDarkness);
		assertEquals(0.35F, config.biomeHighlightOpacity);
		assertEquals(32, config.maxEdgeBannerMarkers);
		config.nightDarkness = 0.0F;
		config.save();
		assertEquals(0.0F, config.nightDarkness);
	}
}
