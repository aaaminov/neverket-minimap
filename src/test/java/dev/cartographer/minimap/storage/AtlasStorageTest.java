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

		storage.save("server:example.test", original);
		MapAtlas loaded = storage.load("server:example.test");

		assertEquals(1, loaded.size());
		assertEquals(42, loaded.snapshots().iterator().next().colors()[1234]);
		assertTrue(java.nio.file.Files.isRegularFile(storage.fileFor("server:example.test")));
	}
}
