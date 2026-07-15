package dev.cartographer.minimap.marker;

/** A persistent copy of a banner decoration exposed by a vanilla map. */
public record BannerMarker(int sourceMapId, String dimension, int x, int z, String name, String assetId, long modifiedAt) {
	public BannerMarker {
		if (dimension == null || dimension.isBlank()) {
			throw new IllegalArgumentException("dimension must not be blank");
		}
		name = name == null ? "" : name.strip();
		if (assetId == null || assetId.isBlank()) {
			throw new IllegalArgumentException("assetId must not be blank");
		}
		if (modifiedAt < 0) {
			throw new IllegalArgumentException("modifiedAt must not be negative");
		}
	}

	public boolean sameContent(BannerMarker other) {
		return this.sourceMapId == other.sourceMapId
			&& this.dimension.equals(other.dimension)
			&& this.x == other.x
			&& this.z == other.z
			&& this.name.equals(other.name)
			&& this.assetId.equals(other.assetId);
	}
}
