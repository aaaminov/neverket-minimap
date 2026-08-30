package dev.neverket.minimap.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MinimapProjectionTest {
	private static final double EPSILON = 1.0E-9;

	@Test
	void playerLookDirectionAlwaysProjectsToScreenTop() {
		assertOffset(0.0, -1.0, projectHeading(0.0));
		assertOffset(0.0, -1.0, projectHeading(90.0));
		assertOffset(0.0, -1.0, projectHeading(180.0));
		assertOffset(0.0, -1.0, projectHeading(-90.0));
	}

	@Test
	void zeroRotationPreservesNorthUpProjection() {
		assertOffset(3.0, -5.0, MinimapProjection.worldToScreen(3.0, -5.0, 0.0));
		assertOffset(3.0, -5.0, MinimapProjection.screenToWorld(3.0, -5.0, 0.0));
	}

	@Test
	void screenAndWorldTransformsAreInverse() {
		MinimapProjection.Offset screen = MinimapProjection.worldToScreen(11.5, -7.25, 123.0);
		assertOffset(11.5, -7.25, MinimapProjection.screenToWorld(screen.x(), screen.y(), 123.0));
	}

	@Test
	void northIndicatorFollowsSquareAndCircularFrames() {
		double rotation = MinimapProjection.viewRotationDegrees(90.0);
		assertOffset(50.0, 0.0, MinimapProjection.framePoint(0.0, -1.0, rotation, 50.0, 40.0, false));
		assertOffset(40.0, 0.0, MinimapProjection.framePoint(0.0, -1.0, rotation, 50.0, 40.0, true));
	}

	@Test
	void viewRotationCanBeQuantizedWithoutChangingItsConvention() {
		assertEquals(90.0, MinimapProjection.quantizedViewRotationDegrees(91.0, 2.0), EPSILON);
		assertEquals(-180.0, MinimapProjection.quantizedViewRotationDegrees(0.0, 2.0), EPSILON);
	}

	@Test
	void rotationLimiterKeepsOneAppliedAngleUntilItsIntervalElapses() {
		MinimapProjection.RotationLimiter limiter = new MinimapProjection.RotationLimiter(2.0, 250_000_000L);

		assertEquals(-180.0, limiter.update(0.0, true, 100L), EPSILON);
		assertEquals(-180.0, limiter.update(90.0, true, 250_000_099L), EPSILON);
		assertEquals(90.0, limiter.update(90.0, true, 250_000_100L), EPSILON);
		assertEquals(0.0, limiter.update(90.0, false, 250_000_101L), EPSILON);
		assertEquals(-90.0, limiter.update(-90.0, true, 250_000_102L), EPSILON);
	}

	private static MinimapProjection.Offset projectHeading(double yawDegrees) {
		double radians = Math.toRadians(yawDegrees);
		double worldX = -Math.sin(radians);
		double worldZ = Math.cos(radians);
		return MinimapProjection.worldToScreen(
			worldX, worldZ, MinimapProjection.viewRotationDegrees(yawDegrees)
		);
	}

	private static void assertOffset(double expectedX, double expectedY, MinimapProjection.Offset actual) {
		assertEquals(expectedX, actual.x(), EPSILON);
		assertEquals(expectedY, actual.y(), EPSILON);
	}
}
