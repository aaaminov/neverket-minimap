package dev.neverket.minimap.storage;

import com.google.gson.Gson;
import dev.neverket.minimap.atlas.MapAtlas;
import dev.neverket.minimap.atlas.MapSnapshot;
import dev.neverket.minimap.atlas.TerrainTile;
import dev.neverket.minimap.marker.BannerMarker;
import dev.neverket.minimap.marker.QuickMarker;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class AtlasStorage {
	private static final int FORMAT_VERSION = 6;
	private static final Gson GSON = new Gson();

	private final Path directory;

	public AtlasStorage(Path directory) {
		this.directory = directory;
	}

	public MapAtlas load(String worldKey) throws IOException {
		Path file = this.fileFor(worldKey);
		MapAtlas atlas = new MapAtlas();
		if (!Files.isRegularFile(file)) {
			file = this.legacyFileFor(worldKey);
			if (!Files.isRegularFile(file)) {
				return atlas;
			}
		}

		try (Reader reader = this.openReader(file)) {
			StoredAtlas stored = GSON.fromJson(reader, StoredAtlas.class);
			if (stored != null && stored.format == 1) {
				// Version 1 trusted the placeholder 0,0 center created by the vanilla
				// multiplayer client. Those coordinates cannot be migrated safely.
				return atlas;
			}
			if (stored == null || (stored.format < 2 || stored.format > FORMAT_VERSION) || stored.maps == null) {
				throw new IOException("Unsupported or invalid atlas file: " + file);
			}
			for (StoredMap map : stored.maps) {
				byte[] colors = Base64.getDecoder().decode(map.colors);
				atlas.put(new MapSnapshot(map.id, map.dimension, map.centerX, map.centerZ, map.scale, colors));
			}
			if (stored.format >= 4 && stored.terrain != null) {
				for (StoredTerrainTile tile : stored.terrain) {
					byte[] colors = Base64.getDecoder().decode(tile.colors);
					atlas.putTerrainTile(new TerrainTile(tile.dimension, tile.tileX, tile.tileZ, colors));
				}
			}
			if (stored.format >= 4 && stored.terrainChunks != null) {
				for (StoredTerrainChunk chunk : stored.terrainChunks) {
					atlas.putTerrainChunkReference(new MapAtlas.TerrainChunk(chunk.dimension, chunk.chunkX, chunk.chunkZ));
				}
			}
			if (stored.format >= 5 && stored.quickMarker != null) {
				StoredQuickMarker marker = stored.quickMarker;
				atlas.putQuickMarker(new QuickMarker(marker.dimension, marker.x, marker.z, marker.modifiedAt));
			}
			if (stored.format >= 5 && stored.bannerMarkers != null) {
				for (StoredBannerMarker marker : stored.bannerMarkers) {
					atlas.putBannerMarker(new BannerMarker(
						marker.sourceMapId, marker.dimension, marker.x, marker.z, marker.name, marker.assetId, marker.modifiedAt
					));
				}
			}
			if (stored.format >= 6 && stored.biomeChunks != null) {
				for (StoredBiomeChunk chunk : stored.biomeChunks) {
					atlas.putBiomeChunk(new MapAtlas.BiomeChunk(chunk.dimension, chunk.chunkX, chunk.chunkZ, chunk.biomes));
				}
			}
		}
		return atlas;
	}

	public void save(String worldKey, MapAtlas atlas) throws IOException {
		this.save(this.snapshot(worldKey, atlas));
	}

	public SaveSnapshot snapshot(String worldKey, MapAtlas atlas) {
		return new SaveSnapshot(
			worldKey,
			List.copyOf(atlas.snapshots()),
			List.copyOf(atlas.terrainTiles()),
			List.copyOf(atlas.terrainChunks()),
			atlas.quickMarker(),
			List.copyOf(atlas.bannerMarkers()),
			List.copyOf(atlas.biomeChunks())
		);
	}

	public void save(SaveSnapshot snapshot) throws IOException {
		Files.createDirectories(this.directory);
		List<StoredMap> maps = new ArrayList<>();
		for (MapSnapshot map : snapshot.maps) {
			maps.add(new StoredMap(map.id(), map.dimension(), map.centerX(), map.centerZ(), map.scale(), Base64.getEncoder().encodeToString(map.colors())));
		}
		List<StoredTerrainTile> terrain = new ArrayList<>();
		for (TerrainTile tile : snapshot.terrain) {
			terrain.add(new StoredTerrainTile(
				tile.dimension(), tile.tileX(), tile.tileZ(), Base64.getEncoder().encodeToString(tile.colors())
			));
		}
		List<StoredTerrainChunk> terrainChunks = new ArrayList<>();
		for (MapAtlas.TerrainChunk chunk : snapshot.terrainChunks) {
			terrainChunks.add(new StoredTerrainChunk(chunk.dimension(), chunk.chunkX(), chunk.chunkZ()));
		}
		StoredQuickMarker quickMarker = snapshot.quickMarker
			.map(marker -> new StoredQuickMarker(marker.dimension(), marker.x(), marker.z(), "", marker.modifiedAt()))
			.orElse(null);
		List<StoredBannerMarker> bannerMarkers = new ArrayList<>();
		for (BannerMarker marker : snapshot.bannerMarkers) {
			bannerMarkers.add(new StoredBannerMarker(
				marker.sourceMapId(), marker.dimension(), marker.x(), marker.z(), marker.name(), marker.assetId(), marker.modifiedAt()
			));
		}
		List<StoredBiomeChunk> biomeChunks = new ArrayList<>();
		for (MapAtlas.BiomeChunk chunk : snapshot.biomeChunks) {
			biomeChunks.add(new StoredBiomeChunk(chunk.dimension(), chunk.chunkX(), chunk.chunkZ(), chunk.biomes()));
		}

		Path target = this.fileFor(snapshot.worldKey);
		Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
		try (Writer writer = new OutputStreamWriter(
			new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary))), StandardCharsets.UTF_8
		)) {
			GSON.toJson(new StoredAtlas(
				FORMAT_VERSION, snapshot.worldKey, maps, terrain, terrainChunks, quickMarker, bannerMarkers, biomeChunks
			), writer);
		}

		try {
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public static final class SaveSnapshot {
		private final String worldKey;
		private final List<MapSnapshot> maps;
		private final List<TerrainTile> terrain;
		private final List<MapAtlas.TerrainChunk> terrainChunks;
		private final Optional<QuickMarker> quickMarker;
		private final List<BannerMarker> bannerMarkers;
		private final List<MapAtlas.BiomeChunk> biomeChunks;

		private SaveSnapshot(
			String worldKey,
			List<MapSnapshot> maps,
			List<TerrainTile> terrain,
			List<MapAtlas.TerrainChunk> terrainChunks,
			Optional<QuickMarker> quickMarker,
			List<BannerMarker> bannerMarkers,
			List<MapAtlas.BiomeChunk> biomeChunks
		) {
			this.worldKey = worldKey;
			this.maps = maps;
			this.terrain = terrain;
			this.terrainChunks = terrainChunks;
			this.quickMarker = quickMarker;
			this.bannerMarkers = bannerMarkers;
			this.biomeChunks = biomeChunks;
		}
	}

	Path fileFor(String worldKey) {
		return this.directory.resolve(this.fileStem(worldKey) + ".json.gz");
	}

	Path legacyFileFor(String worldKey) {
		return this.directory.resolve(this.fileStem(worldKey) + ".json");
	}

	private Reader openReader(Path file) throws IOException {
		BufferedInputStream input = new BufferedInputStream(Files.newInputStream(file));
		if (file.getFileName().toString().endsWith(".gz")) {
			return new InputStreamReader(new GZIPInputStream(input), StandardCharsets.UTF_8);
		}
		return new InputStreamReader(input, StandardCharsets.UTF_8);
	}

	private String fileStem(String worldKey) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(worldKey.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash, 0, 16);
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
		}
	}

	private record StoredAtlas(
		int format,
		String world,
		List<StoredMap> maps,
		List<StoredTerrainTile> terrain,
		List<StoredTerrainChunk> terrainChunks,
		StoredQuickMarker quickMarker,
		List<StoredBannerMarker> bannerMarkers,
		List<StoredBiomeChunk> biomeChunks
	) {
	}

	private record StoredMap(int id, String dimension, int centerX, int centerZ, byte scale, String colors) {
	}

	private record StoredTerrainTile(String dimension, int tileX, int tileZ, String colors) {
	}

	private record StoredTerrainChunk(String dimension, int chunkX, int chunkZ) {
	}

	private record StoredQuickMarker(String dimension, int x, int z, String name, long modifiedAt) {
	}

	private record StoredBannerMarker(int sourceMapId, String dimension, int x, int z, String name, String assetId, long modifiedAt) {
	}

	private record StoredBiomeChunk(String dimension, int chunkX, int chunkZ, String[] biomes) {
	}
}
