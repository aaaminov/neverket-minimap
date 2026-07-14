package dev.cartographer.minimap.atlas;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

/** Combines map snapshots in world coordinates without obtaining any world data itself. */
public final class MapAtlas {
	private static final int INDEX_BUCKET_SIZE = 128;

	private final Map<String, Map<Integer, MapSnapshot>> snapshots = new LinkedHashMap<>();
	private final Map<String, Map<Long, List<MapSnapshot>>> spatialIndex = new HashMap<>();
	private long version;

	public boolean put(MapSnapshot snapshot) {
		Map<Integer, MapSnapshot> layer = this.snapshots.computeIfAbsent(snapshot.dimension(), ignored -> new LinkedHashMap<>());
		MapSnapshot previous = layer.get(snapshot.id());
		if (previous != null && previous.sameContent(snapshot)) {
			return false;
		}

		layer.put(snapshot.id(), snapshot);
		this.rebuildIndex(snapshot.dimension());
		this.version++;
		return true;
	}

	public int colorAt(String dimension, int worldX, int worldZ) {
		Map<Long, List<MapSnapshot>> layerIndex = this.spatialIndex.get(dimension);
		if (layerIndex == null) {
			return 0;
		}

		List<MapSnapshot> candidates = layerIndex.get(bucketKey(Math.floorDiv(worldX, INDEX_BUCKET_SIZE), Math.floorDiv(worldZ, INDEX_BUCKET_SIZE)));
		if (candidates == null) {
			return 0;
		}

		for (MapSnapshot candidate : candidates) {
			int color = candidate.colorAt(worldX, worldZ) & 0xFF;
			if (color != 0) {
				return color;
			}
		}
		return 0;
	}

	public Set<String> dimensions() {
		return Set.copyOf(this.snapshots.keySet());
	}

	public Collection<MapSnapshot> snapshots() {
		List<MapSnapshot> result = new ArrayList<>();
		this.snapshots.values().forEach(layer -> result.addAll(layer.values()));
		return List.copyOf(result);
	}

	public int size() {
		return this.snapshots.values().stream().mapToInt(Map::size).sum();
	}

	public long version() {
		return this.version;
	}

	public Optional<Bounds> bounds(String dimension) {
		Map<Integer, MapSnapshot> layer = this.snapshots.get(dimension);
		if (layer == null || layer.isEmpty()) {
			return Optional.empty();
		}
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (MapSnapshot snapshot : layer.values()) {
			minX = Math.min(minX, snapshot.minX());
			minZ = Math.min(minZ, snapshot.minZ());
			maxX = Math.max(maxX, snapshot.maxXExclusive());
			maxZ = Math.max(maxZ, snapshot.maxZExclusive());
		}
		return Optional.of(new Bounds(minX, minZ, maxX, maxZ));
	}

	private void rebuildIndex(String dimension) {
		Map<Long, List<MapSnapshot>> index = new HashMap<>();
		Map<Integer, MapSnapshot> layer = this.snapshots.get(dimension);
		if (layer != null) {
			for (MapSnapshot snapshot : layer.values()) {
				int minBucketX = Math.floorDiv(snapshot.minX(), INDEX_BUCKET_SIZE);
				int minBucketZ = Math.floorDiv(snapshot.minZ(), INDEX_BUCKET_SIZE);
				int maxBucketX = Math.floorDiv(snapshot.maxXExclusive() - 1, INDEX_BUCKET_SIZE);
				int maxBucketZ = Math.floorDiv(snapshot.maxZExclusive() - 1, INDEX_BUCKET_SIZE);
				for (int bucketZ = minBucketZ; bucketZ <= maxBucketZ; bucketZ++) {
					for (int bucketX = minBucketX; bucketX <= maxBucketX; bucketX++) {
						index.computeIfAbsent(bucketKey(bucketX, bucketZ), ignored -> new ArrayList<>()).add(snapshot);
					}
				}
			}
			index.values().forEach(candidates -> candidates.sort(Comparator.comparingInt(MapSnapshot::scale)));
		}
		this.spatialIndex.put(dimension, index);
	}

	private static long bucketKey(int x, int z) {
		return ((long)x << 32) ^ (z & 0xFFFFFFFFL);
	}

	public record Bounds(int minX, int minZ, int maxXExclusive, int maxZExclusive) {
		public int width() {
			return this.maxXExclusive - this.minX;
		}

		public int height() {
			return this.maxZExclusive - this.minZ;
		}
	}
}
