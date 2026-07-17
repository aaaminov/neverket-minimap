package dev.neverket.minimap.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RecordingAreaTest {
	@Test
	void usesCircularChunkDistance() {
		assertTrue(RecordingArea.containsChunkOffset(0, 0, 0));
		assertTrue(RecordingArea.containsChunkOffset(2, 0, 2));
		assertTrue(RecordingArea.containsChunkOffset(-1, -1, 2));
		assertFalse(RecordingArea.containsChunkOffset(2, 1, 2));
		assertFalse(RecordingArea.containsChunkOffset(2, 2, 2));
	}

	@Test
	void convertsRadiusToBlocksAndRejectsNegativeValues() {
		assertEquals(512, RecordingArea.radiusBlocks(32));
		assertThrows(IllegalArgumentException.class, () -> RecordingArea.radiusBlocks(-1));
		assertThrows(IllegalArgumentException.class, () -> RecordingArea.containsChunkOffset(0, 0, -1));
	}

	@Test
	void excludesSquareCornersFromRecordingArea() {
		assertEquals(5, countChunks(1));
		assertEquals(13, countChunks(2));
		assertEquals(3209, countChunks(32));
	}

	private static int countChunks(int radius) {
		int count = 0;
		for (int z = -radius; z <= radius; z++) {
			for (int x = -radius; x <= radius; x++) {
				if (RecordingArea.containsChunkOffset(x, z, radius)) {
					count++;
				}
			}
		}
		return count;
	}
}
