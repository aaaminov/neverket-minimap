package dev.cartographer.minimap.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Persists an identity that follows one concrete singleplayer save directory. */
public final class WorldIdentityStore {
	private static final String DIRECTORY = "data";
	private static final String FILE_NAME = "neverket-minimap-world-id.txt";

	private WorldIdentityStore() {
	}

	public static UUID loadOrCreate(Path worldRoot) throws IOException {
		Path identityFile = worldRoot.resolve(DIRECTORY).resolve(FILE_NAME);
		UUID existing = read(identityFile);
		if (existing != null) {
			return existing;
		}

		Files.createDirectories(identityFile.getParent());
		UUID created = UUID.randomUUID();
		Path temporary = identityFile.resolveSibling(FILE_NAME + ".tmp");
		Files.writeString(temporary, created.toString() + System.lineSeparator(), StandardCharsets.UTF_8);
		try {
			Files.move(temporary, identityFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(temporary, identityFile, StandardCopyOption.REPLACE_EXISTING);
		}
		return created;
	}

	private static UUID read(Path file) {
		if (!Files.isRegularFile(file)) {
			return null;
		}
		try {
			return UUID.fromString(Files.readString(file, StandardCharsets.UTF_8).trim());
		} catch (IOException | IllegalArgumentException ignored) {
			return null;
		}
	}
}
