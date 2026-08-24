package dev.neverket.minimap.client;

import dev.neverket.minimap.atlas.RecordingArea;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** In-memory surface cache used by fog rendering; it is deliberately not persisted. */
public final class TerrainContourCache {
	public static final byte LAND = 1;
	public static final byte WATER = 2;
	public static final int NO_SAMPLE = Integer.MIN_VALUE;
	private static final int CHUNK_PIXELS = 16 * 16;

	private final Map<String, Map<Long, ContourChunk>> dimensions = new HashMap<>();
	private long version;

	public boolean hasChunk(String dimension, int chunkX, int chunkZ) {
		Map<Long, ContourChunk> chunks = this.dimensions.get(dimension);
		return chunks != null && chunks.containsKey(chunkKey(chunkX, chunkZ));
	}

	public boolean putChunk(String dimension, int chunkX, int chunkZ, short[] heights, byte[] kinds) {
		if (heights.length != CHUNK_PIXELS || kinds.length != CHUNK_PIXELS) {
			throw new IllegalArgumentException("contour chunk must contain exactly 256 samples");
		}
		Map<Long, ContourChunk> chunks = this.dimensions.computeIfAbsent(dimension, ignored -> new HashMap<>());
		long key = chunkKey(chunkX, chunkZ);
		ContourChunk previous = chunks.get(key);
		if (previous != null && Arrays.equals(previous.heights, heights) && Arrays.equals(previous.kinds, kinds)) {
			return false;
		}
		chunks.put(key, new ContourChunk(heights.clone(), kinds.clone()));
		this.version++;
		return true;
	}

	public void retainWithin(String dimension, int centerChunkX, int centerChunkZ, int radiusChunks) {
		Map<Long, ContourChunk> chunks = this.dimensions.get(dimension);
		if (chunks == null) {
			return;
		}
		boolean changed = false;
		Iterator<Long> iterator = chunks.keySet().iterator();
		while (iterator.hasNext()) {
			long key = iterator.next();
			int chunkX = (int)(key >> 32);
			int chunkZ = (int)key;
			if (!RecordingArea.containsChunkOffset(chunkX - centerChunkX, chunkZ - centerChunkZ, radiusChunks)) {
				iterator.remove();
				changed = true;
			}
		}
		if (changed) {
			this.version++;
		}
	}

	public Sampler sampler(String dimension) {
		return new Sampler(dimension);
	}

	public long version() {
		return this.version;
	}

	public void clear() {
		if (!this.dimensions.isEmpty()) {
			this.dimensions.clear();
			this.version++;
		}
	}

	private static long chunkKey(int chunkX, int chunkZ) {
		return ((long)chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
	}

	public final class Sampler {
		private final String dimension;
		private int chunkX = Integer.MIN_VALUE;
		private int chunkZ = Integer.MIN_VALUE;
		private ContourChunk chunk;

		private Sampler(String dimension) {
			this.dimension = dimension;
		}

		/** Packs the signed surface height in the high bits and the water flag in bit zero. */
		public int sampleAt(int worldX, int worldZ) {
			int requestedChunkX = Math.floorDiv(worldX, 16);
			int requestedChunkZ = Math.floorDiv(worldZ, 16);
			if (requestedChunkX != this.chunkX || requestedChunkZ != this.chunkZ) {
				this.chunkX = requestedChunkX;
				this.chunkZ = requestedChunkZ;
				Map<Long, ContourChunk> chunks = TerrainContourCache.this.dimensions.get(this.dimension);
				this.chunk = chunks == null ? null : chunks.get(chunkKey(requestedChunkX, requestedChunkZ));
			}
			if (this.chunk == null) {
				return NO_SAMPLE;
			}
			int index = Math.floorMod(worldX, 16) + Math.floorMod(worldZ, 16) * 16;
			return this.chunk.heights[index] << 1 | (this.chunk.kinds[index] == WATER ? 1 : 0);
		}
	}

	private record ContourChunk(short[] heights, byte[] kinds) {
	}
}
