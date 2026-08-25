package dev.neverket.minimap.config;

/** Version-independent decoder for the four vanilla map brightness levels. */
public final class PackedMapColor {
	private static final int[] BRIGHTNESS = {180, 220, 255, 135};

	private PackedMapColor() {
	}

	public static int argb(int baseRgb, int brightnessId) {
		int modifier = BRIGHTNESS[Math.floorMod(brightnessId, BRIGHTNESS.length)];
		int red = (baseRgb >> 16 & 0xFF) * modifier / 255;
		int green = (baseRgb >> 8 & 0xFF) * modifier / 255;
		int blue = (baseRgb & 0xFF) * modifier / 255;
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}
}
