package dev.cartographer.minimap.atlas;

/** A persistent 128x128 block-resolution tile sampled from chunks the client has loaded. */
public final class TerrainTile {
	public static final int SIDE = 128;
	public static final int PIXEL_COUNT = SIDE * SIDE;

	private final String dimension;
	private final int tileX;
	private final int tileZ;
	private final byte[] colors;

	public TerrainTile(String dimension, int tileX, int tileZ, byte[] colors) {
		if (dimension == null || dimension.isBlank()) {
			throw new IllegalArgumentException("dimension must not be blank");
		}
		if (colors.length != PIXEL_COUNT) {
			throw new IllegalArgumentException("terrain tile must contain exactly " + PIXEL_COUNT + " pixels");
		}
		this.dimension = dimension;
		this.tileX = tileX;
		this.tileZ = tileZ;
		this.colors = colors.clone();
	}

	public String dimension() {
		return this.dimension;
	}

	public int tileX() {
		return this.tileX;
	}

	public int tileZ() {
		return this.tileZ;
	}

	public byte[] colors() {
		return this.colors.clone();
	}
}
