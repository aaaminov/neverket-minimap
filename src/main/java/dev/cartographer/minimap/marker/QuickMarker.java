package dev.cartographer.minimap.marker;

/** The single user-editable marker stored for a world atlas. */
public record QuickMarker(String dimension, int x, int z, long modifiedAt) {
	public QuickMarker {
		if (dimension == null || dimension.isBlank()) {
			throw new IllegalArgumentException("dimension must not be blank");
		}
		if (modifiedAt < 0) {
			throw new IllegalArgumentException("modifiedAt must not be negative");
		}
	}
}
