package dev.cartographer.minimap.atlas;

import dev.cartographer.minimap.marker.BannerMarker;
import dev.cartographer.minimap.marker.QuickMarker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

/** Combines vanilla map snapshots and optional block-resolution terrain tiles in world coordinates. */
public final class MapAtlas {
	private static final int INDEX_BUCKET_SIZE = 128;

	private final Map<String, Map<Integer, MapSnapshot>> snapshots = new LinkedHashMap<>();
	private final Map<String, Map<Long, List<MapSnapshot>>> spatialIndex = new HashMap<>();
	private final Map<String, Map<Long, byte[]>> terrainTiles = new LinkedHashMap<>();
	private final Map<String, Set<Long>> terrainChunks = new LinkedHashMap<>();
	private final Map<String, Map<Long, String[]>> biomeChunks = new LinkedHashMap<>();
	private final Map<Integer, List<BannerMarker>> bannerMarkersByMap = new LinkedHashMap<>();
	private QuickMarker quickMarker;
	private long version;

	public boolean put(MapSnapshot snapshot) {
		Map<Integer, MapSnapshot> layer = this.snapshots.computeIfAbsent(snapshot.dimension(), ignored -> new LinkedHashMap<>());
		MapSnapshot previous = layer.get(snapshot.id());
		if (previous != null && previous.samePlacement(snapshot)) {
			snapshot = previous.mergeKnownPixels(snapshot);
		}
		if (previous != null && previous.sameContent(snapshot)) {
			return false;
		}

		layer.put(snapshot.id(), snapshot);
		this.rebuildIndex(snapshot.dimension());
		this.version++;
		return true;
	}

	public int colorAt(String dimension, int worldX, int worldZ, boolean includeDetailedTerrain) {
		if (includeDetailedTerrain) {
			Map<Long, byte[]> terrainLayer = this.terrainTiles.get(dimension);
			if (terrainLayer != null) {
				int tileX = Math.floorDiv(worldX, TerrainTile.SIDE);
				int tileZ = Math.floorDiv(worldZ, TerrainTile.SIDE);
				byte[] colors = terrainLayer.get(bucketKey(tileX, tileZ));
				if (colors != null) {
					int index = Math.floorMod(worldX, TerrainTile.SIDE) + Math.floorMod(worldZ, TerrainTile.SIDE) * TerrainTile.SIDE;
					int color = colors[index] & 0xFF;
					if (color != 0) {
						return color;
					}
				}
			}
		}
		return this.mapColorAt(dimension, worldX, worldZ);
	}

	public int colorAt(String dimension, int worldX, int worldZ) {
		return this.colorAt(dimension, worldX, worldZ, true);
	}

	public ColorSampler sampler(String dimension, boolean includeDetailedTerrain) {
		return this.sampler(dimension, includeDetailedTerrain, false);
	}

	public ColorSampler sampler(String dimension, boolean includeDetailedTerrain, boolean detailedTerrainRequiresMapCoverage) {
		return new ColorSampler(dimension, includeDetailedTerrain, detailedTerrainRequiresMapCoverage);
	}

