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
		return packTint(opacity, brightness);
	}

	/** Uses the 1.21.1 client API, which reports current sky brightness instead of integer darkness. */
	public static int tintFromSkyBrightness(
		ModConfig config,
		float opacity,
		float skyBrightness,
		boolean hasSkyLight,
		boolean fixedTime
	) {
		float brightness = 1.0F;
		if (config.mapLightingMode == ModConfig.MapLightingMode.DAY_NIGHT && hasSkyLight && !fixedTime) {
			float darkness = 1.0F - clamp(skyBrightness, 0.0F, 1.0F);
			brightness = 1.0F - darkness * config.nightDarkness;
		}
		return packTint(opacity, brightness);
	}

	private static int packTint(float opacity, float brightness) {
		int alpha = Math.round(clamp(opacity, 0.0F, 1.0F) * 255.0F);
		int channel = Math.round(clamp(brightness, 0.0F, 1.0F) * 255.0F);
		return alpha << 24 | channel << 16 | channel << 8 | channel;
	}

	/** Multiplies two ARGB colors channel by channel, matching a textured GUI tint. */
	public static int applyTint(int argb, int tint) {
		if ((argb >>> 24) == 0 || (tint >>> 24) == 0) {
			return 0;
		}
		return multiplyChannel(argb, tint, 24) << 24
			| multiplyChannel(argb, tint, 16) << 16
			| multiplyChannel(argb, tint, 8) << 8
			| multiplyChannel(argb, tint, 0);
	}

	private static int multiplyChannel(int color, int tint, int shift) {
		return ((color >>> shift & 0xFF) * (tint >>> shift & 0xFF) + 127) / 255;
	}

	private static float clamp(float value, float minimum, float maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}
