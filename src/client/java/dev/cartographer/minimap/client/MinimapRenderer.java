package dev.cartographer.minimap.client;

import dev.cartographer.minimap.config.ModConfig;
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
	private final MapMarkerRenderer markerRenderer;
	private MapViewTexture viewTexture;
	private int viewSize;

	public MinimapRenderer(Minecraft minecraft, WorldSession session, ModConfig config) {
		this.minecraft = minecraft;
		this.session = session;
		this.config = config;
		this.markerRenderer = new MapMarkerRenderer(minecraft);
		this.resizeViewTexture(config.size);
	}

	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!this.config.visible || this.minecraft.gui.screen() instanceof FullscreenMapScreen
			|| this.minecraft.player == null || this.minecraft.level == null || !this.session.active()) {
			return;
		}

		int size = this.config.size;
		this.resizeViewTexture(size);
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
		String dimension = this.minecraft.level.dimension().identifier().toString();
		this.viewTexture.update(
			this.session.atlas(), dimension, playerPosition.x, playerPosition.z, this.config.zoom, size, size,
			this.config.shape == ModConfig.Shape.CIRCLE, this.config.unknownTerrain, true,
			this.useDetailedTerrain(), this.detailedTerrainRequiresMapCoverage(), false,
			this.config.showTerrainContours, this.config.terrainContourRangeChunks,
			false, 0, 0.0F
		);

		int tint = mapTint(this.minecraft, this.config, this.config.opacity);
		this.viewTexture.blit(graphics, x, y, size, size, tint);
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
			playerPosition.x, playerPosition.z, this.config.zoom,
			x, y, size, size, this.config.shape == ModConfig.Shape.CIRCLE,
			Integer.MIN_VALUE, Integer.MIN_VALUE, false
		);
		drawPlayerArrow(graphics, x + size / 2, y + size / 2, this.minecraft.player.getYRot(partialTick));

		if (this.config.showCardinalDirections) {
			graphics.centeredText(this.minecraft.font, Component.translatable("direction.neverket-minimap.north"), x + size / 2, y - 10, 0xFFFFFFFF);
			graphics.centeredText(this.minecraft.font, Component.translatable("direction.neverket-minimap.south"), x + size / 2, y + size + 2, 0xFFFFFFFF);
			graphics.text(this.minecraft.font, Component.translatable("direction.neverket-minimap.west"), x - 10, y + size / 2 - 4, 0xFFFFFFFF, true);
			graphics.text(this.minecraft.font, Component.translatable("direction.neverket-minimap.east"), x + size + 3, y + size / 2 - 4, 0xFFFFFFFF, true);
		}
		if (this.config.showCoordinates) {
			String coordinates = (int)Math.floor(playerPosition.x) + ", " + (int)Math.floor(playerPosition.z);
			int coordinatesY = y + size + (this.config.showCardinalDirections ? 17 : 5);
			graphics.centeredText(this.minecraft.font, coordinates, x + size / 2, coordinatesY, 0xFFFFFFFF);
		}
	}

	private boolean useDetailedTerrain() {
		return this.config.recordingMode == ModConfig.RecordingMode.EXPLORED_TERRAIN
			|| this.config.mapDetailMode == ModConfig.MapDetailMode.LOADED_TERRAIN_DETAIL;
	}

	private boolean detailedTerrainRequiresMapCoverage() {
		return this.config.recordingMode == ModConfig.RecordingMode.MAPS;
	}

	private void resizeViewTexture(int size) {
		if (this.viewTexture != null && this.viewSize == size) {
			return;
		}
		if (this.viewTexture != null) {
			this.viewTexture.close();
		}
		this.viewSize = size;
		this.viewTexture = new MapViewTexture(
			this.minecraft,
			Identifier.fromNamespaceAndPath("neverket-minimap", "hud_view"),
			size,
			size
		);
	}

	private boolean hasVisibleEffects() {
		return this.minecraft.player != null
			&& this.minecraft.player.getActiveEffects().stream().anyMatch(effect -> effect.isVisible() && effect.showIcon());
	}

	static int mapTint(Minecraft minecraft, ModConfig config, float opacity) {
		float brightness = 1.0F;
		if (config.mapLightingMode == ModConfig.MapLightingMode.DAY_NIGHT && minecraft.level != null
			&& minecraft.level.dimensionType().hasSkyLight() && !minecraft.level.dimensionType().hasFixedTime()) {
			brightness = 1.0F - Math.clamp(minecraft.level.getSkyDarken() / 15.0F, 0.0F, 1.0F) * config.nightDarkness;
		}
		int alpha = Math.round(Math.clamp(opacity, 0.0F, 1.0F) * 255.0F);
		int channel = Math.round(brightness * 255.0F);
		return alpha << 24 | channel << 16 | channel << 8 | channel;
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

	private static void drawBorder(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
		graphics.fill(x - 1, y - 1, x + size + 1, y, color);
		graphics.fill(x - 1, y + size, x + size + 1, y + size + 1, color);
		graphics.fill(x - 1, y, x, y + size, color);
		graphics.fill(x + size, y, x + size + 1, y + size, color);
	}

	private static void drawCircularBorder(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
		double centerX = x + size / 2.0;
		double centerY = y + size / 2.0;
		double radius = size / 2.0;
		int previousX = Integer.MIN_VALUE;
		int previousY = Integer.MIN_VALUE;
		int samples = Math.max(180, size * 4);
		for (int sample = 0; sample < samples; sample++) {
			double angle = Math.PI * 2.0 * sample / samples;
			int pixelX = (int)Math.round(centerX + Math.cos(angle) * radius);
			int pixelY = (int)Math.round(centerY + Math.sin(angle) * radius);
			if (pixelX != previousX || pixelY != previousY) {
				graphics.fill(pixelX, pixelY, pixelX + 1, pixelY + 1, color);
				previousX = pixelX;
				previousY = pixelY;
			}
		}
	}

	@Override
	public void close() {
		if (this.viewTexture != null) {
			this.viewTexture.close();
		}
	}
}
