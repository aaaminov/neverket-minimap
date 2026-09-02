package dev.neverket.minimap.geometry;

/** Minecraft-independent projection helpers shared by every client renderer. */
public final class MinimapProjection {
	private MinimapProjection() {
	}

	/**
	 * Returns the clockwise screen rotation that keeps the player's look direction at the top.
	 * Minecraft yaw is 0 toward south and increases toward west.
	 */
	public static double viewRotationDegrees(double yawDegrees) {
		return normalizeDegrees(180.0 - yawDegrees);
	}

	public static double normalizeDegrees(double degrees) {
		if (!Double.isFinite(degrees)) {
			return 0.0;
		}
		double normalized = degrees % 360.0;
		if (normalized >= 180.0) normalized -= 360.0;
		if (normalized < -180.0) normalized += 360.0;
		return normalized;
	}

	/** Converts an east/south world offset to a right/down minimap offset. */
	public static Offset worldToScreen(double worldX, double worldZ, double rotationDegrees) {
		double radians = Math.toRadians(rotationDegrees);
		double cosine = Math.cos(radians);
		double sine = Math.sin(radians);
		return new Offset(worldX * cosine - worldZ * sine, worldX * sine + worldZ * cosine);
	}

	/** Converts a right/down minimap offset to an east/south world offset. */
	public static Offset screenToWorld(double screenX, double screenY, double rotationDegrees) {
		double radians = Math.toRadians(rotationDegrees);
		double cosine = Math.cos(radians);
		double sine = Math.sin(radians);
		return new Offset(screenX * cosine + screenY * sine, -screenX * sine + screenY * cosine);
	}

	/** Projects a world direction onto the boundary of a square or circular minimap. */
	public static Offset framePoint(
		double worldX,
		double worldZ,
		double rotationDegrees,
		double halfWidth,
		double halfHeight,
		boolean circular
	) {
		Offset direction = worldToScreen(worldX, worldZ, rotationDegrees).normalized();
		if (direction.x() == 0.0 && direction.y() == 0.0) {
			return direction;
		}
		if (circular) {
			double radius = Math.min(halfWidth, halfHeight);
			return direction.scaled(radius);
		}
		double horizontalScale = direction.x() == 0.0 ? Double.POSITIVE_INFINITY : halfWidth / Math.abs(direction.x());
		double verticalScale = direction.y() == 0.0 ? Double.POSITIVE_INFINITY : halfHeight / Math.abs(direction.y());
		return direction.scaled(Math.min(horizontalScale, verticalScale));
	}

	/** Extra pixels required on one side so a rotated rectangle never exposes an empty corner. */
	public static int rotationPadding(int axisLength, int otherAxisLength) {
		if (axisLength <= 0 || otherAxisLength <= 0) {
			throw new IllegalArgumentException("axis lengths must be positive");
		}
		return Math.max(0, (int)Math.ceil((Math.hypot(axisLength, otherAxisLength) - axisLength) / 2.0));
	}

	public record Offset(double x, double y) {
		public Offset normalized() {
			double length = Math.hypot(this.x, this.y);
			return length == 0.0 ? this : new Offset(this.x / length, this.y / length);
		}

		public Offset scaled(double scale) {
			return new Offset(this.x * scale, this.y * scale);
		}
	}
}