	private int mapColorAt(String dimension, int worldX, int worldZ) {
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

	public boolean putTerrainChunk(String dimension, int chunkX, int chunkZ, byte[] colors) {
		if (colors.length != 16 * 16) {
			throw new IllegalArgumentException("terrain chunk must contain exactly 256 pixels");
		}
		int tileX = Math.floorDiv(chunkX, 8);
		int tileZ = Math.floorDiv(chunkZ, 8);
		int startX = Math.floorMod(chunkX, 8) * 16;
		int startZ = Math.floorMod(chunkZ, 8) * 16;
		Map<Long, byte[]> layer = this.terrainTiles.computeIfAbsent(dimension, ignored -> new LinkedHashMap<>());
		byte[] tile = layer.computeIfAbsent(bucketKey(tileX, tileZ), ignored -> new byte[TerrainTile.PIXEL_COUNT]);
		boolean changed = false;
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				byte color = colors[x + z * 16];
				int tileIndex = startX + x + (startZ + z) * TerrainTile.SIDE;
				if (color != 0 && tile[tileIndex] != color) {
					tile[tileIndex] = color;
					changed = true;
				}
			}
		}
		boolean newlyRecorded = this.terrainChunks.computeIfAbsent(dimension, ignored -> new java.util.HashSet<>())
			.add(bucketKey(chunkX, chunkZ));
		if (changed || newlyRecorded) {
			this.version++;
		}
		return changed;
	}

	public boolean hasTerrainChunk(String dimension, int chunkX, int chunkZ) {
		Set<Long> layer = this.terrainChunks.get(dimension);
		return layer != null && layer.contains(bucketKey(chunkX, chunkZ));
	}

	public boolean putBiomeChunk(String dimension, int chunkX, int chunkZ, String[] biomes) {
		if (biomes.length != 16) {
			throw new IllegalArgumentException("biome chunk must contain exactly 16 quart samples");
		}
		String[] replacement = Arrays.copyOf(biomes, biomes.length);
		Map<Long, String[]> layer = this.biomeChunks.computeIfAbsent(dimension, ignored -> new LinkedHashMap<>());
		long key = bucketKey(chunkX, chunkZ);
		String[] previous = layer.get(key);
		if (Arrays.equals(previous, replacement)) {
			return false;
		}
		layer.put(key, replacement);
		this.version++;
		return true;
	}

	public boolean hasBiomeChunk(String dimension, int chunkX, int chunkZ) {
		Map<Long, String[]> layer = this.biomeChunks.get(dimension);
		return layer != null && layer.containsKey(bucketKey(chunkX, chunkZ));
	}

	/** Returns the recorded biome id, or {@code null} when that quart has not been recorded. */
	public String biomeAt(String dimension, int worldX, int worldZ) {
		Map<Long, String[]> layer = this.biomeChunks.get(dimension);
		if (layer == null) {
			return null;
		}
		String[] biomes = layer.get(bucketKey(Math.floorDiv(worldX, 16), Math.floorDiv(worldZ, 16)));
		if (biomes == null) {
			return null;
		}
		int quartX = Math.floorMod(worldX, 16) / 4;
		int quartZ = Math.floorMod(worldZ, 16) / 4;
		String biome = biomes[quartX + quartZ * 4];
		return biome == null || biome.isBlank() ? null : biome;
	}

	public BiomeSampler biomeSampler(String dimension) {
		return new BiomeSampler(dimension);
	}

	public Collection<BiomeChunk> biomeChunks() {
		List<BiomeChunk> result = new ArrayList<>();
		this.biomeChunks.forEach((dimension, chunks) -> chunks.forEach((key, biomes) -> result.add(new BiomeChunk(
			dimension, (int)(key >> 32), (int)(long)key, Arrays.copyOf(biomes, biomes.length)
		))));
		return List.copyOf(result);
	}

	public void putBiomeChunk(BiomeChunk chunk) {
		this.putBiomeChunk(chunk.dimension(), chunk.chunkX(), chunk.chunkZ(), chunk.biomes());
	}

	public boolean hasMapCoverage(String dimension, int chunkX, int chunkZ) {
		int startX = chunkX * 16;
		int startZ = chunkZ * 16;
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				if (this.mapColorAt(dimension, startX + x, startZ + z) != 0) {
					return true;
				}
			}
		}
		return false;
	}

	public Collection<TerrainTile> terrainTiles() {
		List<TerrainTile> result = new ArrayList<>();
		this.terrainTiles.forEach((dimension, layer) -> layer.forEach((key, colors) -> result.add(new TerrainTile(
			dimension,
			(int)(key >> 32),
			(int)(long)key,
			colors
		))));
		return List.copyOf(result);
	}

	public void putTerrainTile(TerrainTile tile) {
		this.terrainTiles.computeIfAbsent(tile.dimension(), ignored -> new LinkedHashMap<>())
			.put(bucketKey(tile.tileX(), tile.tileZ()), tile.colors());
		this.version++;
	}

	public Collection<TerrainChunk> terrainChunks() {
		List<TerrainChunk> result = new ArrayList<>();
		this.terrainChunks.forEach((dimension, chunks) -> chunks.forEach(key -> result.add(new TerrainChunk(
			dimension,
			(int)(key >> 32),
			(int)(long)key
		))));
		return List.copyOf(result);
	}

	public void putTerrainChunkReference(TerrainChunk chunk) {
		if (this.terrainChunks.computeIfAbsent(chunk.dimension(), ignored -> new java.util.HashSet<>())
			.add(bucketKey(chunk.chunkX(), chunk.chunkZ()))) {
			this.version++;
		}
	}

	public Set<String> dimensions() {
		Set<String> result = new java.util.HashSet<>(this.snapshots.keySet());
		result.addAll(this.terrainTiles.keySet());
		result.addAll(this.biomeChunks.keySet());
		this.bannerMarkersByMap.values().forEach(markers -> markers.forEach(marker -> result.add(marker.dimension())));
		if (this.quickMarker != null) {
			result.add(this.quickMarker.dimension());
		}
		return Set.copyOf(result);
	}

	public Optional<QuickMarker> quickMarker() {
		return Optional.ofNullable(this.quickMarker);
	}

	public boolean putQuickMarker(QuickMarker marker) {
		if (marker.equals(this.quickMarker)) {
			return false;
		}
		this.quickMarker = marker;
		this.version++;
		return true;
	}

	public boolean removeQuickMarker() {
		if (this.quickMarker == null) {
			return false;
		}
		this.quickMarker = null;
		this.version++;
		return true;
	}

	public boolean replaceBannerMarkers(int sourceMapId, Collection<BannerMarker> markers) {
		List<BannerMarker> previous = this.bannerMarkersByMap.getOrDefault(sourceMapId, List.of());
		Map<Long, BannerMarker> previousByCoordinate = new LinkedHashMap<>();
		previous.forEach(marker -> previousByCoordinate.put(bucketKey(marker.x(), marker.z()), marker));
		Map<Long, BannerMarker> unique = new LinkedHashMap<>();
		for (BannerMarker marker : markers) {
			if (marker.sourceMapId() != sourceMapId) {
				throw new IllegalArgumentException("banner marker source map does not match");
			}
			BannerMarker old = previousByCoordinate.get(bucketKey(marker.x(), marker.z()));
			unique.put(bucketKey(marker.x(), marker.z()), old != null && old.sameContent(marker) ? old : marker);
		}
		List<BannerMarker> replacement = List.copyOf(unique.values());
		if (previous.equals(replacement)) {
			return false;
		}
		if (replacement.isEmpty()) {
			this.bannerMarkersByMap.remove(sourceMapId);
		} else {
			this.bannerMarkersByMap.put(sourceMapId, replacement);
		}
		this.version++;
		return true;
	}

	public boolean putBannerMarker(BannerMarker marker) {
		List<BannerMarker> current = new ArrayList<>(this.bannerMarkersByMap.getOrDefault(marker.sourceMapId(), List.of()));
		current.add(marker);
		return this.replaceBannerMarkers(marker.sourceMapId(), current);
	}

	public Collection<BannerMarker> bannerMarkers(String dimension) {
		Map<Long, BannerMarker> unique = new LinkedHashMap<>();
		this.bannerMarkersByMap.values().forEach(markers -> markers.stream()
			.filter(marker -> marker.dimension().equals(dimension))
			.forEach(marker -> unique.put(bucketKey(marker.x(), marker.z()), marker)));
		return List.copyOf(unique.values());
	}

	public Collection<BannerMarker> bannerMarkers() {
		List<BannerMarker> result = new ArrayList<>();
		this.bannerMarkersByMap.values().forEach(result::addAll);
		return List.copyOf(result);
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

	public Optional<MapSnapshot> findById(int id) {
		return this.snapshots.values().stream().map(layer -> layer.get(id)).filter(java.util.Objects::nonNull).findFirst();
	}

	public Optional<Bounds> bounds(String dimension) {
		Map<Integer, MapSnapshot> layer = this.snapshots.get(dimension);
		Map<Long, byte[]> terrainLayer = this.terrainTiles.get(dimension);
		Collection<BannerMarker> bannerLayer = this.bannerMarkers(dimension);
		boolean hasQuickMarker = this.quickMarker != null && this.quickMarker.dimension().equals(dimension);
		if ((layer == null || layer.isEmpty()) && (terrainLayer == null || terrainLayer.isEmpty())
			&& bannerLayer.isEmpty() && !hasQuickMarker) {
			return Optional.empty();
		}
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		if (layer != null) {
			for (MapSnapshot snapshot : layer.values()) {
				minX = Math.min(minX, snapshot.minX());
				minZ = Math.min(minZ, snapshot.minZ());
				maxX = Math.max(maxX, snapshot.maxXExclusive());
				maxZ = Math.max(maxZ, snapshot.maxZExclusive());
			}
		}
		if (terrainLayer != null) {
			for (long key : terrainLayer.keySet()) {
				int tileX = (int)(key >> 32);
				int tileZ = (int)key;
				minX = Math.min(minX, tileX * TerrainTile.SIDE);
				minZ = Math.min(minZ, tileZ * TerrainTile.SIDE);
				maxX = Math.max(maxX, (tileX + 1) * TerrainTile.SIDE);
				maxZ = Math.max(maxZ, (tileZ + 1) * TerrainTile.SIDE);
			}
		}
		if (!bannerLayer.isEmpty()) {
			for (BannerMarker marker : bannerLayer) {
				minX = Math.min(minX, marker.x());
				minZ = Math.min(minZ, marker.z());
				maxX = Math.max(maxX, marker.x() + 1);
				maxZ = Math.max(maxZ, marker.z() + 1);
			}
		}
		if (hasQuickMarker) {
			minX = Math.min(minX, this.quickMarker.x());
			minZ = Math.min(minZ, this.quickMarker.z());
			maxX = Math.max(maxX, this.quickMarker.x() + 1);
			maxZ = Math.max(maxZ, this.quickMarker.z() + 1);
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

	public final class ColorSampler {
		private final String dimension;
		private final boolean includeDetailedTerrain;
		private final boolean detailedTerrainRequiresMapCoverage;
		private int bucketX = Integer.MIN_VALUE;
		private int bucketZ = Integer.MIN_VALUE;
		private byte[] terrainColors;
		private List<MapSnapshot> mapCandidates;

		private ColorSampler(String dimension, boolean includeDetailedTerrain, boolean detailedTerrainRequiresMapCoverage) {
			this.dimension = dimension;
			this.includeDetailedTerrain = includeDetailedTerrain;
			this.detailedTerrainRequiresMapCoverage = detailedTerrainRequiresMapCoverage;
		}

		public int colorAt(int worldX, int worldZ) {
			int currentBucketX = Math.floorDiv(worldX, INDEX_BUCKET_SIZE);
			int currentBucketZ = Math.floorDiv(worldZ, INDEX_BUCKET_SIZE);
			if (currentBucketX != this.bucketX || currentBucketZ != this.bucketZ) {
				this.bucketX = currentBucketX;
				this.bucketZ = currentBucketZ;
				long key = bucketKey(currentBucketX, currentBucketZ);
				Map<Long, byte[]> terrainLayer = MapAtlas.this.terrainTiles.get(this.dimension);
				this.terrainColors = this.includeDetailedTerrain && terrainLayer != null ? terrainLayer.get(key) : null;
				Map<Long, List<MapSnapshot>> mapLayer = MapAtlas.this.spatialIndex.get(this.dimension);
				this.mapCandidates = mapLayer == null ? null : mapLayer.get(key);
			}

			int terrainColor = 0;
			if (this.terrainColors != null) {
				int index = Math.floorMod(worldX, INDEX_BUCKET_SIZE) + Math.floorMod(worldZ, INDEX_BUCKET_SIZE) * INDEX_BUCKET_SIZE;
				terrainColor = this.terrainColors[index] & 0xFF;
				if (terrainColor != 0 && !this.detailedTerrainRequiresMapCoverage) {
					return terrainColor;
				}
			}
			if (this.mapCandidates != null) {
				for (MapSnapshot candidate : this.mapCandidates) {
					int mapColor = candidate.colorAt(worldX, worldZ) & 0xFF;
					if (mapColor != 0) {
						return terrainColor != 0 ? terrainColor : mapColor;
					}
				}
			}
			return 0;
		}
	}

	public final class BiomeSampler {
		private final String dimension;
		private int chunkX = Integer.MIN_VALUE;
		private int chunkZ = Integer.MIN_VALUE;
		private String[] biomes;

		private BiomeSampler(String dimension) {
			this.dimension = dimension;
		}

		public String biomeAt(int worldX, int worldZ) {
			int currentChunkX = Math.floorDiv(worldX, 16);
			int currentChunkZ = Math.floorDiv(worldZ, 16);
			if (currentChunkX != this.chunkX || currentChunkZ != this.chunkZ) {
				this.chunkX = currentChunkX;
				this.chunkZ = currentChunkZ;
				Map<Long, String[]> layer = MapAtlas.this.biomeChunks.get(this.dimension);
				this.biomes = layer == null ? null : layer.get(bucketKey(currentChunkX, currentChunkZ));
			}
			if (this.biomes == null) {
				return null;
			}
			int quartX = Math.floorMod(worldX, 16) / 4;
			int quartZ = Math.floorMod(worldZ, 16) / 4;
			String biome = this.biomes[quartX + quartZ * 4];
			return biome == null || biome.isBlank() ? null : biome;
		}
	}

	public record TerrainChunk(String dimension, int chunkX, int chunkZ) {
	}

	public record BiomeChunk(String dimension, int chunkX, int chunkZ, String[] biomes) {
		public BiomeChunk {
			biomes = Arrays.copyOf(biomes, biomes.length);
		}

		@Override
		public String[] biomes() {
			return Arrays.copyOf(this.biomes, this.biomes.length);
		}
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
