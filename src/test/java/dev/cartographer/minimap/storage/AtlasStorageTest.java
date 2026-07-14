package dev.cartographer.minimap.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.cartographer.minimap.atlas.MapAtlas;
import dev.cartographer.minimap.atlas.MapSnapshot;
import java.nio.file.Path;
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

		storage.save("server:example.test", original);
		MapAtlas loaded = storage.load("server:example.test");

		assertEquals(1, loaded.size());
		assertEquals(42, loaded.snapshots().iterator().next().colors()[1234]);
		assertTrue(loaded.hasTerrainChunk("minecraft:overworld", -3, 5));
		assertEquals(24, loaded.colorAt("minecraft:overworld", -40, 88, true));
		assertTrue(java.nio.file.Files.isRegularFile(storage.fileFor("server:example.test")));
	}
}
