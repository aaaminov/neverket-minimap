package dev.cartographer.minimap.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.cartographer.minimap.atlas.MapAtlas;
import dev.cartographer.minimap.atlas.MapSnapshot;
import dev.cartographer.minimap.atlas.TerrainTile;
import java.io.IOException;
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

public final class AtlasStorage {
	private static final int FORMAT_VERSION = 3;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path directory;

	public AtlasStorage(Path directory) {
		this.directory = directory;
	}

	public MapAtlas load(String worldKey) throws IOException {
		Path file = this.fileFor(worldKey);
		MapAtlas atlas = new MapAtlas();
		if (!Files.isRegularFile(file)) {
			return atlas;
		}

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			StoredAtlas stored = GSON.fromJson(reader, StoredAtlas.class);
			if (stored != null && stored.format == 1) {
				// Version 1 trusted the placeholder 0,0 center created by the vanilla
				// multiplayer client. Those coordinates cannot be migrated safely.
				return atlas;
			}
			if (stored == null || (stored.format != 2 && stored.format != FORMAT_VERSION) || stored.maps == null) {
				throw new IOException("Unsupported or invalid atlas file: " + file);
			}
			for (StoredMap map : stored.maps) {
				byte[] colors = Base64.getDecoder().decode(map.colors);
				atlas.put(new MapSnapshot(map.id, map.dimension, map.centerX, map.centerZ, map.scale, colors));
			}
			if (stored.format == FORMAT_VERSION && stored.terrain != null) {
				for (StoredTerrainTile tile : stored.terrain) {
					byte[] colors = Base64.getDecoder().decode(tile.colors);
					atlas.putTerrainTile(new TerrainTile(tile.dimension, tile.tileX, tile.tileZ, colors));
				}
			}
			if (stored.format == FORMAT_VERSION && stored.terrainChunks != null) {
				for (StoredTerrainChunk chunk : stored.terrainChunks) {
					atlas.putTerrainChunkReference(new MapAtlas.TerrainChunk(chunk.dimension, chunk.chunkX, chunk.chunkZ));
				}
			}
		}
		return atlas;
	}

	public void save(String worldKey, MapAtlas atlas) throws IOException {
		Files.createDirectories(this.directory);
		List<StoredMap> maps = new ArrayList<>();
		for (MapSnapshot snapshot : atlas.snapshots()) {
			maps.add(new StoredMap(snapshot.id(), snapshot.dimension(), snapshot.centerX(), snapshot.centerZ(), snapshot.scale(), Base64.getEncoder().encodeToString(snapshot.colors())));
		}
		List<StoredTerrainTile> terrain = new ArrayList<>();
		for (TerrainTile tile : atlas.terrainTiles()) {
			terrain.add(new StoredTerrainTile(
				tile.dimension(), tile.tileX(), tile.tileZ(), Base64.getEncoder().encodeToString(tile.colors())
			));
		}
		List<StoredTerrainChunk> terrainChunks = new ArrayList<>();
		for (MapAtlas.TerrainChunk chunk : atlas.terrainChunks()) {
			terrainChunks.add(new StoredTerrainChunk(chunk.dimension(), chunk.chunkX(), chunk.chunkZ()));
		}

		Path target = this.fileFor(worldKey);
		Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
			GSON.toJson(new StoredAtlas(FORMAT_VERSION, worldKey, maps, terrain, terrainChunks), writer);
		}

		try {
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	Path fileFor(String worldKey) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(worldKey.getBytes(StandardCharsets.UTF_8));
			return this.directory.resolve(HexFormat.of().formatHex(hash, 0, 16) + ".json");
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
		}
	}

	private record StoredAtlas(int format, String world, List<StoredMap> maps, List<StoredTerrainTile> terrain, List<StoredTerrainChunk> terrainChunks) {
	}

	private record StoredMap(int id, String dimension, int centerX, int centerZ, byte scale, String colors) {
	}

	private record StoredTerrainTile(String dimension, int tileX, int tileZ, String colors) {
	}

	private record StoredTerrainChunk(String dimension, int chunkX, int chunkZ) {
	}
}
