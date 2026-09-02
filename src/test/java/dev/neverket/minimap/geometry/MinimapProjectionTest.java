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
	void viewRotationPreservesFractionalYawForSmoothRendering() {
		assertEquals(167.75, MinimapProjection.viewRotationDegrees(12.25), EPSILON);
		assertEquals(167.5, MinimapProjection.viewRotationDegrees(12.5), EPSILON);
	}

	@Test
	void rotationPaddingCoversTheFullSquareDiagonal() {
		assertEquals(24, MinimapProjection.rotationPadding(112, 112));
		assertEquals(12, MinimapProjection.rotationPadding(200, 100));
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
