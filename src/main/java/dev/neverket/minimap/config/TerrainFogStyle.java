package dev.neverket.minimap.config;

/** Shared colors and blending for the in-memory partially explored terrain layer. */
public final class TerrainFogStyle {
	private TerrainFogStyle() {
	}

	public static int terrainColor(ModConfig.UnknownTerrain unknown, boolean water) {
		if (unknown == ModConfig.UnknownTerrain.DARK) {
			return water ? 0xFF292929 : 0xFF5A5A5A;
		}
		return water ? 0x88292929 : 0x885A5A5A;
	}

	public static int boundaryColor(ModConfig.UnknownTerrain unknown) {
		return unknown == ModConfig.UnknownTerrain.DARK ? 0xFF424242 : 0x88424242;
	}

	public static int applyContour(int base, float fade) {
		return alphaOver(base, 0xFF090B0D, fade * 0.16F);
	}

	private static int alphaOver(int base, int overlay, float opacity) {
		float amount = Math.clamp(opacity, 0.0F, 1.0F) * ((overlay >>> 24) / 255.0F);
		return lerpColor(base, overlay | 0xFF000000, amount);
	}

	private static int lerpColor(int from, int to, float amount) {
		float clamped = Math.clamp(amount, 0.0F, 1.0F);
		int alpha = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * clamped);
		int red = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * clamped);
		int green = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * clamped);
		int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * clamped);
		return alpha << 24 | red << 16 | green << 8 | blue;
	}
}
