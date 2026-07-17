package dev.neverket.minimap.atlas;

public final class MapCoordinates {
	private MapCoordinates() {
	}

	/**
	 * Recovers the vanilla map center from the player's world coordinate and the
	 * in-map player marker. The marker has half-pixel precision, so the result is
	 * snapped back to the grid used by vanilla map creation.
	 */
	public static int centerFromPlayerMarker(double playerCoordinate, byte markerCoordinate, byte scale) {
		int blocksPerPixel = 1 << scale;
		int mapSize = MapSnapshot.SIDE * blocksPerPixel;
		int gridOffset = mapSize / 2 - 64;
		double approximateCenter = playerCoordinate - markerCoordinate / 2.0 * blocksPerPixel;
		return (int)Math.round((approximateCenter - gridOffset) / mapSize) * mapSize + gridOffset;
	}
}
