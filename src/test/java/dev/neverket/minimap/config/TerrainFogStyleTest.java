package dev.neverket.minimap.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TerrainFogStyleTest {
	@Test
	void terrainKeepsWaterDarkerThanLand() {
		assertEquals(0xFF292929, TerrainFogStyle.terrainColor(true));
		assertEquals(0xFF5A5A5A, TerrainFogStyle.terrainColor(false));
	}

	@Test
	void terrainEdgeFadesAlphaWithoutDarkRgbHalo() {
		assertEquals(0x405A5A5A, TerrainFogStyle.composeTerrain(0, false, 0.25F));
		assertEquals(0x40424242, TerrainFogStyle.composeBoundary(0, 0.25F));
	}

	@Test
	void contourDarkeningIsStable() {
		assertEquals(0xFF4D4D4E, TerrainFogStyle.applyContour(0xFF5A5A5A, 1.0F));
	}
}
