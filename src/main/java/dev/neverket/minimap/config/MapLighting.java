package dev.neverket.minimap.config;

/** Pure map-lighting calculations shared by version-specific renderers. */
public final class MapLighting {
	private MapLighting() {
	}

	public static int tint(ModConfig config, float opacity, int skyDarken, boolean hasSkyLight, boolean fixedTime) {
		float brightness = 1.0F;
		if (config.mapLightingMode == ModConfig.MapLightingMode.DAY_NIGHT && hasSkyLight && !fixedTime) {
			float darkness = clamp(skyDarken / 15.0F, 0.0F, 1.0F);
			brightness = 1.0F - darkness * config.nightDarkness;
		}
		int alpha = Math.round(clamp(opacity, 0.0F, 1.0F) * 255.0F);
		int channel = Math.round(clamp(brightness, 0.0F, 1.0F) * 255.0F);
		return alpha << 24 | channel << 16 | channel << 8 | channel;
	}

	private static float clamp(float value, float minimum, float maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
