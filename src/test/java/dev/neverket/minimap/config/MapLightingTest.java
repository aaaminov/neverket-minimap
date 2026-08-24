package dev.neverket.minimap.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MapLightingTest {
	@Test
	void dayNightLightingUsesTheSkyDarknessForTheRgbTint() {
		ModConfig config = new ModConfig();
		config.mapLightingMode = ModConfig.MapLightingMode.DAY_NIGHT;
		config.nightDarkness = 0.5F;

		assertEquals(0xFFFFFFFF, MapLighting.tint(config, 1.0F, 0, true, false));
		assertEquals(0xFF808080, MapLighting.tint(config, 1.0F, 15, true, false));
	}

	@Test
	void alwaysBrightAndUnlitDimensionsIgnoreTheSkyDarkness() {
		ModConfig config = new ModConfig();
		config.nightDarkness = 1.0F;

		config.mapLightingMode = ModConfig.MapLightingMode.ALWAYS_BRIGHT;
		assertEquals(0xFFFFFFFF, MapLighting.tint(config, 1.0F, 15, true, false));

		config.mapLightingMode = ModConfig.MapLightingMode.DAY_NIGHT;
		assertEquals(0xFFFFFFFF, MapLighting.tint(config, 1.0F, 15, false, false));
		assertEquals(0xFFFFFFFF, MapLighting.tint(config, 1.0F, 15, true, true));
	}

	@Test
	void opacityOnlyChangesTheAlphaChannel() {
		ModConfig config = new ModConfig();
		assertEquals(0xE6FFFFFF, MapLighting.tint(config, 0.9F, 0, true, false));
	}
}
