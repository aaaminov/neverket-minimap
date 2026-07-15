package dev.cartographer.minimap.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.cartographer.minimap.marker.BannerMarker;
import dev.cartographer.minimap.marker.QuickMarker;
import org.junit.jupiter.api.Test;

class MapAtlasTest {
	@Test
	void mapsWorldCoordinatesToVanillaPixels() {
		byte[] colors = new byte[MapSnapshot.PIXEL_COUNT];
		colors[0] = 5;
		colors[127 + 127 * 128] = 9;
		MapSnapshot snapshot = new MapSnapshot(1, "minecraft:overworld", 0, 0, (byte)0, colors);

		assertEquals(5, snapshot.colorAt(-64, -64));
		assertEquals(9, snapshot.colorAt(63, 63));
		assertEquals(0, snapshot.colorAt(64, 63));
	}

	@Test
	void combinesAdjacentMapsAndSeparatesDimensions() {
		MapAtlas atlas = new MapAtlas();
		atlas.put(solidMap(1, "minecraft:overworld", 0, 0, (byte)0, (byte)4));
		atlas.put(solidMap(2, "minecraft:overworld", 128, 0, (byte)0, (byte)8));
		atlas.put(solidMap(3, "minecraft:the_nether", 0, 0, (byte)0, (byte)12));

		assertEquals(4, atlas.colorAt("minecraft:overworld", 63, 0));
		assertEquals(8, atlas.colorAt("minecraft:overworld", 64, 0));
		assertEquals(12, atlas.colorAt("minecraft:the_nether", 0, 0));
		assertEquals(0, atlas.colorAt("minecraft:the_end", 0, 0));
	}

	@Test
	void prefersDetailedKnownPixelsAndFallsBackForUnknownOnes() {
		MapAtlas atlas = new MapAtlas();
		atlas.put(solidMap(1, "minecraft:overworld", 0, 0, (byte)1, (byte)20));
		byte[] detailed = new byte[MapSnapshot.PIXEL_COUNT];
		detailed[64 + 64 * 128] = 40;
		atlas.put(new MapSnapshot(2, "minecraft:overworld", 0, 0, (byte)0, detailed));

		assertEquals(40, atlas.colorAt("minecraft:overworld", 0, 0));
		assertEquals(20, atlas.colorAt("minecraft:overworld", 1, 0));
	}

	@Test
	void ignoresUnchangedSnapshots() {
		MapAtlas atlas = new MapAtlas();
		MapSnapshot map = solidMap(1, "minecraft:overworld", 0, 0, (byte)0, (byte)4);

		assertTrue(atlas.put(map));
		assertFalse(atlas.put(map));
		assertEquals(1, atlas.size());
	}

	@Test
	void calculatesLayerBounds() {
		MapAtlas atlas = new MapAtlas();
		atlas.put(solidMap(1, "minecraft:overworld", 0, 0, (byte)0, (byte)4));
		atlas.put(solidMap(2, "minecraft:overworld", 128, 0, (byte)0, (byte)8));

		MapAtlas.Bounds bounds = atlas.bounds("minecraft:overworld").orElseThrow();
		assertEquals(-64, bounds.minX());
		assertEquals(192, bounds.maxXExclusive());
		assertEquals(256, bounds.width());
	}

	@Test
	void keepsPreviouslyKnownPixelsWhenUpdateIsPartial() {
		MapAtlas atlas = new MapAtlas();
		byte[] first = new byte[MapSnapshot.PIXEL_COUNT];
		first[10] = 4;
		atlas.put(new MapSnapshot(1, "minecraft:overworld", 0, 0, (byte)0, first));
		byte[] update = new byte[MapSnapshot.PIXEL_COUNT];
		update[20] = 8;
		atlas.put(new MapSnapshot(1, "minecraft:overworld", 0, 0, (byte)0, update));

		byte[] merged = atlas.snapshots().iterator().next().colors();
		assertEquals(4, merged[10]);
		assertEquals(8, merged[20]);
	}

	@Test
	void recoversVanillaCenterFromPlayerMarker() {
		assertEquals(64, MapCoordinates.centerFromPlayerMarker(100.0, (byte)36, (byte)1));
		assertEquals(-128, MapCoordinates.centerFromPlayerMarker(-100.0, (byte)56, (byte)0));
	}

