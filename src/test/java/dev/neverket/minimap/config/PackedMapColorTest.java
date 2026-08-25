package dev.neverket.minimap.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PackedMapColorTest {
	@Test
	void decodesAllVanillaBrightnessLevelsInArgb() {
		int water = 0x4040FF;
		assertEquals(0xFF2D2DB4, PackedMapColor.argb(water, 0));
		assertEquals(0xFF3737DC, PackedMapColor.argb(water, 1));
		assertEquals(0xFF4040FF, PackedMapColor.argb(water, 2));
		assertEquals(0xFF212187, PackedMapColor.argb(water, 3));
	}
}
