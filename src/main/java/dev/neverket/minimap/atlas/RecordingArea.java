package dev.neverket.minimap.atlas;

/** Shared geometry for the circular client-side terrain recording area. */
public final class RecordingArea {
	private static final int CHUNK_SIDE = 16;

	private RecordingArea() {
	}

	public static boolean containsChunkOffset(int offsetX, int offsetZ, int radiusChunks) {
		if (radiusChunks < 0) {
			throw new IllegalArgumentException("radius must not be negative");
		}
		long distanceSquared = (long)offsetX * offsetX + (long)offsetZ * offsetZ;
		return distanceSquared <= (long)radiusChunks * radiusChunks;
	}

	public static int radiusBlocks(int radiusChunks) {
		if (radiusChunks < 0) {
			throw new IllegalArgumentException("radius must not be negative");
		}
		return radiusChunks * CHUNK_SIDE;
	}
}