	@Test
	void storesDetailedTerrainSeparatelyFromVanillaMaps() {
		MapAtlas atlas = new MapAtlas();
		atlas.put(solidMap(1, "minecraft:overworld", 0, 0, (byte)0, (byte)4));
		byte[] terrain = new byte[16 * 16];
		java.util.Arrays.fill(terrain, (byte)28);

		assertTrue(atlas.putTerrainChunk("minecraft:overworld", -1, -1, terrain));
		assertTrue(atlas.hasTerrainChunk("minecraft:overworld", -1, -1));
		assertEquals(28, atlas.colorAt("minecraft:overworld", -1, -1, true));
		assertEquals(4, atlas.colorAt("minecraft:overworld", -1, -1, false));
		assertFalse(atlas.putTerrainChunk("minecraft:overworld", -1, -1, terrain));
	}

	@Test
	void cachedSamplerMatchesAtlasLayers() {
		MapAtlas atlas = new MapAtlas();
		atlas.put(solidMap(1, "minecraft:overworld", 0, 0, (byte)0, (byte)4));
		byte[] terrain = new byte[16 * 16];
		java.util.Arrays.fill(terrain, (byte)28);
		atlas.putTerrainChunk("minecraft:overworld", 0, 0, terrain);

		MapAtlas.ColorSampler detailed = atlas.sampler("minecraft:overworld", true);
		MapAtlas.ColorSampler mapsOnly = atlas.sampler("minecraft:overworld", false);
		assertEquals(28, detailed.colorAt(4, 4));
		assertEquals(4, mapsOnly.colorAt(4, 4));
		assertEquals(atlas.colorAt("minecraft:overworld", -20, -20, true), detailed.colorAt(-20, -20));
	}

	@Test
	void detailedTerrainCanBeRestrictedToKnownMapPixels() {
		MapAtlas atlas = new MapAtlas();
		byte[] mapColors = new byte[MapSnapshot.PIXEL_COUNT];
		mapColors[64 + 64 * 128] = 4;
		atlas.put(new MapSnapshot(1, "minecraft:overworld", 0, 0, (byte)0, mapColors));
		byte[] terrain = new byte[16 * 16];
		java.util.Arrays.fill(terrain, (byte)28);
		atlas.putTerrainChunk("minecraft:overworld", 0, 0, terrain);

		MapAtlas.ColorSampler sampler = atlas.sampler("minecraft:overworld", true, true);

		assertEquals(28, sampler.colorAt(0, 0));
		assertEquals(0, sampler.colorAt(1, 0));
	}

	@Test
	void keepsOnlyOneQuickMarker() {
		MapAtlas atlas = new MapAtlas();

		assertTrue(atlas.putQuickMarker(new QuickMarker("minecraft:overworld", 10, 20, 100L)));
		assertTrue(atlas.putQuickMarker(new QuickMarker("minecraft:the_nether", -5, 8, 200L)));

		QuickMarker marker = atlas.quickMarker().orElseThrow();
		assertEquals("minecraft:the_nether", marker.dimension());
		assertEquals(200L, marker.modifiedAt());
		assertTrue(atlas.removeQuickMarker());
		assertTrue(atlas.quickMarker().isEmpty());
		assertFalse(atlas.removeQuickMarker());
	}

	@Test
	void replacesBannerMarkersPerSourceMapAndPreservesObservationTime() {
		MapAtlas atlas = new MapAtlas();
		BannerMarker first = new BannerMarker(4, "minecraft:overworld", 10, 20, "Home", "minecraft:red_banner", 100L);
		assertTrue(atlas.replaceBannerMarkers(4, java.util.List.of(first)));

		BannerMarker sameObservedLater = new BannerMarker(4, "minecraft:overworld", 10, 20, "Home", "minecraft:red_banner", 999L);
		assertFalse(atlas.replaceBannerMarkers(4, java.util.List.of(sameObservedLater)));
		assertEquals(100L, atlas.bannerMarkers("minecraft:overworld").iterator().next().modifiedAt());

		assertTrue(atlas.replaceBannerMarkers(4, java.util.List.of()));
		assertTrue(atlas.bannerMarkers("minecraft:overworld").isEmpty());
	}

	private static MapSnapshot solidMap(int id, String dimension, int x, int z, byte scale, byte color) {
		byte[] colors = new byte[MapSnapshot.PIXEL_COUNT];
		java.util.Arrays.fill(colors, color);
		return new MapSnapshot(id, dimension, x, z, scale, colors);
	}
}
