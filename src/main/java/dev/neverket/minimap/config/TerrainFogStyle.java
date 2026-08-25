package dev.neverket.minimap.config;

/** Shared colors and blending for the in-memory partially explored terrain layer. */
public final class TerrainFogStyle {
	private TerrainFogStyle() {
	}

	public static int terrainColor(boolean water) {
		return water ? 0xFF292929 : 0xFF5A5A5A;
	}

	public static int composeTerrain(int unknownColor, boolean water, float fade) {
		return alphaOver(unknownColor, terrainColor(water), fade);
	}

	public static int composeBoundary(int unknownColor, float fade) {
		return alphaOver(unknownColor, 0xFF424242, fade);
	}

	public static int applyContour(int base, float fade) {
		return alphaOver(base, 0xFF090B0D, fade * 0.16F);
	}

	private static int alphaOver(int base, int overlay, float opacity) {
		float overlayAlpha = Math.clamp(opacity, 0.0F, 1.0F) * ((overlay >>> 24) / 255.0F);
		float baseAlpha = (base >>> 24) / 255.0F;
		float outputAlpha = overlayAlpha + baseAlpha * (1.0F - overlayAlpha);
		if (outputAlpha <= 0.0F) {
			return 0;
		}
		int alpha = Math.round(outputAlpha * 255.0F);
		int red = compositeChannel(base, overlay, 16, baseAlpha, overlayAlpha, outputAlpha);
		int green = compositeChannel(base, overlay, 8, baseAlpha, overlayAlpha, outputAlpha);
		int blue = compositeChannel(base, overlay, 0, baseAlpha, overlayAlpha, outputAlpha);
		return alpha << 24 | red << 16 | green << 8 | blue;
	}

	private static int compositeChannel(
		int base, int overlay, int shift, float baseAlpha, float overlayAlpha, float outputAlpha
	) {
		float baseChannel = base >> shift & 0xFF;
		float overlayChannel = overlay >> shift & 0xFF;
		return Math.round((overlayChannel * overlayAlpha
			+ baseChannel * baseAlpha * (1.0F - overlayAlpha)) / outputAlpha);
	}
}
