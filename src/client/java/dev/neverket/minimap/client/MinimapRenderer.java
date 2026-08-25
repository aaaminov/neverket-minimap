package dev.neverket.minimap.client;

import dev.neverket.minimap.config.ModConfig;
import dev.neverket.minimap.config.MapLighting;
import java.util.Optional;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
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

	public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		if (!this.config.visible || this.minecraft.options.hideGui || this.minecraft.screen instanceof FullscreenMapScreen
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
		String dimension = this.minecraft.level.dimension().location().toString();
		this.viewTexture.update(
			this.session.atlas(), this.session.terrainContours(), dimension,
			playerPosition.x, playerPosition.z, this.config.zoom, size, size,
			this.config.shape == ModConfig.Shape.CIRCLE, this.config.unknownTerrain, true,
			this.useDetailedTerrain(), this.detailedTerrainRequiresMapCoverage(),
			this.config.showTerrainContours, this.config.terrainContourRangeChunks,
			false, 0, 0.0F
		);

		int tint = mapTint(this.minecraft, this.config, this.config.opacity, partialTick);
		if (this.config.shape == ModConfig.Shape.CIRCLE) {
			this.viewTexture.blitCircular(graphics, x, y, size, size, tint);
		} else {
			this.viewTexture.blit(graphics, x, y, size, size, tint);
		}
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
		drawPlayerArrow(graphics, x + size / 2, y + size / 2, this.minecraft.player.getViewYRot(partialTick));

		if (this.config.showCardinalDirections) {
			graphics.drawCenteredString(this.minecraft.font, Component.translatable("direction.neverket-minimap.north"), x + size / 2, y - 10, 0xFFFFFFFF);
			graphics.drawCenteredString(this.minecraft.font, Component.translatable("direction.neverket-minimap.south"), x + size / 2, y + size + 2, 0xFFFFFFFF);
			graphics.drawString(this.minecraft.font, Component.translatable("direction.neverket-minimap.west"), x - 10, y + size / 2 - 4, 0xFFFFFFFF, true);
			graphics.drawString(this.minecraft.font, Component.translatable("direction.neverket-minimap.east"), x + size + 3, y + size / 2 - 4, 0xFFFFFFFF, true);
		}
		if (this.config.showCoordinates) {
			String coordinates = (int)Math.floor(playerPosition.x) + ", " + (int)Math.floor(playerPosition.z);
			int coordinatesY = y + size + (this.config.showCardinalDirections ? 17 : 5);
			graphics.drawCenteredString(this.minecraft.font, coordinates, x + size / 2, coordinatesY, 0xFFFFFFFF);
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
			ResourceLocation.fromNamespaceAndPath("neverket-minimap", "hud_view"),
			size,
			size,
			4
		);
	}

	private boolean hasVisibleEffects() {
		return this.minecraft.player != null
			&& this.minecraft.player.getActiveEffects().stream().anyMatch(effect -> effect.isVisible() && effect.showIcon());
	}

	static int mapTint(Minecraft minecraft, ModConfig config, float opacity, float partialTick) {
		if (minecraft.level == null) {
			return MapLighting.tint(config, opacity, 0, false, false);
		}
		return MapLighting.tintFromSkyBrightness(
			config,
			opacity,
			minecraft.level.getSkyDarken(partialTick),
			minecraft.level.dimensionType().hasSkyLight(),
			minecraft.level.dimensionType().hasFixedTime()
		);
	}

	static void drawPlayerArrow(GuiGraphics graphics, int centerX, int centerY, float yawDegrees) {
		MapDecoration decoration = new MapDecoration(MapDecorationTypes.PLAYER, (byte)0, (byte)0, (byte)0, Optional.empty());
		TextureAtlasSprite sprite = Minecraft.getInstance().getMapDecorationTextures().get(decoration);
		graphics.pose().pushPose();
		graphics.pose().translate(centerX, centerY, 0.0F);
		graphics.pose().mulPose(Axis.ZP.rotationDegrees(yawDegrees + 180.0F));
		graphics.blit(-5, -5, 0, 10, 10, sprite);
		graphics.pose().popPose();
	}

	private static void drawBorder(GuiGraphics graphics, int x, int y, int size, int color) {
		graphics.fill(x - 1, y - 1, x + size + 1, y, color);
		graphics.fill(x - 1, y + size, x + size + 1, y + size + 1, color);
		graphics.fill(x - 1, y, x, y + size, color);
		graphics.fill(x + size, y, x + size + 1, y + size, color);
	}

	private static void drawCircularBorder(GuiGraphics graphics, int x, int y, int size, int color) {
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
