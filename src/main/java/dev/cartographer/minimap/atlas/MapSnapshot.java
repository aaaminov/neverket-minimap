package dev.cartographer.minimap.atlas;

import java.util.Arrays;

/** A persistent copy of the information exposed by one vanilla filled map. */
public final class MapSnapshot {
	public static final int SIDE = 128;
	public static final int PIXEL_COUNT = SIDE * SIDE;

	private final int id;
	private final String dimension;
	private final int centerX;
	private final int centerZ;
	private final byte scale;
	private final byte[] colors;

	public MapSnapshot(int id, String dimension, int centerX, int centerZ, byte scale, byte[] colors) {
		if (dimension == null || dimension.isBlank()) {
			throw new IllegalArgumentException("dimension must not be blank");
		}
		if (scale < 0 || scale > 4) {
			throw new IllegalArgumentException("vanilla map scale must be in range 0..4");
		}
		if (colors.length != PIXEL_COUNT) {
			throw new IllegalArgumentException("vanilla map must contain exactly " + PIXEL_COUNT + " pixels");
		}

		this.id = id;
		this.dimension = dimension;
		this.centerX = centerX;
		this.centerZ = centerZ;
		this.scale = scale;
		this.colors = colors.clone();
	}

	public int id() {
		return this.id;
	}

	public String dimension() {
		return this.dimension;
	}

	public int centerX() {
		return this.centerX;
	}

	public int centerZ() {
		return this.centerZ;
	}

	public byte scale() {
		return this.scale;
	}

	public int blocksPerPixel() {
		return 1 << this.scale;
	}

	public int minX() {
		return this.centerX - 64 * this.blocksPerPixel();
	}

	public int minZ() {
		return this.centerZ - 64 * this.blocksPerPixel();
	}

	public int maxXExclusive() {
		return this.minX() + SIDE * this.blocksPerPixel();
	}

	public int maxZExclusive() {
		return this.minZ() + SIDE * this.blocksPerPixel();
	}

	public byte colorAt(int worldX, int worldZ) {
		int pixelX = Math.floorDiv(worldX - this.minX(), this.blocksPerPixel());
		int pixelZ = Math.floorDiv(worldZ - this.minZ(), this.blocksPerPixel());
		if (pixelX < 0 || pixelX >= SIDE || pixelZ < 0 || pixelZ >= SIDE) {
			return 0;
		}
		return this.colors[pixelX + pixelZ * SIDE];
	}

	public byte[] colors() {
		return this.colors.clone();
	}

	public boolean sameContent(MapSnapshot other) {
		return this.id == other.id
			&& this.dimension.equals(other.dimension)
			&& this.centerX == other.centerX
			&& this.centerZ == other.centerZ
			&& this.scale == other.scale
			&& Arrays.equals(this.colors, other.colors);
	}

	public boolean samePlacement(MapSnapshot other) {
		return this.id == other.id
			&& this.dimension.equals(other.dimension)
			&& this.centerX == other.centerX
			&& this.centerZ == other.centerZ
			&& this.scale == other.scale;
	}

	public MapSnapshot mergeKnownPixels(MapSnapshot newer) {
		if (!this.samePlacement(newer)) {
			throw new IllegalArgumentException("Cannot merge maps with different placement");
		}
		byte[] merged = this.colors.clone();
		for (int index = 0; index < merged.length; index++) {
			if (newer.colors[index] != 0) {
				merged[index] = newer.colors[index];
			}
		}
		return new MapSnapshot(this.id, this.dimension, this.centerX, this.centerZ, this.scale, merged);
	}
}
