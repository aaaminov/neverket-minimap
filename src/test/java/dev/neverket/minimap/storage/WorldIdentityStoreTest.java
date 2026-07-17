package dev.neverket.minimap.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldIdentityStoreTest {
	@TempDir
	Path directory;

	@Test
	void identityIsStableForTheSameSave() throws IOException {
		Path world = Files.createDirectory(this.directory.resolve("World"));

		UUID first = WorldIdentityStore.loadOrCreate(world);
		UUID second = WorldIdentityStore.loadOrCreate(world);

		assertEquals(first, second);
	}

	@Test
	void recreatedSaveWithTheSameFolderGetsANewIdentity() throws IOException {
		Path world = Files.createDirectory(this.directory.resolve("World"));
		UUID deletedWorld = WorldIdentityStore.loadOrCreate(world);
		try (var paths = Files.walk(world)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.delete(path);
				} catch (IOException exception) {
					throw new IllegalStateException(exception);
				}
			});
		}

		Path recreatedWorld = Files.createDirectory(this.directory.resolve("World"));
		UUID newWorld = WorldIdentityStore.loadOrCreate(recreatedWorld);

		assertNotEquals(deletedWorld, newWorld);
	}
}
