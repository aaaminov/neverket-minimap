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

	public static double quantizedViewRotationDegrees(double yawDegrees, double stepDegrees) {
		if (!(stepDegrees > 0.0) || !Double.isFinite(stepDegrees)) {
			throw new IllegalArgumentException("stepDegrees must be finite and positive");
		}
		double rotation = viewRotationDegrees(yawDegrees);
		return normalizeDegrees(Math.round(rotation / stepDegrees) * stepDegrees);
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

	public record Offset(double x, double y) {
		public Offset normalized() {
			double length = Math.hypot(this.x, this.y);
			return length == 0.0 ? this : new Offset(this.x / length, this.y / length);
		}

		public Offset scaled(double scale) {
			return new Offset(this.x * scale, this.y * scale);
		}
	}

	/** Keeps every minimap layer on one throttled rotation angle. */
	public static final class RotationLimiter {
		private final double stepDegrees;
		private final long intervalNanos;
		private double appliedDegrees;
		private long lastUpdateNanos = Long.MIN_VALUE;

		public RotationLimiter(double stepDegrees, long intervalNanos) {
			if (!(stepDegrees > 0.0) || !Double.isFinite(stepDegrees)) {
				throw new IllegalArgumentException("stepDegrees must be finite and positive");
			}
			if (intervalNanos < 0L) {
				throw new IllegalArgumentException("intervalNanos must not be negative");
			}
			this.stepDegrees = stepDegrees;
			this.intervalNanos = intervalNanos;
		}

		public double update(double yawDegrees, boolean enabled, long nowNanos) {
			if (!enabled) {
				this.appliedDegrees = 0.0;
				this.lastUpdateNanos = Long.MIN_VALUE;
				return 0.0;
			}
			if (this.lastUpdateNanos == Long.MIN_VALUE
				|| nowNanos < this.lastUpdateNanos
				|| nowNanos - this.lastUpdateNanos >= this.intervalNanos) {
				this.appliedDegrees = quantizedViewRotationDegrees(yawDegrees, this.stepDegrees);
				this.lastUpdateNanos = nowNanos;
			}
			return this.appliedDegrees;
		}
	}
}
