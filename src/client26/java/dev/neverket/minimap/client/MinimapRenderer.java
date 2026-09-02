package dev.neverket.minimap.client;

import dev.neverket.minimap.config.ModConfig;
import dev.neverket.minimap.config.MapLighting;
import dev.neverket.minimap.geometry.MinimapProjection;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;

public final class MinimapRenderer implements AutoCloseable {
	private static final int MARGIN = 10;

	private final Minecraft minecraft;
	private final WorldSession session;
	private final ModConfig config;
	private final GuiGraphicsAccess graphicsAccess;
	private final MapMarkerRenderer markerRenderer;
	private MapViewTexture viewTexture;
	private int viewSize;
	private boolean viewRotationOverscan;

	public MinimapRenderer(Minecraft minecraft, WorldSession session, ModConfig config, GuiGraphicsAccess graphicsAccess) {
		this.minecraft = minecraft;
		this.session = session;
		this.config = config;
		this.graphicsAccess = graphicsAccess;
		this.markerRenderer = new MapMarkerRenderer(minecraft);
		this.resizeViewTexture(config.size, this.needsRotationOverscan());
	}

	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!this.config.visible || this.minecraft.gui.screen() instanceof FullscreenMapScreen
			|| this.minecraft.player == null || this.minecraft.level == null || !this.session.active()) {
			return;
		}

		int size = this.config.size;
		this.resizeViewTexture(size, this.needsRotationOverscan());
		int cardinalPadding = this.config.showCardinalDirections ? 7 : 0;
		int bottomTextPadding = (this.config.showCardinalDirections ? 12 : 0) + (this.config.showCoordinates ? 14 : 0);
		int x = switch (this.config.corner) {
			case TOP_LEFT, BOTTOM_LEFT -> MARGIN + cardinalPadding;
			case TOP_RIGHT, BOTTOM_RIGHT -> graphics.guiWidth() - size - MARGIN - cardinalPadding;
		};
		int y = switch (this.config.corner) {
			case TOP_LEFT -> MARGIN + cardinalPadding;
			case TOP_RIGHT -> MARGIN + cardinalPadding + (this.hasVisibleEffects() ? 28 : 0);
			case BOTTOM_LEFT, BOTTOM_RIGHT -> Math.max(MARGIN + cardinalPadding, graphics.guiHeight() - size - MARGIN - bottomTextPadding);
		};

		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
		var playerPosition = this.minecraft.player.getPosition(partialTick);
		float playerYaw = this.minecraft.player.getYRot(partialTick);
		double rotationDegrees = this.config.rotateMinimap
			? MinimapProjection.viewRotationDegrees(playerYaw)
			: 0.0;
		String dimension = this.minecraft.level.dimension().identifier().toString();
		this.viewTexture.update(
			this.session.atlas(), this.session.terrainContours(), dimension,
			playerPosition.x, playerPosition.z, this.config.zoom, size, size,
			this.config.shape == ModConfig.Shape.CIRCLE, this.config.minimapUnknownOpacity,
			this.useDetailedTerrain(), this.detailedTerrainRequiresMapCoverage(),
			this.config.showTerrainContours, this.config.terrainContourRangeChunks,
			false, 0, 0.0F
		);

		int tint = mapTint(this.minecraft, this.config, this.config.opacity);
		this.drawMapTexture(graphics, x, y, size, tint, rotationDegrees);
		if (this.config.showMinimapBorder) {
			int borderColor = this.config.minimapBorderColor.argb();
			if (this.config.shape == ModConfig.Shape.SQUARE) {
				drawBorder(graphics, x, y, size, borderColor);
			} else {
				drawCircularBorder(graphics, x, y, size, borderColor);
			}
		}
		this.markerRenderer.render(
			graphics, this.session.atlas(), this.config, dimension,
			playerPosition.x, playerPosition.z, this.config.zoom, rotationDegrees,
			x, y, size, size, this.config.shape == ModConfig.Shape.CIRCLE,
			Integer.MIN_VALUE, Integer.MIN_VALUE, false
		);
		this.drawOtherPlayers(graphics, playerPosition.x, playerPosition.z, x, y, size, partialTick, rotationDegrees);
		drawPlayerArrow(graphics, x + size / 2, y + size / 2, this.config.rotateMinimap ? -180.0F : playerYaw);
		if (this.config.rotateMinimap && this.config.showNorth) {
			drawNorthIndicator(graphics, x, y, size, this.config.shape == ModConfig.Shape.CIRCLE, rotationDegrees);
		}

		if (this.config.showCardinalDirections) {
			this.drawCardinalDirections(graphics, x, y, size, rotationDegrees);
		}
		if (this.config.showCoordinates) {
			String coordinates = (int)Math.floor(playerPosition.x) + ", " + (int)Math.floor(playerPosition.z);
			int coordinatesY = y + size + (this.config.showCardinalDirections ? 17 : 5);
			graphics.centeredText(this.minecraft.font, coordinates, x + size / 2, coordinatesY, 0xFFFFFFFF);
		}
	}

	private void drawMapTexture(
		GuiGraphicsExtractor graphics, int x, int y, int size, int tint, double rotationDegrees
	) {
		boolean circular = this.config.shape == ModConfig.Shape.CIRCLE;
		if (!this.config.rotateMinimap) {
			if (circular) {
				this.viewTexture.blitCircular(graphics, x, y, size, size, tint);
			} else {
				this.viewTexture.blit(graphics, x, y, size, size, tint);
			}
			return;
		}

		if (!circular) {
			graphics.enableScissor(x, y, x + size, y + size);
		}
		graphics.pose().pushMatrix();
		try {
			graphics.pose().translate(x + size / 2.0F, y + size / 2.0F);
			graphics.pose().rotate((float)Math.toRadians(rotationDegrees));
			graphics.pose().translate(-(x + size / 2.0F), -(y + size / 2.0F));
			if (circular) {
				this.viewTexture.blitCircular(graphics, x, y, size, size, tint);
			} else {
				this.viewTexture.blitOverscanned(graphics, x, y, size, size, tint);
			}
		} finally {
			graphics.pose().popMatrix();
			if (!circular) {
				graphics.disableScissor();
			}
		}
	}

	private void drawOtherPlayers(
		GuiGraphicsExtractor graphics, double centerX, double centerZ, int mapX, int mapY, int size, float partialTick,
		double rotationDegrees
	) {
		if (!this.config.showPlayers || this.minecraft.level == null || this.minecraft.player == null) {
			return;
		}
		double radius = size / 2.0 - 4.0;
		for (var player : this.minecraft.level.players()) {
			if (player == this.minecraft.player) {
				continue;
			}
			var position = player.getPosition(partialTick);
			double dx = (position.x - centerX) / this.config.zoom;
			double dz = (position.z - centerZ) / this.config.zoom;
			MinimapProjection.Offset offset = MinimapProjection.worldToScreen(dx, dz, rotationDegrees);
			dx = offset.x();
			dz = offset.y();
			if (Math.abs(dx) > radius || Math.abs(dz) > radius
				|| (this.config.shape == ModConfig.Shape.CIRCLE && dx * dx + dz * dz > radius * radius)) {
				continue;
			}
			drawOtherPlayerMarker(graphics, (int)Math.round(mapX + size / 2.0 + dx), (int)Math.round(mapY + size / 2.0 + dz));
		}
	}

	private void drawCardinalDirections(GuiGraphicsExtractor graphics, int x, int y, int size, double rotationDegrees) {
		boolean circular = this.config.shape == ModConfig.Shape.CIRCLE;
		drawCardinal(graphics, "north", 0.0, -1.0, x, y, size, circular, rotationDegrees);
		drawCardinal(graphics, "south", 0.0, 1.0, x, y, size, circular, rotationDegrees);
		drawCardinal(graphics, "west", -1.0, 0.0, x, y, size, circular, rotationDegrees);
		drawCardinal(graphics, "east", 1.0, 0.0, x, y, size, circular, rotationDegrees);
	}

	private void drawCardinal(
		GuiGraphicsExtractor graphics,
		String key,
		double worldX,
		double worldZ,
		int x,
		int y,
		int size,
		boolean circular,
		double rotationDegrees
	) {
		MinimapProjection.Offset point = MinimapProjection.framePoint(
			worldX, worldZ, rotationDegrees, size / 2.0 + 6.0, size / 2.0 + 6.0, circular
		);
		int textX = (int)Math.round(x + size / 2.0 + point.x());
		int textY = (int)Math.round(y + size / 2.0 + point.y()) - 4;
		graphics.centeredText(
			this.minecraft.font, Component.translatable("direction.neverket-minimap." + key), textX, textY, 0xFFFFFFFF
		);
	}

	private static void drawNorthIndicator(
		GuiGraphicsExtractor graphics, int x, int y, int size, boolean circular, double rotationDegrees
	) {
		MinimapProjection.Offset direction = MinimapProjection.worldToScreen(0.0, -1.0, rotationDegrees).normalized();
		MinimapProjection.Offset point = MinimapProjection.framePoint(
			0.0, -1.0, rotationDegrees, size / 2.0, size / 2.0, circular
		);
		int centerX = (int)Math.round(x + size / 2.0 + point.x());
		int centerY = (int)Math.round(y + size / 2.0 + point.y());
		drawCompassNeedle(graphics, centerX, centerY, direction.x(), direction.y());
	}

	private static void drawCompassNeedle(
		GuiGraphicsExtractor graphics, int centerX, int centerY, double directionX, double directionY
	) {
		double perpendicularX = -directionY;
		double perpendicularY = directionX;
		for (int step = -4; step <= 4; step++) {
			drawNeedleOutline(graphics, centerX, centerY, directionX, directionY, perpendicularX, perpendicularY, step, 0);
		}
		for (int side = -2; side <= 2; side++) {
			drawNeedleOutline(graphics, centerX, centerY, directionX, directionY, perpendicularX, perpendicularY, 1, side);
		}
		for (int step = -3; step <= 0; step++) {
			drawNeedlePixel(graphics, centerX, centerY, directionX, directionY, perpendicularX, perpendicularY, step, 0, 0xFFFFFFFF);
		}
		for (int step = 1; step <= 4; step++) {
			drawNeedlePixel(graphics, centerX, centerY, directionX, directionY, perpendicularX, perpendicularY, step, 0, 0xFFFF4545);
		}
		for (int side = -2; side <= 2; side++) {
			drawNeedlePixel(graphics, centerX, centerY, directionX, directionY, perpendicularX, perpendicularY, 1, side, 0xFFFF4545);
		}
	}

	private static void drawNeedleOutline(
		GuiGraphicsExtractor graphics, int centerX, int centerY, double directionX, double directionY,
		double perpendicularX, double perpendicularY, int step, int side
	) {
		int pixelX = (int)Math.round(centerX + directionX * step + perpendicularX * side);
		int pixelY = (int)Math.round(centerY + directionY * step + perpendicularY * side);
		graphics.fill(pixelX - 1, pixelY - 1, pixelX + 2, pixelY + 2, 0xE0000000);
	}

	private static void drawNeedlePixel(
		GuiGraphicsExtractor graphics, int centerX, int centerY, double directionX, double directionY,
		double perpendicularX, double perpendicularY, int step, int side, int color
	) {
		int pixelX = (int)Math.round(centerX + directionX * step + perpendicularX * side);
		int pixelY = (int)Math.round(centerY + directionY * step + perpendicularY * side);
		graphics.fill(pixelX, pixelY, pixelX + 1, pixelY + 1, color);
	}

	private boolean useDetailedTerrain() {
		return this.config.recordingMode == ModConfig.RecordingMode.EXPLORED_TERRAIN
			|| this.config.mapDetailMode == ModConfig.MapDetailMode.LOADED_TERRAIN_DETAIL;
	}

	private boolean detailedTerrainRequiresMapCoverage() {
		return this.config.recordingMode == ModConfig.RecordingMode.MAPS;
	}

	private void resizeViewTexture(int size, boolean rotationOverscan) {
		if (this.viewTexture != null && this.viewSize == size && this.viewRotationOverscan == rotationOverscan) {
			return;
		}
		if (this.viewTexture != null) {
			this.viewTexture.close();
		}
		this.viewSize = size;
		this.viewRotationOverscan = rotationOverscan;
		this.viewTexture = new MapViewTexture(
			this.minecraft,
			this.graphicsAccess,
			Identifier.fromNamespaceAndPath("neverket-minimap", "hud_view"),
			size,
			size,
			0,
			rotationOverscan
		);
	}

	private boolean needsRotationOverscan() {
		return this.config.rotateMinimap && this.config.shape == ModConfig.Shape.SQUARE;
	}

	private boolean hasVisibleEffects() {
		return this.minecraft.player != null
			&& this.minecraft.player.getActiveEffects().stream().anyMatch(effect -> effect.isVisible() && effect.showIcon());
	}

	static int mapTint(Minecraft minecraft, ModConfig config, float opacity) {
		if (minecraft.level == null) {
			return MapLighting.tint(config, opacity, 0, false, false);
		}
		return MapLighting.tint(
			config,
			opacity,
			minecraft.level.getSkyDarken(),
			minecraft.level.dimensionType().hasSkyLight(),
			minecraft.level.dimensionType().hasFixedTime()
		);
	}

	static void drawPlayerArrow(GuiGraphicsExtractor graphics, int centerX, int centerY, float yawDegrees) {
		TextureAtlasSprite sprite = Minecraft.getInstance()
			.getAtlasManager()
			.getAtlasOrThrow(AtlasIds.MAP_DECORATIONS)
			.getSprite(MapDecorationTypes.PLAYER.value().assetId());
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, centerY);
		graphics.pose().rotate((float)Math.toRadians(yawDegrees + 180.0F));
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, -5, -5, 10, 10);
		graphics.pose().popMatrix();
	}

	static void drawOtherPlayerMarker(GuiGraphicsExtractor graphics, int centerX, int centerY) {
		graphics.fill(centerX - 1, centerY - 4, centerX + 2, centerY + 5, 0xFF55DDE0);
		graphics.fill(centerX - 4, centerY - 1, centerX + 5, centerY + 2, 0xFF55DDE0);
		graphics.fill(centerX, centerY, centerX + 1, centerY + 1, 0xFFFFFFFF);
	}

	private static void drawBorder(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
		graphics.fill(x - 1, y - 1, x + size + 1, y, color);
		graphics.fill(x - 1, y + size, x + size + 1, y + size + 1, color);
		graphics.fill(x - 1, y, x, y + size, color);
		graphics.fill(x + size, y, x + size + 1, y + size, color);
	}

	private static void drawCircularBorder(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
		double radius = size / 2.0;
		double innerRadius = Math.max(0.0, radius - 1.0);
		double center = size / 2.0;
		for (int row = 0; row < size; row++) {
			double dy = row + 0.5 - center;
			double outerSpan = Math.sqrt(Math.max(0.0, radius * radius - dy * dy));
			int outerLeft = Math.max(0, (int)Math.ceil(center - outerSpan - 0.5));
			int outerRight = Math.min(size, (int)Math.floor(center + outerSpan - 0.5) + 1);
			if (Math.abs(dy) >= innerRadius) {
				graphics.fill(x + outerLeft, y + row, x + outerRight, y + row + 1, color);
				continue;
			}
			double innerSpan = Math.sqrt(innerRadius * innerRadius - dy * dy);
			int innerLeft = Math.max(outerLeft, (int)Math.ceil(center - innerSpan - 0.5));
			int innerRight = Math.min(outerRight, (int)Math.floor(center + innerSpan - 0.5) + 1);
			graphics.fill(x + outerLeft, y + row, x + innerLeft, y + row + 1, color);
			graphics.fill(x + innerRight, y + row, x + outerRight, y + row + 1, color);
		}
	}

	@Override
	public void close() {
		if (this.viewTexture != null) {
			this.viewTexture.close();
		}
	}
}
