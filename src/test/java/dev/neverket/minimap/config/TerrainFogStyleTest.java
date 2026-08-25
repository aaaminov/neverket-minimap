package dev.neverket.minimap.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TerrainFogStyleTest {
	@Test
	void darkTerrainKeepsWaterDarkerThanLand() {
		assertEquals(0xFF292929, TerrainFogStyle.terrainColor(ModConfig.UnknownTerrain.DARK, true));
		assertEquals(0xFF5A5A5A, TerrainFogStyle.terrainColor(ModConfig.UnknownTerrain.DARK, false));
		assertEquals(0xFF424242, TerrainFogStyle.boundaryColor(ModConfig.UnknownTerrain.DARK));
	}

	@Test
	void transparentTerrainUsesTheSameRgbPalette() {
		assertEquals(0x88292929, TerrainFogStyle.terrainColor(ModConfig.UnknownTerrain.TRANSPARENT, true));
		assertEquals(0x885A5A5A, TerrainFogStyle.terrainColor(ModConfig.UnknownTerrain.TRANSPARENT, false));
		assertEquals(0x88424242, TerrainFogStyle.boundaryColor(ModConfig.UnknownTerrain.TRANSPARENT));
	}

	@Test
	void contourDarkeningIsStable() {
		assertEquals(0xFF4D4D4E, TerrainFogStyle.applyContour(0xFF5A5A5A, 1.0F));
	}
}
