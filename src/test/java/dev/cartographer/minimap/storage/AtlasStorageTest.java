package dev.cartographer.minimap.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.cartographer.minimap.atlas.MapAtlas;
import dev.cartographer.minimap.atlas.MapSnapshot;
import dev.cartographer.minimap.atlas.TerrainTile;
import dev.cartographer.minimap.marker.BannerMarker;
import dev.cartographer.minimap.marker.QuickMarker;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtlasStorageTest {
	@Test
	void roundTripsAtlas(@TempDir Path directory) throws Exception {
		AtlasStorage storage = new AtlasStorage(directory);
		MapAtlas original = new MapAtlas();
		byte[] colors = new byte[MapSnapshot.PIXEL_COUNT];
		colors[1234] = 42;
		original.put(new MapSnapshot(17, "minecraft:overworld", 192, -64, (byte)2, colors));
		byte[] terrain = new byte[16 * 16];
		java.util.Arrays.fill(terrain, (byte)24);
		original.putTerrainChunk("minecraft:overworld", -3, 5, terrain);
		String[] biomes = new String[16];
		java.util.Arrays.fill(biomes, "minecraft:plains");
		original.putBiomeChunk("minecraft:overworld", -3, 5, biomes);
		original.putQuickMarker(new QuickMarker("minecraft:the_nether", 12, -34, 123456L));
		original.putBannerMarker(new BannerMarker(
			17, "minecraft:overworld", 200, -70, "Home", "minecraft:red_banner", 234567L
		));

		storage.save("server:example.test", original);
		MapAtlas loaded = storage.load("server:example.test");

		assertEquals(1, loaded.size());
		assertEquals(42, loaded.snapshots().iterator().next().colors()[1234]);
		assertTrue(loaded.hasTerrainChunk("minecraft:overworld", -3, 5));
		assertEquals(24, loaded.colorAt("minecraft:overworld", -40, 88, true));
		assertEquals("minecraft:plains", loaded.biomeAt("minecraft:overworld", -40, 88));
		assertEquals(123456L, loaded.quickMarker().orElseThrow().modifiedAt());
		assertEquals(12, loaded.quickMarker().orElseThrow().x());
		assertEquals("Home", loaded.bannerMarkers("minecraft:overworld").iterator().next().name());
		assertTrue(java.nio.file.Files.isRegularFile(storage.fileFor("server:example.test")));
		byte[] compressed = Files.readAllBytes(storage.fileFor("server:example.test"));
		assertEquals(0x1F, compressed[0] & 0xFF);
		assertEquals(0x8B, compressed[1] & 0xFF);
	}

	@Test
	void loadsVersionFourTerrainWhenMarkersAreAdded(@TempDir Path directory) throws Exception {
		AtlasStorage storage = new AtlasStorage(directory);
		byte[] colors = new byte[TerrainTile.PIXEL_COUNT];
		colors[0] = 24;
		String json = """
			{
			  "format": 4,
			  "world": "server:old.example",
			  "maps": [],
			  "terrain": [{"dimension":"minecraft:overworld","tileX":0,"tileZ":0,"colors":"%s"}],
			  "terrainChunks": []
			}
			""".formatted(Base64.getEncoder().encodeToString(colors));
		Files.createDirectories(directory);
		Files.writeString(storage.legacyFileFor("server:old.example"), json);

		MapAtlas loaded = storage.load("server:old.example");

		assertEquals(24, loaded.colorAt("minecraft:overworld", 0, 0, true));
		assertTrue(loaded.quickMarker().isEmpty());
	}
}
